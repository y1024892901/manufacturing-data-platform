package com.mfg.mdm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BOM 行 —— 用料明细（src_mdm.md_bom_line）。
 *
 * <p>{@code qtyPer}（单位用量）是<b>齐套率与用料需求计算的唯一依据</b>。
 * 演示中「用量 3 改成 4」这个变更，改的就是这个字段，
 * 它会一路传导到订单齐套率与延期风险。
 */
@Entity
@Table(name = "md_bom_line", catalog = "src_mdm")
@Getter
@Setter
@NoArgsConstructor
public class BomLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bom_id", nullable = false)
    private Long bomId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "child_material_id")
    private Long childMaterialId;

    @Column(name = "child_material_code", nullable = false, length = 32)
    private String childMaterialCode;

    @Column(name = "child_material_name", length = 200)
    private String childMaterialName;

    /** ★ 单位用量：生产 1 个母件需要多少子件 */
    @Column(name = "qty_per", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyPer;

    @Column(name = "unit_code", nullable = false, length = 16)
    private String unitCode;

    /** 损耗率（%），实际需求 = 用量 × (1 + 损耗率) */
    @Column(name = "scrap_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal scrapRate = BigDecimal.ZERO;

    @Column(name = "seq_no", nullable = false)
    private Integer seqNo = 1;

    /** 关键料：齐套分析优先关注 */
    @Column(name = "is_key_material", nullable = false)
    private Boolean keyMaterial = false;

    @Column(name = "position_desc", length = 200)
    private String positionDesc;

    /** 替代料组：同组内物料可互相替代 */
    @Column(name = "substitute_group", length = 32)
    private String substituteGroup;

    @Column(name = "remark", length = 300)
    private String remark;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /**
     * 含损耗的实际单位用量。
     *
     * <p>齐套与用料需求计算必须用这个值，而非裸的 qtyPer。
     * 忽略损耗率会导致备料不足。
     */
    @Transient
    public BigDecimal getEffectiveQtyPer() {
        BigDecimal qty = qtyPer == null ? BigDecimal.ZERO : qtyPer;
        BigDecimal scrap = scrapRate == null ? BigDecimal.ZERO : scrapRate;
        return qty.multiply(BigDecimal.ONE.add(
                scrap.divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP)));
    }
}
