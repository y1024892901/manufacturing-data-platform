# transforms/models/metrics/ — 统一指标口径层

## 技术栈
dbt + YAML 指标定义（参考 dbt Semantic Layer / MetricFlow 思路，演示系统用轻量自研实现）

## 职责
**指标口径的唯一出口。** 这是原方案 7.1 节"统一指标保证智能问答读取的数据可信、口径一致"
承诺的真实载体——原方案只写了愿望，没有实现机制。

## 为什么需要这一层

没有它会发生什么：

| 角色 | 对"按期率"的理解 | 结果 |
|---|---|---|
| 报表开发 | 完工日期 ≤ 计划日期 的订单数 / 总订单数 | 三个数字互不相同，演示现场自相矛盾 |
| AI 问数 | `status = '已完成'` 的占比 | 同上 |
| 数据分析师 | 排除已取消订单后的按期占比 | 同上 |

有了这一层：**所有角色都只能从 `metric_*` 表取数**，口径物理上无法分歧。

## 放什么代码

| 文件 | 指标 | 口径定义要点 |
|---|---|---|
| `metric_order_delivery.sql` | 按期交付率、完工率、延期订单数 | 分母排除已取消订单；延期判定以计划完工日期当日 23:59:59 为界 |
| `metric_production.sql` | 计划量、完工量、合格率、产能利用率 | 合格量以 QMS 检验记录为准，**非 MES 自报** |
| `metric_inventory.sql` | 齐套率、库存周转、缺料订单数 | 齐套判定须逐层展开 BOM |
| `metric_equipment.sql` | 设备可用率、MTBF、MTTR、能耗单耗 | 可用率排除已报废设备 |
| `metric_finance.sql` | 应收余额、回款率、逾期金额、毛利率 | 以财务凭证为准，非业务单据 |

## 指标定义元数据结构

每个指标在 YAML 中声明，既供 dbt 生成 SQL，也供 MCP 工具读取展示给 AI：

```yaml
metrics:
  - name: on_time_delivery_rate
    label: 按期交付率
    definition: 计划完工日期前完成的订单数 / 有效订单数
    numerator: countif(actual_finish_date <= plan_finish_date)
    denominator: count(*) filter (where status != '已取消')
    grain: [month, factory, workshop, product]
    owner: 计划部
    updated_at: daily
```

## 干什么事情
1. `metric_*` 表是 **ADS 层、报表 API、MCP `query_metric` 工具、Agent 的共同数据源**
2. 指标口径变更必须在 `docs/metrics.md` 登记并通知下游
3. 提供 `dbt test`：指标值域检查（如比率必须在 0~1）、环比波动阈值告警
