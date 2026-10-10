package com.mfg.workflow.service;

import com.mfg.workflow.dto.PendingTaskView;
import com.mfg.workflow.dto.TimelineItem;
import com.mfg.workflow.entity.WfActionLog;
import com.mfg.workflow.entity.WfDefinition;
import com.mfg.workflow.entity.WfInstance;
import com.mfg.workflow.entity.WfNode;
import com.mfg.workflow.entity.WfTask;
import com.mfg.workflow.repo.WfActionLogRepository;
import com.mfg.workflow.repo.WfDefinitionRepository;
import com.mfg.workflow.repo.WfInstanceRepository;
import com.mfg.workflow.repo.WfTaskRepository;
import com.mfg.security.repo.SysUserRepository;
import com.mfg.security.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * 审批查询服务 —— 把引擎的原始数据组装成前端友好的视图。
 */
@Service
@RequiredArgsConstructor
public class WorkflowQueryService {

    private final ApprovalEngine engine;
    private final WfInstanceRepository instanceRepo;
    private final WfActionLogRepository logRepo;
    private final WfDefinitionRepository definitionRepo;
    private final WfTaskRepository taskRepo;
    private final SysUserRepository userRepo;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> userOptions() {
        return userRepo.findByEnabledTrueOrderByIdAsc().stream()
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()) && !Boolean.TRUE.equals(user.getLocked()))
                .map(user -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("username", user.getUsername());
                    row.put("realName", user.getRealName());
                    row.put("deptCode", user.getDeptCode());
                    row.put("positionName", user.getPositionName());
                    row.put("roles", user.roleCodes());
                    row.put("canApprove", user.roleCodes().contains("ADMIN") || user.getRoles().stream()
                            .anyMatch(role -> role.permissionCodes().contains("WF:TASK:APPROVE")));
                    return row;
                }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> progress(Long instanceId) {
        WfInstance instance = engine.getInstance(instanceId);
        List<WfTask> tasks = taskRepo.findByInstanceIdOrderByNodeSeqAsc(instanceId);
        List<WfTask> selected = tasks.stream().filter(task -> "SELECTED_ROUTE".equals(task.getSignMode())).toList();
        List<Map<String, Object>> steps = new java.util.ArrayList<>();
        if (!selected.isEmpty()) {
            for (WfTask task : selected) steps.add(step(task.getNodeSeq(), task.getNodeName(), task.getApproverRole(), task));
        } else {
            WfDefinition definition = definitionRepo.findById(instance.getDefinitionId()).orElseThrow();
            for (WfNode node : definition.getNodes()) {
                WfTask latest = tasks.stream().filter(task -> node.getNodeSeq().equals(task.getNodeSeq()))
                        .max(java.util.Comparator.comparing(WfTask::getId)).orElse(null);
                steps.add(step(node.getNodeSeq(), node.getNodeName(), node.getApproverRole(), latest));
            }
        }
        var me = CurrentUser.get();
        Long myTaskId = me.hasPermission("WF:TASK:APPROVE") ? tasks.stream()
                .filter(WfTask::isPending)
                .filter(task -> task.getAssignedUser() == null ? me.hasRole(task.getApproverRole())
                        : me.getUsername().equals(task.getAssignedUser()))
                .map(WfTask::getId).findFirst().orElse(null) : null;
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("instanceId", instanceId);
        result.put("instanceNo", instance.getInstanceNo());
        result.put("bizType", instance.getBizType());
        result.put("bizNo", instance.getBizNo());
        result.put("bizTitle", instance.getBizTitle());
        result.put("status", instance.getStatus());
        result.put("submitter", instance.getSubmitter());
        result.put("submittedAt", instance.getSubmittedAt());
        result.put("currentNodeSeq", instance.getCurrentNodeSeq());
        result.put("steps", steps);
        result.put("events", timeline(instanceId));
        result.put("myTaskId", myTaskId);
        return result;
    }

    private Map<String, Object> step(Integer sequence, String name, String role, WfTask task) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("nodeSeq", sequence);
        result.put("nodeName", name);
        result.put("approverRole", "FINANCE_SUPERVISOR".equals(role) ? "FIN_SUPERVISOR" : role);
        result.put("assignedUser", task == null ? null : task.getAssignedUser());
        result.put("assignedName", task == null || task.getAssignedUser() == null ? null : userRepo
                .findByUsername(task.getAssignedUser()).map(user -> user.getRealName()).orElse(task.getAssignedUser()));
        result.put("status", task == null ? "WAITING" : task.getTaskStatus());
        result.put("opinion", task == null ? null : task.getOpinion());
        result.put("finishedAt", task == null ? null : task.getFinishedAt());
        result.put("taskId", task == null ? null : task.getId());
        return result;
    }

    /**
     * 我的待办（含业务信息，可直接渲染）。
     */
    @Transactional(readOnly = true)
    public List<PendingTaskView> myPending() {
        List<WfTask> tasks = engine.myPendingTasks();
        if (tasks.isEmpty()) {
            return List.of();
        }

        // 批量取实例，避免 N+1 查询
        Map<Long, WfInstance> instanceMap = instanceRepo
                .findAllById(tasks.stream().map(WfTask::getInstanceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(WfInstance::getId, i -> i));

        return tasks.stream()
                .map(t -> toView(t, instanceMap.get(t.getInstanceId())))
                .filter(v -> v != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PendingTaskView> myPending(int page, int size) {
        var current = com.mfg.security.config.CurrentUser.get();
        var pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<WfTask> tasks = taskRepo.findMyPendingPagedExcludingBizTypes(
                current.getRoleCodes(), current.getUsername(), ApprovalEngine.READ_ONLY_MDM_BIZ_TYPES, pageable);
        Map<Long, WfInstance> instances = instanceRepo
                .findAllById(tasks.getContent().stream().map(WfTask::getInstanceId).distinct().toList())
                .stream().collect(Collectors.toMap(WfInstance::getId, item -> item));
        List<PendingTaskView> content = tasks.getContent().stream()
                .map(task -> toView(task, instances.get(task.getInstanceId())))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new PageImpl<>(content, pageable, tasks.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> myHandled(int page, int size) {
        String username = com.mfg.security.config.CurrentUser.get().getUsername();
        var pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<WfTask> taskPage = taskRepo.findMyHandled(username, pageable);
        List<WfTask> tasks = taskPage.getContent();
        Map<Long, WfInstance> instances = instanceRepo.findAllById(tasks.stream().map(WfTask::getInstanceId).distinct().toList())
                .stream().collect(Collectors.toMap(WfInstance::getId, item -> item));
        List<Map<String, Object>> content = tasks.stream().map(task -> {
            WfInstance instance = instances.get(task.getInstanceId());
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("taskId", task.getId()); row.put("instanceId", task.getInstanceId());
            row.put("instanceNo", instance == null ? null : instance.getInstanceNo());
            row.put("bizType", instance == null ? null : instance.getBizType());
            row.put("bizNo", instance == null ? null : instance.getBizNo());
            row.put("bizTitle", instance == null ? null : instance.getBizTitle());
            row.put("nodeName", task.getNodeName()); row.put("result", task.getTaskStatus());
            row.put("opinion", task.getOpinion()); row.put("finishedAt", task.getFinishedAt());
            return row;
        }).toList();
        return new PageImpl<>(content, pageable, taskPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<WfInstance> myStarted(int page, int size) {
        return instanceRepo.findBySubmitterOrderByIdDesc(
                com.mfg.security.config.CurrentUser.get().getUsername(),
                PageRequest.of(safePage(page), safeSize(size)));
    }

    private int safePage(int page) { return Math.max(0, page - 1); }

    private int safeSize(int size) { return Math.min(200, Math.max(1, size)); }

    /**
     * 审批时间轴。
     */
    @Transactional(readOnly = true)
    public List<TimelineItem> timeline(Long instanceId) {
        WfInstance instance = engine.getInstance(instanceId);

        Map<Integer, String> nodeNames = definitionRepo.findById(instance.getDefinitionId())
                .map(d -> d.getNodes().stream()
                        .collect(Collectors.toMap(WfNode::getNodeSeq, WfNode::getNodeName,
                                (a, b) -> a)))
                .orElse(Map.of());

        Map<Long, Integer> taskDurations = taskRepo
                .findByInstanceIdOrderByNodeSeqAsc(instanceId).stream()
                .filter(t -> t.getDurationMin() != null)
                .collect(Collectors.toMap(WfTask::getId, WfTask::getDurationMin, (a, b) -> a));

        return logRepo.findByInstanceIdOrderByOperatedAtAscIdAsc(instanceId).stream()
                .map(l -> new TimelineItem(
                        l.getAction(),
                        label(l.getAction()),
                        nodeLabel(l, nodeNames),
                        l.getOperator(),
                        l.getOperatorName(),
                        l.getOpinion(),
                        l.getOperatedAt(),
                        taskDurations.get(l.getTaskId())))
                .toList();
    }

    /**
     * 时间轴该行显示的节点名。
     *
     * <p>三个特殊情况需要区分，否则会误导：
     * <ul>
     *   <li>SUBMIT —— 提交动作发生在进入首节点之前，标「发起」</li>
     *   <li>FINISH —— 流程终点，不属于任何节点，标「流程完成」</li>
     *   <li>其他 —— 显示实际节点名</li>
     * </ul>
     */
    private String nodeLabel(WfActionLog log, Map<Integer, String> nodeNames) {
        if ("FINISH".equals(log.getAction())) {
            return "流程完成";
        }
        if (log.getNodeSeq() == null) {
            return "发起";
        }
        return nodeNames.get(log.getNodeSeq());
    }

    private PendingTaskView toView(WfTask task, WfInstance instance) {
        if (instance == null) {
            return null;
        }
        Long waitingHours = instance.getSubmittedAt() == null ? null
                : Duration.between(instance.getSubmittedAt(), LocalDateTime.now()).toHours();

        return new PendingTaskView(
                task.getId(), instance.getId(), instance.getInstanceNo(),
                instance.getBizType(), instance.getBizId(), instance.getBizNo(),
                instance.getBizTitle(), instance.getBizSnapshot(), instance.getSubmitter(),
                instance.getSubmittedAt(), waitingHours, task.getNodeSeq(),
                task.getNodeName(), task.getApproverRole());
    }

    private String label(String action) {
        return switch (action) {
            case "SUBMIT" -> "提交审批";
            case "APPROVE" -> "同意";
            case "REJECT" -> "驳回";
            case "CANCEL" -> "撤回";
            case "TRANSFER" -> "转办";
            case "ADD_SIGN" -> "加签";
            // 流程终点标记，非人工操作；前端应以不同样式渲染（如灰色）
            case "FINISH" -> "流程完成";
            default -> action;
        };
    }
}
