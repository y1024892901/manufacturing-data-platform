# plm/ — 产品生命周期管理（PLM）

> **整体计划** · 依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 PLM 章节

## 系统定位

PLM 在十系统中负责「产品长什么样、怎么变」这一件事，是**产品定义的上游**：它不拥有物料、BOM、工艺路线的权威数据（那属于 MDM），而是消费 MDM 分发到 `src_plm` 的只读副本，并在工程变更发生时用 **ECR → ECO → ECN** 变更链把「要改什么」变成 MDM 里的一份**新版本草稿**，再由 MDM 走自己的审批发布流程分发给 ERP/MES。

它也不碰订单、库存、质量、设备——变更的**下游影响**（重排产、换料、返工）由 ERP/MES 各自消费 MDM 的已发布版本来决定。所以 PLM 的边界可以一句话概括：**只定义与变更，不发布、不下发、不执行**。

## 本职模块基线

依据 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 的 PLM 章节抄录，第三列为依据实际代码判断的当前状态。

| 模块 | 核心能力 | 当前状态 | 依据 |
|---|---|---|---|
| 产品结构 | 产品族、版本、EBOM、零部件、替代关系、图文档 | **部分实现** | 产品族 `plm_product_family`、产品版本 `plm_product_version` 有完整 CRUD + 发布（`PlmCatalogService`，type 取 `families` / `versions`）；EBOM 只有 MDM 分发来的只读副本 `plm_md_bom` / `plm_md_bom_line`（`PlmStructureController` 只查不写）；零部件主数据在 MDM；替代关系（`md_bom_line.substitute_group`）与图文档引用关系未实现 |
| 工艺规划 | MBOM、工艺路线、工序、工时、工装、工作中心、质量点 | **部分实现** | `src_plm.plm_md_routing` / `plm_md_routing_operation` 两张只读副本表由 MDM 分发侧写入（`MasterDataDistributor`），V13 迁移建表；**PLM 模块内没有任何工艺规划接口或服务**——本模块 4 个 Controller 里没有一个 `/routing` 路径；工装、质量点未实现 |
| 工程变更 | ECR、ECO、变更对象、影响分析、审批、发布、失效控制 | **部分实现** | ECR（`plm_ecr`）、影响分析（`plm_impact_analysis`）、ECO（`plm_eco`）、ECN（`plm_ecn`）四张表 + 完整变更链（`EngineeringChangeChainService`）；ECO 走三级审批（`EcoApprovalCallback` + `PLM_ECO_APPROVAL` 定义）；ECN 走两级审批（`EngineeringChangeService` + `EngineeringChangeApprovalCallback` + `PLM_ECN_CHANGE` 定义）；**失效控制（生效日期到期自动失效旧版本）未实现**，ECN 实施后新版本落库为 `DRAFT` / `is_current=0` |
| 文档管理 | 图纸、工艺卡、作业指导书、受控版本、签入签出、引用关系 | **部分实现** | `plm_document` 支持新建/修改/发布，`(doc_no, version_no)` 唯一键构成受控版本，发布写入 `released_by` / `released_at`；文件本体只存 `file_name` 字符串，**签入签出、引用关系、文件存储均未实现** |
| 配置管理 | 产品配置、选件、替代件、生效日期、基线 | **部分实现** | 工程基线 `plm_engineering_baseline` 可把「产品版本 + BOM 版本 + 工艺版本」固化成一条基线并发布；**产品配置、选件、替代件未实现** |
| 工艺/BOM 发布 | 向 MDM/ERP/MES 发布、发布记录、回退、差异比较 | **部分实现** | 差异比较已实现：`PlmStructureController.compare()` 按 `child_material_code` 对齐两个 BOM 版本，输出 `ADDED` / `REMOVED` / `CHANGED` / `UNCHANGED`；ECN 实施时向 `src_mdm` 建 BOM 或工艺路线新版本（`EngineeringChangeChainService.implement()`）；**发布记录仅落在 `plm_ecn` / `plm_eco` 自身字段上，回退未实现**；真正向 ERP/MES 分发由 MDM 分发中心负责，PLM 不发分发事件 |

## 代码分层标准

本项目 `source-apps/*` 统一按 8 个分层组织；逐层核对本模块实际情况如下。

| 分层 | 目录 | 本模块实际 | 说明 |
|---|---|---|---|
| 领域规则 | `domain/` | **空 — 未实现** | 没有枚举、没有状态机类。ECN 状态以字符串字面量散落在 `EngineeringChangeService`（`"DRAFT"` / `"REVIEWING"` / `"APPROVED"` / `"IMPLEMENTED"`）和 `EngineeringChangeChainService` 的 SQL 里，非法状态靠 `BizException.conflict(...)` 手工拦截 |
| 单据实体 | `entity/` | 1 个类 | 仅 `EngineeringChange`（`@Table(name="plm_ecn", catalog="src_plm")`）。ECR / ECO / 影响分析 / 产品族 / 文档 / 基线**没有 JPA 实体**，全部走 `JdbcTemplate` 原生 SQL + `Map<String,Object>` |
| 数据访问 | `repo/` | 1 个接口 | `EngineeringChangeRepository`（`existsByEcnNo`、`findByEcnNo`），其余查询是 service 内联 SQL |
| 业务服务 | `service/` | 3 个类 | `EngineeringChangeService`（JPA 版 ECN 生命周期）、`EngineeringChangeChainService`（JDBC 版 ECR/ECO/ECN 变更链 + 实施）、`PlmCatalogService`（产品族/版本/文档/基线通用目录服务） |
| 独立维护接口 | `controller/` | 4 个类 | `EngineeringChangeController`、`EngineeringChangeChainController`、`PlmCatalogController`、`PlmStructureController` |
| 审批策略与回调 | `workflow/` | **空 — 未实现** | 回调被放在**非标准的 `callback/` 包**：`EcoApprovalCallback`（`supports("ECO")`）、`EngineeringChangeApprovalCallback`（`supports("ECN")`）。审批链本身不在 Java 里，而在 SQL 种子 `mfg_auth.wf_definition` / `wf_node` |
| 上下游集成 | `integration/` | **空 — 未实现** | 全模块**没有一处调用 `BusinessEventService`**，不发布也不消费业务事件。与 MDM 的集成是「直接跨库写 `src_mdm`」，不是事件 |
| 查询与报表 | `query/` | **空 — 未实现** | 分页、详情、差异比较的 SQL 直接写死在 controller（`PlmStructureController`）与 service（`PlmCatalogService.page()`、`EngineeringChangeChainService.page()`）中 |

> 三个非标准点值得记录：① 回调包名是 `callback/` 而非标准 `workflow/`；② 两套 ECN 实现并存（JPA 的 `EngineeringChangeService` 与 JDBC 的 `EngineeringChangeChainService`）写同一张 `plm_ecn` 表，字段映射不一致——JPA 实体没有 `ecr_id` / `eco_id` / `target_type` / `target_version` 这四个变更链字段；③ `PlmStructureController` 绕过 service 层直接持有 `JdbcTemplate`。

## 最低验收（五项）

抄录 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 末尾「系统本职完成的最低验收」五项：

1. 至少 5 个本职模块具有实体、服务、接口和角色权限；
2. 至少 3 类核心单据可以从创建流转到关闭或作废；
3. 至少 1 个审批流程和 1 个异常处理闭环；
4. 至少 2 个向其他系统发送或消费的幂等业务事件；
5. 至少 1 页本系统运营查询或统计报表。
