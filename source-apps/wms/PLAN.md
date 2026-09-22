# wms/ — 仓储与库存执行

> **整体计划** · 依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 WMS：仓储与库存执行 章节

## 系统定位

WMS 是十系统主线里**库存数量的唯一记账方**：上游从 SRM 收 ASN（`srm_asn` → `wms_receipt`），从 QMS 收检验结论（`PENDING_INSPECTION` → `AVAILABLE`/`QUARANTINED`/`RETURNED`），向下为 MES 提供领料可用量、为 ERP 提供齐套率输入（`wms_inventory.available_qty`）。它管的是「**货在哪、有多少、能不能用**」，不管「买没买」（SRM）和「合不合格」（QMS）。

按 [implementation-roadmap.md](../../docs/implementation-roadmap.md)，WMS 与 SRM、QMS 同属 **P3：采购、仓储、质量闭环**，验收目标是「ASN 收货自动生成待检库存；QMS 结果驱动可用、隔离或退供库存」。

## 本职模块基线

摘自 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 WMS 章节，右侧为按 2026-09-22 实际代码判断的完成度（详见 [STATUS.md](STATUS.md)）。

| 模块 | 核心能力 | 当前状态 |
|---|---|---|
| 基础设置 | 仓库、库区、库位、容器、条码、收发策略 | 部分实现 |
| 入库 | ASN 收货、采购收货、生产入库、退料入库、待检入库 | 部分实现 |
| 上架与库存 | 推荐库位、上架、库存余额、批次、序列号、冻结、解冻 | 部分实现 |
| 出库 | 销售出库、生产领料、补料、退供、调拨、波次/拣货 | 部分实现 |
| 库内作业 | 移库、拆零、合并、盘点、盘盈盘亏、库存调整 | 部分实现 |
| 质量库存 | 待检、合格、隔离、报废、退供等库存状态转换 | **已实现** |
| 仓储报表 | 库龄、周转、呆滞、缺料、准确率、作业时效 | 未实现 |

判断依据：`quality_status` 的**六态转换 + 12 类库存动作 + 全量流水留痕 + 幂等**是本模块最完整的一块，也是整个 P3 阶段最扎实的实现；而库区/容器/条码、序列号、波次拣货、库龄周转报表在代码里找不到对应类或表。基础设置只有库位一种对象（`wms_location`）。

## 代码分层标准

本项目 `source-apps/<system>/` 统一分层，WMS 的实际落地情况（**三个模块中分层最完整的**）：

| 分层 | 本项目标准职责 | WMS 现状 | 说明 |
|---|---|---|---|
| `domain/` | 状态机、枚举、领域规则 | **空** | 12 类动作与校验规则硬编码在 `InventoryTransactionService.action()` 的 `switch` 里；盘点/调拨状态机在 `P3CrudService.rules()` 与 controller 内联的 `Map` 参数中 |
| `entity/` | 单据、台账、配置实体 | 有 3 个 | `Inventory`、`StockTransaction`、`WarehouseLocation` |
| `repo/` | 数据访问与领域查询 | 有 3 个 | `InventoryRepository`（含四维组合查询）、`StockTransactionRepository`、`WarehouseLocationRepository` |
| `service/` | 业务动作、校验、事务边界 | 有 2 个 | `InventoryTransactionService`（★ 库存数量与质量状态的唯一写入口）、`InventoryService`（余额+流水成对写入） |
| `controller/` | 独立维护 API | 有 2 个 | `WarehouseController`（强类型）、`WmsP3Controller`（泛型 + 3 个权限注解） |
| `dto/` | 入参命令对象 | 有 2 个 | `InventoryActionCommand`、`StockTransactionCommand`，均为 `record` + Jakarta Validation 注解 |
| `workflow/` | 本系统审批策略与审批回调 | **空** | 无审批代码；catalog 要求「盘盈盘亏」审批，未实现 |
| `integration/` | 上下游事件、分发、幂等处理 | **空** | 无集成代码；`WMS.RECEIPT.PENDING_INSPECTION` 事件由 SRM 的 `P3FlowService.arriveAsn()` 代发 |
| `query/` | 列表、详情、台账、统计查询 | **空** | 分页直接写在 controller；无报表接口 |

**本模块独有的设计取舍**：库存写入有**两条并行通道**，这是刻意的，也是本模块最容易踩坑的地方。

| 通道 | 入口 | 技术 | 覆盖范围 |
|---|---|---|---|
| A. 领域事务 | `POST /api/wms/transactions` → `InventoryService.transact()` | JPA 实体 + `InventoryRepository` | 只写 `on_hand_qty`/`allocated_qty`/`available_qty`，用于常规出入库流水 |
| B. 状态事务 | `POST /api/wms/inventory-actions/{action}` → `InventoryTransactionService.action()` | 裸 `JdbcTemplate` + `FOR UPDATE` | 额外管 `frozen_qty` 与 `quality_status`，并强制写 `wms_inventory_ledger` 流水 |

通道 B 之所以绕开 JPA：`Inventory` 实体没有映射 V33 新增的 `quality_status`/`frozen_qty`/`received_date` 三列，而这三列正是质量库存的核心；用裸 SQL 配 `SELECT ... FOR UPDATE` 行锁，才能在并发下保证「校验—扣减—写流水」原子。代价是同一张表有两种写法，`Inventory` 实体读到的 `availableQty` 与通道 B 算出的 `available_qty` 在语义上需要人工对账（通道 B 的 `on_hand`/`available`/`frozen` 三列增减是独立计算的）。

幂等设计同样值得说明：通道 B 不像 `BusinessEventService` 那样用 UUID，而是要求**调用方传 `idempotencyKey`**（业务幂等号，≤80 字符），落在 `wms_inventory_ledger` 的唯一键上。重放时严格比对动作类型、库存四维、来源系统、来源单号与数量，任一项不符即报错——这把「重复提交」和「同号改数量」两类错误分开了。

## 最低验收（五项）

抄录 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 末尾「系统本职完成的最低验收」：

1. 至少 5 个本职模块具有实体、服务、接口和角色权限；
2. 至少 3 类核心单据可以从创建流转到关闭或作废；
3. 至少 1 个审批流程和 1 个异常处理闭环；
4. 至少 2 个向其他系统发送或消费的幂等业务事件；
5. 至少 1 页本系统运营查询或统计报表。

WMS 当前的逐条对照与缺口，见 [STATUS.md](STATUS.md#对照最低验收)。
