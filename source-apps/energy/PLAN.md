# energy/ — 能源管理系统

> **整体计划** · 依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 **能源管理：能源计量、分析与控制** 章节

## 系统定位

能源管理把「设备/车间的用能读数」变成可管理的数据：上报能耗 → 自动算出单位能耗 → 逐级汇总到车间，
为定额考核、异常识别和碳排估算提供基础。

它与 EAM 同属**设备域**（`meta_system.business_domain = '设备域'`，`owner_dept = '设备与能源中心'`），
但职责不同：EAM 管设备的**健康**，能源管设备的**耗用**。两者通过设备编码关联——
`energy_md_equipment` 就是 MDM 分发给能源系统的设备主数据只读副本。

当前实现是整个平台里**最薄的一个切片**：2 张台账表、2 个实体、4 个接口，
只有「手工上报能耗 + 单位能耗自动计算」这一件事，定额、异常、碳管理三块均未开始。

## 本职模块基线

抄录 `docs/system-functional-catalog.md` 的能源管理章节，并按实际代码判定当前状态：

| 模块 | 核心能力（目录要求） | 当前状态 | 判定依据 |
|---|---|---|---|
| 能源基础 | 能源品类、仪表、采集点、计量层级、设备/车间映射 | **部分实现** | 实体有 `energyType`（`ELECTRIC`/`GAS`/`WATER`/`STEAM`）与 `unitCode`（默认 `KWH`）；`src_energy.energy_md_equipment` 副本表承载设备映射。但无能源品类主数据表、无仪表、无采集点、无计量层级 |
| 数据采集 | 抄表、自动采集、补录、校验、缺失数据处理 | **部分实现** | 两个手工上报接口 `POST /api/energy/usages`、`POST /api/energy/workshop-usages`，含非负校验与单位能耗自动计算。无抄表计划、无自动采集、无补录、无缺失数据处理 |
| 能源台账 | 日/月用能、峰平谷、电水气热、设备/车间/订单分摊 | **部分实现** | 设备级 `energy_equipment_usage`（含 `statDate` + `statHour`）与车间级 `energy_workshop_usage` 两张台账；`energyType` 覆盖电水气汽。无月度汇总、无峰平谷（无 `PEAK`/`FLAT`/`VALLEY` 字段）、无订单分摊 |
| 定额与目标 | 单耗定额、目标、预算、能耗基线、同比环比 | **部分实现** | DDL 里 `baseline_value`（能耗基线）与 `deviation_rate`（偏离率）两列已建好，**但实体未映射、无任何接口读写**，等于有列无功能；无定额维护、无同比环比 |
| 异常管理 | 阈值、偏离、告警、处置、确认、节能措施追踪 | **未实现** | `is_abnormal` 列存在于 DDL（注释标注为治理规则 E05 的检测对象），实体未映射。无阈值配置、无告警、无处置与确认闭环 |
| 碳管理 | 能源折标、排放因子、碳排估算、订单碳足迹 | **未实现** | 无折标系数、无排放因子、无碳排相关类或字段 |

## 代码分层标准

`docs/system-functional-catalog.md` 规定每个系统统一按 8 层实现。本模块**实际只用了其中 3 层**：

| 分层 | 本模块状态 | 说明 |
|---|---|---|
| `domain/` | **空** | 无枚举。`ELECTRIC`/`GAS`/`WATER`/`STEAM` 只是 DDL 注释里的约定，代码中没有任何类型约束——接口可以写入任意 `energyType` 字符串 |
| `entity/` | 已使用（2 个） | `EquipmentUsage`、`WorkshopUsage` |
| `repo/` | 已使用（2 个） | `WorkshopUsageRepository` 声明了 `existsByWorkshopCodeAndStatDateAndEnergyType`；`EquipmentUsageRepository` 是空的 `JpaRepository` |
| `service/` | **空** | 默认值填充、非负校验、单位能耗计算全部内联在 Controller 中 |
| `controller/` | 已使用（2 个） | `EquipmentUsageController`、`WorkshopUsageController` |
| `workflow/` | **空** | 未接入审批。全库 `wf_definition` 的 `biz_type` 中没有能源相关类型 |
| `integration/` | **空** | 不发出事件、不写 `mfg_ops.biz_outbox`。作为接收方，MDM 侧 `MasterDataDistributor.writeToEnergy()` **当前是空实现（`return 0;`）**，因此 `energy_md_equipment` 表建了但从未被写入 |
| `query/` | **空** | 两个列表接口都是 `List<T> findAll()`，**既不筛条件也不分页**，无任何统计查询 |

> 唯一有实质逻辑的地方是单位能耗：`energyValue.divide(outputQty, 6, RoundingMode.HALF_UP)`，
> 两张台账各算一次、算法相同但**代码重复**（`EquipmentUsageController` 与 `WorkshopUsageController` 各写一份）。

## 最低验收（五项）

抄录 `docs/system-functional-catalog.md` 末尾「系统本职完成的最低验收」：

1. 至少 5 个本职模块具有实体、服务、接口和角色权限；
2. 至少 3 类核心单据可以从创建流转到关闭或作废；
3. 至少 1 个审批流程和 1 个异常处理闭环；
4. 至少 2 个向其他系统发送或消费的幂等业务事件；
5. 至少 1 页本系统运营查询或统计报表。

> 当前达标情况见 [STATUS.md](STATUS.md) 的「对照最低验收」表——**五项全部未达标**。
> 能源模块是三个待建模块中差距最大的：只有单表录入，没有单据流转、没有审批、没有事件、没有报表。
