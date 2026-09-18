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
import java.util.ArrayList;
import java.util.List;

/**
 * BOM 主数据头（src_mdm.md_bom）。
 *
 * <p><b>本项目的演示核心。</b> BOM 之所以必须是主数据：
 * ERP 用它算料、MES 用它领料、成本用它算钱。若三方各存一份，
 * 必然导致「料算不准、领错料、成本失真」，且无人知道哪个是对的。
 *
 * <p>带版本与生效期：
 * <ul>
 *   <li>{@code bomVersion} —— V1.0 / V1.1，每次变更递增</li>
 *   <li>{@code isCurrent} —— 同一产品只有一个当前生效版本</li>
 *   <li>{@code effectiveDate} —— 生效日期，支持提前订版</li>
 * </ul>
 */
@Entity
@Table(name = "md_bom", catalog = "src_mdm")
@Getter
@Setter
@NoArgsConstructor
public class Bom implements MasterDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bom_code", nullable = false, length = 32)
    private String bomCode;

    @Column(name = "bom_name", nullable = false, length = 200)
    private String bomName;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_code", length = 32)
    private String productCode;

    @Column(name = "bom_version", nullable = false, length = 16)
    private String bomVersion = "V1.0";

    /** EBOM（工程，研发用）/ MBOM（制造，工艺用） */
    @Column(name = "bom_type", nullable = false, length = 16)
    private String bomType = "MBOM";

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate = LocalDate.now();

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(name = "is_current", nullable = false)
    private Boolean current = false;

    /** 基准数量：生产多少个母件。分母，影响用量计算 */
    @Column(name = "base_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal baseQty = BigDecimal.ONE;

    @Column(name = "base_unit_code", length = 16)
    private String baseUnitCode;

    // ---------- 治理（BOM 变更走三级审批）----------

    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    /** BOM 变更必须填写原因——引擎层已强制校验 */
    @Column(name = "change_reason", length = 300)
    private String changeReason;

    /** 关联的工程变更单号（来自 PLM） */
    @Column(name = "change_ecn_no", length = 32)
    private String changeEcnNo;

    @Column(name = "approved_by", length = 32)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", length = 32)
    private String updatedBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "bomId", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("lineNo ASC")
    private List<BomLine> lines = new ArrayList<>();

    @Override
    public String getBusinessCode() {
        return bomCode;
    }

    @Override
    public String getName() {
        return bomName;
    }

    @Override
    public BizType bizType() {
        return BizType.BOM;
    }

    /** 发布时额外记录审批人与时间 */
    @Override
    public void publish(String operator) {
        MasterDataEntity.super.publish(operator);
        this.approvedBy = operator;
        this.approvedAt = LocalDateTime.now();
        this.current = true;
    }

    /** 是否处于可被业务系统引用的状态 */
    @Transient
    public boolean isUsable() {
        return current && statusEnum().isConsumable();
    }
}
