# orchestration/jobs/ — 作业定义

## 技术栈
Dagster `define_asset_job` · 资产选择器（`AssetSelection`）

## 放什么代码

| 作业 | 选择的资产 | 用途 |
|---|---|---|
| `full_pipeline_job` | `*` | 全链路重跑，演示与验收用 |
| `daily_batch_job` | 采集 + 全部建模 + 治理 | 每日定时批 |
| `hourly_ingest_job` | `ingest_sources` | 小时级增量采集 |
| `finance_domain_job` | 财务域全链路 | 按域独立跑 |
| `production_domain_job` | 生产域全链路 | 按域独立跑 |
| `equipment_domain_job` | 设备域全链路 | 按域独立跑 |
| `ml_refresh_job` | 特征 → 训练 → 预测 → 评分 | 模型刷新 |
| `single_table_rerun_job` | 参数化单表 | **故障恢复演示** |
| `demo_reset_job` | 清空 + 重新生成 + 全链路 | 演示前重置到干净状态 |

## 干什么事情
1. 域名作业支持**并行执行**（三个域互不依赖），演示时展示并行加速
2. `single_table_rerun_job` 接受表名参数，只重跑受影响链路——这是"可恢复、可重复"验收要求的实现
3. `demo_reset_job` 保证演示可反复重来，不怕现场搞乱数据
