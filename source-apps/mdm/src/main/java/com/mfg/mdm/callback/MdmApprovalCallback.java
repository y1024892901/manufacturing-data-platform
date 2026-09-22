package com.mfg.mdm.callback;

import com.mfg.mdm.repo.*;
import com.mfg.mdm.service.MasterDataService;
import com.mfg.mdm.service.BomService;
import com.mfg.workflow.callback.ApprovalCallback;
import com.mfg.workflow.entity.WfInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 主数据审批回调 —— 审批引擎与 MDM 模块的接合点。
 *
 * <p>引擎只负责推流程，不认识"物料""BOM"。本类承接业务后果：
 * <ul>
 *   <li>审批通过 → 主数据状态置 PUBLISHED + 版本递增 + <b>自动分发到 9 个业务系统</b></li>
 *   <li>审批驳回 → 状态回 REJECTED，记录驳回原因</li>
 * </ul>
 *
 * <p><b>演示价值</b>：审批通过的那一刻，物料就出现在 ERP / MES / WMS 等系统的
 * 只读副本表里，业务单据的下拉框立刻能选到它。这个「审批 → 立即可用」
 * 的对比是主数据管控最直观的证明。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MdmApprovalCallback implements ApprovalCallback {

    private final MasterDataService masterDataService;
    private final CustomerRepository customerRepo;
    private final SupplierRepository supplierRepo;
    private final MaterialRepository materialRepo;
    private final ProductRepository productRepo;
    private final BomRepository bomRepo;
    private final RoutingRepository routingRepo;
    private final BomService bomService;

    @Override
    public boolean supports(String bizType) {
        return switch (bizType) {
            case "CUSTOMER", "SUPPLIER", "MATERIAL", "PRODUCT", "BOM", "ROUTING" -> true;
            default -> false;
        };
    }

    @Override
    public void onApproved(WfInstance instance) {
        String bizType = instance.getBizType();
        Long bizId = instance.getBizId();

        log.info("审批通过回调: 业务类型={} 单号={} 实例={}",
                bizType, instance.getBizNo(), instance.getInstanceNo());

        var results = switch (bizType) {
            case "CUSTOMER" -> publishIfExists(bizType, bizId, customerRepo);
            case "SUPPLIER" -> publishIfExists(bizType, bizId, supplierRepo);
            case "MATERIAL" -> publishIfExists(bizType, bizId, materialRepo);
            case "PRODUCT" -> publishIfExists(bizType, bizId, productRepo);
            case "BOM" -> publishIfExists(bizType, bizId, bomRepo);
            case "ROUTING" -> publishIfExists(bizType, bizId, routingRepo);
            default -> java.util.List.<com.mfg.mdm.service.MasterDataDistributor.DistResult>of();
        };

        if (!results.isEmpty()) {
            String summary = results.stream()
                    .map(r -> r.system() + (r.success() ? "✓" : "✗"))
                    .reduce((a, b) -> a + " " + b).orElse("");
            log.info("主数据分发结果: {} [{}] → {}",
                    bizType, instance.getBizNo(), summary);
        }

        // BOM 发布后才切换当前版本：审批中的新版本不能影响正在执行的生产订单。
        if ("BOM".equals(bizType)) {
            bomRepo.findById(bizId).ifPresent(bomService::makeCurrent);
        }
    }

    @Override
    public void onRejected(WfInstance instance, String reason) {
        String bizType = instance.getBizType();
        Long bizId = instance.getBizId();

        log.info("审批驳回回调: 业务类型={} 单号={} 原因={}", bizType, instance.getBizNo(), reason);

        switch (bizType) {
            case "CUSTOMER" -> rejectIfExists(bizType, bizId, customerRepo, reason);
            case "SUPPLIER" -> rejectIfExists(bizType, bizId, supplierRepo, reason);
            case "MATERIAL" -> rejectIfExists(bizType, bizId, materialRepo, reason);
            case "PRODUCT" -> rejectIfExists(bizType, bizId, productRepo, reason);
            case "BOM" -> rejectIfExists(bizType, bizId, bomRepo, reason);
            case "ROUTING" -> rejectIfExists(bizType, bizId, routingRepo, reason);
            default -> log.debug("未处理的业务类型: {}", bizType);
        }
    }

    /**
     * 发布——但先确认实体存在。
     *
     * <p>回调在流程结束后触发，此时审批状态已落库。若业务实体不存在
     * （如演示触发器创建的假单据只有流程没有数据），跳过即可，
     * <b>绝不能抛异常</b>——否则调用方事务会被标记回滚，
     * 导致「审批已通过但状态没保存」这种最坏结果。
     */
    private <T extends com.mfg.mdm.domain.MasterDataEntity> java.util.List<
            com.mfg.mdm.service.MasterDataDistributor.DistResult> publishIfExists(
            String bizType, Long bizId, org.springframework.data.jpa.repository.JpaRepository<T, Long> repo) {

        if (!masterDataService.exists(bizId, repo)) {
            log.warn("审批回调跳过: {} 实体不存在 (id={})，"
                            + "该流程可能由演示触发器创建，无对应业务数据",
                    bizType, bizId);
            return java.util.List.of();
        }
        return masterDataService.onApproved(bizId, repo);
    }

    private <T extends com.mfg.mdm.domain.MasterDataEntity> void rejectIfExists(
            String bizType, Long bizId,
            org.springframework.data.jpa.repository.JpaRepository<T, Long> repo,
            String reason) {

        if (!masterDataService.exists(bizId, repo)) {
            log.warn("驳回回调跳过: {} 实体不存在 (id={})", bizType, bizId);
            return;
        }
        masterDataService.onRejected(bizId, repo, reason);
    }

    @Override
    public void onNodeEntered(WfInstance instance, String nodeName) {
        // 当前节点名已由待办列表展示，此处无需额外动作
        log.debug("流程节点流转: 实例={} → {}", instance.getInstanceNo(), nodeName);
    }
}
