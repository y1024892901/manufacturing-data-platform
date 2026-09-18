# ai/mcp_server/ — MCP 工具服务

## 技术栈
**FastMCP**（Python MCP SDK）· FastAPI（HTTP/SSE 传输）· Pydantic 校验 · PostgreSQL 只读账号

## 职责
**数仓能力对 AI 的唯一出口。** 所有 AI 访问数据的行为都必须经过这里。

## 核心安全设计（最重要）

```
❌ 绝不这样做：把数据库连接串交给 AI，让它自由生成并执行 SQL
✅ 必须这样做：AI 只能调用受控工具，每个工具都有权限校验 + 参数校验 + 审计日志
```

| 安全机制 | 实现 |
|---|---|
| 只读账号 | MCP 连接数据库用 `mcp_readonly` 账号，只有 `SELECT` 权限 |
| 表白名单 | `run_sql` 只允许查询 `ads` / `dws` schema，禁止触碰 `ods` / `platform_meta` 与用户表 |
| 强制 LIMIT | 所有 SQL 自动追加 `LIMIT 1000`，防止 AI 拉全表拖垮数据库 |
| 语句检查 | 拒绝 `INSERT/UPDATE/DELETE/DROP/CREATE/ALTER/TRUNCATE` 等非查询语句 |
| 超时保护 | `statement_timeout = 30s` |
| 操作审计 | 每次调用写入 `app_meta.mcp_call_log`：时间、工具、参数、返回行数、耗时、会话 ID |
| 敏感字段脱敏 | `dim_customer` 的联系方式等字段在返回前掩码 |

## 九个工具

| 工具 | 入参 | 出参 | 说明 |
|---|---|---|---|
| `list_datasets(domain?)` | 域可选 | 数据集清单 + 口径摘要 | 让 AI 先知道有什么数据 |
| `describe_table(table)` | 表名 | 字段、类型、**业务含义**、主键、粒度 | AI 写 SQL 前的必调项 |
| `query_metric(metric, dims, filters)` | 指标名、维度、过滤 | 指标值 + 口径说明 | **★ 常规问题的唯一入口，口径锁死** |
| `run_sql(sql, purpose)` | SQL、目的说明 | 结果集 | **★ 探索性问题，受限但灵活** |
| `lookup_glossary(term)` | 术语 | 定义 + 相关指标 + 来源 | 向量检索业务口径 |
| `get_lineage(table, direction)` | 表名、方向 | 上下游节点与列级依赖 | 从 Dagster 读取 |
| `get_order_risk(order_no)` | 订单号 | 风险等级 + SHAP 归因 + 证据 | 单订单深度查询 |
| `get_dq_status(domain?, date?)` | 域、日期 | 规则通过率 + 问题明细 | 数据质量状态 |
| `get_dq_issue(rule_id?)` | 规则编号 | 问题明细 + 样例主键 | 治理问题下钻 |

## `query_metric` 与 `run_sql` 的分工（设计精髓）

| 问题类型 | 走哪个工具 | 结果 |
|---|---|---|
| "本月按期交付率多少" | `query_metric` | 口径统一，**AI 算不错** |
| "哪个车间的齐套率最低" | `query_metric` | 同上 |
| "帮我看下 3 月报工记录里最异常的 10 条" | `run_sql` | 灵活探索，但受白名单与 LIMIT 约束 |

## 干什么事情
1. 以 **MCP 协议**对外提供服务，任何支持 MCP 的客户端（Claude Desktop / Claude Code / 自研前端）都能接入
2. 同时提供 HTTP/SSE 接口，供自研报表的「AI 分析」按钮调用
3. 每次调用可审计，**演示时现场展示审计日志**（合规亮点）
4. 工具的返回格式统一为「数据 + 口径说明 + 数据来源」三段式，让 AI 的回答天然带依据
