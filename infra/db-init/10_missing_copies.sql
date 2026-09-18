-- ============================================================
-- 10_missing_copies.sql —— 补齐遗漏的主数据只读副本表
--
-- 背景：04_business_systems.sql 建 ERP 的副本表时只建了
-- 客户/供应商/物料/成本中心，漏了 BOM。而 ERP 恰恰是
-- BOM 最主要的消费方（用它算料）。
--
-- 幂等: IF NOT EXISTS
-- ============================================================

SET NAMES utf8mb4;

USE src_erp;

-- BOM 头副本：ERP 用它确定"该产品当前用哪一版 BOM 算料"
CREATE TABLE IF NOT EXISTS erp_md_bom (
    bom_code          VARCHAR(32)  NOT NULL              COMMENT 'BOM 编码',
    bom_version       VARCHAR(16)  NOT NULL              COMMENT 'BOM 版本',
    product_code      VARCHAR(32)  NOT NULL              COMMENT '产品编码',
    is_current        TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否当前生效版本',
    base_qty          DECIMAL(18,4) NOT NULL DEFAULT 1   COMMENT '基准数量',
    master_version    INT          NOT NULL DEFAULT 1    COMMENT '来源主数据版本',
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (bom_code, bom_version),
    KEY idx_erp_bom_product (product_code),
    KEY idx_erp_bom_current (product_code, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】BOM 头（ERP 用它算料，BOM 变更后需重新展开）';

-- BOM 行副本：单位用量是算料的直接依据
CREATE TABLE IF NOT EXISTS erp_md_bom_line (
    bom_code          VARCHAR(32)  NOT NULL,
    bom_version       VARCHAR(16)  NOT NULL,
    line_no           INT          NOT NULL,
    child_material_code VARCHAR(32) NOT NULL             COMMENT '子件物料编码',
    qty_per           DECIMAL(18,6) NOT NULL             COMMENT '★ 单位用量',
    unit_code         VARCHAR(16)  NOT NULL,
    scrap_rate        DECIMAL(8,4) NOT NULL DEFAULT 0    COMMENT '损耗率',
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (bom_code, bom_version, line_no),
    KEY idx_erp_bomline_child (child_material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】BOM 行：qty_per 是物料需求计算的唯一依据';

-- ============================================================
-- 验证
-- ============================================================
SELECT TABLE_NAME AS 表, TABLE_COMMENT AS 说明
FROM information_schema.TABLES
WHERE TABLE_SCHEMA='src_erp' AND TABLE_NAME LIKE '%bom%'
ORDER BY TABLE_NAME;
