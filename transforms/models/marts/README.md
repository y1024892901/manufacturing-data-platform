# transforms/models/marts/ — 主题宽表层（DWS）

## 技术栈
dbt · PostgreSQL 窗口函数与聚合

## 职责
围绕**财务、生产、设备**三个业务域，把 DWD 明细聚合成可直接分析的宽表。

## 放什么代码

### 财务域

| 模型 | 粒度 | 回答的问题 |
|---|---|---|
| `dws_fin_receivable_snapshot` | 客户 × 订单 × 日 | 应收状态、账龄、回款及时性 |
| `dws_fin_order_recon` | 一个订单 | 订单金额与财务金额是否对得上（规则 F05） |

### 生产域

| 模型 | 粒度 | 回答的问题 |
|---|---|---|
| `dws_prod_order_progress` | 一个生产订单 | 完工率、工序进度、预计完成日期 |
| `dws_prod_kitting` | 订单 × 物料 | 齐套率、缺口、预计到料（规则 P05） |
| `dws_prod_operation_progress` | 订单 × 工序 | 各工序完成情况 |
| `dws_prod_delay_features` | **一个生产订单（宽表）** | **★ 模型特征表** |

### 设备域

| 模型 | 粒度 | 回答的问题 |
|---|---|---|
| `dws_equip_daily_availability` | 设备 × 日 | 可用率、停机时长、状态时段冲突（规则 E02） |
| `dws_equip_fault_summary` | 设备 | 故障次数、MTBF、维修间隔、点检漏检 |
| `dws_equip_energy_baseline` | 设备 × 日 | 能耗基线偏离、异常评分（规则 E05） |

## ★ dws_prod_delay_features —— 全项目最关键的一张表

下游延期风险模型的**唯一输入**。一行一个生产订单，特征必须齐备：

| 特征 | 来源 | 业务含义 |
|---|---|---|
| `completion_rate` | 订单进度 | 已完成数量 / 计划数量 |
| `kitting_rate` | 齐套分析 | 已齐套物料数 / 应齐套物料数 |
| `arrival_deviation_days` | 采购到货 | 关键物料到货平均延迟天数 |
| `downtime_hours` | 设备停机 | 该订单关联工序的累计停机时长 |
| `defect_rate` | 质量检验 | 不良数 / 检验数 |
| `remaining_days` | 交期计算 | 距计划完工日期的剩余天数 |
| `order_qty` | 生产订单 | 计划数量（规模特征） |
| `priority_level` | 生产订单 | 优先级 |

> 特征工程全部在 SQL 中完成，**不在 Python 里做**。理由：保证训练与推理用同一套口径，
> 从物理上杜绝"离线在线特征不一致"这一经典生产事故。

## 干什么事情
1. 物化为表，每个模型带 `dbt test` 校验业务合理性（如 `completion_rate` 必须在 0~1）
2. 所有指标的计算口径在此层固化，ADS 与 AI 都从这里取
3. 每个模型在 `schema.yml` 中写明业务定义，`dbt docs` 自动生成口径说明页
