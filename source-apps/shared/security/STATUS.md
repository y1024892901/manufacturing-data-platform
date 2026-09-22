# shared/security/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

**认证与授权两层已完整落地并被 10 个业务模块使用**（JWT 登录 + 系统访问权 URL 拦截 +
`@PreAuthorize` 权限点 + 登录/操作双审计 + 管理端用户角色部门维护）；
**数据范围（第三层）已建好但未接线**——`@DataScope` 全仓库 0 个使用点，规则表配了却不生效。

## 已实现

### 分层文件统计

| 分层 | 文件数 | 关键类 |
|---|---|---|
| `config/` | 5 | `SecurityConfig`（无状态链 + 10 条系统 URL 规则）、`JwtAuthenticationFilter`、`CurrentUser`、`OperationAuditAspect`、`SecurityExceptionHandler` |
| `controller/` | 3 | `AuthController`、`UserAdminController`、`NavigationController` |
| `dto/` | 11 | `LoginRequest`、`LoginResponse`、`DemoAccount`、`AdminUserCommand`、`AdminUserView`、`RoleCommand`、`DepartmentCommand`、`IdSetCommand`、`SystemSetCommand`、`DataScopeCommand`、`PasswordResetCommand` |
| `entity/` | 6 | `SysUser`、`SysRole`、`SysPermission`、`SysDept`、`SysAuditLog`、`SysLoginLog` |
| `jwt/` | 2 | `JwtTokenProvider`、`JwtProperties` |
| `principal/` | 1 | `LoginUser`（`isAdmin` / `hasRole` / `hasPermission` / `canAccessSystem`） |
| `repo/` | 6 | 6 个 `JpaRepository`，与 entity 一一对应 |
| `scope/` | 3 | `@DataScope`、`DataScopeAspect`、`DataScopeContext`（⚠️ 见「未实现」） |
| `service/` | 5 | `AuthService`、`UserAdminService`、`PlatformAdminService`、`OperationAuditService`、`LoginAuditService` |
| `src/test/` | 1 | `DemoPasswordHashTest`（断言演示口令 `Test@123456` 与种子哈希匹配） |
| `domain/` `workflow/` `integration/` `query/` | 0 | 空层，原因见 [PLAN.md](PLAN.md) 的分层表 |

合计 44 个 Java 文件（含 1 个测试）、约 2094 行。

## 对外接口 / 扩展点

### REST 端点

| 类型 | 名称 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，返回 JWT + 用户完整信息（角色/权限点/可访问系统/部门）。放行，无需令牌 |
| GET | `/api/auth/demo-accounts` | 演示账号清单（按部门排序，**不返回密码哈希**）。放行 |
| GET | `/api/auth/me` | 当前登录用户（直接返回 `LoginUser`）。供前端渲染菜单与按钮 |
| POST | `/api/auth/logout` | **空操作**：无状态令牌无需服务端注销，保留接口仅供前端统一调用 |
| GET | `/api/navigation?system={code}` | 按系统编码返回菜单树（`MenuItem(label, route)`）。直接读 `MENUS` 常量表；无该系统访问权抛 `FORBIDDEN` |
| GET/POST/PUT/DELETE | `/api/admin/users…` | 用户查询（关键字分页）、详情、新增、修改、软删除、启用/停用/锁定/解锁、重置密码、设置角色、设置可访问系统、设置数据范围 |
| GET | `/api/admin/users/export` | 导出 CSV（带 UTF-8 BOM） |
| POST | `/api/admin/users/import` | 导入 CSV（`multipart/form-data`，初始口令固定 `Test@123456`，按用户名跳过已存在项） |
| GET/POST/PUT/DELETE | `/api/admin/roles…` | 角色分页、下拉选项、新增、修改、删除、设置权限点 |
| GET | `/api/admin/permissions?system={code}` | 权限点列表（可按系统过滤） |
| GET/POST/PUT/DELETE | `/api/admin/departments…` | 部门分页、下拉选项、新增、修改、停用 |
| GET | `/api/admin/login-logs` | 登录日志分页（按时间倒序） |
| GET | `/api/admin/audit-logs` | 操作审计分页（按时间倒序） |

`/api/admin/**` 整个类标注 `@PreAuthorize("hasRole('ADMIN')")`。
`/api/navigation` 无注解，仅需登录。

### 供其他模块使用的扩展点

| 类型 | 名称 | 说明 |
|---|---|---|
| 工具类（静态） | `CurrentUser.get()` / `find()` / `usernameOrSystem()` | 全项目取当前用户的唯一入口。`get()` 未登录抛 401；`usernameOrSystem()` 在无上下文时回落 `system`，已在 CRM 等模块的 Controller/Service 中大量使用 |
| 上下文对象 | `LoginUser` | 放入 `SecurityContext` 的主体；提供 `isAdmin()` / `hasRole()` / `hasPermission()` |
| 声明式注解 | `@PreAuthorize("hasAuthority('…')")` | 由 `@EnableMethodSecurity` 开启；权限码形如 `MDM:BOM:CHANGE` |
| 声明式注解 | `@DataScope(resource=…, userField=…, deptField=…, ignoreAdmin=…)` | **已实现但 0 使用点**，见「未实现」 |
| 切面 | `OperationAuditAspect` | 切 `@RestController` 的 `POST/PUT/PATCH/DELETE`，排除 `/api/auth/`。其他模块的写接口**自动**被审计，无需改动 |
| 切面 | `DataScopeAspect` | 切 `@annotation(dataScope)`，设置 `DataScopeContext`。当前未被触发 |
| 上下文容器 | `DataScopeContext` / `DataScopeContext.ScopeType`（`ALL`/`DEPT`/`SELF`/`NONE`） | `ThreadLocal` 传递，`current()` 无上下文时返回 `ALL` |
| 配置项 | `mfg.jwt.secret` / `expire-minutes` / `header` / `prefix` / `issuer` | 见 `JwtProperties`；`application.yml` 中 `secret` 由 `${JWT_SECRET}` 注入，无默认值 |
| Spring Bean | `PasswordEncoder`（BCrypt） | 供其他模块复用同一套口令策略 |

## 数据表

**重要前提**：本模块（以及全项目）所有实体的 `@Table` 注解**都不带 `catalog` 属性**，
库名由唯一数据源的 JDBC URL 决定 —— `bootstrap/application.yml` 指向 **`mfg_auth`**。
故下表是「注解值 → 实际解析到的库名.表名」。

| 注解里的表名 | 实际库.表 | 实体 | 说明 |
|---|---|---|---|
| `sys_user` | `mfg_auth.sys_user` | `SysUser` | 用户；`data_scope_type` / `data_scope_value` 两列见「未实现」 |
| `sys_role` | `mfg_auth.sys_role` | `SysRole` | 角色；种子数据 36 条（含 `is_system` 内置标记，内置角色不可改编码/不可删） |
| `sys_permission` | `mfg_auth.sys_permission` | `SysPermission` | 权限点；种子数据 57 条权限码 |
| `sys_dept` | `mfg_auth.sys_dept` | `SysDept` | 部门（9 大中心）；删除实为置 `is_enabled=false` |
| `sys_login_log` | `mfg_auth.sys_login_log` | `SysLoginLog` | 登录日志（成功/失败、失败原因、IP、UA 截断 500 字符） |
| `sys_audit_log` | `mfg_auth.sys_audit_log` | `SysAuditLog` | 操作审计；`before_value` / `after_value` 为 JSON 列，当前只写 `after_value` |
| `sys_user_role` | `mfg_auth.sys_user_role` | （`@JoinTable`） | 用户-角色关联，`SysUser.roles` |
| `sys_role_permission` | `mfg_auth.sys_role_permission` | （`@JoinTable`） | 角色-权限关联，`SysRole.permissions` |
| `sys_user_system` | `mfg_auth.sys_user_system` | （`@CollectionTable`） | 用户可访问系统，`SysUser.systems` |
| —（无实体） | `mfg_auth.sys_data_scope` | 无 | 数据范围规则表。**只有 `DataScopeAspect` 用 `JdbcTemplate` 直查**，故意不建 JPA 实体（pom 注释：避免为它建实体）。DDL 见 `infra/db-init/03_auth_workflow.sql:116`，种子见 `08_seed_data.sql:390`；**无 Flyway 迁移涉及此表** |

DDL 全部由手写脚本 `infra/db-init/03_auth_workflow.sql` 建立（该文件 `USE mfg_auth;`），
Flyway 从 `baseline-version: 11` 起接管，`ddl-auto: none`。

## 未实现 / 缺口

| 缺口 | 现状与证据 |
|---|---|
| **数据范围完全未接线** | `@DataScope` 全仓库 **0 个使用点**（唯一出现处是 `DataScope.java:13` 的 javadoc 示例），切点永不触发。规则表 `sys_data_scope` 已配 8 类资源（`CUSTOMER`/`OPPORTUNITY`/`SALES_ORDER`/`WORK_ORDER`/`EQUIPMENT`/`INSPECTION`/`PURCHASE_ORDER`/`SUPPLIER`）约 30 条角色规则，**全部不生效**。影响：销售代表能看到所有客户的订单，与「数据范围」的设计意图相反 |
| **`data_scope_value` 无过滤语义** | 由 `PUT /api/admin/users/{id}/data-scope` 写入、`AdminUserView` 回显，但 `resolveScope()` 只读 `data_scope_type`，从不解析该列 |
| **`perm_type` 未使用** | `sys_permission.perm_type` 默认 `API`，代码中无按该列分支的逻辑（无按钮级/字段级权限） |
| **无服务端登出与令牌吊销** | `POST /api/auth/logout` 直接返回成功，无黑名单/白名单机制。令牌有效期内始终可用；账号停用能即时生效（每请求查库），但**已签发的令牌本身无法作废** |
| **无 Refresh Token** | 只有单一 access token（默认 480 分钟），过期即需重新登录 |
| **无自助注册 / 自助改密** | 只能由管理员创建与重置 |
| **无验证码 / 二次验证** | 仅靠失败计数锁定（阈值 5） |
| **`canAccessSystem()` / `hasPermission()` 无调用方** | `LoginUser` 提供的方法当前无人使用：URL 层走 `SecurityConfig` 的 authority，`NavigationController` 直接写 `user.getSystems().contains(code)`，判断口径分散 |
| **`JwtProperties.secret` 类内默认值与 yml 策略不一致** | 类内给了默认密钥，`application.yml` 用 `${JWT_SECRET}` 且无默认值（缺则启动失败）。实际生效的是 yml，类内默认值是误导性冗余 |
| **CORS 全开** | `allowedOriginPatterns("*")` + `allowCredentials(true)`，`SecurityConfig` 已注释为「本地演示」取舍，未收敛到实际域名 |
| **`SysAuditLog.beforeValue` 从未写入** | `OperationAuditService.record()` 只设置 `afterValue`，改前值无留痕 |
| **无单元测试覆盖鉴权链路** | 仅 1 个测试（演示口令哈希），过滤器/切面/权限判定均无测试 |
