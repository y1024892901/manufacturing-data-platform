# 04. ERP + CRM 收口

> **依赖**：[00 横向地基](00-foundation.md)（鉴权 / 事件消费 / 终态动作 / 实体同步四套模板） · **预估**：1–2 轮 · **对应验收项**：ERP ①②③④⑤、CRM ①②③⑤（CRM ④ 已达标，本计划仅补消费侧）
>
> 现状依据：[erp/STATUS.md](../../source-apps/erp/STATUS.md)、[crm/STATUS.md](../../source-apps/crm/STATUS.md)；验收定义见 [system-functional-catalog.md](../system-functional-catalog.md)；计划分工见 [README.md](README.md)。
> 下表「涉及文件 / 位置」中，Java 路径的包根是 `source-apps/<系统>/src/main/java/com/mfg/<系统>/`，行内 `erp/entity/…`、`crm/service/…` 这类简写均指该根下的相对位置；SQL 一律指 `source-apps/bootstrap/src/main/resources/db/migration/`。

## 目标

做完后，ERP 的单据主链不再停在「下达 / 已确认」：**销售订单、生产订单、财务凭证（或应付）三类单据可以创建 → 流转 → 关闭或作废**，已入库的 `ERP:*` 权限码（`08_seed_data.sql` 14 个 + `V25` 8 个）在接口上真实生效，并补上入向事件消费者与账龄报表——五项验收全达标。

CRM 补上**线索 / 报价 / 合同三类单据的关闭与作废**、打通**客诉闭环**（登记 → 转派 → 关联 QMS 8D → 关闭），权限点落到接口，销售漏斗报表落地——五项验收全达标。

> 两个系统的④均已由出向事件侧达标，本计划只补**入向消费**；两个系统的界面工作归 [08 前端与运营报表](08-frontend-reports.md)，本计划只做后端。

## 现状

**ERP（★★★☆☆，`../current-status.md` 验收矩阵：①部分 ②✗ ③部分 ④部分 ⑤部分）**

单据主链「销售订单 → 信用检查 → ATP → MRP → 生产订单下达 / 请购转出 → 开票 → 应收 → 回款 → 自动凭证」已跑通（P2 阶段目标达成），分层统计为 `entity/` 4 + `repo/` 4 + `service/` 3 + `controller/` 4 + 非标准 `callback/` 1 + `dto/` 2 共 19 个文件（含 `package-info.java`），`domain/`、`workflow/`、`integration/`、`query/` 四层全空。

与最低验收的差距（逐条证据见 [erp/STATUS.md](../../source-apps/erp/STATUS.md#对照最低验收)）：

- **② 未实现（本计划的核心目标）**：32 个端点里没有任何一类单据能走到关闭或作废。生产订单仅 `CREATED → RELEASED`；财务凭证创建后无审核 / 过账；应付无付款与核销。而 `src_erp.erp_sales_order.order_status` 的列注释里 `CLOSED`、`src_erp.erp_prod_order.order_status` 的列注释里 `FINISHED`/`CLOSED` **都已设计但无接口**。
- **① 部分**：DB 侧权限字典与 10 个岗位角色的授予都已入库——`08_seed_data.sql` 定义 14 个 `ERP:*` 码（含终态动作所需的 `ERP:PROD_ORDER:CLOSE`、`ERP:VOUCHER:AUDIT`、`ERP:PAYABLE:PAY`），`V25__p2_workflow_permission_seed.sql` 再补 8 个操作级码（`ERP:CREDIT:APPROVE`、`ERP:ATP:COMMIT`、`ERP:MRP:RUN`、`ERP:PRODUCTION_ORDER:RELEASE`、`ERP:FINANCE:RECEIPT`、`ERP:FINANCE:SETTLE`、`ERP:FINANCE:COST_VIEW`、`ERP:SALES_ORDER:CONFIRM`），**合计 22 个**。但 **Java 代码 0 处 `@PreAuthorize`**（全仓库仅 36 处该方法级注解，全部落在 mdm / plm / qms / wms / shared，四个待收口系统一处也没有）——任何持有 `SYSTEM_ERP` 的账号可执行全部 32 个端点。
- **④ 部分**：发送侧 6 个事件达标（`ERP.SALES_ORDER.CONFIRMED`、`ERP.MRP.COMPLETED`、`ERP.RECEIPT.SETTLED`、`ERP.PURCHASE_REQUISITION.CREATED`、`ERP.PRODUCTION_ORDER.CREATED`、`ERP.PRODUCTION_ORDER.RELEASED`），**消费侧为 0**，全仓库没有任何模块订阅 `ERP.*` 事件。
- **⑤ 部分**：仅有 `margin()` → `GET /api/erp/order-costs/{no}` 单笔毛利查询，账龄分析未实现（`erp_receivable.due_date` 已备）。
- **技术债**：`MasterDataService.assertConsumable()` 的「仅已发布可消费」校验只覆盖 `SalesOrderService` / `ProductionOrderService`，`ErpP2Service` 全部接口绕过校验直读 `src_mdm`；MRP 展 BOM 读本地副本 `src_erp.erp_md_bom` 而物料 / 生产版本读源库，**同模块两套主数据口径并存**（[erp/PLAN.md](../../source-apps/erp/PLAN.md) 自评为「最需要收敛的技术债」）。`erp_md_customer`/`erp_md_supplier`/`erp_md_material`/`erp_md_cost_center`（`infra/db-init/04`）与 `erp_md_product`（`V13`）五张副本表建了但代码从未读取。JPA 实体落后：仅 4 个实体、16 张表无实体，`erp_sales_order` 有 7 列（`V22`）、`erp_prod_order` 有 3 列（`V27`）未映射。

**CRM（★★☆☆☆，验收矩阵：①部分 ②✗ ③部分 ④✓ ⑤✓）**

「报价 → 合同 → ERP 销售订单」主线已打通，带 3 条审批流（`QUOTATION`、`QUOTATION_RISK`、`CONTRACT`）与 3 个幂等 Outbox 事件。分层为 `entity/` 2 + `repo/` 2 + `service/` 1 + `controller/` 2，`domain/`、`workflow/`、`integration/`、`query/` 四层全空，回调在非标准的 `callback/` 包。

与最低验收的差距（逐条证据见 [crm/STATUS.md](../../source-apps/crm/STATUS.md#对照最低验收)）：

- **② 部分**：线索（`NEW → FOLLOWING → CONVERTED`）、报价（`DRAFT → PENDING → APPROVED → EFFECTIVE → EXPIRED`）、合同（`DRAFT → PENDING → APPROVED → ACTIVE`）三类可流转，但 **`TERMINATED`、`CANCELED` 只出现在「该报价是否已关联有效合同」的排除条件里，没有任何代码写入**；线索的 `invalid_reason` 也无路径。
- **① 部分**：两个 Controller（`CrmP2Controller`、`OpportunityController`）**零 `@PreAuthorize`**；`CRM:*` 码共 10 个已有定义与授予——`08_seed_data.sql` 定义 5 个（`CRM:OPPORTUNITY:*` 五个动作），`V25` 再补 5 个（`CRM:LEAD:CREATE`、`CRM:LEAD:ASSIGN`、`CRM:OPPORTUNITY:STAGE`、`CRM:QUOTATION:SUBMIT`、`CRM:CONTRACT:ACTIVATE`），由 `09_role_permission.sql` 授给四个销售岗位——但接口一处不读。
- **③ 部分**：审批有三条，但**异常闭环未实现**——客诉只登记为 `OPEN`，`crm_complaint` 的 `solution`、`customer_feedback`、`closed_at` 三列无人写入，无转派、无 QMS 8D/CAPA 关联。
- **缺口**：`V19` 建的 `crm_opportunity_product`、`crm_competitor`、`crm_sales_forecast` 三张表**全仓库零读写**；预测的 `product_code` 在 SQL 中写死 `NULL`；`Opportunity` 实体未映射 `source_lead_no`、`probability`、`expected_close_date`、`lost_reason`、`contact_name` 五列（由 SQL 写入，JPA 读接口看不到）；`CrmP2Service` 单类约 500 行承载 7 个模块。

> 说明：`CrmP2Service.customer360()` 原读的 `src_mdm.md_customer_contact` 表全仓无建表语句（缺陷 #2，属 [01 缺陷修复](01-bugfix.md)）。**该缺陷已在工作区修复但尚未提交**：现行代码第 19 行已改为 `src_mdm.md_partner_contact WHERE partner_type='CUSTOMER' AND partner_id=(…)`，与 `V16` 实际建的联系人表一致。本计划**不重复修**，只需在 01 提交后复验客户 360 不再报错。

## 任务拆解

> 表内顺序即优先级；每行可独立完成、独立验证。ERP 的 1–10 与 CRM 的 11–18 可并行，19–20 为两系统共用。

| # | 系统 | 任务 | 涉及文件 / 位置 | 验收方式 |
|---|---|---|---|---|
| 1 | ERP | 建 `domain/` 层：单据状态枚举与合法迁移表。取值直接取自两列的注释（销售订单 `DRAFT/PENDING/CONFIRMED/IN_PROD/DELIVERED/CLOSED/CANCELED`，生产订单 `CREATED/RELEASED/IN_PROGRESS/FINISHED/CLOSED/CANCELED`），淘汰散落的字符串字面量 | 新建 `source-apps/erp/src/main/java/com/mfg/erp/domain/`：`SalesOrderStatus`、`ProductionOrderStatus`、`VoucherStatus`、`ReceivableStatus`、`PayableStatus` | 枚举值集合与 `infra/db-init/04_business_systems.sql` 的 `order_status` 列注释逐字一致；非法迁移抛业务异常 |
| 2 | ERP | JPA 实体补齐未映射列：`SalesOrder` 补 `credit_status`/`atp_status`/`source_type`/`source_contract_no`/`tax_rate`/`factory_code`/`ship_to_address`（`V22`）；`ProductionOrder` 补 `production_version_code`/`kit_status`/`kit_checked_at`（`V27`） | `erp/entity/SalesOrder.java`、`erp/entity/ProductionOrder.java` | `GET /api/erp/sales-orders/{id}` 与 `GET /api/erp/production-orders` 直接返回这些字段，不再依赖裸 SQL |
| 3 | ERP | 销售订单关闭动作：前置为 `order_status ∈ {CONFIRMED, IN_PROD, DELIVERED}` 且 `erp_receivable` 未结金额为 0；置 `CLOSED` 并写 `erp_sales_order_change`（`change_type='CLOSE'`）留痕。本动作是全模块**唯一缺权限码**的终态动作（需新增 `ERP:SALES_ORDER:CLOSE`） | `POST /api/erp/sales-orders/{id}/close`；`erp/service/SalesOrderService.java` 或 `ErpP2Service` | 调用后 `SELECT order_status FROM src_erp.erp_sales_order WHERE sales_order_no=?` 为 `CLOSED`；重复调用返回业务异常；`erp_sales_order_change` 有对应行 |
| 4 | ERP | 生产订单完工入库 + 关闭：完工写 `completed_qty`/`qualified_qty`/`scrap_qty`/`actual_finish_date` 并置 `FINISHED`；关闭置 `CLOSED`（权限码 `ERP:PROD_ORDER:CLOSE` 已入库但无接口） | `POST /api/erp/production-orders/{id}/finish`、`POST /api/erp/production-orders/{id}/close` | 两接口各使 `src_erp.erp_prod_order.order_status` 前进一态；`FINISHED` 前调用 `close` 被拒 |
| 5 | ERP | 财务凭证过账：`erp_fin_voucher` **当前无状态列**，需先加列再实现 `post`/`cancel`；过账后凭证不可修改，复用既有借贷平衡校验 | V39+ 迁移加 `voucher_status`/`posted_at`/`posted_by`；`POST /api/erp/financial-vouchers/{id}/post`、`POST /api/erp/financial-vouchers/{id}/cancel` | 过账后 `voucher_status='POSTED'` 且 `posted_at` 非空；重复过账被拒；过账后 `PUT` 类修改被拒 |
| 6 | ERP | 应付付款与核销：新建付款台账表（`erp_settlement` 的 `receipt_id` 非空、只服务应收，不能复用），累加 `erp_payable.paid_amount`，全额时置 `PAID`，并生成付款凭证 | V39+ 迁移新建 `src_erp.erp_payment`；`POST /api/erp/payables/{id}/pay`、`POST /api/erp/payables/{id}/close` | `erp_payable.status` 走 `OPEN → PARTIAL → PAID`；付款金额超过未付金额被拒 |
| 7 | ERP | 应收账龄报表：按 `due_date` 分「未到期 / 1–30 / 31–60 / 61–90 / 90+」五档，聚合 `outstanding_amount` | `GET /api/erp/receivables/aging`；新建 `erp/query/ReceivableAgingQuery.java`（`query/` 层目前为空） | 五档金额合计 = 未结清应收总额；跨档边界用例（`due_date` = 今天）行为明确 |
| 8 | ERP | 主数据口径收敛（二选一，不许继续并存）：`ErpP2Service` 的全部接口接入 `MasterDataService.assertConsumable()`；BOM 读取由本地副本统一到与物料 / 生产版本同一口径 | `erp/service/ErpP2Service.java`、`MasterDataService` 调用点；涉及 `src_erp.erp_md_bom`/`erp_md_bom_line` 与 `src_mdm.md_material`/`md_production_version` | 未发布（`PUBLISHED` 以外状态）的物料 / BOM 经任一 ERP 接口消费时被拒；同一物料经两条路径取到的字段一致 |
| 9 | ERP | 入向事件消费：按 00 的消费者模板处理 `CRM.CONTRACT.ORDER_REQUESTED`，按 `source_contract_no` 幂等建销售订单（该列已有唯一索引 `uk_erp_order_contract`） | 新建 `erp/integration/` 包；消费 `mfg_ops.biz_inbox` | 重放同一事件两次只产生一张订单；`erp_sales_order.source_contract_no` 有值 |
| 10 | ERP | 权限落地：32 个端点补 `@PreAuthorize`，映射已入库的 22 个 `ERP:*` 码（`08_seed_data.sql` 14 个 + `V25` 8 个，恰好覆盖信用 / ATP / MRP / 生产订单下达 / 财务收付 / 凭证审核等现有端点）与 `09_role_permission.sql` 的角色。终态动作的权限码**绝大多数已存在**，唯一缺的是销售订单关闭——随 V39+ 迁移补 `ERP:SALES_ORDER:CLOSE` 并授给 `SALES_DIRECTOR`/`FINANCE_DIRECTOR` | `erp/controller/` 四个 Controller + `erp/service/` | `grep -c @PreAuthorize erp/` ≥ 32；无对应权限的账号调用返回 403（不再只靠 `SYSTEM_ERP`） |
| 11 | CRM | 建 `domain/` 层：阶段 / 状态枚举与阈值常量。折扣 ≥ 10%、毛利率 < 15%、金额 ≥ 50 万三个风险阈值现在是 `CrmP2Service` 方法体字面量，抽为常量并集中一处 | 新建 `source-apps/crm/src/main/java/com/mfg/crm/domain/`：`LeadStatus`、`OpportunityStage`、`QuotationStatus`、`ContractStatus`、`ComplaintStatus`、`QuoteRiskThreshold` | 三档风险分流结果与抽取前一致（`QUOTATION` vs `QUOTATION_RISK` 用例逐条比对） |
| 12 | CRM | 三类单据补终态动作：线索失效（写 `invalid_reason`、置 `INVALID`）、报价作废（`EFFECTIVE` 需先 `EXPIRED` 才可作废、置 `VOID`）、合同终止 / 归档 / 续签（写 `crm_contract_change` 留痕）；`TERMINATED`、`CANCELED` 正式进入枚举 | `POST /api/crm/leads/{id}/invalidate`、`POST /api/crm/quotations/{id}/void`、`POST /api/crm/contracts/{id}/terminate`、`POST /api/crm/contracts/{id}/archive` | 三类单据各有一条路径到达终态；作废后的报价不能再被 `createContract()` 引用 |
| 13 | CRM | 客诉闭环：转派责任人、关联 QMS 8D/CAPA 单号、关闭时写 `solution`/`customer_feedback`/`closed_at`，并发出 `CRM.COMPLAINT.CLOSED` | `POST /api/crm/complaints/{id}/transfer`、`POST /api/crm/complaints/{id}/link-8d`、`POST /api/crm/complaints/{id}/close`；`crm/service/` | `crm_complaint.closed_at` 非空；未关闭的客诉不出现在已闭环统计里 |
| 14 | CRM | 启用 `V19` 三张空表：商机产品明细（随商机创建写入）、竞争对手（独立维护）、预测快照（落库，替代只算不存） | `crm_opportunity_product`、`crm_competitor`、`crm_sales_forecast`；`CrmP2Service`、`OpportunityController` | 三张表各有写入路径与对应查询接口，`SELECT COUNT(*)` 不再恒为 0 |
| 15 | CRM | 实体同步：`Opportunity` 补 `source_lead_no`/`probability`/`expected_close_date`/`lost_reason`/`contact_name` 五列；补 `findByOpportunityNo` 领域查询 | `crm/entity/Opportunity.java`、`crm/repo/OpportunityRepository.java` | 走 JPA 的读接口能返回 `lost_reason` 与 `contact_name`（现状必须直连 SQL 才看得到） |
| 16 | CRM | 权限落地：两个 Controller 补 `@PreAuthorize`，使用 `08_seed_data.sql` 与 `V25` 已有的 `CRM:*` 码，按 `09_role_permission.sql` 的 `SALES_REP`/`SALES_ASSISTANT`/`SALES_SUPERVISOR`/`SALES_DIRECTOR` 授予 | `crm/controller/OpportunityController.java`、`crm/controller/CrmP2Controller.java` | `grep -c @PreAuthorize crm/` ≥ 两个 Controller 的端点数；仅持 `SYSTEM_CRM` 的账号调用合同生效 / 生成订单返回 403 |
| 17 | CRM | 分层归位与拆分：`callback/CrmApprovalCallback` 移入 `workflow/`；列表 / 预测 / 客户 360 的 SQL 迁入 `query/`；`integration/` 承接事件发布 | `crm/workflow/`、`crm/query/`、`crm/integration/` | 8 层目录齐备；`CrmP2Service` 行数显著下降（拆分后每类职责一个类） |
| 18 | CRM | 销售漏斗与目标达成报表：按阶段聚合商机数量 / 金额 / 加权金额与阶段间转化率；同时修掉预测里 `product_code` 写死 `NULL` 的问题 | `GET /api/crm/analytics/funnel`；`crm/query/` | 各阶段漏斗金额合计 = 未关闭商机总额；转化率与手工核对一致 |
| 19 | 两系统 | 新增 Flyway 迁移承载本计划的 DDL 与权限种子（凭证状态列、`erp_payment` 表、缺失权限码与角色授予）；**版本号从 V39 起，注意与 02/03/05/06/07 并行计划的争用** | `source-apps/bootstrap/src/main/resources/db/migration/V39+__p4_erp_crm_closure.sql` | 迁移可在空库与已有库上重复执行；不修改 `infra/db-init/` 下任何脚本 |
| 20 | 两系统 | 端到端验收脚本：仿 `ops/demo_bom_approval.py` 的写法（纯标准库、可重复运行、真实 HTTP 打 `localhost:8080`） | 新建 `ops/demo_erp_crm_closure.py` | 脚本断言：ERP 三类单据到达终态、CRM 三类单据到达终态、客诉闭环、账龄五档；结尾 `sys.exit(非0)` 以便 CI 判定（参照缺陷 #23 的教训） |

## 完成标准

**ERP**

- [ ] ① 任务 10 完成：32 个端点全部有 `@PreAuthorize`，22 个已入库 `ERP:*` 权限码全部有接口引用；无权限账号返回 403。
- [ ] ② 任务 3 / 4 / 5 / 6 完成：**销售订单、生产订单、财务凭证三类**（外加应付）可从创建流转到 `CLOSED` / `POSTED` / `PAID`，且每次终态变更都在 `erp_sales_order_change` 或凭证状态列留痕。
- [ ] ③ 任务 9 完成：信用例外审批链路保持可用，且 ERP 至少有一个入向消费者真实处理事件（`CRM.CONTRACT.ORDER_REQUESTED`）。
- [ ] ④ 出向 6 个事件不回归 + 入向 1 个消费者生效。
- [ ] ⑤ 任务 7 完成：`GET /api/erp/receivables/aging` 返回五档账龄。
- [ ] 任务 2 完成：`SalesOrder`、`ProductionOrder` 的未映射列全部映射，接口可读写。
- [ ] 任务 8 完成：主数据口径二选一收敛落地，`ErpP2Service` 不再绕过 `assertConsumable()`。

**CRM**

- [ ] ① 任务 16 完成：两个 Controller 均有 `@PreAuthorize`，`CRM:*` 码有接口引用。
- [ ] ② 任务 12 完成：线索、报价、合同**三类**单据各有一条到达 `INVALID` / `VOID` / `TERMINATED` 的路径。
- [ ] ③ 任务 13 完成：客诉闭环跑通（登记 → 转派 → 关联 8D → 关闭），`closed_at` 非空；三条既有审批流不回归。
- [ ] ④ 任务 9 的 CRM 侧对应事件已发出（现状已达标，保持）。
- [ ] ⑤ 任务 18 完成：销售漏斗报表可查，且 `product_code` 不再是写死的 `NULL`。
- [ ] 任务 14 完成：`V19` 三张表均有读写路径。
- [ ] 任务 17 完成：`domain/`、`workflow/`、`integration/`、`query/` 四层目录齐备。

**计划级**

- [ ] 任务 19 的迁移在两个系统上均可重复执行，且未触碰 `infra/db-init/`。
- [ ] 任务 20 的验收脚本连跑两次结果一致（可重复运行，符合 [ops/PLAN.md](../../ops/PLAN.md) 的脚本约定）。
- [ ] `source-apps/erp/STATUS.md`、`source-apps/crm/STATUS.md`、`../current-status.md` 的完成度与验收矩阵按交付约定同步更新。

## 风险与前置

1. **前置：01 缺陷修复。** 状态已核实（2026-09-22）：本计划涉及的两条已**在工作区改好但尚未提交**——缺陷 #2 的 `customer360()` 已改读 `src_mdm.md_partner_contact`（`CrmP2Service.java:19`），缺陷 #4 同批修复（见 [05](05-srm-eam.md)）。因此任务 18 的漏斗 / 客户 360 增强**不再被阻塞**，但需在 01 正式提交后复验一次；`erp_receivable` 的账龄（任务 7）本就不依赖它。
2. **前置：00 的四件模板必须先落地。** 任务 8（实体同步）、9（事件消费）、10/16（鉴权）、3–6/12（终态动作）分别是 00 四个模板的应用，若 00 未定稿，本计划应只做**一个系统**的示范，避免在 ERP/CRM 里重复解同一道题。
3. **Flyway 版本号争用（最现实的冲突点）。** 02/03/05/06/07 与本计划都从 V39 起新开迁移，并行提交会撞号。建议在 00 中先划段（例如按计划号分段），本计划落地前确认一次当前最大版本。
4. **ERP 主链回归风险。** 任务 1 引入状态机、任务 2 改实体映射、任务 8 改主数据口径，三者都会影响已跑通的 MRP / ATP / 齐套检查。改动后必须重跑现有 `ops/` 脚本与 P2 演示数据，确认主链未断。
5. **`erp_receivable` 存在语义重复的列（待确认）。** 该表同时有 `settle_status`（`infra/db-init/04`）与 `status`（`V24`），以及 `received_amount` 与 `settled_amount` 两套金额列。任务 6 / 7 落地前需确认以哪套为准，否则账龄与核销会各算一套。
6. **跨库直写的改造边界（待确认）。** CRM 目前**直接跨库写** `src_erp.erp_sales_order` 完成订单落地，事件只是投递记录。任务 9 只新增 ERP 侧消费者；是否同时把 CRM 的直写改为纯事件驱动，取决于 00 的消费者模板进度与两系统的联调安排，未定则本计划保留直写、仅保证消费者幂等可重放。
7. **CRM 单类拆分的回归面。** 任务 17 把约 500 行的 `CrmP2Service` 拆到 `query/` 与 `integration/`，涉及两个 Controller 共 25 个端点（`CrmP2Controller` 20 + `OpportunityController` 5）。建议按模块分批迁移（线索 → 报价 → 合同 → 客诉 → 预测 → 客户 360），每批跑一次验收脚本。
8. **界面不在本计划范围。** 两个系统的操作页面与报表页归 [08 前端与运营报表](08-frontend-reports.md)；本计划的验收一律以接口 + SQL 断言为准，符合「P5 前不以截图代替界面」的约束。
