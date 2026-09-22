# shared/security/ — 认证与权限（JWT / RBAC / 数据范围）

> **整体计划** · 共享技术模块（非十系统之一）

## 职责边界

### 解决什么横切问题

「你是谁、你能进哪个系统、进去后能做什么、你只能看哪些行、你干过什么」——这五个问题
贯穿全部 10 个业务系统。本模块把它们收敛成一条**每次请求必经的链路**：

```
请求 → JwtAuthenticationFilter（解析令牌 → 查库 → 写入 SecurityContext）
        │
        ├─ 第 1 层 系统访问权：SecurityConfig 的 URL 规则  /api/mdm/** → hasAuthority('SYSTEM_MDM')
        ├─ 第 2 层 角色与权限点：Controller 上的 @PreAuthorize("hasAuthority('MDM:BOM:CHANGE')")
        ├─ 第 3 层 数据范围：@DataScope 注解 + DataScopeAspect → DataScopeContext(SQL WHERE 条件)
        │         ⚠️ 已装配但**全仓库无任何方法标注 @DataScope**，见「不做什么」
        └─ 旁路   操作审计：OperationAuditAspect 记录写操作
```

三层是**由粗到细**的过滤：先拦住「无该系统的访问权」（403，连菜单都不该出现），
再判断「该角色能否执行此动作」，最后才是「同一动作下你能看到哪些数据行」。
**第 1、2 层与审计是实际生效的；第 3 层的落点已建好但尚未接线**——数据范围规则在
`sys_data_scope` 表里配置齐全（8 类资源、约 30 条角色规则），注解却没有任何使用点。

### 给谁用

| 使用方 | 用法 | 证据 |
|---|---|---|
| 10 个业务模块 | `CurrentUser.get()` 取当前用户；`CurrentUser.usernameOrSystem()` 在无上下文时回落 `system`；Controller 上标注 `@PreAuthorize` | pom 全部依赖 `shared-security` |
| `shared-workflow` | 审批引擎取提交人/审批人、校验角色、校验转交/加签/抄送对象是否存在 | `shared-workflow/pom.xml` 依赖本模块；`ApprovalEngine` 注入 `CurrentUser` 与 `SysUserRepository` |
| 前端 | 登录后调 `/api/auth/me` 拿到角色、权限点、可访问系统，据此渲染菜单与按钮 | `AuthController.me()` 直接返回 `LoginUser` |
| 演示登录页 | `/api/auth/demo-accounts` 按部门列出全部演示账号，做「一键切换身份」 | `AuthService.demoAccounts()` |
| 平台管理员 | `/api/admin/**` 下的用户、角色、权限、部门、登录日志、审计日志维护 | `UserAdminController`（类级 `@PreAuthorize("hasRole('ADMIN')")`） |

### 上游依赖

只依赖 `shared-common`（`ApiResponse` / `ErrorCode` / `BizException`）。
**不依赖 `shared-workflow`**：依赖方向是 `security ← workflow`，本模块是更下层的基座。

## 不做什么

| 非目标 | 现状 |
|---|---|
| **不做用户自助注册** | 全仓库无注册端点。用户只能由管理员在 `/api/admin/users` 创建，或从 CSV 导入 |
| **不做自助修改密码** | 只有管理员重置：`POST /api/admin/users/{id}/reset-password`。无「当前用户改自己密码」的接口 |
| **不做 OAuth2 / SSO / 第三方登录** | 自研 JWT（HS256，`jjwt` 库），账号密码 + BCrypt 校验 |
| **不做 Refresh Token** | 只有单一 access token，无刷新端点。过期即重新登录 |
| **不做服务端登出 / 令牌黑名单** | `POST /api/auth/logout` 直接返回 `ApiResponse.ok()`，注释写明「无状态令牌无需服务端注销；此处保留接口以便前端统一调用」。**令牌在有效期内始终可用**，撤回权限只能靠停用/锁定账号（每次请求都查库，这一步能立即生效） |
| **不做验证码 / 二次验证** | 有失败计数与锁定（阈值 5），但无图形验证码、无短信/邮箱验证码 |
| **数据范围当前未接线到任何查询** | `@DataScope` 注解与 `DataScopeAspect` 切面均已实现，但**全仓库 0 个方法标注该注解**（唯一出现处是注解自身的 javadoc 示例）。切点 `@Around("@annotation(dataScope)")` 因此永不触发——规则表 `sys_data_scope` 里配了规则，却没有任何查询受限。另有一条**并行的、状态与注解不同的**链路：`sys_user.data_scope_type` 被 `JwtAuthenticationFilter` 装进 `LoginUser`、并由 `/api/auth/me` 与 `AdminUserView` 回显，但同样**不参与任何 SQL 过滤**。要让数据范围真正生效，需要在业务查询方法上补 `@DataScope` 注解，或改为在 `Repository` 层统一读取 `DataScopeContext.current()` |
| **不做部门树递归数据范围** | `DEPT` 范围只匹配**单个** `dept_code`，不含下级部门。`sys_user.data_scope_value` 列可存多个值、也由 `/api/admin/users/{id}/data-scope` 写入并回显，但 `DataScopeAspect.resolveScope()` **只读取 `data_scope_type`，从不读取 `data_scope_value`**——该列的过滤语义未实现 |
| **不做字段级 / 按钮级权限下发** | `sys_permission.perm_type` 默认 `API`，但代码中无按 `perm_type` 分支的逻辑；权限只到「接口 + 数据行」粒度 |
| **不做 URL 权限矩阵的配置化** | 系统级 URL 规则（`/api/mdm/**` 等 10 条）**硬编码**在 `SecurityConfig` 中，新增系统需改代码（其余粒度靠注解，无需配置） |
| **不做多租户** | 无 tenant 概念，所有用户共享一套数据 |

## 代码分层

本项目 `source-apps` 统一分层为 `domain/ entity/ repo/ service/ controller/ workflow/ integration/ query/`。
本模块实际用 8 个包、空 4 个：

| 分层 | 现状 | 文件数 | 关键类 | 说明 |
|---|---|---|---|---|
| `config/` | ✅ 有（自定义包） | 5 | `SecurityConfig`、`JwtAuthenticationFilter`、`CurrentUser`、`OperationAuditAspect`、`SecurityExceptionHandler` | 安全相关的横切装配集中在此 |
| `controller/` | ✅ 有 | 3 | `AuthController`、`UserAdminController`、`NavigationController` | 认证、平台管理、菜单导航 |
| `dto/` | ✅ 有 | 11 | `LoginRequest/Response`、`DemoAccount`、`AdminUserCommand/View`、`RoleCommand`、`DepartmentCommand`、`IdSetCommand`、`SystemSetCommand`、`DataScopeCommand`、`PasswordResetCommand` | 全部为 `record`；写入用 `Command`、读取用 `View`，命名区分读写方向 |
| `entity/` | ✅ 有 | 6 | `SysUser`、`SysRole`、`SysPermission`、`SysDept`、`SysAuditLog`、`SysLoginLog` | 6 张平台表。注意 `sys_data_scope` **没有实体**——它只在切面里被 `JdbcTemplate` 直查 |
| `jwt/` | ✅ 有（自定义包） | 2 | `JwtTokenProvider`、`JwtProperties` | token 的签发/解析/取头，与 `config` 分开以便复用 |
| `principal/` | ✅ 有（自定义包） | 1 | `LoginUser` | 放 `SecurityContext` 的当前用户；`isAdmin()` / `hasRole()` / `hasPermission()` |
| `repo/` | ✅ 有 | 6 | 6 个 `JpaRepository` | 与 entity 一一对应 |
| `scope/` | ✅ 有（自定义包） | 3 | `@DataScope`、`DataScopeAspect`、`DataScopeContext` | 数据范围三件套：注解声明、切面解析、ThreadLocal 上下文 |
| `service/` | ✅ 有 | 5 | `AuthService`、`UserAdminService`、`PlatformAdminService`、`OperationAuditService`、`LoginAuditService` | 按「认证 / 用户 / 平台配置 / 审计写入」拆分 |
| `src/test/` | ✅ 有 | 1 | `DemoPasswordHashTest` | 见「关键设计约束 9」 |
| `domain/` | ❌ 空 | 0 | — | 权限模型简单（用户-角色-权限点三张关联表），规则内联在 `LoginUser` 与切面中，未抽领域层 |
| `workflow/` | ❌ 空 | 0 | — | 审批在 `shared-workflow`，本模块不参与 |
| `integration/` | ❌ 空 | 0 | — | 不对外集成，无外部身份提供方 |
| `query/` | ❌ 空 | 0 | — | 管理端分页查询收在各 `service` 内（如 `UserAdminService.page()`），量小未单开 |

合计 44 个 Java 文件（含 1 个测试）、约 2094 行。

## 关键设计约束

### 1. JWT 只放「系统访问权 + 角色」，权限点**每次查库**

`JwtTokenProvider.generate()` 写入 `sub`（用户名）、`realName`、`roles`、`systems`；
**权限点不入令牌**。`JwtAuthenticationFilter` 每次请求都 `findByUsernameAndDeletedFalse()` 查库，
把三样东西转成 Spring Security 的 authority：

| 来源 | authority 形式 | 供谁用 |
|---|---|---|
| `LoginUser.permissions` | 原样，如 `MDM:BOM:CHANGE` | `@PreAuthorize("hasAuthority('…')")` |
| `LoginUser.roleCodes` | 加前缀，如 `ROLE_ADMIN` | `hasRole('ADMIN')` |
| `LoginUser.systems` | 加前缀并大写，如 `SYSTEM_MDM` | `SecurityConfig` 的 URL 规则 |

**权衡**：代价是每请求一次用户查询（含 3 个 EAGER 关联），收益是**权限变更即时生效**
——管理员改完权限、甚至停用账号，都无需等令牌过期。演示时「改权限立刻见效」依赖这个设计。

**一处名实不符要注意**：`JwtTokenProvider` 的类注释称令牌携带 `roles` / `systems`
「让每次请求无需查库即可完成大部分权限判断」，但 `JwtAuthenticationFilter` 实际**只读取
`claims.getSubject()`（用户名）**，`roles` / `systems` 两个 claim 构造 authority 时完全没有被消费
——authority 全部来自查库得到的 `SysUser`。即当前实现是「令牌只当身份凭据，权限一律实时查库」。
保留这两个 claim 的代价是令牌变长且存在信息冗余（JWT 未加密，仅签名，客户端可解码看到）；
若确实要做无查库的快速判断，需要另配一套「以令牌为准」的过滤器并接受权限延迟生效。

### 2. 异常处理器必须按 `@Order` 排在兜底处理器之前

`SecurityExceptionHandler` 标了 `@Order(Ordered.HIGHEST_PRECEDENCE)`。若没有它，
`@PreAuthorize` 抛出的 `AccessDeniedException` 会被 `common` 的
`GlobalExceptionHandler.handleOther()` 捕获，返回**90001 系统内部错误 + HTTP 500**——
「无权限」被报成「系统故障」，错误语义完全错了。这是本模块**必须**存在一个
`@RestControllerAdvice` 的原因，而不是把安全异常也交给 `common` 处理
（`common` 看不到 `spring-security` 的类型，否则循环依赖）。

### 3. 登录失败的留痕必须用 `REQUIRES_NEW`

`LoginAuditService.failed()` / `succeeded()` 与 `OperationAuditService.record()` 都标
`@Transactional(propagation = REQUIRES_NEW)`。原因：`AuthService.login()` 自身是
`@Transactional`，登录失败会**抛出异常回滚事务**；若审计写入参与同一事务，
「登录失败」这条记录会被一起回滚——恰恰是最需要留下的记录。

### 4. 登录失败计数与锁定阈值 5

`LoginAuditService.LOCK_THRESHOLD = 5`：密码错误累计 5 次即 `is_locked = true`。
计数只在 `countFailure = true` 时累加（即**密码错误**才计数；账号不存在、已停用、已锁定不计数，
避免攻击者用错误账号把真实账号刷锁）。登录成功后 `AuthService.login()` 重置 `failedLoginCount = 0`，
管理员解锁（`/users/{id}/unlock`）与重置密码也会清零。

### 5. 数据范围切面的判定规则（**当前未被触发**，改动它前先确认是否要接线）

`DataScopeAspect.resolveScope()` 的判定顺序（**顺序本身就是约束**）。
下面的规则是**已实现的行为契约**，但因无方法标注 `@DataScope`，线上从未执行过：

| 情形 | 结果 | 理由 |
|---|---|---|
| 无登录上下文（定时任务、内部调用） | **不受限**，直接 `proceed()` | 否则定时任务会因取不到用户而崩 |
| 当前是 ADMIN 且注解未要求 `ignoreAdmin = true` | `ALL` | 管理员兜底 |
| `sys_user.data_scope_type` 不是 `ROLE` | 取其字面值（`ALL`/`DEPT`/`SELF`/`NONE`），无法解析则 `NONE` | 支持按用户覆盖角色规则；`NONE` 是「宁可不显示也不越权」 |
| 按角色查 `sys_data_scope`，取**最宽松**的一条 | 有 `ALL` → `ALL`；否则有 `DEPT` → `DEPT`；否则 `SELF` | 一人多岗时，任一角色允许看全部就应能看全部，否则会出现「升职后反而看不到数据」 |
| 该资源**未配置**任何规则 | `ALL` | 宽松默认，便于新资源上线时不被卡住 |

`DataScopeContext` 用 `ThreadLocal` 传递，`set` / `clear` 必须在 `try/finally` 中配对
——切面已如此实现，业务代码调 `DataScopeContext.current()` 时无上下文会返回
`ALL`（而非 null），避免拼 SQL 时出现空指针。

### 6. 操作审计只切 `@RestController`，且只记写方法

`OperationAuditAspect` 的切点是 `within(@RestController *)`，方法为
`POST/PUT/PATCH/DELETE` 且 URI **不以 `/api/auth/` 开头**（登录相关由 `LoginAuditService` 专门记录，
避免重复）。审计字段从 URI 推导：

```
/api/srm/purchase-orders/123   → system=srm, objectType=PURCHASE_ORDERS, objectId=123
/api/admin/users/5             → system=platform（特判）, objectType=USERS, objectId=5
```

成功写 `{"status":"SUCCESS"}`，失败写 `action = "FAILED_POST"` 等并**重新抛出异常**。
审计写入自身失败只 `log.warn`，绝不影响业务主流程。

### 7. 无状态 + CSRF 关闭 + CORS 全开（演示限定）

`SessionCreationPolicy.STATELESS`、`csrf.disable()`、`allowedOriginPatterns("*")` 且
`allowCredentials(true)`。`SecurityConfig` 已注释说明「本地演示允许同机前端访问；
正式部署应收敛到实际域名」。**这是明确的演示取舍，不是疏漏。**

### 8. 密钥与口令策略存在一处不一致（需注意）

- `JwtProperties.secret` 类内**有**默认值（`mfg-data-platform-demo-secret-key-change-me-in-production-2026`），
  `JwtTokenProvider.key()` 会校验长度不足 32 字节即抛异常；
- 但 `bootstrap/application.yml` 写的是 `secret: ${JWT_SECRET}`，**无默认值**，
  且文件头注释声明「MYSQL_PASSWORD / JWT_SECRET 均不设默认值，必须由环境变量注入」。

**实际生效的是 yml**：缺 `JWT_SECRET` 时应用启动失败。类内默认值仅在有人以其他配置源启动时才兜底，
保留它容易被误读为「可以不上密钥」，新增配置项时应避免这种双重声明。

### 9. 演示口令有回归测试兜底

`DemoPasswordHashTest` 断言种子哈希
`$2a$10$wMGsPx.8vZQdPRB46K3G4.dfFjIafjLx2hhhOyaTbB3y7fBId/AsK` 与文档化的演示口令
`Test@123456` 匹配。这条测试保护的是「种子数据里的哈希被改动后，文档却没人改」这类
演示事故——BCrypt 的哈希无法肉眼比对，只能靠 `matches()`。

### 10. 权限模型是两层结构，不要混用判断入口

`SysUser` 的类注释明确了两层：

1. **系统访问权**（`sys_user_system.system_code`）—— 能进哪几个系统；
2. **角色**（`sys_user_role` → `sys_role_permission` → `sys_permission.perm_code`）—— 进去之后能干什么。

代码里有三个等价的判断入口：`LoginUser.hasRole()` / `hasPermission()` / `canAccessSystem()`。
**注意 `canAccessSystem()` 目前无调用方**——URL 层由 `SecurityConfig` 的 authority 判断，
`NavigationController` 则直接写 `user.getSystems().contains(code)`。新增代码应统一用
`LoginUser` 的方法，避免判断口径分散。
