# crm/ — 客户关系管理（CRM）

> **整体计划** · 依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 CRM 章节

## 系统定位

CRM 在十系统中负责「客户与销售过程」这一段：**客户档案不归它所有**——它通过 `app-mdm` 依赖只读引用 MDM 已发布的 `md_customer`（`assertConsumable` 校验），自己拥有线索、商机、报价、合同、客诉这些销售过程单据，并在合同生效后把结果交给 ERP 生成销售订单。

所以它与 ERP 的边界是：**CRM 管到「合同生效 + 提出订单请求」为止，ERP 管信用、交期、发货、开票、回款。** CRM 里的商机不等于订单，赢单必须先进报价再进合同。

## 本职模块基线

依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 CRM 章节抄录，第三列为依据实际代码判断的当前状态。

| 模块 | 核心能力 | 当前状态 | 依据 |
|---|---|---|---|
| 客户 360 | 客户档案只读引用、联系人、拜访、标签、客户分级 | **部分实现** | `GET /api/crm/customer-360/{code}`（`CrmP2Service.customer360()`）一次聚合 MDM 客户、MDM 联系人、CRM 商机/报价/合同/客诉、ERP 应收，前端 `/crm/customers`（`Customer360View.vue`）。**缺口**：其中联系人一段查 `src_mdm.md_customer_contact`，而该表在本仓库任何建表 SQL 中都不存在，该字段会直接抛异常；拜访、标签、客户分级未实现 |
| 线索管理 | 线索录入、分配、跟进、转商机、失效原因 | **部分实现** | 录入 `createLead()`、分配 `assign()`（写 `crm_lead_assignment` 留痕并把负责人改过去）、跟进 `follow()`（写 `crm_lead_follow` 并置 `FOLLOWING`）、转商机 `convert()`（校验客户已发布 → 从 `md_customer` 取值插入 `crm_opportunity` → 线索置 `CONVERTED` 并回填 `converted_opportunity_no`，查重防重复转换）。**失效原因未实现**：`crm_lead.invalid_reason` 字段存在但无任何代码写入，也没有「标记无效」动作 |
| 商机管理 | 阶段、预计金额、赢单率、竞争对手、跟进记录、预测 | **部分实现** | `Opportunity` 实体 + 阶段推进接口（合法阶段白名单 `LEAD/QUALIFY/PROPOSAL/NEGOTIATE/WON/LOST`；**赢单前必须存在 `EFFECTIVE` 报价**，丢单必填原因并写 `lost_reason`）；跟进记录 `OpportunityFollow` + 按时间倒序查询。**缺口**：竞争对手表 `crm_competitor`（V19 建表）无任何代码读写；赢单率 `win_rate` 只在预测里被当权重读，没有维护入口 |
| 报价管理 | 报价单、价格版本、折扣、报价审批、有效期、转合同 | **已实现**（相对最完整） | `createQuote()` 逐行校验 MDM 产品已发布（`md_product.status='PUBLISHED'`）、数量 > 0，并**自动计算折扣率与毛利率**（`(1 - 报价额/标准价)*100`、`(报价额-成本)/报价额*100`）；版本管理 `newVersion()` 复制头行升版本；审批按风险分流（折扣 ≥ 10% 或毛利率 < 15% 或金额 ≥ 50 万走 `QUOTATION_RISK` 两级，否则 `QUOTATION` 一级）；`activateQuote()` 生效时把同编号旧版本批量置 `EXPIRED` |
| 合同管理 | 合同、交付条款、回款计划、变更、续签、归档 | **部分实现** | `createContract()` 只允许由 `EFFECTIVE` 报价转入（并校验该报价未关联有效合同），**回款计划合计必须严格等于合同金额**否则拒绝；金额与税额从报价继承；审批 `CONTRACT` 两级（销售总监 + 财务主管）；`activate()` 生效后**发布事件** `CRM.CONTRACT.ACTIVATED`；`newContractVersion()` 生成新版本并写 `crm_contract_change` 留痕、`previous_contract_id` 指向旧版本；`createOrder()` 生成 ERP 销售订单。**缺口**：续签、终止、归档未实现——`TERMINATED` / `CANCELED` 只出现在关联校验的排除条件里，没有任何代码把合同置成这两个状态 |
| 销售预测 | 按客户/产品/区域/销售员预测、目标达成、漏斗分析 | **部分实现** | `GET /api/crm/forecast`（`CrmP2Service.forecast()`）按「预计签约月 + 负责人 + 部门」聚合 `expect_amount` 与「金额 × 概率」的加权预测，排除 `LOST`，内存分页。**缺口**：`product_code` 在 SQL 里写死为 `NULL`（未按产品维度拆分）、无区域维度、无目标达成、无漏斗分析；V19 建的 `crm_sales_forecast` 表完全没有被使用 |
| 客诉协同 | 客诉登记、责任转派、关联 QMS 8D/CAPA、闭环评价 | **部分实现** | 仅 `createComplaint()`：登记客诉（`complaint_no` / 客户 / 销售订单 / 交货单 / 产品 / 批次 / 严重度 / 问题描述 / 责任部门），状态固定为 `OPEN`。**转派、关联 QMS 8D/CAPA、处理结论回填、闭环评价、关闭全部未实现**——`crm_complaint` 表里的 `solution`、`customer_feedback`、`closed_at` 三个字段无任何代码写入 |

## 代码分层标准

本项目 `source-apps/*` 统一按 8 个分层组织；逐层核对本模块实际情况如下。

| 分层 | 目录 | 本模块实际 | 说明 |
|---|---|---|---|
| 领域规则 | `domain/` | **空 — 未实现** | 没有枚举、没有状态机类。阶段白名单、跟进类型白名单、风险阈值（折扣 10% / 毛利 15% / 金额 50 万）全部是 `CrmP2Service` / `OpportunityController` 方法体内的字面量 |
| 单据实体 | `entity/` | 2 个类 | `Opportunity`（`@Table(name="crm_opportunity", catalog="src_crm")`）、`OpportunityFollow`（`crm_opportunity_follow`）。线索、报价、合同、回款计划、客诉**没有 JPA 实体**，走 `JdbcTemplate` + `Map<String,Object>` |
| 数据访问 | `repo/` | 2 个接口 | `OpportunityRepository`（`existsByOpportunityNo`）、`OpportunityFollowRepository`（`findByOpportunityNoOrderByFollowAtDesc`） |
| 业务服务 | `service/` | 1 个类 | `CrmP2Service` 一个类承载线索/商机/报价/合同/客诉/预测/客户 360 全部 P2 业务，约 500 行（含内联 SQL） |
| 独立维护接口 | `controller/` | 2 个类 | `OpportunityController`（商机 + 跟进，JPA 路径）、`CrmP2Controller`（线索/报价/合同/客诉/预测/客户 360，HTTP 薄壳直调 service） |
| 审批策略与回调 | `workflow/` | **空 — 未实现** | 回调被放在**非标准的 `callback/` 包**：`CrmApprovalCallback`（`supports()` 匹配 `QUOTATION*` 与 `CONTRACT`）。审批链本身在 SQL 种子 `mfg_auth.wf_definition` / `wf_node`（`V25`、`V26`） |
| 上下游集成 | `integration/` | **空 — 未实现** | 有集成**动作**但无集成**层**：事件发布内联在 `CrmP2Service.activate()` 与 `createOrder()` 中调用 `BusinessEventService.publish(...)`；事件只是投递记录，仓库内没有读取收件箱执行业务动作的消费者 |
| 查询与报表 | `query/` | **空 — 未实现** | 列表/预测/客户 360 的 SQL 全部内联在 `CrmP2Service` 里，其中 `list()` 用字符串拼表名（带 `Set.of(...)` 白名单防注入） |

> 三个非标准点值得记录：① 回调包名是 `callback/` 而非标准 `workflow/`；② 实体层只覆盖商机与跟进，其余单据是纯 SQL，因此**同一个业务对象在 JPA 与 JDBC 两条路径间没有共享的领域模型**；③ 跨系统动作（写 ERP 销售订单、写 ERP 应收查询）直接跨库读写 `src_erp`，不是通过事件由 ERP 自己完成。

## 最低验收（五项）

抄录 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 末尾「系统本职完成的最低验收」五项：

1. 至少 5 个本职模块具有实体、服务、接口和角色权限；
2. 至少 3 类核心单据可以从创建流转到关闭或作废；
3. 至少 1 个审批流程和 1 个异常处理闭环；
4. 至少 2 个向其他系统发送或消费的幂等业务事件；
5. 至少 1 页本系统运营查询或统计报表。
