package com.mfg.mdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.mdm.domain.BizType;
import com.mfg.mdm.domain.MasterDataEntity;
import com.mfg.mdm.domain.MasterDataStatus;
import com.mfg.security.config.CurrentUser;
import com.mfg.workflow.dto.StartApprovalRequest;
import com.mfg.workflow.entity.WfInstance;
import com.mfg.workflow.service.ApprovalEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 主数据核心服务 —— 状态机 + 审批接入的统一实现。
 *
 * <p>12 类主数据的 CRUD 各有各的字段，但「状态流转 + 提审 + 发布 + 分发」
 * 这套流程完全一致。本类把这套流程抽出来，各模块只需传入自己的实体与仓储。
 *
 * <p><b>状态流转规则</b>：
 * <pre>
 *   新建       → DRAFT（可编辑，下游不可见）
 *   提交审批   → PENDING（不可编辑）
 *   审批通过   → PUBLISHED（★ 下游可见）+ 自动分发到 9 个系统
 *   审批驳回   → REJECTED（可重新编辑提交）
 *   已发布后改 → CHANGING（旧版本仍可用）+ 走变更审批
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final ApprovalEngine approvalEngine;
    private final MasterDataDistributor distributor;
    private final ObjectMapper objectMapper;

    // ============================================================
    // 一、新建
    // ============================================================

    /**
     * 保存新建的主数据（草稿状态）。
     *
     * <p>新数据一律是 DRAFT：可自由编辑，但业务系统看不到。
     * 必须走完审批才会变成 PUBLISHED。
     */
    @Transactional
    public <T extends MasterDataEntity> T create(T entity, JpaRepository<T, Long> repo) {
        String me = CurrentUser.usernameOrSystem();

        entity.setStatus(MasterDataStatus.DRAFT.name());
        entity.setVersionNo(1);
        entity.setUpdatedBy(me);
        entity.setUpdatedAt(LocalDateTime.now());

        T saved = repo.save(entity);
        log.info("主数据新增(草稿): {} [{}] by {}",
                entity.bizType().getLabel(), entity.getBusinessCode(), me);
        return saved;
    }

    // ============================================================
    // 二、修改
    // ============================================================

    /**
     * 修改主数据。
     *
     * <p>只有 DRAFT / REJECTED 状态可直接改。已发布的数据要改，
     * 必须先发起变更申请走审批——这是主数据管控的核心约束。
     */
    @Transactional
    public <T extends MasterDataEntity> T update(T entity, JpaRepository<T, Long> repo,
                                                 String changeReason) {
        MasterDataStatus status = entity.statusEnum();

        if (!status.isEditable()) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,
                    "【%s】当前状态为「%s」，不可直接修改。已发布的主数据变更需走变更审批流程"
                            .formatted(entity.bizType().getLabel(), status.getLabel()));
        }

        entity.setChangeReason(changeReason);
        entity.setUpdatedBy(CurrentUser.usernameOrSystem());
        entity.setUpdatedAt(LocalDateTime.now());

        T saved = repo.save(entity);
        log.info("主数据修改: {} [{}] 原因={}",
                entity.bizType().getLabel(), entity.getBusinessCode(), changeReason);
        return saved;
    }

    // ============================================================
    // 三、提交审批
    // ============================================================

    /**
     * 提交审批。
     *
     * <p>把实体快照为 JSON 存入流程实例——审批人看到的将是
     * <b>提交那一刻的数据</b>，而非后续可能被改动的当前值。
     */
    @Transactional
    public <T extends MasterDataEntity> WfInstance submitForApproval(
            T entity, JpaRepository<T, Long> repo) {

        BizType type = entity.bizType();

        if (!type.needsApproval()) {
            // 基础字典类（计量单位等）无需审批，直接发布
            entity.publish(CurrentUser.usernameOrSystem());
            repo.save(entity);
            distributor.distribute(entity);
            log.info("主数据直接发布(无需审批): {} [{}]",
                    type.getLabel(), entity.getBusinessCode());
            return null;
        }

        MasterDataStatus status = entity.statusEnum();
        if (!status.isEditable() && status != MasterDataStatus.PUBLISHED) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,
                    "当前状态「%s」不可提交审批".formatted(status.getLabel()));
        }

        // 状态 → 审批中（已发布的走变更，旧版本仍可用）
        boolean isChange = status == MasterDataStatus.PUBLISHED;
        entity.markPending();
        repo.save(entity);

        WfInstance instance = approvalEngine.start(new StartApprovalRequest(
                type.name(),
                entity.getId(),
                entity.getBusinessCode(),
                buildTitle(entity, isChange),
                buildSnapshot(entity)));

        log.info("主数据提交审批: {} [{}] → 实例={} 状态={}",
                type.getLabel(), entity.getBusinessCode(),
                instance.getInstanceNo(), entity.getStatus());

        return instance;
    }

    // ============================================================
    // 四、审批结果处理（由 ApprovalCallback 调用）
    // ============================================================

    /**
     * 审批通过 → 发布 + 自动分发到全部目标系统。
     *
     * @return 每个目标系统的分发结果
     */
    @Transactional
    public <T extends MasterDataEntity> java.util.List<MasterDataDistributor.DistResult> onApproved(
            Long entityId, JpaRepository<T, Long> repo) {

        T entity = repo.findById(entityId)
                .orElseThrow(() -> BizException.notFound("主数据", entityId));

        String operator = CurrentUser.usernameOrSystem();
        entity.publish(operator);
        repo.save(entity);

        log.info("主数据已发布: {} [{}] 版本={}",
                entity.bizType().getLabel(), entity.getBusinessCode(), entity.getVersionNo());

        // 发布后立即分发——演示时"审批通过 → ERP 立刻能选到这个物料"
        return distributor.distribute(entity);
    }

    /**
     * 审批驳回 → 回到可编辑状态，记录驳回原因。
     */
    @Transactional
    public <T extends MasterDataEntity> void onRejected(Long entityId,
                                                        JpaRepository<T, Long> repo,
                                                        String reason) {
        T entity = repo.findById(entityId)
                .orElseThrow(() -> BizException.notFound("主数据", entityId));

        MasterDataStatus before = entity.statusEnum();
        entity.markRejected(reason);
        repo.save(entity);

        log.info("主数据被驳回: {} [{}] 状态 {} → {}，原因={}",
                entity.bizType().getLabel(), entity.getBusinessCode(),
                before.getLabel(), entity.statusEnum().getLabel(), reason);
    }

    // ============================================================
    // 五、对外查询辅助
    // ============================================================

    /**
     * 校验主数据可被业务系统引用。
     *
     * <p>业务系统在单据中引用主数据前必须调用此方法——这是
     * 「只有已发布的主数据才能被消费」这条规则的实际执行点。
     *
     * <p>演示价值：在 ERP 建采购订单时选一个 DRAFT 状态的物料，
     * 会收到明确的错误提示，而不是悄悄存了个无效编码。
     */
    public void assertConsumable(MasterDataEntity entity, String refBy) {
        MasterDataStatus status = entity.statusEnum();
        if (!status.isConsumable()) {
            throw BizException.of(ErrorCode.MASTER_DATA_NOT_PUBLISHED,
                    "%s【%s】当前状态为「%s」，尚未发布，%s 不能引用。请先完成审批。"
                            .formatted(entity.bizType().getLabel(), entity.getBusinessCode(),
                                    status.getLabel(), refBy));
        }
    }

    /**
     * 判断主数据是否存在。
     *
     * <p>供审批回调用：回调在流程结束后触发，此时审批状态已经落库。
     * 若业务实体不存在（例如演示触发器创建的假单据），只应记日志跳过，
     * 而不是抛异常——否则会连带影响审批本身。
     */
    public <T extends MasterDataEntity> boolean exists(Long entityId, JpaRepository<T, Long> repo) {
        return entityId != null && repo.existsById(entityId);
    }

    // ---------- 内部辅助 ----------

    private String buildTitle(MasterDataEntity e, boolean isChange) {
        return "%s · %s%s".formatted(
                e.getBusinessCode(),
                e.getName() == null ? "" : e.getName(),
                isChange ? "（变更申请）" : "（新增申请）");
    }

    private String buildSnapshot(MasterDataEntity e) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "bizType", e.bizType().name(),
                    "code", e.getBusinessCode() == null ? "" : e.getBusinessCode(),
                    "name", e.getName() == null ? "" : e.getName(),
                    "versionNo", e.getVersionNo() == null ? 0 : e.getVersionNo(),
                    "changeReason", e.getChangeReason() == null ? "" : e.getChangeReason(),
                    "snapshotAt", LocalDateTime.now().toString()));
        } catch (Exception ex) {
            log.warn("构建快照失败: {}", ex.getMessage());
            return null;
        }
    }
}
