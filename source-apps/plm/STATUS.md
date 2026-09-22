# plm/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**工程变更链（ECR → 影响分析 → ECO → 三级审批 → ECN → 实施建 MDM 新版本草稿）是唯一打通并闭环的业务主线，产品结构/文档/基线只有目录级 CRUD；工艺规划、业务事件、统计报表、失效控制四项为零。**

## 已实现

- **变更链闭环**（`EngineeringChangeChainService`）：`plm_ecr` → `plm_impact_analysis` → `plm_eco` → `plm_ecn` → 向 `src_mdm` 建 BOM / 工艺路线新版本。
- **ECO 三级审批**：`PLM_ECO_APPROVAL`（研发经理 → 生产主管 → 成本会计），回调 `EcoApprovalCallback`；审批通过后自动生成 ECN，靠 `plm_ecn.ecn_no` 唯一键 + `ON DUPLICATE KEY UPDATE` 保证**回调重放不产生重复 ECN**。
- **ECN 两级审批**：`PLM_ECN_CHANGE`（研发经理 → 工艺主管），回调 `EngineeringChangeApprovalCallback`；驳回时**退回 `DRAFT` 并把驳回理由追加进 `change_reason`**，工程师可改后重提。
- **BOM 版本差异比较**：`PlmStructureController.compare()` 按 `child_material_code` 对齐左右版本，产出 `ADDED` / `REMOVED` / `CHANGED` / `UNCHANGED` 与数量差额。
- **BOM 只读副本浏览**：`plm_md_bom` / `plm_md_bom_line` 分页 + 头行详情。
- **受控文档与基线**：`plm_document`（`doc_no + version_no` 唯一，发布写 `released_by` / `released_at`）、`plm_engineering_baseline`（产品版本 + BOM 版本 + 工艺版本组合）。
- **已发布数据不可直改**：`PlmCatalogService.ensureEditable()` 只允许 `DRAFT` / `ACTIVE` 被修改，其余抛「已发布数据不可直接修改，请通过工程变更建立新版本」。
- **前端页面**（`apps/web-portal/src/modules/plm/`）：`PlmCatalogView.vue`（`/plm/families`、`/plm/products`、`/plm/baselines`、`/plm/documents`）、`PlmBomView.vue`（`/plm/boms`，含差异比较弹窗）、`ChangeChainView.vue`（`/plm/ecns`，ECR/ECO/ECN 三个页签）。

### 分层文件统计

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `domain/` | **0 — 未实现** | 无枚举、无状态机类 |
| `entity/` | 1 | `EngineeringChange`（`src_plm.plm_ecn`） |
| `repo/` | 1 | `EngineeringChangeRepository` |
| `service/` | 3 | `EngineeringChangeService`、`EngineeringChangeChainService`、`PlmCatalogService` |
| `controller/` | 4 | `EngineeringChangeController`、`EngineeringChangeChainController`、`PlmCatalogController`、`PlmStructureController` |
| `workflow/` | **0 — 未实现** | 回调改放在 `callback/`：`EcoApprovalCallback`、`EngineeringChangeApprovalCallback` |
| `integration/` | **0 — 未实现** | 全模块零 `BusinessEventService` 调用 |
| `query/` | **0 — 未实现** | 查询 SQL 内联在 controller / service |
| 其他 | 1 | `package-info.java` |

## 接口清单

`EngineeringChangeChainController` · `@RequestMapping("/api/plm")`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/plm/{type:ecrs\|ecos}` | ECR / ECO 分页（关键字匹配 `*_no` 与 `*_title`）。正则为 `ecrs\|ecos`，**不匹配 `ecns`** |
| POST | `/api/plm/ecrs` | 新建 ECR（`ecrNo` / `ecrTitle` / `productCode` / `problemDesc` / `changeReason` 必填，`urgency` 默认 `NORMAL`） |
| PUT | `/api/plm/ecrs/{id}/impact-analysis` | **覆盖式**重写影响分析条目，并把 ECR 置 `ANALYZED` |
| POST | `/api/plm/ecrs/{id}/create-eco` | 由 ECR 生成 ECO，要求 ECR 已 `ANALYZED`，并把 ECR 置 `ECO_CREATED` |
| POST | `/api/plm/ecos/{id}/submit` | 提交 ECO 审批，要求 `DRAFT`，置 `REVIEWING` 并启动 `ECO` 流程 |
| POST | `/api/plm/ecos/{id}/create-ecn` | 由已批准 ECO 生成 ECN（编号 `ECN-%05d`，状态直接 `APPROVED`） |
| POST | `/api/plm/ecns/{id}/implement` | 实施 ECN：`BOM` 目标复制 `md_bom` + `md_bom_line` 新版本；`ROUTING` 目标复制 `md_routing` + `md_routing_operation` 新版本 |

`EngineeringChangeController` · `@RequestMapping("/api/plm/ecns")`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/plm/ecns` | ECN 分页（JPA `findAll`，`size` 上限 200） |
| GET | `/api/plm/ecns/{id}` | ECN 详情（不存在抛 `EntityNotFoundException`） |
| POST | `/api/plm/ecns` | 新建 ECN · `@PreAuthorize("hasAuthority('PLM:ECN:CREATE')")` |
| POST | `/api/plm/ecns/{id}/submit` | 提交评审（仅 `DRAFT`）· `PLM:ECN:CREATE` |

`PlmCatalogController` · `@RequestMapping("/api/plm/catalog")`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/plm/catalog/{type}` | 分页；`type` ∈ `families` / `versions` / `documents` / `baselines` |
| POST | `/api/plm/catalog/{type}` | 新建 · `hasAnyAuthority('PLM:PRODUCT:UPDATE','PLM:DOCUMENT:UPDATE','PLM:ECN:CREATE') or hasRole('ADMIN')` |
| PUT | `/api/plm/catalog/{type}/{id}` | 修改（`ensureEditable` 拦已发布）· 同上 |
| POST | `/api/plm/catalog/{type}/{id}/release` | 发布；`families` 直接拒绝（产品族无需发布）· 同上 |

`PlmStructureController` · `@RequestMapping("/api/plm/structures")`（直接持有 `JdbcTemplate`，无 service）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/plm/structures/boms` | BOM 副本分页（关键字匹配 `bom_code` / `product_code`，按 `synced_at` 倒序） |
| GET | `/api/plm/structures/boms/{code}/{version}` | BOM 头 + 行，返回 `{header, lines}` |
| GET | `/api/plm/structures/boms/compare?code=&left=&right=` | 两版本差异比较（`ADDED` / `REMOVED` / `CHANGED` / `UNCHANGED` + `deltaQty`） |

**访问控制**：`SecurityConfig` 对 `/api/plm/**` 要求 authority `SYSTEM_PLM`（来自 `sys_user_system`，`JwtAuthenticationFilter` 注入）；业务权限点再叠加 `@PreAuthorize`。

## 数据表

**`@Table` 注解中的库名.表名（JPA 实体）**

| 库.表 | 实体 |
|---|---|
| `src_plm.plm_ecn` | `EngineeringChange` |

**JdbcTemplate 触及的表**

| 库.表 | 读/写 | 位置 | 建表来源 |
|---|---|---|---|
| `src_plm.plm_ecr` | 读写 | `EngineeringChangeChainService` | `V17__p1_plm_change_chain.sql` |
| `src_plm.plm_impact_analysis` | 读写（每次 DELETE 后重插） | 同上 | `V17` |
| `src_plm.plm_eco` | 读写 | 同上 | `V17` |
| `src_plm.plm_ecn` | 读写（与 JPA 实体共用同表） | 同上 | `04_business_systems.sql` + `V17` ALTER |
| `src_plm.plm_product_family` | 读写 | `PlmCatalogService` | `V14__p1_plm_catalog.sql` |
| `src_plm.plm_product_version` | 读写 | 同上 | `V14` |
| `src_plm.plm_document` | 读写 | 同上 | `V14` |
| `src_plm.plm_engineering_baseline` | 读写 | 同上 | `V14` |
| `src_plm.plm_md_bom` / `plm_md_bom_line` | **只读** | `PlmStructureController` | `04_business_systems.sql` |
| `src_mdm.md_bom` / `md_bom_line` / `md_routing` / `md_routing_operation` | 读旧版本 + 写新版本草稿 | `EngineeringChangeChainService.implement()` | `02_master_data.sql` |

> `src_plm.plm_md_material` / `plm_md_product` / `plm_md_routing` / `plm_md_routing_operation` 由 MDM 分发侧写入（`MasterDataDistributor`），**PLM 模块自身不读不写、无接口暴露**。

## 对照最低验收

| 验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| 1. 至少 5 个本职模块具有实体、服务、接口和角色权限 | **部分实现** | 有服务与接口的模块：产品结构（`PlmCatalogService`）、工程变更（`EngineeringChangeChainService` + `EngineeringChangeService`）、文档管理、配置管理、BOM/工艺发布（差异比较）——共 5 个；但**实体层只有 1 个 JPA 实体**（`EngineeringChange`），其余全为 `Map` + 原生 SQL。角色权限：`08_seed_data.sql:180-183` 定义 `PLM:PRODUCT:VIEW` / `PLM:PRODUCT:CREATE` / `PLM:ECN:CREATE` / `PLM:ECN:APPROVE`，`09_role_permission.sql:236-240` 授予 `PRODUCT_ENGINEER`、`PROCESS_ENGINEER`、`PROCESS_SUPERVISOR`、`RND_MANAGER`；**缺口**：代码里用到的 `PLM:PRODUCT:UPDATE`、`PLM:DOCUMENT:UPDATE` 在任何 SQL 中都未定义，实际只能靠 `PLM:ECN:CREATE` 或 `ADMIN` 角色兜底 |
| 2. 至少 3 类核心单据可以从创建流转到关闭或作废 | **部分实现** | 3 类单据可流转：ECR（`DRAFT` → `ANALYZED` → `ECO_CREATED`）、ECO（`DRAFT` → `REVIEWING` → `APPROVED` / `REJECTED`）、ECN（`DRAFT` → `REVIEWING` → `APPROVED` → `IMPLEMENTED`，驳回退回 `DRAFT`；变更链侧生成的 ECN 直接 `APPROVED` → `IMPLEMENTED`）。**缺口**：三类都**没有关闭/作废动作**——`plm_ecn.ecn_status` 注释里的 `CANCELED` 无任何代码路径可达；ECR/ECO 也没有取消接口 |
| 3. 至少 1 个审批流程和 1 个异常处理闭环 | **部分实现** | 审批流程**有两条**：ECN 两级（`PLM_ECN_CHANGE`：研发经理 `RND_MANAGER` → 工艺主管 `PROCESS_SUPERVISOR`，见 `infra/db-init/11_plm_ecn_workflow.sql`）、ECO 三级（`PLM_ECO_APPROVAL`：研发经理 → 生产主管 `PROD_SUPERVISOR` → 成本会计 `COST_ACCOUNTANT`，见 `V17`），回调类 `EcoApprovalCallback` / `EngineeringChangeApprovalCallback`；影响分析（`plm_impact_analysis`）充当变更前评估。**异常处理闭环未实现**：无失败重试、无超时升级、无对账 |
| 4. 至少 2 个向其他系统发送或消费的幂等业务事件 | **未实现** | PLM **零业务事件**——全模块无 `BusinessEventService` 调用，`integration/` 层为空。变更实施是**跨库直写** `src_mdm`（`EngineeringChangeChainService.implement()`），不是事件；唯一具备幂等语义的动作是审批回调经 `ecn_no` 唯一键 + `ON DUPLICATE KEY UPDATE` 去重，但那是本地回调而非跨系统事件 |
| 5. 至少 1 页本系统运营查询或统计报表 | **部分实现** | 有运营查询与比较报表：变更链台账分页 `/api/plm/{ecrs\|ecos}` 与 `/api/plm/ecns`、BOM 副本分页与头行详情、BOM 两版本差异比较 `/api/plm/structures/boms/compare`；前端 6 个路由页（`/plm/families`、`/plm/products`、`/plm/baselines`、`/plm/documents`、`/plm/boms`、`/plm/ecns`）。**缺口**：没有任何聚合统计接口（无变更数量、变更周期、实施率等指标） |

## 未实现 / 缺口

1. **集成事件为零** —— `integration/` 空，ECN 实施后 ERP/MES 不会收到任何通知，只能等 MDM 重新发布再分发。
2. **工艺规划零接口** —— `plm_md_routing` / `plm_md_routing_operation` 只是 MDM 分发的只读副本，PLM 内没有 MBOM、工序、工时、工装、工作中心的任何服务或接口。
3. **`EngineeringChangeService.implement()` 是死代码** —— 该方法有实现但 `EngineeringChangeController` 没有 `/implement` 映射；前端「实施」实际走 `EngineeringChangeChainController` 的 `/api/plm/ecns/{id}/implement`。
4. **两套 ECN 实现互相看不见** —— JPA 实体 `EngineeringChange` 未映射 `ecr_id` / `eco_id` / `target_type` / `target_version`，因此 `GET /api/plm/ecns/{id}` 看不到变更链写入的变更目标；`GET /api/plm/ecns`（JPA）与 `GET /api/plm/{ecrs|ecos}` 的返回结构也不一致。
5. **路由正则漏 `ecns`** —— `@GetMapping("/{type:ecrs|ecos}")` 只匹配 `ecrs` / `ecos`，链服务的 `page("ecns")` 分支无法通过该端点访问。
6. **权限点未种子化** —— `PLM:PRODUCT:UPDATE`、`PLM:DOCUMENT:UPDATE` 在 SQL 中不存在；`PLM:ECN:APPROVE` 已定义但没有任何 `@PreAuthorize` 引用。
7. **失效控制 / 回退 / 发布记录未实现** —— 实施后写出的 `md_bom` / `md_routing` 新版本为 `status='DRAFT'`、`is_current=0`，不自动失效旧版本（这是「不绕开主数据治理」的刻意取舍，但失效控制本身仍属空白）。
8. **无状态时间轴与操作审计** —— ECR/ECO/ECN 没有状态变更历史表，未关联 `wf_action_log`，无导出接口。
9. **无「作废/取消」动作**；无任何统计报表接口。
10. **`EcnView.vue` 未挂路由** —— `apps/web-portal/src/modules/plm/EcnView.vue` 存在但 `router/index.ts` 的 `/plm/ecns` 指向 `ChangeChainView.vue`。
11. **无自动化测试** —— `tests/unit`、`tests/integration`、`tests/e2e` 目录下只有 README，没有针对 PLM 的用例。
