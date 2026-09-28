# AI 数据问答与 MCP

> 状态：基础版已实现。面向当前 MySQL 单实例中的十个 `src_*` 系统库，支持 DeepSeek 或阿里云千问的自然语言只读查询。

## 链路

```text
Portal AI 问答页
  -> Python AI API (8000)
  -> OpenAI 兼容 Chat Completions：DeepSeek / 千问
  -> MCP Client
  -> MCP Streamable HTTP Server (8001)
  -> MySQL 源系统数据库
```

AI Agent 会读取 MCP 工具列表，再按问题调用数据源发现、表结构搜索、表结构查看和只读 SQL 查询工具。API Key 与 MySQL 配置均从本机环境变量读取；模型 Key 不返回给浏览器。

## 本机启动

先确认 MySQL 服务已启动，且 `infra/.env` 已配置 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USER`、`MYSQL_PASSWORD`。AI 模块会从此文件读取数据库连接信息。

在仓库根目录执行：

```powershell
Copy-Item ai/.env.example ai/.env
# 编辑 ai/.env，填写 DEEPSEEK_API_KEY 或 DASHSCOPE_API_KEY
uv sync --project ai
```

分别在两个终端启动服务：

```powershell
# 终端 1：MCP Streamable HTTP，http://127.0.0.1:8001/mcp
uv run --project ai python -m ai.mcp_server.server
```

```powershell
# 终端 2：AI 问答 API，http://127.0.0.1:8000
uv run --project ai python -m ai.api
```

再按项目现有方式启动前端（`apps/web-portal` 下执行 `npm run dev`），登录后从左侧“AI 数据问答”进入。Vite 开发代理会把 `/ai-api/*` 转发到 8000 端口。

服务检查地址：

- API 健康检查：<http://127.0.0.1:8000/health>
- 可配置模型：<http://127.0.0.1:8000/api/ai/providers>
- API 文档：<http://127.0.0.1:8000/docs>
- MCP Inspector / MCP 客户端地址：<http://127.0.0.1:8001/mcp>

## 配置

`ai/.env.example` 可复制为 `ai/.env`。选择 `AI_PROVIDER=deepseek` 或 `AI_PROVIDER=qwen` 作为默认模型，也可在问答页面切换。DeepSeek 使用 `DEEPSEEK_API_KEY`；千问使用 `DASHSCOPE_API_KEY`（也接受 `QWEN_API_KEY`）。模型 ID 和兼容接口 URL 均可配置。

数据库连接与十个系统库名沿用 `infra/.env` 中已有的 `MYSQL_*`、`SRC_*_DB` 配置。AI API 和 MCP 默认绑定 `127.0.0.1`，适用于本机演示；服务没有用户级数据权限控制。

## MCP 工具

| 工具 | 作用 |
|---|---|
| `list_data_sources` | 列出 MDM、CRM、ERP、PLM、SRM、WMS、MES、QMS、EAM、能源系统 |
| `search_data_schema` | 按关键词搜索十个系统中的表、字段和注释 |
| `list_system_tables` | 列出一个系统中的全部表和视图 |
| `describe_data_table` | 查看表字段、类型、主键提示和注释 |
| `query_system_data` | 在指定源库执行只读 SQL，结果最多返回 100 行 |

每次查询选择一个源系统库。问题涉及多个系统时，AI 可以分别查询并列出各自结果；当前版本不自动建立跨库关联或推断业务关系。

AI 运行时可探索已经接入的表结构，不要求预先为每一种自然语言问题编写专用接口。查询端仅开放 `src_*` 源库，并校验单条 `SELECT`，设置执行超时与结果行数上限；这些是只读查询约束，不是用户权限体系。

## 当前边界

- 直接读取现有 MySQL 源系统库，尚未依赖 ODS/DWD 数仓同步任务。
- 支持 Qwen、DeepSeek 两种 OpenAI 兼容工具调用接口。
- 暂无用户级权限控制、跨库 JOIN 语义、会话持久化和问答审计存储。
- 模型 Key 必须由使用方在本机配置；没有有效 Key 时问答接口会返回明确配置提示。
