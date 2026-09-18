package com.mfg.workflow.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.principal.LoginUser;
import com.mfg.workflow.callback.ApprovalCallback;
import com.mfg.workflow.dto.StartApprovalRequest;
import com.mfg.workflow.entity.*;
import com.mfg.workflow.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 审批引擎 —— 本项目自研的轻量工作流内核。
 *
 * <p>职责边界：只负责「按定义推流程」，不认识任何业务概念。
 * 业务后果由 {@link ApprovalCallback} 的实现方承接。
 *
 * <p>状态机：
 * <pre>
 *           提交
 *   DRAFT ──────► RUNNING ──(逐节点通过)──► APPROVED
 *                    │
 *                    ├──(驳回 BACK)──► REJECTED  ──► 业务单据回到 DRAFT
 *                    ├──(驳回 PREV)──► 退回上一节点，流程继续
 *                    └──(撤回)────────► CANCELED
 * </pre>
 *
 * <p>任务归属模型：任务按<b>角色</b>创建（"工艺主管审核"），
 * 而非按具体人。谁领取就归谁。人事变动无需改流程配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngine {

    private final WfDefinitionRepository definitionRepo;
    private final WfInstanceRepository instanceRepo;
    private final WfTaskRepository taskRepo;
    private final WfActionLogRepository logRepo;

    /**
     * 审批回调集合，<b>延迟注入</b>。
     *
     * <p>为什么用 {@link ObjectProvider} 而不是 {@code List<ApprovalCallback>}：
     * 回调实现方（如 MDM 的 MdmApprovalCallback）需要 MasterDataService，
     * 而 MasterDataService 又要用 ApprovalEngine 发起审批——直接注入会形成
     * {@code Engine → Callback → Service → Engine} 的循环依赖，Spring 启动即失败。
     *
     * <p>用 ObjectProvider 后，引擎在<b>构造时</b>不解析回调，
     * 只在真正触发回调时（提交/审批动作中）才去容器取，循环即被打破。
     * 这是 Spring 官方推荐的打破构造器循环依赖的方式。
     */
    private final ObjectProvider<ApprovalCallback> callbackProvider;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ============================================================
    // 一、发起流程
    // ============================================================

    /**
     * 提交审批。
     *
     * <p>会做幂等保护：同一业务单据已有 RUNNING 实例时拒绝重复提交，
     * 避免审批人看到两条一模一样的待办。
     */
    @Transactional
    public WfInstance start(StartApprovalRequest req) {
        LoginUser me = CurrentUser.get();

        WfDefinition def = definitionRepo.findByBizTypeAndEnabledTrue(req.bizType())
                .orElseThrow(() -> BizException.of(ErrorCode.WORKFLOW_NOT_FOUND,
                        "未配置业务类型【" + req.bizType() + "】的审批流程"));

        if (def.getNodes().isEmpty()) {
            throw BizException.of(ErrorCode.WORKFLOW_NOT_FOUND,
                    "审批流程【" + def.getDefName() + "】未定义任何节点");
        }

        // 幂等：同一单据不允许重复提交
        instanceRepo.findFirstByBizTypeAndBizIdAndStatusOrderByIdDesc(
                        req.bizType(), req.bizId(), "RUNNING")
                .ifPresent(existing -> {
                    throw BizException.conflict(
                            "该单据已在审批中（实例号 " + existing.getInstanceNo()
                                    + "，当前节点：" + existing.getCurrentNodeSeq() + "），请勿重复提交");
                });

        WfInstance instance = new WfInstance();
        instance.setInstanceNo(generateInstanceNo());
        instance.setDefinitionId(def.getId());
        instance.setBizType(req.bizType());
        instance.setBizId(req.bizId());
        instance.setBizNo(req.bizNo());
        instance.setBizTitle(req.bizTitle());
        instance.setBizSnapshot(req.bizSnapshot());
        instance.setSubmitter(me.getUsername());
        instance.setStatus("RUNNING");
        instanceRepo.save(instance);

        // 进入首节点
        WfNode first = def.getNodes().get(0);
        instance.setCurrentNodeSeq(first.getNodeSeq());
        instanceRepo.save(instance);

        createTask(instance, first);

        logAction(instance.getId(), null, null, "SUBMIT", me,
                "提交审批：" + req.bizTitle(), null, "RUNNING");

        log.info("审批发起: {} 实例={} 流程={} 首节点={}({})",
                me.getRealName(), instance.getInstanceNo(), def.getDefName(),
                first.getNodeName(), first.getApproverRole());

        invokeCallback(instance, cb -> cb.onSubmitted(instance));
        invokeCallback(instance, cb -> cb.onNodeEntered(instance, first.getNodeName()));

        return instance;
    }

    // ============================================================
    // 二、审批动作
    // ============================================================

    /**
     * 同意。
     *
     * <p>流程推进规则：
     * <ul>
     *   <li>还有下一节点 → 创建下一节点的任务</li>
     *   <li>已是最后节点 → 流程通过，触发业务回调</li>
     * </ul>
     */
    @Transactional
    public WfInstance approve(Long taskId, String opinion) {
        LoginUser me = CurrentUser.get();
        WfTask task = loadMyPendingTask(taskId, me);

        WfInstance instance = instanceRepo.findById(task.getInstanceId())
                .orElseThrow(() -> BizException.notFound("审批实例", task.getInstanceId()));

        if (!instance.isRunning()) {
            throw BizException.of(ErrorCode.WORKFLOW_ALREADY_FINISHED,
                    "流程已结束（" + instance.getStatus() + "），无需再审批");
        }

        WfDefinition def = definitionRepo.findById(instance.getDefinitionId()).orElseThrow();

        task.complete("APPROVED", opinion, me.getUsername());
        taskRepo.save(task);

        logAction(instance.getId(), task.getId(), task.getNodeSeq(), "APPROVE", me,
                opinion, "RUNNING", "RUNNING");

        log.info("审批同意: {} 在节点【{}】通过，实例={}",
                me.getRealName(), task.getNodeName(), instance.getInstanceNo());

        // 找下一节点
        WfNode next = def.nextNode(task.getNodeSeq());

        if (next == null) {
            // 已是最后节点 → 流程通过
            instance.finish("APPROVED");
            instanceRepo.save(instance);

            // 用 FINISH 而非 APPROVE 记录流程终点：
            // 否则时间轴会出现两条连续的 APPROVE，看起来像同一个人批了两次。
            logAction(instance.getId(), null, null, "FINISH", me,
                    "流程审批完成，业务单据已生效", "RUNNING", "APPROVED");

            log.info("审批通过: 实例={} 业务={} 总耗时={}分钟",
                    instance.getInstanceNo(), instance.getBizTitle(),
                    instance.getTotalDurationMin());

            invokeCallback(instance, cb -> cb.onApproved(instance));
        } else {
            // 推进到下一节点
            instance.setCurrentNodeSeq(next.getNodeSeq());
            instanceRepo.save(instance);

            createTask(instance, next);

            log.info("流转至下一节点: 实例={} → 【{}({})】",
                    instance.getInstanceNo(), next.getNodeName(), next.getApproverRole());

            invokeCallback(instance, cb -> cb.onNodeEntered(instance, next.getNodeName()));
        }

        return instance;
    }

    /**
     * 驳回。
     *
     * <p>两种去向（由节点的 {@code reject_action} 决定）：
     * <ul>
     *   <li>{@code BACK} —— 退回提交人，流程结束，业务单据回草稿</li>
     *   <li>{@code PREV} —— 退回上一节点，流程继续</li>
     * </ul>
     */
    @Transactional
    public WfInstance reject(Long taskId, String opinion) {
        if (opinion == null || opinion.isBlank()) {
            throw BizException.of(ErrorCode.WORKFLOW_OPINION_REQUIRED,
                    "驳回必须填写理由，否则提交人不知道要改什么");
        }

        LoginUser me = CurrentUser.get();
        WfTask task = loadMyPendingTask(taskId, me);

        WfInstance instance = instanceRepo.findById(task.getInstanceId())
                .orElseThrow(() -> BizException.notFound("审批实例", task.getInstanceId()));

        WfDefinition def = definitionRepo.findById(instance.getDefinitionId()).orElseThrow();
        WfNode currentNode = def.nodeAt(task.getNodeSeq());

        task.complete("REJECTED", opinion, me.getUsername());
        taskRepo.save(task);

        logAction(instance.getId(), task.getId(), task.getNodeSeq(), "REJECT", me,
                opinion, "RUNNING", "REJECTED");

        log.info("审批驳回: {} 在节点【{}】驳回，实例={}，去向={}",
                me.getRealName(), task.getNodeName(), instance.getInstanceNo(),
                currentNode != null && currentNode.rejectToSubmitter() ? "退回提交人" : "退上一节点");

        if (currentNode != null && !currentNode.rejectToSubmitter()) {
            // 退上一节点：流程继续
            Optional<WfNode> prev = def.getNodes().stream()
                    .filter(n -> n.getNodeSeq() < task.getNodeSeq())
                    .max(java.util.Comparator.comparingInt(WfNode::getNodeSeq));

            if (prev.isPresent()) {
                instance.setCurrentNodeSeq(prev.get().getNodeSeq());
                instanceRepo.save(instance);

                // 上一节点的旧任务作废，重建待办
                taskRepo.findByInstanceIdAndNodeSeq(instance.getId(), prev.get().getNodeSeq())
                        .forEach(t -> {
                            t.setTaskStatus("CANCELED");
                            taskRepo.save(t);
                        });
                createTask(instance, prev.get());

                logAction(instance.getId(), null, prev.get().getNodeSeq(), "REJECT", me,
                        "退回至上一节点【" + prev.get().getNodeName() + "】", "RUNNING", "RUNNING");
                return instance;
            }
        }

        // 退回提交人：流程结束
        instance.finish("REJECTED");
        instanceRepo.save(instance);

        invokeCallback(instance, cb -> cb.onRejected(instance, opinion));

        return instance;
    }

    /**
     * 撤回（提交人在流程未完成前可撤回）。
     */
    @Transactional
    public WfInstance cancel(Long instanceId, String reason) {
        LoginUser me = CurrentUser.get();

        WfInstance instance = instanceRepo.findById(instanceId)
                .orElseThrow(() -> BizException.notFound("审批实例", instanceId));

        if (!instance.isRunning()) {
            throw BizException.of(ErrorCode.WORKFLOW_ALREADY_FINISHED,
                    "流程已结束，无法撤回");
        }
        if (!instance.getSubmitter().equals(me.getUsername()) && !me.isAdmin()) {
            throw BizException.of(ErrorCode.FORBIDDEN, "只有提交人可以撤回审批");
        }

        // 作废所有待办
        taskRepo.findByInstanceIdOrderByNodeSeqAsc(instanceId).stream()
                .filter(WfTask::isPending)
                .forEach(t -> {
                    t.setTaskStatus("CANCELED");
                    t.setOpinion("提交人撤回");
                    taskRepo.save(t);
                });

        instance.finish("CANCELED");
        instanceRepo.save(instance);

        logAction(instanceId, null, null, "CANCEL", me,
                reason == null ? "提交人撤回" : reason, "RUNNING", "CANCELED");

        // 撤回视同驳回，业务单据回到草稿
        invokeCallback(instance, cb -> cb.onRejected(instance,
                reason == null ? "提交人撤回" : reason));

        log.info("审批撤回: 实例={} 操作人={}", instance.getInstanceNo(), me.getRealName());
        return instance;
    }

    // ============================================================
    // 三、查询
    // ============================================================

    /** 我的待办（按当前用户持有的角色匹配） */
    @Transactional(readOnly = true)
    public List<WfTask> myPendingTasks() {
        LoginUser me = CurrentUser.get();
        if (me.getRoleCodes().isEmpty()) {
            return List.of();
        }
        return taskRepo.findMyPending(me.getRoleCodes());
    }

    /** 我的待办数量（首页角标） */
    @Transactional(readOnly = true)
    public long myPendingCount() {
        LoginUser me = CurrentUser.get();
        if (me.getRoleCodes().isEmpty()) {
            return 0L;
        }
        return taskRepo.countMyPending(me.getRoleCodes());
    }

    /** 审批时间轴 */
    @Transactional(readOnly = true)
    public List<WfActionLog> timeline(Long instanceId) {
        return logRepo.findByInstanceIdOrderByOperatedAtAscIdAsc(instanceId);
    }

    /** 实例详情 */
    @Transactional(readOnly = true)
    public WfInstance getInstance(Long instanceId) {
        return instanceRepo.findById(instanceId)
                .orElseThrow(() -> BizException.notFound("审批实例", instanceId));
    }

    /** 某业务单据的审批历史 */
    @Transactional(readOnly = true)
    public List<WfInstance> historyOf(String bizType, Long bizId) {
        return instanceRepo.findByBizTypeAndBizIdOrderByIdDesc(bizType, bizId);
    }

    /** 某业务单据是否正在审批中 */
    @Transactional(readOnly = true)
    public Optional<WfInstance> runningOf(String bizType, Long bizId) {
        return instanceRepo.findFirstByBizTypeAndBizIdAndStatusOrderByIdDesc(
                bizType, bizId, "RUNNING");
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /** 创建节点任务（按角色，不指定人） */
    private void createTask(WfInstance instance, WfNode node) {
        WfTask task = new WfTask();
        task.setInstanceId(instance.getId());
        task.setNodeSeq(node.getNodeSeq());
        task.setNodeName(node.getNodeName());
        task.setApproverRole(node.getApproverRole());
        task.setTaskStatus("PENDING");
        taskRepo.save(task);
    }

    /**
     * 加载待办任务并校验处理权。
     *
     * <p>三重校验：任务存在 → 未处理 → 当前用户持有该角色。
     * 第三项是权限边界：销售代表不能审批销售主管的待办。
     */
    private WfTask loadMyPendingTask(Long taskId, LoginUser me) {
        WfTask task = taskRepo.findById(taskId)
                .orElseThrow(() -> BizException.of(ErrorCode.WORKFLOW_TASK_NOT_FOUND,
                        "待办不存在：id=" + taskId));

        if (!task.isPending()) {
            throw BizException.of(ErrorCode.WORKFLOW_TASK_NOT_FOUND,
                    "该待办已被处理（当前状态：" + task.getTaskStatus() + "）");
        }

        if (!me.hasRole(task.getApproverRole())) {
            throw BizException.of(ErrorCode.WORKFLOW_NOT_YOUR_TASK,
                    "该待办需要【" + task.getApproverRole() + "】角色，当前用户无此权限");
        }

        // 领取任务（首次处理时记录处理人）
        task.claimBy(me.getUsername());
        return task;
    }

    /** 调用匹配业务类型的回调，单个回调失败不影响主流程 */
    private void invokeCallback(WfInstance instance, java.util.function.Consumer<ApprovalCallback> action) {
        // 运行时才向容器索取回调实现，避免与业务模块形成构造期循环依赖
        for (ApprovalCallback cb : callbackProvider) {
            if (!cb.supports(instance.getBizType())) {
                continue;
            }
            try {
                action.accept(cb);
            } catch (Exception e) {
                // 回调失败不回滚审批本身：审批结果已落库，业务侧问题单独排查
                log.error("审批回调执行失败: 实例={} 业务类型={} 回调={}",
                        instance.getInstanceNo(), instance.getBizType(),
                        cb.getClass().getSimpleName(), e);
            }
        }
    }

    private void logAction(Long instanceId, Long taskId, Integer nodeSeq,
                           String action, LoginUser me, String opinion,
                           String fromStatus, String toStatus) {
        logRepo.save(WfActionLog.of(instanceId, taskId, nodeSeq, action,
                me.getUsername(), me.getRealName(), opinion, fromStatus, toStatus));
    }

    /** 生成实例号：WF-20260918-0001（按日流水） */
    private String generateInstanceNo() {
        String prefix = "WF-" + LocalDate.now().format(DATE_FMT) + "-";
        long todayCount = instanceRepo.count() + 1;
        String no = prefix + String.format("%04d", todayCount);
        // 极端并发下可能有重号，重试几次
        int retry = 0;
        while (instanceRepo.findByInstanceNo(no).isPresent() && retry++ < 100) {
            no = prefix + String.format("%04d", todayCount + retry);
        }
        return no;
    }
}
