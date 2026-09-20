# 制造业十系统协同平台

本项目是一个可操作的制造业业务演示平台。当前实施范围是 P0-P5：统一门户、身份权限、自研审批、MDM 与九类业务系统。P6 数仓治理和 P7 AI/MCP/Skill 已按当前决策暂缓，不作为现阶段启动依赖。

## 当前架构

| 层 | 技术与端口 | 职责 |
|---|---|---|
| Web | Vue 3 + TypeScript + Vite，5173 | 一个前端工程；主平台与十系统使用不同路由和布局 |
| API | Java 17 + Spring Boot 3.2，8080 | Maven 多模块、单进程；业务规则、审批、权限和审计 |
| 数据库 | 本机 MySQL 8，3306 | 单实例多 database；MDM、九业务库及平台库隔离 |
| 登录 | Spring Security + JWT | 8 小时令牌；同源标签页共享登录与同步退出 |
| 数据协作 | 业务服务 + 审批 + 事件 | 主数据唯一入口；业务单据通过状态动作和跨系统事件协作 |

## 页面入口

- `/login`：登录。
- `/portal`：统一门户，只展示系统入口、统一待办和平台管理。
- `/mdm/**`、`/crm/**`、`/erp/**`、`/plm/**`、`/srm/**`、`/wms/**`、`/mes/**`、`/qms/**`、`/eam/**`、`/energy/**`：十个独立系统工作区。

门户点击系统卡片后使用新标签页打开。JWT 不进入 URL，路由守卫和后端 API 都会校验系统访问权。

## 目录

```text
manufacturing-data-platform/
├── apps/web-portal/       Vue 3 唯一前端工程
├── source-apps/           Java 多模块单进程后端
│   ├── bootstrap/         IDEA 启动模块与 Flyway
│   ├── shared/            通用响应、安全、审批、主数据消费
│   ├── mdm/               统一主数据管理
│   └── crm|erp|plm|srm|wms|mes|qms|eam|energy/
├── infra/db-init/         MySQL 全量建库、建表和固定种子
├── docs/                  范围、技术基线和功能目录
└── tests/                 自动化验收
```

旧的 Python/React/数仓目录是后续阶段或历史方案，不参与 P0-P5 运行。

## 本地启动

### 1. MySQL 8

使用 `D:\mysql8` 实例并确认 3306 可连接。数据库初始化脚本位于 `infra/db-init/`，执行顺序为 `01` 到 `11`。

### 2. 后端（IDEA）

1. 将 `source-apps/pom.xml` 作为 Maven 工程导入。
2. Project SDK 和 Maven Runner JRE 都选择 Java 17。
3. 运行 `com.mfg.bootstrap.MfgSourceApplication`。
4. 在 IDEA Run Configuration 的 Environment variables 中设置：

```text
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_USER=root
MYSQL_PASSWORD=<本机 MySQL 8 密码>
JWT_SECRET=<至少 32 字节的本地演示密钥>
```

后端启动后访问 `http://localhost:8080/api/health`。Flyway 会对既有库执行增量迁移，不会运行 Docker。

### 3. 前端

```powershell
cd D:\manufacturing-data-platform\apps\web-portal
npm install
npm run dev
```

打开 `http://localhost:5173/login`。演示账号由 `/api/auth/demo-accounts` 动态返回；初始化脚本中的统一演示密码见 `infra/db-init/08_seed_data.sql`。

## 规则

- 客户、供应商、物料、产品、BOM、工艺、组织、仓库与财务基础数据以 MDM 为权威入口。
- Controller 只做协议转换，正式规则放在 service/domain。
- 所有变更 API 记录脱敏审计；密码和请求正文不会写入审计表。
- 业务系统 API 除业务权限外，还会校验 `SYSTEM_<系统码>` 访问权。
- P6/P7 恢复实施前，不引入 PostgreSQL、React、Hadoop 或额外前端端口。

实施范围见 [实施路线](docs/implementation-roadmap.md)，各系统功能边界见 [功能目录](docs/system-functional-catalog.md)。
