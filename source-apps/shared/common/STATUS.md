# shared/common/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**统一响应 / 业务异常 / 错误码 / 全局异常处理已 100% 覆盖并被全部 10 个业务模块使用**；
**业务事件 Outbox/Inbox 总线已跑通**（定时分发 + 幂等 + 3 次重试 + 死信 + 人工重试接口，13 个发布点）；
另含一套 **P3 采购-仓储-质量主链的通用骨架**（`P3CrudService` 覆盖 20 张业务表、
`P3FlowService` 打通询价定标→收货→检验→库存），用于 SRM/WMS/QMS 三系统共用演示主链。

## 已实现

### 分层文件统计

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `api/` | 2 | `ApiResponse<T>`（`ok` / `ok(data)` / `fail(code,msg)` / `fail(ErrorCode,msg)`）、`ErrorCode`（分段枚举，10xxx/20xxx/30xxx/40xxx/50xxx/90xxx） |
| `exception/` | 2 | `BizException`（静态工厂 `of` / `notFound` / `conflict` / `badState`）、`GlobalExceptionHandler`（4 类异常分支 + 兜底） |
| `integration/` | 2 | `BusinessEventService`（`@Scheduled(fixedDelay=1000)` 的 `dispatch()`）、`BusinessEventController` |
| `service/` | 2 | `P3CrudService`（通用分页/详情/新增/状态流转）、`P3FlowService`（定标、到货、检验判定） |
| `package-info.java` | 1 | — |
| （空层）`domain/` `entity/` `repo/` `controller/` `workflow/` `query/` | 0 | 空层，原因见 [PLAN.md](PLAN.md) 的分层表（含「`BusinessEventController` 不在 `controller/` 包」这一已知偏差） |
| `src/test/` | 0 | 无测试 |

合计 9 个 Java 文件、约 523 行。

## 对外接口 / 扩展点

### REST 端点

| 类型 | 名称 | 说明 |
|---|---|---|
| GET | `/api/events/business?status=&page=&size=` | 事件分页（按 `occurred_at` 倒序，`size` 上限 200）。`status` 可选，用于筛 `PENDING`/`SUCCESS`/`DEAD` 等 |
| GET | `/api/events/business/{eventId}` | 事件详情，附带该事件在各目标系统的 `biz_inbox` 投递记录（按 `target_system` 排序） |
| POST | `/api/events/business/{eventId}/retry` | **人工重试**：把 `status` 重置为 `RETRYING`、`retry_count=0`。仅对 `FAILED`/`DEAD`/`RETRYING` 生效，其余状态抛 `IllegalStateException` |

三个端点均无 `@PreAuthorize` 注解（仅需登录）。**没有手动触发分发的端点**——投递完全由定时器驱动。

### 供其他模块使用的扩展点

| 类型 | 名称 | 说明 |
|---|---|---|
| 响应契约 | `ApiResponse<T>` | 全项目统一返回结构，前端与 AI 工具按同一约定解析 |
| 异常契约 | `BizException` + `ErrorCode` | 业务失败统一抛出；由 `GlobalExceptionHandler` 转 `ApiResponse`，Controller 内不写 try-catch |
| 全局处理器 | `GlobalExceptionHandler`（`@RestControllerAdvice`） | 自动生效，业务模块无需装配。**安全类异常不在此处**，见 [shared/security](../security/PLAN.md) |
| 事件发布 | `BusinessEventService.publish(eventType, sourceSystem, targetSystem, aggregateType, aggregateId, payload)` | 跨系统集成的统一出口，共 **13 个调用点** |
| 通用 CRUD | `P3CrudService.page/detail/create/status` | 被 3 个 Controller + 1 个 Service 使用，见下表 |
| 主链流转 | `P3FlowService.awardRfq/arriveAsn/judgeInspection` | 见下表的调用覆盖情况 |

#### `P3CrudService` 的使用方与白名单

| 使用方 | 基路径 | 支持的 `{kind}` |
|---|---|---|
| `qms/controller/QmsP3Controller` | `/api/qms` | `standards`、`sampling-plans`、`ncrs`、`capas`、`8d` |
| `wms/controller/WmsP3Controller` | `/api/wms` | `receipts`、`putaway`、`inventories`、`inventory-actions`、`transfers`、`counts` |
| `srm/controller/SrmP3Controller`（⚠️ **git 未跟踪文件**） | `/api/srm` | `onboarding`、`rfqs`、`quotes`、`asns`、`supplier-quality`、`performance` |
| `qms/service/QmsLifecycleService`（Service 层直接调用） | — | 调 `crud.status(...)` 做状态流转 |

白名单共 **20 张表**（7 张 `src_srm.*` + 6 张 `src_wms.*` + 7 张 `src_qms.*`），
每个 `{kind}` 还带一套状态迁移规则与模糊搜索列（见 PLAN.md「关键设计约束 7」）。

#### `P3FlowService` 的使用方

| 方法 | 调用方 | 端点 |
|---|---|---|
| `awardRfq` | `SrmP3Controller` | `POST /api/srm/rfqs/{id}/award` |
| `arriveAsn` | `SrmP3Controller` | `POST /api/srm/asns/{id}/arrive` |
| `judgeInspection` | **无任何调用方** | —（QMS 走自己的 `QmsLifecycleService.judge`，逻辑重复，见「未实现」） |

#### 业务事件的 13 个发布点

| 发布方 | eventType | source → target |
|---|---|---|
| `P3FlowService` | `SRM.PURCHASE_ORDER.SENT` | `SRM` → `WMS` |
| `P3FlowService` | `WMS.RECEIPT.PENDING_INSPECTION` | `WMS` → `QMS` |
| `P3FlowService` | `QMS.INSPECTION.JUDGED` | `QMS` → `WMS` |
| `qms/QmsLifecycleService` | `QMS.INSPECTION.JUDGED` | `QMS` → `WMS` |
| `qms/QmsLifecycleService` | `QMS.NCR.DISPOSED` | `QMS` → `SRM`（判定为退货时）或 `WMS` |
| `crm/CrmP2Service` | `CRM.CONTRACT.ACTIVATED`、`CRM.CONTRACT.ORDER_REQUESTED` | `crm` → `erp` |
| `crm/CrmP2Service` | `ERP.SALES_ORDER.CREATED` | `erp` → `crm` |
| `erp/ErpP2Service` | `ERP.SALES_ORDER.CONFIRMED`、`ERP.MRP.COMPLETED` | `erp` → `crm` |
| `erp/ErpP2Service` | `ERP.PURCHASE_REQUISITION.CREATED` / `ERP.PRODUCTION_ORDER.CREATED` | `erp` → `srm` / `mes` |
| `erp/ErpP2Service` | `ERP.RECEIPT.SETTLED` | `erp` → `crm` |
| `erp/ProductionOrderService` | `ERP.PRODUCTION_ORDER.RELEASED` | `erp` → `mes` |

## 数据表

本模块**没有任何 JPA 实体**，因此没有 `@Table` 注解可列。所有表都以
**限定名 + 原生 SQL（`JdbcTemplate`）** 访问，这是本模块的既定做法（见 PLAN.md 分层表的
`entity/` 行）。按用途分两组：

| 表 | 访问方式 | 说明 |
|---|---|---|
| `mfg_ops.biz_outbox` | `BusinessEventService` 原生 SQL | 事件发件箱。DDL 见 Flyway `V26__p2_business_closure.sql:1`。字段含 `event_id`、`event_type`、`source_system`、`target_system`、`aggregate_type`、`aggregate_id`、`trace_id`、`payload`（JSON）、`status`、`retry_count`、`error_message`、`occurred_at`、`processed_at` |
| `mfg_ops.biz_inbox` | `BusinessEventService` 原生 SQL | 事件收件箱，`INSERT IGNORE` 保证幂等。DDL 见 `V26__p2_business_closure.sql:21` |
| `src_srm.*` × 7 | `P3CrudService` 白名单 + `P3FlowService` | `srm_onboarding`、`srm_rfq`、`srm_supplier_quote`、`srm_purchase_order`、`srm_asn`、`srm_supplier_quality`、`srm_supplier_performance` |
| `src_wms.*` × 6 | 同上 | `wms_receipt`、`wms_putaway`、`wms_inventory`、`wms_inventory_action`、`wms_transfer`、`wms_count_plan` |
| `src_qms.*` × 7 | 同上 | `qms_standard`、`qms_sampling_plan`、`qms_inspection`、`qms_ncr`、`qms_rework`、`qms_capa`、`qms_eight_d` |

`P3CrudService` 在 `create()` 时还会读 `information_schema.COLUMNS`（白名单校验），
这是本模块唯一的元数据读取。

## 未实现 / 缺口

| 缺口 | 现状与证据 |
|---|---|
| **无任何单元测试** | `src/test/` 不存在。响应结构、异常处理、事务边界、事件重试逻辑均无自动化验证 |
| **`P3FlowService.judgeInspection` 是死代码** | 检验判定逻辑在 `P3FlowService.judgeInspection()` 与 `qms/QmsLifecycleService.judge()` 中**各写了一份**，且只有后者被 Controller 调用。两份逻辑一旦漂移（如库存状态映射规则）会出现「同样判定不同结果」 |
| **P3 与 P2 的系统编码大小写不一致** | P3 链用大写系统码（`SRM`/`WMS`/`QMS`），P2 模块用小写（`crm`/`erp`/`mes`/`srm`）。同一张 `biz_outbox` 表里两种风格混存，按 `source_system`/`target_system` 统计或路由时需要兼容处理 |
| **事件总线假设单进程** | `dispatch()` 整个方法是一个 `@Transactional`，逐条 `FOR UPDATE` + 逐条更新。多实例部署时锁与提交边界不成立；注释已写明「单进程演示环境」 |
| **无手动触发分发的端点** | 投递只靠 `@Scheduled(fixedDelay=1000)`；进程未起时事件只堆积在 outbox，无「立即分发」按钮 |
| **payload 无 schema 与版本** | `payload` 是自由 JSON，无事件契约定义、无 `schema_version` 字段，消费方只能按约定解析 |
| **`biz_inbox` 无消费确认** | `INSERT IGNORE` 即视为已投递（`status='SUCCESS'`），没有「消费方已处理」的回执，重试可能对消费方产生重复副作用（消费方须自行幂等） |
| **pom 描述中的「审计字段」未实现** | `shared-common/pom.xml` 的 `<description>` 写了「公共基础：统一响应、业务异常、分页、审计字段」，但**没有任何审计基类或 `@MappedSuperclass`**；各模块实体自行声明 `created_at`/`updated_at` |
| **`BusinessEventController` 未放在 `controller/`** | 位于 `integration/` 包内，与统一分层不一致（已在 PLAN.md 记为已知偏差） |
| **`SrmP3Controller` 未被 git 跟踪** | 该文件存在于工作区但 `git status` 显示为 `??`，一旦被清理/覆盖，`P3FlowService` 将**完全没有调用方** |
| **`P3CrudService` 的字段白名单依赖数据库元数据查询** | 每次 `create()` 多一次 `information_schema` 查询；且按「驼峰转下划线」猜测列名，列名不规则时静默丢弃该字段（全不匹配才报错），可能出现「提交了却少写字段」而不报错 |
