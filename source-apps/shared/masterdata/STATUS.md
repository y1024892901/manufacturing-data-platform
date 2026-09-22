# shared/masterdata/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**本模块是空壳，完成度 0%**：`src/` 下只有一个 2 行的 `package-info.java`，
没有任何实体、仓储、服务或端点；10 个业务模块的 `pom.xml` 都已声明依赖它，
但**全仓库没有一行 Java 代码引用 `com.mfg.masterdata`**。
pom 描述承诺的三项能力（只读副本、发布校验、分发客户端）目前都由
**第 10 个系统 `source-apps/mdm`**（包名 `com.mfg.mdm`）承担，消费方直接依赖它。

## 已实现

### 分层文件统计

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `domain/` | 0 | — |
| `entity/` | 0 | — |
| `repo/` | 0 | — |
| `service/` | 0 | — |
| `controller/` | 0 | — |
| `workflow/` | 0 | — |
| `integration/` | 0 | — |
| `query/` | 0 | — |
| `package-info.java` | 1 | `package com.mfg.masterdata;`（仅声明包，无内容） |
| `src/test/` | 0 | — |

合计 **1 个 Java 文件、2 行**。除 `pom.xml` 与 `package-info.java` 外，模块内没有其他源文件。

## 对外接口 / 扩展点

| 类型 | 名称 | 说明 |
|---|---|---|
| — | **无** | 本模块不提供任何 Controller 端点、接口、切面、回调或 Spring Bean。它产出的构件 `shared-masterdata-1.0.0.jar` 里除 `package-info.class` 外不含任何类 |

### 依赖声明（已铺好，但空转）

| 声明方 | 位置 | 实际效果 |
|---|---|---|
| 聚合 | `source-apps/pom.xml:45`（`<module>`）、`:102`（`dependencyManagement`） | 会被构建 |
| 消费方 | 10 个模块的 pom：`mdm:33`、`crm:38`、`erp:38`、`plm:33`、`srm:33`、`wms:34`、`mes:33`、`qms:34`、`eam:33`、`energy:33` | 引入了一个空 jar，**不产生任何行为** |

### 三项承诺能力的实际归属

| 承诺能力（`pom.xml` 描述） | 实际实现位置 | 消费方如何使用 |
|---|---|---|
| 只读副本 | 副本表由 `com.mfg.mdm` 的分发逻辑写入各目标库（如 `src_erp.erp_md_material`） | 无统一读取客户端。消费方要么直接注入 `com.mfg.mdm.repo.*Repository`（如 CRM 的 `OpportunityController` 用 `com.mfg.mdm.entity.Customer` + `CustomerRepository`），要么写裸 SQL 查副本（如 `CrmP2Service` 里 `SELECT COUNT(*) FROM src_mdm.md_customer WHERE customer_code=? AND status='PUBLISHED'`） |
| 发布校验 | `com.mfg.mdm.service.MasterDataService.assertConsumable(entity, refBy)` | CRM / ERP / WMS 等模块**直接 import `com.mfg.mdm.service.MasterDataService`** 调用（前提是它们的 pom 依赖 `app-mdm`，目前 `crm`、`erp`、`wms`、`bootstrap` 四个模块依赖了它） |
| 分发客户端 | `com.mfg.mdm.service.MasterDataDistributor`（`distributeTo()` 标 `REQUIRES_NEW`，单目标失败记 `FAILED` 日志） | 由 `mdm` 内部调用，消费方不直接使用 |

## 数据表

**无任何 `@Table` 注解**——本模块没有 JPA 实体，因此不声明任何表。
（主数据副本表如 `src_erp.erp_md_material`、主数据表如 `src_mdm.md_customer`
均由 `source-apps/mdm` 模块定义与写入，不在本模块范围内。）

## 未实现 / 缺口

| 缺口 | 说明 |
|---|---|
| **三项职责全部未实现** | 只读副本、发布校验、分发客户端在 `shared-masterdata` 内均无任何代码 |
| **模块无唯一价值** | 由于实现落在了 `com.mfg.mdm`，本模块目前只是一个被 10 处 pom 引用、内容为空的 jar |
| **消费方各自造轮子** | 「发布校验」至少有两种调用形态：注入 `com.mfg.mdm.service.MasterDataService`（CRM/ERP/WMS）与裸 SQL 判状态（`CrmP2Service.convert`）。校验口径与报错文案分散，未来易漂移 |
| **消费方对 `app-mdm` 形成硬依赖** | `crm`、`erp`、`wms`、`bootstrap` 依赖 `app-mdm`，即**为了让别的系统能读取主数据，必须把整个第 10 个系统打进自己的编译依赖**。这正是本模块本来要消除的耦合 |
| **副本读取无统一入口** | 有的走 mdm 的 JPA 仓储（直连 `src_mdm` 主表），有的走裸 SQL 查副本表，两套来源可能给出不一致结果（主表已改版而副本未同步时） |
| **无测试** | 无 `src/test/` |
| **命名易混淆** | `com.mfg.masterdata`（空壳）与 `com.mfg.mdm.*`（真实实现）仅差几个字母，阅读代码时极易误判 |

### 结论性判断

本模块若要真正落地，最小可行范围是：定义**副本只读视图 DTO + 按编码/状态查询的只读仓储**，
以及**唯一的发布校验入口**（把 `mdm` 的 `assertConsumable` 语义搬进来或改为面向接口），
再让 `crm` / `erp` / `wms` 从「依赖 `app-mdm`」切换为「依赖 `shared-masterdata`」。
在此之前，任何文档都不应声称本模块提供了主数据消费能力。
