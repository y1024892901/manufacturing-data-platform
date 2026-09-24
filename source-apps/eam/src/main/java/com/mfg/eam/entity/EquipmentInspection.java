package com.mfg.eam.entity;import jakarta.persistence.*;import lombok.*;import java.time.*;
@Entity @Table(name="eam_inspection",catalog="src_eam") @Getter @Setter @NoArgsConstructor public class EquipmentInspection{@Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(name="inspection_no")private String inspectionNo;@Column(name="equipment_code")private String equipmentCode;@Column(name="inspection_type")private String inspectionType;@Column(name="plan_date")private LocalDate planDate;@Column(name="actual_date")private LocalDate actualDate;@Column(name="inspection_result")private String result;@Column(name="abnormal_desc")private String abnormalDesc;@Column(name="inspector_code")private String inspectorCode;@Column(name="is_missed")private Boolean missed=false;
// check_items 在 DDL 中是 JSON 列，按 String 映射：读写的是原始 JSON 文本，不做结构化解析（避免为一列引入 JSON 类型绑定）
@Column(name="check_items")private String checkItems;
@Column(name="created_at")private LocalDateTime createdAt;}
