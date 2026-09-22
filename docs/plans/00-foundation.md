# 00. 横向地基

> **依赖**：无 · **预估**：1–2 轮 · **对应验收项**：① ② ③ ④（⑤ 由 [08 前端与运营报表](08-frontend-reports.md) 覆盖）
>
> 现状依据 [../current-status.md](../current-status.md) 的「横向共性问题」一节；验收定义见 [../system-functional-catalog.md](../system-functional-catalog.md)；计划分工见 [README.md](README.md)。

## 目标

把**跨 10 个系统重复出现的四件同一件事**各立一套模板：接口级鉴权怎么标、事件怎么被消费、单据怎么走到终态、实体怎么跟上表结构。做完后，后续每个系统计划（02–07）不再各自解同一道题，而是照着模板填空；本计划**不新增任何业务功能**，只产出约定、骨架与校验脚本。

## 现状

据 [../current-status.md](../current-status.md)，四个问题是结构性而非个别疏漏：

| # | 问题 | 已验证的规模 |
|---|---|---|
| 1 | 接口级权限普遍缺失 | 十系统 **204 个端点**中仅 **32 个**有 `@PreAuthorize`（mdm 21 / plm 5 / wms 3 / qms 3），缺口 **172 个**；erp、crm、srm、mes、eam、energy **六系统为 0** |
| 2 | 事件只发不收 | `BusinessEventService`（`shared/common/…/integration/`）已把事件写入 `mfg_ops.biz_outbox` 并投递 `biz_inbox`，但**全仓库无任何消费者**——`biz_inbox` 全仓只被该文件自身引用 |
| 3 | 单据走不到终态 | 十系统中 **8 个**无法走到终态；「关闭/作废」是十系统里最普遍的缺口 |
| 4 | JPA 实体落后于表结构 | 迁移脚本加了列但实体未映射，接口查不到已建好的字段（各系统清单见「任务拆解·四」） |

鉴权一项还有**三个具体破口**（本次核对权限码字典后确认，与 [../current-status.md](../current-status.md) 记录的 3 个不同）：

| 类别 | 权限码 | 后果 |
|---|---|---|
| 被 `@PreAuthorize` 引用，但**任何 SQL 中都不存在** | `MDM:BOM:PUBLISH`、`MDM:MATERIAL:PUBLISH`、`MDM:PRODUCT:CREATE`、`MDM:PRODUCT:UPDATE`、`PLM:PRODUCT:UPDATE`、`PLM:DOCUMENT:UPDATE` | 6 个。`PLM:*` 两项尚有 `or hasRole('ADMIN')` 兜底，故 ADMIN 可用、**普通业务角色被误拒** |
| 已在 `V37`/`V38` 写入 `sys_permission`，但 `09_role_permission.sql` **未授予任何角色** | `QMS:CAPA:CLOSE`、`QMS:NCR:DISPOSE`、`WMS:COUNT:POST`、`WMS:INVENTORY:ACTION`、`WMS:TRANSFER:EXECUTE` | 5 个。字典有码、无人有权，退化为 `hasRole('ADMIN')` 或直接 403 |
| 被引用且已定义，但**只改了工作区、未提交** | `MDM:BOM:UPDATE` → 已改为 `MDM:BOM:CHANGE` | 见「风险与前置」第 1 条 |

## 任务拆解

> 四组可并行；每组内部按序号执行。表内「涉及文件 / 位置」以仓库根为起点。

### 一、鉴权铺开（F1）

> **前置决策**：权限码是「一码一动作」还是「复用同前缀粗码」。建议**一码一动作**——`MDM:BOM:CHANGE` 的语义是「BOM 变更申请」，用它守卫 `PUT /api/mdm/boms/{id}`（普通修改）属语义借用，会把「能改 BOM」和「能提变更」混为一谈。

| # | 任务 | 涉及文件 / 位置 | 验收方式 |
|---|---|---|---|
| F1-01 | 建立「权限码三方对照」核对脚本：列出所有 `@PreAuthorize` 引用的权限码，逐码标注「字典是否有 / 是否授予角色 / 被哪些端点引用」 | 新增 `ops/check_permissions.py`（或 `infra/` 下同名脚本） | 脚本输出三方对照表；当前应有 6 行「未定义」、5 行「未授予」 |
| F1-02 | 用**一个新 Flyway 迁移**补齐 6 个未定义权限码到 `mfg_auth.sys_permission`，同批补 `sys_role_permission` 授权行。**不改 `infra/db-init/`**（该通道截至 V11 已 baseline，见 [README.md](README.md) 交付约定）；写法照 `V37`/`V38` 的 `INSERT IGNORE` + `09_role_permission.sql` 的 `JOIN` 授权形态 | 新增 `source-apps/bootstrap/src/main/resources/db/migration/V39__…sql` | 以 `PROCESS_ENGINEER` 等非 ADMIN 角色调 `POST /api/plm/catalog/{type}` 返回 200（当前仅 ADMIN 可过）；`SELECT` 复查 6 码在两张表均有行 |
| F1-03 | 为 `V37`/`V38` 已定义但未授予的 5 个权限码补授权行（`QMS:CAPA:CLOSE`、`QMS:NCR:DISPOSE`、`WMS:COUNT:POST`、`WMS:INVENTORY:ACTION`、`WMS:TRANSFER:EXECUTE`） | 同 F1-02 的迁移 | 以 QMS/WMS 业务角色（非 ADMIN）调对应端点返回 200；`QMS:8D:CLOSE` 在 `V37` 中缺失，一并补字典 |
| F1-04 | 固化权限码命名规范：`<系统码>:<对象>:<动作>`，动作取 `VIEW/CREATE/UPDATE/DELETE/SUBMIT/APPROVE/CLOSE/VOID/PUBLISH/EXECUTE` 等受控词表 | 写入 `docs/` 下鉴权约定小节（或 `shared/security/PLAN.md`） | 规范文档成文；F1-01 脚本可校验新码是否符合规范（非法命名报错） |
| F1-05 | 鉴权基线校验脚本：断言**每个端点**要么有方法级注解、要么在显式白名单中（登录、`/api/health`、Swagger 等） | 同 F1-01 脚本 | 脚本报告「无注解且不在白名单」的端点数；本组全部完成后应归零 |
| F1-06 | MDM 剩余无鉴权端点补注解：`MdmGovernanceController`（**整类零注解，含改写状态的 `POST /api/mdm/merge`**）、`PartnerMasterController`、`DistributionController` 的 `events`/`{eventId}`/`retry`/`reconcile` | `source-apps/mdm/src/main/java/com/mfg/mdm/controller/` | 无权限账号调 `POST /api/mdm/merge` 返回 403；有权限账号仍 200 |
| F1-07 | CRM 全量补注解（25 个端点，当前 0 个） | `source-apps/crm/src/main/java/com/mfg/crm/controller/` | 以 `SYSTEM_CRM` 但无细粒度权限的账号调合同生效/生成 ERP 订单返回 403 |
| F1-08 | ERP 全量补注解（32 个端点，当前 0 个）；激活已种子化却无人读取的 14 个 `ERP:*` 权限码 | `source-apps/erp/src/main/java/com/mfg/erp/controller/` | ERP 业务角色可正常操作；越权账号 403；`ERP:*` 码不再「只是数据」 |
| F1-09 | SRM 与 WMS 剩余端点补注解（SRM 12 个端点 0 注解；WMS 现有 3 个，补 `POST /api/wms/locations`、`POST /api/wms/transactions` 等） | `source-apps/srm/…/controller/`、`source-apps/wms/…/controller/` | 逐端点 403 验证；WMS 的 `WMS:STOCK:IN`/`WMS:STOCK:OUT` 被实际读取 |
| F1-10 | QMS、MES、EAM、能源补注解（QMS 19 个中 16 个无注解，含 `InspectionController.judge()` 未守卫的 `QMS:INSPECTION:JUDGE`；MES 6 / EAM 12 / 能源 4 全为 0） | 各系统 `…/controller/` | 四系统各抽 3 个写操作用越权账号验证 403 |
| F1-11 | `shared` 端点补齐：`GET/POST /api/events/business*`（3 个端点仅登录可访问）；`WorkflowController` 的转办/加签/取消/抄送/时间轴（当前仅 `pending`/`approve`/`reject` 有注解） | `source-apps/shared/common/…/integration/`、`source-apps/shared/workflow/…/controller/` | 越权账号调转办与抄送返回 403；`WF:TASK:*` 覆盖面完整 |

### 二、事件消费框架（F2）

> **现状机制**：`publish()` 写 `biz_outbox` → `@Scheduled(fixedDelay=1000) dispatch()` 用 `INSERT IGNORE` 投递 `biz_inbox` 并**立即把 outbox 置 `SUCCESS`**。即「投递成功」= 「写进 inbox」，而 inbox 无人读。`biz_inbox` DDL（`V26`）**只有** `event_id/event_type/source_system/target_system/aggregate_type/aggregate_id/trace_id/payload/status/received_at`——**没有**消费状态、消费者、消费时间、重试次数、错误信息、租约列。故消费机制必须新建，不能靠现有列。

| # | 任务 | 涉及文件 / 位置 | 验收方式 |
|---|---|---|---|
| F2-01 | 定义事件契约：`event_type` 命名规范（`<系统>.<聚合>.<动作>`）、`payload` 字段与类型、**`event_version` 的使用约定**（该列已存在于 `biz_outbox`，但从未被写入非默认值）、以及「事件类型 → 目标系统 → 消费方」路由表 | `docs/` 下事件契约小节；路由表可放 `infra/` 或契约文档内 | 路由表覆盖当前全部已发布事件类型（P2 的 `crm`/`erp` 类与 P3 的 `SRM`/`WMS`/`QMS` 类，见 [../current-status.md](../current-status.md) 缺陷 #14）；每个事件类型有明确消费方或显式记为「暂不消费」 |
| F2-02 | 新迁移给 `mfg_ops.biz_inbox` 加消费字段：`consume_status`（`PENDING/PROCESSING/CONSUMED/FAILED/DEAD`）、`consumer_name`、`consumed_at`、`retry_count`、`error_message`、`next_retry_at`、`lease_until`；并新建消费位点表（如 `mfg_ops.biz_consumer_offset`）与死信查询视图。**复用已有的唯一键 `uk_biz_inbox_event_target(event_id, target_system)` 作为幂等键**，不重复造 | `source-apps/bootstrap/src/main/resources/db/migration/V39__…sql`（与 F1-02 可同批或顺延编号） | 迁移后 `DESCRIBE mfg_ops.biz_inbox` 含上述列；重复插入同 `(event_id, target_system)` 仍被唯一键拦下 |
| F2-03 | 在 `shared/common` 定义消费者接口与注册表：`BusinessEventConsumer`（`supports(eventType)` + `consume(payload)`），Spring 自动收集所有实现 | `source-apps/shared/common/src/main/java/com/mfg/common/integration/`（当前只有 `BusinessEventService` 与 `BusinessEventController`） | 接口与注册表落地；未注册消费方的事件类型在派发时被记为「无消费方」而非静默丢弃 |
| F2-04 | 实现 inbox 消费调度器：按 `consume_status` 领取（用 `FOR UPDATE SKIP LOCKED` + `lease_until` 防重复消费）、以唯一键幂等、失败按指数退避重试、超限转死信 | `source-apps/shared/common/…/integration/`（新增调度类，参照 `BusinessEventService.dispatch()` 的写法） | 同一条事件重复投递只产生**一次**业务效果；人为让消费方抛异常，观察 `retry_count` 递增直至 `DEAD` |
| F2-05 | 人工重放端点：查询死信/失败事件并支持重放（对齐现有 `BusinessEventService.retry()` 的语义与错误码风格） | `source-apps/shared/common/…/integration/BusinessEventController.java` | 调重放端点后 `consume_status` 回到 `PENDING` 并被重新消费；非失败态调用返回业务错误码而非 500 |
| F2-06 | 梳理**跨库直写清单**并给出迁移决策：列出所有「同进程直接写他系统库」的调用点（如 CRM 直接写 `src_erp`、PLM 实施直接写 `src_mdm`、WMS→QMS 到货直写、MDM 分发直写 9 个副本表），逐条标注「改为事件驱动 / 保留但记录理由」 | 决策产物写入本文件同级的契约文档；调用点如 `CrmP2Service.createOrder()`、`MasterDataDistributor`、`P3FlowService.arriveAsn()` | 清单覆盖 [../current-status.md](../current-status.md) 提到的全部直写路径；每条有结论与理由，无「待定」项 |
| F2-07 | 试点改造 1–2 条现有事件为**真实消费**：建议取 `WMS.RECEIPT.PENDING_INSPECTION`（当前由 SRM 的 `P3FlowService.arriveAsn()` 发布）与 `QMS.INSPECTION.JUDGED` | 发布方 `source-apps/shared/common/…/service/P3FlowService.java`；消费方落在 WMS/QMS 的 `integration/` 包（多数系统该包尚不存在，需新建） | 同一条事件投递两次，库存/检验状态只变化一次；`biz_inbox` 出现 `consume_status='CONSUMED'` 的行——即 [../current-status.md](../current-status.md) 横向共性问题 2「无任何模块订阅消费」出现首个反例 |

### 三、单据终态模式（F3）

| # | 任务 | 涉及文件 / 位置 | 验收方式 |
|---|---|---|---|
| F3-01 | 定义统一终态约定文档：状态取值（终态统一为 `CLOSED`（正常完结）与 `VOIDED`（作废）；未生效单据用 `CANCELED`）；动作与端点模板（`POST /api/<系统>/<单据>/{id}/close`、`/void`）；合法迁移表；**终态不可再流转** | `docs/` 下终态约定小节 | 约定成文并与 [../system-functional-catalog.md](../system-functional-catalog.md) 第 2 项验收（「至少 3 类核心单据可以从创建流转到关闭或作废」）口径一致；8 个缺终态的系统各自能对照本约定列出「需要新增哪两个动作」 |
| F3-02 | 权限码模板：`<系统码>:<单据>:CLOSE` / `:VOID`，纳入 F1-04 的受控词表，并在同批迁移中定义与授予 | 同 F1-02 迁移 | 新码在 `sys_permission` 与 `sys_role_permission` 均有行；F1-01 脚本对「引用了但未定义」报零 |
| F3-03 | 状态机骨架模板：在 `domain/` 下定义状态枚举 + 合法迁移校验（拒绝非法迁移并抛业务错误码）。多数模块 `domain/` 为空、状态以字符串字面量散在 service 里 | 以 MDM 的 `domain/MasterDataStatus`（7 态，十系统里唯一已有的领域层）为样板；模板写入约定文档 | 样板代码可被 02–07 直接照抄；非法迁移返回业务错误码而非 500 |
| F3-04 | 审计与状态时间轴约定：终态动作必须留痕（复用 `shared/security` 的 `OperationAuditService`；注意其 `before_value` 列**当前从未写入**，需一并确定是否补）；时间轴复用 `wf_action_log` 或各单据自建状态流水 | `source-apps/shared/security/…/service/OperationAuditService.java`；约定写入文档 | 试点单据的 close/void 在审计表可查；`before_value`/`after_value` 的取舍在文档中写明结论 |
| F3-05 | 幂等要求：重复调用 close/void 应返回同一终态的稳定结果或业务错误码，**不得**二次冲销、不得 500 | 约定文档 | 约定成文；作为 02–07 各系统终态任务的统一验收口径 |
| F3-06 | 试点落地：在 MDM 的四类主数据（物料/产品/BOM/工艺路线，当前**无停用接口**，走不到 `DISABLED`）上完整实现终态动作，作为其余系统的可运行样例 | `source-apps/mdm/…/controller/{Material,Product,Bom,Routing}Controller.java`、`…/service/MasterDataService.java`、`…/domain/MasterDataEntity.java` | 四类各走一遍 `DRAFT → PENDING → PUBLISHED → DISABLED`；停用后 `findConsumable()` 不再返回该记录；重复停用返回业务错误码（与 [02-mdm-plm.md](02-mdm-plm.md) 任务 5 为同一件事，以本模板为准执行） |

### 四、实体-表同步（F4）

> **约定**：从本计划起，迁移脚本新增列**必须同步更新实体**；F4-01 的核对脚本作为该约定的执行手段。列级缺口本次核对结果如下（`→` 后为未映射列）。

| # | 任务 | 涉及文件 / 位置 | 验收方式 |
|---|---|---|---|
| F4-01 | 实体-表一致性核对脚本：比对 `information_schema.columns` 与 JPA 实体（`@Table`/`@Column`）的列集合，输出「表有列无实体字段」差异清单 | 新增 `ops/check_entity_schema.py`（或同目录脚本） | 脚本对已知缺口全部报出（下表各行的列都能命中）；修完后归零 |
| F4-02 | ERP：`SalesOrder` → `credit_status`/`atp_status`/`source_type`/`source_contract_no`/`tax_rate`/`factory_code`/`ship_to_address`（`V22`）；`ProductionOrder` → `production_version_code`/`kit_status`/`kit_checked_at`（`V27`） | `source-apps/erp/src/main/java/com/mfg/erp/entity/` | `GET /api/erp/sales-orders/{id}` 返回上述字段（当前为裸 SQL 专有）；`kit_status` 可经实体读到而非只在 `release()` 的 SQL 中 |
| F4-03 | ERP：16 张无实体表补实体或**显式登记为「裸 SQL 表」**（`erp_receivable`、`erp_invoice`、`erp_receipt`、`erp_settlement`、`erp_customer_credit`、`erp_credit_check`、`erp_atp_snapshot`、`erp_supply_snapshot`、`erp_sales_order_change`、`erp_mrp_run`、`erp_mrp_requirement`、`erp_plan_suggestion`、`erp_purchase_requisition`、`erp_payable`、`erp_order_cost`、`erp_md_bom`/`erp_md_bom_line`） | `source-apps/erp/…/entity/` 与 ERP 的 `PLAN.md` | 「补实体」者接口可读到该表字段；「登记为裸 SQL」者在文档中登记理由。**逐表取舍需先与 [04-erp-crm.md](04-erp-crm.md) 对齐**，避免两边各改一半 |
| F4-04 | WMS：`Inventory` → `quality_status`/`frozen_qty`/`received_date`（`V33`） | `source-apps/wms/…/entity/Inventory.java` | `GET /api/wms/inventories` 的 JSON 出现质量状态与冻结量（当前看不到） |
| F4-05 | MES：`WorkOrder` → `routing_code`/`routing_version`/`work_center`/`workshop_code`/`plan_start_time`/`plan_end_time`/`plan_hours`/`actual_hours`/`workshop_user`/`created_at`/`updated_at`（共 11 列）；`mes_prod_result` 无实体无 repo 无接口（`progress_rate` 恒为 `NULL`） | `source-apps/mes/…/entity/WorkOrder.java`；`mes_prod_result` 需新建实体+repo | 工单详情返回派工与工时字段（「派工与工时能力只有列名、没有代码」的状态结束）；`progress_rate` 有值 |
| F4-06 | QMS：`Inspection` → `source_type`/`source_no`/`standard_code`/`supplier_code`（`V36` 及原有列）；`qms_ncr`/`qms_capa`/`qms_eight_d`/`qms_standard`/`qms_sampling_plan` 五表按 F4-03 同一口径取舍 | `source-apps/qms/…/entity/Inspection.java` | `GET /api/qms/inspections` 返回来源单号与供应商（当前只能从裸 SQL 结果取） |
| F4-07 | SRM：`PurchaseOrder`/`SupplierDelivery` → `V31` 的 5 列（`source_requisition_no`/`confirmed_at`/`change_reason`/`asn_no`/`receipt_no`）。注意 `confirmed_at`/`change_reason`/`asn_no`/`receipt_no` **目前没有任何代码写入**，「加映射」与「补写入」是两件事 | `source-apps/srm/…/entity/` | 映射后 JPA 读接口可见；对「无写入点」的四列，明确是补写入还是删列，**需先决策** |
| F4-08 | EAM：`Equipment` → `purchase_date`/`purchase_price`/`warranty_end_date`/`maintenance_cycle_days`/`last_maintenance_date`；`EquipmentFault` → `fault_cause`；`EquipmentInspection` → `check_items`(JSON)；`eam_equipment_status_log` 有 DDL **无实体无接口** | `source-apps/eam/…/entity/`；状态台账需新建 | 设备/故障/点检详情可读写上述字段；设备状态切换后台账有记录（对应缺陷 #8） |
| F4-09 | CRM：`Opportunity` → `source_lead_no`/`probability`/`expected_close_date`/`lost_reason`/`contact_name` | `source-apps/crm/…/entity/Opportunity.java` | 走 JPA 的商机读接口能看到这些列（当前 `convert()`/`stage()` 用 SQL 写入后读接口看不到） |
| F4-10 | PLM：`EngineeringChange` → `ecr_id`/`eco_id`/`target_type`/`target_version` | `source-apps/plm/…/entity/EngineeringChange.java` | `GET /api/plm/ecns/{id}` 能看到变更目标（当前看不到） |
| F4-11 | 能源：`EquipmentUsage`/`WorkshopUsage` → `baseline_value`/`deviation_rate`/`is_abnormal` | `source-apps/energy/…/entity/` | 能耗接口可读写三列（当前完全无法读写） |
| F4-12 | 约定固化：把「迁移加列必须同步实体」写进 [README.md](README.md) 的交付约定与各模块 `PLAN.md`；F4-01 脚本纳入 F1-05 的校验入口 | `docs/plans/README.md`、各模块 `PLAN.md` | 约定成文；脚本可被一键运行并给出非零退出码 |

## 完成标准

- [ ] **① 权限**：十系统 204 个端点中，「无方法级注解且不在白名单」的端点数归零；6 个未定义权限码在 `sys_permission` 有行，5 个未授予码在 `sys_role_permission` 有行；权限码命名规范成文且被脚本校验。
- [ ] **② 终态**：终态约定（状态取值 / 端点模板 / 权限码 / 审计 / 幂等）成文；MDM 四类主数据作为可运行样例走通到 `DISABLED`。
- [ ] **③ 事件**：`biz_inbox` 具备消费状态与重试字段；消费调度器落地；至少 1 条现有事件被真实消费（`consume_status='CONSUMED'` 有行），且重复投递只产生一次效果；死信可查、可人工重放。
- [ ] **④ 实体同步**：F4-01 脚本对 F4-02…F4-11 所列全部列/表缺口报零；「迁移加列必须同步实体」写入交付约定。
- [ ] **⑤ 可复用性**：02–07 各计划的「涉及文件 / 位置」能直接引用本文件的模板编号（F1/F2/F3/F4），不需要各自重新设计。
- [ ] 本计划**未新增任何业务功能**——所有改动的性质是注解、迁移、约定与脚本。

## 风险与前置

1. **工作区已在被并发修改（最高优先级）**：截至本文撰写，工作区存在**未提交**修改——`BomController.java` 把 `MDM:BOM:UPDATE` 改为 `MDM:BOM:CHANGE`、`CrmP2Service.customer360()` 改查 `md_partner_contact`、`EngineeringChangeChainController` 的正则补了 `ecns`、`bootstrap/application.yml` 的注释改为「不使用多数据源切换」。**这些同时命中缺陷 #1、#2、#7、#12**。开工前必须先确认这些改动是否要提交，否则本计划与 [01-bugfix.md](01-bugfix.md) 会对已修完的条目重复劳动。
2. **`F1-02` 与 [02-mdm-plm.md](02-mdm-plm.md) 任务 1 重叠**：该计划已把「补齐 5 个 MDM 缺失权限码」列为自己的任务 1。建议**以本计划 F1-02 为准**（同一迁移一次补齐 6 码 + 5 个授权，避免两个迁移抢同一批权限码），02 的任务 1 改为「验证」。开工前需与 02 对账。
3. **`MDM:BOM:UPDATE` → `MDM:BOM:CHANGE` 是语义借用**：`MDM:BOM:CHANGE` 的中文名是「BOM变更申请」，且 `09_role_permission.sql` 只授予 `PRODUCT_ENGINEER`/`PROCESS_ENGINEER`（`PROCESS_SUPERVISOR`/`RND_MANAGER` 未授）。若接受此借用，需确认「改 BOM」的组织范围是否就是这两个角色；若不接受，则应正经补种 `MDM:BOM:UPDATE` 并授权。
4. **数据范围（缺陷 #10）不在本计划铺开范围**：`DataScopeAspect` 的切点 `@annotation(dataScope)` 全仓 0 个使用点（唯一的 `@DataScope(` 出现在 `DataScope.java` 的 javadoc 示例里）。补注解只是第一步，真正生效还需把 `DataScopeContext` 接到查询 SQL 上；且 `resolveScope()` 只读 `data_scope_type`、**从不解析 `data_scope_value`**，而「未配置规则 → 返回 `ALL`」是宽松默认。建议单独立项，本计划只在 F1-01 中登记。
5. **`shared/masterdata` 空壳的处置未决**（缺陷 #11）：1 个 `package-info.java`、10 个 pom 声明依赖、全仓 0 引用，消费方（crm/erp/wms）改为直接依赖 `app-mdm` 并 `import com.mfg.mdm.*`。「实现三职责」与「删模块清依赖」是两条相反的路，需先决策，本计划不预设。
6. **`F2-02` 的迁移编号需统一分配**：本计划与 02–07 都会需要 `V39+` 编号，且 [README.md](README.md) 已约定「不再改动 `infra/db-init/`」。开工前需一次性分配迁移编号区间，否则会出现编号冲突。
7. **`F2-04` 的单进程假设**：现有 `BusinessEventService.dispatch()` 是单个 `@Transactional` + 逐行 `FOR UPDATE`，多实例下锁语义不成立。本计划面向单进程演示环境，此假设需在约定文档中写明，避免被误当作生产级实现。
8. **`event_version` 与 `payload` 无 schema**：`biz_outbox.event_version` 列存在但从未写入非默认值，`payload` 是自由 JSON。F2-01 的契约是**新增**约束，对历史事件数据不做回溯校验。
9. **事件系统编码大小写混存**（缺陷 #14）：`biz_outbox` 中 P3 链写 `SRM`/`WMS`/`QMS`（大写），P2 模块写 `crm`/`erp`/`mes`/`srm`（小写）。F2-01 的路由表必须选定一种大小写并写迁移归一化历史数据，否则消费方按 `target_system` 匹配会漏掉一半事件。
10. **待确认项**：`BizType.PRODUCT` 的 `approvalFlow` 指向 `MDM_MATERIAL_NEW`（而非已种子化却无人引用的 `MDM_PRODUCT_NEW`）——是刻意复用物料审批链还是登记错误，**待确认**，本计划不擅自改。

## 后续计划如何受益

| 本计划产物 | 直接受益的计划 | 受益方式 |
|---|---|---|
| F1 鉴权模板 + 校验脚本 | 02–07 全部 | 各系统只需按系统填注解与授权行，「权限码是否存在/是否授予」由脚本兜底，不再逐个 SQL 排查 |
| F2 消费框架 + 路由表 | 02 MDM/PLM（消费 `PLM.ECN.IMPLEMENTED`）、04 ERP/CRM、05 SRM/EAM、06 MES | 各系统只需实现 `BusinessEventConsumer` 并登记，不需要各自重写投递/重试/死信 |
| F3 终态约定 + MDM 样例 | 02–07 全部（8 个系统缺终态） | 每类单据的终态动作从「设计题」降为「按模板加两个端点 + 两个权限码」 |
| F4 实体同步清单 + 核对脚本 | 02–07 全部 | 消除「接口查不到已建好的字段」，各系统计划可直接引用本文件的列清单逐项销账 |
