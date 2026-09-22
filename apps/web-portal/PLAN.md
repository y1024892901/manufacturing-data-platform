# apps/web-portal/ — 统一前端工程

> **整体计划** · 前端工程（非十系统之一）

## 定位

web-portal 是**唯一在用**的前端工程，用一套 Vue 3 + TypeScript + Vite 构建产物同时承载「主平台」和「十系统工作区」。它本身不是十系统之一——十系统指的是 `source-apps/` 下的 MDM / CRM / ERP / PLM / SRM / WMS / MES / QMS / EAM / 能源这十个后端业务域，而 web-portal 是这十个域共用的**界面层**。

代码层面的落点是 `src/shared/systemCatalog.ts`：一份 10 条的静态目录声明了每个系统的 `code` / `name` / `accent`（主题色）/ `description` / `capabilities` / `menus`，门户卡片墙、系统侧边栏、系统首页三条视图全部从这一份数据派生。**新增第 11 个系统时，改的是这份目录 + 一组路由，而不是新建前端工程。**

对照 [docs/implementation-roadmap.md](../../docs/implementation-roadmap.md)，本工程对应 **P5「十系统门户与运营管理界面」**（单点登录、系统门户、角色化工作台、统一待办、跨系统数据流动地图、分发监控、审计查询）。P5 的验收口径是「管理者和各岗位可从页面完成核心操作，不依赖 Swagger 或脚本」——这也解释了本工程为什么用「配置驱动 + 共享组件」而不是给每个页面手写一套：先把十系统的操作面铺满，再按需把高频页面下沉为专项视图。

其余 `apps/` 子目录（`admin/`、`api/`、`web-admin/`、`web-report/`）均为**已废弃**的历史方案，见 [apps/README.md](../README.md)。

## 路由与布局规划

### 两套布局，一条分界线

| 布局 | 文件 | 承载 | 判断依据 |
|---|---|---|---|
| 门户布局 | `src/layouts/PortalLayout.vue` | 登录后的落地首页、平台级功能（用户/角色/组织/审批/事件/审计/运行监控） | 路径前缀 `/portal` |
| 系统工作区布局 | `src/layouts/SystemLayout.vue` | 十系统各自的业务页面 | 路由 `meta.system = <code>` |

`SystemLayout` 与 `PortalLayout` 是刻意的**两套视觉**：门户是深蓝渐变侧边栏 + 「统一工作台 / 平台管理」两组菜单；系统工作区是深灰侧边栏 + 该系统自己的 `menus`，并用 `system.accent` 作为主题色注入 CSS 变量 `--accent`（激活菜单项、品牌图标、系统首页渐变都用它）。这让十个系统在「同一个外壳」里仍能被一眼区分，同时保留统一的顶部栏、用户区、退出逻辑。

`SystemLayout` 底部固定一个「← 返回统一门户」按钮，`PortalHome` 打开系统时用 `window.open(path, '_blank')` 新标签页打开。这是**刻意的**：门户是「系统选择器」，不希望在系统内部还要靠浏览器后退回到门户；而新标签页共享同一个 `localStorage`，登录态天然延续，因此不需要额外的 SSO 跳转参数。

### 三种页面形态构成成熟度阶梯

`src/router/index.ts` 用三个工厂函数声明路由，形态本身就是完成度标签：

```ts
const placeholder = (path, title, description, items = []) => ({ path, component: Placeholder, props: {...} })
const workspace   = (path, kind)                        => ({ path, component: Workspace,  props: { kind } })
const systemRoute = (code, home, children)              => ({ path: `/${code}`, component: SystemLayout, meta: { system: code }, children: [{ path: '', redirect: `/${code}/dashboard` }, { path: 'dashboard', component: home }, ...children] })
```

| 形态 | 组件 | 含义 | 当前用量 |
|---|---|---|---|
| `placeholder(...)` | `src/shared/components/ModulePlaceholder.vue` | 排期占位，只渲染标题、描述和能力清单，**没有任何接口调用** | 4 条路由 |
| `workspace(...)` | `src/views/BusinessWorkspaceView.vue` | 表格 + 新建 + 业务动作按钮，一次性拉 50 条、无真分页 | 8 条路由 |
| 具名视图（`import(...)`） | 各系统模块目录 | 真实分页、增删改、审批流转、状态机 | 59 条路由 + 10 条 dashboard |

`systemRoute` 统一注入 `'' → /<code>/dashboard` 的默认重定向和 `meta.system`，因此**十个系统的布局装配、菜单渲染、访问权校验只写一遍**；差异只在 `children` 数组。`/ops/:kind` 是一张 22 条的旧 URL 映射表，把历史上 `/ops/crm`、`/ops/srmPo` 这类扁平路径 301 式地重定向到新的系统内路径，最后 `/:pathMatch(.*)*` 兜底回 `/portal`。

## 目录组织约定

```
src/
  main.ts            应用装配：Pinia → Router → Element Plus(zhCn) → 全局 $zh/$zhKey → 跨标签页同步 → mount
  App.vue            唯一根组件：离线提示条 + <router-view/>
  router/index.ts    全部路由与守卫（单文件，无子路由文件拆分）
  api/http.ts        唯一的 axios 实例与拦截器
  stores/auth.ts     唯一的 Pinia store（认证）
  layouts/           两套外壳，只放导航/页头/用户区，不放业务逻辑
  modules/<系统>/    十系统的业务页面，按系统分目录
  shared/            跨系统复用：目录数据、显示映射、通用组件
  views/             尚未归入某系统目录的页面（见「非目标」）
  styles/index.css   全局变量与通用类（.page-title / .metric-card / .toolbar / .status-dot …）
```

### 职责边界

| 目录 | 该放什么 | 不该放什么 |
|---|---|---|
| `layouts/` | 导航结构、页头、用户区的装配；菜单从 `systemCatalog` 读 | 任何 `http.get`。两个布局都只调 `auth.loadMe()` |
| `modules/<系统>/` | 该系统专属页面；一个页面可服务多条路由（靠 `props.kind` 区分） | 跨系统共用的表格/表单骨架 |
| `shared/components/` | 被两个以上系统复用的组件（4 个：`P3BusinessView` / `SystemOverview` / `TablePager` / `ModulePlaceholder`） | 只服务单一系统的页面 |
| `shared/systemCatalog.ts` | 系统目录元数据（门户卡片、侧边栏菜单、系统首页三处共用） | 业务表单字段定义 |
| `shared/p3Catalog.ts` | P3 系（SRM/WMS/QMS）列表的**列定义与表单字段定义**，`<system>:<kind>` 为键 | 组件 |
| `shared/display.ts` | 枚举中英映射 `zh()` 与字段名中英映射 `zhKey()` | 业务计算 |
| `stores/` | 全局且跨路由共享的状态。目前只有认证一项 | 列表页的数据（一律页面内 `ref` 自取） |
| `api/` | 唯一的 axios 实例、`baseURL`、超时、鉴权头、统一错误 | 各业务接口的封装函数——**没有 api 层，页面直接 `http.get('/mdm/materials')`** |

### 新增一个系统页面应该放哪里

1. **首选** `src/modules/<系统>/XxxView.vue`，在 `router/index.ts` 的对应 `systemRoute('<系统>', ...)` 的 `children` 里加一条 `{ path, component: () => import(...) }`；
2. 若该页面是「列表 + 新建 + 状态流转」的标准台账，**先看能不能进 `shared/p3Catalog.ts`**——加一条配置（`columns` + `fields` + `statuses`）即可，路由直接指向 `shared/components/P3BusinessView.vue`，不必新建 `.vue` 文件。这是本工程省掉最多重复代码的一步（19 条路由靠一份 24 行的目录文件支撑）；
3. 若要在侧边栏出现，必须在 `shared/systemCatalog.ts` 对应系统的 `menus` 里补一条——**路由存在不等于菜单存在**，两者是分开声明的（当前有 12 条路由没有菜单入口）；
4. 只有「两个以上系统共用」时，才把组件上提到 `shared/components/`。

## 权限与安全约束

以下均为**代码中真实存在**的约束，不是规划意图。

### JWT 存放

| 项 | 实现 |
|---|---|
| 存放位置 | `localStorage`，键名 `mfg_token`；用户档案同存 `localStorage.mfg_user` |
| 未用 | 没有 httpOnly Cookie，没有 refresh token，没有加密存储 |
| 注入方式 | `api/http.ts` 请求拦截器**直接从 `localStorage` 读**，而非从 Pinia store 读 |
| 超时 | `axios.create({ baseURL: '/api', timeout: 15000 })` |
| 失效处理 | 响应拦截器遇 `401` → 清除 `mfg_token` 与 `mfg_user` → `window.location.assign('/login?redirect=<当前路径>')` |

**权衡**：`localStorage` 意味着令牌对 XSS 可读，安全性弱于 httpOnly Cookie。选它是因为这是纯前端演示、后端同源部署、无独立认证域，`localStorage` 省掉了 CSRF 防护与跨域 Cookie 配置。请求拦截器绕过 store 直读 `localStorage` 是**重复的真相来源**——若将来改为内存存令牌，这两处必须同步改。

### 路由守卫（`src/router/index.ts` 的 `router.beforeEach`）

按顺序执行，逐条都是真实分支：

| # | 条件 | 结果 |
|---|---|---|
| 1 | `to.meta.public`（仅 `/login`） | 已有 token → 跳 `/portal`；否则放行 |
| 2 | 无 token | `{ path: '/login', query: { redirect: to.fullPath } }` |
| 3 | 有 token 但无 `user` | `await auth.loadMe()`；**失败则 `auth.clearLocal()` 并跳 `/login`** |
| 4 | `to.meta.admin` 且非管理员 | `/portal?denied=admin` |
| 5 | `to.meta.system` 且 `!auth.canAccess(system)` | `/portal?denied=<system>` |

`PortalHome` 在 `onMounted` 读取 `route.query.denied`，弹一条「当前账号没有该页面或业务系统的访问权限」——**守卫把拒绝原因编码进 query，由落地页负责解释**，避免守卫里直接弹 toast。

### 系统访问权校验

```ts
isAdmin:   state => Boolean(state.user?.roles?.includes('ADMIN'))
canAccess: state => (system: string) => Boolean(state.user?.roles?.includes('ADMIN') || state.user?.systems?.includes(system))
```

即**双重放行**：`ADMIN` 角色通行全部系统，其余账号按 `user.systems` 白名单判定。校验点在两处冗余执行——路由守卫（`meta.system`）与 `SystemLayout.onMounted`；`PortalHome` 用 `canAccess` 过滤卡片墙，因此**未授权系统不会出现在门户上**。

`meta.admin` 是门户布局内**唯一**的权限标记，只打在 `/portal/users`、`/portal/roles`、`/portal/organization`、`/portal/audit` 四条上。`/portal/approval`、`/portal/events`、`/portal/operations` 无标记，任何登录用户可达；侧边栏的「平台管理」分组整体包在 `v-if="auth.isAdmin"` 里。

### 跨标签页登录态同步

`stores/auth.ts` 建了一个 `BroadcastChannel('mfg-auth')`，并由 `main.ts` 调用 `initCrossTabSync()`：

- 登录成功 / 登出 → `channel.postMessage({ type: 'LOGIN' | 'LOGOUT' })`；
- 监听 `window` 的 `storage` 事件（`event.key === 'mfg_token'`）与 channel 的 `LOGOUT` 消息 → 一旦发现 `localStorage` 里没有 token，即 `clearLocal()` 并回调跳 `/login`。

**为什么需要**：门户用 `window.open` 新标签页打开系统，用户可能在某个标签页退出，其余标签页必须立刻掉线，否则会留下一个还持有内存态 `user` 的僵尸页面。回调里先判断 `router.currentRoute.value.path !== '/login'`，避免在登录页上重复跳转。`syncReady` 标志保证监听器只注册一次。

## 非目标

1. **不做微前端 / 不做多工程拆分。** 十个系统不各建一个 Vue 工程，不引入 Module Federation 或 qiankun。理由是演示场景下独立部署、独立版本、跨团队边界这些收益都不存在，而共享组件与统一身份的收益是立即的。
2. **不做服务端渲染。** 纯 SPA + `createWebHistory`，无 SSR / SSG，无 SEO 需求（内部系统）。
3. **不做前端权限点级控制。** 只做「系统级 + 管理员」两级；按钮级的细粒度授权一律交给后端 `@PreAuthorize`，前端不重复实现一套权限点。
4. **不写单元测试。** 本工程无任何 `.spec.ts` / `.test.ts`，无 Vitest / Playwright 配置；验收方式是「页面能点通」，见 [STATUS.md](STATUS.md)。这是一处明确的欠账，而非疏忽。
5. **不做移动端适配。** `styles/index.css` 写死 `body{min-width:1180px}`，是桌面优先的内部运营系统。
6. **不做 i18n。** 界面全中文硬编码，仅 `shared/display.ts` 把后端的英文枚举转成中文显示；无 vue-i18n，无语言包。
7. **不在前端维护业务规则。** 状态机、审批流、主数据「已发布才可选」等规则全部由后端判定，前端只负责把错误消息展示出来。
8. **不让 `views/` 继续膨胀。** 该目录是历史遗留的平铺结构，新页面一律进 `modules/<系统>/`；存量 `views/` 页面只做迁移，不再新增（详见 STATUS.md 的缺口章节）。
