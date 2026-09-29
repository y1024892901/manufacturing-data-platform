package com.mfg.energy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 车间级能耗汇总，用于把单台设备读数提升为组织级节能管理。 */
@Entity @Table(name = "energy_workshop_usage", catalog = "src_energy") @Getter @Setter @NoArgsConstructor
public class WorkshopUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "workshop_code") private String workshopCode;
    @Column(name = "stat_date") private LocalDate statDate;
    @Column(name = "energy_type") private String energyType;
    @Column(name = "energy_value") private BigDecimal energyValue;
    @Column(name = "unit_code") private String unitCode;
    @Column(name = "total_output") private BigDecimal totalOutput;
    @Column(name = "unit_consumption") private BigDecimal unitConsumption;
    @Column(name = "shift_code") private String shiftCode;
    @Column(name = "cost_center_code") private String costCenterCode;
    @Column(name = "baseline_value", precision = 18, scale = 4) private BigDecimal baselineValue;
    @Column(name = "baseline_period_start_date") private LocalDate baselinePeriodStartDate;
    @Column(name = "baseline_period_end_date") private LocalDate baselinePeriodEndDate;
    @Column(name = "deviation_rate", precision = 8, scale = 4) private BigDecimal deviationRate;
    @Column(name = "warning_threshold_percent", precision = 8, scale = 4) private BigDecimal warningThresholdPercent;
    @Column(name = "is_abnormal", nullable = false) private Boolean abnormal = false;
    @Column(name = "remark", length = 1000) private String remark;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
