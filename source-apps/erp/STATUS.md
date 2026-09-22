# erp/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

ERP 已打通「销售订单 → 信用检查 → ATP 交期承诺 → MRP 展开 → 生产订单下达 / 请购转出 → 开票 → 应收 → 回款核销 → 自动凭证」的单据主链（P2 阶段目标达成），但生产订单只走到「下达」、凭证无审核流转、8 个本职模块中**没有任何一类单据能走到关闭**，且 Java 侧无一处操作级权限校验。

## 已实现

### 分层文件统计

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `domain/` | 0 | 未建立（无状态枚举，状态用字符串字面量） |
| `entity/` | 4 | `SalesOrder`、`SalesOrderLine`、`ProductionOrder`、`FinancialVoucher` |
| `repo/` | 4 | `SalesOrderRepository`、`SalesOrderLineRepository`、`ProductionOrderRepository`、`FinancialVoucherRepository` |
| `service/` | 3 | `SalesOrderService`、`ProductionOrderService`、`ErpP2Service`（JdbcTemplate 直写，约 400 行） |
| `controller/` | 4 | `SalesOrderController`、`ProductionOrderController`、`FinancialVoucherController`、`ErpP2Controller` |
| `callback/`（非标准层） | 1 | `ErpApprovalCallback`（仅支持 `CREDIT_EXCEPTION`） |
| `dto/`（非标准层） | 2 | `SalesOrderCommand`（含内嵌 `Line`）、`ProductionOrderCommand` |
| `workflow/` | 0 | 未建立 |
| `integration/` | 0 | 未建立（出向事件走 shared-common 的 `BusinessEventService`） |
| `query/` | 0 | 未建立（列表查询内联在 `ErpP2Service.list()`） |
| **合计** | **19** | 含 `package-info.java` |

## 接口清单

全部 32 个端点，逐个取自 4 个 `@RestController` 的注解。`ErpP2Controller` 挂在 `/api/erp` 下，其余三个各有独立前缀。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/erp/sales-orders` | 销售订单分页（JPA `findAll`） |
| GET | `/api/erp/sales-orders/{id}` | 订单详情，返回 `header` + `lines` 两段 |
| POST | `/api/erp/sales-orders` | 建单：校验订单号唯一、客户已发布、逐行物料已发布，自动累计 `total_amount` |
| POST | `/api/erp/sales-orders/{id}/confirm` | 确认订单（要求信用通过 + ATP 已承诺） |
| POST | `/api/erp/sales-orders/{id}/credit-check` | ★ 信用检查：懒初始化客户额度后判定 `PASSED`/`EXCEPTION` |
| POST | `/api/erp/sales-orders/{id}/credit-exception` | 发起信用例外审批（走 `ApprovalEngine`） |
| POST | `/api/erp/sales-orders/{id}/atp` | ★ ATP 交期承诺：按行算可用量，给 `FULL`/`PARTIAL` 与承诺日期 |
| POST | `/api/erp/sales-orders/{id}/atp/accept` | 接受承诺；分批交付时必须填例外原因 |
| POST | `/api/erp/sales-orders/{id}/change` | 订单变更（写 `erp_sales_order_change`，重置信用/ATP 为 `UNCHECKED`） |
| POST | `/api/erp/sales-orders/{id}/cancel` | 订单取消（原因必填） |
| GET | `/api/erp/credits` | 客户信用额度列表（白名单表 `erp_customer_credit`） |
| POST | `/api/erp/mrp/runs` | 新建 MRP 运行 |
| GET | `/api/erp/mrp/runs` | MRP 运行列表 |
| POST | `/api/erp/mrp/runs/{id}/execute` | ★ 执行 MRP：递归展 BOM，产净需求与建议 |
| GET | `/api/erp/mrp/suggestions` | 计划建议列表（`erp_plan_suggestion`） |
| POST | `/api/erp/mrp/suggestions/{id}/confirm` | ★ 确认建议：转请购单（→srm 事件）或生产订单（→mes 事件） |
| GET | `/api/erp/production-orders` | 生产订单分页（JPA） |
| POST | `/api/erp/production-orders` | 建生产订单：校验产品已发布且存在当前 BOM |
| POST | `/api/erp/production-orders/{id}/release` | ★ 下达：要求齐套检查为 `READY`，发布 `ERP.PRODUCTION_ORDER.RELEASED` |
| GET | `/api/erp/production-orders/p2` | 生产订单列表（JdbcTemplate 版，与上一个端点并存） |
| GET | `/api/erp/production-orders/{id}/kit-check` | ★ 齐套检查：按 BOM 行算需求量对比 `erp_supply_snapshot` |
| GET | `/api/erp/financial-vouchers` | 凭证分页 |
| POST | `/api/erp/financial-vouchers` | 手工建凭证：借贷必相等、期间内凭证号唯一 |
| GET | `/api/erp/invoices` | 发票列表 |
| POST | `/api/erp/invoices` | ★ 开票：建发票 + 自动生成应收 + 自动凭证 |
| GET | `/api/erp/receipts` | 回款单列表 |
| POST | `/api/erp/receipts` | ★ 回款登记 + 自动凭证 |
| POST | `/api/erp/settlements` | ★ 核销：回款冲应收，回写信用占用，生成核销凭证，发布 `ERP.RECEIPT.SETTLED` |
| GET | `/api/erp/receivables` | 应收列表 |
| GET | `/api/erp/payables` | 应付列表 |
| POST | `/api/erp/payables` | 应付建档 |
| GET | `/api/erp/order-costs/{no}` | ★ 订单毛利：收入 − `erp_order_cost` 合计，返回毛利率 |

分页参数统一为 `page`（从 1 起）/`size`（上限 200），响应统一包在 `ApiResponse` 中。

## 数据表

### JPA 实体映射（`@Table` 注解）

| 库.表 | 实体类 |
|---|---|
| `src_erp.erp_sales_order` | `SalesOrder` |
| `src_erp.erp_sales_order_line` | `SalesOrderLine` |
| `src_erp.erp_prod_order` | `ProductionOrder` |
| `src_erp.erp_fin_voucher` | `FinancialVoucher` |

### JdbcTemplate 直读写的表（无实体类，SQL 写在 `ErpP2Service`）

| 库.表 | 用途 | 建表位置 |
|---|---|---|
| `src_erp.erp_customer_credit` | 客户信用占用台账 | `V22__p2_erp_order_credit_atp.sql` |
| `src_erp.erp_credit_check` | 信用检查结果 + 例外审批状态 | `V22`（`exception_*` 由 `V26` 追加） |
| `src_erp.erp_atp_snapshot` | ATP 逐行承诺快照 | `V22`（`exception_*` 由 `V26` 追加） |
| `src_erp.erp_supply_snapshot` | 供应快照（ATP 与齐套的共同数据源） | `V22` |
| `src_erp.erp_sales_order_change` | 订单变更/取消留痕 | `V22` |
| `src_erp.erp_mrp_run` | MRP 运行批次 | `V23__p2_erp_mrp.sql` |
| `src_erp.erp_mrp_requirement` | MRP 净需求明细 | `V23` |
| `src_erp.erp_plan_suggestion` | 计划建议（生产/采购） | `V23` |
| `src_erp.erp_purchase_requisition` | 采购申请单 | `V23` |
| `src_erp.erp_invoice` | 销售发票 | `V24__p2_erp_finance.sql` |
| `src_erp.erp_receipt` | 客户回款单 | `V24` |
| `src_erp.erp_settlement` | 核销记录 | `V24` |
| `src_erp.erp_payable` | 应付账款 | `V24` |
| `src_erp.erp_order_cost` | 订单成本归集（毛利的数据源） | `V24` |
| `src_erp.erp_md_bom` | BOM 只读副本（MRP 展开用） | `infra/db-init/10_missing_copies.sql` |
| `src_erp.erp_md_bom_line` | BOM 行只读副本 | 同上 |

另有 3 张只读引用的主数据表：`src_mdm.md_customer`（信用额度与已用额度）、`src_mdm.md_material`（安全库存、物料类型、基本单位）、`src_mdm.md_production_version`（生产版本，确认建议时取当版本）。

**注意**：`infra/db-init/04_business_systems.sql` 建立的 `erp_md_customer`、`erp_md_supplier`、`erp_md_material`、`erp_md_cost_center` 四张副本表，以及 `V13` 建立的 `erp_md_product`，在本模块 Java 代码中**均未被读取**——ERP 改为直读 `src_mdm`。这是「分发副本」与「直读源库」两种设计共存的现状。

## 对照最低验收

| 验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| 1. 至少 5 个本职模块具有实体、服务、接口和角色权限 | **部分实现** | 实体 + 服务 + 接口齐备的模块有 6 个（销售订单、生产订单、生产计划、应收、应付、总账）。权限方面：DB 侧种子完整——`08_seed_data.sql` 定义 14 个 `ERP:*` 权限码（`SALES_ORDER` 3 个、`PROD_ORDER` 4 个、`VOUCHER` 3 个、`RECEIVABLE` 2 个、`PAYABLE` 2 个），`09_role_permission.sql` 按岗位授予 `SALES_REP`/`SALES_DIRECTOR`/`PROD_PLANNER`/`PROD_MANAGER`/`CASHIER`/`AR_ACCOUNTANT`/`AP_ACCOUNTANT`/`COST_ACCOUNTANT`/`GL_ACCOUNTANT`/`FINANCE_DIRECTOR`（该角色拿全部 `ERP:%`）等角色；URL 级另有 `SecurityConfig` 的 `.requestMatchers("/api/erp/**").hasAuthority("SYSTEM_ERP")` 粗粒度门禁。**缺口：Java 代码中 0 处 `@PreAuthorize`**，14 个操作级权限码（如 `ERP:SALES_ORDER:CONFIRM`）入库但从未在接口上生效 |
| 2. 至少 3 类核心单据可以从创建流转到关闭或作废 | **未实现** | 只有 1 类合格：销售订单 `DRAFT`→（`change` 重置）→`CONFIRMED`→`ATP_COMMITTED`，可经 `cancel()` 变为 `CANCELED`，且变更/取消均落 `erp_sales_order_change` 留痕。生产订单仅 `CREATED`→`RELEASED`（`ProductionOrderService.release()`），无关闭接口；财务凭证创建后无审核/过账流转（`FinancialVoucherController` 只有 page/create）；MRP 运行 `CREATED`→`RUNNING`→`COMPLETED` 与建议 `PROPOSED`→`CONFIRMED` 是批次与技术状态，不构成单据闭环 |
| 3. 至少 1 个审批流程和 1 个异常处理闭环 | **已实现** | 审批流程：信用例外——`ErpP2Service.submitCreditException()` 调 `workflow.start(StartApprovalRequest.of("CREDIT_EXCEPTION", ...))`，回调由 `ErpApprovalCallback.onApproved()/onRejected()` 处理，回写 `erp_credit_check.exception_status` 与订单 `credit_status`。异常处理闭环：信用不足 → `EXCEPTION` 判定 → 例外审批 → 回写订单为 `CREDIT_PASSED`。另有简化闭环 `acceptAtp()`（分批交付需填例外原因，直接置 `APPROVED`，不走审批引擎） |
| 4. 至少 2 个向其他系统发送或消费的幂等业务事件 | **部分实现** | 发送侧达标：6 处 `BusinessEventService.publish()` 调用点，覆盖 `ERP.SALES_ORDER.CONFIRMED`→crm、`ERP.MRP.COMPLETED`→crm、`ERP.RECEIPT.SETTLED`→crm、`ERP.PURCHASE_REQUISITION.CREATED`→srm、`ERP.PRODUCTION_ORDER.CREATED`→mes、`ERP.PRODUCTION_ORDER.RELEASED`→mes。幂等由 `BusinessEventService` 保证：`event_id` 用 UUID，投递时 `INSERT IGNORE INTO mfg_ops.biz_inbox`，失败重试 3 次后置 `DEAD`。**缺口：消费侧为 0** —— 全仓库只有 `BusinessEventService` 自身读写 `biz_outbox`/`biz_inbox`，没有任何模块订阅处理 `ERP.*` 事件 |
| 5. 至少 1 页本系统运营查询或统计报表 | **已实现** | 统计查询：`ErpP2Service.margin()` → `GET /api/erp/order-costs/{no}`，返回收入、成本、毛利、毛利率。运营列表：`ErpP2Service.list()` 白名单 8 张表（信用、MRP 运行、计划建议、应收、发票、回款、应付、生产订单），每张表有固定的关键字列与状态列，统一排序分页 |

## 未实现 / 缺口

按影响面排序：

1. **无操作级权限校验**。14 个 `ERP:*` 权限码已入库并授给角色，但 Java 侧 0 处 `@PreAuthorize`；对比 QMS 模块已在该处落地 3 处。任何持有 `SYSTEM_ERP` 的用户可执行全部 32 个端点。
2. **无任何单据可关闭**。生产订单缺完工入库与关闭（`ERP:PROD_ORDER:CLOSE` 权限码已备但无接口）；财务凭证缺审核/过账；应付缺付款与核销。验收项 2 因此不达标。
3. **无入向事件消费者**。ERP 只发不收；`biz_inbox` 写入后无业务处理，跨系统联动目前靠同进程直调而非事件驱动。
4. **生产订单的进度回写与领料需求缺失**。无接收 MES 报工的入口，无从 ERP 侧下发的领料需求单。
5. **产能负荷未实现**。MRP 只做物料净需求，无工作中心负荷表与负荷计算。
6. **主数据口径不统一**。`MasterDataService.assertConsumable()` 的已发布校验只覆盖 `SalesOrderService`/`ProductionOrderService` 两个类；`ErpP2Service` 的全部接口绕过校验直读 `src_mdm`。BOM 读本地副本 `erp_md_bom`，物料与生产版本读源库 `src_mdm`，两者并存。
7. **JPA 实体落后于表结构**。`V22` 给 `erp_sales_order` 加了 `credit_status`/`atp_status`/`source_type`/`source_contract_no`/`tax_rate`/`factory_code`/`ship_to_address` 七列，`V27` 给 `erp_prod_order` 加了 `production_version_code`/`kit_status`/`kit_checked_at` 三列，但 `SalesOrder`、`ProductionOrder` 实体均未映射这些列，只能靠裸 SQL 读写（如 `release()` 直接 `SELECT kit_status`）。`erp_receivable`、`erp_invoice`、`erp_receipt`、`erp_settlement` 等 16 张表则完全没有实体类。
8. **ATP 快照无历史**。`atp()` 每次先 `DELETE FROM erp_atp_snapshot WHERE sales_order_no=?` 再重插，同一订单的历次承诺不可追溯。
9. **账龄分析未实现**。`erp_receivable` 已有 `due_date`，但无账龄分档查询或报表接口。
10. **业务异常直接返回框架异常**。`SalesOrderController.detail()` 抛 `EntityNotFoundException` 而非 `BizException`，与其余端点的错误契约不一致。
11. **无测试**。`erp/` 下无 `src/test` 目录；全仓库仅 `shared/security` 与 `wms` 各有一个测试类。
