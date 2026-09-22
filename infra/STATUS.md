# infra/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

数据库层（本机 MySQL 8.0.43 实例 + 18 库 99 表 + 全套种子数据）已完整落地并执行完毕；`.env` 已配置可用，`README.md` 已同步为本机 MySQL 方案并写明「不使用 Docker」的理由；**容器化方案（docker-compose / dockerfiles）已被明确放弃，且相应的 `docker-compose.yml` 与 `dockerfiles/` 目录已从仓库中删除**，`configs/` 仍为空壳。

## 文件清单

| 文件 | 说明 |
|---|---|
| `PLAN.md` | ✅ 整体计划 —— 职责边界、不做什么、规划内容 |
| `README.md` | ✅ 已同步 —— 描述本机 MySQL 8.0.43 方案，含「不使用 Docker」一节（说明废弃原因）与 `db-init/` 11 个脚本的执行顺序 |
| `STATUS.md` | 本文件 —— 目前进展 |
| `.env` | 运行期真实配置（不入库）。含 `MYSQL_HOST/PORT/USER/PASSWORD`、10 个 `SRC_*_DB`、4 个 `DW_*_DB`、`META_DB`/`AUTH_DB`/`APP_DB`/`OPS_DB`、LLM Key、各服务端口、`JWT_SECRET` |
| `.env.example` | ⚠️ 陈旧 —— 模板内容是 `POSTGRES_*` / `WAREHOUSE_DB` / `MCP_RO_USER` 等 PostgreSQL 版键名，与真实 `.env` 的 `MYSQL_*` 键名**完全不匹配** |
| `configs/` | ❌ 未实现 —— 骨架目录，只有 README.md，没有任何配置文件 |
| `db-init/` | ✅ 11 个 SQL 脚本 + 3 个 md（README / PLAN / STATUS），详见 [db-init/STATUS.md](db-init/STATUS.md) |
| `mysql8/` | ✅ 3 个 .bat 启停脚本 + 3 个 md（README / PLAN / STATUS），详见 [mysql8/STATUS.md](mysql8/STATUS.md) |

> 本目录**只剩下上述内容**：早期容器化方案涉及的 `docker-compose.yml` 与 `dockerfiles/` 均已删除，`configs/` 下只有 README.md。

## 关键机制 / 使用方式

### 1. 为什么不使用 Docker

早期方案曾规划 `docker-compose.yml` + `dockerfiles/`，把 Python 侧服务（Dagster / API）容器化。**该方案已废弃，相关文件已从仓库中删除。**

原因：数据库直接使用本机 MySQL 8.0.43 实例即可满足演示需求；引入容器反而给演示现场增加不确定性 —— 镜像拉取、端口映射、卷权限都可能让演示卡在无关环节。

因此**当前演示全程不经过容器**：数据库用 `mysql8/` 的 .bat 启停，Java 系统用 `mvn spring-boot:run`，前端用 `npm run dev`，均为本机原生进程。

### 2. 配置的单一来源是 `.env`

`.gitignore` 里 `.env` / `*.key` / `*.pem` / `secrets/` 全部排除，因此真实口令不入库。`mysql8/stop-mysql8.bat` 与 `status-mysql8.bat` 都从 `..\.env` 按行解析 `MYSQL_PASSWORD`（`for /f "tokens=1,* delims=="`），脚本内不出现明文口令。

### 3. 数据分层：一个实例、18 个库

MySQL 的 database 在本项目里承担了「schema」的角色，用来做层次隔离：

```
src_mdm                     第 10 个系统（主数据权威源）
src_crm … src_energy        9 类业务系统（只持有主数据只读副本）
mfg_ods / mfg_dwd / mfg_dws / mfg_ads    数仓四层
mfg_meta / mfg_auth / mfg_app / mfg_ops  平台能力四库
```

建库语句在 `db-init/01_databases.sql`，统一 `utf8mb4` / `utf8mb4_0900_ai_ci`（选择 8.0 而非本机原有 5.7 正是为了这个排序规则与 CTE/窗口函数能力）。

## 未实现 / 缺口

| # | 缺口 | 影响 | 说明 |
|---|---|---|---|
| 1 | `.env.example` 与 `.env` 键名不兼容 | **高** | 按模板注释照抄 `cp .env.example .env` 会得到一个**没有 `MYSQL_*` 键**的配置，所有依赖方读不到数据库连接信息；模板仍是 PostgreSQL/LLM 双供应商的旧版 |
| 2 | `configs/` 无内容 | 低 | 计划中的 dbt profiles、Dagster 配置均未落地 |
| 3 | 无自动化「从零重建」入口 | 中 | `db-init/README.md` 里是 8 行手抄的 `mysql.exe < xx.sql` 命令，且**未包含 09~11 三个后补脚本** |
| 4 | 无版本/迁移管理 | 低 | 11 个脚本靠编号约定顺序，新增脚本（10、11 即为后补）无登记机制 |

### 关于「库数」与「表数」的口径不一致

三处说法互相矛盾，已逐一实数核对，以**实测为准**：

| 来源 | 说法 | 实测 |
|---|---|---|
| `db-init/README.md` | 18 个库、96 张表 | 库 ✅ 18；表 ❌ 96 只统计了 02~07 六个脚本 |
| `db-init/01_databases.sql` 文件头 | 「创建全部 **20** 个数据库」 | ❌ 文件内实际只有 **18** 条 `CREATE DATABASE` |
| `mysql8/README.md` | 「本项目 **20** 个库」 | ❌ 同上，实际 18 |

**实测口径**：`CREATE DATABASE` = 18；`CREATE TABLE` = 99（02~07 的 96 张 + `08` 的 `sys_dept` 1 张 + `10` 的 2 张 BOM 副本）。详见 [db-init/STATUS.md](db-init/STATUS.md) 的脚本清单表。
