# mes/ — 目前进展

> 更新于 2026-09-29 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**MES 已具备生产工单与派工、报工明细 CRUD、Andon 异常闭环；报工新增、修改、删除会在事务内重算工单数量/工时/状态，并同步生产订单实绩。工单暂停/取消、在制品与领退料、返工报废、OEE 等统计接口和业务事件仍待建设。**

## 已实现

### 分层文件统计

`source-apps/mes/src/main/java/com/mfg/mes/` 共 9 个 Java 文件（8 个代码文件 + 1 个 `package-info.java`）：

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `domain/` | 0 | — |
| `entity/` | 4 | `WorkOrder`、`WorkReport`、`ProdResult`、`AndonEvent` |
| `repo/` | 5 | 工单、报工、生产实绩与 Andon 仓储 |
| `service/` | 4 | 工单、报工、生产实绩汇总与 Andon 服务 |
| `controller/` | 3 | `WorkOrderController`、`WorkReportController`、`AndonEventController` |
| `dto/` | 0 | — |
| `workflow/` | 0 | — |
| `integration/` | 0 | — |
| `query/` | 0 | — |

`src/test/` 已覆盖报工数量回算和 Andon 状态流转；本次补充生产实绩聚合与报工修改后的回算验证。

**依赖的共享模块**（`pom.xml` 声明 `shared-common`/`shared-security`/`shared-workflow`/`shared-masterdata`，但代码实际只用到前两个）：

| 共享类 | MES 用它做什么 |
|---|---|
| `ApiResponse` / `ErrorCode` / `BizException` | 统一响应包装与错误码 |
| `CurrentUser.usernameOrSystem()` | 报工人 `operator_code` 与 `created_by` |
| `shared-workflow` | **声明了依赖但零使用**：无 `ApprovalCallback` 实现，工单无审批 |
| `shared-masterdata` | **声明了依赖但零使用**：MES 不主动校验物料是否已发布（对比 WMS 的 `assertConsumable`） |

## 接口清单

MES 控制器为工单、报工明细和 Andon 异常提供分页/详情及按权限控制的单据操作。

### `WorkOrderController` — 工单（`/api/mes/work-orders`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mes/work-orders` | 工单分页（`page`/`size`，size 上限 200） |
| POST | `/api/mes/work-orders` | 建工单：置 `CREATED`，`completedQty`/`qualifiedQty`/`scrapQty` 全部归零。**未做 `workOrderNo` 重复校验**（`WorkOrderRepository` 里没有 `existsByWorkOrderNo`，而 DDL 有唯一键 `uk_wo_no`，重复时会撞数据库异常而非业务错误码） |
| POST | `/api/mes/work-orders/{id}/start` | 开工：状态必须 `CREATED` 或 `RELEASED` → `STARTED`，写 `actualStartTime` |
| POST | `/api/mes/work-orders/{id}/report` | 汇总报工：仅 `STARTED` 可报；参数 `qualifiedQty`、`scrapQty`（默认 0）；累计 `completedQty + 合格 + 报废` 不得超过 `planQty`；**累计数量正好等于计划数时自动置 `COMPLETED`** 并写 `actualEndTime` |

### `WorkReportController` — 报工明细（`/api/mes/reports`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mes/reports` | 报工分页 |
| POST / PUT / DELETE | `/api/mes/reports` | 报工只允许关联已开工或已完工工单；单号唯一，报工总数由合格数与报废数推导且不得超计划。新增、修改、删除都会重算工单合格/报废/完工数量、实际工时和状态，并同步订单级生产实绩。 |

> 快捷报工入口也写入报工明细，再统一重算工单与生产实绩；报工操作由服务事务包裹，更新或删除后若累计数低于计划会将工单恢复为生产中并清除实际完工时间。

### 工单状态机（代码中的实际值）

```
POST /api/mes/work-orders            → CREATED
POST /api/mes/work-orders/{id}/start → CREATED ──┐
                                       RELEASED ─┴─→ STARTED
POST /api/mes/work-orders/{id}/report → STARTED ──(累计 = planQty)──→ COMPLETED
```

| 状态 | 可达路径 | 说明 |
|---|---|---|
| `CREATED` | 建单接口写入 | — |
| `RELEASED` | **无接口可写** | `/start` 接受它，但全仓没有任何代码把工单置为 `RELEASED`（DDL 注释称其为「已下达」）；只能靠直接改库或外部写入 |
| `STARTED` | `/start` | 写 `actualStartTime` |
| `COMPLETED` | `/report` 累计达计划 | 写 `actualEndTime`；**达计划才完工，超产不允许，欠产则永远停在 `STARTED`** |
| `PAUSED` | **未实现** | DDL 注释里有「已暂停」，`WorkReport` 有 `is_paused`/`pause_reason`/`pause_minutes` 三列，但**没有任何接口能写入或推进该状态** |
| `CANCELED` | **未实现** | DDL 注释里有「已取消」，无接口 |

## 数据表

### JPA `@Table` 注解声明的表（库名来自 `catalog`）

| 实体 | 库.表 | 关键列 |
|---|---|---|
| `WorkOrder` | `src_mes.mes_work_order` | `work_order_no`、`prod_order_no`、`product_code`、`op_seq`、`operation_code`、`operation_name`、`equipment_code`、`plan_qty`、`completed_qty`、`qualified_qty`、`scrap_qty`、`wo_status`、`actual_start_time`、`actual_end_time`、`routing_code`、`routing_version`、`work_center`、`workshop_code`、`plan_start_time`、`plan_end_time`、`plan_hours`、`actual_hours`、`workshop_user`、`created_at`、`updated_at` |
| `WorkReport` | `src_mes.mes_work_report` | `report_no`、`work_order_no`、`prod_order_no`、`op_seq`、`report_qty`、`qualified_qty`、`scrap_qty`、`report_date`、`start_time`、`end_time`、`work_hours`、`equipment_code`、`operator_code`、`shift_code`、`is_paused`、`pause_reason`、`pause_minutes`、`created_by`、`created_at` |
| `ProdResult` | `src_mes.mes_prod_result` | `prod_order_no`(唯一)、`product_code`、`total_plan_qty`、`total_completed_qty`、`total_qualified_qty`、`progress_rate`、`current_op_seq`、`last_report_at`、`updated_at` |

### 有表但无任何 Java 代码读写的表

| 库.表 | 建表位置 | 说明 |
|---|---|---|
| `src_mes.mes_md_material` | `infra/db-init/05_business_systems_2.sql` | 物料主数据只读副本，由 **MDM** `MasterDataDistributor` 写入（`INSERT INTO src_mes.mes_md_material`），MES 侧无代码 |
| `src_mes.mes_md_routing_operation` | 同上 | 工艺路线工序只读副本，由 **MDM** `MasterDataDistributor.writeRoutingOperations(..., "src_mes.mes_md_routing_operation")` 写入，MES 侧无代码 |

> **实体与表结构同步（计划 00 · F4-05）**：`mes_work_order` 的 11 列（`routing_code`、`routing_version`、`work_center`、`workshop_code`、`plan_start_time`、`plan_end_time`、`plan_hours`、`actual_hours`、`workshop_user`、`created_at`、`updated_at`）与 `mes_work_report` 的 `created_at` 已补进实体映射，派工/工时/数据范围字段在 Java 侧可见。
> `mes_prod_result` 由 `ProdResultService` 在工单创建/变更及报工新增、修改、删除时重算。每个生产订单保留一行；计划量取其工序计划量最大值，完成量/合格量取当前最高已报工工序，避免多工序报工重复累计。生产实绩目前没有独立查询接口。

### 迁移脚本

| 脚本 | 内容 |
|---|---|
| — | **无 MES 专属迁移脚本**。`source-apps/bootstrap/src/main/resources/db/migration/` 的 V12–V38 中没有任何 `V*nn__*mes*.sql`，两张表的 DDL 全部来自 `infra/db-init/05_business_systems_2.sql`（初始化脚本，非增量迁移） |

### 与源码的对照：MES 之外谁在改 MES 的数据

| 来源 | 位置 | 行为 |
|---|---|---|
| MDM 主数据分发 | `MasterDataDistributor`（约 313、324 行） | 写 `mes_md_material`、`mes_md_routing_operation` 副本 |
| 权限门禁 | `SecurityConfig` 第 79 行 | `/api/mes/**` 需 `SYSTEM_MES` 权限 |
| 演示文档 | `MfgSourceApplication` 第 133 行 | 启动横幅「`/api/mes/**` 工单 · 报工」 |
| 角色权限种子 | `09_role_permission.sql` | `MES:WORK_ORDER:VIEW`/`START`、`MES:WORK_REPORT:CREATE` → `TEAM_LEADER`；`MES:%` → `WORKSHOP_CHIEF`/`PROD_SUPERVISOR`/`PROD_MANAGER`（**但这些权限点没有被任何接口注解使用**） |

## 对照最低验收

| 验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| 1. 至少 5 个本职模块具有实体、服务、接口和角色权限 | **未达标** | 工单与派工、报工执行、Andon 异常 3 个模块已有实体/服务/接口；在制品、领退料、返工报废和专用生产报表仍未实现。现有 MES controller 已使用 `@PreAuthorize`，服务也校验数据范围；系统仍未达到 5 个模块的门槛 |
| 2. 至少 3 类核心单据可以从创建流转到关闭或作废 | **未达标** | 工单可从 `CREATED`/`RELEASED` 开工并在累计达到计划数时完工，但无取消路径；Andon 可从待响应流转到已响应、已处理和已关闭。报工明细没有独立状态流转，当前只有两类具备状态流程的单据，未达到三类门槛 |
| 3. 至少 1 个审批流程和 1 个异常处理闭环 | **部分实现** | MES 尚无审批流程和工单提交/审核动作；Andon 已支持待响应、已响应、已处理、已关闭的基础闭环。异常升级和响应时效仍未实现；工单暂停/恢复接口仍未实现 |
| 4. 至少 2 个向其他系统发送或消费的幂等业务事件 | **未实现** | 全仓检索：MES **不发布任何事件**（无 `BusinessEventService.publish()` 调用），也**不消费任何事件**；与 MES 相关的事件类型在 `mfg_ops.biz_outbox` 的事件清单里为 0 条。ERP 生产订单与 MES 工单之间没有事件通道，只能靠 `prodOrderNo` 字符串关联 |
| 5. 至少 1 页本系统运营查询或统计报表 | **部分实现** | MES 首页显示最近 200 张工单的状态与进度；`mes_prod_result` 已同步生产订单级进度，但首页暂未读取该表，而是直接累加工单数量，因此同一订单包含多道工序时可能重复计数。OEE、达成率、直通率、工时偏差和在制品统计接口仍未实现。 |

**结论：当前尚未达到全部验收门槛。** 第 1、2 项已实现部分模块与单据流程，但按「至少 5 个模块」「至少 3 类单据」的门槛计仍有差距；事件集成仍未实现，首页进度概览也还没有切换到订单级实绩表。

## 未实现 / 缺口

### 业务能力缺口（catalog 七模块中五项全空）

- **派工**：`mes_work_order` 的 `equipment_code`/`work_center`/`workshop_code`/`workshop_user` 四列已全部映射进实体（计划 00 · F4-05），但**没有任何接口写入**；无派工单、无班组/人员分配、无优先级字段与接口、无排产。
- **在制品管理**：WIP 状态、工序流转、批次追溯、工艺参数、电子作业指导书——全部无表无接口。工单的 `op_seq` 是单值，无法表达工序链流转（对比 `mes_md_routing_operation` 副本表里明明有工序序列）。
- **领退料协同**：领料、补料、退料、超耗、替代料、批次绑定——全部未实现。WMS 侧有 `ISSUE`/`RETURN_MATERIAL` 库存动作，但两边没有对接（`WorkOrder` 与 WMS 无任何代码关联）。
- **异常与 Andon**：已具备缺料/设备/质量/工艺/安全等异常类型及响应、处理、关闭流程；人员异常分类、升级和响应时效仍未实现。
- **返工报废**：`WorkReport.scrapQty` 只有一个报废数量字段，**无返工任务单、无返工路线、无报废申请单、无损失归集**。注意 QMS 模块有 `qms_rework` 表（P3 的 `reworks` kind），MES 侧无对应物。
- **生产报表**：`mes_prod_result` 已写入生产订单进度汇总，但尚无独立查询接口；首页保留最近 200 张工单概览，暂按工单数量汇总，跨工序订单可能重复计数。OEE、达成率、直通率、工时偏差和在制品统计仍未实现。
- **工序暂停**：DDL 与实体都有暂停三列，无接口写入。
- **工时**：报工接口已保存 `workHours`、`startTime`、`endTime` 和班次，工单实际工时由报工明细累计；计划工时字段尚未参与产能分析。

### 工程与架构缺口

- **工单号重复无业务校验**：`POST /api/mes/work-orders` 不检查 `workOrderNo` 唯一性，DDL 的 `uk_wo_no` 会把冲突抛成数据库异常，绕过 `BizException` 错误码体系。对照 `WorkReportController` 是有 `existsByReportNo` 校验的，说明是遗漏而非设计。
- **报工锁与汇总**：报工新增、修改、删除均在事务内先锁工单和生产实绩行，再读写明细并重算汇总；修改/删除同时先锁定目标报工行。生产实绩使用 MySQL 唯一键原子 upsert 建行，再通过悲观写锁串行化同一生产订单的聚合。
- **无 `MesP3Controller`**：SRM 与 WMS 都接入了 `P3CrudService` 的泛型 CRUD 通道（`SrmP3Controller`/`WmsP3Controller`），MES 没有，`P3CrudService` 的表名注册表里也无任何 `mes_*` 条目。P4 开工时需先决定是补齐泛型通道还是继续手写。
- **查询性能**：`WorkReportController` 用 `workOrders.findAll().stream().filter(...)` 全表加载匹配工单；`POST /api/mes/reports` 每次都要拉全表。
- **无 `domain/` 层**：工单状态（`"CREATED"`/`"STARTED"`/`"COMPLETED"`）以字符串字面量散落在两个 controller 的 `if` 里，没有枚举或状态机定义，与 SRM/WMS 把状态机集中到 `P3CrudService.rules()` 的做法相比更分散。
- **无实体到 DTO 的隔离**：工单直接 `@RequestBody WorkOrder` 接收，客户端可以传任意字段（包括 `status`、`completedQty`），虽然建单时被强制覆盖，但报工接口的字段暴露面仍偏大。
- **测试覆盖仍有限**：服务层测试覆盖工单累计回算、生产实绩多工序聚合和 Andon 基本状态流转；尚未覆盖数据库并发场景及完整端到端接口链路。
- **无详情、无导出接口**：catalog 要求核心单据具备「草稿/提交/审核或确认/关闭或作废、状态时间轴、操作审计、权限校验、分页查询、详情查看和导出接口」——MES 只有分页列表与几个动作接口，缺详情、缺时间轴（`WorkOrder` 只有起止时间两个字段，无操作审计）。
- **无 MES 专属迁移脚本**：两张表的 schema 只在 `infra/db-init/05_business_systems_2.sql`（初始化脚本）里，增量迁移目录 V12–V38 无 MES 文件，后续改表需补迁移。
