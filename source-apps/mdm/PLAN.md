# mdm/ — 统一主数据管理平台

> **整体计划** · 依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 MDM 章节

## 系统定位

MDM 是十系统中的第 10 个系统，也是唯一一个**不产生业务单据、只产生被引用数据**的系统。其余 9 个系统（CRM / ERP / PLM / SRM / WMS / MES / QMS / EAM / 能源）各自维护一套业务单据，但客户、供应商、物料、产品、BOM、工艺路线这些**被所有单据共同引用**的数据，必须由 MDM 做唯一真实来源（Single Source of Truth）。

不可替代性体现在：如果 ERP、MES、WMS 各存一份物料表，同一个物料编码在三处含义可能不同（单位不同、类型不同、是否需检验不同），最终导致「料算不准、领错料、成本失真」，而且没有任何一方能证明自己是对的。本项目的做法是——主数据只在 `src_mdm` 库维护，下游系统只持有**只读副本表**，且副本只能由 MDM 的分发服务写入。

代码层面的落点：`BizType` 为每类主数据声明了「走哪条审批流程」和「要分发到哪些系统」，`MasterDataDistributor` 是唯一的下游写入者。

## 本职模块基线

下表逐条抄录 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的「MDM：统一主数据管理」章节，并在右侧增加「当前状态」列——状态依据本模块**实际存在的代码**判断，不依据规划意图。

| 模块 | 核心能力 | 当前状态 |
|---|---|---|
| 数据治理工作台 | 数据所有者、维护人、数据质量、重复检测、合并、停用 | **部分实现** — 合并 `MdmGovernanceService.merge()` + `md_merge_record` / `md_code_mapping`、停用 `disable()`、联系人均已实现；**数据所有者/维护人无字段**（实体只有 `created_by` / `updated_by`），**数据质量无任何实现**，**重复检测有仓储方法但无人调用**（`CustomerRepository.findByUnifiedSocialCodeAndIdNot`、`findByCustomerName`），`md_change_history` 表已建但无 Java 写入 |
| 业务伙伴 | 客户、供应商、联系人、地址、银行、资质、信用等级 | **部分实现** — `Customer`（`customerLevel` / `creditLimit` / `paymentTerms` / `taxNo` / `address`）、`Supplier`（`supplierLevel` / `qualStatus` / `qualExpireDate` / `leadTimeDays`）、联系人 `md_partner_contact` 已实现；**银行未实现**（无实体无表）；地址仅单个字符串字段，**无多地址簿**；**资质仅字段级**，无资质台账 |
| 产品与物料 | 物料、产品、分类、单位、换算、替代料、批次/序列号策略 | **部分实现** — `Material`、`Product`、`MaterialCategory`、替代料 `BomSubstitute`、换算 `Material.conversionRate` / `md_unit.convert_rate` 均已实现；**批次策略**仅 `Material.batchManaged` 一个开关，**序列号策略未实现**（无字段无表）；`md_unit` 无 JPA 实体，维护走 `MdmReferenceService` 原生 SQL 且不经审批 |
| 工程主数据 | BOM、工艺路线、工作中心、工序、生产版本、版本生效期 | **已实现** — `Bom` + `BomLine`（`bomVersion` / `current` / `effectiveDate` / `expireDate`）、`Routing` + `RoutingOperation`、工作中心 `md_work_center`、生产版本 `md_production_version` 全部具备；生产版本保存时强制校验产品、BOM、工艺路线三者均为 `PUBLISHED`/`CHANGING`（`MdmGovernanceService.validateProduction`） |
| 组织与财务主数据 | 公司、工厂、车间、仓库、成本中心、会计科目 | **部分实现** — 仓库 `md_warehouse`、成本中心 `md_cost_center`、会计科目 `md_account_subject`、组织 `md_org_unit` 有表且有接口；**公司 / 工厂 / 车间均无独立实体与表**，仅以 `factory_code`、`workshop_code` 字符串字段散落在其他表中；上述四类均不经审批、不参与分发（`BizType` 中 `approvalFlow` 为 `null`） |
| 分发中心 | 发布、目标系统选择、分发事件、失败重试、补发、一致性对账 | **已实现** — 发布 `MasterDataService.onApproved()`、分发事件 `md_outbox` / `md_inbox`、失败重试 `MdmOutboxService.retry()` 与 `retry_count<3 → DEAD`、补发 `POST /api/mdm/materials/{id}/redistribute`、对账 `md_reconciliation` 与 `reconcileEvent()` 全部具备；**目标系统不可选择**（由 `BizType.targetSystems` 硬编码）；**对账为弱实现**——`source_hash` / `target_hash` 列已建但从不写入，结果只有 `MATCH`（有写入行数）与 `CHECK`（无行数）两种 |

## 代码分层标准

本项目 `source-apps/*` 统一分层为 8 层：

```text
source-apps/<system>/
  domain/        状态机、枚举、领域规则
  entity/        单据、台账、配置实体
  repo/          数据访问与领域查询
  service/       业务动作、校验、事务边界
  controller/    独立维护 API
  workflow/      本系统审批策略与审批回调
  integration/   上下游事件、分发、幂等处理
  query/         列表、详情、台账、统计查询
```

mdm 模块的实际落点（`src/main/java/com/mfg/mdm/`）如下。**注意 `workflow/`、`integration/`、`query/` 三个目录在 mdm 中并不存在**，目录职责由其他包承担：

| 分层 | 是否存在 | 实际目录 / 承担者 | 说明 |
|---|---|---|---|
| `domain/` | ✅ 存在 | `domain/` | 3 个文件：`MasterDataEntity`（统一契约接口 + `markPending` / `publish` / `markRejected` 默认方法）、`MasterDataStatus`（7 态状态机）、`BizType`（12 类业务类型 + 审批流程编码 + 分发目标） |
| `entity/` | ✅ 存在 | `entity/` | 10 个实体：`Customer`、`Supplier`、`Material`、`Product`、`MaterialCategory`、`Bom`、`BomLine`、`BomSubstitute`、`Routing`、`RoutingOperation` |
| `repo/` | ✅ 存在 | `repo/` | 10 个 `JpaRepository` 接口 |
| `service/` | ✅ 存在 | `service/` | 9 个类，同时承担了 `integration/` 的职责：`MasterDataDistributor`（分发）、`MdmOutboxService`（Outbox/Inbox/幂等/重试） |
| `controller/` | ✅ 存在 | `controller/` | 8 个 `@RestController` |
| `workflow/` | ❌ **目录不存在** | `callback/MdmApprovalCallback` + 共享模块 `shared-workflow` | 审批引擎 `ApprovalEngine`、流程实例 `WfInstance` 都在 `shared-workflow`；本模块只用 `callback/` 承接回调。循环依赖用 `ObjectProvider<ApprovalCallback>` 延迟获取解决 |
| `integration/` | ❌ **目录不存在** | `service/MasterDataDistributor`、`service/MdmOutboxService` | 上下游事件与幂等处理物理上放在 `service/`，未单独建包 |
| `query/` | ❌ **目录不存在** | `controller/` + `service/MdmGovernanceService` + `service/DistributionMonitorService` | 统计查询散落三处：`MaterialController.stats()`、`MdmGovernanceService.page()`、`DistributionMonitorService.latest()` 与 `MdmOutboxService.page()` |

另有两个标准之外的目录：

| 目录 | 文件数 | 说明 |
|---|---|---|
| `dto/` | 2 | `BomCommand`、`RoutingCommand`（record + Bean Validation，BOM/工艺路线表头与明细一次写入） |
| `callback/` | 1 | `MdmApprovalCallback` —— 审批引擎与业务模块的接合点 |

共 44 个 Java 文件（含 `package-info.java`），约 2784 行。

**本模块的分层偏离是有意的**：`domain/` 层是 mdm 相对其他模块（如 erp、crm 只有 `callback/controller/dto/entity/repo/service`）多出来的一层，因为 12 类主数据的「状态流转 + 提审 + 发布 + 分发」逻辑完全一致，抽到 `domain/MasterDataEntity` 默认方法 + `service/MasterDataService` 泛型方法后，每类主数据不必重写一遍。

## 最低验收（五项）

抄录 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 末尾「系统本职完成的最低验收」，逐项对照结果见 [STATUS.md](STATUS.md)：

1. 至少 5 个本职模块具有实体、服务、接口和角色权限；
2. 至少 3 类核心单据可以从创建流转到关闭或作废；
3. 至少 1 个审批流程和 1 个异常处理闭环；
4. 至少 2 个向其他系统发送或消费的幂等业务事件；
5. 至少 1 页本系统运营查询或统计报表。
