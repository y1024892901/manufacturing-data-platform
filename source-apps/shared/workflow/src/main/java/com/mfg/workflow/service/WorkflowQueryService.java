package com.mfg.workflow.service;

import com.mfg.workflow.dto.PendingTaskView;
import com.mfg.workflow.dto.TimelineItem;
import com.mfg.workflow.entity.WfActionLog;
import com.mfg.workflow.entity.WfInstance;
import com.mfg.workflow.entity.WfNode;
import com.mfg.workflow.entity.WfTask;
import com.mfg.workflow.repo.WfActionLogRepository;
import com.mfg.workflow.repo.WfDefinitionRepository;
import com.mfg.workflow.repo.WfInstanceRepository;
import com.mfg.workflow.repo.WfTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
