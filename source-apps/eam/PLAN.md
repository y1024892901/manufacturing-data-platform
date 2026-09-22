# eam/ — 设备资产管理系统

> **整体计划** · 依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 **EAM：设备资产与维修管理** 章节

## 系统定位

EAM 是设备域的记录系统（system of record）：设备台账、故障、点检、维修四类数据在这里产生并维护，
其它系统只是引用或回写（例如数仓回写 `health_score` / `health_level`）。

在制造业务链上，EAM 是**停机的起点**：MES 工单在设备上执行，设备故障 → 停机 → 影响工序与订单交期。
因此 EAM 的数据必须能穿透到「设备 → 工序 → 工单 → 生产订单」这条传导链——
现有实体已经预留了 `work_order_no` / `prod_order_no` / `operation_code` 三个字段承接这条链。

当前实现是一个**可独立维护的演示切片**：4 张业务表、4 个实体、12 个接口，
覆盖「设备建档 → 故障上报 → 维修 → 恢复」这条最小闭环，尚未覆盖点检计划、停机台账、备件与可靠性。

## 本职模块基线

抄录 `docs/system-functional-catalog.md` 的 EAM 章节，并按实际代码判定当前状态：

| 模块 | 核心能力（目录要求） | 当前状态 | 判定依据 |
|---|---|---|---|
| 资产台账 | 设备树、位置、型号、关键设备、生命周期、附件、状态 | **部分实现** | `EquipmentController` 有建档/分页/状态切换；`Equipment` 有位置、型号、类型、状态。无设备树、无附件、无生命周期字段暴露 |
| 点检保养 | 点检计划、保养计划、任务单、移动点检、漏检预警 | **部分实现** | 有 `EquipmentInspection` 与「创建 → 完成点检」两个接口。无点检/保养计划排程，`is_missed` 字段写死为 `false`，漏检预警未实现 |
| 维修管理 | 维修申请、故障报码、维修工单、派工、工时、维修结果 | **部分实现** | `EquipmentRepairController` 覆盖故障关联、开始维修、完工结论三态。无派工（`repairmanCode` 取当前登录人）、无工时审批 |
| 停机管理 | 停机开始/恢复、停机原因、影响设备/工序/订单、损失工时 | **部分实现** | `downtimeMinutes` 由维修起止时间自动算出；故障单已带 `workOrderNo`/`prodOrderNo`/`operationCode`。但无独立的停机开始/恢复接口，`eam_equipment_status_log` 台账表无任何代码使用 |
| 备件协同 | 备件台账、领用、归还、最低库存、维修成本 | **未实现** | 仅有只读副本表 `src_eam.eam_md_material`（由 MDM 分发写入）。无备件领用/归还/最低库存接口；`replacedParts` 只是维修单上的一个文本字段 |
| 可靠性 | 故障模式、MTBF、MTTR、维修履历、健康评分 | **未实现** | 无故障模式编码、无 MTBF/MTTR 计算逻辑。`healthScore`/`healthLevel` 字段由数仓回写，EAM 自身不产出评分 |

## 代码分层标准

`docs/system-functional-catalog.md` 规定每个系统统一按 8 层实现。本模块**实际只用了其中 3 层**：

| 分层 | 本模块状态 | 说明 |
|---|---|---|
| `domain/` | **空** | 无状态机/枚举类。状态取值以字面量集合内联在 Controller 中，例如 `Set.of("RUNNING","IDLE","FAULT","MAINTENANCE","SCRAPPED")` |
| `entity/` | 已使用（4 个） | `Equipment`、`EquipmentFault`、`EquipmentInspection`、`EquipmentRepair` |
| `repo/` | 已使用（4 个） | 均为 `JpaRepository`，只额外声明了 `existsBy*` / `findByEquipmentCode` |
| `service/` | **空** | 业务动作（建档查重、故障联动设备状态、维修算停机时长）全部写在 Controller 方法体内，无 `@Transactional` 服务层 |
| `controller/` | 已使用（2 个） | `EquipmentController`、`EquipmentRepairController` |
| `workflow/` | **空** | 未接入审批引擎。全库 `wf_definition` 的 `biz_type` 中没有 EAM，权限点 `EAM:FAULT:APPROVE` 已种下但无流程可用 |
| `integration/` | **空** | EAM 全程只做**接收方**：MDM 的 `MasterDataDistributor.writeToEam()` 直接写 `src_eam.eam_md_material`。EAM 自身不发出事件、不写 `mfg_ops.biz_outbox` |
| `query/` | **空** | 列表查询一律 `findAll(PageRequest)`，无按状态/车间/时间的条件查询，无统计报表接口 |

> 分层缺口合计 5 层。补齐顺序建议：`domain/`（状态枚举）→ `service/`（事务边界）→ `query/`（台账与统计）→ `workflow/` → `integration/`。

## 最低验收（五项）

抄录 `docs/system-functional-catalog.md` 末尾「系统本职完成的最低验收」：

1. 至少 5 个本职模块具有实体、服务、接口和角色权限；
2. 至少 3 类核心单据可以从创建流转到关闭或作废；
3. 至少 1 个审批流程和 1 个异常处理闭环；
4. 至少 2 个向其他系统发送或消费的幂等业务事件；
5. 至少 1 页本系统运营查询或统计报表。

> 当前达标情况见 [STATUS.md](STATUS.md) 的「对照最低验收」表——**五项均未完全达标**，
> 主要缺口集中在：服务层缺失、无审批、无事件、无报表。
