# orchestration/schedules/ — 调度定义

## 技术栈
Dagster Schedule · Cron 表达式

## 放什么代码

| 调度 | Cron | 作业 | 说明 |
|---|---|---|---|
| `daily_batch_schedule` | `0 2 * * *` | `daily_batch_job` | 每日凌晨 2 点全量批 |
| `hourly_ingest_schedule` | `15 * * * *` | `hourly_ingest_job` | 每小时 15 分增量采集 |
| `ml_refresh_schedule` | `0 4 * * 1` | `ml_refresh_job` | 每周一凌晨刷新模型 |
| `dq_daily_schedule` | `30 2 * * *` | `dq_report` | 每日质量报告 |

> 分钟位避开 `:00` 整点，减少资源争抢。

## 干什么事情
1. **演示系统默认关闭自动调度**（`infra/configs/dagster.yaml` 里可开关），避免演示时后台任务干扰
2. 演示"调度能力"时改为手动触发，效果更可控
3. 所有调度启动时间、执行结果写入运行日志，管理后台可查
