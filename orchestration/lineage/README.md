# orchestration/lineage/ — 血缘导出与图构建

## 技术栈
Dagster Asset Graph API · Python · PostgreSQL · dbt manifest（列级血缘）

## 职责
把 Dagster 自动生成的资产血缘导出为**可查询、可展示、可被 AI 调用**的形式。

## 放什么代码

| 文件 | 内容 |
|---|---|
| `exporter.py` | 从 Dagster 读取资产图，导出节点与边 |
| `graph_builder.py` | 构建上下游闭包（给定表名，返回全链路） |
| `impact_analyzer.py` | **影响面分析**：某表变更影响哪些下游产物 |
| `writer.py` | 写入 `ads_lineage_snapshot` 供 MCP 与前端读取 |

## 血缘数据的三种消费方式

| 消费者 | 用途 |
|---|---|
| 自研报表前端 | 可视化血缘图，点击节点下钻 |
| MCP `get_lineage` 工具 | AI 回答"这个指标的数据从哪来" |
| `impact_analyzer` | 变更影响面评估（改一个源表会波及什么） |

## 血缘节点携带的元数据

| 字段 | 说明 |
|---|---|
| `node_id` | 资产唯一标识（如 `dws_prod_kitting`） |
| `node_type` | ods / stg / dim / fct / dws / metric / ads / model |
| `domain` | 财务 / 生产 / 设备 |
| `owner` | 负责人 |
| `row_count` | 最近一次产出行数 |
| `last_materialized` | 最近成功时间 |
| `description` | 业务含义 |

## 干什么事情
1. 每次全链路运行后自动刷新血缘快照
2. 提供**列级血缘**（dbt manifest 含列级依赖信息），演示"这个字段是怎么算出来的"
3. 演示高光点：从报表一个指标出发，一路向上追溯到源系统字段
