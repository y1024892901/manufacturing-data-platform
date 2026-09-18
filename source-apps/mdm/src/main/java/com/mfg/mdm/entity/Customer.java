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
 * 客户主数据（src_mdm.md_customer）。
 *
 * <p>本类的表名写法值得注意：{@code catalog = "src_mdm"}。
 * 应用主数据源连的是 {@code mfg_auth}，但通过 MySQL 的跨库限定名
 * （{@code src_mdm.md_customer}）直接访问主数据库，
 * 无需配置多数据源。这是本项目在「一个 MySQL 实例、18 个库」
 * 架构下最省事的方案。
 */
@Entity
@Table(name = "md_customer", catalog = "src_mdm")
@Getter
@Setter
@NoArgsConstructor
public class Customer implements MasterDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客户编码（全公司唯一，CRM 与 ERP 共用同一个） */
    @Column(name = "customer_code", nullable = false, length = 32)
    private String customerCode;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "short_name", length = 100)
    private String shortName;

    /** 统一社会信用代码——用于「一客多码」识别 */
    @Column(name = "unified_social_code", length = 32)
    private String unifiedSocialCode;

    /** 等级：A/B/C/D */
    @Column(name = "customer_level", length = 16)
    private String customerLevel;

    /** 类型：DIRECT（直客）/ DEALER（经销商）/ AGENT（代理） */
    @Column(name = "customer_type", length = 16)
    private String customerType;

    @Column(name = "industry", length = 50)
    private String industry;

    @Column(name = "region", length = 50)
    private String region;

    // ---------- 信用与结算（财务审批关注点）----------

    @Column(name = "credit_limit", nullable = false, precision = 18, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "credit_used", nullable = false, precision = 18, scale = 2)
    private BigDecimal creditUsed = BigDecimal.ZERO;

    /** 账期：NET30 / NET60 / 预付 */
    @Column(name = "payment_terms", length = 32)
    private String paymentTerms;

    @Column(name = "tax_no", length = 32)
    private String taxNo;

    // ---------- 联系方式（标记为敏感，返回时脱敏）----------

    @Column(name = "contact_person", length = 50)
    private String contactPerson;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "address", length = 300)
    private String address;

    // ---------- 主数据治理字段 ----------

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

    // ---------- 契约实现 ----------

    @Override
    public String getBusinessCode() {
        return customerCode;
    }

    @Override
    public String getName() {
        return customerName;
    }

    @Override
    public BizType bizType() {
        return BizType.CUSTOMER;
    }

    /** 信用可用额度 */
    @Transient
    public BigDecimal getAvailableCredit() {
        BigDecimal limit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
        BigDecimal used = creditUsed == null ? BigDecimal.ZERO : creditUsed;
        return limit.subtract(used);
    }
}
