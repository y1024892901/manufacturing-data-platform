package com.mfg.energy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "energy_meter", catalog = "src_energy")
@Getter
@Setter
@NoArgsConstructor
public class EnergyMeter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "meter_code", nullable = false, length = 32)
    private String meterCode;
    @Column(name = "meter_name", nullable = false, length = 120)
    private String meterName;
    @Column(name = "energy_type", nullable = false, length = 16)
    private String energyType;
    @Column(name = "unit_code", nullable = false, length = 16)
    private String unitCode;
    @Column(name = "meter_kind", nullable = false, length = 16)
    private String meterKind = "SUBMETER";
    @Column(name = "workshop_code", length = 32)
    private String workshopCode;
    @Column(name = "equipment_code", length = 32)
    private String equipmentCode;
    @Column(name = "parent_meter_code", length = 32)
    private String parentMeterCode;
    @Column(name = "install_location", length = 200)
    private String installLocation;
    @Column(precision = 12, scale = 4)
    private BigDecimal multiplier;
    @Column(name = "read_interval_minutes")
    private Integer readIntervalMinutes;
    @Column(name = "calibration_date")
    private LocalDate calibrationDate;
    @Column(name = "warning_threshold_percent", precision = 8, scale = 4)
    private BigDecimal warningThresholdPercent;
    @Column(name = "data_source", length = 80)
    private String dataSource;
    @Column(name = "responsible_person", length = 80)
    private String responsiblePerson;
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";
    @Column(length = 1000)
    private String remark;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
