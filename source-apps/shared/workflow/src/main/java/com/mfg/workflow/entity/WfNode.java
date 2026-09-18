package com.mfg.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 审批节点（mfg_auth.wf_node）。
 *
 * <p>一条审批链 = 同一 {@code definitionId} 下按 {@code nodeSeq} 排列的若干节点。
 * BOM 三级审批就是三个节点：10 工艺主管 → 20 生产主管 → 30 成本会计。
 */
@Entity
@Table(name = "wf_node")
@Getter
@Setter
@NoArgsConstructor
public class WfNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    /** 节点顺序：10 / 20 / 30（留间隔便于中间插节点） */
    @Column(name = "node_seq", nullable = false)
    private Integer nodeSeq;

    @Column(name = "node_name", nullable = false, length = 50)
    private String nodeName;

    /** 审批角色编码，如 PROCESS_SUPERVISOR */
    @Column(name = "approver_role", nullable = false, length = 32)
    private String approverRole;

    /**
     * 审批方式：
     * <ul>
     *   <li>{@code SINGLE} —— 单人审批（一人处理即通过）</li>
     *   <li>{@code ALL} —— 会签（该角色下所有人全部同意）</li>
     *   <li>{@code ANY} —— 或签（该角色下任一人同意即可）</li>
     * </ul>
     * 本项目 7 条链均用 {@code SINGLE}：一个角色对应一个人，
     * 任务由「角色」持有，任何人领取即归属自己。
     */
    @Column(name = "approve_mode", nullable = false, length = 16)
    private String approveMode = "SINGLE";

    @Column(name = "is_required", nullable = false)
    private Boolean required = true;

    /**
     * 驳回动作：
     * <ul>
     *   <li>{@code BACK} —— 退回提交人（流程结束，业务单据回到草稿）</li>
     *   <li>{@code PREV} —— 退上一节点（流程继续，回到前一审批人）</li>
     * </ul>
     */
    @Column(name = "reject_action", nullable = false, length = 16)
    private String rejectAction = "BACK";

    @Column(name = "timeout_hours")
    private Integer timeoutHours;

    /** REMIND / AUTO_PASS / AUTO_REJECT */
    @Column(name = "timeout_action", length = 16)
    private String timeoutAction;

    @Column(name = "remark", length = 200)
    private String remark;

    public boolean rejectToSubmitter() {
        return "BACK".equalsIgnoreCase(rejectAction);
    }
}
