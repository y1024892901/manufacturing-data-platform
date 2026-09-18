# apps/api/routers/ — 路由层

## 技术栈
FastAPI `APIRouter` · Pydantic v2 响应模型 · 依赖注入

## 放什么代码

| 文件 | 前缀 | 包含 |
|---|---|---|
| `metrics.py` | `/metrics` | 统一指标查询（走 `metric_*` 表，口径唯一） |
| `reports.py` | `/reports` | 5 个报表页面的数据聚合 |
| `governance.py` | `/dq` | 质量统计、问题明细、对账差异 |
| `metadata.py` | `/tables` `/lineage` `/glossary` | 表画像、血缘、口径检索 |
| `agent.py` | `/agent` | AI 分析（SSE 流式）与问数 |
| `audit.py` | `/audit` | LLM 与 SQL 调用审计查询 |
| `health.py` | `/health` | 存活与就绪探针 |

## 路由约定

| 约定 | 说明 |
|---|---|
| 版本前缀 | 全部挂在 `/api/v1` 下，便于后续演进 |
| 响应模型 | 每个端点显式声明 `response_model`，自动生成 OpenAPI |
| 分页 | 列表接口统一 `page` / `page_size`，最大 500 |
| 导出 | 需要导出的端点加 `?format=csv`（原方案的"导出"验收项） |
| 权限 | 用 `Depends(require_role(...))` 声明式控制 |

## 干什么事情
1. 路由层**只做参数校验与响应组装**，业务逻辑放 `services/`
2. 指标类接口**只查 `metric_*` 与 `ads_*`**，绝不直接写复杂 SQL（口径在 dbt 里）
3. 所有列表接口返回 `total`，供前端分页组件使用
