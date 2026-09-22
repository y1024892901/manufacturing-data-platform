# 05. SRM + EAM 收口

> **依赖**：[00 横向地基](00-foundation.md)（鉴权 / 事件消费 / 终态动作 / 实体同步四套模板） · **预估**：1–2 轮 · **对应验收项**：SRM ①②③⑤（④已达标）· EAM ①②③④⑤
>
> 现状依据：[srm/STATUS.md](../../source-apps/srm/STATUS.md)、[eam/STATUS.md](../../source-apps/eam/STATUS.md)；验收定义见 [system-functional-catalog.md](../system-functional-catalog.md)；计划分工见 [README.md](README.md)。
> 下表「涉及文件 / 位置」中，Java 路径的包根是 `source-apps/<系统>/src/main/java/com/mfg/<系统>/`，行内 `srm/service/…`、`eam/domain/…` 这类简写均指该根下的相对位置；SQL 一律指 `source-apps/bootstrap/src/main/resources/db/migration/`。

## 目标

做完后，SRM 不再是「四条线能跑、其余只有表」：**对账协同从无到有建成**，绩效算分、8D 闭环、订单确认与变更、资质管理从「有表无逻辑」变成可用模块，准入审批接入审批引擎，采购订单可走到关闭或作废——五项验收全达标。

EAM 补齐缺失的 `domain/`、`service/`、`query/`、`workflow/`、`integration/` 五层，设备状态切换落台账，重大故障可走审批，故障停机可发事件影响下游交期，点检漏检、备件协同、可靠性指标与停机报表落地——五项验收全达标。

> EAM 的④（幂等事件）目前**完全未达标**（EAM 不发任何事件、只被动接收 MDM 分发），是本计划里工作量最大的一项；两个系统的界面工作归 [08 前端与运营报表](08-frontend-reports.md)。

## 现状

**SRM（★★☆☆☆，`../current-status.md` 验收矩阵：①部分 ②部分 ③部分 ④✓ ⑤✗）**

供应商准入、询报价定标、采购订单下达、ASN 到货四条线能跑通真实表并发出 2 个幂等事件（`SRM.PURCHASE_ORDER.SENT`、`WMS.RECEIPT.PENDING_INSPECTION`）。分层只有 `entity/` 2 + `repo/` 2 + `controller/` 2 共 7 个 Java 文件，`domain/`、`service/`、`workflow/`、`integration/`、`query/` 五层全空——业务编排借住在共享的 `P3CrudService` / `P3FlowService` 里。

与最低验收的差距（逐条证据见 [srm/STATUS.md](../../source-apps/srm/STATUS.md#对照最低验收)）：

- **① 部分**：6 个 `kind` 有接口有表，但实体只有 2 个、`service/` 为空，业务规则集中在共享 `P3CrudService.rules()`（一个 switch）与 `cols()`（手写列表）里——**改 SRM 状态机要动共享模块**（该注册表同时挂着 WMS 的 6 个 kind 与 QMS 的 8 个 kind）。权限方面：`08_seed_data.sql` 定义 6 个 `SRM:*` 码，`V37__p3_qms_8d_permissions.sql` 再补 2 个（`SRM:RFQ:AWARD`、`SRM:ASN:CREATE`），**合计 8 个**均由 `09_role_permission.sql` 授给四个采购岗位，但**本模块 0 处 `@PreAuthorize`**。
- **② 部分**：询价单、ASN、准入单可经 `POST /api/srm/{kind}/{id}/status` 走到终态；但**采购订单只有 `CREATED → SENT`**，`/send` 之后没有 `CLOSED`/`CANCELED` 接口；报价单与绩效单无状态字段，到货单无状态流转。
- **③ 未达标**：审批**完全未实现**——`srm_onboarding.workflow_instance_id` 列预留但无代码写入，全仓无一 SRM 的 `ApprovalCallback`。异常闭环只有入口：`P3FlowService.judgeInspection()` 在 IQC 不合格时自动生成 `srm_supplier_quality` 记录（含 PPM），但 8D 整改 / 验证 / 关闭（`eight_d_status`、`due_date`、`closed_at`）无接口。
- **⑤ 未实现**：只有两个分页列表，无 `stats` / 汇总 / 报表接口，`query/` 层不存在；DDL 为绩效预置的 `score`/`rating`/`ppm` 列没有任何算分逻辑去填。
- **模块级缺口**：**对账协同无表、无实体、无接口**；`srm_qualification` 建了表却未注册进 `P3CrudService`，无任何读写入口；退供、供应商分级与年度复审、比价视图与合同价格均未实现。
- **实体落后**：`V31` 给 `srm_purchase_order` 加的 `source_requisition_no`/`confirmed_at`/`change_reason` 与给 `srm_delivery` 加的 `asn_no`/`receipt_no` **均未映射进实体**，JPA 看不到；其中 `confirmed_at`/`change_reason`/`asn_no`/`receipt_no` 目前**没有任何代码写入**。

**EAM（★★☆☆☆，验收矩阵：①✗ ②部分 ③部分 ④✗ ⑤✗）**

已跑通「设备建档 → 故障上报 → 维修完工 → 设备恢复」最小闭环（12 个接口、4 张业务表、11 个 Java 文件）。

与最低验收的差距（逐条证据见 [eam/STATUS.md](../../source-apps/eam/STATUS.md#对照最低验收)）：

- **① 未达标**：**只用了 entity/repo/controller 三层**，`service/domain/workflow/integration/query` 五层全空——查重、状态联动、停机时长计算全内联在 Controller 里，**无 `@Transactional` 边界**。种子里的 6 个 `EAM:*` 权限码（`EAM:EQUIPMENT:VIEW`、`EAM:FAULT:CREATE`、`EAM:FAULT:APPROVE`、`EAM:REPAIR:FINISH`、`EAM:INSPECTION:CREATE`、`EAM:INSPECTION:PLAN`）**未被任何接口读取**；全库 `wf_definition` 的 `biz_type` 中没有 EAM。目录的 6 个模块中 4 个部分实现、2 个（备件协同、可靠性）未实现。
- **② 部分**：故障单 `OPEN → CLOSED`、维修单 `REPAIRING → REPAIRED/PENDING_PARTS/SCRAPPED`、点检单 `null → NORMAL/ABNORMAL` 三条链完整；但设备本身**只有状态切换、无建档 → 报废的作废语义**，且无任何作废接口。
- **③ 半达标**：异常闭环有（故障 → 维修 → 完工 → 设备恢复），**审批无**——`EAM:FAULT:APPROVE` 已种下但无流程可用。
- **④ 未达标**：EAM **不发事件**、不写 `mfg_ops.biz_outbox`。作为接收方，MDM 用 `INSERT … ON DUPLICATE KEY UPDATE` 写 `src_eam.eam_md_material` 是幂等的，但属被动接收且仅 1 条链路。
- **⑤ 未达标**：3 个列表接口都是无条件的 `findAll(PageRequest)`，无按状态 / 车间 / 时间的过滤，无聚合统计接口。
- **台账与字段**：`src_eam.eam_equipment_status_log` **有 DDL 无实体无接口**，状态切换不落台账，导致「设备可用率」与状态时段重叠检测都缺数据源；`eam_equipment` 的 `purchase_date`/`purchase_price`/`warranty_end_date`/`maintenance_cycle_days`/`last_maintenance_date` 五列、`eam_fault.fault_cause`、`eam_inspection.check_items`(JSON) 均存在于 DDL 而实体未映射。`is_missed` 恒为 `false`。
- **性能**：`EquipmentRepairController.create` 用 `faults.findAll().stream().filter(...)` 全表扫描找故障单。

> 说明三处已修复或待 01 处理的缺陷（2026-09-22 核实）：
> ① 缺陷 #4（`supplier-quality` 状态接口按 `status` 列判定，实际列名是 `eight_d_status`）**已在工作区修复但尚未提交**——`P3CrudService.sc()` 现含 `case"supplier-quality"->"eight_d_status"`，该状态接口已可用；本计划任务 3 在其之上做完整 8D 推进。
> ② 缺陷 #8（`eam_equipment_status_log` 有 DDL 无实体无接口）仍属 [01 缺陷修复](01-bugfix.md) 范围，本计划任务 15 在其之上做台账与可用率能力，不重复修。
> ③ 缺陷 #13（`SrmP3Controller.java` 未被 git 跟踪）经核实**已修复**——该文件已被跟踪且 `source-apps/srm/` 工作区干净，本计划无需再处理。

## 任务拆解

> 表内顺序即优先级；每行可独立完成、独立验证。SRM 的 1–12 与 EAM 的 13–25 可并行，26–28 为两系统共用。

| # | 系统 | 任务 | 涉及文件 / 位置 | 验收方式 |
|---|---|---|---|---|
| 1 | SRM | 建 `domain/` 层，把状态机从共享 `P3CrudService.rules()` 收回本模块：状态枚举 + 合法迁移白名单（准入、询价、报价、ASN、采购订单、到货检验、8D 七组） | 新建 `source-apps/srm/src/main/java/com/mfg/srm/domain/`：`OnboardingStatus`、`RfqStatus`、`QuoteStatus`、`AsnStatus`、`EightDStatus`、`PurchaseOrderStatus` | 迁移白名单与 `srm/STATUS.md` 记录的实测状态机逐条一致；非法迁移抛 `MASTER_DATA_INVALID_STATE` |
| 2 | SRM | 建 `service/` 层，把 SRM 的跨单据编排从共享 `P3FlowService` 收回：`awardRfq()`、`arriveAsn()` 迁入并补 `@Transactional` 边界 | 新建 `srm/service/RfqAwardService.java`、`srm/service/AsnArrivalService.java`；当前实现见 `source-apps/shared/common/…/service/P3FlowService.java` | 定标与到货两条流程的落库结果与迁移前逐字段一致（表行对比），且任一步失败时整体回滚 |
| 3 | SRM | 8D 闭环：`eight_d_status` 从 `OPEN` 逐级推进到 `D8`/`CLOSED`，写入 `due_date`、`closed_at`，关闭时发事件（缺陷 #4 已使该状态接口可用，本任务补齐推进规则与终态） | `POST /api/srm/supplier-quality/{id}/advance`；`srm/service/`；表 `src_srm.srm_supplier_quality` | 状态可推进到 `CLOSED` 且 `closed_at` 非空；非法跳级（如 `OPEN → D8`）被拒 |
| 4 | SRM | 采购订单协同补全：确认、交付承诺、变更、关闭、取消五个动作，并让 `V31` 的列真正被写入 | `POST /api/srm/purchase-orders/{id}/confirm`、`/promise`、`/change`、`/close`、`/cancel`；`srm/entity/PurchaseOrder.java`、`srm/entity/SupplierDelivery.java`（补 3 + 2 列） | `srm_purchase_order.order_status` 走 `CREATED → SENT → PARTIAL → CLOSED`/`CANCELED`；`confirmed_at`、`change_reason`、`promised_date` 均被写入 |
| 5 | SRM | **对账协同（全新模块）**：新建对账单与对账明细表，按供应商 + 期间汇总采购订单 / 到货 / 退供数据，确认后与应付核对 | V39+ 迁移新建 `src_srm.srm_reconciliation`、`srm_reconciliation_line`；`POST /api/srm/reconciliations`、`GET /api/srm/reconciliations`、`POST /{id}/confirm`、`POST /{id}/settle` | 对账单金额 = 期间采购订单已收金额合计；`confirm` 幂等；接口对应表不再「无表无接口」 |
| 6 | SRM | 供应商绩效算分：从 `srm_delivery.delay_days`、`srm_supplier_quality.ppm` 汇总 `delivery_rate`/`qualified_rate`/`score`/`rating`，按 `(supplier_code, period_code)` 落地 | `srm/service/SupplierPerformanceService.java`；`POST /api/srm/performances/recalculate?period=YYYY-MM`；表 `srm_supplier_performance`（已有 `UNIQUE KEY uk_supplier_period`） | 同一期间重复算分结果一致（唯一键生效）；`score`/`rating` 不再恒为 NULL |
| 7 | SRM | 资质管理：把 `srm_qualification` 接入接口层，并补到期预警 | 注册进 `P3CrudService` 注册表 + `SrmP3Controller` 的 `kind` 白名单，或写强类型接口；`GET /api/srm/qualifications/expiring?days=30`；表 `src_srm.srm_qualification` | 资质可增删查；预警返回 `expire_date` 在 30 天内的证书 |
| 8 | SRM | 退供动作：到货不合格时由 SRM 侧发起退供，与 WMS 的 `SUPPLIER_RETURN` 库存动作对齐 | `POST /api/srm/deliveries/{id}/return`；`src_srm.srm_delivery` | 退供后 `srm_delivery` 有留痕，且 WMS 侧可查到对应退货库存动作 |
| 9 | SRM | 接入审批引擎：供应商准入走 `ApprovalEngine.start(StartApprovalRequest.of(bizType, bizId, bizNo, bizTitle))`，`srm_onboarding.workflow_instance_id` 落值 | 新增 `wf_definition`/`wf_node` 行（biz_type `SUPPLIER_ONBOARDING`，两级：采购主管 → 质量）随 V39+ 种子——该表由 SQL 声明，无 Java 配置；新建 `srm/workflow/SrmApprovalCallback.java` 实现 `com.mfg.workflow.callback.ApprovalCallback`（`supports`/`onApproved`/`onRejected`） | 准入单 `DRAFT → APPROVING → APPROVED`/`REJECTED`，且 `workflow_instance_id` 非空；`supports("SUPPLIER_ONBOARDING")` 被引擎命中；回调落点符合 8 层约定的 `workflow/`（现有 5 个回调实现的写法可照抄） |
| 10 | SRM | 权限落地：两个 Controller 补 `@PreAuthorize`，使用已入库的 8 个 `SRM:*` 码（`08_seed_data.sql` 6 个 + `V37` 的 `SRM:RFQ:AWARD`、`SRM:ASN:CREATE`）及 `09_role_permission.sql` 授予的 `BUYER`/`SQE`/`PURCHASE_SUPERVISOR`/`PURCHASE_DIRECTOR`；对账、订单关闭等新动作缺的码随 V39+ 迁移补种 | `srm/controller/PurchaseOrderController.java`、`srm/controller/SrmP3Controller.java` | `grep -c @PreAuthorize srm/` ≥ 端点数；仅持 `SYSTEM_SRM` 的账号调用定标 / 对账确认返回 403 |
| 11 | SRM | 运营报表：准时交付率、来料合格率、PPM、采购金额趋势、对账进度 | `GET /api/srm/stats/supplier-performance`、`GET /api/srm/stats/delivery`；新建 `srm/query/` 层 | ⑤达标：报表数字与台账 SQL 手工核算一致 |
| 12 | SRM | 工程收敛：`P3CrudService.detail()` 主键不存在时改抛 `BizException.notFound`（现抛 Spring 原生异常）；`POST /api/srm/deliveries` 的 `findAll().stream().filter()` 改按订单号查询；`create()` 反复查 `information_schema` 改为缓存 | `srm/controller/PurchaseOrderController.java`、`srm/repo/`、共享 `P3CrudService` | 详情查不到时返回统一错误码；建到货单不再全表加载采购订单 |
| 13 | EAM | 建 `domain/` 层：设备状态、故障状态、点检结果、维修结论四组枚举 + 合法迁移校验（例如禁止 `SCRAPPED` 直接改回 `RUNNING`） | 新建 `source-apps/eam/src/main/java/com/mfg/eam/domain/`：`EquipmentStatus`、`FaultStatus`、`InspectionResult`、`RepairResult` | 非法迁移被拒；`POST /api/eam/equipments/{id}/status` 的合法取值集合不再是 Controller 内联 `Set.of(...)` |
| 14 | EAM | 建 `service/` 层：把建档查重、状态联动、停机时长计算从 Controller 迁入服务类并加 `@Transactional`，修掉「改维修单 + 关故障单 + 改设备状态」三步不同事务的问题 | 新建 `eam/service/EquipmentService.java`、`EquipmentFaultService.java`、`EquipmentInspectionService.java`、`EquipmentRepairService.java` | 让第三步故意失败时前两步回滚（无脏数据）；Controller 只剩参数绑定与响应包装 |
| 15 | EAM | 设备状态台账：`eam_equipment_status_log` 建实体，每次状态切换写入一条时段记录（`status_code`/`start_time`/`end_time`/`duration_minutes`/`fault_no`/`work_order_no`），并提供查询 | `eam/entity/EquipmentStatusLog.java` + `repo`；`GET /api/eam/equipments/{code}/status-log`；表 `src_eam.eam_equipment_status_log` | 每次状态切换后台账行数 +1 且上一行 `end_time`/`duration_minutes` 被闭合；台账时长可用于可用率计算 |
| 16 | EAM | 接入审批引擎：重大故障（按 `fault_level`）须审批通过才可建维修单，用已种下的权限点 `EAM:FAULT:APPROVE`（全库 `wf_definition` 的 `biz_type` 现无 EAM，本次为首次接入） | 新增 `wf_definition`/`wf_node` 行（biz_type `EAM_MAJOR_FAULT`）随 V39+ 种子；新建 `eam/workflow/EamApprovalCallback.java` 实现 `com.mfg.workflow.callback.ApprovalCallback`；`POST /api/eam/faults/{id}/submit-approval` | 重大故障未审批时 `POST /api/eam/repairs` 被拒；审批通过后放行，回调写入故障单 |
| 17 | EAM | 出向事件（本系统④的唯一来源）：故障上报时发 `EAM.EQUIPMENT.FAULT_REPORTED`（携带 `prod_order_no`/`operation_code`/`work_order_no`/停机时长），设备恢复时发 `EAM.EQUIPMENT.RESTORED` | 新建 `eam/integration/` 包，调用 `BusinessEventService.publish()`；事件表 `mfg_ops.biz_outbox` | 两个事件各落一行 `biz_outbox`；重放不产生重复业务效果（沿用 `(event_id, target_system)` 唯一键） |
| 18 | EAM | 停机管理：独立的停机开始 / 恢复动作，记录停机原因、影响设备 / 工序 / 订单、损失工时，与 `eam_repair.downtime_minutes` 口径统一 | `POST /api/eam/downtime/start`、`POST /api/eam/downtime/{id}/recover`；与 `eam_equipment_status_log` 共用时段口径 | 停机时长与维修单上的 `downtime_minutes` 一致；停机期间设备状态为 `FAULT`/`MAINTENANCE` |
| 19 | EAM | 点检保养计划与漏检：按周期生成点检任务单，超期未完成的置 `is_missed=true` | `POST /api/eam/inspection-plans`、`POST /api/eam/inspections/{id}/miss` + 超期扫描；`eam_inspection.is_missed` | `is_missed` 不再是恒为 `false`；超期用例（`plan_date` 早于今天且未完成）置位正确 |
| 20 | EAM | 备件协同：新建备件领用 / 归还台账，支持最低库存预警，并回填维修单 | V39+ 迁移新建 `src_eam.eam_spare_part_issue`；`POST /api/eam/spare-parts/issue`、`POST /api/eam/spare-parts/{id}/return`、`GET /api/eam/spare-parts/low-stock`；关联只读副本 `src_eam.eam_md_material` | 领用写入台账并回填 `eam_repair.replaced_parts`/`repair_cost`；低于最低库存的备件出现在预警列表 |
| 21 | EAM | 可靠性指标：按设备聚合 MTBF / MTTR 与维修履历 | `GET /api/eam/equipments/{code}/reliability`；数据源 `eam_fault`、`eam_repair` | 指标可由故障 / 维修台账手工复算；无故障设备返回明确空值而非 500 |
| 22 | EAM | 实体补列：`Equipment` 补购买日期 / 价格 / 保修截止 / 保养周期 / 上次保养日期五列，`EquipmentFault` 补 `fault_cause`，`EquipmentInspection` 补 `check_items`(JSON) | `eam/entity/Equipment.java`、`EquipmentFault.java`、`EquipmentInspection.java` | 接口可读写这些列（现状为「DDL 有、实体无」） |
| 23 | EAM | 查询能力：三个列表接口补按状态 / 车间 / 时间的条件过滤，建立 `eam/query/` 层；`EquipmentRepairController.create` 的全表扫描改按故障单号查询 | `eam/query/`、`eam/repo/EquipmentFaultRepository.java` | 列表支持条件过滤；建维修单不再 `findAll().stream().filter(...)` |
| 24 | EAM | 权限落地：12 个端点补 `@PreAuthorize`，使用种子里的 6 个 `EAM:*` 权限码；新增动作（停机 / 备件 / 报表）的码随 V39+ 补 | `eam/controller/EquipmentController.java`、`EquipmentRepairController.java` 及新增 Controller | `grep -c @PreAuthorize eam/` ≥ 端点数；6 个 `EAM:*` 码全部有接口引用（现状为 0 引用） |
| 25 | EAM | 运营报表：按车间 / 设备聚合停机时长、设备可用率、故障 TOP N、MTTR | `GET /api/eam/stats/downtime`；`eam/query/` | ⑤达标：报表数字与 `eam_equipment_status_log`、`eam_repair` 手工核算一致 |
| 26 | 两系统 | 迁移通道纪律：EAM 的表由 `infra/db-init/05_business_systems_2.sql` 创建（V1–V11 通道），本计划**新增列 / 新增表一律走 V39+ 迁移**，不改 `db-init` | `source-apps/bootstrap/src/main/resources/db/migration/V39+__p5_srm_eam_closure.sql` | 迁移在仅跑过 Flyway 的空库与已有库上均可执行；`infra/db-init/` 无改动 |
| 27 | 两系统 | 为新写的状态机与算分逻辑补单元测试（`source-apps/srm/src/test/`、`source-apps/eam/src/test/` 目前都不存在） | `srm/src/test/`、`eam/src/test/` | `mvn -pl srm,eam test` 通过；覆盖非法迁移、绩效幂等、MTBF/MTTR 边界 |
| 28 | 两系统 | 端到端验收脚本：仿 `ops/demo_bom_approval.py`（纯标准库、可重复运行、真实 HTTP 打 `localhost:8080`） | 新建 `ops/demo_srm_eam_closure.py` | 脚本断言：SRM 对账 / 8D / 订单终态、EAM 台账 / 事件 / 报表；结尾 `sys.exit(非0)` 以便 CI 判定 |

## 完成标准

**SRM**

- [ ] ① 任务 1、2、5、6、7 完成：六个本职模块（供应商生命周期、寻源与询报价、采购订单协同、供应商绩效、质量协同、对账协同）均有实体、服务、接口；任务 10 完成，`SRM:*` 码落到接口上。
- [ ] ② 任务 4、5 完成：采购订单、询价单、准入单、对账单中**至少 3 类**可创建并流转到 `CLOSED` 或 `CANCELED`。
- [ ] ③ 任务 9、3 完成：1 个审批流程（供应商准入）+ 1 个异常闭环（IQC 不合格 → 8D 推进到 `CLOSED`）。
- [ ] ④ 保持已达标：`SRM.PURCHASE_ORDER.SENT`、`WMS.RECEIPT.PENDING_INSPECTION` 不回归，新增 `SRM.QUALITY.8D_CLOSED`、`SRM.RECONCILIATION.CONFIRMED`。
- [ ] ⑤ 任务 11 完成：至少 1 页运营报表可查。
- [ ] 任务 4 完成：`V31` 的 5 个列在实体中可读写，`confirmed_at`/`change_reason`/`asn_no`/`receipt_no` 不再是「无代码写入」的死列。
- [ ] 任务 12 完成：详情 404 走统一错误码，到货建单不再全表扫描。

**EAM**

- [ ] ① 任务 13、14、23 完成：8 层目录齐备；6 个本职模块均有实体 + 服务 + 接口；任务 24 完成，6 个 `EAM:*` 码全部生效。
- [ ] ② 任务 15、18 完成：故障单、维修单、点检单、设备报废**至少 3 类**可到终态，且设备具备建档 → 报废的作废语义。
- [ ] ③ 任务 16 完成：1 个审批流程（重大故障）+ 1 个异常闭环（故障 → 维修 → 完工 → 设备恢复）均可用。
- [ ] ④ 任务 17 完成：至少 2 个幂等事件落 `biz_outbox`（`EAM.EQUIPMENT.FAULT_REPORTED`、`EAM.EQUIPMENT.RESTORED`）。
- [ ] ⑤ 任务 25 完成：至少 1 页停机 / 可用率报表可查。
- [ ] 任务 15 完成：每次状态切换都落 `eam_equipment_status_log`，「可用率」有数据源。
- [ ] 任务 14 完成：维修完工的三步写库在同一事务内。

**计划级**

- [ ] 任务 26 完成：迁移不触碰 `infra/db-init/`，在空库与已有库上均可重复执行。
- [ ] 任务 27 完成：两模块自建的单元测试通过（当前两模块均无 `src/test`）。
- [ ] 任务 28 完成：验收脚本连跑两次结果一致。
- [ ] `source-apps/srm/STATUS.md`、`source-apps/eam/STATUS.md`、`../current-status.md` 的完成度与验收矩阵按交付约定同步更新。

## 风险与前置

1. **前置：00 横向地基的四件模板必须先落地。** 任务 10 / 24（鉴权）、17（事件消费）、4 / 3（终态动作）、22（实体同步）都是模板的应用；若 00 未定稿，建议本计划只先做 EAM 的 `domain/` + `service/`（任务 13、14）这类不依赖模板的结构性补齐。
2. **前置：01 缺陷修复——两条中已有一条就绪。** 状态核实（2026-09-22）：缺陷 #4 已**在工作区改好但尚未提交**（`P3CrudService.sc()` 已含 `supplier-quality -> eight_d_status`），任务 3 可直接在其上做闭环；缺陷 #8（`eam_equipment_status_log` 有 DDL 无实体无接口）仍待 01 处理，任务 15 在其之上做台账与可用率能力，两者边界不要互相覆盖。缺陷 #13 亦已修复（见上）。三条均不再是阻塞项，但都需在 01 正式提交后复验。
3. **与共享 `P3CrudService` 解耦的回归面（最大结构性风险）。** 该类是 SRM / WMS / QMS 共用的泛型 CRUD 与状态机注册表，`rules()` 与 `cols()` 是手写映射。已核实其 `T` 注册表挂了 **20 个 kind**：SRM 7 个（`onboarding`/`rfqs`/`quotes`/`purchase-orders`/`asns`/`supplier-quality`/`performance`）、WMS 6 个（`receipts`/`putaway`/`inventories`/`inventory-actions`/`transfers`/`counts`）、QMS 7 个（`standards`/`sampling-plans`/`inspections`/`ncrs`/`reworks`/`capas`/`8d`）。因此任务 1 必须采用**委托而非搬迁**：共享类保留兼容注册表，SRM 侧新增 `domain/` 并以之替换 SRM 的调用点；直接搬走规则会同时影响 WMS 与 QMS。
4. **SRM 的跨库直写不应扩大。** `P3FlowService.arriveAsn()` 目前**直接跨库写** `src_wms.wms_receipt` 与 `src_qms.qms_inspection`，这是「事件只发不收」的具体表现。任务 2 收回编排时应保持行为等价，**不要新增直写面**；是否改为事件驱动取决于 00 的消费者模板进度。
5. **与 04 的唯一强耦合点：对账口径。** 任务 5 的对账单要与 ERP 应付对齐（`erp_payable.source_order_no` ↔ `srm_purchase_order.purchase_order_no`），而 04 的任务 6 正在新建 `erp_payment` 付款台账。两个计划的落地顺序需约定：**先 04 建好应付侧，再 05 做对账核销**，否则对账只能对到单边。
6. **Flyway 版本号争用。** 02/03/04/06/07 与本计划都从 V39 起新开迁移，并行提交会撞号；建议在 00 中先划段，落地前确认当前最大版本。
7. **EAM 的两套建表通道。** `src_eam` 的表由 `infra/db-init/05` 创建（V1–V11 通道，Flyway 基线为 11），本计划新增列 / 表走 V39+。空库重建时需保证「`db-init` 先建表、Flyway 再 ALTER」的顺序；若某环境只跑 Flyway，迁移脚本需用 `CREATE TABLE IF NOT EXISTS` / 幂等 ALTER 风格自足。
8. **健康评分不在本计划范围。** `eam_equipment.health_score`/`health_level` 依赖数仓回写，而数仓（P6）尚未启动。任务 21 只算 MTBF/MTTR 与维修履历，健康评分留待 P6，不写入本计划的完成标准。
9. **报表与界面分离。** 两个系统的操作页面与报表页归 [08 前端与运营报表](08-frontend-reports.md)；本计划的验收一律以接口 + SQL 断言为准。
