package com.mfg.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 审批任务 / 待办（mfg_auth.wf_task）。
 *
 * <p>「待办 3 条」就是这个表里 {@code taskStatus='PENDING'} 且
 * {@code approverRole} 属于当前用户的记录。
 *
 * <p>任务按<b>角色</b>创建而非按人：节点定义的是"工艺主管审核"，
 * 而不是"周涛审核"。谁登录进来领取，就归属谁（{@link #claimBy}）。
 * 这样人事变动时无需改流程配置。
 */
@Entity
@Table(name = "wf_task")
@Getter
@Setter
@NoArgsConstructor
public class WfTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "node_seq", nullable = false)
    private Integer nodeSeq;

    @Column(name = "node_name", nullable = false, length = 50)
    private String nodeName;

    @Column(name = "approver_role", nullable = false, length = 32)
    private String approverRole;
    @Column(name = "assigned_user", length = 32) private String assignedUser;
    @Column(name = "source_task_id") private Long sourceTaskId;
    @Column(name = "sign_mode", length = 16) private String signMode;

    /** 实际处理人（领取时写入） */
    @Column(name = "approver_user", length = 32)
    private String approverUser;

    /** PENDING / APPROVED / REJECTED / SKIPPED / CANCELED */
    @Column(name = "task_status", nullable = false, length = 16)
    private String taskStatus = "PENDING";

    @Column(name = "opinion", length = 500)
    private String opinion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_min")
    private Integer durationMin;

    // ---------- 便捷方法 ----------

    public boolean isPending() {
        return "PENDING".equals(taskStatus);
    }

    /** 领取任务：记录处理人 */
    public void claimBy(String username) {
        this.approverUser = username;
        if (this.claimedAt == null) {
            this.claimedAt = LocalDateTime.now();
        }
    }

    /** 完成节点：记录结果与耗时 */
    public void complete(String status, String opinion, String username) {
        this.taskStatus = status;
        this.opinion = opinion;
        if (username != null) {
            this.approverUser = username;
        }
        this.finishedAt = LocalDateTime.now();
        LocalDateTime from = claimedAt != null ? claimedAt : createdAt;
        this.durationMin = (int) Duration.between(from, finishedAt).toMinutes();
    }
}
