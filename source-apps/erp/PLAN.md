# erp/ — 订单、计划、财务与经营核算

> **整体计划** · 依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 ERP 章节

## 系统定位

ERP 是十系统中「接单 → 计划 → 结算」的中枢：上游消费 CRM 转来的销售订单，以及 MDM 已发布的客户、物料、BOM；下游向 MES 下生产订单、向 SRM 提采购申请。它同时持有全项目分析的两类核心对象——`erp_prod_order`（延期风险模型的主体）与 `erp_fin_voucher`（F01 唯一性 / F02 借贷平衡的检测对象）。

按 [docs/implementation-roadmap.md](../../docs/implementation-roadmap.md)，本模块归属 **P2「市场到订单、订单到计划」**，当前代码即该阶段的落地结果；P4 引入 MES/EAM/能源后的联动尚未回写到 ERP。

## 本职模块基线

下表「模块 / 核心能力」两列抄录自 `system-functional-catalog.md` 的 ERP 章节，第三列为依据本模块实际代码的判定。

| 模块 | 核心能力 | 当前状态 |
|---|---|---|
| 销售订单 | 订单、订单行、变更、取消、交期承诺、信用检查、ATP | 部分实现 |
| 生产计划 | MRP 建议、净需求、采购申请、生产计划、产能负荷 | 部分实现 |
| 生产订单 | 下达、齐套检查、领料需求、进度回写、完工入库、关闭 | 部分实现 |
| 采购协同 | 请购、采购申请审批、采购订单状态、到货与发票三单匹配 | 部分实现 |
| 应收管理 | 开票、应收、账龄、核销、回款、信用占用 | 部分实现 |
| 应付管理 | 供应商发票、应付、付款申请、付款、核销 | 部分实现 |
| 总账与成本 | 凭证、科目余额、成本中心、订单成本、标准/实际差异 | 部分实现 |
| 经营分析 | 收入、毛利、订单利润、预算执行、现金回款 | 部分实现 |

逐项落点与缺口：

| 模块 | 已有实现 | 缺口 |
|---|---|---|
| 销售订单 | `SalesOrder` + `SalesOrderLine` 实体；`SalesOrderService.create()` 建单并逐行校验物料；`ErpP2Service.credit()/atp()/acceptAtp()/change()/cancel()/confirm()` | 变更只改表头 `delivery_date`/`remark`，无订单行级增删改量；ATP 快照为覆盖式重算（先 `DELETE` 再插入），无历史留痕 |
| 生产计划 | `erp_mrp_run`/`erp_mrp_requirement`/`erp_plan_suggestion`/`erp_purchase_requisition` 四表全套；`executeMrp()` 递归展 BOM；`confirmSuggestion()` 转请购单或生产订单 | 产能负荷（无工作中心负荷表与负荷计算）；计划重排与锁单 |
| 生产订单 | `ProductionOrder` 实体；`ProductionOrderService.create()/release()`；`ErpP2Service.kitCheck()`；`ERP.PRODUCTION_ORDER.RELEASED` 事件 | 领料需求（无领料单）；进度回写（无接收 MES 报工的入口）；完工入库；关闭（权限码 `ERP:PROD_ORDER:CLOSE` 已入库但无对应接口） |
| 采购协同 | `erp_purchase_requisition` 由 MRP 建议转出，并发布 `ERP.PURCHASE_REQUISITION.CREATED` → srm | 采购申请审批（不走审批引擎）；采购订单状态、到货、发票三单匹配（采购订单主体在 SRM，ERP 侧无接口） |
| 应收管理 | `invoice()` 开票并自动建应收 + 凭证；`receipt()` 回款 + 凭证；`settle()` 核销并回写 `erp_customer_credit.used_amount`；`ERP.RECEIPT.SETTLED` 事件 | 账龄分析（`erp_receivable.due_date` 已有，但无账龄分档查询/报表） |
| 应付管理 | `createPayable()` 建档；`erp_payable` 分页列表 | 供应商发票、付款申请、付款执行、应付核销 |
| 总账与成本 | `FinancialVoucher` 实体 + `FinancialVoucherController`（借贷平衡、期间内凭证号唯一）；`createVoucher()` 由开票/回款/核销自动生成凭证；`margin()` 订单毛利 | 科目余额表；成本中心归集（`erp_md_cost_center` 是只读副本，无任何引用逻辑）；标准/实际差异 |
| 经营分析 | `margin()` → `GET /api/erp/order-costs/{no}` | 预算执行、现金回款报表；订单利润只有单笔查询，无列表化视图 |

## 代码分层标准

本项目 `source-apps` 的统一分层为 `domain/ entity/ repo/ service/ controller/ workflow/ integration/ query/`。erp/ 实际落位：

| 标准分层 | 本模块实际 | 说明 |
|---|---|---|
| `domain/` | **未建立** | 无状态枚举类。`"DRAFT"` / `"CONFIRMED"` / `"CREATED"` / `"RELEASED"` / `"CANCELED"` 等状态以字符串字面量散落在 `SalesOrderService`、`ProductionOrderService`、`ErpP2Service` 中，校验分支也是 `if(!"DRAFT".equals(...))` 硬编码 |
| `entity/` | **已建立**，4 个 | `SalesOrder`、`SalesOrderLine`、`ProductionOrder`、`FinancialVoucher`，全部 `catalog="src_erp"` |
| `repo/` | **已建立**，4 个 | 均为 `JpaRepository`，仅 `SalesOrderLineRepository.findBySalesOrderNoOrderByLineNo()` 一个领域查询方法，其余只有 `existsByXxx` 幂等判重 |
| `service/` | **已建立**，3 个 | `SalesOrderService`、`ProductionOrderService` 走 JPA 实体路径；`ErpP2Service` 是 JdbcTemplate 直写路径 |
| `controller/` | **已建立**，4 个 | `SalesOrderController`、`ProductionOrderController`、`FinancialVoucherController`、`ErpP2Controller` |
| `workflow/` | **未建立该包** | 审批接入放在非标准的 `callback/ErpApprovalCallback.java`，实现 shared-workflow 的 `ApprovalCallback`，`supports()` 仅认 `"CREDIT_EXCEPTION"` 一种业务类型 |
| `integration/` | **未建立该包** | 出向事件调用 shared-common 的 `BusinessEventService.publish()`，共 6 处调用点；**无任何入向消费者** |
| `query/` | **未建立该包** | 列表查询内联在 `ErpP2Service.list()` 的白名单表机制里（8 张表 + 每表固定状态列），无独立查询层 |

另有两个不在标准分层内、但本模块真实拥有的包：`dto/`（2 个 record 命令对象）与 `callback/`。

### 关键设计取舍：两种持久化风格并存

`ErpP2Service` 用 `JdbcTemplate` + 裸 SQL 实现信用、ATP、MRP、财务，与 `SalesOrderService` 的 JPA 实体路径并存。这不是疏漏，而是本模块当前的取舍：

- **JPA 路径**负责「有实体、需校验主数据」的轻量单据（销售订单、生产订单、凭证）；
- **JdbcTemplate 路径**负责「跨库联查 + 逐列计算 + 大量动态列」的重流程（MRP 展开、ATP 撮合、核销），用实体映射反而更笨重。

代价也很直接：`list()` 把表名拼进 SQL，因此必须靠 `Set.of(...)` 白名单挡住注入——这一处是全模块唯一需要重点复核的拼接点。

### 关键设计取舍：主数据校验只覆盖一半接口

`SalesOrderService.create()` 与 `ProductionOrderService.create()` 会经 `MasterDataService.assertConsumable()` 校验客户/物料/BOM 是否 `PUBLISHED`，即真正执行了 MDM 的「只有已发布主数据可被消费」规则。但 `ErpP2Service` 的全部接口绕过该规则直读 `src_mdm`。同时，取数口径也不一致：MRP 展 BOM 读的是 ERP 本地副本 `src_erp.erp_md_bom`，而物料与生产版本读的是主数据源 `src_mdm.md_material`、`src_mdm.md_production_version`。**同一模块内两套主数据口径并存，是当前最需要收敛的技术债。**

## 最低验收（五项）

抄录自 `system-functional-catalog.md` 末尾「系统本职完成的最低验收」：

1. 至少 5 个本职模块具有实体、服务、接口和角色权限；
2. 至少 3 类核心单据可以从创建流转到关闭或作废；
3. 至少 1 个审批流程和 1 个异常处理闭环；
4. 至少 2 个向其他系统发送或消费的幂等业务事件；
5. 至少 1 页本系统运营查询或统计报表。

以上五项的逐条对照与实际证据，见 [STATUS.md](STATUS.md) 的「对照最低验收」章节。
