# mdm/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**mdm 的分发主线（创建 → 审批 → 发布 → 分发 → 重试/对账）已完整闭环、可直接演示，是十系统中完成度最高的一环；但「治理工作台」的软件工程部分严重不足——12 类主数据只落地了 6 类（客户、供应商、物料、产品、BOM、工艺路线），数据所有者 / 数据质量 / 重复检测、序列号策略、银行与工厂组织均未实现，且 5 个权限点只写在 `@PreAuthorize` 里却从未在数据库种子中定义。**

## 已实现

### 分层文件统计

共 44 个 Java 文件、2784 行（含 `package-info.java`）。

| 分层 | 文件数 | 行数 | 关键类 |
|---|---|---|---|
| `domain/` | 3 | 225 | `MasterDataEntity`（统一契约接口，含 `markPending()` / `publish()` / `markRejected()` 默认方法）、`MasterDataStatus`（7 态状态机）、`BizType`（12 类业务类型 + 审批流程编码 + 分发目标） |
| `entity/` | 10 | 858 | `Material`、`Customer`、`Supplier`、`Product`、`MaterialCategory`、`Bom`、`BomLine`、`BomSubstitute`、`Routing`、`RoutingOperation` |
| `repo/` | 10 | 228 | `MaterialRepository`、`CustomerRepository`、`SupplierRepository`、`ProductRepository`、`MaterialCategoryRepository`、`BomRepository`、`BomLineRepository`、`BomSubstituteRepository`、`RoutingRepository`、`RoutingOperationRepository` |
| `service/` | 9 | 973 | `MasterDataService`（状态机 + 提审 + 发布）、`MasterDataDistributor`（450 行，9 个目标系统写入）、`MdmOutboxService`（Outbox/Inbox/重试/对账）、`BomService`、`RoutingService`、`ProductService`、`MdmGovernanceService`、`MdmReferenceService`、`DistributionMonitorService` |
| `controller/` | 8 | 345 | `MaterialController`（203 行）、`BomController`、`RoutingController`、`ProductController`、`PartnerMasterController`、`DistributionController`、`MdmGovernanceController`、`MdmReferenceController` |
| `dto/` | 2 | 18 | `BomCommand`、`RoutingCommand`（record + Bean Validation） |
| `callback/` | 1 | 135 | `MdmApprovalCallback`（审批引擎与业务的接合点） |
| `workflow/` | 0 | 0 | **目录不存在** —— 审批策略由 `shared-workflow` 的 `ApprovalEngine` 承担，本模块只用 `callback/` 接回调 |
| `integration/` | 0 | 0 | **目录不存在** —— 职责物理上落在 `service/MasterDataDistributor` + `service/MdmOutboxService` |
| `query/` | 0 | 0 | **目录不存在** —— 统计查询散在 `MaterialController.stats()`、`MdmGovernanceService.page()`、`DistributionMonitorService.latest()`、`MdmOutboxService.page()` |

### 核心业务规则

**1. 只有 `PUBLISHED` / `CHANGING` 的主数据可被业务系统消费** —— 这是整个模块存在的理由。
执行点有两处：`MasterDataStatus.isConsumable()` 只对这两个状态返回 `true`；`MasterDataService.assertConsumable()` 在引用前强校验，未发布时抛 `MASTER_DATA_NOT_PUBLISHED`。查询侧对应 `MaterialRepository.findConsumable()` / `BomRepository.findConsumable()` / `ProductRepository.findByStatusInOrderByProductCode(["PUBLISHED","CHANGING"])` 三条 JPQL，即 `GET /consumable` 接口的实现点。

**2. 7 态状态机**（`MasterDataStatus`）

| 状态 | 可编辑 | 下游可见 | 说明 |
|---|---|---|---|
| `DRAFT` | 是 | 否 | 新建默认状态 |
| `PENDING` | 否 | 否 | 审批中，锁定编辑 |
| `APPROVED` | 否 | 否 | 审批通过待发布（预留中间态，代码中无流转进入点） |
| `PUBLISHED` | 否 | **是** | 唯一可被引用的状态 |
| `CHANGING` | 否 | 是 | 变更审批中，旧版本仍可用 |
| `REJECTED` | 是 | 否 | 被驳回，可改后重提 |
| `DISABLED` | 否 | 否 | 停用，历史单据仍能查到名称 |

**3. 已发布的数据不能直接改，只能走变更审批。** `MasterDataService.update()` 检查 `status.isEditable()`（仅 `DRAFT` / `REJECTED`），否则抛 `MASTER_DATA_INVALID_STATE`；已发布记录提审时 `MasterDataEntity.markPending()` 判断当前是 `PUBLISHED` 则置 `CHANGING` 而非 `PENDING`，变更期间旧版本仍可被下游引用。

**4. 下游副本只新增/更新，绝不删除。** `MasterDataDistributor` 全部用 `INSERT ... ON DUPLICATE KEY UPDATE`；唯一例外是 BOM 行与工艺路线工序——写前先 `DELETE` 旧行再整体重写（`writeErpBomLines`、`writeRoutingOperations`），因为行号/工序号可能变动，避免残留脏行。

**5. 分发失败被单点隔离，不上抛。** `distributeTo()` 用 try-catch 包住每个目标系统的写入，失败只写一条 `FAILED` 分发日志并返回 `DistResult.fail()`。**注意：该类刻意不使用 `@Transactional(REQUIRES_NEW)`**——类注释明确指出内层事务失败会被标记 rollback-only，导致外层提交时抛 `UnexpectedRollbackException`，反而让「ERP 分发失败」回滚掉整个审批动作。README 中「`distributeTo()` 标 `REQUIRES_NEW`」的描述与代码不一致，以代码为准。

**6. 发布走 Outbox 异步分发，不再同步直发。** `MasterDataService.onApproved()` 只做「置 `PUBLISHED` + 版本号 +1」并调 `MdmOutboxService.enqueue()` 在**同一事务**内写 `md_outbox`（每目标系统一行 `md_inbox`，用 `INSERT IGNORE` + `UNIQUE(event_id,target_system)` 保幂等），随后由 `@Scheduled(fixedDelay=1000)` 的 `dispatch()` 在一秒内推送到各目标系统。因此「审批通过 → 下游立即可见」存在**最多约 1 秒延迟**，`MdmApprovalCallback` 里那段统计分发结果的日志实际上永远收到空列表（`onApproved` 返回 `List.of()`）。

**7. 重试与对账构成异常闭环。** `md_inbox` 失败记 `FAILED` 且 `retry_count+1`；事件整体超过 3 次置 `DEAD`；`retry(eventId)` 把 outbox 重置 `RETRYING`、inbox 非 `SUCCESS` 的复位 `PENDING`；`reconcileEvent()` 逐目标写 `md_reconciliation`，有写入行数记 `MATCH`、无行数记 `CHECK`。

**8. 提交时固化数据快照。** `MasterDataService.submitForApproval()` 用 `ObjectMapper` 把 `bizType/code/name/versionNo/changeReason/snapshotAt` 序列化进流程实例，审批人看到的是提交那一刻的数据。

**9. 基础字典类无需审批，直接发布。** `BizType.needsApproval()` 对 `approvalFlow == null` 的类型（物料分类、计量单位、组织、成本中心、会计科目、员工）返回 `false`，`submitForApproval` 会直接 `publish()` 并分发，返回 `null`（接口回「该类型无需审批，已直接发布」）。

**10. 生产版本保存时强制三方校验。** `MdmGovernanceService.validateProduction()` 要求产品、BOM、工艺路线**三者全部已发布**（`status IN ('PUBLISHED','CHANGING')`），任一未发布即抛 `MASTER_DATA_NOT_PUBLISHED`，且生产版本的 `UPDATE` 带 `AND status='DRAFT'` 条件，已发布的生产版本不可改。

**11. BOM 的约束。** 提交前必须至少一条用料明细（`BomService.submit`）；每个子件物料、每个替代料都必须已发布（`assertConsumable`）；替代料不得与主料相同、基本单位必须与 BOM 行单位一致；替代料只能挂在可编辑状态的 BOM 上。发布后 `MdmApprovalCallback` 才调 `BomService.makeCurrent()` 切换当前版本——审批中的新版本不影响正在执行的生产订单。

**12. 循环依赖的解法。** `ApprovalEngine → ApprovalCallback → MasterDataService → ApprovalEngine` 成环，引擎侧用 `ObjectProvider<ApprovalCallback>` 延迟获取回调；回调侧 `publishIfExists()` 先确认实体存在，不存在只记 `warn` 跳过而**绝不抛异常**，否则会把调用方事务标记回滚，导致「审批已通过但状态没保存」。

**13. 权限双层校验。** URL 层 `SecurityConfig` 要求 `/api/mdm/**` 具备 `SYSTEM_MDM`（由 `JwtAuthenticationFilter` 从用户被授权的系统列表推导，非数据库权限点）；方法层用 `@PreAuthorize("hasAuthority('MDM:XXX:YYY')")`。

## 接口清单

8 个 `@RestController`，共 **61 个端点**。

### `MaterialController` — `/api/mdm/materials`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mdm/materials` | 分页查询；`status` / `materialType` / `keyword` 三选一过滤 |
| GET | `/api/mdm/materials/consumable` | **★ 可选物料（仅 `PUBLISHED`/`CHANGING`）** |
| GET | `/api/mdm/materials/{id}` | 详情 |
| GET | `/api/mdm/materials/by-code/{code}` | 按编码查（含引用前 `assertConsumable` 校验） |
| POST | `/api/mdm/materials` | 新建（草稿）· `MDM:MATERIAL:CREATE` |
| PUT | `/api/mdm/materials/{id}` | 修改（仅草稿/驳回可改）· `MDM:MATERIAL:UPDATE` |
| POST | `/api/mdm/materials/{id}/submit` | 提交审批 · `MDM:MATERIAL:CREATE` |
| POST | `/api/mdm/materials/{id}/redistribute` | 手动重新分发 · `MDM:DISTRIBUTE:RETRY` |
| GET | `/api/mdm/materials/stats` | 按状态统计（draft/pending/published/rejected/total） |

### `BomController` — `/api/mdm/boms`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mdm/boms` | 分页查询（`status` 过滤） |
| GET | `/api/mdm/boms/consumable` | 可选 BOM（`PUBLISHED`/`CHANGING` 且 `current=true`） |
| GET | `/api/mdm/boms/{id}` | 详情（含用料明细，按 `lineNo` 排序） |
| POST | `/api/mdm/boms` | 新建（表头+明细一次写入）· `MDM:BOM:CREATE` |
| PUT | `/api/mdm/boms/{id}` | 修改 · `MDM:BOM:UPDATE` |
| POST | `/api/mdm/boms/{id}/submit` | 提交审批 · `MDM:BOM:CREATE` |
| GET | `/api/mdm/boms/{bomId}/lines/{lineId}/substitutes` | 替代料列表（按优先级） |
| POST | `/api/mdm/boms/{bomId}/lines/{lineId}/substitutes` | 新增替代料 · `MDM:BOM:UPDATE` |
| DELETE | `/api/mdm/boms/{bomId}/lines/{lineId}/substitutes/{id}` | 停用替代料（置 `INACTIVE`）· `MDM:BOM:UPDATE` |

### `RoutingController` — `/api/mdm/routings`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mdm/routings` | 分页查询（`keyword` / `status` 过滤） |
| GET | `/api/mdm/routings/{id}` | 详情（含工序，按 `opSeq` 排序） |
| POST | `/api/mdm/routings` | 新建 · `MDM:ROUTING:UPDATE` 或 `ROLE_ADMIN` |
| PUT | `/api/mdm/routings/{id}` | 修改 · `MDM:ROUTING:UPDATE` 或 `ROLE_ADMIN` |
| POST | `/api/mdm/routings/{id}/submit` | 提交审批（至少一道工序）· 同上 |

### `ProductController` — `/api/mdm/products`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mdm/products` | 分页查询（`keyword` / `status` 过滤） |
| GET | `/api/mdm/products/{id}` | 详情 |
| GET | `/api/mdm/products/consumable` | 可选产品（`PUBLISHED`/`CHANGING`） |
| POST | `/api/mdm/products` | 新建 · `MDM:MATERIAL:CREATE` 或 `MDM:PRODUCT:CREATE` 或 `ROLE_ADMIN` |
| PUT | `/api/mdm/products/{id}` | 修改 · `MDM:MATERIAL:UPDATE` 或 `MDM:PRODUCT:UPDATE` 或 `ROLE_ADMIN` |
| POST | `/api/mdm/products/{id}/submit` | 提交审批 · 同 POST |

### `PartnerMasterController` — `/api/mdm`（客户/供应商）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mdm/customers` | 客户分页查询（`keyword` / `status` 过滤） |
| POST | `/api/mdm/customers` | 新建客户（编码查重） |
| GET | `/api/mdm/customers/{id}` | 客户详情 |
| PUT | `/api/mdm/customers/{id}` | 修改客户 |
| DELETE | `/api/mdm/customers/{id}` | 停用客户（置 `DISABLED`） |
| POST | `/api/mdm/customers/{id}/submit` | 提交审批 |
| GET | `/api/mdm/suppliers` | 供应商分页查询 |
| POST | `/api/mdm/suppliers` | 新建供应商（编码查重） |
| GET | `/api/mdm/suppliers/{id}` | 供应商详情 |
| PUT | `/api/mdm/suppliers/{id}` | 修改供应商 |
| DELETE | `/api/mdm/suppliers/{id}` | 停用供应商（置 `DISABLED`） |
| POST | `/api/mdm/suppliers/{id}/submit` | 提交审批 |

> 本类**没有任何 `@PreAuthorize`**，仅受 `/api/mdm/**` 的 `SYSTEM_MDM` URL 层门禁保护。

### `MdmGovernanceController` — `/api/mdm`（治理工作台）

`{type}` 取值 `warehouses` / `work-centers` / `production-versions`；`{partnerType}` 取值 `customers` / `suppliers`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mdm/{type}` | 仓库/工作中心/生产版本分页查询 |
| POST | `/api/mdm/{type}` | 新建 |
| PUT | `/api/mdm/{type}/{id}` | 修改（生产版本带 `status='DRAFT'` 条件） |
| DELETE | `/api/mdm/{type}/{id}` | 停用（置 `DISABLED`） |
| GET | `/api/mdm/{partnerType}/{partnerId}/contacts` | 联系人列表（主联系人优先） |
| POST | `/api/mdm/{partnerType}/{partnerId}/contacts` | 新增联系人（设主联系人时先清其他主标记） |
| PUT | `/api/mdm/{partnerType}/{partnerId}/contacts/{id}` | 修改联系人 |
| DELETE | `/api/mdm/{partnerType}/{partnerId}/contacts/{id}` | 停用联系人 |
| POST | `/api/mdm/merge` | **合并主数据**（被合并方置 `DISABLED`，写 `md_merge_record` + `md_code_mapping` 旧码映射） |
| GET | `/api/mdm/governance/{kind}` | 台账查询，`{kind}` ∈ `merges` / `mappings` / `history` |

### `MdmReferenceController` — `/api/mdm/reference`（基础字典）

`{type}` 取值 `categories` / `units` / `organizations` / `cost-centers` / `subjects`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mdm/reference/{type}` | 列表（最多 500 条）· `MDM:MATERIAL:VIEW` 或 `MDM:ROUTING:VIEW` 或 `ROLE_ADMIN` |
| POST | `/api/mdm/reference/{type}` | 新建（直接置 `PUBLISHED`，编码重复抛错）· `MDM:MATERIAL:CREATE` 或 `MDM:ROUTING:UPDATE` |
| PUT | `/api/mdm/reference/{type}/{id}` | 修改（`version_no+1`）· `MDM:MATERIAL:UPDATE` 或 `MDM:ROUTING:UPDATE` |
| DELETE | `/api/mdm/reference/{type}/{id}` | 停用 · 同 PUT |

### `DistributionController` — `/api/mdm/distributions`（分发中心）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mdm/distributions` | 分发日志分页（`md_distribution_log`，`status` 过滤）· `MDM:MATERIAL:VIEW` 或 `MDM:BOM:VIEW` 或 `ROLE_ADMIN` |
| POST | `/api/mdm/distributions/{id}/retry` | 单条分发重试（按日志回查实体与目标系统重推）· `MDM:MATERIAL:PUBLISH` 或 `MDM:BOM:PUBLISH` 或 `ROLE_ADMIN` |
| GET | `/api/mdm/distributions/events` | Outbox 事件分页（无 `@PreAuthorize`） |
| GET | `/api/mdm/distributions/events/{eventId}` | 事件详情，含 `targets` 与 `reconciliations` |
| POST | `/api/mdm/distributions/events/{eventId}/retry` | 事件级重试（outbox 复位 `RETRYING`、inbox 复位 `PENDING`） |
| POST | `/api/mdm/distributions/events/{eventId}/reconcile` | 一致性对账，写 `md_reconciliation` |

## 数据表

### `src_mdm`（主数据库，MDM 自有）

JPA 实体映射的 10 张（`@Table(catalog = "src_mdm")`）：

| 库.表 | 实体 |
|---|---|
| `src_mdm.md_customer` | `Customer` |
| `src_mdm.md_supplier` | `Supplier` |
| `src_mdm.md_material` | `Material` |
| `src_mdm.md_material_category` | `MaterialCategory` |
| `src_mdm.md_product` | `Product` |
| `src_mdm.md_bom` | `Bom` |
| `src_mdm.md_bom_line` | `BomLine` |
| `src_mdm.md_bom_substitute` | `BomSubstitute` |
| `src_mdm.md_routing` | `Routing` |
| `src_mdm.md_routing_operation` | `RoutingOperation` |

原生 SQL（`JdbcTemplate`）读写的 17 张：

| 库.表 | 写入者 |
|---|---|
| `src_mdm.md_warehouse`、`src_mdm.md_work_center`、`src_mdm.md_production_version` | `MdmGovernanceService` |
| `src_mdm.md_partner_contact` | `MdmGovernanceService` |
| `src_mdm.md_merge_record`、`src_mdm.md_code_mapping`、`src_mdm.md_change_history` | `MdmGovernanceService`（`md_change_history` 仅查询，无写入） |
| `src_mdm.md_unit`、`src_mdm.md_org_unit`、`src_mdm.md_cost_center`、`src_mdm.md_account_subject` | `MdmReferenceService` |
| `src_mdm.md_distribution_log` | `MasterDataDistributor`（写）、`DistributionMonitorService`（读） |
| `src_mdm.md_outbox`、`src_mdm.md_inbox`、`src_mdm.md_reconciliation` | `MdmOutboxService` |

DDL 位置：`infra/db-init/02_master_data.sql`（16 张）与 `source-apps/bootstrap/src/main/resources/db/migration/V16__p1_mdm_governance_workflow.sql`（11 张）。`src_mdm` 库在 `infra/db-init/01_databases.sql` 创建。

> 有表无代码：`src_mdm.md_employee`（员工）与 `src_mdm.md_change_request` 只有 DDL，无任何 Java 引用。

### 下游只读副本（MDM 是唯一写入者）

20 张副本表，全部由 `MasterDataDistributor` 写入：

| 目标库.表 | 来源主数据 |
|---|---|
| `src_crm.crm_md_customer` | 客户 |
| `src_erp.erp_md_customer` / `erp_md_supplier` / `erp_md_material` / `erp_md_product` / `erp_md_bom` / `erp_md_bom_line` | 客户、供应商、物料、产品、BOM |
| `src_srm.srm_md_supplier` / `srm_md_material` | 供应商、物料 |
| `src_plm.plm_md_material` / `plm_md_product` / `plm_md_bom` / `plm_md_bom_line` / `plm_md_routing` / `plm_md_routing_operation` | 物料、产品、BOM、工艺路线 |
| `src_mes.mes_md_material` / `mes_md_routing_operation` | 物料、工艺路线工序 |
| `src_wms.wms_md_material` | 物料（`safety_stock` / `is_batch_managed`） |
| `src_qms.qms_md_material` | 物料（`is_inspection_required` / `inspection_standard`） |
| `src_eam.eam_md_material` | 物料（备件） |

### 流程与权限种子

- 审批流程：`infra/db-init/08_seed_data.sql` 的 `wf_definition` / `wf_node`，含 5 条 MDM 流程（见下表）
- 权限点：`infra/db-init/08_seed_data.sql:107-123` 定义 15 个 `MDM:*` 权限，`infra/db-init/09_role_permission.sql` 授权到 20+ 角色

## 对照最低验收

| 验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| **1. 至少 5 个本职模块具有实体、服务、接口和角色权限** | ✅ 达成 | 目录 6 个模块全部落地：治理工作台（`MdmGovernanceService`）、业务伙伴（`Customer`/`Supplier`/`md_partner_contact`）、产品与物料（`Material`/`Product`/`MaterialCategory`）、工程主数据（`Bom`/`Routing`/`md_work_center`/`md_production_version`）、组织与财务（`md_org_unit`/`md_cost_center`/`md_account_subject`/`md_warehouse`）、分发中心（`MasterDataDistributor`）。权限：15 个 `MDM:*` 权限点已种子化并授权到 `MDM_ADMIN`、`PRODUCT_ENGINEER`、`PROCESS_SUPERVISOR`、`PROD_PLANNER`、`BUYER`、`SQE`、`COST_ACCOUNTANT` 等 20+ 角色。**缺口**：5 个权限点只出现在 `@PreAuthorize` 而从未种子化——`MDM:BOM:UPDATE`、`MDM:BOM:PUBLISH`、`MDM:MATERIAL:PUBLISH`、`MDM:PRODUCT:CREATE`、`MDM:PRODUCT:UPDATE`。后果分三档：BOM 的 3 个接口（`BomController:22,25,26`）无角色兜底，**任何角色都调不通**；分发重试接口（`DistributionController:28`）有 `or hasRole('ADMIN')` 兜底，**仅 `ADMIN` 可调**；产品接口（`ProductController:22-24`）有已种子化的 `MDM:MATERIAL:CREATE`/`:UPDATE` 兜底，**仍可调通**（`MDM:PRODUCT:*` 属冗余声明）。另 `PartnerMasterController` 的客户/供应商写接口连方法级注解都没有 |
| **2. 至少 3 类核心单据可以从创建流转到关闭或作废** | ✅ 达成 | 状态机 `DRAFT → PENDING → PUBLISHED → CHANGING → DISABLED`（`MasterDataStatus`）已全链路可用。可走完「创建 → 提审 → 发布 → 停用」的：**客户**、**供应商**（`PartnerMasterController` 的 POST/PUT/DELETE/submit 齐备），另加基础字典 5 类（`/api/mdm/reference/{type}` 的 POST/PUT/DELETE）与治理 3 类（`/api/mdm/{type}` 的 POST/PUT/DELETE）。**缺口**：物料、产品、BOM、工艺路线**没有停用接口**（`MaterialController`/`ProductController`/`BomController`/`RoutingController` 均无 `DELETE`），只能走到 `PUBLISHED`，无法走到终态 `DISABLED` |
| **3. 至少 1 个审批流程和 1 个异常处理闭环** | ✅ 达成（超额） | 审批流程 5 条，均由 `MdmApprovalCallback.supports()` 承接：`MDM_CUSTOMER_NEW`（销售主管 → 应收会计信用核查）、`MDM_SUPPLIER_NEW`（供应商质量工程师 → 采购主管批准）、`MDM_MATERIAL_NEW`（工艺主管审核 → 生产计划审核）、`MDM_BOM_CHANGE`（**三级**：工艺主管 → 生产主管 → 成本审核）、`MDM_ROUTING_CHANGE`（生产主管确认）。异常闭环：分发失败 → `md_distribution_log` 记 `FAILED` + `md_inbox` 记 `FAILED`/`retry_count+1` → 超 3 次置 `DEAD` → 重试 `POST /api/mdm/distributions/{id}/retry` 或 `/events/{eventId}/retry` → 对账 `/events/{eventId}/reconcile` 写 `md_reconciliation`。驳回路径亦有：`onRejected` 置 `REJECTED` 并记录原因 |
| **4. 至少 2 个向其他系统发送或消费的幂等业务事件** | ⚠️ 发送侧达成 / 消费侧未实现 | **发送侧远超 2 个**：`MdmOutboxService.enqueue()` 按 `BizType` 生成 `<类型>.PUBLISHED` 事件（`CUSTOMER`/`SUPPLIER`/`MATERIAL`/`PRODUCT`/`BOM`/`ROUTING` 共 6 种），幂等靠三重机制——`md_outbox` 以 `event_id`（UUID）为主键、`md_inbox` 有 `UNIQUE(event_id,target_system)` 且用 `INSERT IGNORE`、目标副本表写入全用 `INSERT ... ON DUPLICATE KEY UPDATE`。**消费侧未实现**：mdm 模块内没有任何消费其他系统事件的代码（无 `BusinessEventService` 引用、无 `@EventListener` / `@KafkaListener`），`shared-common` 的 `com.mfg.common.integration.BusinessEventService` 存在但 mdm 未使用 |
| **5. 至少 1 页本系统运营查询或统计报表** | ✅ 达成 | 4 个统计/台账接口：`GET /api/mdm/materials/stats`（按状态计数）、`GET /api/mdm/distributions`（分发日志分页，可按 `status` 过滤）、`GET /api/mdm/distributions/events` 与 `/events/{eventId}`（事件列表 + 目标系统明细 + 对账记录）、`GET /api/mdm/governance/{kind}`（合并 / 编码映射 / 变更历史三本台账）。**缺口**：无 `query/` 层、无独立报表实体或导出接口，统计口径散落在 controller 与 service 中 |

## 未实现 / 缺口

**主数据覆盖（12 类只落地 6 类）**

1. **未实现的 6 类主数据**：`BizType` 声明了 12 类，但只有 `CUSTOMER`、`SUPPLIER`、`MATERIAL`、`PRODUCT`、`BOM`、`ROUTING` 有实体与服务。`MATERIAL_CATEGORY` 只算半个（`MaterialCategory` 实体与 `MaterialCategoryRepository` 存在却**从未被任何 service / controller 注入**，实际维护走 `MdmReferenceService` 的原生 SQL）。`UNIT`、`ORG_UNIT`、`COST_CENTER`、`ACCOUNT_SUBJECT`、`EMPLOYEE` **完全没有 JPA 实体**，只有 `MdmReferenceService` 的原生 SQL 读写——**员工（`md_employee`）连 SQL 读写都没有**，只有 DDL。
2. **无审批 + 无分发的基础字典类**：`UNIT`/`ORG_UNIT`/`COST_CENTER`/`ACCOUNT_SUBJECT`/`EMPLOYEE`/`MATERIAL_CATEGORY` 的 `approvalFlow` 为 `null`，尽管 `BizType.targetSystems` 给它们声明了分发目标，但 `submitForApproval()` 只被有实体类的 6 类调用，这些字典**实际不会被分发**（没有任何实体类能触发 `distribute()`）。
3. **数据治理的软件工程部分缺失**：数据所有者 / 维护人无字段（只有 `created_by` / `updated_by`）；**数据质量无任何实现**（无质量规则、无评分、无校验任务）；**重复检测有仓储方法但从未被调用**（`CustomerRepository.findByUnifiedSocialCodeAndIdNot`、`findByCustomerName`），即「一客多码」识别只写了查询、没有接到任何提交或审批环节；`md_change_history` 表已建但**无任何 Java 写入**，`GET /governance/history` 永远返回空表。
4. **`md_merge_record` 合并未做业务级幂等**：`merge()` 只校验保留记录与被合并记录不相同，不校验被合并方是否已处于 `DISABLED`，重复提交会命中表上的 `UNIQUE KEY uk_merge_source(entity_type, merged_id)` 抛数据库异常而非友好提示。
5. **序列号策略未实现**：`Material` 只有 `batchManaged`（`is_batch_managed`）一个批次开关，无序列号字段、无序列号表、无序列号规则配置。
6. **组织维度只有字符串字段**：公司 / 工厂 / 车间**无独立实体与表**，仅以 `factory_code`（`md_warehouse`、`md_production_version`）、`workshop_code`（`md_work_center`）字符串散落各处，无法形成组织树。
7. **银行与资质台账未实现**：客户 / 供应商无银行账户表；供应商资质仅有 `qualStatus` / `qualExpireDate` 两个字段，无证书台账（附件、发证机构、有效期明细）。地址仅单个 `address` 字符串，无多地址簿。

**接口与权限**

8. **5 个权限点未种子化**（详见验收项 1）：`MDM:BOM:UPDATE`、`MDM:BOM:PUBLISH`、`MDM:MATERIAL:PUBLISH`、`MDM:PRODUCT:CREATE`、`MDM:PRODUCT:UPDATE`。**最严重的是 BOM 写入被完全锁死**——`PUT /api/mdm/boms/{id}`、新增替代料、停用替代料三个接口（`BomController:22,25,26`）只校验 `MDM:BOM:UPDATE` 且无角色兜底，该权限从未定义，因此**没有任何角色能调用**（含 `ADMIN`）。分发重试接口（`DistributionController:28`）有 `or hasRole('ADMIN')` 兜底，仅 `ADMIN` 可调。产品接口有 `MDM:MATERIAL:CREATE` / `:UPDATE` 兜底，仍可调通，`MDM:PRODUCT:*` 属冗余声明。**而 BOM 创建 `MDM:BOM:CREATE` 是已种子化的**，故现象是「能建 BOM 但不能改 BOM」。
9. **`PartnerMasterController` 无方法级 `@PreAuthorize`**：客户与供应商的创建 / 修改 / 停用接口仅依赖 `/api/mdm/**` 的 `SYSTEM_MDM` URL 门禁，`MDM:CUSTOMER:CREATE`、`MDM:SUPPLIER:CREATE` 等已种子化的权限点**未被任何代码使用**。
10. **核心单据缺停用接口**（详见验收项 2）：物料、产品、BOM、工艺路线无法由 API 置 `DISABLED`。
11. **`GET /api/mdm/distributions/events` 与 `/{eventId}`、`/retry`、`/reconcile` 四个接口无 `@PreAuthorize`**，只受 URL 层 `SYSTEM_MDM` 门禁。另 `POST /api/mdm/merge`（合并主数据，改写状态并写码映射）所在的 `MdmGovernanceController` **全类无任何方法级鉴权**。
12. **无导出接口**：目录标准要求核心单据具备导出能力，mdm 全模块无导出实现。

**分发与对账**

13. **目标系统不可选择**：分发目标是 `BizType.targetSystems` 的硬编码逗号串，`distribute()` 全量推送，无法按单据选择目标系统。
14. **对账是弱实现**：`md_reconciliation` 的 `source_hash` / `target_hash` 列已建但**从不写入**，结果只能取 `MATCH`（有写入行数）或 `CHECK`（无行数），并没有真正比对源库与目标库的版本或内容。
15. **分发非完全同步**：「审批通过 → 下游立即可见」有最多约 1 秒的调度延迟（`@Scheduled(fixedDelay=1000)`），`MdmApprovalCallback` 中统计分发结果的日志恒为空。
16. **`writeToEnergy()` 是死代码**：`MasterDataDistributor` 的 `switch` 分支与 `writeToEnergy()` 方法存在，但没有任何 `BizType.targetSystems` 包含 `energy`，该分支永不命中。`erp_md_cost_center`、`energy_md_equipment` 两张副本表已建但 MDM 从不写入。
17. **无跨库事务与补偿日志的持久化保证**：分发写入下游库时若进程崩溃，`md_outbox` 的 `PROCESSING` 状态会滞留（`dispatch()` 只捞 `PENDING`/`RETRYING`），没有针对 `PROCESSING` 卡死的超时回收。

**跨模块一致性**

18. **CRM 客户 360 查了一张不存在的表**：`CrmP2Service.customer360()` 读 `src_mdm.md_customer_contact`（列 `customer_id`），但 MDM 实际建的表是 `src_mdm.md_partner_contact`（列 `partner_type` / `partner_id`），全仓 DDL 中**不存在 `md_customer_contact`**——该接口的联系人查询会直接报表不存在。
19. **下游消费数据远比声明的窄**：声明分发的 20 张副本表里，实际被下游代码读取的只有少数——PLM 读 `plm_md_bom` / `plm_md_bom_line`（`PlmStructureController`）与 `src_mdm.md_bom` / `md_routing`（`EngineeringChangeChainService`），ERP 读 `erp_md_bom` / `erp_md_bom_line` 与 `src_mdm.md_material` / `md_customer` / `md_production_version`（`ErpP2Service`），CRM 读 `src_mdm.md_customer` / `md_product`。**MES / WMS / QMS / EAM / SRM 均无任何代码读取自己的副本表**——副本写进去了，但那些系统还没真正用起来。
20. **`shared-masterdata` 是空壳**：`pom.xml` 声明依赖 `shared-masterdata`，但该共享模块只有 `pom.xml` + `package-info.java`，无任何类，全仓无一处 `import com.mfg.masterdata.*`。MDM 的统一契约完全靠自己模块内的 `MasterDataEntity` 实现，未下沉到共享层。

**文档漂移**

21. **README 有三处与代码不符**，建议以代码为准修订：
    - 「`MasterDataDistributor.distributeTo()` 标 `REQUIRES_NEW`」——代码**刻意不用**嵌套事务，类注释解释了原因；
    - 「审批通过 → 自动分发」在 README 中被描述为即时，实际是 Outbox 异步（约 1 秒）；
    - 「12 类主数据」表中「产品 / 工艺 / 计量单位 / 组织 / 成本中心 / 科目 / 员工：待实现」已过时——产品、工艺路线**已经实现**（含审批与分发），仍待实现的是计量单位、组织、成本中心、科目、员工（只读字典级）与序列号策略。
