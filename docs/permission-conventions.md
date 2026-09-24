# 权限码命名与鉴权约定

> 计划 00 · F1-04 · 制定于 2026-09-22
>
> 配套校验脚本：[`ops/check_permissions.py`](../ops/check_permissions.py)（F1-01 三方对照 + F1-05 基线校验）

## 一、权限码命名规范

```
<系统码>:<对象>:<动作>
```

| 段 | 取值 | 说明 |
|---|---|---|
| **系统码** | `mdm` `crm` `erp` `plm` `srm` `wms` `mes` `qms` `eam` `energy` `wf` `sys` | **小写**，与 `sys_permission.system_code`、`sys_user_system.system_code` 一致 |

> `sys` 是**平台治理**（用户/角色/部门/导航/审计/事件）的系统码，非十系统之一。
> `SecurityConfig` 的 URL 前缀规则只覆盖 10 个业务系统，没有 `SYSTEM_SYS` 门禁；
> 因此 `/api/admin/**`、`/api/events/**` 只靠「登录 + 方法级权限码」两层保护。
| **对象** | 大写下划线，如 `SALES_ORDER` `WORK_ORDER` `BOM` | 用业务对象名，不用表名 |
| **动作** | 见下方受控词表 | 不在词表内的动作需要在本文件登记后才可使用 |

### 动作受控词表

| 动作 | 语义 | 典型端点 |
|---|---|---|
| `VIEW` | 查询、列表、详情、导出 | `GET` |
| `CREATE` | 新建 | `POST`（集合） |
| `UPDATE` | 修改内容 | `PUT` / `PATCH` |
| `DELETE` | 物理或逻辑删除 | `DELETE` |
| `SUBMIT` | 提交审批 | `POST /{id}/submit` |
| `APPROVE` | 审批通过 / 驳回 | 审批动作 |
| `CLOSE` | 正常完结（终态） | `POST /{id}/close` |
| `VOID` | 作废（终态） | `POST /{id}/void` |
| `PUBLISH` | 发布（主数据专用） | `POST /{id}/publish` |
| `EXECUTE` | 执行一个过程性动作（下达、过账、结算等） | `POST /{id}/execute` |

> **动作粒度**：一个端点一个动作码，不复用粗码。例如「BOM 变更申请」用 `MDM:BOM:CHANGE`，
> 「普通修改 BOM」应另立码，不借前者——否则「能改 BOM」与「能提变更」会被混为一谈。

### 业务状态动作的例外

各单据特有的业务动作允许作为动作段使用，但必须是**该业务对象的既有状态机动作名**，
且在同一系统内保持一致拼写（避免当前 `SCRAP` 与 `SCRAPPED` 并存这类情况）。

### 已登记的动作词（词表外，按上条启用）

| 动作 | 来源 | 用于 |
|---|---|---|
| `START` `PAUSE` `FINISH` | 工单状态机 | `MES:WORK_ORDER:*` |
| `JUDGE` `DISPOSE` `VERIFY` | 检验/不合格闭环 | `QMS:INSPECTION:JUDGE`、`QMS:NCR:DISPOSE`、`QMS:CAPA:VERIFY` |
| `AWARD` `SEND` `ARRIVE` `INSPECT` | 采购/到货流程 | `SRM:RFQ:AWARD`、`SRM:PURCHASE:SEND`、`SRM:ASN:ARRIVE` |
| `CONFIRM` `REVIEW` | 调拨/盘点 | `WMS:TRANSFER:CONFIRM`、`WMS:COUNT:REVIEW` |
| `IMPLEMENT` | ECN 实施 | `PLM:ECN:IMPLEMENT` |
| `FOLLOW` | 跟进记录 | `CRM:LEAD:FOLLOW`、`CRM:OPPORTUNITY:FOLLOW` |
| `CONVERT` `CHANGE` `NEW_VERSION` | 单据转化与版本 | `CRM:LEAD:CONVERT`、`MDM:BOM:CHANGE`、`CRM:QUOTATION:NEW_VERSION` |
| `CHECK` | 只读校验/模拟 | `ERP:CREDIT:CHECK`、`ERP:ATP:CHECK` |
| `TRANSFER` `ADD_SIGN` `CC` | 审批引擎既有方法名（`transfer`/`addSign`/`copyTo`） | `WF:TASK:TRANSFER` 等 |
| `ASSIGN_*` `RESET_PASSWORD` `ENABLE` `DISABLE` `LOCK` `UNLOCK` | 平台治理的提权面 | `SYS:USER:*` |

> `SYS:USER:ASSIGN_ROLE` / `RESET_PASSWORD` / `SYS:ROLE:ASSIGN_PERMISSION` **刻意与普通 `UPDATE` 分码**——
> 它们是提权面，能改用户手机号的角色不应自动能授角色或重置密码。

## 二、鉴权三层

| 层 | 机制 | 位置 | 现状 |
|---|---|---|---|
| **① 系统访问权** | `SYSTEM_<大写系统码>` 权限，按 URL 前缀拦截 | `SecurityConfig` | 已生效 |
| **② 功能权限** | 方法级 `@PreAuthorize("hasAuthority('<权限码>')")` | 各 Controller 方法 | **本次铺开** |
| **③ 数据范围** | `@DataScope` + `DataScopeContext` | 查询方法 | **未接线**（机制两半都缺，见缺陷 #10，单独立项） |

第 ② 层是本次的重点：**每个业务端点都必须有方法级注解**，或在 `ops/check_permissions.py`
的 `WHITELIST` 中显式登记（登录、健康检查、API 文档）。

### 写法约定

```java
@PostMapping("/{id}/submit")
@PreAuthorize("hasAuthority('MDM:MATERIAL:CREATE')")
public ApiResponse<WfInstance> submit(@PathVariable Long id) { ... }
```

- **不要**加 `or hasRole('ADMIN')` 兜底。ADMIN 角色应在 `sys_role_permission` 中被显式授予
  所需权限码；用 OR 兜底会让「权限码是否真的被授予」无法被脚本校验，掩盖配置缺失。
- 需要多个码之一即可时用 `hasAnyAuthority('A','B')`，但优先为每个端点定一个**准确**的码。

## 三、授权口径

- 权限码定义在 `mfg_auth.sys_permission`，授权在 `mfg_auth.sys_role_permission`；
- **新增权限码必须同批授予角色**，否则就是「字典有码、无人有权」——这正是 V37/V38 留下的问题
  （见 `V39__p0_permission_backfill_and_regrant.sql` 的成因说明）；
- 通配授权（`p.perm_code LIKE 'QMS:%'`）只授到主管/经理级；**作业岗需要单独补授**，
  且补授语句必须放在**权限码已存在之后**的迁移里——`infra/db-init/` 通道（V1–V11）
  早于 Flyway 执行，通配语句赶不上后续新增的码。

## 四、迁移编号约定

- `infra/db-init/` 通道截至 V11 已 baseline，**不再改动**；
- 新增库变更一律走 `source-apps/bootstrap/src/main/resources/db/migration/`，从 `V39` 起；
- 编号区间分配见 [plans/README.md](plans/README.md)。

## 五、校验

```bash
python ops/check_permissions.py            # 打印三方对照与基线缺口
python ops/check_permissions.py --strict   # 有缺口时非零退出（CI 用）
```

脚本当前口径：被 `@PreAuthorize` 引用但字典无定义 → 报错；已定义但未授予任何角色 → 报错；
端点无注解且不在白名单 → 计入缺口。三条都应归零。
