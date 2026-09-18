-- ============================================================
-- 04_business_systems.sql —— 9 类业务系统表
--
-- ★ 核心设计：业务系统不维护主数据，只持有「只读副本」
--
--   每个业务库里都有 *_md_* 副本表，它们:
--     ① 由 MDM 分发写入，业务系统内不可编辑
--     ② 带 master_version 字段，可校验"副本是否落后于权威源"
--     ③ 业务单据通过 material_code / customer_code 引用它们
--
--   这解决了「一客多码」「物料编码不一致」这类制造业顽疾，
--   也是数仓治理规则 F03（主数据一致性）的检测对象。
--
-- 幂等: 全部使用 IF NOT EXISTS
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 一、CRM —— 客户关系管理
--     消费主数据: 客户
-- ============================================================
USE src_crm;

-- 主数据只读副本：客户
CREATE TABLE IF NOT EXISTS crm_md_customer (
    customer_code     VARCHAR(32)  NOT NULL              COMMENT '客户编码（来自 MDM）',
    customer_name     VARCHAR(200) NOT NULL,
    short_name        VARCHAR(100) NULL,
    customer_level    VARCHAR(16)  NULL                  COMMENT '等级 A/B/C/D',
    region            VARCHAR(50)  NULL,
    credit_limit      DECIMAL(18,2) NOT NULL DEFAULT 0,
    payment_terms     VARCHAR(32)  NULL,
    contact_person    VARCHAR(50)  NULL,
    contact_phone     VARCHAR(30)  NULL,
    master_version    INT          NOT NULL DEFAULT 1    COMMENT '来源主数据版本',
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (customer_code),
    KEY idx_crm_cust_name (customer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】客户主数据，由 MDM 分发，CRM 内不可编辑';

-- 销售商机
CREATE TABLE IF NOT EXISTS crm_opportunity (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    opportunity_no    VARCHAR(32)  NOT NULL              COMMENT '商机编号: OPP-20260901-001',
    opportunity_name  VARCHAR(200) NOT NULL              COMMENT '商机名称',
    customer_code     VARCHAR(32)  NOT NULL              COMMENT '客户（必须先存在于 MDM）',
    customer_name     VARCHAR(200) NULL,
    -- 金额与时间
    expect_amount     DECIMAL(18,2) NOT NULL DEFAULT 0   COMMENT '预计金额',
    expect_sign_date  DATE         NULL                  COMMENT '预计签约日期',
    -- 阶段流转（演示"推进阶段"用）
    stage_code        VARCHAR(16)  NOT NULL DEFAULT 'LEAD'
                                COMMENT 'LEAD=线索 / QUALIFY=资格确认 / PROPOSAL=方案 / NEGOTIATE=谈判 / WON=赢单 / LOST=输单',
    stage_updated_at  DATETIME(3)  NULL,
    win_rate          DECIMAL(5,2) NULL                  COMMENT '赢率(%)',
    -- 转化结果
    converted_order_no VARCHAR(32) NULL                  COMMENT '转化后的销售订单号（转化动作的回填）',
    converted_at      DATETIME(3)  NULL,
    -- 审计
    owner_user        VARCHAR(32)  NOT NULL              COMMENT '负责人（数据范围过滤字段）',
    owner_name        VARCHAR(50)  NULL,
    dept_code         VARCHAR(32)  NULL                  COMMENT '所属部门',
    remark            VARCHAR(500) NULL,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_opp_no (opportunity_no),
    KEY idx_opp_customer (customer_code),
    KEY idx_opp_stage (stage_code),
    KEY idx_opp_owner (owner_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='销售商机（转化后进入 ERP 生成销售订单）';

-- 商机跟进记录
CREATE TABLE IF NOT EXISTS crm_opportunity_follow (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    opportunity_no    VARCHAR(32)  NOT NULL,
    follow_type       VARCHAR(16)  NOT NULL              COMMENT 'CALL/VISIT/EMAIL/MEETING',
    follow_content    VARCHAR(1000) NULL,
    follow_user       VARCHAR(32)  NOT NULL,
    follow_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_follow_opp (opportunity_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='商机跟进记录';

-- ============================================================
-- 二、ERP —— 企业资源计划
--     消费主数据: 客户 / 供应商 / 物料 / BOM / 成本中心 / 科目
-- ============================================================
USE src_erp;

CREATE TABLE IF NOT EXISTS erp_md_customer (
    customer_code     VARCHAR(32)  NOT NULL,
    customer_name     VARCHAR(200) NOT NULL,
    credit_limit      DECIMAL(18,2) NOT NULL DEFAULT 0,
    credit_used       DECIMAL(18,2) NOT NULL DEFAULT 0,
    payment_terms     VARCHAR(32)  NULL,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (customer_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】客户主数据';

CREATE TABLE IF NOT EXISTS erp_md_supplier (
    supplier_code     VARCHAR(32)  NOT NULL,
    supplier_name     VARCHAR(200) NOT NULL,
    lead_time_days    INT          NOT NULL DEFAULT 0    COMMENT '交货提前期',
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (supplier_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】供应商主数据';

CREATE TABLE IF NOT EXISTS erp_md_material (
    material_code     VARCHAR(32)  NOT NULL,
    material_name     VARCHAR(200) NOT NULL,
    material_spec     VARCHAR(200) NULL,
    material_type     VARCHAR(16)  NOT NULL,
    base_unit_code    VARCHAR(16)  NOT NULL,
    standard_price    DECIMAL(18,4) NOT NULL DEFAULT 0,
    safety_stock      DECIMAL(18,4) NOT NULL DEFAULT 0,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (material_code),
    KEY idx_erp_mat_type (material_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】物料主数据';

CREATE TABLE IF NOT EXISTS erp_md_cost_center (
    cc_code           VARCHAR(32)  NOT NULL,
    cc_name           VARCHAR(100) NOT NULL,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (cc_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】成本中心';

-- 销售订单
CREATE TABLE IF NOT EXISTS erp_sales_order (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    sales_order_no    VARCHAR(32)  NOT NULL              COMMENT '销售订单号: SO-20260901-001',
    customer_code     VARCHAR(32)  NOT NULL,
    customer_name     VARCHAR(200) NULL,
    order_date        DATE         NOT NULL              COMMENT '下单日期',
    delivery_date     DATE         NOT NULL              COMMENT '要求交付日期',
    -- 金额
    total_amount      DECIMAL(18,2) NOT NULL DEFAULT 0   COMMENT '订单金额',
    tax_amount        DECIMAL(18,2) NOT NULL DEFAULT 0,
    currency          VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    -- 状态
    order_status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                                COMMENT 'DRAFT=草稿 / PENDING=待审批 / CONFIRMED=已确认 / IN_PROD=生产中 / DELIVERED=已交付 / CLOSED=已关闭 / CANCELED=已取消',
    source_opportunity_no VARCHAR(32) NULL               COMMENT '来源商机（CRM 转化）',
    -- 审计
    sales_user        VARCHAR(32)  NOT NULL              COMMENT '销售负责人（数据范围）',
    dept_code         VARCHAR(32)  NULL,
    remark            VARCHAR(500) NULL,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_so_no (sales_order_no),
    KEY idx_so_customer (customer_code),
    KEY idx_so_status (order_status),
    KEY idx_so_delivery (delivery_date),
    KEY idx_so_user (sales_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='销售订单（ERP 的核心单据）';

-- 销售订单行
CREATE TABLE IF NOT EXISTS erp_sales_order_line (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    sales_order_no    VARCHAR(32)  NOT NULL,
    line_no           INT          NOT NULL,
    material_code     VARCHAR(32)  NOT NULL              COMMENT '产品物料编码',
    material_name     VARCHAR(200) NULL,
    order_qty         DECIMAL(18,4) NOT NULL             COMMENT '订单数量',
    unit_code         VARCHAR(16)  NOT NULL,
    unit_price        DECIMAL(18,4) NOT NULL DEFAULT 0,
    line_amount       DECIMAL(18,2) NOT NULL DEFAULT 0,
    delivered_qty     DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '已交付数量',
    delivery_date     DATE         NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sol (sales_order_no, line_no),
    KEY idx_sol_so (sales_order_no),
    KEY idx_sol_material (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='销售订单行';

-- 生产订单（★ 全项目的分析核心对象）
CREATE TABLE IF NOT EXISTS erp_prod_order (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    prod_order_no     VARCHAR(32)  NOT NULL              COMMENT '生产订单号: PO-20260901-001',
    sales_order_no    VARCHAR(32)  NULL                  COMMENT '关联销售订单',
    product_code      VARCHAR(32)  NOT NULL              COMMENT '产品编码',
    product_name      VARCHAR(200) NULL,
    -- 数量（治理规则 P02 的检测对象）
    plan_qty          DECIMAL(18,4) NOT NULL             COMMENT '计划数量',
    completed_qty     DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '完工数量',
    qualified_qty     DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '合格数量',
    scrap_qty         DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '报废数量',
    unit_code         VARCHAR(16)  NOT NULL,
    -- 日期（治理规则 P03 的检测对象）
    plan_start_date   DATE         NOT NULL              COMMENT '计划开工日期',
    plan_finish_date  DATE         NOT NULL              COMMENT '计划完工日期',
    actual_start_date DATE         NULL                  COMMENT '实际开工日期',
    actual_finish_date DATE        NULL                  COMMENT '实际完工日期',
    -- 状态
    order_status      VARCHAR(16)  NOT NULL DEFAULT 'CREATED'
                                COMMENT 'CREATED=已创建 / RELEASED=已下达 / IN_PROGRESS=生产中 / FINISHED=已完工 / CLOSED=已关闭 / CANCELED=已取消',
    priority_level    VARCHAR(8)   NOT NULL DEFAULT 'NORMAL' COMMENT '优先级: HIGH/NORMAL/LOW',
    -- 组织
    factory_code      VARCHAR(32)  NULL,
    workshop_code     VARCHAR(32)  NULL,
    cc_code           VARCHAR(32)  NULL                  COMMENT '成本中心',
    -- BOM 与工艺（引用 MDM 的已发布版本）
    bom_code          VARCHAR(32)  NULL,
    bom_version       VARCHAR(16)  NULL,
    routing_code      VARCHAR(32)  NULL,
    routing_version   VARCHAR(16)  NULL,
    -- 审计
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_po_no (prod_order_no),
    KEY idx_po_sales (sales_order_no),
    KEY idx_po_product (product_code),
    KEY idx_po_status (order_status),
    KEY idx_po_finish (plan_finish_date),
    KEY idx_po_priority (priority_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='生产订单（★ 全项目分析核心对象，延期风险模型的主体）';

-- 财务凭证
CREATE TABLE IF NOT EXISTS erp_fin_voucher (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    voucher_no        VARCHAR(32)  NOT NULL              COMMENT '凭证号',
    company_code      VARCHAR(16)  NOT NULL              COMMENT '公司代码',
    fiscal_period     VARCHAR(8)   NOT NULL              COMMENT '会计期间: 2026-09',
    voucher_date      DATE         NOT NULL,
    -- 借贷（治理规则 F02 的检测对象）
    debit_amount      DECIMAL(18,2) NOT NULL DEFAULT 0   COMMENT '借方金额',
    credit_amount     DECIMAL(18,2) NOT NULL DEFAULT 0   COMMENT '贷方金额',
    -- 归属
    cc_code           VARCHAR(32)  NULL                  COMMENT '成本中心（治理规则 F03）',
    subject_code      VARCHAR(32)  NULL                  COMMENT '会计科目',
    -- 关联业务
    source_type       VARCHAR(16)  NULL                  COMMENT '来源类型: SALES/PURCHASE/PROD/OTHER',
    source_no         VARCHAR(32)  NULL                  COMMENT '来源单号',
    sales_order_no    VARCHAR(32)  NULL,
    prod_order_no     VARCHAR(32)  NULL,
    summary           VARCHAR(300) NULL                  COMMENT '摘要',
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_voucher (voucher_no, company_code, fiscal_period),
    KEY idx_vou_period (fiscal_period),
    KEY idx_vou_cc (cc_code),
    KEY idx_vou_so (sales_order_no),
    KEY idx_vou_po (prod_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='财务凭证（F01唯一性 / F02借贷平衡 的检测对象）';

-- 应收账款
CREATE TABLE IF NOT EXISTS erp_receivable (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    receivable_no     VARCHAR(32)  NOT NULL              COMMENT '应收单号',
    customer_code     VARCHAR(32)  NOT NULL,
    customer_name     VARCHAR(200) NULL,
    sales_order_no    VARCHAR(32)  NULL,
    invoice_no        VARCHAR(32)  NULL                  COMMENT '发票号',
    invoice_date      DATE         NOT NULL,
    due_date          DATE         NOT NULL              COMMENT '到期日（账龄计算基准）',
    -- 金额（治理规则 F04 的检测对象）
    invoice_amount    DECIMAL(18,2) NOT NULL DEFAULT 0   COMMENT '应收金额',
    received_amount   DECIMAL(18,2) NOT NULL DEFAULT 0   COMMENT '已回款金额',
    outstanding_amount DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '未回款金额',
    -- 状态
    settle_status     VARCHAR(16)  NOT NULL DEFAULT 'OPEN'
                                COMMENT 'OPEN=未结 / PARTIAL=部分回款 / SETTLED=已结清 / OVERDUE=已逾期 / BAD_DEBT=坏账',
    overdue_days      INT          NOT NULL DEFAULT 0    COMMENT '逾期天数（由数仓计算回写）',
    cc_code           VARCHAR(32)  NULL,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recv_no (receivable_no),
    KEY idx_recv_customer (customer_code),
    KEY idx_recv_due (due_date),
    KEY idx_recv_status (settle_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='应收账款（F04 逻辑合理性 / 账龄分析的对象）';

-- ============================================================
-- 三、PLM —— 产品生命周期管理
--     消费主数据: 物料 / 产品 / BOM
-- ============================================================
USE src_plm;

CREATE TABLE IF NOT EXISTS plm_md_material (
    material_code     VARCHAR(32)  NOT NULL,
    material_name     VARCHAR(200) NOT NULL,
    material_spec     VARCHAR(200) NULL,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】物料主数据';

CREATE TABLE IF NOT EXISTS plm_md_product (
    product_code      VARCHAR(32)  NOT NULL,
    product_name      VARCHAR(200) NOT NULL,
    product_model     VARCHAR(100) NULL,
    lifecycle_status  VARCHAR(16)  NULL,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】产品主数据';

CREATE TABLE IF NOT EXISTS plm_md_bom (
    bom_code          VARCHAR(32)  NOT NULL,
    bom_version       VARCHAR(16)  NOT NULL,
    product_code      VARCHAR(32)  NOT NULL,
    bom_type          VARCHAR(16)  NOT NULL,
    effective_date    DATE         NOT NULL,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (bom_code, bom_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】BOM 头';

CREATE TABLE IF NOT EXISTS plm_md_bom_line (
    bom_code          VARCHAR(32)  NOT NULL,
    bom_version       VARCHAR(16)  NOT NULL,
    line_no           INT          NOT NULL,
    child_material_code VARCHAR(32) NOT NULL,
    qty_per           DECIMAL(18,6) NOT NULL,
    unit_code         VARCHAR(16)  NOT NULL,
    scrap_rate        DECIMAL(8,4) NOT NULL DEFAULT 0,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (bom_code, bom_version, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】BOM 行';

-- 工程变更单（PLM 的专属业务数据）
CREATE TABLE IF NOT EXISTS plm_ecn (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    ecn_no            VARCHAR(32)  NOT NULL              COMMENT '工程变更单号: ECN-2026-001',
    ecn_title         VARCHAR(200) NOT NULL,
    product_code      VARCHAR(32)  NOT NULL,
    bom_code          VARCHAR(32)  NULL                  COMMENT '涉及的 BOM',
    change_type       VARCHAR(16)  NOT NULL              COMMENT 'DESIGN/MATERIAL/PROCESS',
    change_content    VARCHAR(1000) NULL                 COMMENT '变更内容',
    change_reason     VARCHAR(500) NULL,
    -- 状态
    ecn_status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                                COMMENT 'DRAFT=草稿 / REVIEWING=评审中 / APPROVED=已批准 / IMPLEMENTED=已实施 / CANCELED=已取消',
    effective_date    DATE         NULL,
    -- 审计
    submitted_by      VARCHAR(32)  NULL,
    approved_by       VARCHAR(32)  NULL,
    approved_at       DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ecn_no (ecn_no),
    KEY idx_ecn_product (product_code),
    KEY idx_ecn_status (ecn_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工程变更单（BOM 变更的上游来源）';

-- ============================================================
-- 四、SRM —— 供应商关系管理
--     消费主数据: 供应商 / 物料
-- ============================================================
USE src_srm;

CREATE TABLE IF NOT EXISTS srm_md_supplier (
    supplier_code     VARCHAR(32)  NOT NULL,
    supplier_name     VARCHAR(200) NOT NULL,
    supplier_level    VARCHAR(16)  NULL,
    lead_time_days    INT          NOT NULL DEFAULT 0,
    qual_status       VARCHAR(16)  NULL,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (supplier_code),
    KEY idx_srm_sup_status (qual_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】供应商主数据';

CREATE TABLE IF NOT EXISTS srm_md_material (
    material_code     VARCHAR(32)  NOT NULL,
    material_name     VARCHAR(200) NOT NULL,
    base_unit_code    VARCHAR(16)  NOT NULL,
    purchase_unit_code VARCHAR(16) NULL,
    conversion_rate   DECIMAL(18,6) NOT NULL DEFAULT 1,
    standard_price    DECIMAL(18,4) NOT NULL DEFAULT 0,
    master_version    INT          NOT NULL DEFAULT 1,
    synced_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='【只读副本】物料主数据';

-- 采购订单
CREATE TABLE IF NOT EXISTS srm_purchase_order (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    purchase_order_no VARCHAR(32)  NOT NULL              COMMENT '采购订单号: PORD-20260901-001',
    supplier_code     VARCHAR(32)  NOT NULL,
    supplier_name     VARCHAR(200) NULL,
    material_code     VARCHAR(32)  NOT NULL,
    material_name     VARCHAR(200) NULL,
    -- 数量金额
    order_qty         DECIMAL(18,4) NOT NULL,
    received_qty      DECIMAL(18,4) NOT NULL DEFAULT 0,
    unit_code         VARCHAR(16)  NOT NULL,
    unit_price        DECIMAL(18,4) NOT NULL DEFAULT 0,
    total_amount      DECIMAL(18,2) NOT NULL DEFAULT 0,
    -- 日期（齐套分析的关键输入）
    order_date        DATE         NOT NULL,
    promised_date     DATE         NULL                  COMMENT '供应商承诺到货日',
    expected_date     DATE         NOT NULL              COMMENT '预计到货日',
    -- 状态
    order_status      VARCHAR(16)  NOT NULL DEFAULT 'CREATED'
                                COMMENT 'CREATED=已创建 / SENT=已下达 / PARTIAL=部分到货 / RECEIVED=已到货 / CLOSED=已关闭 / CANCELED=已取消',
    -- 关联需求
    prod_order_no     VARCHAR(32)  NULL                  COMMENT '关联生产订单（需求来源）',
    purchase_user     VARCHAR(32)  NOT NULL              COMMENT '采购员（数据范围）',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_pord_no (purchase_order_no),
    KEY idx_pord_supplier (supplier_code),
    KEY idx_pord_material (material_code),
    KEY idx_pord_status (order_status),
    KEY idx_pord_expected (expected_date),
    KEY idx_pord_po (prod_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='采购订单（到货偏差是延期风险模型的输入特征）';

-- 到货单
CREATE TABLE IF NOT EXISTS srm_delivery (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    delivery_no       VARCHAR(32)  NOT NULL              COMMENT '到货单号',
    purchase_order_no VARCHAR(32)  NOT NULL,
    supplier_code     VARCHAR(32)  NOT NULL,
    material_code     VARCHAR(32)  NOT NULL,
    delivery_qty      DECIMAL(18,4) NOT NULL,
    qualified_qty     DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '合格数量（质检后回填）',
    rejected_qty      DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '不合格数量',
    -- 日期（到货偏差计算）
    expected_date     DATE         NULL                  COMMENT '约定到货日',
    actual_date       DATE         NOT NULL              COMMENT '实际到货日',
    delay_days        INT          NOT NULL DEFAULT 0    COMMENT '到货延迟天数 = actual - expected',
    batch_no          VARCHAR(32)  NULL                  COMMENT '批次号',
    inspection_status VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                COMMENT 'PENDING=待检 / PASSED=合格 / FAILED=不合格 / PARTIAL=部分合格',
    -- 是否已入库
    is_stocked        TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否已入库（触发 WMS 入库）',
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_no (delivery_no),
    KEY idx_del_po (purchase_order_no),
    KEY idx_del_material (material_code),
    KEY idx_del_actual (actual_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='到货单（登记到货后触发 WMS 入库）';
