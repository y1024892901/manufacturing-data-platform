package com.mfg.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 审批时间轴的一项。
 *
 * <p>演示时的核心视觉元素：一条时间轴把「谁在什么时候做了什么」
 * 完整呈现出来，比单纯一个"已通过"状态有说服力得多。
 */
@Schema(description = "审批时间轴项")
public record TimelineItem(

        @Schema(description = "操作：SUBMIT / APPROVE / REJECT / CANCEL")
        String action,

        @Schema(description = "操作的动作描述，如「提交审批」「同意」「驳回」")
        String actionLabel,

        @Schema(description = "节点名称")
        String nodeName,

        @Schema(description = "操作人账号")
        String operator,

        @Schema(description = "操作人姓名")
        String operatorName,

        @Schema(description = "审批意见")
        String opinion,

        @Schema(description = "操作时间")
        LocalDateTime operatedAt,

        @Schema(description = "该节点耗时（分钟）")
        Integer durationMin
) {
}
