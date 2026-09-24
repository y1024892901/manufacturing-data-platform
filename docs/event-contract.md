# 业务事件契约

> 计划 00 · F2-01 · 制定于 2026-09-22
>
> 背景见 [current-status.md](current-status.md) 横向共性问题 2：「事件只发不收」——
> `BusinessEventService` 已把事件写入 `mfg_ops.biz_outbox` 并投递 `biz_inbox`，
> 2026-09-23 已接入 `ReceiptInspectionConsumer`，收货事件真实创建 QMS 检验单，并通过事务回滚式数据库烟测。

## 一、命名规范

```
event_type = <系统码>.<聚合>.<动作>
```

- **系统码小写**（`crm` / `erp` / `wms` / `qms` / `srm` / `mes` / `eam` / `energy` / `mdm` / `plm`）；
- 聚合用大写下划线（`SALES_ORDER`、`RECEIPT`、`PURCHASE_ORDER`）；
- 动作用过去式表示「已发生」（`CREATED`、`JUDGED`、`SETTLED`）。

> **注意**：现有事件类型写作 `ERP.SALES_ORDER.CREATED`（前缀大写）。
> 这与「系统码小写」的规范**不一致**，但既已发布且被多处引用，**本阶段保留现状**，
> 只在 `source_system` / `target_system` 两个字段上强制小写（V40 已归一化历史数据）。
> 新增事件类型应遵循规范写作 `erp.sales_order.created`。

## 二、事件路由表

`source_system` / `target_system` **一律小写**（与 `sys_permission.system_code` 一致）。

| 事件类型 | 源 | 目标 | 载荷字段 | 消费方 | 状态 |
|---|---|---|---|---|---|
| `SRM.PURCHASE_ORDER.SENT` | srm | wms | `purchaseOrderNo` | WMS 收货准备 | 待接入 |
| `WMS.RECEIPT.PENDING_INSPECTION` | wms | qms | `receiptNo`、`inspectionNo` | **QMS 生成检验单** | **已实现，数据库烟测通过** |
| `QMS.INSPECTION.JUDGED` | qms | wms | `inspectionNo`、`result` | **WMS 库存状态转换** | 尚未异步化，现仍由 QMS 同事务直写 |
| `QMS.NCR.DISPOSED` | qms | srm / wms | `ncrNo`、`disposition` | SRM 供应商质量 / WMS 处置 | 待接入 |
| `CRM.CONTRACT.ORDER_REQUESTED` | crm | erp | `contractNo`、`salesOrderNo` | ERP 销售订单 | 暂不消费（已同进程直写） |
| `CRM.CONTRACT.ACTIVATED` | crm | erp | — | — | 暂不消费 |
| `ERP.SALES_ORDER.CREATED` | erp | crm | `sourceContractNo` | CRM 回写订单号 | 暂不消费（已同进程直写） |
| `ERP.SALES_ORDER.CONFIRMED` | erp | crm | — | — | 暂不消费 |
| `ERP.PRODUCTION_ORDER.RELEASED` | erp | mes | — | MES 生成工单 | 待接入（MES 计划 06） |
| `ERP.MRP.COMPLETED` | erp | crm | — | — | 暂不消费 |
| `ERP.RECEIPT.SETTLED` | erp | crm | — | — | 暂不消费 |

> **「暂不消费」是明确结论，不是遗留项**。这些事件的跨系统效果当前由**同进程跨库直写**实现
> （见 F2-06 的直写清单）；把它们改成事件驱动会改变事务边界与失败语义，
> 属各系统计划（02–07）的范围，本计划只做路由表登记。

## 三、载荷与版本

- `payload` 是自由 JSON，**本阶段不引入 schema 校验**（对历史事件不做回溯校验）；
- `biz_outbox.event_version` 列已存在但从未写入非默认值。新增事件应写入 `1`；
  消费方按 `event_version` 分支处理是**后续**的演进手段，当前所有事件按版本 1 处理。

## 四、消费语义（F2-03 / F2-04）

| 项 | 约定 |
|---|---|
| **幂等键** | 复用 `uk_biz_inbox_event_target(event_id, target_system)`；同一事件对同一目标系统只会有一条 inbox 行 |
| **领取** | 消费调度器按 `consume_status='PENDING'` 且 `next_retry_at <= NOW()` 领取，用 `FOR UPDATE SKIP LOCKED` + `lease_until` 防止多实例重复消费 |
| **成功** | `consume_status='CONSUMED'`、写 `consumer_name` 与 `consumed_at` |
| **失败** | `retry_count+1`、记 `error_message`、按指数退避设置 `next_retry_at` |
| **死信** | `retry_count` 超过上限（默认 5）转 `consume_status='DEAD'`，不再自动重试，可经人工重放端点恢复为 `PENDING` |
| **无消费方** | 事件类型在注册表中无对应消费方时，**记为「无消费方」而非静默丢弃**——便于发现路由表与实际实现脱节 |
| **单进程假设** | 现有 `BusinessEventService.dispatch()` 是单 `@Transactional` + 逐行 `FOR UPDATE`，多实例下锁语义不成立。**本实现面向单进程演示环境**，不作为生产级方案 |

## 五、消费方实现约定

消费方实现 `BusinessEventConsumer` 接口（`source-apps/shared/common/.../integration/`）：

```java
public interface BusinessEventConsumer {
    /** 本消费方负责的事件类型（与路由表的 event_type 一致） */
    Set<String> supports();
    /** 目标系统码，与事件类型共同匹配 */
    String targetSystem();
    /** 消费方标识，写入 biz_inbox.consumer_name */
    String name();
    /** 消费动作。抛出异常即视为失败，由调度器重试 */
    void consume(BusinessEvent event);
}
```

- Spring 自动收集所有实现类；**未注册消费方的事件类型会在派发时被记录**，不静默丢弃；
- 消费动作**必须幂等**：同一条事件重复投递只产生一次业务效果；
- 消费方放在各系统的 `integration/` 包下——该包在多数系统**尚不存在**，需新建。

## 六、跨库直写清单（F2-06）

当前「同进程直接写他系统库」的调用点，逐条给出结论：

| 调用点 | 直写目标 | 结论 |
|---|---|---|
| `MasterDataDistributor` 各 `writeTo*()` | 9 个系统的 `*_md_*` 副本表 | **保留**。主数据分发是「推副本」而非业务事件，语义上就是一份数据同步；改成事件会让下游必须自己拉全量。已在 Outbox 中记录分发事件 |
| `CrmP2Service.createOrder()` | `src_erp.erp_sales_order` | **保留并记录理由**：跨库事务边界由同一 `@Transactional` 保证，改成事件会失去原子性 |
| `P3FlowService.arriveAsn()` | WMS 收货、QMS 待检 | **已改为事件驱动**：保留 WMS 收货直写，QMS 检验单改由消费者生成 |
| `QmsLifecycleService.judge()` | `src_wms` 库存动作、`src_srm.srm_supplier_quality` | **本轮保留同事务直写**：试点选定收货→检验一条链；库存动作依赖既有 ledger 幂等，后续改造须同时消除双写 |
| `EngineeringChangeChainService.implement()` | `src_mdm.md_bom` / `md_routing` | **保留并记录理由**：ECN 实施的产物是 MDM 主数据草稿，属「变更链的最后一环」，不是异步通知 |

> 结论口径：**数据同步与需要原子性的写入保留直写；纯通知性质的联动改为事件驱动。**

## 2026-09-23 消费实现补充

- `TransactionTemplate` 显式分隔领取、业务提交、失败记录事务，避免同类自调用绕过 `@Transactional`。
- 每次领取一行，使用 `FOR UPDATE SKIP LOCKED` 和 60 秒租约；`consumer_name` 暂存 `lease:<UUID>`，成功后写真实消费者名。旧租约持有者不得提交。
- 按 `(event_type, target_system)` 选择唯一消费者。无实现或重复注册转死信；非法 JSON 进入失败重试，不按空载荷成功处理。
- 业务效果、`CONSUMED` 和位点更新共事务；首次失败后最多自动重试五次，第六次失败转 `DEAD`，失败次数保留。
- `GET /api/events/business/inbox/dead-letters` 使用 `SYS:EVENT:VIEW`；`POST /api/events/business/inbox/replay` 使用 `SYS:EVENT:EXECUTE`，请求为最多 200 个 ID 的数组，非失败状态返回业务错误。
- `publish()` 强制系统码小写；V40 使用二进制比较修复大小写不敏感排序规则导致的历史数据漏更新。
- 烟测命令：`python ops/run_foundation_smoke.py --java D:/jdk-17.0.20/bin/java.exe`。全部测试业务数据回滚，验证真实消费、重复业务事件与重复扫描幂等。
