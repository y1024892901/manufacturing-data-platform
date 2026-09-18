# orchestration/sensors/ — 感知器

## 技术栈
Dagster Sensor · 轮询与事件触发

## 放什么代码

| 传感器 | 触发条件 | 动作 |
|---|---|---|
| `source_change_sensor` | 源系统出现新数据（查 `updated_at` 最大值变化） | 触发增量采集 |
| `dq_failure_sensor` | dbt 测试出现 `error` 级失败 | 阻断下游 + 告警 + 写问题清单 |
| `schema_change_sensor` | 源表结构变更（新增/删除列） | 标记待处理，管理后台提示确认 |
| `sla_sensor` | 数据延迟超过阈值 | 告警并记录 SLA 违规 |
| `disposition_sensor` | 新增处置反馈记录 | 触发反馈回流作业 |

## 干什么事情
1. 让链路从"定时跑"升级为"按需跑"，演示时数据更新后立即看到结果
2. `dq_failure_sensor` 是治理闭环的关键——**测试失败不只是报警，而是真的挡住下游**
3. `schema_change_sensor` 支撑"表结构会变"的现实场景，避免源系统加字段就全链路崩溃
