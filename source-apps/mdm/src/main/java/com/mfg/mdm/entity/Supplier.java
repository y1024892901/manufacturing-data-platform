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

/**
 * 供应商主数据（src_mdm.md_supplier）。
 *
 * <p>{@code leadTimeDays}（交货提前期）是<b>齐套分析的关键输入</b>：
 * 物料需求日期减去提前期，才是采购单最晚下达时间。
 */
@Entity
@Table(name = "md_supplier", catalog = "src_mdm")
@Getter
@Setter
@NoArgsConstructor
public class Supplier implements MasterDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_code", nullable = false, length = 32)
    private String supplierCode;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Column(name = "short_name", length = 100)
    private String shortName;

    @Column(name = "unified_social_code", length = 32)
    private String unifiedSocialCode;

    /** 等级：A/B/C/D，影响采购配额分配 */
    @Column(name = "supplier_level", length = 16)
    private String supplierLevel;

    /** MATERIAL（物料）/ SERVICE（服务）/ OUTSOURCE（外协） */
    @Column(name = "supplier_type", length = 16)
    private String supplierType;

    /** ★ 标准交货提前期（天）——齐套与到货偏差计算的基准 */
    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays = 0;

    @Column(name = "supply_category", length = 100)
    private String supplyCategory;

    // ---------- 资质（质量工程师审批关注点）----------

    /** VALID / EXPIRING / EXPIRED */
    @Column(name = "qual_status", length = 16)
    private String qualStatus;

    @Column(name = "qual_expire_date")
    private LocalDate qualExpireDate;

    // ---------- 财务 ----------

    @Column(name = "payment_terms", length = 32)
    private String paymentTerms;

    @Column(name = "bank_account", length = 64)
    private String bankAccount;

    @Column(name = "tax_no", length = 32)
    private String taxNo;

    // ---------- 绩效（由数仓回写）----------

    @Column(name = "on_time_rate", precision = 5, scale = 4)
    private BigDecimal onTimeRate;

    @Column(name = "quality_pass_rate", precision = 5, scale = 4)
    private BigDecimal qualityPassRate;

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
        return supplierCode;
    }

    @Override
    public String getName() {
        return supplierName;
    }

    @Override
    public BizType bizType() {
        return BizType.SUPPLIER;
    }

    /** 资质是否即将到期（30 天内），演示时用于提示 */
    @Transient
    public boolean isQualExpiringSoon() {
        return qualExpireDate != null
                && !qualExpireDate.isAfter(LocalDate.now().plusDays(30));
    }
}
