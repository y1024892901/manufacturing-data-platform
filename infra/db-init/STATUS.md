# infra/db-init/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

11 个 SQL 脚本全部编写完成**并已在本机 MySQL 8.0.43 上执行完毕**，实测建成 **18 个数据库、99 张表**及全套演示种子数据（36 账号 / 36 角色 / 69 权限点 / 7 条审批链 13 节点 / 15 条治理规则 / 10 个指标口径），全部幂等、可反复重建。

## 脚本清单

> 按执行顺序排列。实测数据由 `CREATE DATABASE` / `CREATE TABLE` 语句计数得出。

| 序号 | 文件名 | 行数 | 目标库 | 建了什么（库 / 表 / 种子） | 幂等方式 |
|---|---|---|---|---|---|
| 01 | `01_databases.sql` | 104 | — | **建 18 个库**：源系统层 10（`src_mdm` + 9 类业务）、数仓四层 4（`mfg_ods`/`mfg_dwd`/`mfg_dws`/`mfg_ads`）、平台层 4（`mfg_meta`/`mfg_auth`/`mfg_app`/`mfg_ops`）。统一 `utf8mb4` / `utf8mb4_0900_ai_ci` | `CREATE DATABASE IF NOT EXISTS` |
| 02 | `02_master_data.sql` | 482 | `src_mdm` | **16 张主数据表**：`md_org_unit`(组织树) `md_employee` `md_unit` `md_cost_center` `md_account_subject` `md_customer` `md_supplier` `md_material_category` `md_material` `md_product` `md_bom` `md_bom_line` `md_routing` `md_routing_operation` `md_distribution_log`(分发记录) `md_change_request`(变更申请)。统一状态机 `DRAFT→PENDING→APPROVED→PUBLISHED→CHANGING→DISABLED` + `REJECTED` | `CREATE TABLE IF NOT EXISTS` |
| 03 | `03_auth_workflow.sql` | 273 | `mfg_auth` | **14 张权限与审批表**：`sys_user` `sys_role` `sys_permission` `sys_user_role` `sys_role_permission` `sys_user_system` `sys_data_scope` `wf_definition` `wf_node` `wf_instance` `wf_task` `wf_action_log` `sys_login_log` `sys_audit_log`。账号分两层（可进哪个系统 + 系统内角色），审批引擎表结构通用、靠数据定义流程 | `CREATE TABLE IF NOT EXISTS` |
| 04 | `04_business_systems.sql` | 476 | `src_crm` `src_erp` `src_plm` `src_srm` | **21 张表**（4 段 `USE`）。CRM 3：`crm_md_customer`(副本) `crm_opportunity` `crm_opportunity_follow`。ERP 9：4 张副本 `erp_md_customer`/`erp_md_supplier`/`erp_md_material`/`erp_md_cost_center` + `erp_sales_order` `erp_sales_order_line` `erp_prod_order` `erp_fin_voucher` `erp_receivable`。PLM 5：4 张副本 `plm_md_material`/`plm_md_product`/`plm_md_bom`/`plm_md_bom_line` + `plm_ecn`。SRM 4：2 张副本 `srm_md_supplier`/`srm_md_material` + `srm_purchase_order` `srm_delivery` | `CREATE TABLE IF NOT EXISTS` |
| 05 | `05_business_systems_2.sql` | 555 | `src_mes` `src_wms` `src_qms` `src_eam` `src_energy` | **22 张表**（5 段 `USE`）。MES 5：`mes_md_material`/`mes_md_routing_operation`(2 副本) + `mes_work_order` `mes_work_report` `mes_prod_result`。WMS 4：`wms_md_material`(副本) + `wms_location` `wms_inventory`(齐套率输入) `wms_stock_txn`。QMS 4：`qms_md_material`(副本) + `qms_inspection` `qms_defect` `qms_rework`。EAM 6：`eam_md_material`(副本) + `eam_equipment` `eam_equipment_status_log` `eam_fault` `eam_repair` `eam_inspection`。能源 3：`energy_md_equipment`(副本) + `energy_equipment_usage` `energy_workshop_usage` | `CREATE TABLE IF NOT EXISTS` |
| 06 | `06_warehouse.sql` | 230 | `mfg_ods` `mfg_dwd` `mfg_dws` `mfg_ads` | **9 张数仓框架表**（4 段 `USE`）。ODS 2：`ods_table_registry`(ODS 表登记) `ods_change_log`(行级变更日志)。DWD 1：`mfg_dwd.dim_key_mapping`(代理键映射，**显式带库名**) 。DWS 1：`metric_definition`(★ 指标口径定义，MCP `query_metric` 的 `definition` 来源)。ADS 5：`ads_dq_issue` `ads_dq_rule_pass_rate` `ads_recon_diff` `ads_lineage_snapshot` `ads_business_glossary`。**只建框架，业务建模表由 dw_kit/dbt 生成** | `CREATE TABLE IF NOT EXISTS` |
| 07 | `07_platform.sql` | 318 | `mfg_ops` `mfg_meta` `mfg_app` | **14 张平台表**（3 段 `USE`）。运维 4：`ops_watermark`(增量位点，安全回退窗口 2 分钟/行数骤降 80% 不推进) `ops_ingest_run_log` `ops_dw_run_log` `ops_injection_manifest`(坏数据注入清单)。元数据 5：`meta_system` `meta_table` `meta_column` `meta_rule` `meta_audit_log`。AI 应用 5：`app_vector_store` `app_llm_call_log` `app_agent_session` `app_mcp_call_log` `app_disposition`(处置反馈闭环) | `CREATE TABLE IF NOT EXISTS` |
| 08 | `08_seed_data.sql` | 589 | `mfg_auth` `mfg_meta` `mfg_dws` | **建 1 张表 + 12 组种子**。建 `sys_dept`(部门树，DEPT 级数据范围的过滤依据)。种子实测行数：部门 **10**、角色 **36**、权限点 **69**、演示账号 **36**（统一口令 `Test@123456`，BCrypt `$2a$`）、用户↔角色（含一人多岗）、用户可访问系统（第一层权限）、数据范围（第二层权限）、审批流程 **7 条 / 13 个节点**、治理规则 **15** 条、指标口径 **10** 条、来源系统登记 **10** 条 | 建表 `IF NOT EXISTS`；种子 `ON DUPLICATE KEY UPDATE`；审批链先 `DELETE FROM wf_node; DELETE FROM wf_definition;` 再插入 |
| 09 | `09_role_permission.sql` | 264 | `mfg_auth` | **纯种子，不建表**：36 条 `INSERT`，建立角色↔权限点映射，按「部门 + 职级」分配（执行岗=查看录入 / 主管岗=+审批与部分主数据维护 / 经理总监=+全局查看与主数据批准）。**没有这张表，所有角色权限数为 0，`@PreAuthorize` 全部拦截** | 先 `DELETE FROM sys_role_permission` 再插入（配置表，可安全重建） |
| 10 | `10_missing_copies.sql` | 51 | `src_erp` | **2 张表**：`erp_md_bom`(BOM 头副本) `erp_md_bom_line`(BOM 行副本，`qty_per` 单位用量是算料的唯一依据)。补 `04` 的遗漏 —— ERP 是 BOM 最主要的消费方（用它算料），却漏建了 BOM 副本 | `CREATE TABLE IF NOT EXISTS`；尾部 `SELECT` 验证 |
| 11 | `11_plm_ecn_workflow.sql` | 14 | `mfg_auth` | **纯种子，不建表**：3 条 `INSERT`，新增 `PLM_ECN_CHANGE` 工程变更审批链 —— 研发经理 `RND_MANAGER` → 工艺主管 `PROCESS_SUPERVISOR`，通过后方可实施。这是 **第 8 条**审批链 | `WHERE NOT EXISTS`（`wf_definition` 按 `def_code`+`def_version` 判重，`wf_node` 按 `definition_id`+`node_seq` 判重） |

### 表的去向汇总（实测）

| 归属 | 库数 | 表数 | 说明 |
|---|---|---|---|
| 源系统层 | 10 | 61 | `src_mdm` 16；`src_crm` 3、`src_erp` 11、`src_plm` 5、`src_srm` 4、`src_mes` 5、`src_wms` 4、`src_qms` 4、`src_eam` 6、`src_energy` 3 |
| 数仓层 | 4 | 9 | `mfg_ods` 2、`mfg_dwd` 1、`mfg_dws` 1、`mfg_ads` 5 |
| 平台层 | 4 | 29 | `mfg_auth` 15（含 `08` 建的 `sys_dept`）、`mfg_meta` 5、`mfg_app` 5、`mfg_ops` 4 |
| **合计** | **18** | **99** | `01` 建库 18；`02`~`07` 建表 96，加 `08` 的 1 张与 `10` 的 2 张共 99 |

> **口径提示**：库数统一为 **18 个**（实测 `CREATE DATABASE` 语句数）。此前 `01_databases.sql` 文件头与 `mysql8/README.md` 误写「20 个」，已一并更正。

## 关键机制 / 使用方式

### 执行方式

按编号顺序执行，缺一不可。`README.md` 给出的是**手抄版 8 条命令**（只到 `08`）：

```bash
export MYSQL_PWD='<见 infra/.env>'
MYSQL=D:/mysql8/bin/mysql.exe

$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 01_databases.sql
# … 02 ~ 08 同上 …
```

**必须补上后三个脚本**，否则会缺角色权限映射（导致所有接口被权限拦截）、缺 ERP 的 BOM 副本、缺 PLM 工程变更审批链：

```bash
$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 09_role_permission.sql
$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 10_missing_copies.sql
$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 11_plm_ecn_workflow.sql
```

### 前置条件

| 条件 | 说明 |
|---|---|
| MySQL 8.0.43 已启动 | `infra/mysql8/start-mysql8.bat`（**不能是 5.7**，见 `mysql8/README.md`） |
| 客户端字符集 | 每条命令都要 `--default-character-set=utf8mb4`，否则中文注释与数据可能乱码 |
| 库必须已存在 | `02`~`11` 都在 `USE <库>` 之后建表，**必须先跑 `01`** |

### 三个已踩过的坑（脚本内已修复）

1. **跨库建表必须显式写库名** —— `06_warehouse.sql` 里 `USE mfg_ods` 之后切到 `mfg_dws`，中间的 `mfg_dwd` 表若写成 `CREATE TABLE dim_key_mapping` 会建到 `mfg_ods`。已改为 `CREATE TABLE IF NOT EXISTS mfg_dwd.dim_key_mapping`。
2. **BCrypt 前缀必须是 `$2a$`** —— Python `bcrypt` 库默认产 `$2b$`，而 Spring Security `BCryptPasswordEncoder` 用 `$2a$`。哈希体相同，替换前缀即可：
   ```python
   import bcrypt
   h = bcrypt.hashpw(b'Test@123456', bcrypt.gensalt(10)).decode()
   print(h.replace('$2b$', '$2a$', 1))
   ```
3. **`08` 是分组多次 `USE` 切换的** —— 依次访问 `mfg_auth` → `mfg_meta` → `mfg_dws` → `mfg_meta`，因此种子的 `INSERT` 必须紧跟其所属的 `USE`，不能重排段落。

### 核心设计（演示价值所在）

| 设计 | 内容 | 演示价值 |
|---|---|---|
| 主数据只读副本 | 17 张 `*_md_*` 表（`04`/`05` 建，加 `10` 的 2 张共 19 张含 ERP BOM），每张带 `master_version` | 可检测「副本是否落后于权威源」，是治理规则 **F03 主数据一致性** 的检测对象 |
| 主数据状态机 | 只有 `PUBLISHED` 才能被业务系统消费 | 草稿物料在 ERP 建采购订单时**下拉框里找不到** —— 前后对比最有说服力 |
| 审批引擎 | 7 条链（`08`）+ PLM 工程变更（`11`）= 8 条；13 个节点；**每个节点都落到真实的人**，无悬空审批节点 | 新增审批流只需插 `wf_definition` + `wf_node` 两行，不改代码 |
| 分级权限 | 36 角色按「执行岗 → 主管 → 经理/总监」分级，数据范围分本人/本部门/全部 | 切账号即见效：`fengyi` 冯一只看得到本车间工单 |
| 指标口径 | `mfg_dws.metric_definition` 含自然语言口径定义 | MCP `query_metric` 返回给 AI 的 `definition` 字段来源，保证 AI 能说明「数字怎么算的」 |

## 未实现 / 缺口

| # | 缺口 | 影响 | 说明 |
|---|---|---|---|
| 1 | `README.md` 执行清单漏了 `09`/`10`/`11` | **高** | 照 README 重建会得到一套**权限全空**的库：`sys_role_permission` 无数据 → 所有 `@PreAuthorize` 拦截；且缺 BOM 副本与 PLM 审批链 |
| 2 | ~~「库数」口径三处不一致~~ **已修复** | — | 统一为 18（实测 `CREATE DATABASE` 语句数）；`01_databases.sql` 与 `mysql8/README.md` 的「20 个」已改 |
| 3 | 无迁移/版本机制 | 中 | `10`/`11` 已是「后补脚本」形态，说明无登记机制；重建顺序完全靠人工维护 README |
| 4 | 无单条一键重建脚本 | 中 | 需要人肉敲 11 条 `mysql.exe < xx.sql`；`ops/scripts/` 计划中的 `reset.sh` 尚未实现 |
| 5 | 无基线校验 | 低 | 没有「执行后应得 18 库 99 表」的自动断言；各脚本尾部的 `SELECT` 只能肉眼比对 |
| 6 | 部分设计表仍未建 | 低 | `02` 只覆盖 12 类主数据中的一部分（如产品/工艺/计量单位已有表，但员工已建 `md_employee`）；`apps` 侧仍有「待实现」的主数据实体（见 `source-apps/mdm/README.md`） |
| 7 | 无降级/清理脚本 | 低 | 只有建，没有 `DROP`。要清干净得手工 `DROP DATABASE` |

> 相关：`README.md` 里「28 个岗位角色 / 29 个演示账号」的说法已过时 —— 实测 `08_seed_data.sql` 中 `sys_role` 与 `sys_user` 各 **36** 行，与 `README.md` 后文的「36 个角色 / 36 个演示账号」一致。
