# eam/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

EAM 已跑通「设备建档 → 故障上报 → 维修完工 → 设备恢复」这条最小闭环（12 个接口、5 张业务表），
但**只用了 entity/repo/controller 三层**，服务层、审批、事件、报表四个方向均为空白，尚未达到最低验收标准。

## 已实现

### 分层文件统计

`source-apps/eam/src/main/java/com/mfg/eam/` 下共 **13 个 Java 文件**：

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `controller/` | 2 | `EquipmentController`、`EquipmentRepairController` |
| `entity/` | 5 | `Equipment`、`EquipmentFault`、`EquipmentInspection`、`EquipmentRepair`、`EquipmentStatusLog` |
| `repo/` | 5 | `EquipmentRepository`、`EquipmentFaultRepository`、`EquipmentInspectionRepository`、`EquipmentRepairRepository`、`EquipmentStatusLogRepository` |
| 其他 | 1 | `package-info.java` |
| `domain/` | **0** | 未实现 |
| `service/` | **0** | 未实现 |
| `workflow/` | **0** | 未实现 |
| `integration/` | **0** | 未实现 |
| `query/` | **0** | 未实现 |

已实现的业务动作：

| 动作 | 位置 | 说明 |
|---|---|---|
| 设备建档查重 | `EquipmentController.createEquipment` | `existsByEquipmentCode` 命中则抛 `MASTER_DATA_ALREADY_EXISTS`；新建设备状态强制为 `IDLE` |
| 设备状态白名单校验 | `EquipmentController.status` | 仅接受 `RUNNING`/`IDLE`/`FAULT`/`MAINTENANCE`/`SCRAPPED`，非法值抛 `MASTER_DATA_INVALID_STATE` |
| 故障上报联动 | `EquipmentController.fault` | 按 `equipmentCode` 反查设备取名称、置设备为 `FAULT`、写 `faultTime=now`、`reportedBy=CurrentUser`、状态置 `OPEN` |
| 故障关闭联动 | `EquipmentController.close` | 故障置 `CLOSED`，同时把设备恢复为 `IDLE` |
| 点检完成 | `EquipmentController.completeInspection` | 结果仅接受 `NORMAL`/`ABNORMAL`，回写 `actualDate=now` 与 `inspectorCode` |
| 维修单三重校验 | `EquipmentRepairController.create` | ① 维修单号唯一；② 关联故障单存在；③ 维修设备必须与故障设备一致；④ 仅 `OPEN` 故障可建维修单 |
| 停机时长自动计算 | `EquipmentRepairController.complete` | `ChronoUnit.MINUTES.between(start, end)`，取 `Math.max(0, …)` 防负数 |
| 维修完工联动 | `EquipmentRepairController.complete` | 结论为 `REPAIRED` 时，故障置 `CLOSED` 且设备置 `IDLE`（`PENDING_PARTS`/`SCRAPPED` 不联动） |

## 接口清单

遍历 `@RestController` / `@RequestMapping` / `@GetMapping` / `@PostMapping`，共 **12 个接口**：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/eam/equipments` | 设备分页查询（`page` 默认 1、`size` 默认 20、上限 200） |
| POST | `/api/eam/equipments` | 设备建档（编码查重，初始状态 `IDLE`） |
| POST | `/api/eam/equipments/{id}/status` | 切换设备状态（五态白名单） |
| GET | `/api/eam/faults` | 故障分页查询 |
| POST | `/api/eam/faults` | 故障上报（联动设备转 `FAULT`） |
| POST | `/api/eam/faults/{id}/close` | 关闭故障（联动设备转 `IDLE`） |
| GET | `/api/eam/inspections` | 点检分页查询 |
| POST | `/api/eam/inspections` | 创建点检单（初始 `result=null`、`missed=false`） |
| POST | `/api/eam/inspections/{id}/complete` | 完成点检（`result` 必填，`abnormalDesc` 可选） |
| GET | `/api/eam/repairs` | 维修单分页查询 |
| POST | `/api/eam/repairs` | 创建维修单（校验故障存在、设备一致、故障未关闭） |
| POST | `/api/eam/repairs/{id}/complete` | 维修完工（`result` 限 `REPAIRED`/`PENDING_PARTS`/`SCRAPPED`，自动算停机时长） |

> 权限：`SecurityConfig` 对 `/api/eam/**` 统一要求 `hasAuthority("SYSTEM_EAM")`，
> 该 authority 由 `sys_user_system` 表映射产生（`admin`、`xujing` 两个演示账号已授予 `eam`）。
> 代码中**没有任何 `@PreAuthorize`**，因此种子里那 6 个 `EAM:*` 权限点（`EAM:EQUIPMENT:VIEW`、
> `EAM:FAULT:CREATE`、`EAM:FAULT:APPROVE`、`EAM:REPAIR:FINISH`、`EAM:INSPECTION:CREATE`、
> `EAM:INSPECTION:PLAN`）**只是数据，不被任何接口读取**——目前是系统级门禁，不是细粒度权限。

## 数据表

4 个实体的 `@Table` 注解均为 `catalog = "src_eam"` 的跨库限定名写法：

| 实体 | 表 | 关键字段 |
|---|---|---|
| `Equipment` | `src_eam.eam_equipment` | `equipment_code`(唯一)、`equipment_name`、`equipment_model`、`equipment_type`、`workshop_code`、`work_center`、`location_desc`、`capacity_per_hour`、`equipment_status`(默认 `IDLE`)、`health_score`、`health_level`、`purchase_date`、`purchase_price`、`warranty_end_date`、`maintenance_cycle_days`、`last_maintenance_date`、`created_at`、`updated_at` |
| `EquipmentFault` | `src_eam.eam_fault` | `fault_no`、`equipment_code`、`fault_type`、`fault_level`、`fault_desc`、`fault_cause`、`fault_time`、`work_order_no`、`prod_order_no`、`operation_code`、`fault_status`(默认 `OPEN`)、`reported_by`、`created_at`、`updated_at` |
| `EquipmentInspection` | `src_eam.eam_inspection` | `inspection_no`、`equipment_code`、`inspection_type`、`plan_date`、`actual_date`、`inspection_result`、`abnormal_desc`、`check_items`(JSON，按 `String` 映射)、`inspector_code`、`is_missed`(默认 `false`)、`created_at` |
| `EquipmentRepair` | `src_eam.eam_repair` | `repair_no`、`fault_no`、`equipment_code`、`repair_start_time`、`repair_end_time`、`downtime_minutes`、`repair_type`、`repair_content`、`replaced_parts`、`repair_cost`、`maintenance_hours`、`repairman_code`、`repair_result`、`remark`、`created_at` |
| `EquipmentStatusLog` | `src_eam.eam_equipment_status_log` | `equipment_code`、`status_code`、`start_time`、`end_time`(空=进行中)、`duration_minutes`、`fault_no`、`work_order_no`、`remark`、`created_at` |

**建表来源（重要）**：这 4 张表**不在 Flyway 迁移里**，由
`infra/db-init/05_business_systems_2.sql` 建库脚本创建（全库 27 个 Flyway 脚本 V12–V38 中没有任何 `src_eam` 语句）。
Flyway 的基线版本是 11，V1–V11 正是 `infra/db-init/` 下的脚本，两者是**两套并行的建表通道**。

同库中还有 1 张**有表无代码**的表，以及 1 张已补实体但尚无写入点的表：

| 表 | 状态 |
|---|---|
| `src_eam.eam_equipment_status_log` | 有 DDL，**实体与 repo 已补**（`EquipmentStatusLog` + `EquipmentStatusLogRepository`，计划 00 · F4-08）：可经 JPA 查询设备的状态时段。但那只是映射——**无接口，且状态切换仍不落台账**，写入点属计划 05 范围（缺陷 #8） |
| `src_eam.eam_md_material` | 有 DDL，由 MDM 侧 `MasterDataDistributor.writeToEam()` 写入，EAM 只读 |

## 对照最低验收

| 验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| ① 至少 5 个本职模块具有实体、服务、接口和角色权限 | **未达标** | 实体 4 个、接口 12 个；但**无 service 层**（业务写在 Controller），且 `EAM:*` 权限点未被代码引用。目录的 6 个模块中 4 个部分实现、2 个未实现 |
| ② 至少 3 类核心单据可以从创建流转到关闭或作废 | **部分达标** | 故障单 `OPEN → CLOSED` 完整；维修单 `REPAIRING → REPAIRED/PENDING_PARTS/SCRAPPED` 完整；点检单 `result=null → NORMAL/ABNORMAL` 完整。**但设备本身只有状态切换、无建档→报废的作废语义（`SCRAPPED` 仅是状态字符串）**，且无任何作废接口 |
| ③ 至少 1 个审批流程和 1 个异常处理闭环 | **半达标** | **异常闭环有**：故障上报 → 维修 → 完工 → 设备恢复，联动真实存在。**审批流程无**：全库 `wf_definition` 的 `biz_type` 无 EAM，未接入 `ApprovalEngine` |
| ④ 至少 2 个向其他系统发送或消费的幂等业务事件 | **未达标** | EAM **不发事件**（无 `integration/` 层、不写 `mfg_ops.biz_outbox`）。作为接收方，MDM 物料分发用 `INSERT … ON DUPLICATE KEY UPDATE` 写了 `src_eam.eam_md_material`，这是幂等的，但属于**被动接收**且只有 1 条链路 |
| ⑤ 至少 1 页本系统运营查询或统计报表 | **未达标** | 3 个列表接口都是无条件的 `findAll(PageRequest)`，无按状态/车间/时间的过滤，无聚合统计接口 |

## 未实现 / 缺口

按补齐优先级排列：

1. **无 `service/` 层**（最结构性缺口）。查重、状态联动、停机时长计算都内联在 Controller 中，无 `@Transactional` 边界——
   `EquipmentRepairController.complete` 里「改维修单 + 关故障单 + 改设备状态」三步写库不在同一事务内，中途失败会留下不一致数据。
2. **无 `domain/` 层**。设备状态、故障状态、点检结果、维修结论四组状态字面量散落在 Controller 与实体默认值里，无枚举、无状态机、无合法迁移校验（例如可把 `SCRAPPED` 设备直接改回 `RUNNING`）。
3. **无审批**。`EAM:FAULT:APPROVE` 权限点已种子化，但没有 `wf_definition`/`wf_node` 定义，重大故障无法走审批。
4. **无事件**。故障停机不影响任何下游系统的交期计算，`prod_order_no` 字段只是被存下来、没人消费。
5. **`eam_equipment_status_log` 空转**。实体与 repo 已补（计划 00 · F4-08），但状态切换仍不落台账，因此「设备可用率」「状态时段重叠检测（治理规则 E02）」都缺数据源——缺的是**写入点**，不是映射。
6. **点检漏检不成立**。`is_missed` 恒为 `false`，无计划生成、无超期扫描（治理规则 E04 无输入）。
7. **实体字段与表结构已对齐，但接口仍不读写**（计划 00 · F4-08 补齐映射）：`eam_equipment` 的 `purchase_date`/`purchase_price`/`warranty_end_date`/`maintenance_cycle_days`/`last_maintenance_date`、`eam_fault` 的 `fault_cause`、`eam_inspection` 的 `check_items`(JSON，按 `String` 映射) 现已映射进实体。**缺口从「映射缺失」转为「读写缺失」**：12 个接口没有一个接收或返回这些字段，资产信息、故障原因、点检项在接口层依旧不可见。
8. **备件与可靠性整块缺失**：无备件领用/归还/最低库存，`repair_cost`/`replaced_parts` 只存不算；无 MTBF/MTTR，`health_score` 依赖数仓回写而数仓侧尚无产出。
9. **查询能力薄弱**：`EquipmentRepairController.create` 用 `faults.findAll().stream().filter(...)` 全表扫描找故障单，数据量上来后是性能问题。
