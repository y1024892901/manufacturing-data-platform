package com.mfg.mes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 每次报工的原始明细，支撑班次、设备、停机与产能分析。 */
@Entity @Table(name = "mes_work_report", catalog = "src_mes") @Getter @Setter @NoArgsConstructor
public class WorkReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "report_no") private String reportNo;
    @Column(name = "work_order_no") private String workOrderNo;
    @Column(name = "prod_order_no") private String prodOrderNo;
    @Column(name = "op_seq") private Integer opSeq;
    @Column(name = "report_qty") private BigDecimal reportQty;
    @Column(name = "qualified_qty") private BigDecimal qualifiedQty;
    @Column(name = "scrap_qty") private BigDecimal scrapQty;
    @Column(name = "report_date") private LocalDate reportDate;
    @Column(name = "start_time") private LocalDateTime startTime;
    @Column(name = "end_time") private LocalDateTime endTime;
    @Column(name = "work_hours") private BigDecimal workHours;
    @Column(name = "equipment_code") private String equipmentCode;
    @Column(name = "operator_code") private String operatorCode;
    @Column(name = "shift_code") private String shiftCode;
    @Column(name = "is_paused") private Boolean paused = false;
    @Column(name = "pause_reason") private String pauseReason;
    @Column(name = "pause_minutes") private Integer pauseMinutes = 0;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at") private LocalDateTime createdAt;
}
