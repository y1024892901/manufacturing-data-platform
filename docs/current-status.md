# 当前进展总览

> 截至 2026-09-22 · 汇总自各模块 `STATUS.md`，并对照 [功能目录](system-functional-catalog.md) 的「最低验收五项」
>
> 十个系统各自的详细接口清单与差距，见对应目录下的 `PLAN.md` / `STATUS.md`。

## 一句话结论

**P1 主线（MDM → PLM 变更链）已完整闭环，是完成度最高的一环；P2（CRM/ERP）单据主链跑通但无一单据能走到终态；P3（SRM/WMS/QMS）按纵向切片推进，其中 WMS 库存与 QMS 质量闭环较扎实；P4 仅 EAM 有最小闭环，MES 尚未开工，能源差距最大。P6/P7 未启动。**

项目按「每个系统纵向切片」推进，而非逐阶段串行完成——因此不应以阶段号推断某系统已可用。

## ⚠️ 安全警示：明文口令已进入公开仓库

**`ops/demo_master_data_lifecycle.py` 第 190、205 行硬编码了 MySQL `root` 的明文口令**（以 `MYSQL_PWD` 环境变量字面量传入，目标 `127.0.0.1:3306`，通过 `D:/mysql8/bin/mysql.exe` 直连）。

该文件自首个提交 `6cbe906` 起就已存在于仓库中，而仓库为 **public**——因此该口令**已在 GitHub 上公开暴露**，且可能已被缓存、索引或抓取。

这与项目自身的原则（`infra/.env` 被 `.gitignore` 排除、口令只存于环境变量）直接冲突，属于绕过保护的一次例外。

**处置建议**：

1. **立即轮换该口令**——这是唯一真正有效的措施。修改代码不会撤回已公开的内容；
2. 把脚本改为从 `infra/.env` 或环境变量读取，不再写死；
3. 如需从仓库历史中清除，需重写 `6cbe906` 之后的历史并强推（注意：**即使如此，已公开的内容也无法收回**，因此第 1 步不可省略）；
4. 检查该 MySQL 实例是否对公网可达——若可达，风险等级进一步上升。

## 十系统完成度

| 系统 | 完成度 | 已跑通的主线 | 最大缺口 |
|---|---|---|---|
| **MDM** | ★★★★☆ | 创建 → 审批 → 发布 → Outbox 分发 → 重试/对账 | 分发副本写了但下游没读；治理工作台（数据所有者/质量/重复检测）未实现 |
| **QMS** | ★★★★☆ | 检验 → 判定 → 驱动 WMS 库存状态 + 回写 SRM 质量 | 完全未接审批引擎；过程质量、成品质量、统计报表为零 |
| **WMS** | ★★★☆☆ | 收货 → 上架 → 12 类库存动作 → 六态质量转换（ledger 留痕） | 仓储报表、波次拣货、序列号、容器条码全空；存在两套库存写入通道 |
| **ERP** | ★★★☆☆ | 销售订单 → 信用 → ATP → MRP → 生产订单 → 开票 → 应收 → 回款 → 自动凭证 | 8 个本职模块全部只到「部分实现」，无单据可关闭或作废 |
| **PLM** | ★★★☆☆ | ECR → 影响分析 → ECO（三级审批）→ ECN → 实施建 MDM 新版本 | `domain/workflow/integration/query` 四层全空；工艺规划、失效控制、报表为零 |
| **SRM** | ★★☆☆☆ | 准入 → 询报价定标 → 采购订单 → ASN 到货 | 对账协同无表无接口；绩效/8D/订单变更/资质只有表没有逻辑 |
| **CRM** | ★★☆☆☆ | 报价 → 合同 → ERP 销售订单（3 审批流 + 3 幂等事件） | 7 个模块均缺关闭/作废；接口无业务权限校验 |
| **EAM** | ★★☆☆☆ | 设备建档 → 故障上报 → 维修完工 → 设备恢复 | 仅用 entity/repo/controller 三层；service/domain/workflow/integration/query 全空 |
| **能源** | ★☆☆☆☆ | 能耗录入 + 单位能耗自动计算 | 两个实体都无状态字段；定额、异常、碳管理、分摊均未实现 |
| **MES** | ★☆☆☆☆ | 工单 + 报工两条骨架 | 7 个本职模块中 5 个零代码（在制品、领退料、Andon、返工报废、报表） |

**共享模块**（非十系统，详见各自 `PLAN.md` / `STATUS.md`）：

| 模块 | 状态 | 说明 |
|---|---|---|
| `shared/common` | ★★★★☆ | 统一响应、异常、错误码、全局异常处理被 10 个模块使用；业务事件 Outbox-Inbox 总线跑通（13 个发布点、幂等 + 3 次重试 + 死信 + 人工重试端点） |
| `shared/security` | ★★★★☆ | 认证 + 两层授权（JWT、系统访问权 URL 拦截、`@PreAuthorize`）+ 登录/操作双审计已落地；**第三层「数据范围」已建未接线**（见缺陷 #10） |
| `shared/workflow` | ★★★★☆ | 审批内核完整：5 张核心表、7 类动作、16 个端点、**8 条流程定义**、6 个业务模块接入、5 个 `ApprovalCallback` 实现类；未实现会签/或签、超时自动流转、流程版本迁移 |
| `shared/masterdata` | ☆☆☆☆☆ | **完成度 0%**，空壳（见缺陷 #11） |

## 验收状态

按 [功能目录](system-functional-catalog.md) 的五项最低验收，**目前没有任何一个系统五项全达标**：

| 系统 | ①≥5 模块 | ②≥3 类单据到终态 | ③审批+异常闭环 | ④≥2 幂等事件 | ⑤运营报表 |
|---|---|---|---|---|---|
| MDM | 部分 | 部分 | ✓ | ✓ | ✗ |
| QMS | 部分 | ✓ | ✗（无审批） | 部分 | ✗ |
| WMS | 部分 | ✓ | 部分 | 部分 | ✗ |
| ERP | 部分 | ✗ | 部分 | 部分 | 部分 |
| PLM | 部分 | 部分 | ✓ | ✗ | ✗ |
| SRM | 部分 | 部分 | 部分 | ✓ | ✗ |
| CRM | 部分 | ✗ | 部分 | ✓ | ✗ |
| EAM | ✗ | 部分 | 部分 | ✗ | ✗ |
| 能源 | ✗ | ✗ | ✗ | ✗ | ✗ |
| MES | ✗ | ✗ | ✗ | ✗ | ✗ |

## 横向共性问题

以下问题跨多个模块重复出现，属结构性而非个别疏漏：

1. **接口级权限普遍缺失**。`SecurityConfig` 只提供 `SYSTEM_<系统码>` 的粗粒度门禁；ERP、SRM、MES、EAM、能源、CRM 的接口**没有任何 `@PreAuthorize`**，种子数据里已定义的细粒度权限码无人读取。
2. **事件只发不收**。`BusinessEventService` 把事件写入 outbox 并投递到 `biz_inbox`，但**全仓库没有任何模块订阅消费**。跨系统联动实际靠同进程直接跨库写入实现。
3. **JPA 实体落后于表结构**。多个模块的迁移脚本加了列，实体未映射，导致接口查不到这些字段（见各 `STATUS.md` 的具体列清单）。
4. **单据普遍走不到终态**。缺少「关闭 / 作废」动作是十系统里最普遍的缺口。
5. **`domain/`、`workflow/`、`integration/`、`query/` 四层在多数模块为空**，业务规则散落在 controller 与 service 中。

## 已确认的缺陷（可直接复现）

以下为核对源码时确认、可复现的具体问题：

| # | 位置 | 问题 |
|---|---|---|
| 1 | `BomController:22,25,26` | 三个接口守卫 `hasAuthority('MDM:BOM:UPDATE')`，但该权限码**不在权限字典中**（MDM 权限码全集无此项），任何角色含 ADMIN 都无法调用——现象是「能建 BOM 但不能改 BOM」 |
| 2 | `CrmP2Service.customer360()` | 查询 `src_mdm.md_customer_contact`，该表**全仓无建表语句**；MDM 实际建的是 `md_partner_contact`（V16）——客户 360 的联系人查询会直接报表不存在 |
| 3 | `MasterDataDistributor.writeToEnergy()` | 方法体为 `return 0;`，能源的 MDM 分发是**空实现**，`energy_md_equipment` 建表后从未被写入 |
| 4 | `P3CrudService.sc()` | SRM 的 `supplier-quality` 状态接口按 `status` 列判定，实际表列名为 `eight_d_status`——该接口任何调用必被拒 |
| 5 | `PlmCatalogController` | 使用 `PLM:PRODUCT:UPDATE` / `PLM:DOCUMENT:UPDATE` 守卫，两权限码在任何 SQL 中都不存在 |
| 6 | `EngineeringChangeService.implement()` | 有实现但 Controller 无 `/implement` 映射，为死代码 |
| 7 | `@GetMapping("/{type:ecrs\|ecos}")` | 正则不含 `ecns`，链服务的 `page("ecns")` 分支无法经该端点访问 |
| 8 | `src_eam.eam_equipment_status_log` | 有 DDL 无实体无接口，设备状态切换不落台账 |
| 9 | `WorkflowController` / `WorkflowQueryService` | 审批回调放在非标准的 `callback/` 包，而非约定的 `workflow/` |
| 10 | `DataScopeAspect` | 切点 `@Around("@annotation(dataScope)")` **全仓库 0 个使用点**，`sys_data_scope` 中已配的约 30 条规则全部不生效——销售代表能看到全部客户订单，与设计意图相反 |
| 11 | `shared/masterdata` | 空壳模块（`src/` 下仅 2 行 `package-info.java`），10 个模块 pom 声明依赖但全仓 0 行引用；消费方（crm/erp/wms）改为直接依赖 `app-mdm` 并 import `com.mfg.mdm.*`，耦合未消除 |
| 12 | `bootstrap/application.yml:37` | 注释称「各业务系统的库通过多数据源切换（见 shared/common 的 DataSourceConfig）」，但全仓库不存在 `DataSourceConfig` 或任何 `AbstractRoutingDataSource`；真实机制是单数据源 + 限定名 SQL |
| 13 | `SrmP3Controller.java` | 未被 git 跟踪（`??`），却是 `P3FlowService` 的唯一调用方——被清理则该服务完全失去调用方 |
| 14 | `mfg_ops.biz_outbox` | P3 链写 `SRM`/`WMS`/`QMS`，P2 模块写 `crm`/`erp`/`mes`/`srm`，系统编码大小写混存 |
| 15 | `src/views/` | 5 个文件不被任何路由引用且无构建产物（`DashboardView`、`EcnView`、`EcnWorkspaceView`、`ShellView`、`SystemWorkspaceView`）——死代码 |
| 16 | `systemCatalog.menus` vs `router/index.ts` | 菜单与路由**分开声明且互不校验**，导致 12 条路由没有侧边栏入口；`BusinessWorkspaceView` 声明的 22 个 `kind` 仅 8 个可达 |
| 17 | `ReferenceAdminView.vue` | 读 `route.meta.kind`，但路由只传 `props: { kind }` 且组件未声明该 prop——实际靠 `route.path.split('/').pop()` 兜底，**目前可用是偶然** |
| 18 | `MaterialView.vue` / `BusinessWorkspaceView.vue` | 分页不一致：前者写死 `page=1&size=20` 无法翻页；后者表头「共 N 条」显示的是当前页条数而非总数 |
| 19 | `api/http.ts` | 请求拦截器从 `localStorage` 直读令牌，而非从 Pinia store 读——同一份状态有两个真相来源 |
| 20 | `ops/demo_master_data_lifecycle.py:190,205` | **明文硬编码 MySQL root 口令**，且已进入公开仓库——见上方安全警示 |
| 21 | 库数口径 | `01_databases.sql` 文件头写「20 个」、`mysql8/README` 写 20、`db-init/README` 写 18，实测 `CREATE DATABASE` 为 **18** |
| 22 | `08_seed_data.sql` | 文件头写「28 个岗位角色 / 29 个演示账号」，实测各为 **36** |
| 23 | `ops/demo_approval_boundary.py` | 失败时不 `sys.exit(非0)`（结尾只打印 `N/11 通过`），无法被 CI 据此判定失败——同目录的 `demo_bom_approval.py` 反而有 `sys.exit(1)` |

### 已修复

**代码类**（验证：`mvn compile` BUILD SUCCESS、`vue-tsc -b` 通过、`py_compile` 通过）

| 原缺陷 | 修法 | 文件 |
|---|---|---|
| #1 BOM 改不了 | `MDM:BOM:UPDATE`（字典中不存在）→ `MDM:BOM:CHANGE`（已定义已授予，且与角色分层设计一致：主管可建不可改） | `mdm/.../controller/BomController.java` |
| #2 客户 360 报错 | `md_customer_contact`（无建表语句）→ `md_partner_contact WHERE partner_type='CUSTOMER'` | `crm/.../service/CrmP2Service.java` |
| #4 SRM 状态接口必被拒 | `P3CrudService.sc()` 补 `supplier-quality → eight_d_status` | `shared/common/.../service/P3CrudService.java` |
| #6 死代码 | 删除 `EngineeringChangeService.implement()`（其实现只是翻状态、不建 MDM 版本，与自身类注释矛盾；完整实现在 `EngineeringChangeChainService`），类注释改为指向后者 | `plm/.../service/EngineeringChangeService.java` |
| #9 包名不合约定 | `com.mfg.{crm,erp,mdm,plm}.callback` → `...workflow`（5 个类）。共享模块自身的 `com.mfg.workflow.callback` 保留——它是工作流引擎的扩展点 | 5 个文件 |
| #12 配置注释误导 | 改为说明实际的「单数据源 + 跨库限定名」机制 | `bootstrap/.../application.yml` |
| #14 事件系统码大小写混存 | P3 侧 `SRM`/`WMS`/`QMS` → 小写，与 `sys_permission.system_code` 的规范一致（`event_type` 的 `QMS.` 前缀保持大写，与 P2 写法一致） | `shared/common/.../P3FlowService.java`、`qms/.../QmsLifecycleService.java` |
| #19 401 后内存态未清 | 401 分支补清 Pinia store（路由守卫读的是 store 的 token，只清 localStorage 不够——在 `/login` 不跳转的场景下会被守卫弹回 `/portal`）；用动态 import 规避 `http.ts ↔ stores/auth.ts` 的模块级循环依赖 | `apps/web-portal/src/api/http.ts` |
| #15 死文件 | 删除 `src/views/` 下 5 个无任何路由引用的视图（`DashboardView`/`EcnView`/`EcnWorkspaceView`/`ShellView`/`SystemWorkspaceView`） | 5 个文件 |
| #16 菜单与路由脱节 | 补齐 **12 条**有页面无入口的菜单项（MDM 5 条参考数据、QMS 抽样方案/CAPA/8D、SRM 质量协同/绩效、WMS 收货/调拨）；`/erp/atp` 原本与 `/erp/sales-orders` 共用 `kind:'sales'`，新增独立 `atp` 配置与专属动作；顺带修正 `/qms/reworks` 的错误标签「返工与CAPA」 | `systemCatalog.ts`、`router/index.ts`、`ErpP2View.vue` |
| #17 属性契约失效 | `ReferenceAdminView` 此前读 `route.meta.kind`（路由从未设置该 meta），实际靠 path 末段兜底才碰巧可用；改为正确声明并读取路由传入的 `kind` prop，未知取值会告警 | `ReferenceAdminView.vue` |
| #18 分页不一致 | `MaterialView` 写死 `page=1&size=20` 无法翻页 → 接入 `TablePager`；`BusinessWorkspaceView` 表头「共 N 条」显示的是当前页条数 → 改读 `totalElements`，并补 `TablePager` 与切域重置页码 | 2 个文件 |
| #21 库数口径 | `01_databases.sql` 与 `mysql8/README.md` 的「20」→ **18**（实测 `CREATE DATABASE` 数） | 2 个文件 |
| #22 种子数字 | `08_seed_data.sql` 四处「28/29」→ **36**（实测 INSERT 元组数） | 1 个文件 |
| #23 脚本无法被 CI 判定 | 两个演示脚本补失败时 `sys.exit(1)`；`demo_bom_approval.py` 的「流程未通过」分支原本也只打印不退出 | 2 个文件 |

**文档/基础设施类**（上一轮完成）

- `infra/db-init/README.md` 执行清单漏列 `09`/`10`/`11`（原会导致照文档重建出权限全空的库）——已补全并加了显式警示；
- `infra/.env.example` 仍是 PostgreSQL 模板——已重写，键名与真实 `.env` 的 `MYSQL_*` / `SRC_*_DB` 对齐；
- `infra/README.md` 仍描述 PostgreSQL + `docker compose up`——已重写为本机 MySQL 方案；
- `infra/docker-compose.yml` 与 `infra/dockerfiles/`（依赖文件缺失、`compose up` 必然失败）——已删除。

### 经复核确认「不是缺陷」的条目

- **#7**（`@GetMapping("/{type:ecrs|ecos}")` 缺 `ecns`）：`GET /api/plm/ecns` 被两个 Controller 同时映射，Spring 的「字面量优先」规则使 `EngineeringChangeController` 胜出，**补正则并不会生效**（试改后已回退）。唯一消费者 `ChangeChainView.vue` 的表格是动态列，拿 JPA 实体照常渲染。链服务的 `ecns` 分支属死代码，非用户可见缺陷。
- **#13**（`SrmP3Controller` 未被跟踪）：已在上一次提交中补入。
- **#5**（`PLM:PRODUCT:UPDATE` / `PLM:DOCUMENT:UPDATE` 不存在）：三处 `@PreAuthorize` 均带 `or hasRole('ADMIN')` 且含已定义已授予的 `PLM:ECN:CREATE`，ADMIN 与持该码者可用，影响小于原述。
- 库存流水 `wms_inventory_ledger.source_system` 的大小写：与 `biz_outbox` 是两回事——该列被 `InventoryTransactionService` 用作**幂等比对**，自洽即可用，且存量流水已写为大写，改动反而可能让新旧记录对不上，故有意保留。

## 文档说明

- 本次文档整理为各模块新增了 `PLAN.md`（整体计划）与 `STATUS.md`（目前进展）；
- 尚未实施的骨架目录（`ai/`、`models/`、`orchestration/`、`transforms/`、`ingestion/`、`source-data/`、`tests/` 等）的 README 已精简为几行状态说明，原有设计稿已移除（可回溯提交 `6cbe906`）；
- 已废弃的历史方案目录（原 `apps/admin`、`apps/api`、`apps/web-admin`、`apps/web-report`）已删除。
