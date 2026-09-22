# bootstrap/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

启动模块已完整交付其四项职责：**主启动类可运行、Flyway 通道有 27 个迁移脚本、应用配置齐备、2 个平台级 Controller 提供 3 个接口**；
本模块**不含任何实体与业务规则**（无 entity / 无 repo），因此不参与十系统的五项最低验收。
最大的结构性问题是 **DB schema 存在两条并行通道**（`infra/db-init` 与 Flyway），且 EAM、energy、MES 三个系统不在 Flyway 覆盖范围内。

## 已实现

### 分层文件统计

`bootstrap` **不适用 8 层业务分层**（那是业务模块的标准）。它的文件按基础设施职责分类：

| 类别 | 文件数 | 关键类 / 文件 |
|---|---|---|
| 启动类 | 1 | `com.mfg.bootstrap.MfgSourceApplication` |
| `controller/` | 2 | `HealthController`、`DemoController` |
| 其他 | 1 | `package-info.java` |
| 资源：配置 | 1 | `src/main/resources/application.yml` |
| 资源：迁移 | 27 | `src/main/resources/db/migration/V12…V38 *.sql` |
| `entity/` | **0** | 本模块不定义实体 |
| `repo/` | **0** | 本模块不定义仓储 |
| `domain/` / `service/` / `workflow/` / `integration/` / `query/` | **0** | 均不在本模块职责内 |

Java 文件合计 4 个。构建产物为 `app-bootstrap-1.0.0.jar`（经 `spring-boot-maven-plugin` repackage 的唯一可执行 jar）。

关键能力实现情况：

| 能力 | 载体 | 说明 |
|---|---|---|
| 组件扫描接线 | `MfgSourceApplication` 的三个 `com.mfg` 前缀注解（`@SpringBootApplication` + `@EntityScan` + `@EnableJpaRepositories`） | 10 个业务模块与 4 个 shared 模块自动装载，启动类内不逐个声明 |
| 本地环境注入 | `MfgSourceApplication.loadLocalEnv()` | 从 cwd 与 CodeSource 两处向上找 `infra/.env`，写入 System Property；额外映射 `MYSQL_PASSWORD → spring.datasource.password`、`JWT_SECRET → mfg.jwt.secret` |
| 启动横幅 | `MfgSourceApplication.printStartupBanner()` | 打印端口、Swagger、演示账号与 10 个系统的 URL 前缀表 |
| 定时能力开关 | `@EnableScheduling` | 本模块只开开关，具体 `@Scheduled` 任务在 `MdmOutboxService` 与 `shared/common` 的 `BusinessEventService` 中 |
| 存活与库概览 | `HealthController` | 探测 `SELECT DATABASE()` / `VERSION()`，异常时降级为 `DEGRADED` 而非 500 |
| 审批链路演习 | `DemoController` | 直连 `ApprovalEngine` 发起 BOM 三级审批，含提交时 JSON 快照 |

## 接口清单

遍历 `@RestController` / `@RequestMapping` / `@GetMapping` / `@PostMapping`，共 **3 个接口**：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查：返回 `status`/`time`/`service`，并探测当前库名与 MySQL 版本；数据库异常时 `status` 置 `DEGRADED` 并附 `dbError` |
| GET | `/api/health/databases` | 数据库概览（分页，`page` 默认 1、`size` 默认 20、上限 100）：查 `information_schema.TABLES`，按 `src_mdm` / `src_%` / 数仓四层 / 平台能力分四类 layer，返回每库表数量 |
| POST | `/api/demo/bom-change` | 模拟提交 BOM 变更审核（参数：`bomCode`、`materialCode`、`oldQty`、`newQty`、`reason`），经 `ApprovalEngine.start()` 创建 `WfInstance` 并返回实例号与当前节点；未登录抛 `UNAUTHORIZED` |

> 权限说明：`HealthController` 的两个接口与 `DemoController` 均**不在** `SecurityConfig` 的 `/api/xxx/**` 系统前缀拦截清单内。
> `DemoController` 自己做登录校验（`CurrentUser.find()` 判空），`HealthController` 则无鉴权检查。
> 另注：`ops/demo_approval_boundary.py` 会向 `/api/demo/bom-change` 传 `&reuseBizId=…`，
> 但方法签名不接受该参数（Spring 静默忽略），详见 [PLAN.md](PLAN.md) 的「Demo 控制器的定位与去留」。

## 数据表

本模块**无 `@Entity` / 无 `@Table`**，因此不产出实体表。数据表通过 **Flyway 迁移脚本**间接创建，
共 **27 个脚本、约 291 行**，语句构成为 `CREATE TABLE` ≈ 70 处、`ALTER TABLE` 18 处、`INSERT` 24 处。

涉及的 schema 与引用次数（按脚本中限定名统计）：

| schema | 引用次数 | 说明 |
|---|---|---|
| `mfg_auth` | 45 | 权限、工作流定义与节点、权限点种子（改动最频繁） |
| `src_erp` | 26 | 订单信用/ATP、MRP、财务、生产订单 |
| `src_mdm` | 15 | 治理、联系人、演示基线 |
| `src_crm` | 14 | 线索、商机、报价、合同 |
| `src_wms` | 10 | 收货上架、库存管控、盘点、流水台账 |
| `src_plm` | 10 | 产品族、ECR/ECO 变更链 |
| `src_srm` | 9 | 准入、询价、ASN、供应商质量 |
| `src_qms` | 7 | 检验标准、抽样、NCR/CAPA、8D |
| `mfg_ops` | 2 | 事件出站表 `biz_outbox` |

> ⚠️ **未覆盖的 schema**：`src_eam`、`src_energy`、`src_mes` 在 27 个脚本中**一次都没出现**。
> EAM 与 energy 的表由 `infra/db-init/05_business_systems_2.sql` 建立，属 V1–V11 通道；
> 为这两个模块加字段时应新开 V39+ 脚本，而非回头修改 `infra/db-init/`。

### 迁移脚本清单

| 版本 | 行数 | 语句构成（CREATE/ALTER/INSERT） | 主题 |
|---|---|---|---|
| `V12__platform_identity_baseline.sql` | 8 | 0/1/0 | `sys_user` 加锁定、失败计数、逻辑删除字段 |
| `V13__p1_product_routing_distribution.sql` | 37 | 3/0/0 | 产品与工艺路线的分发副本表 |
| `V14__p1_plm_catalog.sql` | 32 | 4/0/0 | PLM 产品族等目录表 |
| `V15__p1_product_workflow.sql` | 15 | 0/0/3 | 产品主数据审批流定义与节点（`MDM_PRODUCT_NEW`） |
| `V16__p1_mdm_governance_workflow.sql` | 13 | 12/1/0 | MDM 治理相关表（含 `md_partner_contact`） |
| `V17__p1_plm_change_chain.sql` | 6 | 3/1/2 | PLM 变更链（`plm_ecr`） |
| `V18__p2_crm_lead_customer360.sql` | 4 | 4/0/0 | CRM 线索与客户 360 |
| `V19__p2_crm_opportunity_forecast.sql` | 4 | 3/1/0 | 商机扩展字段与商机产品行 |
| `V20__p2_crm_quotation.sql` | 2 | 2/0/0 | 报价单 |
| `V21__p2_crm_contract.sql` | 3 | 3/0/0 | 销售合同 |
| `V22__p2_erp_order_credit_atp.sql` | 7 | 5/1/0 | 销售订单加来源/信用/ATP 字段 |
| `V23__p2_erp_mrp.sql` | 4 | 4/0/0 | MRP 运算相关表 |
| `V24__p2_erp_finance.sql` | 6 | 5/1/0 | 发票等财务表 |
| `V25__p2_workflow_permission_seed.sql` | 6 | 0/0/6 | 报价审批流定义、节点与权限种子 |
| `V26__p2_business_closure.sql` | 74 | 2/6/5 | 业务收口（最大脚本）：`mfg_ops.biz_outbox` 事件出站表等 |
| `V27__p2_production_order_control.sql` | 5 | 0/1/0 | 生产订单加版本、齐套状态字段 |
| `V28__p2_demo_baseline.sql` | 6 | 0/0/6 | 演示基线数据（演示客户/供应商，`INSERT IGNORE`） |
| `V29__p3_srm_onboarding_rfq.sql` | 4 | 4/0/0 | SRM 准入申请与询价 |
| `V30__p3_srm_asn_quality.sql` | 3 | 3/0/0 | SRM ASN 与供应商质量 |
| `V31__p3_srm_order_extension.sql` | 2 | 0/2/0 | 采购订单与交货单加字段 |
| `V32__p3_wms_receipt_putaway.sql` | 2 | 2/0/0 | WMS 收货与上架 |
| `V33__p3_wms_inventory_control.sql` | 3 | 2/1/0 | 库存加质量状态/冻结量，新增库存动作表 |
| `V34__p3_wms_counting.sql` | 2 | 2/0/0 | WMS 盘点计划与盘点行 |
| `V35__p3_qms_standard_sampling.sql` | 3 | 3/0/0 | QMS 检验标准与检验项 |
| `V36__p3_qms_ncr_capa.sql` | 6 | 2/1/0 | 检验单加溯源字段，新增 NCR/CAPA |
| `V37__p3_qms_8d_permissions.sql` | 2 | 1/0/1 | QMS 8D 表与权限点种子 |
| `V38__p3_inventory_transaction_closure.sql` | 32 | 1/2/1 | 库存事务收口：`wms_inventory_ledger` 幂等流水台账与权限种子 |

编排规律：建表一律 `CREATE TABLE IF NOT EXISTS`；改表用 `ALTER TABLE`；种子数据用
`INSERT ... SELECT ... WHERE NOT EXISTS` 或 `INSERT IGNORE`，保证在半成品库上可重跑。
所有语句均写**库名限定名**，不依赖连接的默认 schema。

## 对照最低验收

`docs/system-functional-catalog.md` 末尾的五项验收**针对 10 个业务系统**，bootstrap 作为基础设施模块**不参与该验收**。
理由：五项验收均以「本职模块」「核心单据」「本系统运营报表」为对象，而本模块
无实体、无单据、无业务状态机、无对外业务事件，不具备可验收的业务语义。
强行套用会把「Flyway 能否执行成功」误记为「单据能否流转」，反而掩盖真实进度。

作为替代，本模块的等效验收标准（基础设施口径）如下：

| 等效验收项 | 状态 | 证据 / 缺口 |
|---|---|---|
| ① 主启动类可运行且自动装载全部 10 个系统 | **达标** | `MfgSourceApplication` 三个 `com.mfg` 前缀扫描注解；`app-bootstrap-1.0.0.jar` 已 repackage，`start-class` 与 `mainClass` 一致 |
| ② 无需手工注入环境变量即可在 IDEA 直接启动 | **达标** | `loadLocalEnv()` 自动读取 `infra/.env` 并覆盖遗留口令；缺失时仅告警不阻断，报错信息提示应配 `MYSQL_PASSWORD` 与 `JWT_SECRET` |
| ③ Flyway 迁移在既有库上可幂等重跑 | **达标（有边界）** | `baseline-on-migrate` + `baseline-version: 11` 跳过 V1–V11 基线；脚本用 `IF NOT EXISTS` / `INSERT IGNORE` / `WHERE NOT EXISTS`。**边界**：`V29`–`V38` 有若干 `CREATE TABLE` 未带 `IF NOT EXISTS`，在已建表的库上重跑会失败 |
| ④ 应用存活与数据库连通性可自证 | **达标** | `GET /api/health` 探测当前库与版本，失败降级 `DEGRADED`；`GET /api/health/databases` 给出 18 个库的表数量分类概览，演示开场即可确认「10 个系统 + 数仓四层都在」 |
| ⑤ 平台级配置外部化，密钥不入版本控制 | **达标** | `MYSQL_PASSWORD` 与 `JWT_SECRET` **均无默认值**，缺失即启动失败（优于用错口令连错库）；`infra/.env` 已被 Git 忽略 |

## 未实现 / 缺口

1. **两条并行建表通道**（最主要的结构性风险）。`infra/db-init/01…11` 与 Flyway `V12…V38` 都能建表，
   而 `baseline-version: 11` 只是让 Flyway「假装」V1–V11 已归它管。新人若要加一条 DDL，
   无法从代码判断该写进 `infra/db-init/` 还是新建 `V39`。建议在 `infra/db-init/README.md` 与
   `application.yml` 处写明分工线：**V1–V11 只用于初始化空库，此后一律新增 V39+ 迁移**。
2. **EAM / energy / MES 三个系统不在 Flyway 覆盖内**。27 个脚本中不含 `src_eam`、`src_energy`、`src_mes` 任何语句。
   其中 EAM 与 energy 的表来自 `infra/db-init/05_business_systems_2.sql`；
   这意味着**这三个系统的表结构演进无法用迁移脚本追踪**。
3. **部分迁移脚本缺 `IF NOT EXISTS`**（V29–V38 的若干 `CREATE TABLE`）。与「可幂等重跑」的惯例不一致，在重复执行或从 P2 库升级的场景下会直接报错。
4. **`/api/health/**` 无鉴权**。`SecurityConfig` 的系统前缀拦截清单未包含 `/api/health`，也未显式 `permitAll`；
   接口本身会返回库名与 MySQL 版本，属信息暴露面，建议显式声明其公开或受保护状态。
5. **`DemoController` 的注释与实际不符**（详见 [PLAN.md](PLAN.md)）。类注释指向的 `POST /api/mdm/bom/{id}/change-request` 并不存在，
   真实入口是 `POST /api/mdm/boms/{id}/submit`；且 `reuseBizId` 参数被静默忽略、接口未受 `mfg.demo-mode` 约束。
6. **无启动自检**。启动横幅只打印端口与前缀表，不校验 Flyway 是否全绿、10 个模块的 Controller 是否都注册成功。
   建议在 `printStartupBanner()` 中补充「已装载 Controller 数 / 已应用迁移版本」两项，让演示开场就能发现模块漏接。
