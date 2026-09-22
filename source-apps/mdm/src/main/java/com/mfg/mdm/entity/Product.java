package com.mfg.mdm.entity;

import com.mfg.mdm.domain.BizType;
import com.mfg.mdm.domain.MasterDataEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "md_product", catalog = "src_mdm")
@Getter @Setter @NoArgsConstructor
public class Product implements MasterDataEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "product_code", nullable = false, length = 32) private String productCode;
    @Column(name = "product_name", nullable = false, length = 200) private String productName;
    @Column(name = "product_model", length = 100) private String productModel;
    @Column(name = "material_id") private Long materialId;
    @Column(name = "category_id") private Long categoryId;
    @Column(name = "unit_code", nullable = false, length = 16) private String unitCode = "PCS";
    @Column(name = "weight_kg", precision = 18, scale = 4) private BigDecimal weightKg;
    @Column(name = "lifecycle_status", length = 16) private String lifecycleStatus = "DESIGN";
    @Column(name = "launch_date") private LocalDate launchDate;
    @Column(name = "eol_date") private LocalDate eolDate;
    @Column(name = "status", nullable = false, length = 16) private String status = "DRAFT";
    @Column(name = "version_no", nullable = false) private Integer versionNo = 1;
    @Column(name = "change_reason", length = 300) private String changeReason;
    @Column(name = "created_by", length = 32) private String createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by", length = 32) private String updatedBy;
    @Column(name = "updated_at", insertable = false, updatable = false) private LocalDateTime updatedAt;

    @Override public String getBusinessCode() { return productCode; }
    @Override public String getName() { return productName; }
    @Override public BizType bizType() { return BizType.PRODUCT; }
}
