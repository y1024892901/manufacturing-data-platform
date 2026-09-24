# 单据终态约定

> 计划 00 · F3-01 / F3-02 / F3-03 / F3-04 / F3-05 · 制定于 2026-09-22
>
> 对应 [功能目录](system-functional-catalog.md) 第 2 项验收：「至少 3 类核心单据可以从创建流转到关闭或作废」。
> 十系统中当前有 **8 个**走不到终态——本约定是它们统一补齐的依据。

## 一、终态取值（F3-01）

| 状态 | 语义 | 何时用 |
|---|---|---|
| `CLOSED` | **正常完结** —— 单据已完成其业务使命 | 订单交付并结算完毕、工单完工入库、维修单验收通过 |
| `VOIDED` | **作废** —— 单据已生效但被宣告无效 | 已下达的生产订单被取消、已发布的合同被撤销 |
| `CANCELED` | **撤销** —— 单据尚未生效即被取消 | 草稿/待审单据撤回、未确认的订单取消 |

> **`VOIDED` 与 `CANCELED` 的区别是「是否已经生效过」**。
> 未生效用 `CANCELED`，已生效用 `VOIDED`。这条区分决定了是否需要补冲销凭证/库存回滚。

### 终态不可逆

`CLOSED` / `VOIDED` / `CANCELED` **都是终态，不可再流转**。任何指向离开终态的迁移请求
必须被拒绝并返回业务错误码，而不是静默忽略。

## 二、端点模板（F3-01）

```
POST /api/<系统>/<单据>/{id}/close     # 正常完结
POST /api/<系统>/<单据>/{id}/void      # 作废（已生效单据）
POST /api/<系统>/<单据>/{id}/cancel    # 撤销（未生效单据）
```

- 请求体可空；需要理由的单据接受 `{"reason": "..."}`；
- 响应统一 `ApiResponse<单据实体>`，返回终态后的完整单据；
- **不使用 `DELETE`** —— 业务单据一律逻辑终态，不做物理删除。

### 合法迁移

```
                ┌──────────────────────────────────────────┐
                │                                          │
   DRAFT ──► PENDING ──► APPROVED ──► RELEASED ──► CLOSED   │  ← 正常主线
     │           │           │            │                │
     └──► CANCELED ◄─────────┘            └──► VOIDED ──────┘  ← 两条终态支线
```

- 各单据的**具体**状态名自定（工单用 `STARTED`/`FINISHED`、维修单用 `REPAIRING` 等），
  但**终态取值必须用上面三个词**，且必须有一个 `domain/` 下的状态枚举承载合法迁移表；
- 非法迁移（如从 `CLOSED` 回到 `RELEASED`）返回业务错误码 `MASTER_DATA_INVALID_STATE`
  或该模块既有的等价错误码，**不得返回 500**。

## 三、权限码（F3-02）

```
<系统码>:<单据>:CLOSE
<系统码>:<单据>:VOID
<系统码>:<单据>:CANCEL
```

- 已纳入 [permission-conventions.md](permission-conventions.md) 的动作受控词表；
- **定义与授予必须在同一个迁移里**（见附件约定：只定义不授予会造成「字典有码、无人有权」）；
- 授予口径：`CLOSE` 通常给**作业岗或主管**；`VOID` 因其影响面大，**只给主管级**。

## 四、状态机骨架模板（F3-03）

十系统里唯一已有的领域层是 MDM 的 `domain/MasterDataStatus`（7 态），以它为样板。
每个单据的状态枚举应形如：

```java
public enum WorkOrderStatus {
    CREATED, RELEASED, STARTED, PAUSED, FINISHED, CLOSED, CANCELED;

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALLOWED = Map.of(
        CREATED,  Set.of(RELEASED, CANCELED),
        RELEASED, Set.of(STARTED, CANCELED),
        STARTED,  Set.of(PAUSED, FINISHED),
        PAUSED,   Set.of(STARTED, CANCELED),
        FINISHED, Set.of(CLOSED),
        CLOSED,   Set.of(),      // 终态
        CANCELED, Set.of()       // 终态
    );

    public boolean canTransitionTo(WorkOrderStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean isTerminal() { return this == CLOSED || this == CANCELED; }
}
```

**要求**：
- 状态以**枚举**承载，不再用字符串字面量散落在 service 的 `if` 里（这是当前最普遍的写法）；
- 迁移校验放在枚举或 `domain/` 的领域服务中，service 调用它，controller 不判状态；
- `@Enumerated(EnumType.STRING)` 映射到既有的 `VARCHAR` 状态列，**不改变列类型**。

## 五、审计与时间轴（F3-04）

| 项 | 约定 |
|---|---|
| **审计** | 终态动作必须留痕：复用 `shared/security` 的 `OperationAuditService`（已有操作审计切面） |
| **`before_value` / `after_value`** | **已落实**：终态服务调用 `OperationAuditService.recordTransition()`，向实际存在的 `before_value` / `after_value` JSON 列写状态快照，并与业务共事务提交；表中不存在 `detail` 列。通用审计切面保持原有行为 |
| **状态时间轴** | 优先复用 `wf_action_log`（已走审批的单据）；未走审批的单据自建状态流水表，字段对齐 `wf_action_log` 的形态（`biz_type`/`biz_id`/`from_status`/`to_status`/`operator`/`reason`/`created_at`） |
| **操作人** | 一律取 `CurrentUser.usernameOrSystem()`，不接受前端传入 |

## 六、幂等要求（F3-05）

| 场景 | 期望行为 |
|---|---|
| 重复 `close` 已 `CLOSED` 的单据 | 返回**业务错误码**（如「单据已关闭」），或直接返回当前状态的成功响应——**二选一，但同一单据内保持一致** |
| 重复 `close` 已 `VOIDED` 的单据 | 返回业务错误码（终态冲突） |
| 并发两次 `close` | 服务方法内对单据行加锁（`SELECT ... FOR UPDATE` 或 JPA 乐观锁），保证只产生一次副作用 |
| 任何情况 | **不得 500**、**不得二次冲销**（重复生成冲销凭证、重复回滚库存都是严重的数据错误） |

## 七、各系统需新增的终态动作

按 [current-status.md](current-status.md) 的验收矩阵，8 个系统缺终态。各系统在自己的计划里
（02–07）按本约定列出「需要新增哪几个动作」并落地。参考清单：

| 系统 | 建议先做的单据 | 动作 |
|---|---|---|
| ERP | 销售订单、生产订单、付款单 | `close` / `void` / `cancel` |
| CRM | 商机、报价、合同 | `close`（赢单/丢单）/ `void` |
| SRM | 采购订单、ASN | `close` / `void` |
| WMS | 收货单、盘点单、调拨单 | `close` / `cancel` |
| MES | 工单、报工单 | `close` / `cancel` |
| QMS | 检验单、不合格单、返工单 | `close` / `void` |
| EAM | 维修单、点检单 | `close` / `cancel` |
| 能源 | 能耗台账、定额单 | `close` / `void` |

> MDM 与 PLM 已有较完整的终态（`DISABLED` / `IMPLEMENTED`），
> 但 MDM 的四类主数据**缺停用接口**（走不到 `DISABLED`）—— 那是 F3-06 的试点内容。

## F3-06 已实现样例（2026-09-23）

`POST /api/mdm/{materials|products|boms|routings}/{id}/disable` 使用对应 `MDM:<对象>:DISABLE` 权限。
`MasterDataTerminalService` 通过 JPA 悲观写锁执行 `PUBLISHED → DISABLED`；重复停用、草稿或审批中停用返回业务错误。
四类均不再出现在可消费列表中。停用审计和 `<类型>.DISABLED` Outbox 记录共事务写入。
副本同步仍复用现有 MDM 分发实现，副本字段与只读校验的全面治理属于计划 02。
权限与角色同批迁移为 V40.2。四类状态转换及重复请求已有单元测试；完整 HTTP 生命周期仍待验收。
