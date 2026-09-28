package com.mfg.mes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mes_andon_event", catalog = "src_mes")
@Getter @Setter @NoArgsConstructor
public class AndonEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_no") private String eventNo;
    @Column(name = "event_type") private String eventType;
    @Column(name = "severity") private String severity;
    @Column(name = "work_order_no") private String workOrderNo;
    @Column(name = "prod_order_no") private String prodOrderNo;
    @Column(name = "equipment_code") private String equipmentCode;
    @Column(name = "workshop_code") private String workshopCode;
    @Column(name = "description") private String description;
    @Column(name = "affected_qty") private BigDecimal affectedQty;
    @Column(name = "downtime_minutes") private Integer downtimeMinutes;
    @Column(name = "response_due_at") private LocalDateTime responseDueAt;
    @Column(name = "reported_by") private String reportedBy;
    @Column(name = "reported_at") private LocalDateTime reportedAt;
    @Column(name = "response_by") private String responseBy;
    @Column(name = "response_at") private LocalDateTime responseAt;
    @Column(name = "response_note") private String responseNote;
    @Column(name = "resolution") private String resolution;
    @Column(name = "resolved_by") private String resolvedBy;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;
    @Column(name = "event_status") private String status = "OPEN";
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
