# apps/web-portal/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**web-portal 已把「统一门户 + 十系统工作区」的主干跑通：登录、跨标签页登出、按系统授权的路由守卫、两套布局、93 个可达页面全部落地，且用一份 10 条的 `systemCatalog` 与一份 24 行的 `p3Catalog` 把 19 条路由收敛到同一个配置驱动组件上；但深度极不均衡——MDM / CRM / ERP 走的是带审批流转的专项视图，SRM / WMS / QMS 三系统共 19 条路由是「同一张通用台账换列配置」，MES / EAM / 能源仍有 4 条路由只渲染占位页，另有 5 个 `views/` 文件已成死代码且 12 条路由没有侧边栏入口。**

## 已实现

### 路由清单

`src/router/index.ts` 共 **114 条路由记录**：4 条顶层独立路由、11 条布局路由（1 门户 + 10 系统）、8 条门户子路由、91 条系统内子路由（71 条显式 + 10 条 `dashboard` + 10 条 `'' → /<code>/dashboard` 默认重定向）。下表列出全部可达路径。

#### 顶层与门户布局（`src/layouts/PortalLayout.vue`）

| 路径 | 视图文件 | 说明 |
|---|---|---|
| `/login` | `src/views/LoginView.vue` | 登录页，`meta.public`；左宣传栅格 + 右表单，账号由 `GET /auth/demo-accounts` 动态拉取 |
| `/` | — | 重定向 `/portal` |
| `/portal` | `src/modules/platform/PortalHome.vue` | 系统门户卡片墙，按 `canAccess` 过滤；点击 `window.open` 新标签页进入 |
| `/portal/users` | `src/modules/platform/UserAdminView.vue` | 用户与系统授权，`meta.admin`；含 CSV 导入 / 导出 |
| `/portal/roles` | `src/modules/platform/ReferenceAdminView.vue`（`props.kind='roles'`） | 角色与权限点，`meta.admin` |
| `/portal/organization` | `src/modules/platform/ReferenceAdminView.vue`（`props.kind='organization'`） | 组织与岗位，`meta.admin` |
| `/portal/approval` | `src/views/ApprovalCenterView.vue` | 统一审批中心：待办 / 已办 / 我发起的 / 抄送我的 四页签 + 审批时间轴抽屉 |
| `/portal/events` | `src/modules/platform/EventMonitorView.vue` | 数据流转与分发监控：主数据分发 / 业务系统事件 双模式，支持重试与对账 |
| `/portal/audit` | `src/modules/platform/AuditLogView.vue` | 审计日志：操作日志 / 登录日志 双页签，`meta.admin` |
| `/portal/operations` | `src/views/OperationsView.vue` | 平台运行监控：`GET /health` + `GET /health/databases` 表结构清单 |
| `/ops/:kind` | — | 22 条旧 URL 兼容重定向（如 `/ops/crm → /crm/opportunities`），未命中回 `/portal` |
| `/:pathMatch(.*)*` | — | 兜底重定向 `/portal` |

#### MDM — 统一主数据管理（`/mdm`，`SystemLayout`）

| 路径 | 视图文件 | 说明 |
|---|---|---|
| `/mdm/dashboard` | `src/modules/mdm/HomeView.vue` | 系统首页，渲染 `SystemOverview` |
| `/mdm/materials` | `src/views/MaterialView.vue` | 物料主数据；新建草稿 + 提交审批；**无分页组件，写死 page=1&size=20** |
| `/mdm/products` | `src/modules/mdm/ProductView.vue` | 产品主数据；成品物料下拉取 `GET /mdm/materials/consumable`（仅已发布） |
| `/mdm/boms` | `src/views/BomView.vue` | BOM 与替代料；详情抽屉内逐行加载替代料并支持停用 |
| `/mdm/routings` | `src/modules/mdm/RoutingView.vue` | 工艺路线与工序；表头 + 工序明细一次提交，工序行可增删 |
| `/mdm/customers` | `src/modules/mdm/PartnerView.vue`（`kind='customers'`） | 客户与联系人；含联系人抽屉 |
| `/mdm/suppliers` | `src/modules/mdm/PartnerView.vue`（`kind='suppliers'`） | 供应商与联系人 |
| `/mdm/warehouses` | `src/modules/mdm/MdmResourceView.vue`（`kind='warehouses'`） | 仓库主数据 |
| `/mdm/work-centers` | `src/modules/mdm/MdmResourceView.vue`（`kind='work-centers'`） | 工作中心 |
| `/mdm/production-versions` | `src/modules/mdm/MdmResourceView.vue`（`kind='production-versions'`） | 生产版本 |
| `/mdm/governance` | `src/modules/mdm/GovernanceView.vue` | 主数据治理：合并记录 / 编码映射 / 变更历史 三页签 + 发起合并 |
| `/mdm/categories` | `src/modules/mdm/ReferenceDataView.vue`（`kind='categories'`） | 物料分类；**无侧边栏入口** |
| `/mdm/units` | `src/modules/mdm/ReferenceDataView.vue`（`kind='units'`） | 单位与换算；**无侧边栏入口** |
| `/mdm/organizations` | `src/modules/mdm/ReferenceDataView.vue`（`kind='organizations'`） | 组织主数据；**无侧边栏入口** |
| `/mdm/cost-centers` | `src/modules/mdm/ReferenceDataView.vue`（`kind='cost-centers'`） | 成本中心；**无侧边栏入口** |
| `/mdm/subjects` | `src/modules/mdm/ReferenceDataView.vue`（`kind='subjects'`） | 会计科目；**无侧边栏入口** |

#### CRM — 客户关系管理（`/crm`）

| 路径 | 视图文件 | 说明 |
|---|---|---|
| `/crm/dashboard` | `src/modules/crm/HomeView.vue` | 系统首页 |
| `/crm/customers` | `src/modules/crm/Customer360View.vue` | 客户360：按客户编码聚合商机 / 报价 / 合同 / 应收 / 客诉，动态生成页签 |
| `/crm/leads` | `src/modules/crm/CrmP2View.vue`（`kind='leads'`） | 销售线索；动作：分配 / 跟进 / 转商机 |
| `/crm/opportunities` | `src/modules/crm/CrmP2View.vue`（`kind='opportunities'`） | 商机管理；动作：推进阶段 |
| `/crm/quotations` | `src/modules/crm/CrmP2View.vue`（`kind='quotations'`） | 报价管理；动作：升版 / 提交审批 / 生效；新建含报价明细子表 |
| `/crm/contracts` | `src/modules/crm/CrmP2View.vue`（`kind='contracts'`） | 合同管理；动作：提交审批 / 生效 / 变更升版 / 生成订单；新建含回款计划 |
| `/crm/forecast` | `src/modules/crm/CrmP2View.vue`（`kind='forecast'`） | 销售预测；**只读**（隐藏新建按钮） |
| `/crm/complaints` | `src/modules/crm/CrmP2View.vue`（`kind='complaints'`） | 客诉协同 |

#### ERP — 企业资源计划（`/erp`）

| 路径 | 视图文件 | 说明 |
|---|---|---|
| `/erp/dashboard` | `src/modules/erp/HomeView.vue` | 系统首页 |
| `/erp/sales-orders` | `src/modules/erp/ErpP2View.vue`（`kind='sales'`） | 销售订单；动作：信用检查 / 交期计算 / 确认交期 / 信用例外 / 确认 / 取消 |
| `/erp/credits` | `src/modules/erp/ErpP2View.vue`（`kind='credits'`） | 信用管理 |
| `/erp/atp` | `src/modules/erp/ErpP2View.vue`（**`kind='sales'`**） | 交期承诺；**与销售订单共用同一个视图实例，非独立页面** |
| `/erp/mrp` | `src/modules/erp/ErpP2View.vue`（`kind='mrp'`） | 物料需求计划；动作：运行计划 |
| `/erp/suggestions` | `src/modules/erp/ErpP2View.vue`（`kind='suggestions'`） | 计划建议；动作：确认建议 |
| `/erp/production-orders` | `src/modules/erp/ErpP2View.vue`（`kind='production'`） | 生产订单；动作：齐套检查 / 下达 |
| `/erp/receivables` | `src/modules/erp/ErpP2View.vue`（`kind='receivables'`） | 应收账款 |
| `/erp/invoices` | `src/modules/erp/ErpP2View.vue`（`kind='invoices'`） | 销售发票 |
| `/erp/receipts` | `src/modules/erp/ErpP2View.vue`（`kind='receipts'`） | 回款与核销；动作：核销应收（两步输入） |
| `/erp/payables` | `src/modules/erp/ErpP2View.vue`（`kind='payables'`） | 应付账款 |
| `/erp/vouchers` | `src/modules/erp/ErpP2View.vue`（`kind='vouchers'`） | 业务凭证 |
| `/erp/margins` | `src/modules/erp/MarginView.vue` | 订单成本与毛利；按销售订单号查 `GET /erp/order-costs/{no}` |

#### PLM — 产品生命周期管理（`/plm`）

| 路径 | 视图文件 | 说明 |
|---|---|---|
| `/plm/dashboard` | `src/modules/plm/HomeView.vue` | 系统首页 |
| `/plm/families` | `src/modules/plm/PlmCatalogView.vue`（`kind='families'`） | 产品族；`releasable:false`，无发布按钮 |
| `/plm/products` | `src/modules/plm/PlmCatalogView.vue`（`kind='versions'`） | 产品版本 |
| `/plm/baselines` | `src/modules/plm/PlmCatalogView.vue`（`kind='baselines'`） | 工程基线 |
| `/plm/boms` | `src/modules/plm/PlmBomView.vue` | EBOM / MBOM 受控副本；结构抽屉 + 版本比较（ADDED / REMOVED / CHANGED） |
| `/plm/ecns` | `src/modules/plm/ChangeChainView.vue` | 工程变更闭环：变更申请 / 变更命令 / 变更通知 三页签 |
| `/plm/documents` | `src/modules/plm/PlmCatalogView.vue`（`kind='documents'`） | 受控文档 |

#### SRM / WMS / QMS — 三系统共用 `P3BusinessView`（共 19 条路由）

以下三节全部指向 `src/shared/components/P3BusinessView.vue`，列与表单来自 `src/shared/p3Catalog.ts`。

| 路径 | 配置键 | 说明 |
|---|---|---|
| `/srm/onboarding` | `srm:onboarding` | 供应商准入；可新增，状态：提交审批 / 批准 / 认证 / 驳回 / 加入黑名单 |
| `/srm/rfq` | `srm:rfqs` | 询报价与定标；可新增；额外动作「供应商报价」（写 `/srm/quotes`）、「定标」（`/srm/rfqs/{id}/award`） |
| `/srm/purchase-orders` | `srm:purchase-orders` | 采购订单；**`readonly:true`，无新增** |
| `/srm/deliveries` | `srm:asns` | ASN 到货协同；动作「确认到货」（`/srm/asns/{id}/arrive`） |
| `/srm/quality` | `srm:supplier-quality` | 供应商质量；**无侧边栏入口** |
| `/srm/performance` | `srm:performance` | 供应商绩效；`readonly:true`；**无侧边栏入口** |
| `/wms/locations` | `wms:putaway` | 库位与上架 |
| `/wms/receipts` | `wms:receipts` | 收货待检；`readonly:true`；**无侧边栏入口** |
| `/wms/inventory` | `wms:inventories` | 库存余额；`readonly:true` |
| `/wms/transactions` | `wms:inventory-actions` | 库存状态操作（冻结 / 解冻 / 隔离 / 报废 / 退供 / 领料 / 退料） |
| `/wms/transfers` | `wms:transfers` | 库存调拨；**无侧边栏入口** |
| `/wms/counting` | `wms:counts` | 盘点作业 |
| `/qms/standards` | `qms:standards` | 检验标准 |
| `/qms/sampling` | `qms:sampling-plans` | 抽样方案；**无侧边栏入口** |
| `/qms/inspections` | `qms:inspections` | 质量检验；动作「检验判定」（合格数,不合格数,结论） |
| `/qms/defects` | `qms:ncrs` | 不合格评审；状态：评审 / 返工 / 报废 / 让步 / 退供 / 关闭 |
| `/qms/reworks` | `qms:reworks` | 返工作业；`readonly:true` |
| `/qms/capas` | `qms:capas` | CAPA；**无侧边栏入口** |
| `/qms/8d` | `qms:8d` | 供应商 8D；**无侧边栏入口** |

#### MES / EAM / 能源 — 工作区与占位（`/mes`、`/eam`、`/energy`）

| 路径 | 视图文件 | 说明 |
|---|---|---|
| `/mes/dashboard` | `src/modules/mes/HomeView.vue` | 系统首页 |
| `/mes/work-orders` | `src/views/BusinessWorkspaceView.vue`（`kind='mes'`） | 工单；动作：开工 / 报工；**无分页，一次拉 50 条** |
| `/mes/dispatch` | `src/shared/components/ModulePlaceholder.vue` | **占位页**，无接口调用 |
| `/mes/reports` | `src/views/BusinessWorkspaceView.vue`（`kind='mesReports'`） | 报工明细 |
| `/mes/andon` | `src/shared/components/ModulePlaceholder.vue` | **占位页** |
| `/eam/dashboard` | `src/modules/eam/HomeView.vue` | 系统首页 |
| `/eam/equipments` | `src/views/BusinessWorkspaceView.vue`（`kind='eamEquipment'`） | 设备台账；动作：切换状态 |
| `/eam/inspections` | `src/views/BusinessWorkspaceView.vue`（`kind='eamInspection'`） | 点检计划；动作：完成点检 |
| `/eam/faults` | `src/views/BusinessWorkspaceView.vue`（`kind='eamFault'`） | 故障工单；动作：关闭故障 |
| `/eam/repairs` | `src/views/BusinessWorkspaceView.vue`（`kind='eamRepair'`） | 维修执行；动作：完成维修 |
| `/energy/dashboard` | `src/modules/energy/HomeView.vue` | 系统首页 |
| `/energy/meters` | `src/shared/components/ModulePlaceholder.vue` | **占位页** |
| `/energy/equipment` | `src/views/BusinessWorkspaceView.vue`（`kind='energy'`） | 设备能耗 |
| `/energy/workshops` | `src/views/BusinessWorkspaceView.vue`（`kind='energyWorkshop'`） | 车间能耗 |
| `/energy/alerts` | `src/shared/components/ModulePlaceholder.vue` | **占位页** |

### 视图文件统计

共 **46 个 `.vue`** + **8 个 `.ts`**，源码合计 1312 行（`src/` 下全部 `.vue` 与 `.ts`）。文件普遍是「单行压缩」风格——模板、逻辑、样式各占一行，因此**行数远小于实际规模**，不宜用行数衡量完成度。

| 目录 | 文件数 | 说明 |
|---|---|---|
| `src/App.vue` | 1 | 根组件：离线提示条 + `<router-view/>` |
| `src/layouts/` | 2 | `PortalLayout.vue`（门户外壳）、`SystemLayout.vue`（系统外壳） |
| `src/modules/` | **28** | 十系统业务页面，按系统分目录——见下表 |
| `src/shared/components/` | 4 | `P3BusinessView.vue`（20 行，支撑 19 条路由）、`TablePager.vue`（46 行）、`SystemOverview.vue`（8 行）、`ModulePlaceholder.vue`（1 行） |
| `src/views/` | **11** | 平铺的历史遗留目录；**6 个被路由引用，5 个已成死代码**——见「未实现 / 缺口」 |
| `src/styles/index.css` | 1（非 `.vue`） | 全局变量与通用类：`.page-title` / `.metric-card` / `.toolbar` / `.status-dot` 等 |

`src/modules/` 内部构成（28 个）：

| 子目录 | 文件数 | 构成 |
|---|---|---|
| `mdm/` | 7 | `HomeView`、`ProductView`、`RoutingView`、`PartnerView`、`MdmResourceView`、`ReferenceDataView`、`GovernanceView` |
| `platform/` | 5 | `PortalHome`、`UserAdminView`、`ReferenceAdminView`、`EventMonitorView`、`AuditLogView` |
| `plm/` | 4 | `HomeView`、`PlmCatalogView`、`PlmBomView`、`ChangeChainView` |
| `crm/` | 3 | `HomeView`、`Customer360View`、`CrmP2View` |
| `erp/` | 3 | `HomeView`、`ErpP2View`、`MarginView` |
| `eam/` `energy/` `mes/` `qms/` `srm/` `wms/` | 各 1 | 均为 `HomeView.vue`，内容一致：`<SystemOverview system="…"/>` |

> **十个 `modules/*/HomeView.vue` 全部是同一模式**：一行，渲染共享的 `SystemOverview`。真正的内容在 `SystemOverview.vue` 里——它从 `systemCatalog` 取该系统条目，渲染主题色 Hero、三条 `capabilities`、`menus.slice(1)` 生成的快捷入口宫格、以及一条「上游业务事件 → 领域处理 → 下游状态同步 → 审计与待办」协同链。因此**十个系统首页的差异完全由 `systemCatalog.ts` 的数据决定，零重复代码**。

### 关键机制

**1. 鉴权（`src/stores/auth.ts`，66 行，全工程唯一的 Pinia store）**

- 令牌与用户档案存 `localStorage`（`mfg_token` / `mfg_user`），登录写、登出清；
- `loadMe()` 在路由守卫里按需触发，把后端 `/auth/me` 的 `roleCodes` 映射为前端 `roles`，并补齐 `systems` / `permissions`；
- `isAdmin` / `canAccess(system)` 两个 getter 是全部前端权限判定的唯一出口；
- 跨标签页同步靠 `BroadcastChannel('mfg-auth')` + `storage` 事件（细节见 [PLAN.md](PLAN.md)）。

**2. HTTP 封装（`src/api/http.ts`，22 行）**

唯一的 axios 实例，`baseURL:'/api'`、`timeout:15000`（`vite.config.ts` 把 `/api` 代理到 `http://localhost:8080`）。三个真实行为：

| 行为 | 实现 |
|---|---|
| 注入令牌 | 请求拦截器从 `localStorage` 直读 `mfg_token`，拼 `Authorization: Bearer …` |
| 业务码判定 | 响应拦截器把 `body.code !== 0 && body.code !== 200` 一律 `reject(new Error(body.message))`——**业务错误因此也走 `catch`**，页面统一用 `ElMessage.error(e.message)` 呈现 |
| 401 处理 | 清 `mfg_token` / `mfg_user`，若当前不在 `/login` 则 `window.location.assign('/login?redirect=…')`；**用整页跳转而非路由跳转**，以彻底丢弃内存态 |
| 二进制旁路 | `responseType` 为 `blob` / `arraybuffer` 时直接返回，不做业务码解包（供 CSV 导出使用） |

**3. 分页组件（`src/shared/components/TablePager.vue`，46 行）**

全工程唯一的分页实现，标准 `v-model:page` / `v-model:size` + `@change` 三线协议：

```vue
<TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load"/>
```

内部封装 `el-pagination`（`background`，页长 `[10,20,50,100]`，布局 `sizes, prev, pager, next, jumper`），并在换页长时自动把 `page` 重置为 1——**因此每个调用页无需自己处理「换页长后页码越界」**。契约上 `page` / `size` 由父组件持有，组件只发事件不改内部状态。

**4. 配置驱动的通用台账（`P3BusinessView.vue` + `p3Catalog.ts`）**

这是本工程复用度最高的机制：**19 条路由共用 1 个 20 行组件 + 1 份 24 行配置**。`p3Catalog` 以 `<system>:<kind>` 为键声明 `columns`（列）/ `fields`（表单字段，含 `text|number|date|textarea|select` 类型与 `required`）/ `statuses`（可流转状态）/ `readonly`（是否只读）。组件据此渲染：动态列表、动态表单（`fieldComponent()` 按类型选 `el-select`/`el-date-picker`/`el-input`）、状态下拉（`POST /{system}/{kind}/{id}/status`）、详情抽屉、以及 **CSV 导入 / 导出**（导出带 `﻿` BOM 头以兼容 Excel，且诚实地标为「导出当前页」）。此外内置三条专用动作分支：`srm:rfqs` 定标、`srm:asns` 确认到货、`qms:inspections` 检验判定，以及 `srm:rfqs` 的供应商报价登记。

同样的「配置驱动」思路在四处独立实现，各自持有 `configs` / `cfg` / `cs` 映射：

| 组件 | 配置映射 | 服务路由数 |
|---|---|---|
| `shared/components/P3BusinessView.vue` | `p3Catalog`（独立文件） | 19 |
| `modules/erp/ErpP2View.vue` | 内联 `cs`（10 个 kind → api 路径） | 11 |
| `modules/crm/CrmP2View.vue` | 内联 `cfg` + `fields` | 6 |
| `modules/mdm/ReferenceDataView.vue` | 内联 `configs`（5 个字典） | 5 |
| `modules/mdm/MdmResourceView.vue` | 内联 `cs`（3 个 kind） | 3 |
| `modules/plm/PlmCatalogView.vue` | 内联 `configs`（4 个 kind） | 4 |
| `views/BusinessWorkspaceView.vue` | 内联 `configs`（22 个 kind） | 8（另有 14 个成死配置） |

**5. 枚举与字段名中英映射（`src/shared/display.ts`，14 行）**

- `zh(value)`：把后端英文枚举（约 90 个，覆盖状态、阶段、动作、密级、文档类型等）翻成中文，**未命中时原样返回**，`null`/空串返回 `—`；
- `zhKey(value)`：把后端**下划线字段名**（约 80 个，`material_code` → `物料编码`）翻成中文表头，未命中时原样返回。

两者都在 `main.ts` 挂到 `app.config.globalProperties.$zh` / `$zhKey`，因此模板里可直接写 `{{ $zh(row.status) }}` 和 `:label="$zhKey(k)"`。**这是「后端字段直出表格」得以成立的前提**——多个列表页用 `Object.keys(rows[0]||{}).slice(0,8)` 动态取列，靠 `zhKey` 才能有中文表头。

**6. 全局兜底与装配（`main.ts` / `App.vue`）**

`App.vue` 监听 `online` / `offline` 事件，断网时在顶部固定显示一条「网络已断开，当前页面数据可能不是最新状态」的 `el-alert`。`main.ts` 装配顺序为 Pinia → Router → Element Plus（`zh-cn` 语言包）→ 全局 `$zh`/`$zhKey` → `initCrossTabSync()` → `mount('#app')`。

**7. 打包分块（`vite.config.ts`）**

`manualChunks` 手工切出三块：`vue`（vue + vue-router + pinia）、`element`（element-plus）、`http`（axios），避免单一巨型 chunk。构建脚本为 `vue-tsc -b && vite build`——**类型检查是构建的前置门槛**。

## 未实现 / 缺口

**占位页（4 条路由只有占位，无任何接口调用）**

1. `/mes/dispatch`（排产与派工）、`/mes/andon`（Andon 异常）、`/energy/meters`（能源仪表与采集点）、`/energy/alerts`（能耗异常告警）——四者渲染 `ModulePlaceholder`，只展示标题、描述与能力清单，**没有表格、没有表单、没有请求**。配置里声明的「排产看板 / 班次 / 人员 / 设备资源」「仪表台账 / 采集点 / 计量层级 / 补录校验」「阈值规则 / 告警 / 责任人 / 处置闭环」等均未实现。

   **没有任何一个系统是「只有占位视图」**：占位只出现在 **MES（4 条中的 2 条）** 与 **能源（4 条中的 2 条）**，两系统的 `dashboard` 与另一条业务页（`/mes/work-orders`、`/energy/equipment`）均已是可用的工作区。其余 8 个系统（MDM / CRM / ERP / PLM / SRM / WMS / QMS / EAM）无占位路由。因此更准确的说法是：**MES 与能源各有一半功能面尚未开始，而非整个系统未实现**。

**死代码**

2. **`src/views/` 有 5 个文件不被任何路由引用**：`DashboardView.vue`、`EcnView.vue`、`EcnWorkspaceView.vue`、`ShellView.vue`、`SystemWorkspaceView.vue`。旁证是 `dist/assets/` 里没有它们的构建产物——说明它们从未进入构建图。其中 `EcnWorkspaceView.vue` 有 31 行、`ShellView.vue` 有 8 行，是**写完但被废弃的早期实现**。
3. **`BusinessWorkspaceView.vue` 的 `configs` 映射有 14 个死配置**。该文件声明 22 个 `kind`，但只有 8 个被 `workspace(...)` 路由实际传入（`mes`、`mesReports`、`eamEquipment`、`eamInspection`、`eamFault`、`eamRepair`、`energy`、`energyWorkshop`）。其余 14 个（`mdmCustomers`、`mdmSuppliers`、`crm`、`erp`、`erpFinance`、`production`、`qms`、`qmsDefect`、`qmsRework`、`wmsInventory`、`wmsLocations`、`wmsTxn`、`srmPo`、`srmDelivery`）虽然 `/ops/:kind` 的映射表里有对应键，但那条路由是**重定向**，不会渲染 `BusinessWorkspaceView`——这些配置永远走不到。

**路由与菜单不一致**

4. **12 条路由没有侧边栏入口，只能靠直接输入 URL 或从 `/ops/*` 旧路径重定向到达**：`/mdm/categories`、`/mdm/units`、`/mdm/organizations`、`/mdm/cost-centers`、`/mdm/subjects`（MDM 的 5 类基础字典）、`/qms/sampling`、`/qms/capas`、`/qms/8d`、`/srm/quality`、`/srm/performance`、`/wms/receipts`、`/wms/transfers`。根因是 `systemCatalog.ts` 的 `menus` 与 `router/index.ts` 的 `children` **分开声明、互不校验**（`/mdm` 路由有 16 个子页，菜单只列了 11 条）。
5. **`/erp/atp`（交期承诺）与 `/erp/sales-orders` 指向同一个 `kind='sales'`**，页面标题与列完全相同，并非独立的 ATP 视图。菜单里两者并列展示，会让使用者误以为有两个不同页面。

**代码质量问题**

6. **`ReferenceAdminView.vue` 声明读取 `route.meta.kind`，但路由只传了 `props: { kind }`，而该组件根本没有声明 `kind` prop**。它实际靠 `route.path.split('/').pop()` 兜底取值（`/portal/roles` → `roles`，`/portal/organization` → `organization`），**目前能正常工作是偶然**——一旦路径末尾与角色判定不一致就会判错分支，且路由里声明的 `props` 是无效代码。
7. **分页不统一**。`TablePager` 虽已被 18 个文件引用（是全工程复用度第二高的共享组件），但三个页面绕开了它：`views/MaterialView.vue` 写死 `page:1, size:20` 且表尾只有一行「共 N 条记录」（**超过 20 条物料就无法翻页**）；`views/BusinessWorkspaceView.vue` 一次请求 `size:50` 且只显示 `rows.length`（表头「共 N 条」实际是**当前页条数**，不是总数）。此外 `ReferenceDataView.vue`（MDM 五类基础字典）调的是 `GET /mdm/reference/{kind}`，**接口不分页、返回全量数组**，页面上也没有分页控件——字典量小时无妨，量大时会一次性铺满整屏。
8. **`P3BusinessView.vue` 的 `cfg` 无兜底**。`p3Catalog[`${system}:${kind}`]` 未命中时 `cfg.value` 为 `undefined`，模板中 `cfg.readonly`、`cfg.columns` 会直接抛错。当前 19 条路由与 19 个配置键**恰好一一对应**，属隐式契约，缺一个键就是白屏。
9. **列表页动态取列的脆弱性**。`CrmP2View`、`ErpP2View`、`ChangeChainView`、`Customer360View`、`GovernanceView` 都用 `Object.keys(rows[0] || {}).slice(0, 8)` 之类的方式**按接口返回的第一行对象决定列**。优点是后端加字段前端自动显示；缺点是**首行为空即无列、列序由 JSON 键序决定、字段增减会静默改变表格**，且列数被硬截断在 8/9 个。

**工程化欠账**

10. **零测试**。全工程无 `.spec.ts` / `.test.ts`、无 Vitest / Playwright 配置、`package.json` 无 `test` 脚本。`README.md` 只说明 `pnpm install` / `pnpm dev`，验收方式完全是手工点通。
11. **无 ESLint / Prettier / EditorConfig**，无 CI 配置。风格靠单行压缩的既有惯例维持。
12. **`pnpm-workspace.yaml` 是未填写的占位**，内容为 `allowBuilds: esbuild: set this to true or false`——明显是待办提示文本而非有效配置（真正生效的是 `.npmrc` 的 `onlyBuiltDependencies[]=esbuild`）。
13. **无错误边界**。任一页面渲染期抛错（如第 8 条的 `cfg` 未命中）会整页白屏，没有 `errorCaptured` 或路由级兜底。
14. **`marketing` 式硬编码残留**。`LoginView.vue` 的宣传文案（「10 业务系统协同」「1 统一主数据中心」「∞ 可追溯业务链路」）与 `PortalHome` 的标题写死在模板里，与 `systemCatalog` 的实际系统数不复核——若系统数变化，文案不会跟着变。

**仅前端可见、后端无对应能力的部分**

15. **导出能力仅两处**：`P3BusinessView` 的「导出当前页」CSV 与 `UserAdminView` 的 `/admin/users/export`。其余所有列表页**没有导出**。
16. **附件 / 文件上传未实现**：除 `UserAdminView` 的 CSV 导入外，全工程无文件上传组件；PLM 的「受控文档」(`/plm/documents`) 只有 `fileName` 文本字段，**没有真正的文件存储与下载**。
17. **审批中心无「委派 / 代理」与批量操作**，只能逐条同意 / 驳回 / 转交 / 加签 / 抄送；且审批动作**只有同意与驳回两种结论**，没有「退回上一节点」「征求他人意见」等分支。

## 验证方式

```bash
cd apps/web-portal
pnpm install
pnpm dev          # Vite 起在 5173，/api 代理到 http://localhost:8080
```

后端未启动时 `/api` 代理失败，页面会走 `api/http.ts` 的错误分支弹出「网络请求失败」，属预期行为；需要真实数据时先按 [source-apps/bootstrap](../../source-apps/bootstrap/) 启动 Java 服务。
