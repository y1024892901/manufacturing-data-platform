# models/registry/ — 模型版本登记

## 技术栈
文件系统 + JSON 元数据（轻量方案，演示系统不引入完整 MLOps 平台）

## 目录结构

```
registry/
├── delay_risk/
│   ├── 20260917_143022/
│   │   ├── model.txt              LightGBM 模型文件
│   │   ├── metrics.json           AUC / KS / PR-AUC / 分档准确率
│   │   ├── feature_importance.json
│   │   ├── feature_snapshot.json  训练所用特征快照
│   │   ├── shap_summary.json      全局 SHAP 摘要
│   │   └── train_config.json      超参与随机种子
│   └── latest -> 20260917_143022  当前生产版本
└── README.md
```

## 元数据 JSON 结构

| 字段 | 说明 |
|---|---|
| `model_version` | 版本号（时间戳） |
| `trained_at` | 训练时间 |
| `train_rows` / `valid_rows` | 训练/验证样本量 |
| `auc` / `ks` / `pr_auc` | 评估指标 |
| `positive_rate` | 正样本占比 |
| `feature_list` | 使用的特征及版本 |
| `data_range` | 训练数据的起止时间 |
| `random_seed` | 随机种子（**保证可复现**） |
| `previous_version` | 上一版本（便于回滚对比） |

## 干什么事情
1. 保证**结果可复现**（验收要求）：给定种子和特征快照，任何人能重跑出同样的指标
2. `ads_order_delay_risk` 记录 `model_version`，可追溯每条预测是哪版模型给出的
3. 支持版本对比：新模型上线前与旧版本指标并排展示
4. 一键回滚：`latest` 软链接指回旧版本即可
