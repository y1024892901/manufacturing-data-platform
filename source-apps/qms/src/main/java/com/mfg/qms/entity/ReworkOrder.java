package com.mfg.qms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 不合格品返工单，把质量问题对产能与交期的影响显式记录。 */
@Entity @Table(name = "qms_rework", catalog = "src_qms") @Getter @Setter @NoArgsConstructor
public class ReworkOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "rework_no") private String reworkNo;
    @Column(name = "defect_no") private String defectNo;
    @Column(name = "prod_order_no") private String prodOrderNo;
    @Column(name = "work_order_no") private String workOrderNo;
    @Column(name = "material_code") private String materialCode;
    @Column(name = "rework_qty") private BigDecimal reworkQty;
    @Column(name = "rework_hours") private BigDecimal reworkHours;
    @Column(name = "delay_days") private Integer delayDays = 0;
    @Column(name = "rework_status") private String status = "PENDING";
    @Column(name = "start_time") private LocalDateTime startTime;
    @Column(name = "end_time") private LocalDateTime endTime;
    @Column(name = "created_at") private LocalDateTime createdAt;
}
