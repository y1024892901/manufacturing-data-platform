-- ============================================================
-- 02_master_data.sql —— 主数据表（库: src_mdm）
--
-- 这是第 10 个系统「统一主数据管理平台」的物理模型。
-- 核心原则:
--   1. 主数据只在 MDM 维护，9 个业务系统只持有只读副本
--   2. 只有 status='PUBLISHED' 的主数据才能被下游系统消费
--   3. 任何变更都要走审批，全程留痕可追溯
--
-- 统一状态机: DRAFT → PENDING → APPROVED → PUBLISHED
--                              ↘ REJECTED        ↓
--                                            CHANGING → PENDING ...
--                                                 ↓
--                                             DISABLED
--
-- 幂等: 全部使用 IF NOT EXISTS
-- ============================================================

USE src_mdm;

SET NAMES utf8mb4;

-- ============================================================
-- 第一部分：组织与人员（审批链的基础）
-- ============================================================

-- 组织单元：公司 → 工厂 → 车间 → 产线（自引用树）
CREATE TABLE IF NOT EXISTS md_org_unit (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    org_code          VARCHAR(32)  NOT NULL              COMMENT '组织编码',
    org_name          VARCHAR(100) NOT NULL              COMMENT '组织名称',
    org_type          VARCHAR(16)  NOT NULL              COMMENT '类型: COMPANY/FACTORY/WORKSHOP/LINE',
    parent_id         BIGINT       NULL                  COMMENT '上级组织',
    org_level         TINYINT      NOT NULL DEFAULT 1    COMMENT '层级',
    manager_user_id   BIGINT       NULL                  COMMENT '负责人',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED'
                                COMMENT 'DRAFT/PENDING/APPROVED/PUBLISHED/CHANGING/DISABLED',
    version_no        INT          NOT NULL DEFAULT 1    COMMENT '版本号',
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_code (org_code),
    KEY idx_org_parent (parent_id),
    KEY idx_org_type (org_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='组织单元主数据';

-- 员工（审批人来源）
CREATE TABLE IF NOT EXISTS md_employee (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    emp_code          VARCHAR(32)  NOT NULL              COMMENT '工号',
    emp_name          VARCHAR(50)  NOT NULL              COMMENT '姓名',
    org_id            BIGINT       NULL                  COMMENT '所属组织',
    position_name     VARCHAR(50)  NULL                  COMMENT '岗位',
    email             VARCHAR(100) NULL,
    mobile            VARCHAR(20)  NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED',
    version_no        INT          NOT NULL DEFAULT 1,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_emp_code (emp_code),
    KEY idx_emp_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='员工主数据';

-- ============================================================
-- 第二部分：基础字典
-- ============================================================

-- 计量单位
CREATE TABLE IF NOT EXISTS md_unit (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    unit_code         VARCHAR(16)  NOT NULL              COMMENT '单位编码: PCS/KG/M/M2',
    unit_name         VARCHAR(20)  NOT NULL              COMMENT '单位名称: 个/千克/米',
    unit_type         VARCHAR(16)  NOT NULL              COMMENT '类型: QUANTITY/WEIGHT/LENGTH/AREA/TIME',
    base_unit_code    VARCHAR(16)  NULL                  COMMENT '基本单位（换算基准）',
    convert_rate      DECIMAL(18,6) NOT NULL DEFAULT 1   COMMENT '对基本单位的换算率',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED',
    version_no        INT          NOT NULL DEFAULT 1,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_unit_code (unit_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='计量单位主数据（制造业单位换算错误会导致数量级灾难）';

-- 成本中心
CREATE TABLE IF NOT EXISTS md_cost_center (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    cc_code           VARCHAR(32)  NOT NULL              COMMENT '成本中心编码',
    cc_name           VARCHAR(100) NOT NULL              COMMENT '成本中心名称',
    org_id            BIGINT       NULL                  COMMENT '所属组织',
    cc_type           VARCHAR(16)  NULL                  COMMENT '类型: PRODUCTION/SERVICE/ADMIN',
    manager_emp_id    BIGINT       NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED',
    version_no        INT          NOT NULL DEFAULT 1,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cc_code (cc_code),
    KEY idx_cc_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='成本中心主数据';

-- 会计科目
CREATE TABLE IF NOT EXISTS md_account_subject (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    subject_code      VARCHAR(32)  NOT NULL              COMMENT '科目编码',
    subject_name      VARCHAR(100) NOT NULL              COMMENT '科目名称',
    subject_type      VARCHAR(16)  NOT NULL              COMMENT '类型: ASSET/LIABILITY/EQUITY/COST/REVENUE',
    parent_id         BIGINT       NULL                  COMMENT '上级科目',
    subject_level     TINYINT      NOT NULL DEFAULT 1,
    is_leaf           TINYINT(1)   NOT NULL DEFAULT 1    COMMENT '是否明细科目',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED',
    version_no        INT          NOT NULL DEFAULT 1,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_subject_code (subject_code),
    KEY idx_subject_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='会计科目主数据';

-- ============================================================
-- 第三部分：业务伙伴
-- ============================================================

-- 客户
CREATE TABLE IF NOT EXISTS md_customer (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    customer_code     VARCHAR(32)  NOT NULL              COMMENT '客户编码（全公司唯一）',
    customer_name     VARCHAR(200) NOT NULL              COMMENT '客户名称',
    short_name        VARCHAR(100) NULL                  COMMENT '简称',
    unified_social_code VARCHAR(32) NULL                 COMMENT '统一社会信用代码（用于识别一客多码）',
    customer_level    VARCHAR(16)  NULL                  COMMENT '等级: A/B/C/D',
    customer_type     VARCHAR(16)  NULL                  COMMENT '类型: DIRECT/DEALER/AGENT',
    industry          VARCHAR(50)  NULL                  COMMENT '所属行业',
    region            VARCHAR(50)  NULL                  COMMENT '销售区域',
    -- 信用与结算（财务审批关注点）
    credit_limit      DECIMAL(18,2) NOT NULL DEFAULT 0   COMMENT '信用额度',
    credit_used       DECIMAL(18,2) NOT NULL DEFAULT 0   COMMENT '已用额度',
    payment_terms     VARCHAR(32)  NULL                  COMMENT '账期: NET30/NET60/预付',
    tax_no            VARCHAR(32)  NULL                  COMMENT '税号',
    -- 联系方式
    contact_person    VARCHAR(50)  NULL,
    contact_phone     VARCHAR(30)  NULL                  COMMENT '敏感字段，返回时脱敏',
    contact_email     VARCHAR(100) NULL                  COMMENT '敏感字段，返回时脱敏',
    address           VARCHAR(300) NULL,
    -- 主数据治理字段
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                                COMMENT 'DRAFT/PENDING/APPROVED/PUBLISHED/CHANGING/DISABLED',
    version_no        INT          NOT NULL DEFAULT 1,
    change_reason     VARCHAR(300) NULL                  COMMENT '最近一次变更原因',
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_code (customer_code),
    KEY idx_customer_status (status),
    KEY idx_customer_name (customer_name),
    KEY idx_customer_social (unified_social_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='客户主数据（唯一真实来源；CRM 与 ERP 均只持有只读副本）';

-- 供应商
CREATE TABLE IF NOT EXISTS md_supplier (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    supplier_code     VARCHAR(32)  NOT NULL              COMMENT '供应商编码',
    supplier_name     VARCHAR(200) NOT NULL              COMMENT '供应商名称',
    short_name        VARCHAR(100) NULL,
    unified_social_code VARCHAR(32) NULL                 COMMENT '统一社会信用代码',
    supplier_level    VARCHAR(16)  NULL                  COMMENT '等级: A/B/C/D（影响采购分配）',
    supplier_type     VARCHAR(16)  NULL                  COMMENT '类型: MATERIAL/SERVICE/OUTSOURCE',
    -- 供应能力
    lead_time_days    INT          NOT NULL DEFAULT 0    COMMENT '标准交货提前期（天）——齐套分析的关键输入',
    supply_category   VARCHAR(100) NULL                  COMMENT '供应品类',
    -- 资质（质量审批关注点）
    qual_status       VARCHAR(16)  NULL                  COMMENT '资质状态: VALID/EXPIRING/EXPIRED',
    qual_expire_date  DATE         NULL                  COMMENT '资质到期日',
    -- 财务
    payment_terms     VARCHAR(32)  NULL                  COMMENT '账期',
    bank_account      VARCHAR(64)  NULL                  COMMENT '银行账号（敏感）',
    tax_no            VARCHAR(32)  NULL,
    -- 绩效（历史数据回写）
    on_time_rate      DECIMAL(5,4) NULL                  COMMENT '到货准时率（由数仓回写）',
    quality_pass_rate DECIMAL(5,4) NULL                  COMMENT '来料合格率（由数仓回写）',
    -- 治理
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    version_no        INT          NOT NULL DEFAULT 1,
    change_reason     VARCHAR(300) NULL,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_code (supplier_code),
    KEY idx_supplier_status (status),
    KEY idx_supplier_name (supplier_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='供应商主数据';

-- ============================================================
-- 第四部分：物料与产品
-- ============================================================

-- 物料分类（树形，分析维度）
CREATE TABLE IF NOT EXISTS md_material_category (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    category_code     VARCHAR(32)  NOT NULL              COMMENT '分类编码',
    category_name     VARCHAR(100) NOT NULL              COMMENT '分类名称',
    parent_id         BIGINT       NULL                  COMMENT '上级分类',
    category_level    TINYINT      NOT NULL DEFAULT 1,
    is_leaf           TINYINT(1)   NOT NULL DEFAULT 1    COMMENT '是否末级（只有末级可挂物料）',
    category_path     VARCHAR(500) NULL                  COMMENT '全路径，如 原材料/金属/钢材',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED',
    version_no        INT          NOT NULL DEFAULT 1,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_code (category_code),
    KEY idx_category_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='物料分类主数据（树形，供分析按分类聚合）';

-- 物料主数据（原材料/半成品/成品/备件 统一管理）
CREATE TABLE IF NOT EXISTS md_material (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    material_code     VARCHAR(32)  NOT NULL              COMMENT '物料编码（全公司唯一，9个系统共用）',
    material_name     VARCHAR(200) NOT NULL              COMMENT '物料名称',
    material_spec     VARCHAR(200) NULL                  COMMENT '规格型号',
    spec_desc         VARCHAR(500) NULL                  COMMENT '规格描述（用于工具类物料）',
    material_type     VARCHAR(16)  NOT NULL              COMMENT '类型: RAW/SEMI/FINISHED/SPARE/PACKAGING',
    category_id       BIGINT       NULL                  COMMENT '物料分类',
    -- 计量（制造业务的关键：两套单位）
    base_unit_code    VARCHAR(16)  NOT NULL              COMMENT '基本计量单位',
    purchase_unit_code VARCHAR(16) NULL                  COMMENT '采购单位（与基本单位可能不同）',
    conversion_rate   DECIMAL(18,6) NOT NULL DEFAULT 1   COMMENT '采购单位对基本单位换算率',
    -- 计划与库存
    safety_stock      DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '安全库存',
    shelf_life_days   INT          NULL                  COMMENT '保质期（天）',
    is_batch_managed  TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否批次管理',
    -- 采购与成本
    default_supplier_id BIGINT     NULL                  COMMENT '默认供应商',
    standard_price    DECIMAL(18,4) NOT NULL DEFAULT 0   COMMENT '标准成本单价',
    -- 质量
    is_inspection_required TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否需检验',
    inspection_standard VARCHAR(500) NULL                COMMENT '检验标准',
    -- 治理
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    version_no        INT          NOT NULL DEFAULT 1,
    change_reason     VARCHAR(300) NULL,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_code (material_code),
    KEY idx_material_status (status),
    KEY idx_material_category (category_id),
    KEY idx_material_type (material_type),
    KEY idx_material_name (material_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='物料主数据（9 个业务系统共用同一套物料编码）';

-- 产品主数据
CREATE TABLE IF NOT EXISTS md_product (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    product_code      VARCHAR(32)  NOT NULL              COMMENT '产品编码',
    product_name      VARCHAR(200) NOT NULL              COMMENT '产品名称',
    product_model     VARCHAR(100) NULL                  COMMENT '产品型号',
    material_id       BIGINT       NULL                  COMMENT '对应物料（成品物料）',
    category_id       BIGINT       NULL                  COMMENT '产品分类',
    -- 技术参数
    unit_code         VARCHAR(16)  NOT NULL DEFAULT 'PCS',
    weight_kg         DECIMAL(18,4) NULL                 COMMENT '单重（千克）',
    -- 生命周期
    lifecycle_status  VARCHAR(16)  NULL                  COMMENT '生命周期: DESIGN/TRIAL/MASS/PROD/EOL',
    launch_date       DATE         NULL                  COMMENT '上市日期',
    eol_date          DATE         NULL                  COMMENT '停产日期',
    -- 治理
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    version_no        INT          NOT NULL DEFAULT 1,
    change_reason     VARCHAR(300) NULL,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (product_code),
    KEY idx_product_status (status),
    KEY idx_product_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='产品主数据';

-- ============================================================
-- 第五部分：BOM（演示的核心主角）
--
-- 为什么 BOM 必须是主数据:
--   ERP 用它算料、MES 用它领料、成本用它算钱。
--   若三方各存一份 → 料算不准、领错料、成本失真，且无人知道哪个对。
-- ============================================================

-- BOM 头
CREATE TABLE IF NOT EXISTS md_bom (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    bom_code          VARCHAR(32)  NOT NULL              COMMENT 'BOM 编码',
    bom_name          VARCHAR(200) NOT NULL              COMMENT 'BOM 名称',
    product_id        BIGINT       NULL                  COMMENT '产品',
    product_code      VARCHAR(32)  NULL                  COMMENT '产品编码（冗余，便于查询）',
    bom_version       VARCHAR(16)  NOT NULL              COMMENT 'BOM 版本: V1.0 / V1.1',
    bom_type          VARCHAR(16)  NOT NULL DEFAULT 'MBOM'
                                COMMENT 'EBOM=工程BOM（研发） / MBOM=制造BOM（工艺）',
    -- 有效期（版本生效控制）
    effective_date    DATE         NOT NULL              COMMENT '生效日期',
    expire_date       DATE         NULL                  COMMENT '失效日期',
    is_current        TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否当前生效版本',
    -- 基准
    base_qty          DECIMAL(18,4) NOT NULL DEFAULT 1   COMMENT '基准数量（生产多少个母件）',
    base_unit_code    VARCHAR(16)  NULL                  COMMENT '基准单位',
    -- 治理（BOM 变更走三级审批）
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    version_no        INT          NOT NULL DEFAULT 1,
    change_reason     VARCHAR(300) NULL                  COMMENT '变更原因（BOM变更必须填写）',
    change_ecn_no     VARCHAR(32)  NULL                  COMMENT '工程变更单号',
    approved_by       VARCHAR(32)  NULL,
    approved_at       DATETIME(3)  NULL,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bom_code_version (bom_code, bom_version),
    KEY idx_bom_product (product_code),
    KEY idx_bom_status (status),
    KEY idx_bom_current (product_code, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='BOM 主数据头（带版本与生效期；变更需三级审批）';

-- BOM 行（用料明细）
CREATE TABLE IF NOT EXISTS md_bom_line (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    bom_id            BIGINT       NOT NULL              COMMENT '所属 BOM 头',
    line_no           INT          NOT NULL              COMMENT '行号',
    -- 子件
    child_material_id BIGINT       NULL                  COMMENT '子件物料',
    child_material_code VARCHAR(32) NOT NULL             COMMENT '子件物料编码',
    child_material_name VARCHAR(200) NULL,
    -- 用量
    qty_per           DECIMAL(18,6) NOT NULL             COMMENT '单位用量（关键字段！）',
    unit_code         VARCHAR(16)  NOT NULL              COMMENT '单位',
    scrap_rate        DECIMAL(8,4) NOT NULL DEFAULT 0    COMMENT '损耗率（%）',
    -- 装配信息
    seq_no            INT          NOT NULL DEFAULT 1    COMMENT '装配顺序',
    is_key_material   TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否关键料（齐套优先关注）',
    position_desc     VARCHAR(200) NULL                  COMMENT '装配位置',
    -- 替代料（制造业常见）
    substitute_group  VARCHAR(32)  NULL                  COMMENT '替代料组',
    remark            VARCHAR(300) NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bom_line (bom_id, line_no),
    KEY idx_bomline_child (child_material_code),
    KEY idx_bomline_bom (bom_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='BOM 行：单位用量是齐套率与用料需求计算的唯一依据';

-- ============================================================
-- 第六部分：工艺路线
-- ============================================================

CREATE TABLE IF NOT EXISTS md_routing (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    routing_code      VARCHAR(32)  NOT NULL              COMMENT '工艺路线编码',
    routing_name      VARCHAR(200) NOT NULL              COMMENT '工艺路线名称',
    product_code      VARCHAR(32)  NULL                  COMMENT '产品编码',
    routing_version   VARCHAR(16)  NOT NULL DEFAULT 'V1.0',
    effective_date    DATE         NOT NULL,
    expire_date       DATE         NULL,
    is_current        TINYINT(1)   NOT NULL DEFAULT 0,
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    version_no        INT          NOT NULL DEFAULT 1,
    change_reason     VARCHAR(300) NULL,
    created_by        VARCHAR(32)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by        VARCHAR(32)  NULL,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_routing_code_ver (routing_code, routing_version),
    KEY idx_routing_product (product_code),
    KEY idx_routing_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工艺路线主数据头';

CREATE TABLE IF NOT EXISTS md_routing_operation (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    routing_id        BIGINT       NOT NULL              COMMENT '所属工艺路线',
    op_seq            INT          NOT NULL              COMMENT '工序顺序号: 10/20/30',
    operation_code    VARCHAR(32)  NOT NULL              COMMENT '工序编码',
    operation_name    VARCHAR(100) NOT NULL              COMMENT '工序名称: 钻孔/装配/检验',
    work_center       VARCHAR(50)  NULL                  COMMENT '工作中心/车间',
    -- 工时（进度与成本计算依据）
    setup_time_min    DECIMAL(10,2) NOT NULL DEFAULT 0   COMMENT '准备工时（分钟）',
    run_time_min      DECIMAL(10,4) NOT NULL DEFAULT 0   COMMENT '单件加工工时（分钟）',
    wait_time_min     DECIMAL(10,2) NOT NULL DEFAULT 0   COMMENT '等待工时',
    -- 资源需求
    default_equipment_code VARCHAR(32) NULL              COMMENT '默认设备（停机影响的传导起点）',
    required_skill    VARCHAR(50)  NULL                  COMMENT '技能要求',
    is_key_operation  TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否关键工序',
    is_inspection_op  TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否检验工序',
    -- 质检
    inspection_required TINYINT(1) NOT NULL DEFAULT 0,
    remark            VARCHAR(300) NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_routing_op (routing_id, op_seq),
    KEY idx_op_code (operation_code),
    KEY idx_op_equipment (default_equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工艺路线工序明细（设备停机的传导路径：设备→工序→工单→订单）';

-- ============================================================
-- 第七部分：主数据分发（MDM → 9 个业务系统）
-- ============================================================

-- 分发记录：谁在什么时候把哪个版本的主数据分发到了哪个系统
CREATE TABLE IF NOT EXISTS md_distribution_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    entity_type       VARCHAR(32)  NOT NULL              COMMENT '主数据实体: CUSTOMER/MATERIAL/BOM/...',
    entity_code       VARCHAR(64)  NOT NULL              COMMENT '主数据编码',
    entity_version    INT          NOT NULL              COMMENT '分发的版本号',
    target_system     VARCHAR(16)  NOT NULL              COMMENT '目标系统: erp/mes/wms/...',
    distribute_mode   VARCHAR(16)  NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO=发布即分发 / MANUAL=手动补发',
    status            VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED/PENDING',
    row_count         INT          NULL                  COMMENT '写入行数',
    error_message     VARCHAR(500) NULL,
    distributed_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dist (entity_type, entity_code, entity_version, target_system),
    KEY idx_dist_entity (entity_type, entity_code),
    KEY idx_dist_target (target_system),
    KEY idx_dist_time (distributed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='主数据分发日志：可查"这个物料分发给了哪几个系统、什么时候"';

-- 主数据变更申请（版本变更的审批载体）
CREATE TABLE IF NOT EXISTS md_change_request (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    request_no        VARCHAR(32)  NOT NULL              COMMENT '变更申请单号: CR-20260917-001',
    entity_type       VARCHAR(32)  NOT NULL              COMMENT '主数据实体类型',
    entity_code       VARCHAR(64)  NOT NULL              COMMENT '主数据编码',
    base_version      INT          NOT NULL              COMMENT '基于哪个版本变更',
    target_version    INT          NOT NULL              COMMENT '目标版本号',
    change_type       VARCHAR(16)  NOT NULL              COMMENT 'CREATE/UPDATE/DISABLE',
    change_reason     VARCHAR(500) NOT NULL              COMMENT '变更原因（必填）',
    before_snapshot   JSON         NULL                  COMMENT '变更前快照',
    after_snapshot    JSON         NULL                  COMMENT '变更后快照',
    diff_summary      VARCHAR(1000) NULL                 COMMENT '差异摘要（供审批人快速判断）',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    submitted_by      VARCHAR(32)  NOT NULL,
    submitted_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_no (request_no),
    KEY idx_cr_entity (entity_type, entity_code),
    KEY idx_cr_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='主数据变更申请（已发布数据的任何修改都必须走此流程）';
