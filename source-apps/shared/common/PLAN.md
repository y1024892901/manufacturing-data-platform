# shared/common/ — 公共基础（响应 / 异常 / 事件总线 / P3 主链服务）

> **整体计划** · 共享技术模块（非十系统之一）

## 职责边界

### 解决什么横切问题

本模块是全项目**最底层的依赖**（`shared-security`、`shared-workflow`、`shared-masterdata` 以及
10 个业务模块全部依赖它，见 `source-apps/pom.xml` 的 `dependencyManagement`）。它解决三类问题：

| 问题 | 若不抽出来会怎样 | 本模块的答案 |
|---|---|---|
| **响应结构不一致** | 有的接口返回数组、有的返回对象、有的包一层 `{success:true}`，前端要写 N 套解析 | `ApiResponse<T>`：`code / message / data / timestamp`，`code=0` 为成功 |
| **错误语义靠猜** | `@PreAuthorize` 拒绝返回 500、参数校验失败返回 HTML 错误页 | `ErrorCode` 分段枚举（10xxx 通用 / 20xxx 认证 / 30xxx 主数据 / 40xxx 单据 / 50xxx 流程 / 90xxx 系统）+ `BizException` + `GlobalExceptionHandler` 兜底 |
| **上下游调用靠直连** | SRM 定标后直接调 WMS 接口，对方不可用就卡住主流程，且没有重试与追踪 | `BusinessEventService`：Outbox/Inbox 事件表 + 定时分发 + 幂等 + 重试计数 + 死信 + 人工重试接口 |

### 给谁用

| 使用方 | 用到的部分 |
|---|---|
| 10 个业务模块（mdm / crm / erp / mes / wms / qms / srm / plm / eam / energy） | `ApiResponse`、`BizException`、`ErrorCode`（所有 Controller 统一返回） |
| `shared-security` | `ApiResponse` / `ErrorCode`（`SecurityConfig` 把认证失败也写成统一结构）、`BizException`（`CurrentUser.get()` 未登录时抛 401） |
| `shared-workflow` | `ApiResponse`、`BizException`、`ErrorCode`（`WORKFLOW_*` 段错误码） |
| `shared-masterdata` | pom 声明依赖（当前该模块只有 `package-info.java`，见下文） |
| SRM / WMS / QMS 的 P3 演示主链 | `P3CrudService`（通用 CRUD）、`P3FlowService`（跨系统单据流转）——见「边界特例」 |

### 边界特例：`service/P3*` 与「公共基础」的定位存在张力

`P3CrudService` 与 `P3FlowService` 严格来说**不是**通用基础设施：

- `P3CrudService` 内置了一张 **20 个业务表的映射表**（`src_srm.srm_rfq`、`src_wms.wms_receipt`、
  `src_qms.qms_ncr` …），并硬编码了每个对象的状态机迁移规则与模糊搜索列；
- `P3FlowService` 实现的是具体业务流：询价定标 → 生成采购订单 → ASN 到货 → 生成收货单与 IQC 检验
  → 检验判定 → 库存可用性/隔离/退供，并在每步发布业务事件。

它们放在 `shared/common` 的原因，是 **P3 阶段（采购、仓储、质量闭环，见 `docs/implementation-roadmap.md`）
的 SRM/WMS/QMS 三系统共用一个演示主链**，避免同一段流转逻辑在三个模块里各写一遍。
**代价要写清楚**：这两个类是「按表名拼接 SQL 的通用骨架」，新增字段无需改代码，但
**牺牲了类型安全与领域模型**——这也是 10 个业务模块各自仍保留 `entity/ repo/ service/` 的原因。
若将来要替换，应把它们**下沉到各自的业务模块**，而不是让 `common` 继续积累业务知识。

## 不做什么

| 非目标 | 说明 |
|---|---|
| **不依赖任何其他 shared 模块** | `pom.xml` 只依赖 Spring 官方 starter（web / validation / data-jpa / springdoc）与 MySQL 驱动。**没有** `spring-boot-starter-security`、没有 AOP。这条约束是硬性的：`shared-security` 依赖 `common`，若 `common` 反向依赖 security 会构成循环 |
| **不处理安全异常** | `AccessDeniedException` / `AuthenticationException` 由 `shared-security` 的 `SecurityExceptionHandler` 处理，因为 `common` 看不到这些异常类型（见上一条）。两者都用 `@RestControllerAdvice`，靠 `@Order(HIGHEST_PRECEDENCE)` 区分优先级 |
| **不做多数据源配置** | 全项目统一连 `mfg_auth`（见 `bootstrap/src/main/resources/application.yml`），跨库访问靠 SQL 里的**限定名**（`src_srm.`、`src_wms.`），不配置多数据源。⚠️ **`application.yml:37` 的注释「各业务系统的库通过多数据源切换（见 shared/common 的 DataSourceConfig）」是错的**：全仓库不存在 `DataSourceConfig` 类，也无 `AbstractRoutingDataSource` / `@DS` 等任何动态数据源设施（`datasource` 关键字在整个配置目录只出现 1 次）。实际机制就是「单数据源 + 限定名 SQL」，新增库时**不要**按该注释去找切换配置 |
| **不做通用审计/日志切面** | 操作审计在 `shared-security` 的 `OperationAuditAspect`；本模块的 `BusinessEventService` 只管**跨系统**事件，不记录本系统内的读写 |
| **不提供分页封装类** | 直接用 Spring Data 的 `Page` / `Pageable`，`ApiResponse<Page<T>>` 即可，不另造 `PageResult` |
| **不提供基类实体 / 审计字段自动填充** | 各业务模块的实体各自声明 `created_at` / `updated_at`（如 `SysUser` 用 `insertable=false, updatable=false` 交给数据库默认值）。pom 描述里的「审计字段」目前**未实现为公共基类** |

## 代码分层

本项目 `source-apps` 统一分层为 `domain/ entity/ repo/ service/ controller/ workflow/ integration/ query/`。
本模块**是基础设施，分层形态与业务模块不同**，实际用 4 个包、空 6 个：

| 分层 | 现状 | 文件数 | 关键类 | 说明 |
|---|---|---|---|---|
| `api/` | ✅ 有（自定义包） | 2 | `ApiResponse`、`ErrorCode` | **不在统一分层的八个目录中**——统一分层是给业务模块定的，本模块提供的是被依赖的契约，故用 `api` 语义命名 |
| `exception/` | ✅ 有（自定义包） | 2 | `BizException`、`GlobalExceptionHandler` | 同上，`exception` 亦非统一分层目录 |
| `integration/` | ✅ 有 | 2 | `BusinessEventService`（Outbox/Inbox + `@Scheduled` 分发）、`BusinessEventController` | 对应统一分层里的「上下游事件、分发、幂等处理」 |
| `service/` | ✅ 有 | 2 | `P3CrudService`、`P3FlowService` | P3 主链通用骨架（见上文「边界特例」） |
| `domain/` | ❌ 空 | 0 | — | 本模块无领域状态机。P3 的状态迁移规则以 `Map<String, Set<String>>` 形式**内联**在 `P3CrudService.rules()` 中，未抽成领域类 |
| `entity/` | ❌ 空 | 0 | — | 有意为之：`BusinessEventService` 用 `JdbcTemplate` + `Map` 直接读写 `mfg_ops.biz_outbox` / `biz_inbox`，**不为这两张表建实体**——它们是平台运维表，不是业务领域对象；`P3*` 同理按表名操作。副作用：无 JPA 映射、无 `@Table` 注解可列举 |
| `repo/` | ❌ 空 | 0 | — | 同上，统一走 `JdbcTemplate`，不建 `JpaRepository` |
| `controller/` | ❌ 空 | 0 | — | **注意**：本模块唯一的 REST 入口 `BusinessEventController` 放在 `integration/` 包内，以保持「事件相关的读写」内聚。这与统一分层的「controller 单独成包」不一致，是**已知偏差**；新增的业务 Controller 不应效仿，应放 `controller/` |
| `workflow/` | ❌ 空 | 0 | — | 审批走 `shared-workflow`，本模块不参与 |
| `query/` | ❌ 空 | 0 | — | 事件列表查询 `page()` 收在 `BusinessEventService` 内；`P3CrudService.page()` 是通用分页。查询量小，未单开包 |
| `src/test/` | ❌ 无 | 0 | — | 无单元测试 |

合计 9 个 Java 文件、约 523 行。

## 关键设计约束

### 1. 依赖方向单向：common ← security ← workflow

```
shared/common  ←──  shared/security  ←──  shared/workflow
      ↑                    ↑
   10 个业务模块 / shared-masterdata
```

任何在 `common` 里引用 `spring-security` 的改动都会制造循环依赖。这也是
`GlobalExceptionHandler` 只处理「非安全类」异常的原因。

### 2. 业务异常返回 HTTP 200，校验/系统异常才带 HTTP 状态码

`GlobalExceptionHandler` 的分工是被刻意设计的：

| 异常 | HTTP 状态 | 业务码 | 日志级别 | 理由 |
|---|---|---|---|---|
| `BizException` | **200** | 具体业务码 | `warn` | 「物料未发布」是**业务结果**而非请求失败，用 HTTP 状态码表达会让前端把业务提示当故障弹窗 |
| `MethodArgumentNotValidException` / `BindException` | 400 | 10001 | — | 请求体本身不合法，且错误信息拼上**具体字段名**（`field: message`） |
| `DataIntegrityViolationException` | 200 | 10004 | `warn` | 唯一键冲突（重复客户编码）是业务问题，不是系统故障，故单独拦截 |
| 其他 `Exception` | 500 | 90001 | **`error` + 完整堆栈** | 兜底，仅在此处打堆栈 |

### 3. `ErrorCode` 分段是接口契约，不允许跳段

`ErrorCode` 的注释写明了区间约定（10xxx/20xxx/30xxx/40xxx/50xxx/90xxx），
前端与 AI 工具按区间判断错误类型。新增错误码必须落在对应区间内。

### 4. 事件分发：`@Scheduled(fixedDelay = 1000)` + 幂等 + 三振出局

`BusinessEventService` 的语义（单进程演示环境）：

| 环节 | 实现 |
|---|---|
| 写入 | `publish()` 写 `mfg_ops.biz_outbox`，`status` 默认 `PENDING`，同时生成 `event_id` 与 `trace_id` |
| 分发 | `dispatch()` 每 1s 扫 `status IN ('PENDING','RETRYING') AND retry_count < 3`，`ORDER BY occurred_at LIMIT 50 FOR UPDATE` |
| 幂等 | 投递到 `biz_inbox` 用 `INSERT IGNORE`，主键冲突即已投递过 |
| 重试 | 失败 `retry_count+1`；`retry_count+1 >= 3` 置 `DEAD`，否则 `RETRYING`；`error_message` 截断 500 字符 |
| 人工干预 | `POST /api/events/business/{eventId}/retry` 只允许 `FAILED / DEAD / RETRYING` 状态重置，其余抛 `IllegalStateException` |

**注意**：`dispatch()` 整个方法是一个 `@Transactional`，逐条更新；单条事件的失败不会中断循环（异常在循环内被捕获），但 `FOR UPDATE` 锁与逐条提交的边界在并发多实例部署时不成立——**本模块假设单进程**。

### 5. 事件总线**不保证**「恰好一次」，业务幂等由消费方负责

`INSERT IGNORE` 只保证同一 `event_id` 不重复入库；若消费方在此之外还有副作用（扣库存、写凭证），
必须自己保证幂等。这一点在 `P3FlowService` 中有体现：`arriveAsn()` 先 `SELECT COUNT(*) FROM src_wms.wms_receipt WHERE asn_no=?` 判重，命中即抛 `MASTER_DATA_ALREADY_EXISTS`。

### 6. `P3FlowService` 的跨库写操作在**一个本地事务**内完成

`awardRfq()` / `arriveAsn()` / `judgeInspection()` 均标 `@Transactional`，并在同一事务里
写 `src_srm`、`src_wms`、`src_qms` 三个库的表。这在 MySQL 里可行是因为**它们在同一实例上**、
用限定名访问；**若这些库被拆到不同实例，本地事务立即失效**，需要改为最终一致。
`judgeInspection()` 还承担了库存状态的翻译职责：

```java
PASSED / CONCESSION → AVAILABLE
SCRAPPED            → SCRAPPED
RETURN              → RETURNED
REWORK              → REWORK
其余                 → QUARANTINED
```

### 7. `P3CrudService` 的白名单机制

`create()` 不信任入参字段名：先查 `information_schema.COLUMNS` 取出目标表的真实列
（并排除 `auto_increment`），把驼峰入参转下划线后**只保留命中的列**，全部不匹配则抛
`PARAM_INVALID`。这实际上是一层**基于数据库元数据的防注入/防越权写入**，
代价是每次写入多一次 `information_schema` 查询。
