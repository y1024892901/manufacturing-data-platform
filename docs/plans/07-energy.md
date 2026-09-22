# 07. 能源管理

> **依赖**：[00 横向地基](00-foundation.md)（鉴权 / 事件消费 / 终态动作 / 实体同步四项模板）· **预估**：2–3 轮（[plans/README.md](README.md) 基线 2 轮；本计划含 5 个从零建模块、2 类可流转单据）· **对应验收项**：① ② ③ ④ ⑤（五项全部，从零建）

## 目标

把能源管理从「两张台账手工录入」建成完整的计量与分析域：先有能源基础（能源品类、仪表、采集点、计量层级、设备/车间映射），再有数据采集（抄表、补录、校验、缺失处理），然后是台账（日/月用能、峰平谷、设备→车间汇总、订单分摊）、定额与目标（可审批、可作废的单据）、异常管理（阈值、告警、处置确认、节能措施）、碳管理（折标、排放因子、碳排与订单碳足迹），最后产出用能、单耗、排名、超定额、峰谷电费、碳排六类报表。完成后能源达到 [system-functional-catalog.md](../system-functional-catalog.md) 的「最低验收五项」，页面由 [08 前端与运营报表](08-frontend-reports.md) 补齐。

## 现状

`source-apps/energy/` 共 **7 个 Java 文件**（2 个 Controller、2 个实体、2 个 repo、4 个端点），`domain/`、`service/`、`workflow/`、`integration/`、`query/` 五层全空，`src/test/` 不存在。已实现的业务动作只有四件：能耗非负校验、日期与介质缺省、单位能耗自动计算（`energyValue / outputQty`，6 位小数 `HALF_UP`，两张台账各写一份重复代码）、车间级「车间 + 日期 + 介质」去重。**两个实体都没有状态字段**，因此存在 **0 类**可流转单据；不发出也不消费任何事件；`GET` 两个列表接口都是 `findAll()` 全量返回，无分页、无筛选、无聚合。`energy_md_equipment`（设备主数据只读副本）有 DDL 但 MDM 侧 `MasterDataDistributor.writeToEnergy()` 是 `return 0;` 空实现，该表从未被写入，设备编码目前是自由文本、无校验。`energy_equipment_usage` 的 `baseline_value`/`deviation_rate`/`is_abnormal` 三列存在但实体未映射；`uk_energy_equip (equipment_code, stat_date, stat_hour, energy_type)` 无预检，重复上报抛数据库约束异常（500）而非业务错误。种子里的 3 个权限码（`ENERGY:USAGE:VIEW`/`CREATE`、`ENERGY:ANALYSIS:VIEW`）**无任何接口读取**，其中 `ENERGY:ANALYSIS:VIEW` 已授予 `ENERGY_ADMIN` 与 `DATA_ANALYST` 却无对应分析接口。逐条证据见 [energy/STATUS.md](../../source-apps/energy/STATUS.md) 的「对照最低验收」与「未实现 / 缺口」两节。

## 任务拆解

| # | 模块 | 任务 | 涉及文件 / 位置 | 验收方式 |
|---|---|---|---|---|
| 1 | 能源基础 | 建 `domain/` 层：`EnergyType` 枚举（`ELECTRIC`/`GAS`/`WATER`/`STEAM`，与 DDL 注释一致）+ `EnergyUnit` 校验，替换「靠 DDL 注释约定、可写入任意字符串」的现状 | 新建 `source-apps/energy/src/main/java/com/mfg/energy/domain/EnergyType.java`、`EnergyUnit.java` | 上报非法 `energyType` 返回业务错误；`electric` 与 `ELECTRIC` 规范化后车间级去重生效（现状两种写法会绕过去重） |
| 2 | 数据采集 | 建 `service/` 层：新建 `EnergyUsageService`，把两个 Controller 里各写一份的默认值填充与单位能耗计算（6 位 `HALF_UP`）收敛到一处，并加 `@Transactional`；同时补齐操作人（现模块完全不调用 `CurrentUser`，无操作人记录） | 新建 `service/EnergyUsageService.java`；改 `controller/EquipmentUsageController.java`、`controller/WorkshopUsageController.java` | 两个上报接口走同一 service，改造前后返回值一致；新写入的记录带操作人 |
| 3 | 数据采集 | 设备级查重（明确缺陷）：加 `existsByEquipmentCodeAndStatDateAndStatHourAndEnergyType`，上报前预检并抛 `MASTER_DATA_ALREADY_EXISTS`，对齐车间级既有做法与 `uk_energy_equip` 唯一键 | `repo/EquipmentUsageRepository.java`、`controller/EquipmentUsageController.java` | 重复上报返回业务错误码与中文消息，不再出现 500 |
| 4 | 能源台账 | 实体补齐：`EquipmentUsage` 映射 `baselineValue`/`deviationRate`/`isAbnormal` 三列（DDL 已有，实体未映射）；核对 `WorkshopUsage` 是否漏映射 `createdAt` | `entity/EquipmentUsage.java`、`entity/WorkshopUsage.java` | `GET /api/energy/usages` 返回体中出现 `isAbnormal` 等字段；（`WorkshopUsage` 的漏列清单待确认） |
| 5 | 能源台账 | 列表分页与筛选：两个 `GET` 从 `findAll()` 改为分页 + 按 `statDate` 区间 / `workshopCode` / `equipmentCode` / `energyType` 筛选，返回结构与全站前端约定一致（`content` / `totalElements`） | 两个 controller、两个 repo、`service/EnergyUsageService.java` | `GET /api/energy/usages?page=0&size=20&from=&to=` 返回分页结构；前端不再需要全量拉取 |
| 6 | 能源基础 | 仪表台账：新建 `energy_meter` 表（`meter_code`/`meter_name`/`energy_type`/`unit_code`/`workshop_code`/`equipment_code`/`measure_point_code`/`multiplier`/`status`）+ `EnergyMeter` 实体 + `MeterController`（`/api/energy/meters`：分页、新建、详情、`POST /{id}/disable`） | 新建 `entity/EnergyMeter.java`、`repo/EnergyMeterRepository.java`、`controller/MeterController.java`、迁移 | 仪表可建档、可停用；`/energy/meters` 页面（08）有数据源 |
| 7 | 能源基础 | 采集点与计量层级：新建 `energy_measure_point` 表（`measure_point_code`/`name`/`level_code`/`parent_measure_point_code`/`meter_code`）+ `MeasurePoint` 实体 + `MeasurePointController`（`/api/energy/measure-points`，含 `GET /tree` 返回层级树） | 新建 `entity/MeasurePoint.java`、`service/MeasurePointService.java`、`controller/MeasurePointController.java`、迁移 | 能建「总表 → 车间分表 → 设备表」三级并查询到树形结构 |
| 8 | 能源基础 | 设备/车间映射与校验：为 `energy_md_equipment` 建 `MdEquipment` 实体 + repo（现由 MDM 空实现写入，见「风险与前置」第 1 条），上报时校验 `equipment_code` 存在并自动带出 `workshop_code` | 新建 `entity/MdEquipment.java`、`repo/MdEquipmentRepository.java`；`service/EnergyUsageService.java` | 非法设备编码返回业务错误；合法编码自动带出车间；副本表为空时的降级策略需在实现前确认 |
| 9 | 数据采集 | 抄表与补录：新建 `energy_meter_reading` 表（`meter_code`/`reading_date`/`reading_hour`/`reading_value` 表底数/`diff_value` 本期用量/`source`= `MANUAL`/`AUTO`/`SUPPLEMENT`/`is_valid`/`validator`）+ `MeterReading` 实体 + `ReadingController`（`/api/energy/readings`：登记、`POST /{id}/supplement` 补录、`POST /{id}/validate` 校验、`GET /api/energy/readings/missing` 缺失数据） | 新建 `entity/MeterReading.java`、`service/ReadingService.java`、`controller/ReadingController.java`、迁移 | 补录保留 `source=SUPPLEMENT` 与原始值痕迹；缺失清单按仪表 + 日期区间给出；本期用量由相邻表底数差值算出 |
| 10 | 能源台账 | 日/月汇总与峰平谷：新建 `energy_daily_ledger` 表（`workshop_code`/`equipment_code`/`stat_date`/`energy_type`/`peak_type`= `PEAK`/`FLAT`/`VALLEY`/`energy_value`/`price`/`cost`）+ `EnergyLedgerService`，支持 `POST /api/energy/ledgers/generate?date=` 从设备级**汇总生成**车间级（不再靠两张表人工分别录入） | 新建 `entity/DailyLedger.java`、`service/EnergyLedgerService.java`、`controller/LedgerController.java`、迁移 | 同一日期设备级汇总值与车间级台账一致（对账断言）；峰平谷三段可分别取到用量与电费 |
| 11 | 能源台账 | 订单/产品分摊：新建 `energy_allocation` 表（`prod_order_no`/`product_code`/`workshop_code`/`energy_type`/`allocated_value`/`allocation_basis`= `OUTPUT`/`RUN_HOURS`）+ `EnergyAllocationService`，按产量或运行工时把车间用能分摊到生产订单 | 新建 `entity/EnergyAllocation.java`、`service/EnergyAllocationService.java`、`controller/AllocationController.java`、迁移 | 一张生产订单可查分摊能耗与单位产品能耗；分摊基数变化时可重算且留痕 |
| 12 | 定额与目标 | 定额单据（第 1 类可流转单据）：新建 `energy_quota` 表（`quota_no`/`target_code`/`energy_type`/`quota_value`/`start_date`/`end_date`/`status`）+ `EnergyQuota` 实体 + `QuotaService` + `QuotaController`（`/api/energy/quotas`：新建、`POST /{id}/submit`、`/{id}/effective`、`/{id}/cancel`） | 新建 `entity/EnergyQuota.java`、`service/QuotaService.java`、`controller/QuotaController.java`、迁移 | 定额单据可走「草稿 → 提交 → 生效 → 作废」并留状态时间轴 |
| 13 | 定额与目标 | 基线与偏离率落地：`EnergyBaselineService` 按定额或历史均值算基线，在上报/汇总时回写 `baseline_value` 与 `deviation_rate =（实际 − 基线）/ 基线`，并按阈值置 `is_abnormal`；提供同比环比查询 | 新建 `service/EnergyBaselineService.java`；`entity/EquipmentUsage.java`、`query/` 新建统计查询 | 同一仪表连续上报后 `baseline_value`/`deviation_rate` 非空且可解释；同比环比可取到上期值 |
| 14 | 异常管理 | 阈值与告警（异常闭环 + 第 2 类可流转单据）：新建 `energy_alert_rule`（`rule_code`/`energy_type`/`workshop_code`/`threshold_type`= `UPPER`/`LOWER`/`DEVIATION`/`threshold_value`/`level`）与 `energy_alert`（`alert_no`/`alert_level`/`status`= `OPEN`/`ACKED`/`CLOSED`/`owner`/`acked_at`/`closed_at`）两表 + `AlertController`（`/api/energy/alerts`、`POST /{id}/ack`、`POST /{id}/close`）+ `EnergyAlertService.evaluate` | 新建 `entity/AlertRule.java`、`entity/EnergyAlert.java`、`service/EnergyAlertService.java`、`controller/AlertController.java`、迁移 | 超阈值上报自动生成 `OPEN` 告警；`ack` 与 `close` 各留时间戳与操作人；关闭必须填写处置说明 |
| 15 | 异常管理 | 节能措施追踪：告警上补 `measure_desc`/`measure_owner`/`measure_due_date`（或独立 `energy_saving_measure` 表），提供 `POST /api/energy/alerts/{id}/measures`；规则上要求告警关闭前必须有措施记录 | `entity/EnergyAlert.java`、`service/EnergyAlertService.java`、`controller/AlertController.java` | 无措施记录的告警不允许关闭；措施可追踪到责任人与期限 |
| 16 | 碳管理 | 折标与碳排：新建 `energy_emission_factor` 表（`energy_type`/`factor_value`（tCO2 每计量单位）/`standard_coal_factor`/`effective_from`）+ `CarbonService`：`GET /api/energy/carbon/summary?from=&to=`（折标煤 + 碳排）、`GET /api/energy/carbon/order/{prodOrderNo}`（订单碳足迹） | 新建 `entity/EmissionFactor.java`、`service/CarbonService.java`、`controller/CarbonController.java`、迁移 | 给定区间返回电/水/气/汽四类折标与碳排；订单碳足迹与 #11 的分摊结果一致 |
| 17 | 审批 | 接入审批内核：新建 `EnergyQuotaApprovalCallback`（实现 `com.mfg.workflow.callback.ApprovalCallback` 的 `supports`/`onApproved`/`onRejected`，`supports` 匹配能源定额的 `biz_type`），并在迁移中用 `WHERE NOT EXISTS` 形式注册 `wf_definition`（`def_code` 形如 `ENERGY_QUOTA_APPROVAL`，节点 `node_seq` 10/20/30、`approve_mode='SINGLE'`、`reject_action='BACK'`）；当前 13 个 `biz_type` 中无任何能源类型 | 新建 `source-apps/energy/src/main/java/com/mfg/energy/callback/EnergyQuotaApprovalCallback.java`（包名落位见「风险与前置」第 5 条）；迁移插入 `mfg_auth.wf_definition` / `wf_node` | 定额提交后统一审批中心出现待办；通过后单据状态回写 `APPROVED`，驳回回退到草稿 |
| 18 | 集成 | 事件发布与消费：新建 `integration/EnergyEventPublisher`（调 `BusinessEventService.publish(eventType, sourceSystem, targetSystem, aggregateType, aggregateId, payload)`）发布 `ENERGY.USAGE.REPORTED`、`ENERGY.QUOTA.EXCEEDED`、`ENERGY.ALERT.RAISED`；新建 `integration/EnergyEventConsumer` 消费 06 计划发布的 `MES.REPORT.CREATED`（取产出量作为分摊基数）与 `MES.WORK_ORDER.COMPLETED` | 新建 `integration/EnergyEventPublisher.java`、`integration/EnergyEventConsumer.java`；消费幂等靠 `mfg_ops.biz_inbox` 唯一键 `(event_id, target_system)` + 本地处理标记 | 超定额可自动发 `ENERGY.QUOTA.EXCEEDED`；消费 `MES.REPORT.CREATED` 后分摊基数更新且重复投递不重复累加 |
| 19 | 报表与权限 | 六个统计端点 + 权限激活：新建 `query/EnergyReportController`（`/api/energy/statistics/*`）提供 `/daily`（日/月用能）、`/unit-consumption-trend`（单位能耗趋势）、`/workshop-ranking`（车间排名）、`/over-quota`（超定额清单）、`/peak-valley`（峰平谷电费）、`/carbon`（碳排）；全部接口加 `@PreAuthorize`，其中分析类端点用已存在却无人读取的 `ENERGY:ANALYSIS:VIEW` | 新建 `query/EnergyReportController.java`、`query/EnergyQueryService.java`；两个既有 controller + 新 controller 补 `@PreAuthorize` | `DATA_ANALYST` 与 `ENERGY_ADMIN`（含 `ENERGY:ANALYSIS:VIEW`）可访问分析端点；`EQUIPMENT_SUPERVISOR`（仅 `ENERGY:USAGE:VIEW`）访问分析端点返回 403 |
| 20 | 工程化 | 建测试与迁移：新增 `source-apps/energy/src/test/`（现不存在）覆盖单位能耗计算、设备级查重、峰平谷汇总与对账、定额审批回写、碳排因子取数；全部新表走 `V39+` 迁移（命名 `V39__p4_energy_<topic>.sql`），**不改 `infra/db-init/`** | 新建 `source-apps/energy/src/test/java/com/mfg/energy/…`；迁移目录 `source-apps/bootstrap/src/main/resources/db/migration/`（现最高 `V38`） | 模块测试通过；`infra/db-init/05_business_systems_2.sql` 无改动；Flyway 从 `V38` 平滑升级 |

> 轮次划分：第 1 轮 = #1–#9（地基、能源基础、数据采集），第 2 轮 = #10–#20（台账、定额、异常、碳、审批、事件、报表、工程化）。#1–#5 是纯收口，可独立交付并先修掉两个明确缺陷（#3 的 500、#1 的大小写去重失效）。

## 完成标准

- [ ] ≥5 个本职模块具备实体 + 服务 + 接口 + 角色权限：能源基础、数据采集、能源台账、定额与目标、异常管理、碳管理（6 个，且每层目录非空）；
- [ ] 至少 3 类单据能从创建流转到关闭或作废：**能源定额**（草稿 → 提交 → 审批 → 生效 / 作废）、**能耗告警**（`OPEN` → `ACKED` → `CLOSED`）、**抄表记录**（登记 → 校验 / 补录并留痕，若按单据口径实现则计入，否则以定额与告警两类为准并在 STATUS 中说明）；
- [ ] 至少 1 个审批流程（能源定额）+ 1 个异常处理闭环（阈值 → 告警 → 确认 → 处置 → 关闭，含节能措施追踪）；
- [ ] 至少 2 个幂等业务事件：发出的（`ENERGY.*` ≥2 个）与消费的（消费 MES 报工事件更新分摊基数），重复投递不重复累加；
- [ ] 至少 1 页本系统运营查询或统计报表：`/api/energy/statistics/*` 六类端点全部可用，并在 [08](08-frontend-reports.md) 中有对应页面可点；
- [ ] `energy_md_equipment` 不再空转：设备编码可校验、可带出车间（依赖 [01 缺陷修复](01-bugfix.md) 修 `writeToEnergy()`）；
- [ ] 两个既有台账表的**全量 `findAll()` 消失**，改为分页 + 筛选；设备级重复上报返回业务错误而非 500；
- [ ] `baseline_value`/`deviation_rate`/`is_abnormal` 三列被实体映射且有写入路径；
- [ ] `ENERGY:ANALYSIS:VIEW` 被至少一个端点读取（该权限码现已被授予角色却无接口）；
- [ ] 两个既有 Controller 不再各自重复实现单位能耗计算（收敛到 `EnergyUsageService`）；
- [ ] 能源全部接口带 `@PreAuthorize`；
- [ ] [energy/STATUS.md](../../source-apps/energy/STATUS.md) 五项验收对照全部由「未达标」改为「达标」，[../current-status.md](../current-status.md) 的矩阵与总览同步更新。

## 风险与前置

1. **设备主数据依赖 MDM 空实现修复**：`MasterDataDistributor.writeToEnergy()` 位于 `source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` 第 398 行，方法体为 `return 0;`，Javadoc 写「能源：本类型暂无分发目标」。同类的 `writeToMes`/`writeToWms`/`writeToQms`/`writeToEam` 都是可参照的实现（其中 `writeRoutingOperations(Routing, String)` 第 329 行是唯一的「先删后插」多表写入模板，最接近能源设备副本的形态）。但**该文件内没有任何设备分支，也不读 `md_equipment`**——能源的 `energy_md_equipment` 比物料副本多出 `workshop_code` 与 `capacity_per_hour` 两列，需要一个新写手。`src_mdm` 中设备主数据表的实际表名、以及设备是否为 `MasterDataEntity` 的子类型，**均待确认**。此事归 [01 缺陷修复](01-bugfix.md)（缺陷 #3）与 [02 MDM + PLM 收口](02-mdm-plm.md)，能源侧的 #8 只能消费其结果，不能自行写入副本表（会违反「业务系统不维护 MDM 主数据」的约束）。
2. **审批回调包名待定**：catalog 的代码标准要求 `workflow/` 层，而全仓 5 个既有 `ApprovalCallback` 实现都在 `<module>/src/main/java/com/mfg/<module>/callback/` 包（[../current-status.md](../current-status.md) 缺陷 #9 已记录此偏差）。#17 需与 [06 MES](06-mes.md) 的第 6 条风险取同一约定，避免能源与 MES 各选一种放法。
3. **事件消费侧无先例**：`mfg_ops.biz_inbox` 只由 `BusinessEventService.dispatch()` 的 `INSERT IGNORE` 写入，全仓没有任何模块读它。能源的消费端（#18）与 MES 的消费端是同一类问题，建议 [00 横向地基](00-foundation.md) 先定模板再各自落地；另外 #18 依赖 [06](06-mes.md) 先发布 `MES.REPORT.CREATED`，两者存在跨计划时序依赖——若 06 未完成，能源的分摊基数只能先取 `mes_work_report` 副本或手工录入（**待确认**是否允许直读 MES 库表）。
4. **建表通道**：能源两张台账表由 `infra/db-init/05_business_systems_2.sql` 创建（V1–V11 通道，已 baseline），本计划所有新表与新列一律走 `V39+`，不得回改 `infra/db-init/`。
5. **状态语义的自我校正**：STATUS 指出「两张台账表是只进不改的事实表，这本身合理（台账不应有审批流）」。本计划据此**不给台账加状态**，而是把「需要流转的东西」新建为独立单据（定额 `energy_quota`、告警 `energy_alert`）。若评审认为台账也需状态，则完成标准第 2 条的口径要重议。
6. **单位与介质口径**：`energy_type` 与 `unit_code` 目前靠 DDL 注释约定（`ELECTRIC`/`GAS`/`WATER`/`STEAM`，默认 `KWH`），代码无约束。引入 #1 的枚举后，历史数据中已存在的非规范写法（如小写）需要一次数据订正迁移——**存量数据是否需要订正待确认**（演示库可通过重置规避）。
7. **碳排因子的口径**：折标系数与排放因子的取值来源（国标 / 电网基准线 / 演示固定值）未定，需在 #16 开工前确认；若仅作演示，应在文档与页面上明确标注为演示值而非合规口径。
8. **汇总方向**：现状是设备级与车间级**人工分别录入**两张表，存在对不上的风险。#10 改为由设备级汇总生成车间级后，需要一次存量数据对账；若历史数据无法对上，需在 [01](01-bugfix.md) 或本计划内决定是重建还是标注例外。
9. **轮数**：本计划 20 个任务、5 个从零建模块，含 2 类可流转单据与 1 条审批链，正常需 2–3 轮；若第 1 条的 MDM 写手与第 3 条的消费端模板都未就绪，#8 与 #18 会被阻塞，届时先交付 #1–#5、#6–#7、#9–#10（不依赖前置的部分）以保住进度。
