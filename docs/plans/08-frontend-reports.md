# 08. 前端补齐与运营报表

> **依赖**：[02 MDM + PLM](02-mdm-plm.md)、[03 QMS + WMS](03-qms-wms.md)、[04 ERP + CRM](04-erp-crm.md)、[05 SRM + EAM](05-srm-eam.md)、[06 MES](06-mes.md)、[07 能源管理](07-energy.md)（后端的统计接口）· **预估**：1–2 轮 · **对应验收项**：十系统的 ⑤ 运营报表（主体）；同时决定 ①–④ 能否按「不依赖 Swagger 或脚本」的 P5 口径验收

## 目标

两件事并行。其一，把 `apps/web-portal/` 已知的 17 条缺口逐条修掉：4 条占位路由落地、5 个死代码文件清除、12 条无侧边栏入口的路由补菜单、分页与令牌来源统一、并把「菜单与路由互不校验」这一根因用契约测试锁死。其二，补齐十系统共同欠的账——**第 ⑤ 项「至少 1 页本系统运营查询或统计报表」目前十个系统全部未达标**（ERP 为部分），本计划为每个系统交付一页真实数据的报表页，共 10 页。

## 现状

`apps/web-portal/` 主干已跑通：114 条路由记录、93 个可达页面、两套布局、按系统授权的路由守卫。缺口集中在三处：**功能面**——`/mes/dispatch`、`/mes/andon`、`/energy/meters`、`/energy/alerts` 四条路由仍渲染 `ModulePlaceholder`（无表格、无表单、无请求）；**一致性**——`shared/systemCatalog.ts` 的 `menus`（每条仅 `{label, route}` 两个字段）与 `router/index.ts` 的 `children` 分开声明且互不校验，导致 12 条路由没有侧边栏入口，`BusinessWorkspaceView.vue` 声明的 22 个 `kind` 仅 8 个可达（其余 14 个是死配置），且 `configs[props.kind] || configs.crm` 会在 kind 未命中时**静默渲染 CRM 数据**；**工程化**——零测试、无 ESLint/CI、`pnpm-workspace.yaml` 仍是占位文本。报表侧则更彻底：全仓**只有一个**报表型端点（`MaterialController` 的 `@GetMapping("/stats")`），`/report`、`/summary`、`/analysis`、`/dashboard` 路径在十个系统里一个都没有。逐条证据见 [web-portal/STATUS.md](../../apps/web-portal/STATUS.md) 的「未实现 / 缺口」一节（共 17 条）。

## 任务拆解

| # | 模块 | 任务 | 涉及文件 / 位置 | 验收方式 |
|---|---|---|---|---|
| 1 | 前端 · 占位页 | 4 条占位路由落地为真实页面：派工排产、Andon 异常、能源仪表与采集点、能耗告警。新建视图并替换路由中的 `placeholder(...)` 为具名 `import(...)` | 新建 `src/modules/mes/DispatchView.vue`、`src/modules/mes/AndonView.vue`、`src/modules/energy/MeterView.vue`、`src/modules/energy/AlertView.vue`；改 `src/router/index.ts` 的 `mes`/`energy` 两个 `systemRoute` 块 | 四条路由不再渲染 `ModulePlaceholder`；每页有分页表格与至少一个业务动作（派工 / 响应 / 仪表停用 / 告警确认） |
| 2 | 前端 · 死代码 | 删除 5 个不被任何路由引用的文件（`DashboardView`、`EcnView`、`EcnWorkspaceView`、`ShellView`、`SystemWorkspaceView`）；其中 `DashboardView.vue` 是全工程唯一使用 `.metric-card` 三件套的页面，须先完成 #14 的模板迁移再删 | 删除 `src/views/DashboardView.vue`、`EcnView.vue`、`EcnWorkspaceView.vue`、`ShellView.vue`、`SystemWorkspaceView.vue` | `pnpm build`（`vue-tsc -b && vite build`）通过；全仓 grep 五个文件名零引用 |
| 3 | 前端 · 工程化 | 引入测试基建（本工程现无任何 `.spec.ts`、无 Vitest）：`pnpm add -D vitest @vue/test-utils jsdom`，在 `package.json` 增加 `test` 脚本（`vitest run`）。后续 #4、#10、#12 的契约测试都落在这里 | `apps/web-portal/package.json`、新建 `vitest.config.ts`、`src/shared/__tests__/` | `pnpm test` 可运行并产出报告；CI 可据此判定红绿 |
| 4 | 前端 · 一致性 | 根因修复「菜单与路由互不校验」：为 12 条无入口路由补菜单（`/mdm/categories`、`/mdm/units`、`/mdm/organizations`、`/mdm/cost-centers`、`/mdm/subjects`、`/qms/sampling`、`/qms/capas`、`/qms/8d`、`/srm/quality`、`/srm/performance`、`/wms/receipts`、`/wms/transfers`），并新增契约测试断言「每个 `systemRoute` 的每条子路由都有对应菜单项」与「每条菜单 `route` 都能命中路由」 | `src/shared/systemCatalog.ts`（`menus` 数组）、`src/router/index.ts`、新建 `src/shared/__tests__/catalog.spec.ts` | 契约测试通过；12 条路由可从侧边栏到达；再新增路由但漏配菜单时测试失败 |
| 5 | 前端 · 一致性 | `BusinessWorkspaceView.vue` 清理：删除 14 个不可达 `configs` 键（`mdmCustomers`、`mdmSuppliers`、`crm`、`erp`、`erpFinance`、`production`、`qms`、`qmsDefect`、`qmsRework`、`wmsInventory`、`wmsLocations`、`wmsTxn`、`srmPo`、`srmDelivery`）；把 `configs[props.kind] \|\| configs.crm` 的静默兜底改为显式报错 + 空态（当前未知 kind 会显示 CRM 数据） | `src/views/BusinessWorkspaceView.vue`；`/ops/:kind` 的 22 条映射见 `src/router/index.ts` | 传入未知 kind 显示空态与提示，不再显示 CRM 行；配置键数与 `workspace(...)` 调用数一致 |
| 6 | 前端 · 缺陷 | `ReferenceAdminView.vue` 修 prop 契约：组件声明 `defineProps<{kind:string}>()` 并移除 `route.path.split('/').pop()` 兜底（路由已传 `props: { kind }`，现状靠 URL 末段取值是偶然可用） | `src/modules/platform/ReferenceAdminView.vue`、`src/router/index.ts`（`/portal/roles`、`/portal/organization` 两条） | 两个页面仍正确渲染各自字典；改路由路径末段不再影响分支判定 |
| 7 | 前端 · 缺陷 | 分页统一：`views/MaterialView.vue` 接入 `TablePager`（`v-model:page`/`v-model:size`/`:total`/`@change`），移除写死的 `page=1&size=20`；`views/BusinessWorkspaceView.vue` 表尾「共 N 条」改用服务端 `totalElements`（现显示的是当前页条数），并从固定 `size:50` 改为 `TablePager` 驱动 | `src/views/MaterialView.vue`、`src/views/BusinessWorkspaceView.vue`、`src/shared/components/TablePager.vue`（复用，不改） | 物料超过 20 条可翻页；工作区页表尾总数等于接口 `totalElements` |
| 8 | 前端 · 缺陷 | `/erp/atp`（交期承诺）独立化：不再与 `/erp/sales-orders` 共用 `kind='sales'`。优先新建 `kind='atp'` 配置（列含承诺交期、可用量、检查结论）；若后端无独立 ATP 接口，则先做前端列与标题差异并在计划中标注（**待确认**） | `src/router/index.ts`、`src/modules/erp/ErpP2View.vue`（内联 `cs` 映射）或新建 `src/modules/erp/AtpView.vue` | 两个页面标题与列不同；`/erp/atp` 展示交期承诺相关字段 |
| 9 | 前端 · 缺陷 | 令牌来源统一：`api/http.ts` 请求拦截器改为从 Pinia `auth` store 读令牌，`localStorage` 只作持久化（现状同一状态有两个真相来源） | `src/api/http.ts`、`src/stores/auth.ts` | 登录、登出、401 跳转三条路径行为不变；`localStorage` 被外部改写不影响已建立的会话 |
| 10 | 前端 · 健壮性 | `P3BusinessView.vue` 兜底：`p3Catalog[\`${system}:${kind}\`]` 未命中时渲染 `el-empty` 与提示，而非让 `cfg.readonly` 抛错白屏（当前 19 条路由与 19 个配置键恰好一一对应，属隐式契约） | `src/shared/components/P3BusinessView.vue`、`src/shared/p3Catalog.ts` | 用非法 kind 访问不白屏；契约测试断言 19 条路由的 `(system,kind)` 都能在 `p3Catalog` 命中 |
| 11 | 前端 · 工程化 | 修正 `pnpm-workspace.yaml`：移除占位文本（现内容为 `allowBuilds: esbuild: set this to true or false`，是待办提示而非有效配置），保留 `.npmrc` 的 `onlyBuiltDependencies[]=esbuild` | `apps/web-portal/pnpm-workspace.yaml` | `pnpm install` 输出不再出现该占位；esbuild 构建产物仍正常生成 |
| 12 | 前端 · 深度 | SRM/WMS/QMS 的 19 条通用台账补深度：把「换列配置」升级为显式契约——每条 `kind` 明确列清单（不再靠接口首行对象 `Object.keys(rows[0]\|\|{}).slice(0,8)` 决定列）、状态中文映射、可用动作，并补详情抽屉。高频页若需专项视图，在本计划内只登记不实现（见「风险与前置」第 3 条） | `src/shared/p3Catalog.ts`、`src/shared/components/P3BusinessView.vue`、`src/modules/{crm,erp,plm,mdm}/…View.vue` 中同样使用动态取列的 5 处 | 空数据时表头仍正确显示；新增/删除后端字段不再静默改变表格列 |
| 13 | 前端 · 健壮性 | 错误边界与文案对齐：加路由级错误兜底（`app.config.errorHandler` 或路由 `onErrorCaptured`），避免任一页面渲染异常导致整页白屏；`LoginView.vue` 与 `PortalHome.vue` 里硬编码的「10 业务系统协同」等宣传文案改为从 `systemCatalog` 派生 | `src/main.ts`、`src/App.vue`、`src/views/LoginView.vue`、`src/modules/platform/PortalHome.vue` | 人为抛错页面显示兜底提示而非白屏；系统数变化时文案随之变化 |
| 14 | 报表 · 基座 | 报表页基座组件：新建 `ReportPage.vue`（页头 + 筛选区 + KPI 卡片行 + 明细表 + 导出位）与 `MetricCards.vue`（迁移 `DashboardView.vue` 的 `.metric-card`/`.metric-label`/`.metric-value` 与 blue/green/orange/purple 色调），复用 `src/styles/index.css` 已定义的 `.page-title`/`.page-subtitle`/`.toolbar`/`.section-gap`/`.metric-card` 全局类 | 新建 `src/shared/components/ReportPage.vue`、`src/shared/components/MetricCards.vue`（`src/components/` 为空目录，共享组件一律进 `shared/components/`） | 任一报表页只写筛选与数据映射，不重复写 KPI 样式；`pnpm build` 通过 |
| 15 | 报表 · 契约 | 统一报表接口契约：路径统一 `/api/<system>/statistics/*`，返回统一走 `ApiResponse<Map<String,Object>>`（对齐全仓唯一先例 `MaterialController` 的 `@GetMapping("/stats")`），前端统一 `http.get(url)` 后读 `data.data`；把该约定写入 [system-functional-catalog.md](../system-functional-catalog.md) 的验收说明或本计划附录 | 各系统 `query/` 层（由 [02](02-mdm-plm.md)–[07](07-energy.md) 交付）；`src/shared/components/ReportPage.vue` | 十页均能取到数据；任一页无数据时显示空态而非报错 |
| 16 | 报表 · MDM | 主数据分发与对账报表：门户卡展示待发布/已发布/驳回数量与分发成功率、失败重试清单、各下游副本一致性差异。已有 `GET /mdm/materials/stats`（返回 `draft`/`pending`/`published`/`rejected`/`total`）可先复用 | 路由 `/mdm/reports`；新建 `src/modules/mdm/MdmReportView.vue`；接口 `/api/mdm/statistics/distribution`（可先接既有 `/mdm/materials/stats`） | 页面可从侧边栏到达；分发成功率与失败清单取自真实接口 |
| 17 | 报表 · CRM | 销售漏斗与预测达成：线索→商机→报价→合同各阶段转化率、赢单率、按销售员/客户/产品的预测达成 | 路由 `/crm/reports`；新建 `src/modules/crm/SalesReportView.vue`；接口 `/api/crm/statistics/funnel`、`/forecast-achievement` | 漏斗各阶段数量与 `/crm/leads`、`/crm/opportunities` 列表口径可核对 |
| 18 | 报表 · ERP | 经营分析：收入、毛利、订单利润、回款与预算执行。注意 `/erp/margins`（成本毛利）已存在且是全工程最接近报表形态的页面（输入订单号 → 单条聚合 → `el-descriptions`），本页在其之上做区间级汇总 | 路由 `/erp/reports`；新建 `src/modules/erp/OperationReportView.vue`；接口 `/api/erp/statistics/revenue-margin`、`/cash-collection` | 汇总收入与 `/erp/receivables`、`/erp/receipts` 明细可对账；ERP 的 ⑤ 由「部分」转为「达标」 |
| 19 | 报表 · PLM | 变更与发布报表：ECR/ECO/ECN 各阶段周期与通过率、按变更对象的类型分布、发布记录与版本差异计数 | 路由 `/plm/reports`；新建 `src/modules/plm/ChangeReportView.vue`；接口 `/api/plm/statistics/change-cycle`、`/release-summary` | 变更周期可由单据时间戳复算；发布记录与 `/plm/ecns` 一致 |
| 20 | 报表 · SRM | 供应商绩效报表：准时交付率、来料合格率、价格、响应时效、综合评分与排名（口径来自功能目录「供应商绩效」模块）。可复用既有 `/srm/performance` 数据并补聚合 | 路由 `/srm/reports`；新建 `src/modules/srm/PerformanceReportView.vue`；接口 `/api/srm/statistics/supplier-performance` | 排名与 `/srm/performance` 明细可核对；空数据时显示空态 |
| 21 | 报表 · WMS | 仓储报表：库龄、周转率、呆滞料、缺料、库存准确率、作业时效（功能目录「仓储报表」模块的全部指标） | 路由 `/wms/reports`；新建 `src/modules/wms/WarehouseReportView.vue`；接口 `/api/wms/statistics/stock-age`、`/turnover`、`/slow-moving` | 库龄与 `/wms/inventory` 余额可对账；呆滞清单可导出 |
| 22 | 报表 · MES | 生产统计：工单进度、OEE、达成率、直通率、工时偏差、在制品（六类指标与 [06](06-mes.md) 的 `/api/mes/statistics/*` 六个端点一一对应）。**路由不得用 `/mes/reports`——该路径已是报工明细** | 路由 `/mes/statistics`（菜单名「生产统计」）；新建 `src/modules/mes/ProductionReportView.vue`；接口 `/api/mes/statistics/progress`、`/oee`、`/achievement`、`/fpy`、`/hours-variance`、`/wip` | 进度与 `/mes/work-orders` 累计数量可核对；报工明细路由与菜单不受影响 |
| 23 | 报表 · QMS | 质量报表：不良率、PPM、一次合格率、缺陷 Pareto（按缺陷代码排序）、供应商质量绩效 | 路由 `/qms/reports`；新建 `src/modules/qms/QualityReportView.vue`；接口 `/api/qms/statistics/defect-rate`、`/ppm`、`/pareto` | Pareto 前三项与 `/qms/defects` 明细可核对；PPM 计算口径在页面上有说明 |
| 24 | 报表 · EAM | 设备可靠性报表：MTBF、MTTR、停机时长与原因分布、故障模式分布、设备健康评分 | 路由 `/eam/reports`；新建 `src/modules/eam/ReliabilityReportView.vue`；接口 `/api/eam/statistics/mtbf-mttr`、`/downtime` | MTTR 与 `/eam/repairs` 完工时间可复算；停机时长与 `/eam/faults` 一致 |
| 25 | 报表 · 能源 | 能耗与碳排报表：日/月用能、单位能耗趋势、车间排名、超定额清单、峰平谷电费、碳排（六类指标与 [07](07-energy.md) 的 `/api/energy/statistics/*` 一一对应） | 路由 `/energy/reports`；新建 `src/modules/energy/EnergyReportView.vue`；接口 `/api/energy/statistics/daily`、`/unit-consumption-trend`、`/workshop-ranking`、`/over-quota`、`/peak-valley`、`/carbon` | 车间排名与 `/energy/workshops` 台账可对账；超定额清单与告警一致 |
| 26 | 报表 · 导出 | 报表导出统一：十页报表均提供 CSV 导出（沿用 `P3BusinessView` 既有做法：带 `﻿` BOM 头、如实标注「导出当前筛选结果」）；`MetricCards` 与明细表分别提供导出位 | `src/shared/components/ReportPage.vue`、十页报表视图 | 每页可导出当前筛选结果；Excel 打开无乱码 |
| 27 | 报表 · 显示 | 枚举中文映射补齐：把本批新增的枚举（`peak_type`、`alert_level`、`andon_type`、`quota_status`、`issue_type` 等）补进 `shared/display.ts` 的 `labels` 映射，避免报表页直出英文枚举（`zh()` 未命中时会原样返回） | `src/shared/display.ts` | 报表页与列表页无英文枚举裸露；未命中回退行为不变 |
| 28 | 前端 · 工程化 | 代码规范与 CI：引入 ESLint + Prettier（本工程现无任何 lint 配置），并在 CI 中跑 `pnpm install --frozen-lockfile` + `pnpm build` + `pnpm test`（`build` 已含 `vue-tsc -b` 类型检查门槛） | `apps/web-portal/package.json`、新建 `.eslintrc`/`eslint.config.js`、`.prettierrc`、`.github/workflows/` | CI 可在 PR 上给出红/绿结论；lint 与类型错误阻断合并 |

> 轮次划分：第 1 轮 = #1–#15（缺陷修复 + 报表基座），第 2 轮 = #16–#28（十页报表 + 导出 + 收尾）。**建议的替代分批**：报表页中 `#22 MES`、`#25 能源` 与 [06](06-mes.md)/[07](07-energy.md) 同期交付（同一批后端接口一次点通），其余 8 页等到对应系统收口后再做，避免前端等后端。

## 完成标准

- [ ] 4 条占位路由全部替换为真实页面，全仓不再有 `ModulePlaceholder` 引用；
- [ ] 5 个死代码文件已删除，`pnpm build` 通过；
- [ ] 12 条无侧边栏入口的路由全部可从菜单到达；菜单与路由的一致性由契约测试锁定（漏配即测试失败）；
- [ ] `BusinessWorkspaceView.vue` 的 14 个死配置已清除，未知 kind 不再静默显示 CRM 数据；
- [ ] 分页统一：`MaterialView` 可翻页、`BusinessWorkspaceView` 的总数取自服务端；
- [ ] `/erp/atp` 与 `/erp/sales-orders` 是两张不同页面；
- [ ] `api/http.ts` 的令牌来源唯一（Pinia store）；`P3BusinessView` 有兜底；`pnpm-workspace.yaml` 有效；
- [ ] 错误边界生效（渲染异常不白屏）；登录页与门户文案不再硬编码系统数；
- [ ] 引入 Vitest 与 ESLint/Prettier，CI 在 PR 上给出红绿结论；
- [ ] **十系统各有一页运营报表**，均可从侧边栏到达且展示真实接口数据（非占位、非硬编码）：MDM 分发与对账、CRM 销售漏斗与预测、ERP 经营分析、PLM 变更与发布、SRM 供应商绩效、WMS 仓储、MES 生产统计、QMS 质量、EAM 可靠性、能源能耗与碳排；
- [ ] 十页报表共用同一基座（`ReportPage.vue` + `MetricCards.vue`），接口路径与返回结构统一为 `/api/<system>/statistics/*` + `ApiResponse<Map<String,Object>>`；
- [ ] 十页报表均可导出 CSV；
- [ ] 十系统的第 ⑤ 项在 [../current-status.md](../current-status.md) 的验收矩阵中由「✗ / 部分」改为「✓」（ERP 由「部分」转「✓」）；
- [ ] [web-portal/STATUS.md](../../apps/web-portal/STATUS.md) 的 17 条缺口逐条更新为已修复或已登记为后续。

## 风险与前置

1. **报表页的具体指标依赖各系统的 `query/` 层**：本计划只定义页面、路由、菜单与接口契约，数据聚合端点由 [02](02-mdm-plm.md)–[07](07-energy.md) 交付。若某系统未交付，则本计划内只能补一个最小聚合端点，或把该页延后——**不建议**在前端做跨接口拼接计算，那会把业务口径散到界面层（违反 [web-portal/PLAN.md](../../apps/web-portal/PLAN.md)「不在前端维护业务规则」的既有约定）。
2. **无图表库**：`package.json` 现有依赖只有 `element-plus`、`axios`、`vue`、`vue-router`、`pinia` 与图标包，全仓无 `echarts`/`chart.js`/`svg`/`canvas` 用法，也没有 `el-statistic`/`el-progress` 的使用先例。本计划的报表以 **KPI 卡片行 + 明细表 + 数字/百分比** 实现，不引入图表依赖；若评审要求趋势图，需新增 `echarts` 并注意本工程用 pnpm、且 `.npmrc` 的 `onlyBuiltDependencies[]=esbuild` 会影响带构建脚本的依赖（**待确认**是否允许新增前端依赖）。
3. **SRM/WMS/QMS 的 19 条通用台账深度不足是结构性问题**：任务 #12 只做到「显式列清单 + 状态映射 + 详情抽屉」，把高频页下沉为专项视图（如 WMS 出入库、QMS 检验判定）的体量不在 1–2 轮内。本计划如实登记该缺口，不宣称已解决。
4. **契约测试依赖 #3 先落地**：#4、#10、#12 的断言都要跑在 Vitest 上。若 #3 因依赖审批未通过而不做，这三条只能退化为人工核对，一致性会重新回到「靠自觉」。
5. **明确不做**（属 STATUS 已登记但后端无对应能力的部分）：附件/文件上传（PLM 受控文档目前只有 `fileName` 文本字段，无真实存储与下载）、审批中心的委派/代理与批量操作、「退回上一节点」等审批分支。这些需要后端先行，不在本计划目标内。
6. **`views/` 目录只修不扩**：按 [web-portal/PLAN.md](../../apps/web-portal/PLAN.md) 的约定，新页面一律进 `src/modules/<系统>/`，`views/` 存量页面（`MaterialView`、`BusinessWorkspaceView`、`BomView` 等）本计划只做修补，不做目录迁移，避免与报表交付混在同一个 diff 里。
7. **`/mes/reports` 路径冲突**：该路径已是报工明细（`workspace('reports','mesReports')`，菜单「报工明细」）。MES 报表页因此走 `/mes/statistics`（菜单「生产统计」）。若评审倾向把报工明细改名为 `/mes/work-reports` 以腾出 `/mes/reports`，需同步改菜单、`BusinessWorkspaceView` 的 `url` 与 `/ops/mesReports` 重定向——本计划默认不改。
8. **轮数**：本计划 28 个任务、10 页报表，正常 1–2 轮；若十页报表全部等齐 02–07，实际会跨越多个轮次，建议按第 1 轮末尾给出的替代分批方案执行。
