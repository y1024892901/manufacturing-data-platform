package com.mfg.plm.entity;
import jakarta.persistence.*;
import lombok.Getter;import lombok.NoArgsConstructor;import lombok.Setter;
import java.time.LocalDate;import java.time.LocalDateTime;
/** PLM 专属工程变更单；只描述变更，不直接修改 MDM BOM。 */
@Entity @Table(name="plm_ecn",catalog="src_plm") @Getter @Setter @NoArgsConstructor public class EngineeringChange{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="ecn_no")private String ecnNo; @Column(name="ecn_title")private String ecnTitle; @Column(name="product_code")private String productCode; @Column(name="bom_code")private String bomCode;
 @Column(name="change_type")private String changeType; @Column(name="change_content")private String changeContent; @Column(name="change_reason")private String changeReason; @Column(name="ecn_status")private String status="DRAFT";
 @Column(name="effective_date")private LocalDate effectiveDate; @Column(name="submitted_by")private String submittedBy; @Column(name="approved_by")private String approvedBy; @Column(name="approved_at")private LocalDateTime approvedAt;
 @Column(name="ecr_id")private Long ecrId; @Column(name="eco_id")private Long ecoId; @Column(name="target_type")private String targetType; @Column(name="target_version")private String targetVersion;
 @Column(name="created_at",insertable=false,updatable=false)private LocalDateTime createdAt; @Column(name="updated_at",insertable=false,updatable=false)private LocalDateTime updatedAt;
}
