# shared/workflow/ — 自研审批引擎

> **整体计划** · 共享技术模块（非十系统之一）
>
> 模块现状另有 [README.md](README.md) 描述状态机与审批链；本文补充**职责边界**与**分层取舍**。
> 两份文档的口径差异只有一处：README 写「已配置的 7 条审批链」，而迁移
> `infra/db-init/11_plm_ecn_workflow.sql` 之后种子数据实为 **8 条**（新增 `PLM_ECN_CHANGE`），
> 故本文按 8 条陈述，并已在 [STATUS.md](STATUS.md) 记为 README 待更新项。

## 职责边界

### 解决什么横切问题

「提交 → 多级审批 → 通过/驳回 → 全程留痕」是 10 个业务系统**重复率最高**的一段逻辑。
若各系统各写一套，会出现三份代价：

| 重复代价 | 具体表现 |
|---|---|
| 状态机不一致 | A 系统驳回能改后重提，B 系统驳回后单据直接锁死 |
| 待办口径不一致 | 有的按人派单，有的按角色领取，人事变动时要改 N 处配置 |
| 审计缺口 | 只存「已通过」终态，说不清谁在第几步、等了多久、为什么驳回 |

本模块把这四件事收敛成**一份内核**：流程定义与实例、按角色创建的任务、操作日志（时间轴）、以及一条
`ApprovalCallback` 扩展点。业务系统只描述「后果」，不重复实现「流程」。

### 给谁用

`source-apps` 下 **10 个业务模块**（mdm、crm、erp、mes、wms、qms、srm、plm、eam、energy）的 `pom.xml`
均声明依赖 `shared-workflow`。接入只需两步：

1. 在 `wf_definition` + `wf_node` 插两行数据（流程与节点），**不改代码**；
2. 需要业务后果时，实现一个 `ApprovalCallback`。

### 上游依赖

| 依赖 | 用途 | 证据 |
|---|---|---|
| `shared-common` | `ApiResponse` 统一响应、`BizException`/`ErrorCode` 业务异常 | `WorkflowController` 全部返回 `ApiResponse`；`ApprovalEngine` 抛 `BizException` |
| `shared-security` | `CurrentUser.get()` 取当前用户；`@PreAuthorize('WF:TASK:VIEW')` 方法级鉴权；`SysUserRepository` 校验转交/加签/抄送对象是否存在 | `pom.xml` 注释「审批引擎依赖当前登录用户（提交人、审批人角色校验）与 @PreAuthorize」；`ApprovalEngine` 注入 `SysUserRepository` |

**依赖方向是单向的**：workflow → security → common，不反向。这决定了 `SecurityExceptionHandler`
必须留在 security 模块（见下文约束 4）。

## 不做什么

| 非目标 | 理由与现状 |
|---|---|
| **不认识任何业务概念** | 引擎不知道「物料」「BOM」「客户」，不直接改业务单据状态。业务后果一律由 `ApprovalCallback` 承接——这条边界是本模块存在的理由 |
| **不做条件分支 / 并行网关 / 子流程** | `WfDefinition.nextNode()` 是线性推进（取 nodeSeq 更大的最小者）。演示场景的审批链是直线，引入网关会带来画布、表达式、回退路径三倍的复杂度 |
| **不做会签（ALL）/ 或签（ANY）** | `wf_node.approve_mode` 列已建模且默认 `SINGLE`，但**全仓库无任何代码读取该字段**，属预留列而非已实现能力 |
| **不做超时自动流转** | `wf_node.timeout_hours` / `timeout_action` 两列已建，同样**无代码读取**，未实现任何催办或自动通过任务 |
| **不做流程设计器** | 流程定义靠插数据维护。演示场景 8 条链（README 记为 7 条 + 迁移 11 新增 1 条），插数据比做一个画布快得多 |
| **不做流程版本迁移** | `wf_definition.def_version` 有列，但 `findByBizTypeAndEnabledTrue()` 按 bizType 查**不带版本**，在途实例也不会随定义改版而重新绑定。多版本共存未实现 |
| **不直接发通知** | `onNodeEntered` 只回调节点名，是否发消息、发什么，由业务侧决定 |

## 代码分层

本项目 `source-apps` 统一分层为 `domain/ entity/ repo/ service/ controller/ workflow/ integration/ query/`。
本模块**实际使用 6 个包**，其余为空，原因逐条说明：

| 分层 | 现状 | 文件数 | 关键类 | 说明 |
|---|---|---|---|---|
| `callback/` | ✅ 有 | 1 | `ApprovalCallback` | 唯一的扩展点；非统一分层中的标准包，为本模块自定义 |
| `controller/` | ✅ 有 | 1 | `WorkflowController` | 待办、审批、撤回、时间轴、定义查询 |
| `dto/` | ✅ 有 | 3 | `StartApprovalRequest`、`PendingTaskView`、`TimelineItem` | 入参与视图分离；`PendingTaskView` 把任务与实例拼成前端可直接渲染的一行 |
| `entity/` | ✅ 有 | 6 | `WfDefinition`、`WfNode`、`WfInstance`、`WfTask`、`WfActionLog`、`WfCc` | 6 张表一一对应 |
| `repo/` | ✅ 有 | 5 | `WfTaskRepository`（`findMyPending` 按角色 IN 查询）等 | `WfCc` 的仓储合并在 `WfCcRepository`，故 6 实体 / 5 仓储 |
| `service/` | ✅ 有 | 2 | `ApprovalEngine`（写）、`WorkflowQueryService`（读） | **读写分离**：引擎只管推进与留痕，查询服务负责组装视图 |
| `domain/` | ❌ 空 | 0 | — | 状态机**内联**在实体与引擎中：`WfInstance.isRunning()/finish()`、`WfTask.isPending()/claimBy()/complete()`、`ApprovalEngine` 的推进分支。当前 4 个状态、2 种驳回去向，抽独立状态机类是过度设计；若将来增加会签/超时/网关，应先把状态迁移集中到 `domain/` |
| `workflow/` | ❌ 空 | 0 | — | 本模块**自身就是**工作流内核，再套一层 `workflow/` 无意义。各业务模块自己的 `workflow/` 包（放本系统的 `ApprovalCallback` 实现）属于那些模块 |
| `integration/` | ❌ 空 | 0 | — | 不直接对接外部系统；与业务的耦合只经由 `ApprovalCallback` |
| `query/` | ❌ 空 | 0 | — | 查询能力收在 `service/WorkflowQueryService`，未单开 `query/`。若查询继续膨胀（台账、导出、统计）再拆 |
| `src/test/` | ❌ 无 | 0 | — | 模块无单元测试，验证靠 `ops/demo_bom_approval.py` 与 `ops/demo_approval_boundary.py` 端到端脚本 |

合计 19 个 Java 文件、约 1559 行。

## 关键设计约束

### 1. 循环依赖用 `ObjectProvider` 打破（构造期不解析回调）

真实的依赖环：

```
ApprovalEngine → ApprovalCallback → MasterDataService → ApprovalEngine   ← 环
```

`ApprovalEngine` 注入的是 `ObjectProvider<ApprovalCallback>` 而非 `List<ApprovalCallback>`：
构造期不向容器索取回调，只在 `invokeCallback()` 真正触发时遍历 `ObjectProvider`。
这是 Spring 官方推荐的打破构造器循环依赖的方式，**不要改回 `List` 注入**——启动即失败。

### 2. 回调异常必须被吞掉，不能回滚审批

`invokeCallback()` 内部 `try/catch` 只打 `log.error`。理由：审批结果已落库，业务侧失败
（如分发到 ERP 超时）是另一个问题域，不该把已完成的审批回滚掉。

**代价要清楚**：目前**没有**补偿表或自动重试，失败只能靠日志排查。若业务侧动作需要可靠投递，
应由业务侧走 `BusinessEventService`（见 `shared/common`）而不是依赖回调。

### 3. 任务按「角色」创建，不按「人」

`createTask()` 只写 `approverRole`，不写 `approverUser`；谁处理谁 `claimBy()` 领取。
`wf_task.assigned_user` 仅在**转交/加签**时写入，此时 `loadMyPendingTask()` 的校验口径
从「持有该角色」切换为「必须是受派人」：

```java
task.getAssignedUser() != null
        ? !task.getAssignedUser().equals(me.getUsername())
        : !me.hasRole(task.getApproverRole())
```

### 4. 鉴权是两层，且异常处理器必须放在 security 模块

- 第一层：`@PreAuthorize("hasAuthority('WF:TASK:APPROVE')")` —— 「是不是某类主管」；
- 第二层：`loadMyPendingTask()` 校验任务存在 → 未处理 → 角色/受派人匹配 —— 「能不能审**这一步**」。

有 `WF:TASK:APPROVE` 不代表能审任意节点：生产主管不能审工艺主管的待办。

第二层校验抛的是 `BizException`（由 `common` 的 `GlobalExceptionHandler` 处理），
而 `@PreAuthorize` 抛的 `AccessDeniedException` 由 `security` 模块的 `SecurityExceptionHandler`
处理——**它必须在 security 模块**，因为 `common` 不依赖 `spring-security`，否则会形成
`common → security → common` 的循环依赖。

### 5. 驳回必须填理由，且去向由节点配置决定

- `reject()` 入口即校验 `opinion` 非空，不依赖前端；
- 去向读 `wf_node.reject_action`：`BACK` 退回提交人（流程结束，触发 `onRejected`）；
  `PREV` 退回上一节点（**先作废上一节点的旧任务再重建**，避免上一节点同时存在两条待办）。

### 6. 提交幂等：同一单据只允许一条在途实例

`start()` 先查 `bizType + bizId + status='RUNNING'`，命中即抛 `BizException.conflict`。
否则审批人会看到两条一模一样的待办。

### 7. 时间轴终点记 `FINISH` 而非 `APPROVE`

若终点也写 `APPROVE`，时间轴会出现两条连续的「同意」，看起来像同一个人批了两次。
`WorkflowQueryService.nodeLabel()` 对 `FINISH` 特判显示「流程完成」，且 `FINISH` 行 `nodeSeq` 为 `null`。

### 8. 事务边界

| 方法 | 事务 |
|---|---|
| `start` / `approve` / `reject` / `cancel` / `transfer` / `addSign` / `copyTo` | `@Transactional` 写事务 |
| `myPendingTasks` / `myPendingCount` / `timeline` / `getInstance` / `historyOf` / `runningOf` | `@Transactional(readOnly = true)` |
| `invokeCallback()` | **在写事务内执行**，故回调内抛异常虽然被吞，但若回调自身改了库且未标记新事务，会随外层一起提交 |

### 9. 实例号生成不是并发安全的

`generateInstanceNo()` 用 `instanceRepo.count() + 1` 拼 `WF-yyyyMMdd-0001`，冲突时循环重试最多 100 次。
代码注释已声明「极端并发下可能有重号」。演示环境可接受；生产应换成序列或数据库唯一约束 + 重试。

### 10. 加签两种模式在流转上暂无差异

`addSign()` 会把原任务置为 `WAITING`，新任务记录 `sign_mode` 为 `AFTER` 或 `BEFORE`；
但 `approve()` 的判断条件是 `sourceTaskId != null && signMode != null`——**只区分「是不是加签任务」**，
两种模式的实际流转顺序一致（都是加签人先审，再恢复原任务）。
即：`BEFORE` 语义正确，`AFTER` 与 `BEFORE` 行为相同，属未完全实现。
