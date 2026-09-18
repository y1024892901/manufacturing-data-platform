package com.mfg.workflow.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.workflow.dto.PendingTaskView;
import com.mfg.workflow.dto.TimelineItem;
import com.mfg.workflow.entity.WfDefinition;
import com.mfg.workflow.entity.WfInstance;
import com.mfg.workflow.repo.WfDefinitionRepository;
import com.mfg.workflow.service.ApprovalEngine;
import com.mfg.workflow.service.WorkflowQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审批中心接口。
 *
 * <p>演示主线：四个账号接力走完 BOM 三级审批
 * <pre>
 *   1. 赵六(工艺工程师) 提交 BOM 变更
 *   2. 周涛(工艺主管)   登录 → 待办 1 条 → 同意
 *   3. 杨帆(生产主管)   登录 → 待办 1 条 → 同意
 *   4. 郑爽(成本会计)   登录 → 待办 1 条 → 同意 → 流程通过
 * </pre>
 */
@Tag(name = "02. 审批中心", description = "待办、审批、时间轴、流程定义")
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final ApprovalEngine engine;
    private final WorkflowQueryService queryService;
    private final WfDefinitionRepository definitionRepo;

    // ---------- 待办 ----------

    @Operation(summary = "我的待办", description = "按当前用户持有的角色匹配，返回可直接渲染的待办列表")
    @GetMapping("/tasks/pending")
    @PreAuthorize("hasAuthority('WF:TASK:VIEW')")
    public ApiResponse<List<PendingTaskView>> pending() {
        return ApiResponse.ok(queryService.myPending());
    }

    @Operation(summary = "待办数量", description = "首页角标用，避免拉全量数据")
    @GetMapping("/tasks/pending/count")
    public ApiResponse<Long> pendingCount() {
        return ApiResponse.ok(engine.myPendingCount());
    }

    // ---------- 审批动作 ----------

    @Operation(summary = "同意", description = "推进到下一节点；若已是最后节点则流程通过并触发业务回调")
    @PostMapping("/tasks/{taskId}/approve")
    @PreAuthorize("hasAuthority('WF:TASK:APPROVE')")
    public ApiResponse<Map<String, Object>> approve(
            @PathVariable Long taskId,
            @RequestBody(required = false) OpinionRequest body) {

        WfInstance instance = engine.approve(taskId,
                body == null ? null : body.opinion());

        return ApiResponse.ok(Map.of(
                "instanceNo", instance.getInstanceNo(),
                "status", instance.getStatus(),
                "currentNodeSeq", instance.getCurrentNodeSeq() == null
                        ? "" : instance.getCurrentNodeSeq(),
                "message", instance.isApproved()
                        ? "流程审批通过" : "已流转至下一节点"));
    }

    @Operation(summary = "驳回", description = "必须填写意见；按节点配置退回提交人或上一节点")
    @PostMapping("/tasks/{taskId}/reject")
    @PreAuthorize("hasAuthority('WF:TASK:APPROVE')")
    public ApiResponse<Map<String, Object>> reject(
            @PathVariable Long taskId,
            @RequestBody OpinionRequest body) {

        WfInstance instance = engine.reject(taskId, body.opinion());

        return ApiResponse.ok(Map.of(
                "instanceNo", instance.getInstanceNo(),
                "status", instance.getStatus(),
                "message", "REJECTED".equals(instance.getStatus())
                        ? "已驳回，退回提交人" : "已驳回至上一节点"));
    }

    @Operation(summary = "撤回", description = "提交人在流程未完成前撤回，业务单据回到草稿")
    @PostMapping("/instances/{instanceId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(
            @PathVariable Long instanceId,
            @RequestBody(required = false) OpinionRequest body) {

        WfInstance instance = engine.cancel(instanceId,
                body == null ? null : body.opinion());
        return ApiResponse.ok(Map.of(
                "instanceNo", instance.getInstanceNo(),
                "status", instance.getStatus(),
                "message", "已撤回"));
    }

    // ---------- 查询 ----------

    @Operation(summary = "审批时间轴", description = "完整展示谁在什么时候做了什么——演示的核心视觉元素")
    @GetMapping("/instances/{instanceId}/timeline")
    public ApiResponse<List<TimelineItem>> timeline(@PathVariable Long instanceId) {
        return ApiResponse.ok(queryService.timeline(instanceId));
    }

    @Operation(summary = "实例详情")
    @GetMapping("/instances/{instanceId}")
    public ApiResponse<WfInstance> instance(@PathVariable Long instanceId) {
        return ApiResponse.ok(engine.getInstance(instanceId));
    }

    @Operation(summary = "业务单据的审批历史", description = "一个单据可能有多轮审批（被驳回后重新提交）")
    @GetMapping("/instances/history")
    public ApiResponse<List<WfInstance>> history(@RequestParam String bizType,
                                                 @RequestParam Long bizId) {
        return ApiResponse.ok(engine.historyOf(bizType, bizId));
    }

    @Operation(summary = "业务单据当前是否在审批中")
    @GetMapping("/instances/running")
    public ApiResponse<WfInstance> running(@RequestParam String bizType,
                                           @RequestParam Long bizId) {
        return ApiResponse.ok(engine.runningOf(bizType, bizId).orElse(null));
    }

    // ---------- 流程定义 ----------

    @Operation(summary = "全部审批流程定义", description = "展示 7 条审批链的节点与审批角色")
    @GetMapping("/definitions")
    public ApiResponse<List<WfDefinition>> definitions() {
        return ApiResponse.ok(definitionRepo.findByEnabledTrueOrderByIdAsc());
    }

    /** 审批意见请求体 */
    public record OpinionRequest(String opinion) {
    }
}
