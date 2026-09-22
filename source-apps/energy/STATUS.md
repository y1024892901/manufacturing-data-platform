# energy/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

能源模块目前**只有能耗数据录入与单位能耗自动计算**（2 张台账表、2 个实体、4 个接口），
两个实体都没有状态字段，因此没有单据流转、没有审批、没有事件、没有报表——**最低验收五项全部未达标**，是三个待建模块中差距最大的一个。

## 已实现

### 分层文件统计

`source-apps/energy/src/main/java/com/mfg/energy/` 下共 **7 个 Java 文件**：

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `controller/` | 2 | `EquipmentUsageController`、`WorkshopUsageController` |
| `entity/` | 2 | `EquipmentUsage`、`WorkshopUsage` |
| `repo/` | 2 | `EquipmentUsageRepository`、`WorkshopUsageRepository` |
| 其他 | 1 | `package-info.java` |
| `domain/` | **0** | 未实现 |
| `service/` | **0** | 未实现 |
| `workflow/` | **0** | 未实现 |
| `integration/` | **0** | 未实现 |
| `query/` | **0** | 未实现 |

已实现的业务动作：

| 动作 | 位置 | 说明 |
|---|---|---|
| 能耗非负校验 | 两个 Controller 的 `create` | `energyValue == null \|\| signum() < 0` 时抛 `MASTER_DATA_INVALID_STATE`（"能耗量必须为非负数"） |
| 日期缺省 | 两个 Controller 的 `create` | `statDate` 为空时取 `LocalDate.now()` |
| 能源介质缺省 | `WorkshopUsageController.create` | `energyType` 缺省为 `ELECTRIC`；`unitCode` 缺省为 `KWH`（设备级靠实体字段默认值达到同样效果） |
| 单位能耗自动计算 | 两个 Controller 的 `create` | `energyValue / outputQty`（车间级用 `totalOutput`），保留 6 位小数、`RoundingMode.HALF_UP`，仅当产出 > 0 时计算 |
| 车间能耗去重 | `WorkshopUsageController.create` | `existsByWorkshopCodeAndStatDateAndEnergyType` 命中则抛 `MASTER_DATA_ALREADY_EXISTS`（"该车间日期与能源介质已有能耗记录"） |

> 与 EAM 的差异：能源模块**完全没有调用 `CurrentUser`**，也不引用 `BizException.notFound`，
> 即没有操作人记录、也没有按 ID 查单条详情的入口。

## 接口清单

遍历 `@RestController` / `@RequestMapping` / `@GetMapping` / `@PostMapping`，共 **4 个接口**：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/energy/usages` | 设备能耗上报（校验非负、默认当日、按产量算单位能耗） |
| GET | `/api/energy/usages` | 设备能耗全量列表（`findAll()`，**无分页、无筛选**） |
| POST | `/api/energy/workshop-usages` | 车间能耗上报（额外做「车间+日期+介质」去重，并补齐介质与单位默认值） |
| GET | `/api/energy/workshop-usages` | 车间能耗全量列表（`findAll()`，**无分页、无筛选**） |

> 权限：`SecurityConfig` 对 `/api/energy/**` 统一要求 `hasAuthority("SYSTEM_ENERGY")`，
> 该 authority 来自 `sys_user_system` 表（`admin`、`xujing` 已授予 `energy`）。
> 代码中**没有任何 `@PreAuthorize`**，因此种子里的 3 个权限点（`ENERGY:USAGE:VIEW`、`ENERGY:USAGE:CREATE`、
> `ENERGY:ANALYSIS:VIEW`）都没被接口读取；其中 `ENERGY:ANALYSIS:VIEW` 已授予 `FINANCE_DIRECTOR` 等角色，
> 但**对应的分析接口并不存在**。

## 数据表

2 个实体的 `@Table` 注解均为 `catalog = "src_energy"` 的跨库限定名写法：

| 实体 | 表 | 关键字段 |
|---|---|---|
| `EquipmentUsage` | `src_energy.energy_equipment_usage` | `equipment_code`、`stat_date`、`stat_hour`、`energy_type`(默认 `ELECTRIC`)、`energy_value`、`unit_code`(默认 `KWH`)、`run_hours`、`output_qty`、`unit_consumption` |
| `WorkshopUsage` | `src_energy.energy_workshop_usage` | `workshop_code`、`stat_date`、`energy_type`、`energy_value`、`unit_code`、`total_output`、`unit_consumption` |

**建表来源（重要）**：这两张表**不在 Flyway 迁移里**，由
`infra/db-init/05_business_systems_2.sql` 建库脚本创建（全库 27 个 Flyway 脚本 V12–V38 中没有任何 `src_energy` 语句）。
Flyway 基线版本为 11，V1–V11 正是 `infra/db-init/` 下的脚本，两者是**两套并行的建表通道**。

同库中还有 1 张**有表无数据无代码**的能源表：

| 表 | 状态 |
|---|---|
| `src_energy.energy_md_equipment` | 有 DDL（MDM 设备主数据只读副本），但 `MasterDataDistributor.writeToEnergy()` 是 `return 0;` 的空实现，**该表从未被写入**。因此能源系统目前无法校验 `equipment_code` 是否合法——设备编码是自由文本 |

数据库层还定义了两个唯一键，但只有车间级被代码利用：

| 唯一键 | 表 | 代码是否预检 |
|---|---|---|
| `uk_ws_energy (workshop_code, stat_date, energy_type)` | `energy_workshop_usage` | **是**，`WorkshopUsageController` 主动查重后报友好错误 |
| `uk_energy_equip (equipment_code, stat_date, stat_hour, energy_type)` | `energy_equipment_usage` | **否**，`EquipmentUsageController` 无查重，重复上报会直接抛数据库约束异常（500 而非业务错误） |

## 对照最低验收

| 验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| ① 至少 5 个本职模块具有实体、服务、接口和角色权限 | **未达标** | 仅 2 个实体、4 个接口、**零 service 层**；6 个本职模块中 4 个部分实现、2 个完全未实现；`ENERGY:*` 权限点未被代码引用 |
| ② 至少 3 类核心单据可以从创建流转到关闭或作废 | **未达标** | **两个实体都没有状态字段**（`EquipmentUsage`、`WorkshopUsage` 均无 `status`）。只有 INSERT 与全量 SELECT，没有提交/审核/关闭/作废任何一环，存在 **0 类**可流转单据 |
| ③ 至少 1 个审批流程和 1 个异常处理闭环 | **未达标** | 无审批（`wf_definition` 无能源类 `biz_type`）。异常管理模块整块未实现：`is_abnormal` 列虽在 DDL 中，实体未映射，无阈值、无告警、无处置确认 |
| ④ 至少 2 个向其他系统发送或消费的幂等业务事件 | **未达标** | 不发出任何事件（无 `integration/` 层、不写 `mfg_ops.biz_outbox`）；**消费侧也是空的**——`writeToEnergy()` 空实现，连 MDM 设备主数据都没接 |
| ⑤ 至少 1 页本系统运营查询或统计报表 | **未达标** | 两个 GET 都是 `findAll()` 返回全表 List，无分页、无按日期/车间/介质筛选、无任何聚合统计 |

## 未实现 / 缺口

按补齐优先级排列：

1. **无 `service/` 层**（结构性缺口）。单位能耗计算逻辑在两个 Controller 里各写一遍，属重复代码；无事务边界。
2. **无 `domain/` 层**。`energyType` 仅靠 DDL 注释约定 `ELECTRIC`/`GAS`/`WATER`/`STEAM`，代码无枚举校验，可写入任意字符串，导致同一介质出现 `electric`/`ELECTRIC` 两种写法时去重失效。
3. **无状态、无单据流转**。两张台账表都是「只进不改」的事实表，这本身合理（台账不应有审批流），但目录要求的「定额」「异常处置」等**需要流转的单据**一个都还没有。
4. **设备级台账缺查重**（明确缺陷）。`uk_energy_equip` 唯一键含 `stat_hour`，而 `EquipmentUsageController` 不做预检，重复上报时报 500 数据库异常而非业务错误；与车间级的处理方式不一致。
5. **`energy_md_equipment` 空转**。MDM 侧 `MasterDataDistributor.writeToEnergy()` 为 `return 0;`，设备主数据没分发，`equipment_code` 无法校验，也无法与 EAM 做设备级对账。
6. **定额与目标未落地**。`baseline_value`、`deviation_rate` 两列已在 DDL 中，但**实体未映射 `baseline_value`/`deviation_rate`/`is_abnormal` 三列**，接口层完全无法读写；无线损定额、无目标值、无同比环比。
7. **无峰平谷与分摊**。无 `PEAK`/`FLAT`/`VALLEY` 时段字段、无单价与电费，无法做成本核算；车间级台账也没有从设备级台账**汇总生成**的逻辑——目前靠人工分别录入两张表，存在对不上的风险。
8. **无统计查询**。无日/月用能汇总、无单位能耗趋势、无车间排名、无超定额清单。
9. **碳管理整块缺失**。无折标系数、无排放因子、无碳排估算、无订单碳足迹——目录 6 个模块中的最后一个尚未起步。
