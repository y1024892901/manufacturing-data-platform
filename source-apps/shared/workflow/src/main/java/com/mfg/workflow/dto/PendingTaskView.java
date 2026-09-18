package com.mfg.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 待办视图 —— 前端「我的待办」列表的一行。
 *
 * <p>把任务与流程实例的信息拼在一起，前端一次拿到渲染所需全部字段，
 * 不用再按 instanceId 二次请求。
 */
@Schema(description = "待办任务")
public record PendingTaskView(

        @Schema(description = "任务 ID（审批时提交此 ID）")
        Long taskId,

        @Schema(description = "流程实例 ID")
        Long instanceId,

        @Schema(description = "流程实例号")
        String instanceNo,

        @Schema(description = "业务类型")
        String bizType,

        @Schema(description = "业务单据 ID（可据此跳转到业务详情页）")
        Long bizId,

        @Schema(description = "业务单号")
        String bizNo,

        @Schema(description = "业务标题")
        String bizTitle,

        @Schema(description = "提交时的数据快照（JSON）")
        String bizSnapshot,

        @Schema(description = "提交人账号")
        String submitter,

        @Schema(description = "提交时间")
        LocalDateTime submittedAt,

        @Schema(description = "等待时长（小时），用于展示「已等待 X 小时」")
        Long waitingHours,

        @Schema(description = "当前节点顺序")
        Integer nodeSeq,

        @Schema(description = "节点名称，如「工艺主管审核」")
        String nodeName,

        @Schema(description = "需要哪个角色审批")
        String approverRole
) {
}
