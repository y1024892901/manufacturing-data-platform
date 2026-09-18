package com.mfg.mdm.entity;

import com.mfg.mdm.domain.BizType;
import com.mfg.mdm.domain.MasterDataEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物料分类（src_mdm.md_material_category）。
 *
 * <p>树形结构，供分析按分类聚合。分类口径不统一会导致
 * 「同一个物料在这个报表里是原材料，在那个报表里是外购件」。
 */
@Entity
@Table(name = "md_material_category", catalog = "src_mdm")
@Getter
@Setter
@NoArgsConstructor
public class MaterialCategory implements MasterDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_code", nullable = false, length = 32)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "category_level", nullable = false)
    private Integer categoryLevel = 1;

    /** 只有末级分类可挂物料 */
    @Column(name = "is_leaf", nullable = false)
    private Boolean isLeaf = true;

    /** 全路径，如「原材料/金属/钢材」，便于展示与检索 */
    @Column(name = "category_path", length = 500)
    private String categoryPath;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PUBLISHED";

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
        return categoryCode;
    }

    @Override
    public String getName() {
        return categoryName;
    }

    @Override
    public BizType bizType() {
        return BizType.MATERIAL_CATEGORY;
    }
}
