# models/inference/ — 批量推理与评分回写

## 技术栈
Python · LightGBM · pandas · PostgreSQL（写回 ADS）

## 放什么代码

| 文件 | 内容 |
|---|---|
| `predict_delay.py` | 加载模型 → 读取特征 → 批量预测 → 写回 ADS |
| `score_finance.py` | 财务逾期规则评分（调用 SQL 规则） |
| `score_equipment.py` | 设备健康规则评分 |
| `writer.py` | 结果写回 ADS，带批次与模型版本 |

## 结果表结构要点

`ads_order_delay_risk` 的每一行必须**同时包含**：

| 类别 | 字段 | 用途 |
|---|---|---|
| 预测值 | `delay_prob`、`risk_level`、`predicted_delay_days` | 报表展示 |
| 归因 | `top_reason_1/2/3` + 各自贡献度 | 回答"为什么" |
| 建议 | `suggestion` | 回答"怎么办" |
| 证据 | `evidence_json` | 可下钻到具体缺料/停机/质量记录 |
| 元数据 | `model_version`、`scored_at` | 可追溯到哪次评分 |

## 干什么事情
1. 推理**只读 `dws_prod_delay_features`**，与训练同源，保证口径一致
2. 写回时先删同批次旧数据再插入（幂等），保证重复运行结果一致
3. 每行结果带 `evidence_json`，让报表可以"点开看证据"——这是演示可信度的关键
