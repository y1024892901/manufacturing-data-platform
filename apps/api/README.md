# apps/api/ — FastAPI 统一网关

## 技术栈
Python 3.11 · FastAPI · SQLModel（ORM）· Pydantic v2 · asyncpg · SSE-Starlette · Uvicorn

## 职责
前后端的唯一数据出口。所有对数据库与 AI 的访问都经过这里，便于统一加权限、限流与审计。

## 放什么代码 / 文件

```
apps/api/
├── main.py           应用入口与路由注册
├── routers/          按业务域拆分的路由
├── schemas/          Pydantic 请求/响应模型
├── services/         业务逻辑层
├── deps/             依赖注入（数据库会话、当前用户、权限）
└── core/             配置、日志、异常、中间件
```

## 接口清单

| 分组 | 端点 | 说明 |
|---|---|---|
| **报表** | `GET /metrics/{metric}` | 统一指标查询，支持维度与过滤 |
| | `GET /reports/dashboard` | 经营驾驶舱聚合数据 |
| | `GET /reports/order-delay` | 延期风险页数据 |
| | `GET /reports/kitting` | 齐套与缺料页数据 |
| | `GET /reports/equipment` | 设备与质量影响页数据 |
| **治理** | `GET /dq/summary` | 质量统计（通过率、问题数、趋势） |
| | `GET /dq/issues` | 问题明细，支持按规则/表筛选 |
| | `GET /recon/diff` | 跨层对账差异 |
| **元数据** | `GET /lineage/{table}` | 血缘查询（上下游 + 列级） |
| | `GET /glossary/search` | 口径检索 |
| | `GET /tables/{table}/profile` | 表画像：行数、字段、更新时间 |
| **AI** | `POST /agent/analyze` | **SSE 流式**根因分析 |
| | `POST /agent/ask` | 问数（非流式，快） |
| | `GET /audit/llm-calls` | LLM 调用审计 |
| | `GET /audit/sql-calls` | SQL 调用审计 |

## 关键设计

| 设计 | 目的 |
|---|---|
| **SSE 流式** | AI 分析逐字返回，演示体验远好于转圈等待 |
| **依赖注入** | 数据库会话、当前用户、权限统一管理，路由函数保持简洁 |
| **只读连接** | 报表类接口用只读账号，写操作只在 admin 模块 |
| **响应缓存** | 报表聚合结果缓存 5 分钟，演示翻页不等待 |
| **统一错误格式** | 错误码 + 人话说明 + 建议，前端可直接展示 |
| **请求审计** | 记录接口、参数、耗时、用户，供演示"合规能力" |

## 干什么事情
1. 前端**绝不直连数据库**，全部经此层（权限与审计的唯一收口）
2. 为帆软保留兼容：`/metrics` 接口可作为 FineReport 的 HTTP 数据源（如需回归对比演示）
3. `/docs` 自动生成的 OpenAPI 页面本身就是演示材料
4. 提供 `/health` 与 `/ready` 供 Docker 健康检查
