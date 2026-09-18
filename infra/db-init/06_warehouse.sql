-- ============================================================
-- 06_warehouse.sql —— 数仓四层（ODS / DWD / DWS / ADS）
--
-- 分层职责:
--   ODS  源系统原样落地，带 4 个审计列，不做任何清洗
--   DWD  标准化 + 维度事实建模，治理规则在此层执行
--   DWS  三业务域主题宽表 + 统一指标口径
--   ADS  应用直取数据集
--
-- 说明: 建模主体由 dw_kit / dbt 生成，本脚本只建立
--       「审计列约定」「治理结果表」「指标口径表」等框架性结构。
--       实际业务表由建模工具按模型定义创建。
--
-- 幂等: 全部使用 IF NOT EXISTS
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 一、ODS 层 —— 原样落地
--     每张 ODS 表 = 源表结构 + 4 个审计列
--     这里只建框架与示例，实际表由采集程序按源表结构自动创建
-- ============================================================
USE mfg_ods;

-- ODS 表清单（记录已落地的 ODS 表及其源信息）
CREATE TABLE IF NOT EXISTS ods_table_registry (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    ods_table         VARCHAR(64)  NOT NULL              COMMENT 'ODS 表名: ods_erp_prod_order',
    src_system        VARCHAR(16)  NOT NULL              COMMENT '源系统: erp',
    src_database      VARCHAR(32)  NOT NULL              COMMENT '源库: src_erp',
    src_table         VARCHAR(64)  NOT NULL              COMMENT '源表: erp_prod_order',
    primary_key_cols  VARCHAR(200) NOT NULL              COMMENT '业务主键列（逗号分隔）',
    watermark_col     VARCHAR(50)  NULL                  COMMENT '增量水位线列',
    last_batch_id     CHAR(36)     NULL,
    last_extract_at   DATETIME(3)  NULL,
    row_count         BIGINT       NOT NULL DEFAULT 0,
    is_active         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ods_table (ods_table),
    KEY idx_ods_src (src_system)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='ODS 表登记（采集程序据此自动建表）';

-- ODS 变更日志（行级变更留痕，支撑"数据血缘到行级"）
CREATE TABLE IF NOT EXISTS ods_change_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    ods_table         VARCHAR(64)  NOT NULL,
    pk_value          VARCHAR(200) NOT NULL              COMMENT '业务主键值',
    change_type       VARCHAR(8)   NOT NULL              COMMENT 'INSERT/UPDATE/DELETE',
    batch_id          CHAR(36)     NOT NULL,
    before_hash       VARCHAR(64)  NULL,
    after_hash        VARCHAR(64)  NULL,
    changed_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_cl_table_pk (ods_table, pk_value),
    KEY idx_cl_batch (batch_id),
    KEY idx_cl_time (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='ODS 行级变更日志';

-- ============================================================
-- 二、DWD 层 —— 维度事实建模
-- ============================================================

-- 代理键映射表（业务键 → 代理键，支持 SCD2 版本）
-- 注意: 跨库建表必须显式写库名，因为上面 USE mfg_ods 仍生效
CREATE TABLE IF NOT EXISTS mfg_dwd.dim_key_mapping (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    dim_name          VARCHAR(64)  NOT NULL              COMMENT '维度名: dim_customer',
    business_key      VARCHAR(200) NOT NULL              COMMENT '业务键',
    surrogate_key     BIGINT       NOT NULL              COMMENT '代理键',
    is_current        TINYINT(1)   NOT NULL DEFAULT 1    COMMENT '是否当前版本',
    valid_from        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    valid_to          DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dim_key (dim_name, business_key, valid_from),
    KEY idx_dkm_dim (dim_name, business_key),
    KEY idx_dkm_current (dim_name, business_key, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='代理键映射（SCD2 缓慢变化维的基础）';

-- ============================================================
-- 三、DWS 层 —— 主题宽表 + 指标口径
-- ============================================================
USE mfg_dws;

-- ★ 指标口径定义表 —— 全项目的"口径唯一出口"
-- 这是原方案承诺"统一指标保证智能问答口径一致"的真实载体
CREATE TABLE IF NOT EXISTS metric_definition (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    metric_code       VARCHAR(64)  NOT NULL              COMMENT '指标编码: on_time_delivery_rate',
    metric_name       VARCHAR(100) NOT NULL              COMMENT '指标中文名: 按期交付率',
    business_domain   VARCHAR(16)  NOT NULL              COMMENT '业务域: FINANCE/PRODUCTION/EQUIPMENT',
    definition        VARCHAR(500) NOT NULL              COMMENT '★ 口径定义（自然语言，供 AI 引用）',
    numerator_expr    VARCHAR(500) NULL                  COMMENT '分子表达式',
    denominator_expr  VARCHAR(500) NULL                  COMMENT '分母表达式',
    filter_condition  VARCHAR(500) NULL                  COMMENT '过滤条件（如排除已取消订单）',
    source_table      VARCHAR(64)  NOT NULL              COMMENT '物理来源表',
    dim_grain         VARCHAR(200) NULL                  COMMENT '可下钻维度（逗号分隔）',
    unit              VARCHAR(16)  NULL                  COMMENT '单位: %/天/元/件',
    value_range_min   DECIMAL(18,4) NULL                 COMMENT '值域下限（dbt test 用）',
    value_range_max   DECIMAL(18,4) NULL                 COMMENT '值域上限（dbt test 用）',
    owner_dept        VARCHAR(50)  NULL                  COMMENT '口径责任部门',
    refresh_freq      VARCHAR(16)  NOT NULL DEFAULT 'daily',
    is_active         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_metric_code (metric_code),
    KEY idx_metric_domain (business_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='★ 指标口径定义（MCP query_metric 工具的唯一数据源，保证 AI 算不错）';

-- ============================================================
-- 四、ADS 层 —— 应用直取数据集
-- ============================================================
USE mfg_ads;

-- 治理问题清单（★ 治理看板的数据源）
CREATE TABLE IF NOT EXISTS ads_dq_issue (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    run_id            CHAR(36)     NOT NULL              COMMENT '本次治理运行的批次',
    rule_id           VARCHAR(16)  NOT NULL              COMMENT '规则编号: F01/P03/E01',
    rule_name         VARCHAR(200) NULL,
    business_domain   VARCHAR(16)  NOT NULL,
    target_table      VARCHAR(64)  NOT NULL              COMMENT '问题发生的表',
    severity          VARCHAR(8)   NOT NULL              COMMENT 'error=阻断下游 / warn=仅记录',
    pk_value          VARCHAR(200) NULL                  COMMENT '问题记录的主键值（可下钻）',
    issue_detail      VARCHAR(1000) NULL                 COMMENT '问题描述',
    issue_fields      JSON         NULL                  COMMENT '涉及的字段与值',
    detected_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_resolved       TINYINT(1)   NOT NULL DEFAULT 0,
    resolved_at       DATETIME(3)  NULL,
    resolved_by       VARCHAR(32)  NULL,
    PRIMARY KEY (id),
    KEY idx_issue_rule (rule_id),
    KEY idx_issue_run (run_id),
    KEY idx_issue_table (target_table),
    KEY idx_issue_time (detected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='★ 治理问题明细（治理看板数据源；演示时对照坏数据注入清单验证命中）';

-- 治理规则通过率统计
CREATE TABLE IF NOT EXISTS ads_dq_rule_pass_rate (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    run_id            CHAR(36)     NOT NULL,
    stat_date         DATE         NOT NULL,
    rule_id           VARCHAR(16)  NOT NULL,
    rule_name         VARCHAR(200) NULL,
    business_domain   VARCHAR(16)  NOT NULL,
    target_table      VARCHAR(64)  NOT NULL,
    severity          VARCHAR(8)   NOT NULL,
    total_rows        BIGINT       NOT NULL DEFAULT 0    COMMENT '校验总行数',
    passed_rows       BIGINT       NOT NULL DEFAULT 0,
    failed_rows       BIGINT       NOT NULL DEFAULT 0,
    pass_rate         DECIMAL(6,4) NULL                  COMMENT '通过率 0~1',
    run_status        VARCHAR(16)  NOT NULL              COMMENT 'PASSED/FAILED/ERROR',
    duration_ms       INT          NULL,
    checked_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dq_rate (run_id, rule_id),
    KEY idx_rate_date (stat_date),
    KEY idx_rate_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='治理规则通过率（治理看板的通过率趋势图）';

-- 跨层对账结果（F05 / P05 的落地）
CREATE TABLE IF NOT EXISTS ads_recon_diff (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    run_id            CHAR(36)     NOT NULL,
    recon_name        VARCHAR(64)  NOT NULL              COMMENT '对账项: ORDER_FINANCE_AMOUNT',
    from_layer        VARCHAR(16)  NOT NULL              COMMENT '来源层: ODS/DWD/DWS',
    to_layer          VARCHAR(16)  NOT NULL,
    from_value        DECIMAL(20,4) NULL,
    to_value          DECIMAL(20,4) NULL,
    diff_value        DECIMAL(20,4) NULL,
    diff_ratio        DECIMAL(8,6) NULL,
    diff_count        INT          NULL                  COMMENT '差异记录数',
    sample_keys       JSON         NULL                  COMMENT '差异样例主键（可下钻）',
    checked_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_recon_run (run_id),
    KEY idx_recon_name (recon_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='跨层对账差异（F05订单-财务对账 / P05齐套口径一致 的结果）';

-- 数据血缘快照（由 Dagster / dw_kit 导出）
CREATE TABLE IF NOT EXISTS ads_lineage_snapshot (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    node_id           VARCHAR(128) NOT NULL              COMMENT '节点标识: dws_prod_kitting',
    node_type         VARCHAR(16)  NOT NULL              COMMENT 'ods/stg/dim/fct/dws/metric/ads/model',
    node_name         VARCHAR(200) NULL,
    business_domain   VARCHAR(16)  NULL,
    layer_db          VARCHAR(32)  NULL                  COMMENT '所在库: mfg_dws',
    upstream_nodes    JSON         NULL                  COMMENT '上游节点数组',
    downstream_nodes  JSON         NULL                  COMMENT '下游节点数组',
    column_lineage    JSON         NULL                  COMMENT '列级血缘',
    row_count         BIGINT       NULL                  COMMENT '最近产出行数',
    owner             VARCHAR(50)  NULL,
    description       VARCHAR(500) NULL,
    last_materialized DATETIME(3)  NULL,
    snapshot_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_lineage_node (node_id),
    KEY idx_lineage_type (node_type),
    KEY idx_lineage_domain (business_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='数据血缘快照（MCP get_lineage 工具与前端血缘图的数据源）';

-- 业务术语表（AI 语义层的原始语料）
CREATE TABLE IF NOT EXISTS ads_business_glossary (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    term_code         VARCHAR(64)  NOT NULL              COMMENT '术语编码: kitting_rate',
    term_name         VARCHAR(100) NOT NULL              COMMENT '术语名称: 齐套率',
    term_type         VARCHAR(16)  NOT NULL              COMMENT '类型: METRIC/OBJECT/FIELD/RULE/SOP',
    definition        VARCHAR(1000) NOT NULL             COMMENT '定义（供 AI 检索引用）',
    business_domain   VARCHAR(16)  NULL,
    related_metrics   JSON         NULL                  COMMENT '相关指标',
    related_tables    JSON         NULL                  COMMENT '相关表',
    source_doc        VARCHAR(200) NULL                  COMMENT '来源文档',
    is_active         TINYINT(1)   NOT NULL DEFAULT 1,
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_glossary (term_code),
    KEY idx_glossary_type (term_type),
    KEY idx_glossary_name (term_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='业务术语表（RAG 语义层语料；MCP lookup_glossary 工具的数据源）';
