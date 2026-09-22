# wms/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**库存数量与质量状态这条线是三个模块里做得最扎实的——12 类库存动作、六态质量转换、强制流水留痕、业务幂等号去重、8 个单测全部就位；但仓储报表、波次拣货、序列号、容器条码一片空白，库存写入还留着「JPA 通道 / 裸 SQL 通道」两套写法需要收口。**

## 已实现

### 分层文件统计

`source-apps/wms/src/main/java/com/mfg/wms/` 共 13 个 Java 文件（12 个代码文件 + 1 个 `package-info.java`），另有 1 个测试文件：

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `domain/` | 0 | — |
| `entity/` | 3 | `Inventory`（`wms_inventory`）、`StockTransaction`（`wms_stock_txn`）、`WarehouseLocation`（`wms_location`） |
| `repo/` | 3 | `InventoryRepository`、`StockTransactionRepository`、`WarehouseLocationRepository` |
| `service/` | 2 | **`InventoryTransactionService`**（★ 库存唯一写入口）、`InventoryService` |
| `controller/` | 2 | `WarehouseController`、`WmsP3Controller` |
| `dto/` | 2 | `InventoryActionCommand`、`StockTransactionCommand` |
| `workflow/` | 0 | — |
| `integration/` | 0 | — |
| `query/` | 0 | — |
| `test/` | 1 | `InventoryTransactionServiceTest`（8 个 Mockito 测试方法） |

**依赖的共享模块**（`pom.xml` 声明，代码实际用到）：

| 共享类 | WMS 用它做什么 |
|---|---|
| `P3CrudService` | `receipts`/`putaway`/`inventories`/`inventory-actions`/`transfers`/`counts` 6 个对象的通用分页/详情/创建 |
| `InventoryService` 里的 `MasterDataService.assertConsumable()` | 出入库前强制校验物料必须**已发布**（消费 MDM 主数据） |
| `MaterialRepository`（MDM 模块） | 事务时按 `materialCode` 查物料并回填 `materialName`/`unitCode` |
| `CurrentUser` | 流水 `operator_code` 落当前登录人 |
| `BizException` / `ErrorCode` / `ApiResponse` | 统一错误码与响应包装 |

## 接口清单

两个 controller 都挂在 `/api/wms` 前缀下，共 15 个处理方法。

### `WarehouseController` — 强类型基础数据与库存事务

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/wms/locations` | 库位分页 |
| POST | `/api/wms/locations` | 建库位：`locationCode` 唯一校验，强制 `available=true` |
| PATCH | `/api/wms/locations/{id}/availability` | 启用/禁用库位（`enabled` 参数） |
| GET | `/api/wms/inventories` | 库存余额分页（返回 `Inventory` 实体） |
| GET | `/api/wms/transactions` | 出入库流水分页 |
| POST | `/api/wms/transactions` | **登记库存事务**：`txnNo` 唯一 → 物料必须存在**且已发布**（`assertConsumable`）→ 库位必须启用 → `txnType` 必须 `IN_*`/`OUT_*`/`ADJUST` → 出库时 `availableQty` 不允许为负 → 写余额 + 写流水，同一事务 |

### `WmsP3Controller` — 泛型 P3 对象（`kind` 白名单：`receipts`、`putaway`、`inventories`、`inventory-actions`、`transfers`、`counts`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/wms/{kind}` | 分页 + `keyword` + `status` 过滤 |
| GET | `/api/wms/{kind}/{id}` | 单条详情 |
| POST | `/api/wms/{kind}` | 创建；**`inventories`/`inventory-actions`/`receipts` 会被拒**（「该对象只能由业务事务生成」），只剩 `putaway`/`transfers`/`counts` 可手工建 |
| POST | `/api/wms/inventory-actions/{action}` | **★ 库存动作**，`@PreAuthorize("hasAuthority('WMS:INVENTORY:ACTION') or hasRole('ADMIN')")` |
| POST | `/api/wms/transfers/{id}/confirm` | 调拨确认：`DRAFT`→`CONFIRMED` |
| POST | `/api/wms/transfers/{id}/execute` | **★ 调拨执行**，`@PreAuthorize("hasAuthority('WMS:TRANSFER:EXECUTE') or hasRole('ADMIN')")` |
| POST | `/api/wms/counts/{id}/release` | 盘点下发：`DRAFT`→`RELEASED` |
| POST | `/api/wms/counts/{id}/review` | 盘点复核：`RELEASED`→`COUNTING`，`COUNTING`→`REVIEWING` |
| POST | `/api/wms/counts/{id}/post` | **★ 盘点过账**，`@PreAuthorize("hasAuthority('WMS:COUNT:POST') or hasRole('ADMIN')")` |

> **路径优先级说明**：`GET /api/wms/inventories` 同时匹配 `WarehouseController.inventories()`（强类型）与 `WmsP3Controller.page("{kind}")`，Spring 的字面量路径优先于变量路径，因此实际命中的是前者。这是刻意保留的兼容层，不是冲突。

### 库存动作与状态机的实际语义

`POST /api/wms/inventory-actions/{action}` 的 6 个 URL 名映射到 6 类动作，加上 service 内部可调用的 6 类，共 **12 类**：

| action URL | 内部类型 | 数量变化 | 质量状态变化 | 前置校验 |
|---|---|---|---|---|
| `freeze` | `FREEZE` | 可用 −qty，冻结 +qty | → `FROZEN` | 可用库存足够 |
| `unfreeze` | `UNFREEZE` | 可用 +qty，冻结 −qty | → `AVAILABLE` | 冻结库存足够 |
| `quarantine` | `QUARANTINE` | 可用 −qty | → `QUARANTINED` | 可用库存足够 |
| `release` | `RELEASE` | 可用 +qty | → `AVAILABLE` | 原状态必须是 `QUARANTINED`/`PENDING_INSPECTION`/`REWORK` |
| `scrap` | `SCRAP` | 在库 −qty（可用或冻结扣） | → `SCRAPPED` | **禁止负库存** |
| `supplier-return` | `SUPPLIER_RETURN` | 在库 −qty | → `RETURNED` | **禁止负库存** |
| —（内部） | `ISSUE` | 在库 −qty | 不变 | 调拨出库用 |
| —（内部） | `RETURN_MATERIAL` / `RECEIPT` / `PUTAWAY` / `COUNT_GAIN` | 在库 +qty，可用 +qty | → `AVAILABLE` | — |
| —（内部） | `COUNT_LOSS` | 在库 −qty，可用 −qty | 不变 | 盘亏不得超过现有/可用 |

每次动作固定双写：`UPDATE src_wms.wms_inventory`（带 `FOR UPDATE` 行锁）+ `INSERT src_wms.wms_inventory_ledger`（记录 `quality_status_before/after` 与三个 delta）。

**调拨执行**（`executeTransfer`）是一笔复合事务：`ISSUE`（从源库位）+ `RETURN_MATERIAL`（入目标库位），两条流水号由主幂等号派生 `key+":OUT"` / `key+":IN"`；单据状态已是 `COMPLETED` 且幂等号相同则原样返回。

**盘点过账**（`postCount`）是三个模块里校验最严的一段：

```
1. 幂等号必填且 ≤50 字符
2. 盘点单必须 REVIEWING（已 COMPLETED 且同幂等号 → 原样返回）
3. 明细不得为空
4. 全部明细 review_status 必须 = APPROVED
5. 实际数/账面数不得为负；差异必须 = 实际 − 账面
6. 明细库存维度（物料+库位+批次）不得重复
7. 每条明细的账面数必须与当前库存 on_hand_qty 一致，否则「盘点期间库存已变化，请重新盘点」
   —— 全部校验通过后才进入写账阶段，按差异正负发 COUNT_GAIN / COUNT_LOSS
8. 最后置盘点单 COMPLETED + posted_at
```

### controller 内联的状态机（与 `P3CrudService.rules()` 一致）

| kind | 表 | 迁移 |
|---|---|---|
| `transfers` | `wms_transfer` | `DRAFT`→`CONFIRMED`/`CANCELLED`；`CONFIRMED`→`COMPLETED`/`CANCELLED` |
| `counts` | `wms_count_plan` | `DRAFT`→`RELEASED`/`CANCELLED`；`RELEASED`→`COUNTING`；`COUNTING`→`REVIEWING`；`REVIEWING`→`COMPLETED` |
| `putaway` | `wms_putaway` | `CREATED`→`PROCESSING`/`CANCELLED`；`PROCESSING`→`COMPLETED`/`CANCELLED`（无专用接口，走通用 status） |
| `receipts` | `wms_receipt` | 无状态机；状态由 QMS 检验结论驱动（见下） |
| `inventories` | `wms_inventory` | 无状态字段（`P3CrudService.sc("inventories")` 返回 `null`） |
| `inventory-actions` | `wms_inventory_action` | 无状态机（`rules()` 里没有这一项） |

### 跨系统数据流（QMS 判定驱动库存）

```
SRM: POST /api/srm/asns/{id}/arrive
  └─ INSERT src_wms.wms_receipt (status=PENDING_INSPECTION) + INSERT src_qms.qms_inspection
       └─ 发事件 WMS.RECEIPT.PENDING_INSPECTION (WMS→QMS)

QMS: P3FlowService.judgeInspection()
  ├─ UPDATE src_wms.wms_receipt SET status = 质量状态
  │     PASSED/CONCESSION → AVAILABLE   ；SCRAPPED → SCRAPPED ；RETURN → RETURNED
  │     REWORK → REWORK                 ；其余（含 FAILED）→ QUARANTINED
  └─ 若结论为 AVAILABLE，则 upsert src_wms.wms_inventory（ON DUPLICATE KEY 累加数量、置 AVAILABLE、刷新 received_date）
```

## 数据表

### JPA `@Table` 注解声明的表（库名来自 `catalog`）

| 实体 | 库.表 | 关键列 |
|---|---|---|
| `Inventory` | `src_wms.wms_inventory` | `material_code`、`material_name`、`warehouse_code`、`location_code`、`batch_no`、`on_hand_qty`、`allocated_qty`、`available_qty`、`unit_code`、`unit_cost`、`total_cost` |
| `StockTransaction` | `src_wms.wms_stock_txn` | `txn_no`、`txn_type`、`txn_direction`、`txn_qty`、`unit_cost`、`source_no`、`prod_order_no`、`work_order_no`、`txn_date`、`operator_code` |
| `WarehouseLocation` | `src_wms.wms_location` | `location_code`、`location_name`、`warehouse_code`、`warehouse_name`、`location_type`、`is_available` |

### 无实体、由 SQL 直接读写的表

| 库.表 | 来源 | 用途 |
|---|---|---|
| `src_wms.wms_inventory_ledger` | `InventoryTransactionService`（V38 建表） | **库存流水台账**：`idempotency_key` 唯一键、`quality_status_before/after`、三个 delta、来源系统/单号、操作人 |
| `src_wms.wms_receipt` | `P3CrudService` 注册表；`P3FlowService.arriveAsn()` 写；`judgeInspection()` 改状态 | 收货单（待检入库） |
| `src_wms.wms_putaway` | `P3CrudService` 注册表 | 上架单（`suggested_location` 推荐库位 / `actual_location` 实际库位） |
| `src_wms.wms_inventory_action` | `P3CrudService` 注册表 | 库存动作单据（注意：`InventoryTransactionService` 走的是 `ledger`，这张表由泛型 CRUD 单独维护，**两者不联动**） |
| `src_wms.wms_transfer` | `P3CrudService` + `InventoryTransactionService` | 调拨单，V38 加了 `idempotency_key` 唯一键 |
| `src_wms.wms_count_plan` | `P3CrudService` + `InventoryTransactionService` | 盘点计划单，V38 加了 `posted_at`、`idempotency_key` |
| `src_wms.wms_count_line` | `InventoryTransactionService.postCount()` 裸 SQL | 盘点明细（`book_qty`/`actual_qty`/`difference_qty`/`review_status`） |
| `src_wms.wms_md_material` | **MDM 侧** `MasterDataDistributor` 写入 | 物料主数据只读副本（WMS 自己不写） |

### 迁移脚本

| 脚本 | 内容 |
|---|---|
| `V32__p3_wms_receipt_putaway.sql` | 建 `wms_receipt`、`wms_putaway` |
| `V33__p3_wms_inventory_control.sql` | 给 `wms_inventory` 加 `quality_status`/`frozen_qty`/`received_date`；建 `wms_inventory_action`、`wms_transfer` |
| `V34__p3_wms_counting.sql` | 建 `wms_count_plan`、`wms_count_line` |
| `V38__p3_inventory_transaction_closure.sql` | 建 `wms_inventory_ledger`；给 `wms_transfer`/`wms_count_plan` 加幂等键；插入 3 个 WMS 权限点 |

> **实体与表结构不同步**：V33 给 `wms_inventory` 加的 `quality_status`/`frozen_qty`/`received_date` 三列**没有映射进 `Inventory` 实体**，所以 `GET /api/wms/inventories` 返回的 JSON 里看不到质量状态与冻结量——这恰恰是最需要展示给演示观众的两列。要看到必须走 `POST /api/wms/inventory-actions/...` 的返回（ledger 记录）或直接查库。

## 对照最低验收

| 验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| 1. 至少 5 个本职模块具有实体、服务、接口和角色权限 | **部分达标** | 7 个模块里 6 个有表有接口（基础设置/入库/上架库存/出库/库内作业/质量库存），实体 3 个、service 2 个。角色权限是本模块最强的：3 个 `@PreAuthorize` 单元权限点（`WMS:INVENTORY:ACTION`、`WMS:TRANSFER:EXECUTE`、`WMS:COUNT:POST`）+ `SecurityConfig` 的系统级 `SYSTEM_WMS`，权限点由 `V38` 迁移插入 `mfg_auth.sys_permission`，并在 `09_role_permission.sql` 映射到 `WAREHOUSE_KEEPER`/`WAREHOUSE_SUPERVISOR`。缺口：仓储报表模块（第 7 个）无实体无接口 |
| 2. 至少 3 类核心单据可以从创建流转到关闭或作废 | **达标** | ① **调拨单** `DRAFT→CONFIRMED→COMPLETED`（`/transfers/{id}/confirm`、`/execute`）；② **盘点单** `DRAFT→RELEASED→COUNTING→REVIEWING→COMPLETED`（`/counts/{id}/release`、`/review`、`/post`）；③ **收货单** `PENDING_INSPECTION→AVAILABLE/QUARANTINED/RETURNED/SCRAPPED/REWORK`（由 QMS 判定驱动）；④ **上架单** `CREATED→PROCESSING→COMPLETED`（走通用 status）。作废侧：调拨/盘点/上架均可 `CANCELLED` |
| 3. 至少 1 个审批流程和 1 个异常处理闭环 | **未达标（异常半边达标）** | 审批：**未实现** —— 无 `workflow/` 层、无 `ApprovalCallback`；catalog 要求的「盘盈盘亏审批」被实现成了 `WMS:COUNT:POST` 权限校验 + 明细 `review_status=APPROVED` 复核，属于权限与复核而非审批流。异常闭环：**已实现** —— 冻结/解冻、隔离/放行、报废、退供构成完整的质量库存状态机；盘点差异强制复核、盘点期间库存变动检测（「库存已变化，请重新盘点」）构成盘点异常的闭环 |
| 4. 至少 2 个向其他系统发送或消费的幂等业务事件 | **未达标** | 全仓检索：与 WMS 相关的业务事件**只有 1 个** `WMS.RECEIPT.PENDING_INSPECTION`，且它由 SRM 的 `P3FlowService.arriveAsn()` 代发，WMS 自己的代码里**没有任何 `publish()` 调用**。幂等机制本身是完备的（`mfg_ops.biz_outbox` 出箱表 + `event_id` UUID + `biz_inbox` 的 `INSERT IGNORE` + 1 秒轮询 + 3 次重试后 `DEAD`），但没有第 2 个 WMS 事件，也没有消费侧处理器 |
| 5. 至少 1 页本系统运营查询或统计报表 | **未实现** | `query/` 层不存在；只有 `/api/wms/locations`、`/api/wms/inventories`、`/api/wms/transactions` 与泛型分页列表，无库龄、周转率、呆滞、缺料、准确率、作业时效任一接口。缺料分析最接近的替代是 `InventoryService` 里「可用库存不足禁止负库存出库」的单点校验，不构成报表 |

**结论：五项中第 2 项达成，第 1 项部分达成，第 3、4、5 项未达成**（第 3 项异常闭环、第 4 项幂等机制的基础设施都已具备，差的是审批流与第 2 个事件）。

## 未实现 / 缺口

### 业务能力缺口

- **仓储报表（整块未实现）**：库龄、周转、呆滞、缺料、准确率、作业时效六项全无。注意 `wms_inventory` 已经有 `received_date`/`last_in_date`/`last_out_date` 列，做库龄分析的数据基础是有的，缺的是接口。
- **波次与拣货**：无拣货任务、拣货单、波次表；`wms_transfer`/`ISSUE` 都是单笔动作。
- **序列号管理**：`wms_inventory` 的 `batch_no` 做到了批次级，序列号（单件）+ `serial_no` 表完全不存在。
- **容器与条码**：无容器表、无条码表、无 `LPN` 概念。
- **库区**：`wms_location.location_type` 用 `RAW/FINISHED/SPARE/QUARANTINE` 四值代替了库区建模，没有独立的库区/区域表。
- **收发策略（推荐库位）**：`wms_putaway.suggested_location` 只是一个可写入的列，没有任何推荐算法或策略配置。
- **盘盈盘亏审批**：catalog 要求审批，实际只有权限点 + 明细复核，无审批流；且**无盘盈盘亏单据**（`InventoryTransactionService` 直接改余额并写流水，不生成调整单）。
- **拆零、合并、库存调整单**：无对应接口与表；`ADJUST` 类型只在 `StockTransactionCommand.txnType` 的字符串校验里被接受。
- **生产入库、退料入库**：`txn_type` 的 DDL 注释里有 `IN_PROD`/`IN_RETURN`，代码只做前缀校验，无 MES 侧联动。

### 工程与架构缺口

- **库存两条写入通道未收口**：`InventoryService` 走 JPA（写 `wms_stock_txn`），`InventoryTransactionService` 走裸 SQL（写 `wms_inventory_ledger`）。两条通道都改 `wms_inventory`，但**互不感知**：通道 A 不写 ledger、不加 `FOR UPDATE` 行锁，与通道 B 并发时可能丢更新（`InventoryService` 是「读实体—改字段—save」，没有锁）。
- **`wms_inventory_action` 表与 `wms_inventory_ledger` 表职责重叠**：前者是泛型 CRUD 的普通表，后者是库存动作的真实台账，两条路径写出来的「库存动作」不联动，演示时容易混淆。
- **`Inventory` 实体缺 3 列**：`quality_status`/`frozen_qty`/`received_date` 未映射，`GET /api/wms/inventories` 看不到质量状态与冻结量。
- **`InventoryService` 的计算式可疑**：`availableQty = onHandQty − allocatedQty`，但 `onHandQty` 已经先加过 `signed`，等于「先改在库、再用在库减已分配」，与 `InventoryTransactionService` 独立维护 `available_delta` 的口径不一致，两套写法对同一行的 `available_qty` 会有不同结果。
- **`WarehouseController.createLocation` 无 `@Valid`**，`WarehouseLocation` 实体也没有任何校验注解，可以建出空编码库位（`existsByLocationCode` 对 null 不生效）。同类问题：`POST /api/wms/locations` 未做 `@PreAuthorize`。
- **`POST /api/wms/transactions` 无 `@PreAuthorize`**：出入库是全模块最敏感的动作，权限点 `WMS:STOCK:IN`/`WMS:STOCK:OUT` 在种子数据里有映射，但接口上没有注解，实际只受 `SYSTEM_WMS` 门禁保护。
- **`InventoryService.transact()` 用 `locations.findAll().stream().filter(...)`** 全表加载找库位；`InventoryTransactionService.lock()` 用 `COALESCE(?,'')` 包列，会导致该查询无法使用 `uk_inv` 索引。
- **盘点幂等号长度限制两套**：`postCount` 校验盘点单号 ≤50 字符，而它派生的明细幂等号是 `key+":"+lineId`，最终又要过 `action()` 的 ≤80 字符校验（`wms_inventory_ledger.idempotency_key` 为 VARCHAR(80)）。两个阈值不统一，虽然 50+1+19 位主键仍在 80 以内，但规则分散在三处。
- **无 `domain/` 层**：12 类动作、六态质量状态、盘点/调拨状态机散落在 service 的 `switch`、controller 内联 `Map` 与共享 `P3CrudService.rules()` 三处，没有单一事实来源。
- **测试覆盖不均衡**：`InventoryTransactionServiceTest` 8 个用例覆盖了负库存、幂等重放、精度、空盘点、未复核明细、库存变动六类边界（全部用 Mockito 模拟 `JdbcTemplate`，是纯单元测试，无集成测试）；而 `InventoryService` 与 `P3FlowService.upsertInventory()` 零覆盖。
- **无导出接口**（catalog 要求核心单据具备「分页查询、详情查看和导出接口」）。
