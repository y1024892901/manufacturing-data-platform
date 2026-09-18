package com.mfg.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 审批流程实例（mfg_auth.wf_instance）。
 *
 * <p>一次审批走下来就是一条实例记录，记录谁提交的、当前在哪一步、最终结果。
 */
@Entity
@Table(name = "wf_instance")
@Getter
@Setter
@NoArgsConstructor
public class WfInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 实例号，如 WF-20260918-0001 */
    @Column(name = "instance_no", nullable = false, length = 32)
    private String instanceNo;

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    /** 业务单据 ID（如主数据记录的 id） */
    @Column(name = "biz_id", nullable = false)
    private Long bizId;

    /** 业务单号（如物料编码、BOM 编码），便于人读 */
    @Column(name = "biz_no", length = 64)
    private String bizNo;

    @Column(name = "biz_title", length = 200)
    private String bizTitle;

    /**
     * 提交时的业务数据快照（JSON）。
     *
     * <p>关键设计：审批人看到的是<b>提交那一刻</b>的数据，
     * 而不是当前值。否则一旦提交后又改了数据，审批人会看到
     * 与提交时不一致的内容，审批意见就失去意义了。
     */
    @Column(name = "biz_snapshot", columnDefinition = "json")
    private String bizSnapshot;

    @Column(name = "current_node_seq")
    private Integer currentNodeSeq;

    /** RUNNING / APPROVED / REJECTED / CANCELED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "RUNNING";

    @Column(name = "submitter", nullable = false, length = 32)
    private String submitter;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "total_duration_min")
    private Integer totalDurationMin;

    // ---------- 便捷方法 ----------

    public boolean isRunning() {
        return "RUNNING".equals(status);
    }

    public boolean isApproved() {
        return "APPROVED".equals(status);
    }

    /** 流程结束时结算耗时 */
    public void finish(String finalStatus) {
        this.status = finalStatus;
        this.finishedAt = LocalDateTime.now();
        if (submittedAt != null) {
            this.totalDurationMin = (int) Duration.between(submittedAt, finishedAt).toMinutes();
        }
        this.currentNodeSeq = null;
    }
}
