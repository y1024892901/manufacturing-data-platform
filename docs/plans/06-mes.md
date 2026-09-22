# 06. MES 生产执行

> **依赖**：[00 横向地基](00-foundation.md)（鉴权 / 事件消费 / 终态动作 / 实体同步四项模板）· **预估**：2–3 轮（[plans/README.md](README.md) 基线 2 轮；本计划含 5 个从零建模块、约 10 张新表，取下限以外的余量）· **对应验收项**：① ② ③ ④ ⑤（五项全部，从零建）

## 目标

把 MES 从「工单 + 报工两条骨架」建成制造执行域：工单能下达、派工、暂停、完工、取消并留下状态时间轴；在制品按工序链流转且可追溯；领退料与 WMS 库存真实联动；异常与 Andon 有响应时效与升级；返工与报废走审批并归集损失；最后产出进度、OEE、达成率、直通率、工时偏差、在制品六类报表。完成后 MES 达到 [system-functional-catalog.md](../system-functional-catalog.md) 的「最低验收五项」，并由 [08 前端与运营报表](08-frontend-reports.md) 补齐车间可点的页面。

## 现状

`source-apps/mes/` 共 **7 个 Java 文件**（2 个 Controller、6 个端点、2 个实体、2 个 repo），`domain/`、`service/`、`workflow/`、`integration/`、`query/` 五层全空，`src/test/` 不存在。工单状态机只有 `CREATED → STARTED → COMPLETED` 一条路：`RELEASED` 无接口可写、`PAUSED`/`CANCELED` 未实现；报工不写 `work_hours`/`start_time`/`end_time`/`shift_code` 与暂停三列，两个入口都无 `@Transactional`，且用 `findAll().stream().filter` 全表匹配工单。7 个本职模块中**5 个零代码**（在制品、领退料、异常与 Andon、返工报废、生产报表）。`mes_prod_result`（按 `prod_order_no` 唯一）与 `mes_md_routing_operation` 两张表已建好但无任何 Java 类读写；`mes_work_order` 的 11 列（`routing_code`/`routing_version`/`work_center`/`workshop_code`/`plan_start_time`/`plan_end_time`/`plan_hours`/`actual_hours`/`workshop_user`/`created_at`/`updated_at`）未映射进实体。5 个种子权限码（`MES:WORK_ORDER:VIEW`/`START`/`FINISH`/`PAUSE`、`MES:WORK_REPORT:CREATE`）**无任何接口使用**。逐条证据见 [mes/STATUS.md](../../source-apps/mes/STATUS.md) 的「对照最低验收」与「未实现 / 缺口」两节。

## 任务拆解

| # | 模块 | 任务 | 涉及文件 / 位置 | 验收方式 |
|---|---|---|---|---|
| 1 | 架构决策 | 决策 MES 是否接入 P3 泛型通道：现状 `P3CrudService` 的注册表 `T` 有 20 项（SRM 7 / WMS 6 / QMS 7），无任何 `mes_*`；注意**只加 `T` 不够**，`sc()`/`rules()`/`cols()` 三个 switch 必须同时扩展，且 `mes_work_order` 的状态列名是 `wo_status` 而非 `sc()` 默认的 `status` | `source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java`（`T` 第 4 行、`sc()` 第 5 行、`rules()` 第 10 行、`cols()` 第 11 行）；拟新建 `source-apps/mes/src/main/java/com/mfg/mes/controller/MesP3Controller.java` | 决策结论写入 [mes/PLAN.md](../../source-apps/mes/PLAN.md)；若接入，需给出 `T`+`sc()`+`rules()`+`cols()` 四处改动的具体键值 |
| 2 | 工序执行 | 建 `domain/` 层：`MesWorkOrderStatus` 枚举（`CREATED`/`RELEASED`/`STARTED`/`PAUSED`/`COMPLETED`/`CANCELED`，与 DDL 注释一致）+ `MesWorkOrderStateMachine` 显式迁移表，替换散落在 controller `if` 里的字符串字面量 | 新建 `source-apps/mes/src/main/java/com/mfg/mes/domain/MesWorkOrderStatus.java`、`MesWorkOrderStateMachine.java` | 单测覆盖每种迁移的允许 / 拒绝（如 `COMPLETED → PAUSED` 必须被拒） |
| 3 | 工单与派工 | 补齐 `WorkOrder` 实体未映射的 11 列（`routingCode`/`routingVersion`/`workCenter`/`workshopCode`/`planStartTime`/`planEndTime`/`planHours`/`actualHours`/`workshopUser`/`createdAt`/`updatedAt`） | `source-apps/mes/src/main/java/com/mfg/mes/entity/WorkOrder.java` | `GET /api/mes/work-orders` 返回体中出现上述字段 |
| 4 | 工单与派工 | 补齐状态动作：`POST /api/mes/work-orders/{id}/release`（下达 `CREATED → RELEASED`）、`/{id}/pause`、`/{id}/resume`、`/{id}/cancel`（非 `COMPLETED` 可取消）；暂停区间与操作人落状态日志 | `controller/WorkOrderController.java`；新建 `service/WorkOrderService.java`、迁移新建 `mes_work_order_status_log` | 一路走通「建单 → 下达 → 开工 → 暂停 → 恢复 → 完工」与另一路「建单 → 取消」 |
| 5 | 工单与派工 | 补唯一性校验与事务边界：`WorkOrderRepository.existsByWorkOrderNo`，建单重复时抛 `BizException`（不再撞 `uk_wo_no` 抛 500） | `repo/WorkOrderRepository.java`、`service/WorkOrderService.java` | 重复工单号返回统一业务错误码与消息，非数据库异常 |
| 6 | 工序执行 | 把报工逻辑搬入 `service/`：累计数量、自动完工判定、`prodOrderNo`/`opSeq`/`equipmentCode` 继承全部收进 `WorkReportService`，加 `@Transactional`；`findAll().stream().filter` 改为 `WorkOrderRepository.findByWorkOrderNo` | 新建 `service/WorkReportService.java`；改 `controller/WorkReportController.java`、`repo/WorkOrderRepository.java` | 构造第二张表写入失败，验证工单累计与报工记录同事务回滚；报工接口不再全表加载 |
| 7 | 工序执行 | 报工补齐 7 个空置列：`WORK_HOURS`/`START_TIME`/`END_TIME`/`SHIFT_CODE`/`IS_PAUSED`/`PAUSE_REASON`/`PAUSE_MINUTES` 由 `POST /api/mes/reports` 接收并落库 | `entity/WorkReport.java`、`controller/WorkReportController.java`、`dto/` 新建报工入参对象（现直接绑实体） | 报工后这 7 列在库中非空 / 非默认值 |
| 8 | 工序执行 | 打通 `mes_prod_result`（现无实体无 repo）：按 `prod_order_no` upsert `total_plan_qty`/`total_completed_qty`/`total_qualified_qty`/`progress_rate`/`current_op_seq`/`last_report_at`，与报工同事务 | 新建 `entity/ProdResult.java`、`repo/ProdResultRepository.java`、`service/ProdResultService.java` | 报工后能按 `prod_order_no` 查到实绩行，`progress_rate = total_completed_qty / total_plan_qty` |
| 9 | 工单与派工 | 补核心单据的通用能力：详情 `GET /api/mes/work-orders/{id}`、状态时间轴 `GET /api/mes/work-orders/{id}/timeline`、导出 `GET /api/mes/work-orders/export`（CSV，带 BOM 头，对齐前端既有导出做法） | `controller/WorkOrderController.java`、`query/` 新建 `MesQueryService.java` | 每次状态动作后时间轴新增一条；导出文件可被 Excel 正确打开 |
| 10 | 全员 | 权限收口：MES 全部接口加 `@PreAuthorize`，激活已种子化的 5 个权限码；新增的按钮级权限码走 5 列 `perm_type='BUTTON'` 形式（对齐 `V38`） | 两个 controller + 新 controller；迁移中补 `sys_permission` / `sys_role_permission` | `TEAM_LEADER` 可开工与报工但不可取消工单；无权限账号返回 403 |
| 11 | 派工 | 派工单落地：新建 `mes_work_dispatch` 表 + `WorkDispatch` 实体 + `DispatchController`（`/api/mes/dispatches`：分页、新建、`POST /{id}/assign` 改派、`POST /{id}/cancel`），字段含 `team_code`/`operator_code`/`equipment_code`/`shift_code`/`priority` | 新建 `entity/WorkDispatch.java`、`repo/WorkDispatchRepository.java`、`controller/DispatchController.java`、迁移 | 工单可派到班组 / 人员 / 设备 / 班次并带优先级 |
| 12 | 派工 | 有限排产：`POST /api/mes/dispatches/plan` 按工作中心产能与 `mes_md_routing_operation.run_time_min` 生成派工建议（返回建议列表，不落库） | 新建 `service/DispatchPlanService.java`；读 `entity/MdRoutingOperation.java`（见 #14） | 给定一批工单返回带顺序与设备分配的建议清单 |
| 13 | 在制品 | WIP 与工序流转：新建 `mes_wip_item` 表 + `WipItem` 实体 + `WipController`（`/api/mes/wip`：按工单 + 工序建 WIP、`POST /{id}/move` 转序、`GET /api/mes/wip/trace/{batchNo}` 批次追溯） | 新建 `entity/WipItem.java`、`service/WipService.java`、`controller/WipController.java`、迁移 | 一张工单可跨多道工序流转；按批次号能回溯全部工序、报工与操作人 |
| 14 | 在制品 | 工序链来自主数据而非硬编码：为 `mes_md_routing_operation` 建 `MdRoutingOperation` 实体 + repo（现由 MDM `writeRoutingOperations(..., "src_mes.mes_md_routing_operation")` 写入但 MES 侧零代码），提供 `GET /api/mes/routings/{routingCode}/{routingVersion}/operations`；`is_inspection_op=1` 的工序在开工时提示报检 | 新建 `entity/MdRoutingOperation.java`、`repo/MdRoutingOperationRepository.java`、`controller/RoutingController.java` | 派工与转序的工序顺序取自副本表；改副本表数据后接口返回随之变化 |
| 15 | 领退料 | 领退料单据：新建 `mes_material_issue` 表（`issue_type`= `ISSUE`/`SUPPLEMENT`/`RETURN`、`qty`、`batch_no`、`over_consume`、`status`）+ `MaterialIssue` 实体 + `MaterialIssueController`（`/api/mes/material-issues`）；领退料转发 WMS `POST /api/wms/inventory-actions/{action}`（前置见「风险与前置」第 2 条） | 新建 `entity/MaterialIssue.java`、`service/MaterialIssueService.java`、`controller/MaterialIssueController.java`、迁移；调用 `source-apps/wms/.../service/InventoryTransactionService.java` 对应的 HTTP 动作 | 领料成功后 WMS 库存减少，`wms_inventory_ledger` 留 `source_system='MES'`、`source_no=<工单号>` 的记录；同一 `idempotencyKey` 重复提交不重复扣减 |
| 16 | 领退料 | 超耗与替代料：领料量超工单需领量时置 `over_consume=1` 并进入审批（见 #19）；记录 `substitute_material_code` 与替代原因 | `service/MaterialIssueService.java`、迁移补列 | 超耗领料在审批通过前不得下发 WMS；替代料在明细中可查 |
| 17 | 异常与 Andon | Andon 事件闭环：新建 `mes_andon_event` 表（`andon_type`= `MATERIAL`/`EQUIPMENT`/`QUALITY`/`PROCESS`/`PERSONNEL`、`level`、`status`、`raised_by`/`responded_at`/`response_minutes`/`closed_at`）+ `AndonEvent` 实体 + `AndonController`（`POST /api/mes/andon` 上报、`POST /{id}/respond`、`/{id}/escalate`、`/{id}/close`） | 新建 `entity/AndonEvent.java`、`service/AndonService.java`、`controller/AndonController.java`、迁移 | 响应时自动算出 `response_minutes`；超时限未响应可升级到上一级；关闭必须留处置说明 |
| 18 | 返工报废 | 返工任务与报废申请：新建 `mes_rework_task`（`rework_route`/`qty`/`status`）与 `mes_scrap_request`（`scrap_qty`/`reason`/`loss_amount`/`status`）两表 + 实体 + `ReworkController`（`/api/mes/reworks`）/ `ScrapController`（`/api/mes/scraps`）；返工任务可生成返工工单，报废归集工时与物料损失 | 新建 `entity/ReworkTask.java`、`entity/ScrapRequest.java`、`service/ReworkService.java`、`service/ScrapService.java`、两个 controller、迁移 | QMS 判定不合格的批次可在 MES 建返工任务并跟踪到关闭；报废申请在审批通过前停在 `SUBMITTED` |
| 19 | 审批 | 接入审批内核：新建 `MesScrapApprovalCallback`、`MesOverConsumeApprovalCallback`（实现 `com.mfg.workflow.callback.ApprovalCallback` 的 `supports`/`onApproved`/`onRejected`），并在迁移中用 `WHERE NOT EXISTS` 形式注册 `wf_definition`（`def_code` 形如 `MES_SCRAP_REQUEST`，`biz_type` 取裸大写名词，节点 `node_seq` 10/20/30、`approve_mode='SINGLE'`、`reject_action='BACK'`）；包名落位见「风险与前置」第 6 条 | 新建 `source-apps/mes/src/main/java/com/mfg/mes/callback/MesScrapApprovalCallback.java` 等；迁移插入 `mfg_auth.wf_definition` / `wf_node` | 报废申请提交后统一审批中心出现待办；通过后单据状态回写并可继续执行 |
| 20 | 集成 | 事件发布与消费：新建 `integration/MesEventPublisher`（调 `BusinessEventService.publish(eventType, sourceSystem, targetSystem, aggregateType, aggregateId, payload)`）发布 `MES.WORK_ORDER.COMPLETED`、`MES.REPORT.CREATED`、`MES.SCRAP.CREATED`、`MES.ANDON.RAISED`；新建 `integration/MesEventConsumer` 轮询 `mfg_ops.biz_inbox` 中 `target_system='mes'` 的 `ERP.PRODUCTION_ORDER.RELEASED` 生成工单 | 新建 `integration/MesEventPublisher.java`、`integration/MesEventConsumer.java`；消费幂等靠 `biz_inbox` 唯一键 `(event_id, target_system)` + 本地处理标记 | 两个方向各至少 2 个事件可回放：ERP 下达能生成工单，MES 完工能被下游读到；重复投递不重复建单 |
| 21 | 生产报表 | 六个统计端点：新建 `query/MesReportController` 提供 `/api/mes/statistics/progress`（进度）、`/oee`、`/achievement`（达成率）、`/fpy`（直通率）、`/hours-variance`（工时偏差）、`/wip`（在制品）；`query/MesQueryService` 以 `JdbcTemplate` 聚合。**路径不得用 `/api/mes/reports`——该路径已被报工占用** | 新建 `query/MesReportController.java`、`query/MesQueryService.java` | 六个端点各返回非空结构；无数据时返回空集合而非报错；数值可与 `mes_prod_result`、`mes_work_report` 手工核对 |
| 22 | 工程化 | 建测试与迁移：新增 `source-apps/mes/src/test/`（现不存在）覆盖状态机、报工累计与自动完工、领退料幂等、Andon 响应时效、报表聚合；全部新表走 `V39+` 迁移（命名 `V39__p4_mes_<topic>.sql`，对齐 `V29`–`V38` 的 `p<phase>_<domain>_<topic>`），**不改 `infra/db-init/`** | 新建 `source-apps/mes/src/test/java/com/mfg/mes/…`；迁移目录 `source-apps/bootstrap/src/main/resources/db/migration/`（现最高 `V38`） | 模块测试通过；`infra/db-init/05_business_systems_2.sql` 无改动；Flyway 从 `V38` 平滑升到 `V39+` |

> 轮次划分：第 1 轮 = #1–#10（地基与工单/报工收口），第 2 轮 = #11–#18（5 个零代码模块），第 3 轮 = #19–#22（审批、事件、报表、工程化）。第 2、3 轮之间无强依赖，可由两人并行。

## 完成标准

- [ ] 第 1 项架构决策已有结论并写入 [mes/PLAN.md](../../source-apps/mes/PLAN.md)，含泛型通道四处改动的具体键值或手写控制器的理由；
- [ ] 工单状态机六态齐备（`CREATED`/`RELEASED`/`STARTED`/`PAUSED`/`COMPLETED`/`CANCELED`），每条迁移有单测；
- [ ] 至少 3 类单据能从创建流转到关闭或作废：**工单**（建单 → 下达 → 开工 → 完工，或 → 取消）、**报废申请**（提交 → 审批 → 执行 / 驳回）、**Andon 事件**（上报 → 响应 → 关闭）；返工任务同样可到关闭；
- [ ] ≥5 个本职模块具备实体 + 服务 + 接口 + 角色权限：工单与派工、工序执行、在制品管理、领退料协同、异常与 Andon、返工报废（6 个）；
- [ ] `domain/`/`service/`/`workflow/`（或 `callback/`）/`integration/`/`query/`/`dto/`/`src/test` 目录全部非空；报工与工单累计写入同一事务；
- [ ] 至少 1 个审批流程（报废或超耗）+ 1 个异常闭环（Andon 上报 → 响应 → 关闭，含响应时效）；
- [ ] 至少 2 个幂等业务事件：发出的（`MES.*` ≥2 个）与消费的（`ERP.PRODUCTION_ORDER.RELEASED` 生成工单），重复投递不产生重复单据；
- [ ] 至少 1 页本系统运营报表：`/api/mes/statistics/*` 六类端点全部可用，并在 [08](08-frontend-reports.md) 中有对应页面可点；
- [ ] MES 全部接口带 `@PreAuthorize`，种子中 5 个既有权限码全部被读取；
- [ ] `mes_prod_result`、`mes_md_routing_operation` 两张「有表无代码」的表都有实体与 repo；
- [ ] 无 `findAll()` + `stream().filter` 形式的全表匹配残留；
- [ ] [mes/STATUS.md](../../source-apps/mes/STATUS.md) 五项验收对照全部由「未达标」改为「达标」，[../current-status.md](../current-status.md) 的矩阵与总览同步更新。

## 风险与前置

1. **架构决策点（必须先决策再开工）**：MES 是否接入 `P3CrudService` 泛型通道。SRM/WMS/QMS 各有 `SrmP3Controller`/`WmsP3Controller`/`QmsP3Controller`，MES 没有；泛型通道的注册表 `T` 是 20 项且无 `mes_*`，`sc()` 对未注册键默认取 `status` 列而 `mes_work_order` 用的是 `wo_status`，`rules()` 对未注册键返回空 Map（**所有状态迁移被阻断**）。因此接入的代价不是「加一行注册表」，而是 `T`+`sc()`+`rules()`+`cols()` 四处齐改；有状态机与事务的工单/派工仍建议手写 controller，无状态事实表（如只读的实绩汇总）才适合泛型。结论必须在 #1 落定，否则 #4、#11、#18 的动作设计会返工。
2. **领退料的 WMS 前置**：WMS 所有库存数量变更的唯一写入口是 `InventoryTransactionService.action(type, cmd)`，其 `assertType()` 接受 `ISSUE`/`RETURN_MATERIAL`，但 HTTP 层 `WmsP3Controller` 的 `POST /api/wms/inventory-actions/{action}` 只映射 `freeze`/`unfreeze`/`quarantine`/`release`/`scrap`/`supplier-return` 六个动作——**MES 无法经 HTTP 发起领料与退料**。需先由 [03 QMS + WMS 收口](03-qms-wms.md) 补 `case"issue"`/`case"return-material"` 分支与对应权限码，MES 的 #15 才能落地。补料无专用动作码，须在 `COUNT_GAIN` 与既有动作之间决策。
3. **事件消费侧无先例**：`mfg_ops.biz_inbox` 只由 `BusinessEventService.dispatch()` 的 `INSERT IGNORE` 写入，**全仓没有任何模块读它**，消费端需从零建。建议 [00 横向地基](00-foundation.md) 先定消费者模板（轮询间隔、幂等标记、失败重试），MES 的 #20 直接套用，否则每个系统会各写一套。
4. **建表通道**：MES 的三张业务表由 `infra/db-init/05_business_systems_2.sql` 创建（V1–V11 通道，已 baseline）。本计划所有新表与新列一律走 `V39+`，不得回改 `infra/db-init/`；`mes_work_order` 的 11 列未映射属实体问题，不需要改表（#3 只改实体）。
5. **主数据校验缺位**：`mes_md_material` 副本未被 MES 读取，MES 不校验物料是否已发布（对比 WMS 的 `assertConsumable`）。#15 的领料建议顺带加入该校验；若需严格一致，先在 [00](00-foundation.md) 统一副本读取方式。
6. **审批回调落位待定**：catalog 的代码标准要求 `workflow/` 层，但全仓 5 个既有 `ApprovalCallback` 实现都在 `<module>/src/main/java/com/mfg/<module>/callback/` 包（[../current-status.md](../current-status.md) 缺陷 #9 已记录此偏差）。#19 需明确沿用 `callback/` 还是新建 `workflow/`；建议由 [00](00-foundation.md) 统一约定后，MES 与能源一并遵守，避免出现第三种放法。
7. **报工与完工语义需确认**：现逻辑是「累计正好等于计划数才自动完工，超产不允许，欠产永远停在 `STARTED`」。是否允许超产、是否让报工单本身具备状态、暂停时长记在工单还是报工上，都需在 #2/#4/#7 开工前确认（相关 `WorkReport` 暂停三列的归属是待确认项）。
8. **报表路径冲突**：`/api/mes/reports` 已被报工（`WorkReportController`）占用，前端 `/mes/reports` 亦指向报工明细。统计端点因此统一走 `/api/mes/statistics/*`，不重命名既有报工路由（重命名会连带改前端路由与菜单）。
9. **测试基建未知**：全仓仅 2 个测试文件（`shared/security`、`wms`），MES 的 `src/test/` 不存在；`source-apps/mes/pom.xml` 是否已引入 `spring-boot-starter-test` **待确认**，若未引入需在 #22 一并补。
10. **轮数**：本计划 22 个任务、5 个从零建模块，正常需 2–3 轮；若 #1 选择接入泛型通道且需回头改 `P3CrudService`，或「风险与前置」第 2 条的 WMS 前置未就绪，则占用上限 3 轮。
