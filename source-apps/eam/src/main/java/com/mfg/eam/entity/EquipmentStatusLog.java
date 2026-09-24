package com.mfg.eam.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 设备状态时段台账：一条记录表示设备在 [start_time, end_time) 内处于某个状态，
 * {@code end_time} 为空表示仍在进行中。它是设备可用率与治理规则 E02（时段不可重叠）
 * 的唯一数据源。
 *
 * <p>此前该表有 DDL 却无实体、无 repo、无接口，因此状态切换不落台账（缺陷 #8）。
 * 补上映射后可查询设备的状态时段；写入点（状态切换时落台账）属计划 05 的范围。
 */
@Entity @Table(name = "eam_equipment_status_log", catalog = "src_eam") @Getter @Setter @NoArgsConstructor
public class EquipmentStatusLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "equipment_code") private String equipmentCode;
    /** RUNNING / IDLE / FAULT / MAINTENANCE。 */
    @Column(name = "status_code") private String statusCode;
    @Column(name = "start_time") private LocalDateTime startTime;
    /** 空 = 仍在进行中。 */
    @Column(name = "end_time") private LocalDateTime endTime;
    @Column(name = "duration_minutes") private Integer durationMinutes;
    @Column(name = "fault_no") private String faultNo;
    @Column(name = "work_order_no") private String workOrderNo;
    @Column(name = "remark") private String remark;
    @Column(name = "created_at") private LocalDateTime createdAt;
}
