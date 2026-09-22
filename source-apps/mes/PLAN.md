# mes/ — 生产执行与过程追溯

> **整体计划** · 依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 MES：生产执行与过程追溯 章节

## 系统定位

MES 是十系统主线中「订单变产品」的执行环节：接收 ERP 下达的生产订单（`WorkOrder.prodOrderNo`）、把工序拆成工单（`op_seq`/`operation_code`/`equipment_code`），向下把完工数量回给订单进度、把不良品推给 QMS、把报废与停机推给 EAM。它管的是「**工单干到哪一步、出了多少活、出了多少废品**」。

**本模块当前状态与其他两个模块有本质区别**：按 [implementation-roadmap.md](../../docs/implementation-roadmap.md)，MES 属于 **P4：生产执行、设备与能源闭环**（SRM/WMS 属于已开工的 P3），而仓库里 MES 只有 P1 阶段建的**只读主数据副本表**和两张业务表的骨架，P4 尚未开始。因此本 PLAN 描述的是**待建设目标**，而非在建计划。

## 本职模块基线

摘自 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 MES 章节，右侧为按 2026-09-22 实际代码判断的完成度（详见 [STATUS.md](STATUS.md)）。

| 模块 | 核心能力 | 当前状态 |
|---|---|---|
| 工单与派工 | 工单接收、排产、派工、班组/人员/设备分配、优先级 | 部分实现 |
| 工序执行 | 开工、报工、暂停、转序、完工、工时、产量、良品/不良品 | 部分实现 |
| 在制品管理 | WIP 状态、工序流转、批次追溯、工艺参数、电子作业指导书 | 未实现 |
| 领退料协同 | 领料、补料、退料、超耗、替代料、物料批次绑定 | 未实现 |
| 异常与 Andon | 缺料、设备、质量、工艺、人员异常，升级和响应时效 | 未实现 |
| 返工报废 | 返工任务、返工路线、报废申请、损失归集 | 未实现 |
| 生产报表 | 进度、OEE、达成率、直通率、工时偏差、在制品 | 未实现 |

判断依据：7 个模块里只有前两个有代码，且都是**单体工单**级别（一张工单一个工序，`op_seq` 是工单上的一个字段而非流转链）；其余 5 个模块在本仓库中**无实体、无表、无接口**。另需注意：`mes_prod_result`（工序级生产实绩汇总）与 `mes_md_routing_operation` 两张表已在 DDL 里建好，但**没有任何 Java 类映射或读写它们**。

## 代码分层标准

本项目 `source-apps/<system>/` 统一分层，MES 的实际落地情况：

| 分层 | 本项目标准职责 | MES 现状 | 说明 |
|---|---|---|---|
| `domain/` | 状态机、枚举、领域规则 | **空** | 工单状态机（`CREATED`/`RELEASED`/`STARTED`/`COMPLETED`）以字符串字面量散落在 `WorkOrderController` 的 `if` 判断里，没有枚举 |
| `entity/` | 单据、台账、配置实体 | 有 2 个 | `WorkOrder`（`mes_work_order`）、`WorkReport`（`mes_work_report`） |
| `repo/` | 数据访问与领域查询 | 有 2 个 | `WorkOrderRepository`（无自定义方法）、`WorkReportRepository`（`existsByReportNo`） |
| `service/` | 业务动作、校验、事务边界 | **空** | 全部业务逻辑写在 controller 方法体内；报工汇总到工单的逻辑直接放在 `WorkReportController.create()` 里，无 `@Transactional` |
| `controller/` | 独立维护 API | 有 2 个 | `WorkOrderController`、`WorkReportController` |
| `workflow/` | 本系统审批策略与审批回调 | **空** | 无（catalog 要求「返工、报废」审批） |
| `integration/` | 上下游事件、分发、幂等处理 | **空** | 无（MES 不发布也不消费任何业务事件） |
| `query/` | 列表、详情、台账、统计查询 | **空** | 分页直接写在 controller |
| `dto/` | 入参命令对象 | **空** | 工单直接绑 `@RequestBody WorkOrder` 实体 |

**缺少泛型 P3 接口层**：SRM 有 `SrmP3Controller`、WMS 有 `WmsP3Controller`，MES **没有对应的 `MesP3Controller`**，`P3CrudService` 的表名注册表（`onboarding`…`8d` 共 20 项）里也**没有任何 `mes_*` 条目**。这意味着 MES 的每个能力都必须逐个手写 controller，无法复用 P3 的泛型 CRUD 通道——这是 P4 开工前需要先决策的架构分叉点。

**关于 MES 的「集成」现状**：MES 表确实在流入数据，但全部来自外部模块，MES 侧无代码参与。

| 数据流 | 发起方 | 落到 MES 的位置 |
|---|---|---|
| 物料主数据分发 | MDM `MasterDataDistributor`（`INSERT INTO src_mes.mes_md_material`） | `mes_md_material` 只读副本 |
| 工艺路线工序分发 | MDM `MasterDataDistributor.writeRoutingOperations(..., "src_mes.mes_md_routing_operation")` | `mes_md_routing_operation` 只读副本 |
| 权限门禁 | `SecurityConfig` 第 79 行 `.requestMatchers("/api/mes/**").hasAuthority("SYSTEM_MES")` | — |
| 权限点 | `09_role_permission.sql` 映射 `MES:*` 到 `TEAM_LEADER`/`WORKSHOP_CHIEF`/`PROD_SUPERVISOR`/`PROD_MANAGER` | — |

## 最低验收（五项）

抄录 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 末尾「系统本职完成的最低验收」：

1. 至少 5 个本职模块具有实体、服务、接口和角色权限；
2. 至少 3 类核心单据可以从创建流转到关闭或作废；
3. 至少 1 个审批流程和 1 个异常处理闭环；
4. 至少 2 个向其他系统发送或消费的幂等业务事件；
5. 至少 1 页本系统运营查询或统计报表。

MES 当前的逐条对照与缺口，见 [STATUS.md](STATUS.md#对照最低验收)。
