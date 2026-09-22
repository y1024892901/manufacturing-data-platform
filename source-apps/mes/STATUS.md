# mes/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**MES 目前只有工单与报工两条骨架：7 个本职模块中 5 个（在制品、领退料、Andon、返工报废、生产报表）完全没有代码，工单状态机缺 `PAUSED`/`CANCELED`，报工不写工时、不同步 `mes_prod_result`、不发任何业务事件——按最低验收五项属于「未达标」，因为 P4 尚未开工。**

## 已实现

### 分层文件统计

`source-apps/mes/src/main/java/com/mfg/mes/` 共 7 个 Java 文件（6 个代码文件 + 1 个 `package-info.java`）：

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `domain/` | 0 | — |
| `entity/` | 2 | `WorkOrder`（`mes_work_order`）、`WorkReport`（`mes_work_report`） |
| `repo/` | 2 | `WorkOrderRepository`（无自定义方法）、`WorkReportRepository`（`existsByReportNo`） |
| `service/` | 0 | — |
| `controller/` | 2 | `WorkOrderController`、`WorkReportController` |
| `dto/` | 0 | — |
| `workflow/` | 0 | — |
| `integration/` | 0 | — |
| `query/` | 0 | — |

**无测试文件**（`src/test/` 目录不存在）。

**依赖的共享模块**（`pom.xml` 声明 `shared-common`/`shared-security`/`shared-workflow`/`shared-masterdata`，但代码实际只用到前两个）：

| 共享类 | MES 用它做什么 |
|---|---|
| `ApiResponse` / `ErrorCode` / `BizException` | 统一响应包装与错误码 |
| `CurrentUser.usernameOrSystem()` | 报工人 `operator_code` 与 `created_by` |
| `shared-workflow` | **声明了依赖但零使用**：无 `ApprovalCallback` 实现，工单无审批 |
| `shared-masterdata` | **声明了依赖但零使用**：MES 不主动校验物料是否已发布（对比 WMS 的 `assertConsumable`） |

## 接口清单

两个 controller，共 6 个处理方法。**没有任何 `@PreAuthorize` 注解**，权限只由 `SecurityConfig` 的 `SYSTEM_MES` 系统级门禁保护。

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
| POST | `/api/mes/reports` | 登记报工：`reportNo` 唯一校验 → 按 `workOrderNo` 找工单（`findAll().stream().filter`）→ 工单必须 `STARTED` → `reportQty = qualifiedQty + scrapQty` 必须 > 0 且累计不超计划 → 从工单继承 `prodOrderNo`/`opSeq`/`equipmentCode` → 回填 `reportDate`/`operatorCode`/`createdBy` → **同步累加工单的 `completedQty`/`qualifiedQty`/`scrapQty`，累计达计划自动 `COMPLETED`** |

> 两个入口（工单汇总报工 / 报工明细）**共用同一套累计口径**，都会推进工单进度并触发自动完工，这是刻意设计：避免「报工台账」和「工单进度」两套数字（`WorkReportController` 的类注释明确写了这一点）。但两者**都没有 `@Transactional`**，工单保存与报工保存之间如果失败会不一致。

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
| `WorkOrder` | `src_mes.mes_work_order` | `work_order_no`、`prod_order_no`、`product_code`、`op_seq`、`operation_code`、`operation_name`、`equipment_code`、`plan_qty`、`completed_qty`、`qualified_qty`、`scrap_qty`、`wo_status`、`actual_start_time`、`actual_end_time` |
| `WorkReport` | `src_mes.mes_work_report` | `report_no`、`work_order_no`、`prod_order_no`、`op_seq`、`report_qty`、`qualified_qty`、`scrap_qty`、`report_date`、`start_time`、`end_time`、`work_hours`、`equipment_code`、`operator_code`、`shift_code`、`is_paused`、`pause_reason`、`pause_minutes`、`created_by` |

### 有表但无任何 Java 代码读写的表

| 库.表 | 建表位置 | 说明 |
|---|---|---|
| `src_mes.mes_prod_result` | `infra/db-init/05_business_systems_2.sql` | 工序级生产实绩汇总（`progress_rate`、`current_op_seq`、`last_report_at`），**无实体、无 repo、无接口**；报工时不更新，`progress_rate` 永远是 NULL |
| `src_mes.mes_md_material` | 同上 | 物料主数据只读副本，由 **MDM** `MasterDataDistributor` 写入（`INSERT INTO src_mes.mes_md_material`），MES 侧无代码 |
| `src_mes.mes_md_routing_operation` | 同上 | 工艺路线工序只读副本，由 **MDM** `MasterDataDistributor.writeRoutingOperations(..., "src_mes.mes_md_routing_operation")` 写入，MES 侧无代码 |

> **实体与表结构不同步**：`mes_work_order` 的 DDL 有 `routing_code`、`routing_version`、`work_center`、`workshop_code`、`plan_start_time`、`plan_end_time`、`plan_hours`、`actual_hours`、`workshop_user`、`created_at`、`updated_at` 共 11 列**未映射进 `WorkOrder` 实体**。因此「派工到班组/工作中心」「计划与实际工时」「数据范围（车间负责人）」这些字段在 Java 侧完全不可见——派工与工时能力实际上只有列名，没有代码。

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
| 1. 至少 5 个本职模块具有实体、服务、接口和角色权限 | **未达标** | 只有 2 个模块有实体（工单与派工、工序执行），其余 5 个（在制品管理、领退料协同、异常与 Andon、返工报废、生产报表）**无实体无表无接口**。`service/` 层为空，业务逻辑全在 controller。角色权限：`SYSTEM_MES` 系统级门禁存在（`SecurityConfig`），`MES:*` 权限点在 `09_role_permission.sql` 有角色映射，但**本模块没有任何一个接口使用 `@PreAuthorize`**，权限点全部闲置 |
| 2. 至少 3 类核心单据可以从创建流转到关闭或作废 | **未达标** | 只有 1 类：**工单** `CREATED`→（`RELEASED`）→`STARTED`→`COMPLETED`，且**没有作废/取消路径**（`CANCELED` 未实现）。**报工单**（`WorkReport`）没有状态字段，建了就终态，不构成可流转单据。**第三类单据不存在**——无返工任务、无报废申请、无 Andon 事件、无领料单 |
| 3. 至少 1 个审批流程和 1 个异常处理闭环 | **未实现** | 无 `workflow/` 层、无 `ApprovalCallback` 实现（全仓 5 个回调分别属于 mdm/crm/erp/plm），`shared-workflow` 依赖被引入但零使用；工单无提交/审核动作。异常闭环：**无** —— `WorkReport` 有 `is_paused`/`pause_reason`/`pause_minutes` 三列，但报工接口不接收这三个字段，无暂停/恢复接口，无异常升级与响应时效 |
| 4. 至少 2 个向其他系统发送或消费的幂等业务事件 | **未实现** | 全仓检索：MES **不发布任何事件**（无 `BusinessEventService.publish()` 调用），也**不消费任何事件**；与 MES 相关的事件类型在 `mfg_ops.biz_outbox` 的事件清单里为 0 条。ERP 生产订单与 MES 工单之间没有事件通道，只能靠 `prodOrderNo` 字符串关联 |
| 5. 至少 1 页本系统运营查询或统计报表 | **未实现** | `query/` 层不存在；只有 `/api/mes/work-orders` 与 `/api/mes/reports` 两个分页列表，无进度、OEE、达成率、直通率、工时偏差、在制品任一接口或统计视图。`mes_prod_result.progress_rate` 列已建好但无人写入 |

**结论：五项全部未达标。** 严格来说第 1、2 项各有约两个模块/一类单据的雏形，但按「至少 5 个模块」「至少 3 类单据」的门槛计，差距明显。

## 未实现 / 缺口

### 业务能力缺口（catalog 七模块中五项全空）

- **派工**：`mes_work_order` 有 `equipment_code`/`work_center`/`workshop_code`/`workshop_user` 列，但实体只映射了 `equipment_code`；无派工单、无班组/人员分配、无优先级字段与接口、无排产。
- **在制品管理**：WIP 状态、工序流转、批次追溯、工艺参数、电子作业指导书——全部无表无接口。工单的 `op_seq` 是单值，无法表达工序链流转（对比 `mes_md_routing_operation` 副本表里明明有工序序列）。
- **领退料协同**：领料、补料、退料、超耗、替代料、批次绑定——全部未实现。WMS 侧有 `ISSUE`/`RETURN_MATERIAL` 库存动作，但两边没有对接（`WorkOrder` 与 WMS 无任何代码关联）。
- **异常与 Andon**：缺料/设备/质量/工艺/人员异常分类、升级、响应时效——全部未实现。
- **返工报废**：`WorkReport.scrapQty` 只有一个报废数量字段，**无返工任务单、无返工路线、无报废申请单、无损失归集**。注意 QMS 模块有 `qms_rework` 表（P3 的 `reworks` kind），MES 侧无对应物。
- **生产报表**：进度、OEE、达成率、直通率、工时偏差、在制品——全部未实现；`mes_prod_result` 建了表没有代码。
- **工序暂停**：DDL 与实体都有暂停三列，无接口写入。
- **工时**：`WorkReport.workHours`、`WorkOrder` 的 `plan_hours`/`actual_hours`（未映射）都是空置字段；`start_time`/`end_time` 也无接口写入（报工接口只回填 `reportDate`）。

### 工程与架构缺口

- **工单号重复无业务校验**：`POST /api/mes/work-orders` 不检查 `workOrderNo` 唯一性，DDL 的 `uk_wo_no` 会把冲突抛成数据库异常，绕过 `BizException` 错误码体系。对照 `WorkReportController` 是有 `existsByReportNo` 校验的，说明是遗漏而非设计。
- **无 `@Transactional`**：`WorkReportController.create()` 一次写两张表（报工 + 工单汇总），没有事务注解；`WorkOrderController.report()` 是单表但同样无事务声明。对比 WMS 的 `InventoryTransactionService` 全部标了 `@Transactional`。
- **报工不写工时与暂停**：`WorkReport` 有 `workHours`/`startTime`/`endTime`/`shiftCode`/`isPaused`/`pauseReason`/`pauseMinutes` 七列，`POST /api/mes/reports` 全部不接收，永远为 NULL/默认值。
- **无 `MesP3Controller`**：SRM 与 WMS 都接入了 `P3CrudService` 的泛型 CRUD 通道（`SrmP3Controller`/`WmsP3Controller`），MES 没有，`P3CrudService` 的表名注册表里也无任何 `mes_*` 条目。P4 开工时需先决定是补齐泛型通道还是继续手写。
- **查询性能**：`WorkReportController` 用 `workOrders.findAll().stream().filter(...)` 全表加载匹配工单；`POST /api/mes/reports` 每次都要拉全表。
- **无 `domain/` 层**：工单状态（`"CREATED"`/`"STARTED"`/`"COMPLETED"`）以字符串字面量散落在两个 controller 的 `if` 里，没有枚举或状态机定义，与 SRM/WMS 把状态机集中到 `P3CrudService.rules()` 的做法相比更分散。
- **无实体到 DTO 的隔离**：工单直接 `@RequestBody WorkOrder` 接收，客户端可以传任意字段（包括 `status`、`completedQty`），虽然建单时被强制覆盖，但报工接口的字段暴露面仍偏大。
- **无测试**：`src/test/` 不存在；占比更大的 `WorkReportController` 汇总逻辑（累计不超计划、自动完工）零覆盖。
- **无详情、无导出接口**：catalog 要求核心单据具备「草稿/提交/审核或确认/关闭或作废、状态时间轴、操作审计、权限校验、分页查询、详情查看和导出接口」——MES 只有分页列表与几个动作接口，缺详情、缺时间轴（`WorkOrder` 只有起止时间两个字段，无操作审计）。
- **无 MES 专属迁移脚本**：两张表的 schema 只在 `infra/db-init/05_business_systems_2.sql`（初始化脚本）里，增量迁移目录 V12–V38 无 MES 文件，后续改表需补迁移。
