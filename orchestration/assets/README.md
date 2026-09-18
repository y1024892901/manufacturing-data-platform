# orchestration/assets/ — 资产定义

## 技术栈
Dagster `@asset` 装饰器 · dagster-dbt · Python

## 放什么代码

| 文件 | 资产组 | 定义内容 |
|---|---|---|
| `ingest.py` | 采集 | 9 类系统的源表资产，每个源表一个资产 |
| `transform.py` | 建模 | 通过 `@dbt_assets` 把 dbt 全部模型注册为资产（自动获得四层血缘） |
| `quality.py` | 治理 | `dbt_test` 资产、`dq_report` 汇总资产 |
| `features.py` | 特征 | `dws_prod_delay_features` 及其前置依赖 |
| `ml.py` | 模型 | `train_delay_model`、`predict_delay_risk`、`score_finance`、`score_equipment` |
| `semantic.py` | 语义 | `sync_glossary_embedding`（把业务口径写入 pgvector） |
| `lineage.py` | 血缘 | `export_lineage`（导出到 `ads_lineage_snapshot`） |
| `feedback.py` | 闭环 | `ingest_disposition`（处置反馈回流为训练样本） |

## 资产定义示例

```python
@asset(
    group_name="production",
    deps=[AssetKey("fct_prod_order"), AssetKey("fct_inventory")],
    description="生产订单齐套率特征表，一行一单",
)
def dws_prod_kitting(context, postgres: PostgresResource) -> MaterializeResult:
    rows = postgres.run_dbt("dws_prod_kitting")
    return MaterializeResult(metadata={"rows": rows, "grain": "order x material"})
```

## 干什么事情
1. 每个资产返回 `MaterializeResult` 并带 **metadata**（行数、耗时、粒度、负责人），
   这些信息在 Dagster UI 可见，也是血缘报告与 MCP `get_lineage` 的数据源
2. 资产分组与三个业务域对齐，UI 上可按域查看
3. 资产的所有 SQL 逻辑**写在 dbt 里**，Dagster 只负责编排与元数据——避免逻辑分散在两处
