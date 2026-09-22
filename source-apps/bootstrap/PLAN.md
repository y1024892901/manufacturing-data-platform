# bootstrap/ — 启动模块（不是业务系统）

> **整体计划** · 本模块是**基础设施模块**，不承载任何制造业务功能，
> 因此不套用 [docs/system-functional-catalog.md](../../docs/system-functional-catalog.md) 里的「系统本职工作」章节与五项最低验收
> （那五项验收针对 10 个业务系统）。本文件说明**职责边界**。

## 模块职责

`app-bootstrap` 是**整个仓库唯一可执行的 Maven 模块**：其余 10 个业务模块 + 4 个 shared 模块都只是被它依赖的库，
不各自打包成可独立启动的服务。它的职责只有四件：

| # | 职责 | 载体 |
|---|---|---|
| 1 | 提供 Spring Boot 主启动类与组件扫描范围 | `MfgSourceApplication` |
| 2 | 提供 Flyway 迁移通道（V12 起的增量 DDL/DML） | `src/main/resources/db/migration/V12…V38`（27 个脚本） |
| 3 | 提供应用级配置（端口、数据源、JPA、JWT、Swagger） | `src/main/resources/application.yml` |
| 4 | 提供两个平台级 Controller（健康检查、演示辅助） | `HealthController`、`DemoController` |

**职责边界（明确不做的事）**：

- 不定义任何 `@Entity`、不写任何业务 `@Table`——`bootstrap` 下**没有 entity 也没有 repo**；
- 不承载业务规则。唯一的业务相关代码 `DemoController` 是把请求**转发给** `shared/workflow` 的 `ApprovalEngine`，自己不做判断（见下文「Demo 控制器的定位与去留」）；
- 不做 10 个系统的「聚合网关」。系统隔离靠 **URL 前缀 + 库名 + 权限**，不靠进程边界。

### 为什么是单进程而不是 10 个进程

这是本模块存在的根本前提，`MfgSourceApplication` 的类注释写明了三条理由：

1. 10 个 JVM 约需 3~4GB 内存，演示机吃力；
2. 启动一次即可，演示时不用等 10 个服务依次就绪；
3. 需求是「每个系统数据能独立维护」——这是**数据和界面**的独立，不是进程的独立。
   10 个模块各有自己的库（`src_mdm`、`src_crm`…）、自己的 Controller、自己的权限点。

隔离在三个维度上落地，均可核查：

| 维度 | 落地方式 | 证据 |
|---|---|---|
| 路径 | 每系统独立 URL 前缀 | `/api/mdm/**`、`/api/crm/**` … `/api/energy/**` |
| 数据库 | 每系统独立 schema，实体用 `catalog = "src_xxx"` 限定名 | `@Table(name="eam_equipment", catalog="src_eam")` |
| 权限 | 每系统独立 system 码，鉴权按前缀拦截 | `SecurityConfig` 中 10 条 `.requestMatchers("/api/xxx/**").hasAuthority("SYSTEM_XXX")`，authority 来自 `sys_user_system` 表 |

## 启动类

`com.mfg.bootstrap.MfgSourceApplication`（`pom.xml` 中 `<start-class>` 与 `spring-boot-maven-plugin` 的 `mainClass` 都指向它）：

```java
@SpringBootApplication(scanBasePackages = "com.mfg")
@EntityScan(basePackages = "com.mfg")
@EnableJpaRepositories(basePackages = "com.mfg")
@EnableScheduling
```

三个 `com.mfg` 前缀的扫描注解是「单进程多模块」的接线点：因为 10 个业务模块与 `shared/*` 全部处于 `com.mfg` 命名空间下，
所以各模块的 Controller/Service/Entity/Repository 会被自动注册，**启动类里不需要逐个声明模块**。
新增一个业务模块时，只要它被 `pom.xml` 依赖、包名在 `com.mfg` 下，就会被自动装载。

`main()` 的执行顺序是刻意的：**先灌环境变量，再启动容器，最后打印横幅**。

| 方法 | 作用 | 设计意图 |
|---|---|---|
| `loadLocalEnv()` | 从工作目录和 class-code 位置两条路径向上查找 `infra/.env`，逐行解析 `KEY=VALUE` 并写入 JVM System Property；额外把 `MYSQL_PASSWORD` 映射到 `spring.datasource.password`、`JWT_SECRET` 映射到 `mfg.jwt.secret` | Spring Boot **不会**自动读 `.env`。此前只有 `run.bat` 注入环境变量，导致从 IDEA 直接运行时会用到过期口令。System Property 优先级高于 IDEA Run Configuration 的环境变量，故可屏蔽遗留值；该文件已被 Git 忽略 |
| `findEnvSearchStarts()` | 返回两个起点：当前工作目录、`CodeSource` 所在目录 | 兼容「IDEA 中 cwd 是模块目录」与「`java -jar` 时 cwd 是任意目录」两种场景 |
| `printStartupBanner()` | 打印端口、Swagger 地址、演示账号接口、健康检查，以及 10 个系统的 URL 前缀表 | 演示开场用：一眼可见所有系统共用 8080 端口，靠前缀区分 |

`@EnableScheduling` 本身不定义任务，但它是 `MdmOutboxService`（MDM 事件出站重试）与
`shared/common` 的 `BusinessEventService`（事件补发）里 `@Scheduled` 方法能生效的前提——
**定时能力的开关放在启动模块，具体任务留在业务模块**，这是刻意的职责划分。

## Flyway 迁移机制

配置在 `application.yml` 的 `spring.flyway` 段：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 11
    validate-on-migrate: true
```

### 两段式建库：V1–V11 归 scripts，V12+ 归 Flyway

这是本模块最需要说明的一点。数据库 schema 有**两条并行的建表通道**：

| 版本段 | 执行者 | 位置 | 内容 |
|---|---|---|---|
| V1–V11 | 手工执行 / 初始化脚本 | `infra/db-init/01_databases.sql` … `11_plm_ecn_workflow.sql` | 建 18 个库、全部基线表结构、种子数据 |
| V12–V38 | **Flyway（本模块驱动）** | `source-apps/bootstrap/src/main/resources/db/migration/` | 27 个增量脚本：加字段、加表、加种子 |

`baseline-version: 11` + `baseline-on-migrate: true` 的作用是：面对一个已由 `infra/db-init` 建好的数据库，
Flyway 不做任何改动，只在 `flyway_schema_history` 里记一条版本 11 的基线，然后**从 V12 开始接手**。
因此 V1–V11 的编号被**保留而不再由 Flyway 管理**，避免历史脚本重放。

### 迁移脚本的编排规律

27 个脚本按项目阶段（P1/P2/P3）命名，与 [docs/implementation-roadmap.md](../../docs/implementation-roadmap.md) 的分期对应：

| 阶段 | 版本段 | 数量 | 主题 |
|---|---|---|---|
| P1 | V12–V17 | 6 | 平台身份基线、产品与工艺路线分发、PLM 目录、产品审批流、MDM 治理审批流、PLM 变更链 |
| P2 | V18–V28 | 11 | CRM（线索/商机/报价/合同）、ERP（订单信用 ATP／MRP／财务）、工作流与权限种子、业务收口（outbox）、生产订单管控、演示基线数据 |
| P3 | V29–V38 | 10 | SRM（准入询价/ASN 质量/订单扩展）、WMS（收货上架/库存管控/盘点）、QMS（标准抽样/NCR CAPA/8D 权限）、库存事务收口 |

写法上保持幂等，便于在半成品库上重跑：

- 建表一律 `CREATE TABLE IF NOT EXISTS`，且**全部写库名限定名**（`src_erp.erp_xxx`、`mfg_auth.wf_definition`）；
- 改已有表用 `ALTER TABLE src_xxx ADD COLUMN`（如 V22、V27、V31、V33、V36）；
- 插种子数据用 `INSERT ... SELECT ... WHERE NOT EXISTS`（V15、V25）或 `INSERT IGNORE`（V28、V37、V38），避免主键/唯一键冲突。

> **已知的边界**：27 个 Flyway 脚本中**没有任何 `src_eam` / `src_energy` 语句**。
> EAM 与 energy 两个模块的表由 `infra/db-init/05_business_systems_2.sql` 创建，属于上面的 V1–V11 通道。
> 换言之，这两个模块目前**没有走 Flyway 增量**；后续为其加字段时，应新开 V39+ 脚本而不是改 `infra/db-init/`。

## Demo 控制器的定位与去留

### 它是什么

`DemoController`（`@RequestMapping("/api/demo")`，Swagger tag 为「99. 演示辅助」）目前只有**一个接口**：
`POST /api/demo/bom-change`，作用是**不依赖任何业务单据，直接向 `ApprovalEngine` 发起一条 BOM 三级审批**
（工艺主管 → 生产主管 → 成本会计），并附带一段提交时的 JSON 快照。

三个刻意的设计点：

| 设计 | 原因 |
|---|---|
| `bizId` 用 `AtomicLong(System.currentTimeMillis() % 1_000_000_000L)` 起算，而非从 0 开始 | 应用重启后若从固定值重新开始，会与重启前已存在的实例**撞 bizId**，被审批引擎的幂等保护误判为「重复提交」。真实系统中 bizId 由数据库自增，不存在此问题 |
| 先 `CurrentUser.find()` 判空，未登录抛 `UNAUTHORIZED` | 审批必须知道提交人，快照与流程实例都要落提交者 |
| 快照在提交时固化进流程实例 | 审批人看到的是**提交那一刻的数据**，而非后续可能被改动的当前值 |

### 原定去留：作为「演示兜底」

类注释写的是：MDM 的业务提交接口完成前，用这里模拟提交 BOM 变更申请；
**MDM 就绪后真实提交会走 `POST /api/mdm/bom/{id}/change-request`，本接口保留作为演示兜底。**

### 核查结论：注释里的路径已过时，接口现在仍不可删

对照当前代码，上述注释**与实际不符**，去留判断需要修正：

1. **注释预测的路径不存在**。全仓库检索无 `/api/mdm/bom/{id}/change-request`。
2. **MDM 侧真实入口是另一个**：`BomController` 的 `POST /api/mdm/boms/{id}/submit`（`BomService.submit()` 返回 `WfInstance`）。
   它与 Demo 接口的**输入形态根本不同**——需要一条已存在的 BOM 记录和数字 `id`，
   而 Demo 接口接受任意 `bomCode`/`materialCode` 并**不校验单据是否存在**。
3. **`DemoController` 仍在被依赖**。`ops/demo_bom_approval.py` 与 `ops/demo_approval_boundary.py`
   两个演示脚本都调用 `/api/demo/bom-change`，后者更是把它当作**审批引擎的测试夹具**在使用
   （审结、驳回、边界、幂等等用例）。

### 建议

**结论：暂不可删，但应重新定性并做三处收尾。**

| 建议 | 理由 | 具体动作 |
|---|---|---|
| ① 修正类注释中的过时路径 | 现注释指的 `POST /api/mdm/bom/{id}/change-request` 不存在，会误导后来者 | 改为指向真实的 `POST /api/mdm/boms/{id}/submit` |
| ② 明确定位为「审批引擎的无业务依赖入口」，而非「MDM 兜底」 | MDM 已经有自己的提交入口，兜底说法已不成立；但其真实价值仍在——**不需要预置 BOM 数据就能压测审批链路**，这正是那两个 ops 脚本要用的能力 | 更新 `@Tag` 描述与类注释 |
| ③ 处理 `reuseBizId` 参数并加演示开关 | `ops/demo_approval_boundary.py` 会传 `&reuseBizId=...` 想验证「同一 bizId 重复提交被拒」，但 `submitBomChange` 的方法签名**不接收该参数**（Spring 会静默忽略），脚本自身也注释说明改走工作流层验证。同时 `application.yml` 已有 `mfg.demo-mode`（默认 `true`），而本接口并未受其约束 | 二选一：接住 `reuseBizId` 让幂等用例真正成立，或删掉该参数并写明限制；另建议用 `mfg.demo-mode` 把 `/api/demo/**` 挡在生产配置之外 |

> 长期看，一旦 MDM 的 BOM 提交接口也能接受「无需预置数据」的入参形态，本 Controller 连同 `ops/` 下两个脚本可一并退役。
> 在那之前，它是审批链路唯一可脱离业务数据独立验证的入口。
