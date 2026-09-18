# orchestration/ — 工作流编排

## 技术栈
**Dagster 1.7+**（替代原方案的 DolphinScheduler）· Python · dagster-dbt 集成

## 职责
编排全链路：采集 → 建模 → 治理 → 特征 → 训练 → 服务化 → 语义同步。并**自动产出数据血缘**。

## 为什么换成 Dagster（关键决策）

| 维度 | DolphinScheduler | Dagster |
|---|---|---|
| 编排单元 | 任务（Task） | **资产（Asset）** |
| 血缘 | 需另建元数据中心 | **自动生成，可查询可可视化** |
| 与 dbt 集成 | 自己解析 manifest | 官方集成，模型自动注册为资产 |
| 本地开发 | 依赖 ZooKeeper 等 | 单进程可跑 |
| 演示价值 | 一个任务列表 | **一张会自动生长的数据血缘图** |

**决定性理由**：原方案 7.1 节承诺"血缘信息"，用 DolphinScheduler 需要另建元数据中心；
用 Dagster，血缘是编排的**副产品**，零额外成本。MCP 的 `get_lineage` 工具直接读它。

## 放什么代码 / 文件

```
orchestration/
├── assets/       资产定义（每个数据产物是一个资产）
├── jobs/         作业定义（分域、全链路、单表重跑）
├── schedules/    定时调度（日批、小时批、手动触发）
├── sensors/      感知器（源表变更检测、dbt 测试失败触发）
├── resources/    资源（数据库连接、dbt CLI、LLM 客户端）
└── lineage/      血缘导出（写入 ads_lineage_snapshot 供 MCP 查询）
```

## 资产图（与数据流向一一对应）

```
ingest_sources
  └─→ ods_* ──→ stg_* ──→ dim_* / fct_* ──→ dws_* ──→ metric_*
                     │                          │
                     └─→ dbt_test ──────────────┤
                                                ├─→ ads_* ──→ 报表
                                                ├─→ train_delay_model ──→ ads_order_delay_risk
                                                ├─→ score_rules ──→ ads_fin_overdue_score / equip_health_score
                                                ├─→ dq_report ──→ ads_dq_issue
                                                └─→ sync_glossary ──→ pgvector ──→ MCP
```

## 干什么事情
1. 定义资产间的依赖，Dagster 自动解析执行顺序与并行度
2. 任一资产失败 → 下游自动阻塞，上游可单独重跑（**精确重跑，不重跑全链路**）
3. 每次运行记录：开始/结束时间、耗时、产出物、行数、状态、错误堆栈
4. 血缘图实时更新，可直接用于演示
5. 支持"手动触发单表重跑"，演示时现场展示故障恢复
