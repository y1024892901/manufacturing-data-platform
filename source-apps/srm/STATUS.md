# srm/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**供应商准入、询报价定标、采购订单下达、ASN 到货四条线已经能跑通并落真实表、发真实事件；但对账协同完全没做，绩效算分、8D 闭环、订单变更、资质管理只有表没有逻辑。**

## 已实现

### 分层文件统计

`source-apps/srm/src/main/java/com/mfg/srm/` 共 7 个 Java 文件（6 个代码文件 + 1 个 `package-info.java`）：

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `domain/` | 0 | — |
| `entity/` | 2 | `PurchaseOrder`（`srm_purchase_order`）、`SupplierDelivery`（`srm_delivery`） |
| `repo/` | 2 | `PurchaseOrderRepository`、`SupplierDeliveryRepository` |
| `service/` | 0 | — |
| `controller/` | 2 | `PurchaseOrderController`、`SrmP3Controller` |
| `workflow/` | 0 | — |
| `integration/` | 0 | — |
| `query/` | 0 | — |

无测试文件（`src/test/` 不存在）。

**依赖的共享模块**（`pom.xml` 声明，代码实际用到）：

| 共享类 | 位置 | SRM 用它做什么 |
|---|---|---|
| `P3CrudService` | `source-apps/shared/common/.../service/P3CrudService.java` | 6 个 P3 对象的泛型分页/详情/创建/状态流转；内部维护表名注册表与状态机规则 |
| `P3FlowService` | 同目录 | `awardRfq()` 定标生成采购订单、`arriveAsn()` 到货生成收货单 |
| `BusinessEventService` | `.../integration/BusinessEventService.java` | 事件写 `mfg_ops.biz_outbox` + 定时分发 + 重试 |
| `CurrentUser` | `source-apps/shared/security/.../config/CurrentUser.java` | `purchase_user`、`created_by` 落当前登录人 |
| `BizException` / `ErrorCode` / `ApiResponse` | `source-apps/shared/common/...` | 统一错误码与响应包装 |

> `SrmP3Controller.java` 是本次新增文件，`git status` 显示尚未提交（未跟踪），但已编译进 `target/classes`。

## 接口清单

两个 controller 都挂在 `/api/srm` 前缀下，共 12 个处理方法。

### `PurchaseOrderController` — 强类型单据流

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/srm/purchase-orders` | 采购订单分页（`page`/`size`，size 上限 200） |
| POST | `/api/srm/purchase-orders` | 新建采购订单：校验 `purchaseOrderNo` 唯一 → 置 `CREATED` → `orderDate=今天` → `purchaseUser=当前用户` → `totalAmount=orderQty×unitPrice` 四舍五入 2 位 |
| POST | `/api/srm/purchase-orders/{id}/send` | 下达：仅 `CREATED` 可下达，置 `SENT` |
| GET | `/api/srm/deliveries` | 到货单分页 |
| POST | `/api/srm/deliveries` | 登记到货：校验 `deliveryNo` 唯一 → 按 `purchaseOrderNo` 找单 → 订单必须处于 `SENT`/`PARTIAL` → 回填 `supplierCode`/`materialCode`/`expectedDate` → `actualDate=今天` → 算 `delayDays=max(0, actual-expected)` |
| POST | `/api/srm/deliveries/{id}/inspect` | 质检回填：`qualifiedQty` 参数，不得大于 `deliveryQty`；`rejectedQty=delivery-qualified`；`inspectionStatus` 按比例落 `FAILED`（合格 0）/`PASSED`（全合格）/`PARTIAL` |

### `SrmP3Controller` — 泛型 P3 对象（`kind` 白名单：`onboarding`、`rfqs`、`quotes`、`asns`、`supplier-quality`、`performance`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/srm/{kind}` | 分页 + `keyword`（对 `P3CrudService.cols()` 声明的列做 `CONCAT_WS` 模糊匹配）+ `status` 过滤 |
| GET | `/api/srm/{kind}/{id}` | 单条详情（`SELECT *`） |
| POST | `/api/srm/{kind}` | 创建：字段名驼峰转下划线后，只保留 `information_schema` 里真实存在且非自增的列 |
| POST | `/api/srm/{kind}/{id}/status` | 状态流转：查 `P3CrudService.rules()` 白名单，非法迁移抛 `MASTER_DATA_INVALID_STATE` |
| POST | `/api/srm/rfqs/{id}/award` | **★ 定标**：`quoteId` 参数，见下方流程图 |
| POST | `/api/srm/asns/{id}/arrive` | **★ ASN 到货**：默认 `warehouseCode=WH01`，见下方流程图 |

`kind` 非法时抛 `IllegalArgumentException("不支持的SRM对象")`。

### 两个跨系统流程（`P3FlowService`）

```
POST /api/srm/rfqs/{id}/award?quoteId=N
  ├─ 校验：询价单状态 ∈ {PUBLISHED, QUOTING, CLOSED}；报价必须 rfq_id 匹配
  ├─ INSERT src_srm.srm_purchase_order（状态直接 CREATED，purchase_user='system'，
  │        带去 V31 新增列 source_requisition_no = rfq.source_requisition_no）
  ├─ UPDATE srm_rfq     → AWARDED + winner_supplier_code
  ├─ UPDATE srm_supplier_quote → 中标者 AWARDED，同 RFQ 其余报价 LOST
  └─ 发事件 SRM.PURCHASE_ORDER.SENT  (SRM → WMS, aggregate=PURCHASE_ORDER)

POST /api/srm/asns/{id}/arrive?warehouseCode=WH01
  ├─ 校验：ASN 状态 ∈ {SHIPPED, DRAFT}；且 wms_receipt 里不存在同 asn_no（防重）
  ├─ INSERT src_wms.wms_receipt       状态 PENDING_INSPECTION，同时生成检验单号 IQC+毫秒
  ├─ INSERT src_qms.qms_inspection    inspect_type='IQC', inspect_result='PENDING'
  ├─ UPDATE srm_asn → ARRIVED
  └─ 发事件 WMS.RECEIPT.PENDING_INSPECTION  (WMS → QMS, aggregate=RECEIPT)
```

### 各对象的可用状态迁移（`P3CrudService.rules()` 实测值）

| kind | 表 | 状态机 |
|---|---|---|
| `onboarding` | `srm_onboarding` | `DRAFT`→`APPROVING`→`APPROVED`/`REJECTED`；`APPROVED`→`CERTIFIED`/`BLACKLISTED`；`CERTIFIED`→`BLACKLISTED` |
| `rfqs` | `srm_rfq` | `DRAFT`→`PUBLISHED`/`CANCELLED`；`PUBLISHED`→`QUOTING`/`CLOSED`/`CANCELLED`；`QUOTING`→`CLOSED`/`CANCELLED`；`CLOSED`→`AWARDED`/`CANCELLED` |
| `quotes` | `srm_supplier_quote` | **无状态字段**（`P3CrudService.sc("quotes")` 返回 `null`），调 status 接口抛「对象没有状态字段」 |
| `asns` | `srm_asn` | `DRAFT`→`SHIPPED`/`CANCELLED`；`SHIPPED`→`ARRIVED`/`CANCELLED`；`ARRIVED`→`RECEIVED`/`RETURNED` |
| `supplier-quality` | `srm_supplier_quality` | 有 `status` 列判定，但迁移表为空 → **任何流转都被拒**（实际状态列是 `eight_d_status`） |
| `performance` | `srm_supplier_performance` | **无状态字段** |

采购订单自身的状态机只实现了 2 个状态：`CREATED`（新建）→ `SENT`（`/send`）。DDL 注释里的 `PARTIAL`/`RECEIVED`/`CLOSED`/`CANCELED` 没有对应的接口，但 `POST /api/srm/deliveries` 会接受 `PARTIAL` 状态的订单，说明该状态是留给人手工置入的。

## 数据表

### JPA `@Table` 注解声明的表（库名来自 `catalog`）

| 实体 | 库.表 | 关键列 |
|---|---|---|
| `PurchaseOrder` | `src_srm.srm_purchase_order` | `purchase_order_no`、`supplier_code/name`、`material_code/name`、`order_qty`、`received_qty`、`unit_price`、`total_amount`、`order_date`、`promised_date`、`expected_date`、`order_status`、`prod_order_no`、`purchase_user` |
| `SupplierDelivery` | `src_srm.srm_delivery` | `delivery_no`、`purchase_order_no`、`delivery_qty`、`qualified_qty`、`rejected_qty`、`expected_date`、`actual_date`、`delay_days`、`batch_no`、`inspection_status`、`is_stocked`、`created_by` |

### 无实体、由 SQL 直接读写的表

| 库.表 | 来源 | 用途 |
|---|---|---|
| `src_srm.srm_onboarding` | `P3CrudService` 注册表 | 供应商准入申请 |
| `src_srm.srm_qualification` | 建表脚本 `V29__p3_srm_onboarding_rfq.sql` | 资质证书 —— **有表但没有入口**：`P3CrudService` 注册表与 `SrmP3Controller` 白名单里都没有它 |
| `src_srm.srm_rfq` | `P3CrudService` + `P3FlowService` | 询价单 |
| `src_srm.srm_supplier_quote` | `P3CrudService` + `P3FlowService` | 供应商报价 |
| `src_srm.srm_asn` | `P3CrudService` + `P3FlowService` | 发货通知 |
| `src_srm.srm_supplier_quality` | `P3CrudService` 注册表；`P3FlowService.judgeInspection()` 写入 | 供应商质量问题（含 `ppm`、`problem_desc`） |
| `src_srm.srm_supplier_performance` | `P3CrudService` 注册表 | 供应商绩效（`delivery_rate`/`qualified_rate`/`ppm`/`score`/`rating`） |
| `src_wms.wms_receipt` | `P3FlowService.arriveAsn()` 写 | ASN 到货落成的收货单（跨系统写） |
| `src_qms.qms_inspection` | `P3FlowService.arriveAsn()` 写 | 自动开的 IQC 检验单（跨系统写） |
| `mfg_ops.biz_outbox` | `BusinessEventService.publish()` | 业务事件出箱 |

### 迁移脚本

| 脚本 | 内容 |
|---|---|
| `V29__p3_srm_onboarding_rfq.sql` | 建 `srm_onboarding`、`srm_qualification`、`srm_rfq`、`srm_supplier_quote` |
| `V30__p3_srm_asn_quality.sql` | 建 `srm_asn`、`srm_supplier_quality`、`srm_supplier_performance` |
| `V31__p3_srm_order_extension.sql` | 给 `srm_purchase_order` 加 `source_requisition_no`/`confirmed_at`/`change_reason`；给 `srm_delivery` 加 `asn_no`/`receipt_no` |

> **实体与表结构不同步**：V31 加的 5 个列在 `PurchaseOrder`/`SupplierDelivery` 实体里都没有映射，JPA 看不到它们；`source_requisition_no` 只能由 `P3FlowService` 的裸 SQL 写入，`confirmed_at`/`change_reason`/`asn_no`/`receipt_no` 目前**没有任何代码写入**。

## 对照最低验收

| 验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| 1. 至少 5 个本职模块具有实体、服务、接口和角色权限 | **部分** | 6 个 kind 有接口、有表；但 JPA 实体只有 2 个，`service/` 层为空（业务逻辑在共享的 `P3CrudService`/`P3FlowService`）。角色权限：`SecurityConfig` 第 77 行用 `hasAuthority("SYSTEM_SRM")` 做系统级门禁，权限点 `SRM:*` 在 `09_role_permission.sql` 有角色映射（BUYER/SQE/PURCHASE_SUPERVISOR/PURCHASE_DIRECTOR），但**本模块没有任何一个接口使用 `@PreAuthorize`** 声明 `SRM:*` 单元权限 |
| 2. 至少 3 类核心单据可以从创建流转到关闭或作废 | **部分达标** | 可走完整链：询价单 `DRAFT→…→CLOSED/AWARDED`、ASN `DRAFT→SHIPPED→ARRIVED→RECEIVED/RETURNED`、准入单 `DRAFT→…→CERTIFIED/BLACKLISTED`（均经 `POST /api/srm/{kind}/{id}/status`）。缺口：**采购订单**只有 `CREATED→SENT`，`/send` 之后没有 `CLOSED`/`CANCELED` 接口；报价单与绩效单无状态字段；到货单无状态流转 |
| 3. 至少 1 个审批流程和 1 个异常处理闭环 | **未达标（异常半边部分）** | 审批：**未实现** —— `srm_onboarding` 表有 `workflow_instance_id` 列，但全仓无 SRM 的 `ApprovalCallback` 实现，`workflow/` 目录不存在，没有代码写入该列。异常闭环：**部分** —— `P3FlowService.judgeInspection()` 在 IQC 不合格时自动生成 `srm_supplier_quality` 记录（含 PPM 与问题描述），形成「检验不合格 → 供应商质量事件」的入口，但后续 8D 整改/验证/关闭（`eight_d_status`、`closed_at`）无接口可用 |
| 4. 至少 2 个向其他系统发送或消费的幂等业务事件 | **已实现（仅发送侧）** | 2 个事件：`SRM.PURCHASE_ORDER.SENT`（SRM→WMS，定标时发）、`WMS.RECEIPT.PENDING_INSPECTION`（WMS→QMS，ASN 到货时发，由 SRM 接口触发）。幂等性由 `BusinessEventService` 保证：`event_id` 为 UUID 主键、`biz_inbox` 用 `INSERT IGNORE`、出箱 1 秒轮询、重试 3 次后置 `DEAD`。缺口：**没有任何消费侧代码**，两个事件目前只落 `biz_inbox` 而不被处理 |
| 5. 至少 1 页本系统运营查询或统计报表 | **未实现** | 只有 `PurchaseOrderController` 与 `SrmP3Controller` 的分页列表，没有 `stats`/汇总/报表接口；`query/` 层不存在。DDL 里为绩效预置了 `score`/`rating`/`ppm` 等列，但没有算分逻辑去填 |

**结论：五项中第 4 项达成，第 2 项部分达成，第 1、3 项部分，第 5 项未达成。**

## 未实现 / 缺口

### 业务能力缺口

- **对账协同**：`对账单、发票、付款状态查询` —— 无表、无实体、无接口，完全未实现。
- **供应商绩效算分**：`srm_supplier_performance` 只有裸表和通用 CRUD，没有任何服务把 `srm_delivery.delay_days`、`srm_supplier_quality.ppm` 汇总成 `delivery_rate`/`qualified_rate`/`score`/`rating`；DDL 里的 `UNIQUE KEY uk_supplier_period(supplier_code, period_code)` 意味着按期间算分是设计意图，但没有实现。
- **订单确认与变更**：V31 加了 `confirmed_at`（确认）与 `change_reason`（变更原因）两列，无接口、无代码写入。
- **交付承诺**：`PurchaseOrder.promisedDate`（供应商承诺到货日）只是实体上的一个字段，没有独立接口或状态。
- **退供**：`srm_delivery` 没有退供单；WMS 侧有 `SUPPLIER_RETURN` 库存动作，但 SRM 不感知。
- **资质管理**：`srm_qualification` 表建好了，未注册进 `P3CrudService`，无任何读写入口。
- **供应商分级与年度复审**：`srm_onboarding.risk_level` 是个未被写入的列；无复审单、无分级逻辑。
- **比价与合同价格**：`srm_supplier_quote` 有 `unit_price`/`score`，但没有比价视图、没有合同价格表。
- **8D 闭环**：`srm_supplier_quality.eight_d_status`/`due_date`/`closed_at` 三列无接口；`supplier-quality` 的 status 流转表为空，调 status 接口必然被拒。

### 工程与架构缺口

- **无 `workflow/`、`integration/`、`query/`、`domain/`、`service/` 五个分层目录**，业务规则集中在共享的 `P3CrudService.rules()`（一个 switch）与 `P3CrudService.cols()`（手写列表）里，改 SRM 状态机要动共享模块。
- **无任何 `@PreAuthorize`**：3 个 WMS 权限点有注解，SRM 一个都没有，细粒度权限只存在于种子数据中。
- **`P3CrudService.detail()` 用 `queryForMap`**，主键不存在时抛 `EmptyResultDataAccessException`（Spring 原生异常），不走 `BizException.notFound`，错误码不统一。
- **`P3CrudService.status()` 对没有状态列的对象不友好**：`supplier-quality` 判定出有 `status` 列，实际表里叫 `eight_d_status`，导致该 kind 的状态接口是个「必然失败」的死接口。
- **`SrmP3Controller` 与 `PurchaseOrderController` 的 `purchase-orders` 未打通**：`P3CrudService` 注册表里有 `purchase-orders → src_srm.srm_purchase_order`，但 `SrmP3Controller` 的 `K` 白名单里没有它，泛型接口访问不到采购订单。
- **查询性能**：`POST /api/srm/deliveries` 用 `repo.findAll().stream().filter(...)` 全表加载再匹配采购订单；`P3CrudService.page()` 的 `CONCAT_WS` + 前后 `%` 无法走索引；`create()` 每次都查 `information_schema`。
- **无测试**：`src/test/` 不存在，SRM 零测试覆盖。
- **无 `stats`/报表接口**，无导出接口（catalog 要求每个核心单据具备「分页查询、详情查看和导出接口」）。
