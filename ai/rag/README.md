# ai/rag/ — 语义层与向量检索

## 技术栈
**pgvector**（PostgreSQL 扩展）· 嵌入模型（千问 `text-embedding-v3` 或本地 BGE 模型）· Python

## 职责
让 AI 理解业务语义。这是原方案 7.1 节"统一指标、质量标签和血缘信息保证模型及智能问答
读取的数据可信、口径一致"的实现。

## 为什么用 pgvector 而不是独立向量库

| 理由 | 说明 |
|---|---|
| 少一个组件 | 演示系统每多一个服务就多一个故障点 |
| 可与元数据 join | 向量检索结果能直接关联指标口径表，无需跨库 |
| 数据量足够 | 业务口径 + SOP + 指标定义不过几千条，pgvector 性能绰绰有余 |

## 放什么代码

| 文件 | 内容 |
|---|---|
| `embedding.py` | 嵌入生成（批处理、缓存、失败重试） |
| `indexer.py` | 建索引：从 dbt manifest 与元数据表读取内容写入向量表 |
| `retriever.py` | 向量检索 + 关键词混合检索（hybrid search） |
| `store.py` | 向量表 CRUD 与索引维护 |
| `sync_job.py` | 增量同步作业（由 Dagster 编排） |

## 索引的内容（四类语料）

| 语料类型 | 来源 | 示例 |
|---|---|---|
| **指标口径** | `transforms/models/metrics/*.yml` | "按期交付率 = 计划完工日期前完成的订单数 / 有效订单数，分母排除已取消订单" |
| **表与字段含义** | `docs/data-dictionary.md` + dbt manifest | "dws_prod_kitting.kitting_rate 表示订单齐套率，由 BOM 需求与可用库存逐层计算" |
| **业务规则与 SOP** | 手工维护 | "缺料影响订单的判定口径""风险订单的处置流程" |
| **血缘摘要** | Dagster 导出 | "ads_order_delay_risk 依赖 dws_prod_delay_features 与 SHAP 归因结果" |

## 检索策略

```
用户问题："齐套率怎么算的"
    ↓
① 向量检索 top-8（语义相似）
② 关键词检索 top-8（术语精确匹配，如"齐套率"）
③ 合并去重 → 重排序 → top-4 送入 LLM
```

**混合检索的必要性**：纯向量检索对"齐套率""MTBF"这类专业术语召回不稳定，
必须叠加关键词精确匹配（PostgreSQL 的 `pg_trgm` + `tsvector` 即可实现）。

## 干什么事情
1. 由 Dagster 的 `sync_glossary_embedding` 资产增量同步，指标口径变更后自动更新
2. 被 MCP 的 `lookup_glossary` 工具调用
3. 被 Agent 的根因分析流程调用（查"这个指标的定义是什么"）
4. 索引内容变更可追溯（记录语料版本与嵌入时间）
