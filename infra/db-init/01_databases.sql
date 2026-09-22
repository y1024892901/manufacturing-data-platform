-- ============================================================
-- 01_databases.sql —— 创建全部 18 个数据库
--
-- 目标实例: MySQL 8.0.43 @ 127.0.0.1:3306  (D:\mysql8)
-- 幂等: 全部使用 IF NOT EXISTS，可重复执行
--
-- 库名约定:
--   src_*  源系统库（第10个主数据平台 + 9类业务系统）
--   mfg_*  数据平台库（数仓四层 + 平台能力）
-- ============================================================

SET NAMES utf8mb4;
SET @charset = 'utf8mb4';
SET @collate = 'utf8mb4_0900_ai_ci';

-- ============================================================
-- 一、源系统层（10 个）
--     业务系统只维护本系统的业务数据；主数据一律来自 src_mdm
-- ============================================================

-- 第 10 个系统：统一主数据管理平台（Master Data Management）
-- 承载客户/供应商/物料/BOM/工艺路线等 12 类主数据的唯一真实来源
CREATE DATABASE IF NOT EXISTS src_mdm
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 9 类业务系统
CREATE DATABASE IF NOT EXISTS src_crm
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS src_erp
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS src_mes
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS src_wms
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS src_eam
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS src_qms
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS src_srm
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS src_plm
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS src_energy
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 二、数仓四层（4 个）
--     MySQL 的 database 等价于其他数据库的 schema，正好用于分层隔离
-- ============================================================

-- ODS：源系统原样落地，每行带 4 个审计列
CREATE DATABASE IF NOT EXISTS mfg_ods
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- DWD：标准化 + 维度事实建模
CREATE DATABASE IF NOT EXISTS mfg_dwd
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- DWS：三业务域主题宽表 + 统一指标口径
CREATE DATABASE IF NOT EXISTS mfg_dws
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ADS：应用直取数据集（报表 / 模型 / 治理 / 语义）
CREATE DATABASE IF NOT EXISTS mfg_ads
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 三、平台能力层（4 个）
-- ============================================================

-- 元数据：管理后台配置（系统/表/字段/规则/指标）
CREATE DATABASE IF NOT EXISTS mfg_meta
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 权限与审批：用户/角色/数据范围/审批流程
CREATE DATABASE IF NOT EXISTS mfg_auth
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 应用层：向量存储（语义层）、LLM 调用日志、Agent 会话
CREATE DATABASE IF NOT EXISTS mfg_app
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 运维层：采集日志、增量位点、坏数据注入清单、跨层对账
CREATE DATABASE IF NOT EXISTS mfg_ops
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 四、验证
-- ============================================================
SELECT
    SCHEMA_NAME                              AS `数据库`,
    IF(SCHEMA_NAME LIKE 'src\_%', '源系统层',
      IF(SCHEMA_NAME IN ('mfg_ods','mfg_dwd','mfg_dws','mfg_ads'), '数仓层', '平台层')) AS `分类`
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME LIKE 'src\_%' OR SCHEMA_NAME LIKE 'mfg\_%'
ORDER BY `分类`, SCHEMA_NAME;
