package com.mfg.mdm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="md_routing_operation",catalog="src_mdm") @Getter @Setter @NoArgsConstructor
public class RoutingOperation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="routing_id",nullable=false) private Long routingId;
    @Column(name="op_seq",nullable=false) private Integer opSeq;
    @Column(name="operation_code",nullable=false,length=32) private String operationCode;
    @Column(name="operation_name",nullable=false,length=100) private String operationName;
    @Column(name="work_center",length=50) private String workCenter;
    @Column(name="setup_time_min",nullable=false,precision=10,scale=2) private BigDecimal setupTimeMin=BigDecimal.ZERO;
    @Column(name="run_time_min",nullable=false,precision=10,scale=4) private BigDecimal runTimeMin=BigDecimal.ZERO;
    @Column(name="wait_time_min",nullable=false,precision=10,scale=2) private BigDecimal waitTimeMin=BigDecimal.ZERO;
    @Column(name="default_equipment_code",length=32) private String defaultEquipmentCode;
    @Column(name="required_skill",length=50) private String requiredSkill;
    @Column(name="is_key_operation",nullable=false) private Boolean keyOperation=false;
    @Column(name="is_inspection_op",nullable=false) private Boolean inspectionOp=false;
    @Column(name="inspection_required",nullable=false) private Boolean inspectionRequired=false;
    @Column(name="remark",length=300) private String remark;
    @Column(name="created_at",insertable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",insertable=false,updatable=false) private LocalDateTime updatedAt;
}
