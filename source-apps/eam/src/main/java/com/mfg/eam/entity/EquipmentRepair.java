package com.mfg.eam.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 维修是故障闭环的执行单，保留停机、备件和维修成本以供经营分析。 */
@Entity @Table(name = "eam_repair", catalog = "src_eam") @Getter @Setter @NoArgsConstructor
public class EquipmentRepair {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "repair_no") private String repairNo;
    @Column(name = "fault_no") private String faultNo;
    @Column(name = "equipment_code") private String equipmentCode;
    @Column(name = "repair_start_time") private LocalDateTime repairStartTime;
    @Column(name = "repair_end_time") private LocalDateTime repairEndTime;
    @Column(name = "downtime_minutes") private Integer downtimeMinutes = 0;
    @Column(name = "repair_type") private String repairType;
    @Column(name = "repair_content") private String repairContent;
    @Column(name = "replaced_parts") private String replacedParts;
    @Column(name = "repair_cost") private BigDecimal repairCost = BigDecimal.ZERO;
    @Column(name = "maintenance_hours") private BigDecimal maintenanceHours;
    @Column(name = "repairman_code") private String repairmanCode;
    @Column(name = "repair_result") private String repairResult;
    @Column(name = "remark") private String remark;
}
