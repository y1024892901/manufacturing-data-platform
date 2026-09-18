# infra/ — 基础设施与部署

## 技术栈


## 职责
提供**一条命令拉起全栈**的能力。演示系统的第一原则是「永不因环境问题失败」，本目录是这条原则的载体。

## 放什么代码 / 文件

```
infra/
├── docker-compose.yml          主编排文件，定义全部服务与依赖
├── .env.example                环境变量模板（数据库口令、LLM Key、端口）
├── dockerfiles/                各服务的 Dockerfile
├── db-init/                    容器首次启动时执行的初始化 SQL
└── configs/                    服务配置文件（pg 调优、dbt profiles 等）
```

## 服务拓扑

| 服务 | 镜像 | 端口 | 说明 |
|---|---|---|---|
| `postgres` | pgvector/pgvector:pg16 | 5432 | 一个实例，多个 database 分域 |
| `dagster` | 自建 | 3000 | 编排与血缘 UI |
| `api` | 自建 | 8000 | FastAPI 统一网关 |
| `web-report` | node | 5173 | 自研报表前端 |
| `web-admin` | node | 5174 | 管理后台前端 |

## 单实例多库设计（关键决策）

不启动 9 个数据库容器，而是在一个 PostgreSQL 实例内用 **database 隔离**：

| database | 用途 | 谁写 |
|---|---|---|
| `src_crm` … `src_energy` | 模拟源系统（可合并为 `src_sim`） | 造数引擎 |
| `warehouse` | ODS / DWD / DWS / ADS 四层 | dbt |
| `platform_meta` | 管理后台的元数据（系统、表、字段、规则、用户） | Admin API |
| `app_meta` | 向量库（pgvector）与 Agent 会话记录 | AI 服务 |

**收益**：一个容器覆盖全部数据需求，启动时间从分钟级降到秒级，且演示现场只需保证一个进程存活。

## 干什么事情
1. `docker compose up -d` 一键启动，服务间用 `depends_on` + 健康检查控制启动顺序
2. `.env` 统一管理密钥，**LLM API Key 绝不进代码库**
3. 首次启动自动建库、建扩展（pgvector）、建 schema 骨架
4. 提供 `docker compose down -v` 的完整重置路径，保证演示可反复重来
