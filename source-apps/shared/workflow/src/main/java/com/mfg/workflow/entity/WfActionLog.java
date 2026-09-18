package com.mfg.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审批操作日志（mfg_auth.wf_action_log）。
 *
 * <p>全程留痕：谁、在什么时候、对哪一步、做了什么、说了什么。
 * 前端「审批时间轴」组件直接读这张表渲染。
 */
@Entity
@Table(name = "wf_action_log")
@Getter
@Setter
@NoArgsConstructor
public class WfActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "node_seq")
    private Integer nodeSeq;

    /** SUBMIT / APPROVE / REJECT / TRANSFER / CANCEL / ADD_SIGN */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    @Column(name = "operator_name", length = 50)
    private String operatorName;

    @Column(name = "opinion", length = 500)
    private String opinion;

    @Column(name = "from_status", length = 16)
    private String fromStatus;

    @Column(name = "to_status", length = 16)
    private String toStatus;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt = LocalDateTime.now();

    public static WfActionLog of(Long instanceId, Long taskId, Integer nodeSeq,
                                 String action, String operator, String operatorName,
                                 String opinion, String fromStatus, String toStatus) {
        WfActionLog log = new WfActionLog();
        log.instanceId = instanceId;
        log.taskId = taskId;
        log.nodeSeq = nodeSeq;
        log.action = action;
        log.operator = operator;
        log.operatorName = operatorName;
        log.opinion = opinion;
        log.fromStatus = fromStatus;
        log.toStatus = toStatus;
        return log;
    }
}
