package com.mfg.mdm.entity;

import com.mfg.mdm.domain.BizType;
import com.mfg.mdm.domain.MasterDataEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料主数据（src_mdm.md_material）。
 *
 * <p><b>9 个业务系统共用同一套物料编码</b>——这是本平台解决
 * "物料编码不一致"这类制造业顽疾的机制。
 *
 * <p>{@code isInspectionRequired}（是否需检验）与 {@code inspectionStandard}
 * 会被分发到 QMS，作为来料检验的判定依据。
 */
@Entity
@Table(name = "md_material", catalog = "src_mdm")
@Getter
@Setter
@NoArgsConstructor
public class Material implements MasterDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 物料编码（9 个系统共用） */
    @Column(name = "material_code", nullable = false, length = 32)
    private String materialCode;

    @Column(name = "material_name", nullable = false, length = 200)
    private String materialName;

    @Column(name = "material_spec", length = 200)
    private String materialSpec;

    @Column(name = "spec_desc", length = 500)
    private String specDesc;

    /** RAW（原材料）/ SEMI（半成品）/ FINISHED（成品）/ SPARE（备件）/ PACKAGING（包装） */
    @Column(name = "material_type", nullable = false, length = 16)
    private String materialType;

    @Column(name = "category_id")
    private Long categoryId;

    // ---------- 计量（两套单位，制造业的关键）----------

    @Column(name = "base_unit_code", nullable = false, length = 16)
    private String baseUnitCode;

    /** 采购单位可能与基本单位不同（如按「箱」采购，按「个」领用） */
    @Column(name = "purchase_unit_code", length = 16)
    private String purchaseUnitCode;

    /** 采购单位对基本单位的换算率。换算错误会导致数量级灾难 */
    @Column(name = "conversion_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal conversionRate = BigDecimal.ONE;

    // ---------- 计划与库存 ----------

    @Column(name = "safety_stock", nullable = false, precision = 18, scale = 4)
    private BigDecimal safetyStock = BigDecimal.ZERO;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "is_batch_managed", nullable = false)
    private Boolean batchManaged = false;

    // ---------- 采购与成本 ----------

    @Column(name = "default_supplier_id")
    private Long defaultSupplierId;

    @Column(name = "standard_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal standardPrice = BigDecimal.ZERO;

    // ---------- 质量 ----------

    @Column(name = "is_inspection_required", nullable = false)
    private Boolean inspectionRequired = true;

    @Column(name = "inspection_standard", length = 500)
    private String inspectionStandard;

    // ---------- 治理 ----------

    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "change_reason", length = 300)
    private String changeReason;

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", length = 32)
    private String updatedBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Override
    public String getBusinessCode() {
        return materialCode;
    }

    @Override
    public String getName() {
        return materialName;
    }

    @Override
    public BizType bizType() {
        return BizType.MATERIAL;
    }

    /** 计量单位中文名（演示展示用） */
    @Transient
    public String getUnitLabel() {
        if (baseUnitCode == null) {
            return "";
        }
        return switch (baseUnitCode.toUpperCase()) {
            case "PCS" -> "个";
            case "KG" -> "千克";
            case "M" -> "米";
            case "M2" -> "平方米";
            case "L" -> "升";
            case "SET" -> "套";
            case "BOX" -> "箱";
            default -> baseUnitCode;
        };
    }
}
