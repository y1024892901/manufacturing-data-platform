package com.mfg.energy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "energy_alert", catalog = "src_energy")
@Getter
@Setter
@NoArgsConstructor
public class EnergyAlert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "alert_no", nullable = false, length = 32)
    private String alertNo;
    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;
    @Column(name = "source_id")
    private Long sourceId;
    @Column(name = "object_code", nullable = false, length = 32)
    private String objectCode;
    @Column(name = "energy_type", nullable = false, length = 16)
    private String energyType;
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;
    @Column(name = "actual_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal actualValue;
    @Column(name = "threshold_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal thresholdValue;
    @Column(name = "deviation_rate", precision = 8, scale = 4)
    private BigDecimal deviationRate;
    @Column(nullable = false, length = 16)
    private String severity = "MEDIUM";
    @Column(nullable = false, length = 16)
    private String status = "OPEN";
    @Column(name = "assigned_to", length = 80)
    private String assignedTo;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(length = 1000)
    private String cause;
    @Column(name = "corrective_action", length = 1000)
    private String correctiveAction;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
