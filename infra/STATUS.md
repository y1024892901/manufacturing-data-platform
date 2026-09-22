# infra/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

数据库层（本机 MySQL 8.0.43 实例 + 18 库 99 表 + 全套种子数据）已完整落地并执行完毕；`.env` 已配置可用；**容器化部分（docker-compose / dockerfiles / configs）已被明确放弃或仍为空壳**，其中 `docker-compose.yml` 引用的两个 Dockerfile 与一个 `dagster.yaml` 实际并不存在。

## 文件清单

| 文件 | 说明 |
|---|---|
| `README.md` | ⚠️ 陈旧 —— 仍描述 PostgreSQL 16 + pgvector + `docker compose up` 的早期拓扑，未同步到 MySQL 本地方案 |
| `.env` | 运行期真实配置（不入库）。含 `MYSQL_HOST/PORT/USER/PASSWORD`、10 个 `SRC_*_DB`、4 个 `DW_*_DB`、`META_DB`/`AUTH_DB`/`APP_DB`/`OPS_DB`、LLM Key、各服务端口、`JWT_SECRET` |
| `.env.example` | ⚠️ 陈旧 —— 模板内容是 `POSTGRES_*` / `WAREHOUSE_DB` / `MCP_RO_USER` 等 PostgreSQL 版键名，与真实 `.env` 的 `MYSQL_*` 键名**完全不匹配** |
| `docker-compose.yml` | 仅编排 `dagster`(3000) 与 `api`(8000/8001) 两个 Python 服务；文件头与尾注明确记录「MySQL / 10 个 Java 源系统 / 前端刻意不容器化」的决策 |
| `configs/README.md` | ❌ 未实现 —— 骨架目录，无实现代码 |
| `dockerfiles/README.md` | ❌ 已废弃 —— 历史方案，不再采用 |
| `db-init/` | ✅ 11 个 SQL 脚本 + README，详见 [db-init/STATUS.md](db-init/STATUS.md) |
| `mysql8/` | ✅ 3 个 .bat 启停脚本 + README，详见 [mysql8/STATUS.md](mysql8/STATUS.md) |

> `configs/` 与 `dockerfiles/` 两个目录下**只有 README.md，没有任何配置文件**。

## 关键机制 / 使用方式

### 1. Docker 的真实定位：只服务 Python 服务，且当前不可用

`docker-compose.yml` 从 PostgreSQL 方案改为本机 MySQL 方案后，自身不再是「一键拉起全栈」的入口，而是**仅为不便在 Windows 原生运行的 Python 服务预留**。它正在向宿主机取数：

```yaml
MYSQL_HOST: host.docker.internal      # 容器内访问宿主机 3306
extra_hosts: ["host.docker.internal:host-gateway"]
```

**但当前它跑不起来**，因为它引用的三个文件都不存在：

| compose 中的引用 | 实际状态 |
|---|---|
| `dockerfile: infra/dockerfiles/dagster.Dockerfile` | ❌ 不存在（`dockerfiles/` 下只有 README.md） |
| `dockerfile: infra/dockerfiles/api.Dockerfile` | ❌ 不存在 |
| `volumes: ./configs/dagster.yaml` | ❌ 不存在 |

所以**当前演示不经过 Docker**：数据库用 `mysql8/` 的 .bat 启停，Java 系统用 `mvn spring-boot:run`，前端用 `npm run dev`。

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
| 1 | `docker-compose.yml` 无法构建 | 中 | 引用的 `dagster.Dockerfile`、`api.Dockerfile`、`configs/dagster.yaml` 三处均缺失，`docker compose up` 必然失败 |
| 2 | `.env.example` 与 `.env` 键名不兼容 | **高** | 按模板注释照抄 `cp .env.example .env` 会得到一个**没有 `MYSQL_*` 键**的配置，所有依赖方读不到数据库连接信息；模板仍是 PostgreSQL/LLM 双供应商的旧版 |
| 3 | `infra/README.md` 未同步 | 中 | 仍写着 `postgres` 镜像、5432 端口、`docker compose up -d`，与现状相反；「技术栈」小节为空标题 |
| 4 | `configs/` 无内容 | 低 | 计划中的 dbt profiles、Dagster 配置均未落地 |
| 5 | `dockerfiles/` 已废弃但未删除 | 低 | 目录仍在，易被误当作有效方案 |
| 6 | 无自动化「从零重建」入口 | 中 | `db-init/README.md` 里是 8 行手抄的 `mysql.exe < xx.sql` 命令，且**未包含 09~11 三个后补脚本** |
| 7 | 无版本/迁移管理 | 低 | 11 个脚本靠编号约定顺序，新增脚本（10、11 即为后补）无登记机制 |

### 关于「库数」与「表数」的口径不一致

三处说法互相矛盾，已逐一实数核对，以**实测为准**：

| 来源 | 说法 | 实测 |
|---|---|---|
| `db-init/README.md` | 18 个库、96 张表 | 库 ✅ 18；表 ❌ 96 只统计了 02~07 六个脚本 |
| `db-init/01_databases.sql` 文件头 | 「创建全部 **20** 个数据库」 | ❌ 文件内实际只有 **18** 条 `CREATE DATABASE` |
| `mysql8/README.md` | 「本项目 **20** 个库」 | ❌ 同上，实际 18 |

**实测口径**：`CREATE DATABASE` = 18；`CREATE TABLE` = 99（02~07 的 96 张 + `08` 的 `sys_dept` 1 张 + `10` 的 2 张 BOM 副本）。详见 [db-init/STATUS.md](db-init/STATUS.md) 的脚本清单表。
