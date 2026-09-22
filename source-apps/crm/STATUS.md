# crm/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**报价 → 合同 → ERP 订单这条「赢单交付」主线是打通且带审批与跨系统事件的，线索/商机/合同/客诉有接口但缺关闭作废与闭环，且两个 Controller 没有任何业务权限点校验、客户 360 的联系人表在库中不存在。**

## 已实现

- **线索到商机**：`createLead()` → `assign()`（写 `crm_lead_assignment` 留痕）→ `follow()`（写 `crm_lead_follow`）→ `convert()`（校验客户已发布、防重复转换，从 `md_customer` 取名字插入 `crm_opportunity`）。
- **商机阶段推进**：`OpportunityController.stage()` 白名单阶段 + 两条硬规则——**赢单（`WON`）前必须存在 `EFFECTIVE` 报价**、**丢单（`LOST`）必须填原因**（直写 `lost_reason`）；跟进记录支持 `CALL/VISIT/EMAIL/MEETING` 四类并要求白名单校验。
- **报价自动算价**：`createQuote()` 逐行校验 MDM 产品已发布、数量 > 0，自动算 `discount_rate = (1 - 报价额/标准价)*100` 与 `gross_margin_rate = (报价额-成本)/报价额*100`；`newVersion()` 复制头行升版本。
- **报价风险分流审批**：`submit()` 中折扣 ≥ 10% 或毛利率 < 15% 或金额 ≥ 50 万走 `QUOTATION_RISK`（销售总监 + 财务主管两级，`V26`），否则走 `QUOTATION`（销售主管一级，`V25`）；回调 `CrmApprovalCallback` 统一改写状态为 `APPROVED` / `REJECTED`。
- **报价生效唯一性**：`activateQuote()` 生效时把同 `quotation_no` 的其他 `EFFECTIVE` 版本批量置 `EXPIRED`，保证一个报价只有一个生效版本。
- **合同由报价转入且回款计划受控**：`createContract()` 要求报价为 `EFFECTIVE` 且未关联有效合同，**回款计划金额合计必须严格等于合同金额**，金额/税额从报价继承。
- **合同变更留痕**：`newContractVersion()` 生成新版本并写 `crm_contract_change`（`from_version` / `to_version` / `change_reason` / `changed_by`），新行 `previous_contract_id` 指向旧版本。
- **跨系统事件**：合同生效发 `CRM.CONTRACT.ACTIVATED`；生成 ERP 销售订单时发 `CRM.CONTRACT.ORDER_REQUESTED` 与 `ERP.SALES_ORDER.CREATED`（后者 source 标 `erp` / target `crm`，模拟回程事件）。
- **客户 360 聚合**：`GET /api/crm/customer-360/{code}` 一次返回客户、联系人、商机、报价、合同、应收、客诉七段。
- **前端页面**（`apps/web-portal/src/modules/crm/`）：`Customer360View.vue`（`/crm/customers`）、`CrmP2View.vue` 按 `kind` 复用出 `/crm/leads`、`/crm/opportunities`、`/crm/quotations`、`/crm/contracts`、`/crm/forecast`、`/crm/complaints` 六个页面。

### 分层文件统计

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `domain/` | **0 — 未实现** | 阶段白名单、跟进类型、风险阈值全是方法体字面量 |
| `entity/` | 2 | `Opportunity`（`src_crm.crm_opportunity`）、`OpportunityFollow`（`src_crm.crm_opportunity_follow`） |
| `repo/` | 2 | `OpportunityRepository`、`OpportunityFollowRepository` |
| `service/` | 1 | `CrmP2Service`（线索/报价/合同/客诉/预测/客户 360 全在一个类） |
| `controller/` | 2 | `OpportunityController`（商机 + 跟进）、`CrmP2Controller`（其余全部） |
| `workflow/` | **0 — 未实现** | 回调改放在 `callback/`：`CrmApprovalCallback` |
| `integration/` | **0 — 未实现** | 事件发布内联在 `CrmP2Service`，无集成层包 |
| `query/` | **0 — 未实现** | 列表/预测/客户 360 的 SQL 全部内联在 `CrmP2Service` |
| 其他 | 1 | `package-info.java` |

## 接口清单

`CrmP2Controller` · `@RequestMapping("/api/crm")`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/crm/leads` | 线索分页（`keyword` 匹配 `lead_no,customer_name,contact_name`；`status` 精确匹配） |
| POST | `/api/crm/leads` | 新建线索（状态 `NEW`，`owner_user` 默认当前用户） |
| POST | `/api/crm/leads/{id}/assign` | 分配线索（写 `crm_lead_assignment` 留痕 → 改负责人 → 置 `FOLLOWING`） |
| POST | `/api/crm/leads/{id}/follow` | 线索跟进（写 `crm_lead_follow` → 置 `FOLLOWING`） |
| POST | `/api/crm/leads/{id}/convert` | 线索转商机（校验客户 `PUBLISHED`、防重复转换、回填 `converted_opportunity_no`） |
| GET | `/api/crm/quotations` | 报价分页 |
| POST | `/api/crm/quotations` | 新建报价（明细必填、产品须 `PUBLISHED`、数量 > 0，自动算折扣率/毛利率） |
| POST | `/api/crm/quotations/{id}/new-version` | 报价复制升版本（新版本状态 `DRAFT`） |
| POST | `/api/crm/quotations/{id}/submit` | 提交报价审批（仅 `DRAFT` / `REJECTED`；按风险阈值选 `QUOTATION` 或 `QUOTATION_RISK`） |
| POST | `/api/crm/quotations/{id}/activate` | 报价生效（仅 `APPROVED`；同编号其他生效版本置 `EXPIRED`） |
| GET | `/api/crm/contracts` | 合同分页 |
| POST | `/api/crm/contracts` | 由 `EFFECTIVE` 报价生成合同（回款计划合计须等于合同金额） |
| POST | `/api/crm/contracts/{id}/submit` | 提交合同审批（`DRAFT` / `REJECTED` → `PENDING`） |
| POST | `/api/crm/contracts/{id}/activate` | 合同生效（仅 `APPROVED`）并发布 `CRM.CONTRACT.ACTIVATED` |
| POST | `/api/crm/contracts/{id}/create-order` | 生成 ERP 销售订单（头来自合同、行来自报价明细），发布 `CRM.CONTRACT.ORDER_REQUESTED` 与 `ERP.SALES_ORDER.CREATED`；已生成过则直接返回既有订单（幂等） |
| POST | `/api/crm/contracts/{id}/new-version` | 合同变更新版本（仅 `ACTIVE` / `APPROVED`，变更原因必填，写 `crm_contract_change`） |
| GET | `/api/crm/complaints` | 客诉分页 |
| POST | `/api/crm/complaints` | 登记客诉（状态固定 `OPEN`） |
| GET | `/api/crm/forecast` | 销售预测聚合（按预计签约月 / 负责人 / 部门，含加权金额，排除 `LOST`） |
| GET | `/api/crm/customer-360/{code}` | 客户 360 聚合（客户、联系人、商机、报价、合同、应收、客诉） |

`OpportunityController` · `@RequestMapping("/api/crm/opportunities")`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/crm/opportunities` | 商机分页（JPA `findAll`，`size` 上限 200） |
| POST | `/api/crm/opportunities` | 新建商机（编号查重 → MDM 客户须已发布 → `master.assertConsumable(c, "CRM 商机")` → 回填客户名/负责人/部门） |
| POST | `/api/crm/opportunities/{id}/stage` | 推进阶段（白名单；`WON` 需存在生效报价；`LOST` 必填原因） |
| GET | `/api/crm/opportunities/{id}/follows` | 跟进记录（按 `follow_at` 倒序） |
| POST | `/api/crm/opportunities/{id}/follows` | 新增跟进（类型白名单 `CALL/VISIT/EMAIL/MEETING`） |

**访问控制**：`SecurityConfig` 对 `/api/crm/**` 要求 authority `SYSTEM_CRM`（来自 `sys_user_system`）。**两个 CRM Controller 均无任何 `@PreAuthorize`**，即业务权限点未落到接口上（见验收第 1 项）。

## 数据表

**`@Table` 注解中的库名.表名（JPA 实体）**

| 库.表 | 实体 |
|---|---|
| `src_crm.crm_opportunity` | `Opportunity` |
| `src_crm.crm_opportunity_follow` | `OpportunityFollow` |

**JdbcTemplate / 原生 SQL 触及的表**

| 库.表 | 读/写 | 位置 | 建表来源 |
|---|---|---|---|
| `src_crm.crm_lead` | 读写 | `CrmP2Service` | `V18__p2_crm_lead_customer360.sql` |
| `src_crm.crm_lead_follow` | 写 | 同上 | `V18` |
| `src_crm.crm_lead_assignment` | 写 | 同上 | `V18` |
| `src_crm.crm_opportunity` | 读写（`convert()` 插入、`stage()` 直写 `lost_reason`） | 同上 + `OpportunityController` | `04_business_systems.sql` + `V19` ALTER |
| `src_crm.crm_quotation` / `crm_quotation_line` | 读写 | `CrmP2Service` | `V20__p2_crm_quotation.sql` |
| `src_crm.crm_contract` | 读写 | 同上 | `V21__p2_crm_contract.sql` + `V26` ALTER |
| `src_crm.crm_payment_plan` | 读写 | 同上 | `V21` |
| `src_crm.crm_contract_change` | 写 | 同上 | `V21` |
| `src_crm.crm_complaint` | 读写 | 同上 | `V18` |
| `src_mdm.md_customer` | 读（`convert()` 校验发布态并取客户名、客户 360） | 同上 | `02_master_data.sql` |
| `src_mdm.md_product` | 读（报价产品必须已发布） | 同上 | `02_master_data.sql` |
| `src_mdm.md_customer_contact` | 读（客户 360 联系人） | 同上 | **无建表语句 —— 全仓库 SQL 中不存在该表** |
| `src_erp.erp_sales_order` / `erp_sales_order_line` | 写（`createOrder()` 建单） | 同上 | `04_business_systems.sql` |
| `src_erp.erp_receivable` | 读（客户 360 应收段） | 同上 | `04_business_systems.sql` |
| `mfg_ops.biz_outbox` / `biz_inbox` | 写（经 `BusinessEventService.publish` + 定时 `dispatch`） | `BusinessEventService` | `V26` |

> **建表但无人使用的三张表**（`V19`）：`src_crm.crm_opportunity_product`、`src_crm.crm_competitor`、`src_crm.crm_sales_forecast` —— 全仓库代码中没有任何一处读写。

## 对照最低验收

| 验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| 1. 至少 5 个本职模块具有实体、服务、接口和角色权限 | **部分实现** | 有服务 + 接口 + 表的模块：线索、商机、报价、合同、客诉、销售预测、客户 360——7 个本职模块全部有接口，服务层 `CrmP2Service` 一个类承载。**实体层只有 2 个 JPA 实体**（`Opportunity`、`OpportunityFollow`），线索/报价/合同/客诉是纯 SQL + `Map`。角色权限：`08_seed_data.sql:128-132` 定义 `CRM:OPPORTUNITY:VIEW/CREATE/STAGE/CONVERT/PRICE`，`V25` 追加 `CRM:LEAD:CREATE`、`CRM:LEAD:ASSIGN`、`CRM:OPPORTUNITY:STAGE`、`CRM:QUOTATION:SUBMIT`、`CRM:CONTRACT:ACTIVATE`，`09_role_permission.sql:117-133` 授予 `SALES_REP`、`SALES_ASSISTANT`、`SALES_SUPERVISOR`、`SALES_DIRECTOR`。**缺口**：CRM 两个 Controller **零 `@PreAuthorize`**，角色权限没有落到接口，实际只有 `/api/crm/** → SYSTEM_CRM` 的系统级门禁 |
| 2. 至少 3 类核心单据可以从创建流转到关闭或作废 | **部分实现** | 3 类单据可流转：线索（`NEW` → `FOLLOWING` → `CONVERTED`）、报价（`DRAFT` → `PENDING` → `APPROVED` → `EFFECTIVE` → `EXPIRED`，驳回 `REJECTED`）、合同（`DRAFT` → `PENDING` → `APPROVED` → `ACTIVE`，变更走新版本 + `previous_contract_id`）。**缺口**：三类都**没有关闭/作废**——`TERMINATED`、`CANCELED` 只出现在「该报价是否已关联有效合同」的排除条件里，没有任何代码写入；线索的失效（`invalid_reason`）也没有路径 |
| 3. 至少 1 个审批流程和 1 个异常处理闭环 | **部分实现** | 审批流程**有三条**：`CRM_QUOTATION_APPROVAL`（销售主管一级，`V25`）、`CRM_QUOTATION_RISK_APPROVAL`（销售总监 + 财务主管两级，`V26`）、`CRM_CONTRACT_APPROVAL`（销售总监 + 财务主管两级，`V25`），统一回调 `CrmApprovalCallback`。**异常处理闭环未实现**：客诉只登记为 `OPEN`，无转派、无关联 QMS 8D/CAPA、无闭环评价；信用例外与 ATP 例外的闭环在 ERP 侧，不在 CRM |
| 4. 至少 2 个向其他系统发送或消费的幂等业务事件 | **已实现** | 3 个事件经 `mfg_ops.biz_outbox`：`CRM.CONTRACT.ACTIVATED`（crm→erp）、`CRM.CONTRACT.ORDER_REQUESTED`（crm→erp）、`ERP.SALES_ORDER.CREATED`（erp→crm）。**幂等机制**：`event_id` 用 UUID 生成，`biz_outbox` 与 `biz_inbox` 均有唯一键 `(event_id, target_system)`，投递用 `INSERT IGNORE`，失败重试 3 次后置 `DEAD` 并可经 `retry()` 人工重放。**需注意**：仓库内没有读取 `biz_inbox` 执行业务动作的消费者，事件是「可观测 + 可重放」的投递记录；跨系统的实际业务效果（ERP 订单落库）由 CRM **直接跨库写 `src_erp`** 完成 |
| 5. 至少 1 页本系统运营查询或统计报表 | **已实现** | `GET /api/crm/forecast` 销售预测聚合（按预计签约月 / 负责人 / 部门汇总金额与加权金额）与 `GET /api/crm/customer-360/{code}` 客户 360 聚合；前端对应 `/crm/forecast` 与 `/crm/customers` 两个页面，另有 5 个单据台账页 |

## 未实现 / 缺口

1. **接口无业务权限校验** —— `CrmP2Controller`、`OpportunityController` 均无 `@PreAuthorize`；权限点只存在于 `sys_permission` 与角色映射表，任何拥有 `SYSTEM_CRM` 的账号可执行全部动作（含合同生效、生成 ERP 订单）。
2. **`src_mdm.md_customer_contact` 表不存在** —— `customer360()` 的联系人段必然报错；全仓库 SQL 里没有该表的建表语句。
3. **三张建了不用的表** —— `crm_opportunity_product`、`crm_competitor`、`crm_sales_forecast`（均见 `V19`）无任何代码读写，对应「商机产品明细 / 竞争对手 / 预测快照」三个能力未实现。
4. **客诉无闭环** —— `crm_complaint` 的 `solution`、`customer_feedback`、`closed_at` 三个字段无人写入；无转派、无 QMS 8D/CAPA 关联、无闭环评价。
5. **合同缺终止 / 归档 / 续签**；线索缺失效动作。
6. **预测维度不全** —— `product_code` 在 SQL 中写死 `NULL`，无区域维度、无目标达成、无漏斗分析。
7. **无状态时间轴与操作审计** —— 单据没有状态变更历史，未关联 `wf_action_log`；无导出接口。
8. **实体与 SQL 字段不一致** —— `Opportunity` 实体未映射 `source_lead_no`、`probability`、`expected_close_date`、`lost_reason`、`contact_name`，这些列由 `convert()` 与 `stage()` 用 SQL 写入，因此走 JPA 的读接口看不到它们。
9. **`CrmP2Service` 单类过重** —— 一个类约 500 行承载 7 个业务模块；`list()` 用字符串拼接表名（虽有 `Set.of(...)` 白名单，仍是拼接）。
10. **无 `domain/`、`workflow/`、`integration/`、`query/` 分层** —— 回调放在非标准的 `callback/` 包。
11. **无自动化测试** —— `tests/unit`、`tests/integration`、`tests/e2e` 目录下只有 README，没有针对 CRM 的用例。
