# shared/workflow/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md) · 模块概览见 [README.md](README.md)

## 一句话结论

**审批内核已完整可用**：流程定义/实例/待办/时间轴 5 张核心表 + 抄送表已建，
`ApprovalEngine` 覆盖提交、同意、驳回（退回提交人 / 退上一节点两种去向）、撤回、转交、加签、抄送七类动作，
**6 个业务模块已接入、5 个 `ApprovalCallback` 实现类在跑**，全流程走 `WorkflowController` 单一入口；
未实现的是**会签/或签、超时自动流转、流程版本迁移**这三项进阶能力（字段已建模但无代码读取）。

## 已实现

### 分层文件统计

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `callback/` | 1 | `ApprovalCallback`（3 个必实现：`supports` / `onApproved` / `onRejected`；2 个可选默认方法：`onSubmitted` / `onNodeEntered`） |
| `controller/` | 1 | `WorkflowController`（`/api/workflow`，16 个端点） |
| `dto/` | 3 | `StartApprovalRequest`（含静态工厂 `of`）、`PendingTaskView`、`TimelineItem` |
| `entity/` | 6 | `WfDefinition`（含 `nextNode` / `nodeAt` / `lastNodeSeq`）、`WfNode`、`WfInstance`（`isRunning` / `finish`）、`WfTask`（`isPending` / `claimBy` / `complete`）、`WfActionLog`（静态工厂 `of`）、`WfCc` |
| `repo/` | 5 | `WfTaskRepository`（`findMyPending` / `findMyPendingPaged` / `countMyPending` / `findMyHandled` / `findPendingAtNode`）、`WfInstanceRepository`、`WfDefinitionRepository`、`WfActionLogRepository`、`WfCcRepository` |
| `service/` | 2 | `ApprovalEngine`（写）、`WorkflowQueryService`（读） |
| `package-info.java` | 1 | — |
| （空层）`domain/` `workflow/` `integration/` `query/` | 0 | 空层，原因见 [PLAN.md](PLAN.md) 的分层表 |
| `src/test/` | 0 | 无单元测试，验证靠 `ops/demo_bom_approval.py` 与 `ops/demo_approval_boundary.py` |

合计 19 个 Java 文件、约 1559 行。

## 对外接口 / 扩展点

### REST 端点（`/api/workflow`）

| 类型 | 名称 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/tasks/pending` | `WF:TASK:VIEW` | 我的待办（分页），已把任务与实例拼成可直接渲染的 `PendingTaskView`，含等待小时数 |
| GET | `/tasks/pending/count` | 仅登录 | 待办数量（首页角标，避免拉全量） |
| GET | `/tasks/handled` | 仅登录 | 我已处理的（`approver_user` 是我且状态非 `PENDING`） |
| GET | `/tasks/copied` | 仅登录 | 抄送给我的（`WfCcRepository.findByUsernameOrderByIdDesc`） |
| GET | `/instances/started` | 仅登录 | 我发起的流程 |
| POST | `/tasks/{taskId}/approve` | `WF:TASK:APPROVE` | 同意；推进下一节点，或（末节点）流程通过并触发 `onApproved` |
| POST | `/tasks/{taskId}/reject` | `WF:TASK:APPROVE` | 驳回，意见必填（`WORKFLOW_OPINION_REQUIRED`） |
| POST | `/tasks/{taskId}/transfer` | 仅登录 | 转交：原任务置 `TRANSFERRED`，新建一条 `assigned_user` 待办 |
| POST | `/tasks/{taskId}/add-sign` | 仅登录 | 加签：原任务置 `WAITING`，新建加签待办 |
| POST | `/instances/{instanceId}/cancel` | 仅登录 | 撤回（引擎内校验：仅提交人或 ADMIN） |
| POST | `/instances/{instanceId}/cc` | 仅登录 | 抄送（可多人，去重；抄送对象必须存在） |
| GET | `/instances/{instanceId}/timeline` | 仅登录 | **审批时间轴**（演示核心视觉元素） |
| GET | `/instances/{instanceId}` | 仅登录 | 实例详情 |
| GET | `/instances/history?bizType=&bizId=` | 仅登录 | 某业务单据的全部审批历史（可多轮） |
| GET | `/instances/running?bizType=&bizId=` | 仅登录 | 该单据当前是否在审批中（无则返回 `null`） |
| GET | `/definitions` | 仅登录 | 全部启用中的流程定义（含节点与审批角色） |

**鉴权现状**：只有 `pending`、`approve`、`reject` 三个端点带 `@PreAuthorize`；
其余端点仅需登录，安全性依赖引擎内部校验（如 `loadMyPendingTask()` 的角色/受派人二次校验、
`cancel()` 的提交人校验）。`README.md` 的「接口清单」只列了其中 8 个核心端点。

### 供其他模块实现的扩展点

| 类型 | 名称 | 说明 |
|---|---|---|
| 接口 | `ApprovalCallback` | 业务系统接入审批引擎的**唯一**入口。实现类标 `@Component`，由 `ObjectProvider<ApprovalCallback>` 在运行时收集 |
| 入参 DTO | `StartApprovalRequest` | 业务模块构造后交给 `ApprovalEngine.start(...)`，`bizSnapshot` 用于固化提交时的数据快照 |

#### 已接入的 5 个 `ApprovalCallback` 实现

| 模块 | 实现类 | `supports(bizType)` 匹配 |
|---|---|---|
| MDM | `mdm/callback/MdmApprovalCallback` | `CUSTOMER`、`SUPPLIER`、`MATERIAL`、`PRODUCT`、`BOM`、`ROUTING`（唯一同时覆写 `onNodeEntered` 的实现） |
| ERP | `erp/callback/ErpApprovalCallback` | `CREDIT_EXCEPTION` |
| CRM | `crm/callback/CrmApprovalCallback` | `QUOTATION*`（前缀匹配）、`CONTRACT` |
| PLM | `plm/callback/EcoApprovalCallback` | `ECO` |
| PLM | `plm/callback/EngineeringChangeApprovalCallback` | `ECN` |

#### 发起审批的 6 个调用点（`ApprovalEngine.start`）

| 调用方 | 传入的 `bizType` |
|---|---|
| `bootstrap/controller/DemoController`（`POST /api/demo/bom-change`） | `BOM` |
| `mdm/service/MasterDataService` | `BizType` 枚举动态值：`CUSTOMER` / `SUPPLIER` / `MATERIAL` / `PRODUCT` / `BOM` / `ROUTING` |
| `crm/service/CrmP2Service` | `QUOTATION` / `CONTRACT`，折扣≥10% 或毛利<15% 或金额≥50 万时升级为 `QUOTATION_RISK` |
| `erp/service/ErpP2Service` | `CREDIT_EXCEPTION` |
| `plm/service/EngineeringChangeService` | `ECN` |
| `plm/service/EngineeringChangeChainService` | `ECO` |

**审批动作（approve / reject / cancel / transfer / addSign / copyTo）没有任何业务模块直接调用**，
全部收敛在 `WorkflowController`——这是有意的单入口设计，改审批行为只需看一个文件。

## 数据表

**重要前提**：所有实体的 `@Table` 注解**都不带 `catalog` 属性**，库名由唯一数据源决定，
即 `bootstrap/application.yml` 指向的 **`mfg_auth`**。故实际解析结果如下：

| 注解里的表名 | 实际库.表 | 实体 | DDL 位置 |
|---|---|---|---|
| `wf_definition` | `mfg_auth.wf_definition` | `WfDefinition` | `infra/db-init/03_auth_workflow.sql:133` |
| `wf_node` | `mfg_auth.wf_node` | `WfNode` | `infra/db-init/03_auth_workflow.sql:148` |
| `wf_instance` | `mfg_auth.wf_instance` | `WfInstance` | `infra/db-init/03_auth_workflow.sql:169` |
| `wf_task` | `mfg_auth.wf_task` | `WfTask` | `infra/db-init/03_auth_workflow.sql:194`；`assigned_user` / `source_task_id` / `sign_mode` 三列由 Flyway `V16__p1_mdm_governance_workflow.sql:12` 追加 |
| `wf_action_log` | `mfg_auth.wf_action_log` | `WfActionLog` | `infra/db-init/03_auth_workflow.sql:216` |
| `wf_cc` | `mfg_auth.wf_cc` | `WfCc` | **Flyway** `V16__p1_mdm_governance_workflow.sql:13`（显式写 `mfg_auth.wf_cc`，是唯一不由 db-init 建的工作流表） |

流程定义的种子数据：`infra/db-init/08_seed_data.sql`（7 条）+ `infra/db-init/11_plm_ecn_workflow.sql`
（第 8 条 `PLM_ECN_CHANGE`，`biz_type = ECN`，两节点：研发经理 → 工艺主管）。

| 流程编码 | bizType | 审批链 |
|---|---|---|
| `MDM_CUSTOMER_NEW` | `CUSTOMER` | 销售主管 → 应收会计 |
| `MDM_SUPPLIER_NEW` | `SUPPLIER` | 供应商质量工程师 → 采购主管 |
| `MDM_MATERIAL_NEW` | `MATERIAL` | 工艺主管 → 生产计划员 |
| `MDM_BOM_CHANGE` | `BOM` | 工艺主管 → 生产主管 → 成本会计（三级） |
| `MDM_ROUTING_CHANGE` | `ROUTING` | 生产主管 |
| `MDM_CREDIT_CHANGE` | `CREDIT` | 财务主管 → 财务总监 |
| `ERP_PROD_ORDER_RELEASE` | `PROD_ORDER` | 生产主管 |
| **`PLM_ECN_CHANGE`** | **`ECN`** | 研发经理 → 工艺主管 |

## 未实现 / 缺口

| 缺口 | 现状与证据 |
|---|---|
| **会签 / 或签未实现** | `wf_node.approve_mode` 列已建模（注释写明 `SINGLE`/`ALL`/`ANY`，默认 `SINGLE`），但**全仓库无任何代码读取该字段**。`README.md` 也确认「本项目 7 条链均用 SINGLE」 |
| **超时催办 / 自动流转未实现** | `wf_node.timeout_hours` 与 `timeout_action`（`REMIND`/`AUTO_PASS`/`AUTO_REJECT`）两列已建，**无代码读取**；全仓库仅 2 个 `@Scheduled`（`BusinessEventService.dispatch`、`MdmOutboxService.dispatch`），均与审批无关 |
| **流程版本迁移未实现** | `wf_definition.def_version` 有列，但 `ApprovalEngine.start()` 查的是 `findByBizTypeAndEnabledTrue(bizType)`——**不带版本**，在途实例也不会随定义改版重新绑定。多版本共存/灰度未支持 |
| **加签 `AFTER` 与 `BEFORE` 行为相同** | `wf_task.sign_mode` 记录了两种模式，但 `approve()` 的判断是 `sourceTaskId != null && signMode != null`，只区分「是不是加签任务」，两种模式的实际流转顺序一致（都是加签人先审、再恢复原任务）。即 `BEFORE` 语义正确，`AFTER` 未真正实现 |
| **抄送已读状态未实现** | `wf_cc.is_read`（默认 `false`）与 `read_at` 两列已建，但**无任何代码写入**——没有「标记已读」端点，`/tasks/copied` 返回的永远是未读 |
| **无审批转办的历史串联** | 转交会新建任务并把原任务置 `TRANSFERRED`，但两任务之间只有 `source_task_id` 单向引用，时间轴上会显示为两条独立记录 |
| **`README.md` 的流程数已过期** | README 写「已配置的 7 条审批链」，实际种子数据已有 **8 条**（新增 `PLM_ECN_CHANGE`，来自 `11_plm_ecn_workflow.sql`）。另 README 的小节标题写作「七个可扩展点：ApprovalCallback」，而该接口实际是 3 个必实现方法 + 2 个可选方法，标题的数字对不上 |
| **4 个仓储方法无调用方** | `WfTaskRepository.findPendingAtNode`、`WfInstanceRepository.findByStatusOrderByIdDesc`、`WfDefinitionRepository.findByDefCodeAndEnabledTrue`、`WfDefinition.lastNodeSeq()` 均已实现但全仓库无调用，属预留 API（会签与超时若要实现，正需要 `findPendingAtNode`） |
| **无单元测试** | `src/test/` 不存在。状态机分支（尤其驳回两种去向、加签、撤回）只靠端到端脚本覆盖 |
| **实例号生成非并发安全** | `generateInstanceNo()` 用 `count()+1` 加循环重试，代码注释已声明「极端并发下可能有重号」 |
| **回调无补偿机制** | `invokeCallback()` 吞掉异常只记日志，无失败重试表。业务侧动作若要可靠投递，应改走 `shared/common` 的 `BusinessEventService` |
| **`DemoController` 仍是入口之一** | `POST /api/demo/bom-change` 可直接发起一条 `BOM` 审批，而 `docs/implementation-roadmap.md` 的 P1 验收标准明确要求「不使用 `DemoController` 的虚拟单据」 |
