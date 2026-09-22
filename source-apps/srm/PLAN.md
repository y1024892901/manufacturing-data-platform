# srm/ — 供应商与采购协同

> **整体计划** · 依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 SRM：供应商与采购协同 章节

## 系统定位

SRM 是十系统主线的第 5 环：向上承接 ERP 的生产订单与采购需求（`srm_purchase_order.prod_order_no`、`srm_rfq.source_requisition_no`），向下把供应商交付推给 WMS 与 QMS（ASN 到货生成 `wms_receipt` 与 `qms_inspection`）。它管的是「**买什么、向谁买、什么时候到、到得好不好**」，不承担库存记账（WMS 负责）和检验判定（QMS 负责）。

按 [implementation-roadmap.md](../../docs/implementation-roadmap.md)，SRM 属于 **P3：采购、仓储、质量闭环**，验收目标是「ASN 收货自动生成待检库存；QMS 结果驱动可用、隔离或退供库存」。

## 本职模块基线

摘自 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 SRM 章节，右侧为按 2026-09-22 实际代码判断的完成度（详见 [STATUS.md](STATUS.md)）。

| 模块 | 核心能力 | 当前状态 |
|---|---|---|
| 供应商生命周期 | 准入、资质、认证、黑名单、分级、年度复审 | 部分实现 |
| 寻源与询报价 | RFQ、供应商报价、比价、定标、审批、合同价格 | 部分实现 |
| 采购订单协同 | 订单确认、变更、交付承诺、ASN、到货、退供 | 部分实现 |
| 供应商绩效 | 准时交付、来料合格率、价格、响应时效、综合评分 | 部分实现 |
| 质量协同 | 供应商不合格、8D、整改、验证、关闭 | 部分实现 |
| 对账协同 | 对账单、发票、付款状态查询 | 未实现 |

判断依据：6 个模块都有真实表（`srm_onboarding`、`srm_rfq`、`srm_supplier_quote`、`srm_asn`、`srm_supplier_quality`、`srm_supplier_performance`）和可用接口，但**资质、比价、订单变更、绩效算分、8D 关闭、对账**这些子能力在代码里找不到对应类或方法。对账协同连表都没有。

## 代码分层标准

本项目 `source-apps/<system>/` 统一分层，SRM 的实际落地情况：

| 分层 | 本项目标准职责 | SRM 现状 | 说明 |
|---|---|---|---|
| `domain/` | 状态机、枚举、领域规则 | **空** | SRM 的状态机不在这里，被抽到共享模块 `com.mfg.common.service.P3CrudService.rules()` 里，用 `Map<String,Set<String>>` 表达 |
| `entity/` | 单据、台账、配置实体 | 有 2 个 | `PurchaseOrder`、`SupplierDelivery`；P3 的 6 张表没有 JPA 实体，直接走 JdbcTemplate |
| `repo/` | 数据访问与领域查询 | 有 2 个 | `PurchaseOrderRepository`、`SupplierDeliveryRepository`，各带一个 `existsBy*No` 幂等判断 |
| `service/` | 业务动作、校验、事务边界 | **空** | SRM 无自己的 service；跨单据编排在共享的 `P3FlowService`（`awardRfq`、`arriveAsn`），通用增删查在共享的 `P3CrudService` |
| `controller/` | 独立维护 API | 有 2 个 | `PurchaseOrderController`（强类型单据流）、`SrmP3Controller`（泛型 Map 型 CRUD） |
| `workflow/` | 本系统审批策略与审批回调 | **空** | `srm_onboarding` 表预留了 `workflow_instance_id` 列，但没有任何代码写入它；全仓 `*Callback` 只有 mdm/crm/erp/plm 四家，SRM 不在其中 |
| `integration/` | 上下游事件、分发、幂等处理 | **空** | 事件发布借用共享的 `BusinessEventService.publish()`，SRM 目录下无集成代码 |
| `query/` | 列表、详情、台账、统计查询 | **空** | 分页查询直接写在 controller 里；无统计报表接口 |

**本模块独有的设计取舍**：SRM 同时存在两套接口风格——`PurchaseOrderController` 为采购订单/到货单写强类型实体接口（有字段级业务校验），而 6 个 P3 业务对象走 `SrmP3Controller` 的泛型 `Map<String,Object>` 接口（表名、可写列、状态机全由共享注册表驱动）。好处是新表接入零代码；代价是编译期无类型保护，字段名靠 `P3CrudService.cols()` 手写维护。

## 最低验收（五项）

抄录 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 末尾「系统本职完成的最低验收」：

1. 至少 5 个本职模块具有实体、服务、接口和角色权限；
2. 至少 3 类核心单据可以从创建流转到关闭或作废；
3. 至少 1 个审批流程和 1 个异常处理闭环；
4. 至少 2 个向其他系统发送或消费的幂等业务事件；
5. 至少 1 页本系统运营查询或统计报表。

SRM 当前的逐条对照与缺口，见 [STATUS.md](STATUS.md#对照最低验收)。
