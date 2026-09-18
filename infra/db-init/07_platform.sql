-- ============================================================
-- 07_platform.sql —— 平台层三库
--   mfg_ops    运维日志（采集/建模/位点/注入清单）
--   mfg_meta   元数据（管理后台配置）
--   mfg_app    AI 应用（向量库 / LLM日志 / Agent会话）
--
-- 幂等: 全部使用 IF NOT EXISTS
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 一、mfg_ops —— 运维层
-- ============================================================
USE mfg_ops;

-- 增量采集位点
CREATE TABLE IF NOT EXISTS ops_watermark (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    table_name        VARCHAR(64)  NOT NULL              COMMENT '源表名',
    src_system        VARCHAR(16)  NOT NULL,
    last_watermark    DATETIME(3)  NULL                  COMMENT '上次成功同步的最大时间戳',
    last_batch_id     CHAR(36)     NULL,
    last_row_count    BIGINT       NULL                  COMMENT '上次写入行数（异常波动检测用）',
    status            VARCHAR(16)  NOT NULL DEFAULT 'idle'
                                COMMENT 'idle/running/failed/blocked',
    consecutive_fail  INT          NOT NULL DEFAULT 0    COMMENT '连续失败次数',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wm_table (table_name),
    KEY idx_wm_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='增量采集位点（安全回退窗口 2 分钟；行数骤降 80% 不推进位点）';

-- 采集运行日志
CREATE TABLE IF NOT EXISTS ops_ingest_run_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    batch_id          CHAR(36)     NOT NULL,
    src_system        VARCHAR(16)  NOT NULL,
    src_table         VARCHAR(64)  NOT NULL,
    ods_table         VARCHAR(64)  NOT NULL,
    mode              VARCHAR(16)  NOT NULL              COMMENT 'full=全量 / incremental=增量 / rerun=重跑 / shadow=影子',
    started_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at       DATETIME(3)  NULL,
    extract_rows      BIGINT       NULL                  COMMENT '抽取行数',
    written_rows      BIGINT       NULL                  COMMENT '写入行数（幂等去重后）',
    updated_rows      BIGINT       NULL                  COMMENT '更新行数',
    status            VARCHAR(16)  NOT NULL DEFAULT 'running'
                                COMMENT 'running/success/failed',
    error_message     VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY idx_irl_table (src_table),
    KEY idx_irl_batch (batch_id),
    KEY idx_irl_time (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='采集运行日志（管理后台「同步监控」页的数据源）';

-- 建模运行日志
CREATE TABLE IF NOT EXISTS ops_dw_run_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    run_id            CHAR(36)     NOT NULL,
    model_name        VARCHAR(128) NOT NULL,
    layer             VARCHAR(16)  NULL                  COMMENT 'stg/dwd/dws/ads',
    materialization   VARCHAR(16)  NULL                  COMMENT 'table/view/incremental',
    depends_on        JSON         NULL                  COMMENT '上游模型',
    started_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at       DATETIME(3)  NULL,
    rows_affected     BIGINT       NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'running'
                                COMMENT 'running/success/error/skipped',
    error_message     VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY idx_dwrl_model (model_name),
    KEY idx_dwrl_run (run_id),
    KEY idx_dwrl_time (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='建模运行日志（dw_kit / dbt 产出）';

-- ★ 坏数据注入清单（演示治理效果的关键）
CREATE TABLE IF NOT EXISTS ops_injection_manifest (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    run_id            CHAR(36)     NOT NULL              COMMENT '本次注入批次',
    rule_id           VARCHAR(16)  NOT NULL              COMMENT '对应治理规则: F01/P03/E01',
    injector          VARCHAR(50)  NOT NULL              COMMENT '注入器名称',
    src_system        VARCHAR(16)  NOT NULL,
    src_table         VARCHAR(64)  NOT NULL,
    primary_key       VARCHAR(200) NOT NULL              COMMENT '被注入记录的业务主键',
    inject_detail     JSON         NULL                  COMMENT '注入的具体内容（前后值）',
    injected_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_im_rule (rule_id),
    KEY idx_im_run (run_id),
    KEY idx_im_table (src_table)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='★ 坏数据注入清单：演示时对照治理命中结果，证明治理链路真实有效';

-- ============================================================
-- 二、mfg_meta —— 元数据（管理后台配置）
-- ============================================================
USE mfg_meta;

-- 来源系统登记
CREATE TABLE IF NOT EXISTS meta_system (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    system_code       VARCHAR(16)  NOT NULL              COMMENT '系统编码: mdm/crm/erp/...',
    system_name       VARCHAR(100) NOT NULL              COMMENT '系统名称',
    system_type       VARCHAR(16)  NOT NULL DEFAULT 'mysql'
                                COMMENT '类型: mysql/postgres/excel/api',
    business_domain   VARCHAR(64)  NULL                  COMMENT '业务域（逗号分隔）',
    db_name           VARCHAR(32)  NULL                  COMMENT '对应数据库名',
    owner_dept        VARCHAR(50)  NULL,
    owner_person      VARCHAR(50)  NULL,
    is_master_data    TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否主数据源系统',
    description       VARCHAR(500) NULL,
    is_enabled        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_code (system_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='来源系统登记（管理后台「系统管理」页）';

-- 源表登记
CREATE TABLE IF NOT EXISTS meta_table (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    system_code       VARCHAR(16)  NOT NULL,
    src_table         VARCHAR(64)  NOT NULL,
    table_name_cn     VARCHAR(200) NULL                  COMMENT '中文表名',
    ods_table         VARCHAR(64)  NOT NULL,
    business_domain   VARCHAR(16)  NULL,
    table_role        VARCHAR(16)  NULL                  COMMENT 'MASTER=主数据副本 / BUSINESS=业务数据',
    primary_key_cols  VARCHAR(200) NULL,
    watermark_col     VARCHAR(50)  NULL,
    sync_mode         VARCHAR(16)  NOT NULL DEFAULT 'incremental',
    row_count         BIGINT       NULL,
    last_synced_at    DATETIME(3)  NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'active'
                                COMMENT 'active/pending/disabled/error',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_table (system_code, src_table),
    KEY idx_mt_system (system_code),
    KEY idx_mt_domain (business_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='源表登记（驱动采集管道动态生成，新增表零代码）';

-- ★ 字段登记 —— 数据字典与 AI 语义的根基
CREATE TABLE IF NOT EXISTS meta_column (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    table_id          BIGINT       NOT NULL,
    src_name          VARCHAR(64)  NOT NULL              COMMENT '源字段名: ORDER_NO',
    dst_name          VARCHAR(64)  NOT NULL              COMMENT '目标字段名: prod_order_no',
    business_name     VARCHAR(200) NOT NULL              COMMENT '★ 中文业务含义: 生产订单号',
    data_type         VARCHAR(32)  NOT NULL,
    is_primary_key    TINYINT(1)   NOT NULL DEFAULT 0,
    is_sensitive      TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '敏感字段（返回时脱敏）',
    in_scope          TINYINT(1)   NOT NULL DEFAULT 1    COMMENT '是否纳入分析范围',
    ordinal_position  INT          NULL,
    remarks           VARCHAR(500) NULL                  COMMENT '枚举值含义、取值范围等',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_col (table_id, src_name),
    KEY idx_mc_table (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='★ 字段登记：business_name 是数据字典、AI 生成模型、向量语义的共同来源';

-- 治理规则配置
CREATE TABLE IF NOT EXISTS meta_rule (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    rule_id           VARCHAR(16)  NOT NULL              COMMENT '规则编号: F01/P03/E01',
    rule_name         VARCHAR(200) NOT NULL,
    business_domain   VARCHAR(16)  NOT NULL,
    target_table      VARCHAR(64)  NOT NULL,
    rule_type         VARCHAR(16)  NOT NULL
                                COMMENT 'unique/not_null/relationship/range/expression/singular/recon',
    expression        VARCHAR(1000) NULL,
    severity          VARCHAR(8)   NOT NULL DEFAULT 'error' COMMENT 'error=阻断下游 / warn=仅记录',
    action_desc       VARCHAR(300) NULL                  COMMENT '处理动作说明',
    hit_count         BIGINT       NULL                  COMMENT '最近一次命中数',
    is_enabled        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_id (rule_id),
    KEY idx_rule_table (target_table)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='治理规则配置（管理后台「规则管理」页，支持试运行预览命中数）';

-- 元数据变更审计
CREATE TABLE IF NOT EXISTS meta_audit_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    operator          VARCHAR(32)  NOT NULL,
    action            VARCHAR(32)  NOT NULL              COMMENT 'create/update/delete/trigger',
    object_type       VARCHAR(32)  NOT NULL              COMMENT 'system/table/column/rule/metric',
    object_id         VARCHAR(64)  NULL,
    before_value      JSON         NULL,
    after_value       JSON         NULL,
    operated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_mal_time (operated_at),
    KEY idx_mal_obj (object_type, object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='元数据变更审计（可追溯"谁在什么时候改了什么"）';

-- ============================================================
-- 三、mfg_app —— AI 应用层
-- ============================================================
USE mfg_app;

-- ★ 向量存储（语义层：业务口径 / SOP / 数据字典的嵌入）
-- 演示规模几千条，向量存 JSON，相似度在应用层用 NumPy 计算（毫秒级）
CREATE TABLE IF NOT EXISTS app_vector_store (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    collection        VARCHAR(64)  NOT NULL              COMMENT '集合: glossary/metric/schema/sop',
    doc_id            VARCHAR(128) NOT NULL              COMMENT '文档标识',
    doc_title         VARCHAR(300) NULL,
    doc_content       TEXT         NOT NULL              COMMENT '原文内容（送 LLM 用）',
    content_hash      VARCHAR(64)  NOT NULL              COMMENT '内容哈希（判断是否需要重新嵌入）',
    embedding         JSON         NULL                  COMMENT '向量数组（float 列表）',
    embedding_model   VARCHAR(64)  NULL                  COMMENT '嵌入模型',
    embedding_dim     INT          NULL                  COMMENT '向量维度',
    metadata          JSON         NULL                  COMMENT '附加元数据',
    keywords          VARCHAR(500) NULL                  COMMENT '关键词（供全文精确匹配，弥补纯向量召回）',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_vector_doc (collection, doc_id),
    KEY idx_vs_collection (collection),
    FULLTEXT KEY ft_vector_content (doc_title, doc_content, keywords)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='★ 向量存储（RAG 语义层；混合检索 = 向量 + 全文精确匹配）';

-- LLM 调用日志
CREATE TABLE IF NOT EXISTS app_llm_call_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    session_id        VARCHAR(64)  NULL                  COMMENT '会话标识',
    provider          VARCHAR(16)  NOT NULL              COMMENT 'deepseek/qwen',
    model_name        VARCHAR(64)  NOT NULL,
    call_type         VARCHAR(16)  NOT NULL              COMMENT 'chat/stream/tool_call/embedding',
    scene             VARCHAR(32)  NULL                  COMMENT '场景: root_cause/text2sql/glossary',
    prompt_tokens     INT          NULL,
    completion_tokens INT          NULL,
    total_tokens      INT          NULL,
    cost_estimate     DECIMAL(10,6) NULL                 COMMENT '预估成本（元）',
    duration_ms       INT          NULL,
    status            VARCHAR(16)  NOT NULL              COMMENT 'SUCCESS/FAILED/TIMEOUT',
    error_message     VARCHAR(500) NULL,
    called_by         VARCHAR(32)  NULL                  COMMENT '调用人',
    called_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_llm_session (session_id),
    KEY idx_llm_time (called_at),
    KEY idx_llm_scene (scene)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='LLM 调用日志（成本与延迟监控；演示"AI 可审计"）';

-- Agent 会话
CREATE TABLE IF NOT EXISTS app_agent_session (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    session_id        VARCHAR(64)  NOT NULL,
    user_name         VARCHAR(32)  NOT NULL,
    scene             VARCHAR(32)  NULL                  COMMENT 'root_cause/ask/dq_report',
    question          TEXT         NOT NULL              COMMENT '用户提问',
    answer            TEXT         NULL                  COMMENT '最终回答',
    tool_calls        JSON         NULL                  COMMENT '工具调用链（思考过程）',
    cited_sources     JSON         NULL                  COMMENT '引用的数据来源与口径',
    total_steps       INT          NULL                  COMMENT '工具调用步数',
    duration_ms       INT          NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'RUNNING'
                                COMMENT 'RUNNING/SUCCESS/FAILED/TIMEOUT',
    started_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at       DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session (session_id),
    KEY idx_agent_user (user_name),
    KEY idx_agent_time (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 会话记录（可复盘每次分析的思考链与工具调用）';

-- ★ MCP 工具调用审计
CREATE TABLE IF NOT EXISTS app_mcp_call_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    session_id        VARCHAR(64)  NULL,
    tool_name         VARCHAR(64)  NOT NULL              COMMENT '工具名: query_metric/run_sql/...',
    tool_params       JSON         NULL                  COMMENT '入参',
    result_rows       INT          NULL                  COMMENT '返回行数',
    is_truncated      TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '是否被 LIMIT 截断',
    sql_text          TEXT         NULL                  COMMENT '实际执行的 SQL（run_sql 时）',
    duration_ms       INT          NULL,
    status            VARCHAR(16)  NOT NULL              COMMENT 'SUCCESS/DENIED/ERROR',
    denied_reason     VARCHAR(300) NULL                  COMMENT '被拦截原因（白名单/写操作等）',
    called_by         VARCHAR(32)  NULL,
    called_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_mcp_tool (tool_name),
    KEY idx_mcp_session (session_id),
    KEY idx_mcp_time (called_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='★ MCP 工具调用审计（合规演示点：每次 AI 访问数据都有记录）';

-- 处置反馈（闭环：处置结果回流为优化样本）
CREATE TABLE IF NOT EXISTS app_disposition (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    biz_type          VARCHAR(32)  NOT NULL              COMMENT '业务类型: ORDER_DELAY/EQUIP_FAULT',
    biz_id            VARCHAR(64)  NOT NULL              COMMENT '业务ID: 订单号/设备号',
    risk_level        VARCHAR(16)  NULL                  COMMENT '当时的风险等级',
    predicted_reason  VARCHAR(500) NULL                  COMMENT '模型预测的原因',
    actual_reason     VARCHAR(500) NULL                  COMMENT '实际发生的原因',
    disposition       VARCHAR(1000) NULL                 COMMENT '采取的处置措施',
    is_effective      TINYINT(1)   NULL                  COMMENT '处置是否有效',
    actual_delay_days INT          NULL                  COMMENT '实际延期天数（用于校验预测）',
    handled_by        VARCHAR(32)  NULL,
    handled_at        DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_disp_biz (biz_type, biz_id),
    KEY idx_disp_time (handled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='处置反馈（闭环：处置结果回流为模型优化样本）';
