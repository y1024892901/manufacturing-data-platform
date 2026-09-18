# infra/db-init/ — 数据库初始化

## 技术栈
MySQL 8.0.43（`D:\mysql8`，端口 3306）· 纯 SQL · 幂等设计（可重复执行）

## 执行方式

按编号顺序执行（已全部执行完毕，此表仅供重建参考）：

```bash
export MYSQL_PWD='<见 infra/.env>'
MYSQL=D:/mysql8/bin/mysql.exe

$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 01_databases.sql
$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 02_master_data.sql
$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 03_auth_workflow.sql
$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 04_business_systems.sql
$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 05_business_systems_2.sql
$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 06_warehouse.sql
$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 07_platform.sql
$MYSQL -uroot -P3306 -h127.0.0.1 --default-character-set=utf8mb4 < 08_seed_data.sql
```

| 文件 | 内容 | 目标库 |
|---|---|---|
| `01_databases.sql` | 创建 18 个数据库 | — |
| `02_master_data.sql` | 16 张主数据表 | `src_mdm` |
| `03_auth_workflow.sql` | 14 张权限与审批表 | `mfg_auth` |
| `04_business_systems.sql` | CRM / ERP / PLM / SRM | `src_crm` `src_erp` `src_plm` `src_srm` |
| `05_business_systems_2.sql` | MES / WMS / QMS / EAM / 能源 | `src_mes` `src_wms` `src_qms` `src_eam` `src_energy` |
| `06_warehouse.sql` | 数仓四层框架表 | `mfg_ods` `mfg_dwd` `mfg_dws` `mfg_ads` |
| `07_platform.sql` | 运维 / 元数据 / AI 应用表 | `mfg_ops` `mfg_meta` `mfg_app` |
| `08_seed_data.sql` | 账号 / 角色 / 审批链 / 规则 / 指标 | 全部 |

---

## 数据库总览（18 个库，96 张表）

| 层次 | 数据库 | 表数 | 用途 |
|---|---|---|---|
| **① 主数据平台** | `src_mdm` | 16 | 12 类主数据 + 组织人员 + 分发追踪 |
| **② 业务系统** | `src_crm` | 3 | 客户副本、商机、跟进 |
| | `src_erp` | 9 | 销售订单、生产订单、凭证、应收 + 4 张主数据副本 |
| | `src_mes` | 5 | 工单、报工、生产实绩 + 2 张副本 |
| | `src_wms` | 4 | 库存余额、出入库流水、库位 + 1 张副本 |
| | `src_eam` | 6 | 设备台账、状态时段、故障、维修、点检 + 1 张副本 |
| | `src_qms` | 4 | 检验单、不合格品、返工 + 1 张副本 |
| | `src_srm` | 4 | 采购订单、到货单 + 2 张副本 |
| | `src_plm` | 5 | 工程变更单 + 4 张副本（含 BOM） |
| | `src_energy` | 3 | 设备能耗、车间能耗 + 1 张副本 |
| **③ 数仓四层** | `mfg_ods` | 2 | ODS 表登记、行级变更日志 |
| | `mfg_dwd` | 1 | 代理键映射（建模表由 dw_kit 生成） |
| | `mfg_dws` | 1 | **指标口径定义表**（AI 的"口径锁"） |
| | `mfg_ads` | 5 | 治理问题、通过率、对账差异、血缘、术语表 |
| **④ 平台能力** | `mfg_meta` | 5 | 系统/表/字段/规则登记 + 变更审计 |
| | `mfg_auth` | 14 | 用户、角色、权限、数据范围、审批引擎 |
| | `mfg_app` | 5 | 向量库、LLM 日志、Agent 会话、MCP 审计 |
| | `mfg_ops` | 4 | 增量位点、采集日志、建模日志、**坏数据注入清单** |

---

## 核心设计一：主数据只读副本（17 张）

9 个业务系统**不维护主数据**，每个库里都有 `*_md_*` 前缀的只读副本表：

```
src_erp.erp_md_customer          ← MDM 分发的客户副本
src_erp.erp_md_material          ← MDM 分发的物料副本
src_mes.mes_md_routing_operation ← MDM 分发的工序副本
...
```

每张副本带 `master_version` 字段，可用于检测「副本是否落后于权威源」——
这是数仓治理规则 **F03（主数据一致性）** 的检测对象，也是解决
「一客多码」「物料编码不一致」这类制造业顽疾的机制。

| 业务库 | 主数据副本 |
|---|---|
| `src_erp` | 客户、供应商、物料、成本中心（4） |
| `src_plm` | 物料、产品、BOM头、BOM行（4） |
| `src_mes` | 物料、工艺路线工序（2） |
| `src_srm` | 供应商、物料（2） |
| `src_crm` / `src_wms` / `src_qms` / `src_eam` / `src_energy` | 各 1 张 |

## 核心设计二：主数据状态机

只有 `status='PUBLISHED'` 的主数据才能被业务系统消费：

```
DRAFT → PENDING → APPROVED → PUBLISHED → CHANGING → DISABLED
                    ↓
                 REJECTED（退回提交人）
```

演示价值：在草稿状态的物料，**在 ERP 建采购订单时下拉框里找不到**——
这个前后对比最能证明主数据管控是真实生效的。

## 核心设计三：审批引擎（7 条链，13 个节点，分级审批）

| 流程编码 | 审批链 | 级别 |
|---|---|---|
| `MDM_CUSTOMER_NEW` | 销售主管 → 应收会计（信用核查） | 两级 |
| `MDM_SUPPLIER_NEW` | 供应商质量工程师 → 采购主管 | 两级 |
| `MDM_MATERIAL_NEW` | 工艺主管 → 生产计划员 | 两级 |
| **`MDM_BOM_CHANGE`** | **工艺主管 → 生产主管 → 成本会计** | **三级（演示重点）** |
| `MDM_ROUTING_CHANGE` | 生产主管 | 单级 |
| `MDM_CREDIT_CHANGE` | 财务主管 → 财务总监 | 两级 |
| `ERP_PROD_ORDER_RELEASE` | 生产主管 | 单级 |

**表结构通用，7 条链靠数据定义**——新增审批流只需插 `wf_definition` + `wf_node` 两行，不改代码。
**每个节点都落到真实的人**（已校验，无悬空审批节点）。

## 核心设计四：组织架构与 36 个演示账号

**统一密码：`Test@123456`**（BCrypt `$2a$` 哈希，已生成并验证）

### 9 大部门

| 部门 | 人数 | 成员（角色） |
|---|---|---|
| **销售与市场中心** | 6 | 张伟/刘洋/钱一（销售代表）、陈静（销售助理）、王芳（**销售主管**）、李芳（**销售总监**） |
| **财务与成本中心** | 7 | 周八（**财务总监**）、吴倩（**财务主管**）、郑爽（成本会计）、冯琳（应收会计）、蒋楠（应付会计）、何欣（总账会计）、沈璐（出纳） |
| **供应链与采购中心** | 4 | 吴班（**采购总监**）、徐强（**采购主管**）、孙丽（采购员）、马超（供应商质量工程师） |
| **研发与工艺中心** | 3 | 赵六（工艺工程师）、周涛（**工艺主管**兼研发经理）、郭磊（产品工程师） |
| **生产制造中心** | 5 | 林峰（**生产经理**）、孙七（生产计划员）、杨帆（**生产主管**）、冯一（车间主任）、邓超（班组长） |
| **质量管理中心** | 3 | 郑十（质量工程师）、戴丽（**质量主管**）、钱多（质检员） |
| **设备与能源中心** | 3 | 陈峰（**设备主管**）、褚健（设备维修工）、卫兰（能源管理员） |
| **仓储物流中心** | 2 | 陈二（库管员）、游涛（**仓储主管**） |
| **信息技术中心** | 3 | admin（系统管理员）、杨明（主数据管理员）、徐静（数据分析师） |

### 36 个角色（分级：执行岗 → 主管 → 经理/总监）

```
销售：  销售代表 → 销售助理 → 销售主管 → 销售总监
财务：  成本/应付/应收/总账会计、出纳 → 财务主管 → 财务总监
采购：  采购员、SQE → 采购主管 → 采购总监
研发：  产品工程师、工艺工程师 → 工艺主管 → 研发经理
生产：  生产文员、班组长、车间主任 → 生产计划员 → 生产主管 → 生产经理
质量：  质检员 → 质量工程师 → 质量主管
设备：  设备维修工 → 设备主管 / 能源管理员
仓储：  库管员 → 仓储主管
平台：  主数据管理员、数据分析师、系统管理员
```

### 演示时的看点（切账号即见效）

| 场景 | 登录账号 | 看到什么 |
|---|---|---|
| 建客户申请 | `zhangwei` 张伟 | 提交后状态「待审批」，**不可编辑**；只能看自己负责的客户 |
| 审批客户 | `wangfang` 王芳 | 待办列表出现该申请，**[同意] [驳回]** |
| 信用核查 | `fenglin` 冯琳 | 王芳通过后，流转到自己待办 |
| **BOM 三级审批** | `zhaoliu`→`zhoutao`→`yangfan`→`zhengshuang` | **四级四账号接力，完整演示跨部门审批** |
| 数据范围 | `fengyi` 冯一 | 只看得到本车间的工单，其他车间不可见 |
| 采购下单 | `sunli` 孙丽 | 只能看自己下的采购订单 |

## 核心设计五：15 条治理规则 + 10 个指标口径

- **治理规则**（`mfg_meta.meta_rule`）：F01~F05 财务域、P01~P05 生产域、E01~E05 设备域，
  每条含规则表达式、严重级别（`error` 阻断下游 / `warn` 仅记录）、处理动作
- **指标口径**（`mfg_dws.metric_definition`）：含**自然语言口径定义**，
  这是 MCP `query_metric` 工具返回给 AI 的 `definition` 字段来源，
  保证 AI 回答时能说明「这个数字是怎么算的」

---

## 需要注意

### ⚠️ 密码哈希的前缀问题

种子脚本中的密码哈希使用 **`$2a$` 前缀**：

- `bcrypt` Python 库生成的是 `$2b$` 前缀，需替换为 `$2a$`
- **`$2a$` 是 Spring Security `BCryptPasswordEncoder` 的原生格式**，校验无障碍
- 哈希体（salt + digest）在两种前缀下完全相同，替换前缀不改变校验结果

生成命令：

```python
import bcrypt
h = bcrypt.hashpw(b'Test@123456', bcrypt.gensalt(10)).decode()
print(h.replace('$2b$', '$2a$', 1))     # → $2a$10$...
```

### ⚠️ 跨库建表的坑

`06_warehouse.sql` 中 `USE mfg_ods` 之后 `USE mfg_dws`，**中间创建 `mfg_dwd` 的表必须显式写库名**：

```sql
CREATE TABLE IF NOT EXISTS mfg_dwd.dim_key_mapping (...)   -- 正确
CREATE TABLE IF NOT EXISTS dim_key_mapping (...)           -- 错误：会建到 mfg_ods
```

MySQL 在单个连接中不能通过 `USE` 一次性切换多库，`CREATE TABLE` 不带库名时
使用当前 `USE` 的库。此为开发中实际踩到的坑，已修正。

### 表的前缀约定

| 前缀 | 含义 |
|---|---|
| `md_*` / `*_md_*` | 主数据（权威源 / 只读副本） |
| `fct_*` / `dim_*` / `brg_*` | 数仓 DWD 层的事实/维度/桥接表（由建模工具生成） |
| `dws_*` / `metric_*` | DWS 主题宽表 / 指标口径 |
| `ads_*` | ADS 应用数据集 |
| `ops_*` | 运维日志 |
| `meta_*` | 元数据配置 |
| `app_*` | AI 应用 |
| `wf_*` | 审批引擎 |
| `sys_*` | 权限与系统 |
