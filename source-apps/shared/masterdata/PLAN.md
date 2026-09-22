# shared/masterdata/ — 主数据消费端（只读副本 / 发布校验 / 分发客户端）

> **整体计划** · 共享技术模块（非十系统之一）
>
> ⚠️ **本模块当前是空壳**：`src/` 下只有一个 `package-info.java`（2 行），无任何实现。
> 本文的「职责边界」写的是**设计意图与接入后必须遵守的约束**；「当前事实」另见
> [STATUS.md](STATUS.md)。请勿把本文当作现状描述。

## 职责边界

### 解决什么横切问题（设计意图）

主数据的**生产**（创建、审批、发布、版本、变更、分发编排）是第 10 个系统 `source-apps/mdm` 的职责。
但主数据的**消费**发生在另外 9 个系统里，且每个消费方都要做同样三件事：

| 消费侧职责 | 若每个模块各写一遍会怎样 |
|---|---|
| **只读副本**：把已发布主数据在本地库留一份副本，供查询/下拉/报表关联 | 各系统直接跨库 `JOIN src_mdm.*`，主数据库的查询压力随系统数线性增长；且主数据改版时各系统看到的口径可能不同 |
| **发布校验**：引用主数据前必须确认其状态为 `PUBLISHED` | 出现「草稿物料被写进采购订单」这类脏数据；`ErrorCode.MASTER_DATA_NOT_PUBLISHED`（30002）就是为这个语义预留的 |
| **分发客户端**：主数据发布后，把副本推送到各目标系统 | 分发逻辑散落在 9 个系统，目标系统清单（ERP/MES/WMS/QMS/SRM/EAM/PLM）每加一个都要改多处 |

本模块的定位就是把这**三件事**从 9 个消费方里抽出来，成为共享的消费端 SDK。

### 给谁用（计划）

`source-apps` 下 **10 个业务模块**的 `pom.xml` 均已声明 `<artifactId>shared-masterdata</artifactId>`：
`mdm`、`crm`、`erp`、`plm`、`srm`、`wms`、`mes`、`qms`、`eam`、`energy`。
即依赖关系**已经铺好**，缺的只是模块内的实现。

### 与 `source-apps/mdm` 的边界（这条最关键）

| 关注点 | 归属 |
|---|---|
| 主数据的实体、状态机、审批、版本、变更、分发**编排**、分发日志 | `source-apps/mdm`（第 10 个系统） |
| 消费方读取已发布副本、引用前校验、调用分发 | 本模块（`shared/masterdata`） |

**本模块不得反向依赖 `source-apps/mdm`**：`mdm` 的 pom 也声明了 `shared-masterdata`，
一旦反向依赖即成环。契约（副本 DTO、校验接口）应定义在本模块，由 `mdm` 去实现或由消费方按副本表自查。

## 不做什么

| 非目标 | 说明 |
|---|---|
| **不生产主数据** | 不建主数据实体、不写主数据表、不做审批与版本。主数据的唯一真实来源是 `src_mdm` 库，写入只允许经 `mdm` 模块 |
| **不引入第二套主数据状态机** | 只**读**状态（`PUBLISHED` / `DISABLED` 等），状态的产生由 `mdm` 决定 |
| **不做跨库强一致** | 副本与主库之间是最终一致。副本未及时更新时应「读旧值 + 可观测」，而不是阻塞业务——`mdm` 侧的分发已按「单个目标系统失败不阻断整体」设计（`MasterDataDistributor.distributeTo()` 标 `REQUIRES_NEW`，失败只记一条 `FAILED` 分发日志） |
| **不删除副本** | 主数据被停用后副本必须保留，否则历史单据查不到名称（这条约束来自 `mdm/README.md`「分发只新增/更新，绝不删除」） |
| **不做业务规则** | 不判断「这张订单能不能用这个物料」，只回答「这个物料当前是否可被引用」 |
| **不依赖 `shared-security` / `shared-workflow`** | 当前 pom 只依赖 `shared-common`。保持最小依赖，避免消费方被迫引入整套安全/审批栈 |

## 代码分层

本项目 `source-apps` 统一分层为 `domain/ entity/ repo/ service/ controller/ workflow/ integration/ query/`。
本模块**当前 0 个实现文件**，全部为空：

| 分层 | 现状 | 文件数 | 说明 |
|---|---|---|---|
| （全部）`domain/` `entity/` `repo/` `service/` `controller/` `workflow/` `integration/` `query/` | ❌ 空 | 0 | 未创建任何目录 |
| `package-info.java` | ✅ 有 | 1 | 仅 2 行：`/** shared/masterdata 模块。 */` + `package com.mfg.masterdata;` |
| `src/test/` | ❌ 无 | 0 | — |

合计 **1 个 Java 文件、2 行**。

### 接入时建议的分层落点（计划）

| 分层 | 计划内容 |
|---|---|
| `domain/` | 副本的状态判定规则（哪些状态算「可被引用」） |
| `dto/` | 各消费方共用的副本视图（物料/客户/供应商/BOM 的最小字段集） |
| `repo/` | 只读查询：按编码查副本、按状态过滤（供下拉框使用） |
| `service/` | 「发布校验」统一入口（对应 `mdm` 侧已有的 `assertConsumable` 语义）、分发客户端调用封装 |
| `integration/` | 与 `mdm` 分发日志的对接、幂等与重试 |
| `controller/` | **计划为空**：消费端 SDK，不对外暴露自己的 REST 端点 |
| `workflow/` | **计划为空**：主数据审批在 `mdm` + `shared-workflow` |

## 关键设计约束

### 1. 依赖方向：`common ← masterdata`，且**不得**依赖 `app-mdm`

当前 `pom.xml` 唯一的依赖是 `shared-common`。`source-apps/mdm` 的 pom 也依赖本模块，
反向依赖会构成 Maven 模块环，编译期即失败。

### 2. 「发布校验」必须唯一实现，不允许消费方各自判状态

`ErrorCode` 已为本模块的语义预留了 4 个错误码：
`MASTER_DATA_NOT_FOUND`(30001)、`MASTER_DATA_NOT_PUBLISHED`(30002)、
`MASTER_DATA_INVALID_STATE`(30003)、`MASTER_DATA_ALREADY_EXISTS`(30004)。
其中 **30002 的注释直接写明「只有 PUBLISHED 状态的主数据才能被业务系统消费」**——
这是本模块存在的理由，也是最需要收敛成单点判断的一条。

### 3. 副本读取要有兜底，不能让主数据缺失阻断业务

主数据被停用或副本未同步时，已存在的历史单据仍需能显示名称。
这与 `mdm` 侧「绝不删除下游副本」的约束是一体两面：**读路径要能容忍副本过期**。

### 4. 与 `mdm` 的循环依赖风险要提前规避

参考 `shared/workflow` 用 `ObjectProvider` 打破
`ApprovalEngine → ApprovalCallback → MasterDataService → ApprovalEngine` 的做法：
若本模块将来需要回调 `mdm`，必须用同样的**延迟获取**而非构造器注入。

### 5. 命名易混淆（务必注意）

`com.mfg.masterdata`（本模块）与 `com.mfg.mdm.*`（第 10 个系统的真实实现）是**两个不同的东西**。
`mdm/README.md` 记录的业务规则（12 类主数据、状态机、分发目标）描述的是 `com.mfg.mdm`，不是本模块。
