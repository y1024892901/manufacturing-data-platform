# models/ — 机器学习与规则评分

## 技术栈
Python 3.11 · **LightGBM** · **SHAP** · scikit-learn · pandas · numpy

## 职责
产出三类可解释的风险评分，并回写 ADS 层。

> **相对原方案的关键调整**：原方案用 sklearn 在几十行人造数据上训练模型，输出不可解释的概率。
> 调整后：LightGBM 保证效果，**SHAP 保证可解释**——演示时能明确回答"**为什么**这单有风险"。

## 放什么代码 / 文件

```
models/
├── features/         特征定义与特征表校验
├── training/         模型训练与评估
├── inference/        批量评分与写回 ADS
├── explain/          SHAP 归因与自然语言解释生成
├── rules/            规则评分（财务逾期、设备健康）
└── registry/         模型版本登记（模型文件 + 元数据）
```

## 三类评分

| 评分 | 实现方式 | 为什么这样做 |
|---|---|---|
| 生产订单延期风险 | **LightGBM 二分类 + SHAP** | 特征间有交互，模型效果更好；SHAP 给出单样本归因 |
| 财务逾期评分 | **规则评分（SQL）** | 账龄规则清晰，模型反而降低可信度；规则可被 dbt test 覆盖 |
| 设备健康评分 | **规则评分（SQL）** | 同上，且规则更易向设备部门解释 |

> 规则评分**故意不用机器学习**——对业务方来说"账龄 90 天以上扣 30 分"比"模型输出 0.73"
> 更容易被接受，也更容易被验证。这是有意为之的设计选择，不是技术能力不足。

## 输出到 ADS 的三张结果表

| 表 | 一行 = 什么 | 关键字段 |
|---|---|---|
| `ads_order_delay_risk` | 一个生产订单 | `delay_prob`、`risk_level`、`predicted_delay_days`、`top_reason_1/2/3`、`suggestion` |
| `ads_fin_overdue_score` | 一笔应收 | `overdue_level`、`score`、`risk_factors` |
| `ads_equip_health_score` | 一台设备 | `health_level`、`score`、`abnormal_factors` |

## 特征工程原则

**所有特征在 dbt 的 `dws_prod_delay_features` 中计算，本目录只做校验和消费。**

理由：训练与推理用同一套 SQL 口径，从物理上杜绝"离线在线特征不一致"这一经典生产事故。

## 干什么事情
1. `train.py` 训练并产出模型文件 + 评估报告（AUC、PR 曲线、特征重要性）
2. `predict.py` 批量评分写回 `ads_order_delay_risk`
3. `explain.py` 用 SHAP 生成每个订单的 Top3 原因，并翻译成中文自然语言
4. 训练数据、模型文件、评估指标全部落盘带版本，保证**结果可复现**
