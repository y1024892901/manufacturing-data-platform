# ai/mcp_server/tools/ — MCP 工具实现

## 技术栈
Python · FastMCP `@mcp.tool()` 装饰器 · Pydantic 入参模型 · SQLAlchemy（只读）

## 放什么代码

| 文件 | 工具 | 实现要点 |
|---|---|---|
| `list_datasets.py` | 数据集清单 | 读元数据表，按域分组，附口径摘要 |
| `describe_table.py` | 表结构描述 | 从 dbt manifest + 元数据表合并字段业务含义 |
| `query_metric.py` | 指标查询 | 只查 `metric_*` 表，动态拼维度与过滤，**禁止自由 SQL** |
| `run_sql.py` | 受限 SQL | 语句类型检查 → 表白名单 → 追加 LIMIT → 超时 → 审计 |
| `lookup_glossary.py` | 口径检索 | 调 `ai/rag/retriever`，混合检索后返回 |
| `get_lineage.py` | 血缘查询 | 读 `ads_lineage_snapshot`，支持上下游与列级 |
| `get_order_risk.py` | 订单风险 | 查 `ads_order_delay_risk`，含归因与证据 |
| `get_dq_status.py` | 质量状态 | 查 `ads_dq_rule_pass_rate` |
| `get_dq_issue.py` | 问题明细 | 查 `ads_dq_issue`，支持按规则下钻 |

## 统一的工具返回格式（强制约定）

所有工具返回**三段式**结构，让 AI 的回答天然带依据：

```json
{
  "data": [{"month": "2026-08", "on_time_rate": 0.873}],
  "definition": "按期交付率 = 计划完工日期前完成的订单数 / 有效订单数（排除已取消）",
  "source": {
    "table": "metric_order_delivery",
    "lineage": ["dws_prod_order_progress", "fct_prod_order", "ods_erp_prod_order"],
    "data_updated_at": "2026-09-17T02:15:33"
  },
  "row_count": 1,
  "truncated": false
}
```

**为什么强制三段式**：AI 回答时会自然引用 `definition` 与 `source`，
使输出从"我查到的数字是 87.3%"升级为"按期交付率 87.3%（口径：排除已取消订单，
数据来自 metric_order_delivery，更新于今日 2:15）"。**这是演示可信度的核心机制。**

## 参数校验规范

每个工具用 Pydantic 模型严格校验入参：

| 校验项 | 示例 |
|---|---|
| 枚举收敛 | `domain` 只能是 `finance` / `production` / `equipment` |
| 表名白名单 | `table` 必须在注册表中存在 |
| 维度白名单 | 聚合维度只能取自指标定义的允许维度 |
| 日期格式 | 强制 ISO 格式，拒绝自由文本 |
| 长度限制 | SQL 长度 ≤ 5000 字符 |

## 干什么事情
1. 每个工具独立可测（不依赖 LLM），便于回归测试（`tests/integration/test_mcp_tools.py`）
2. 错误返回结构化信息（错误码 + 人话说明 + 建议），让 AI 能自我纠正
3. 工具描述（docstring）会被 MCP 协议暴露给 LLM，**必须写清楚"什么时候用我"**
