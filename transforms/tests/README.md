# transforms/tests/ — 数据治理规则

## 技术栈
dbt tests（通用测试 + singular 测试）· dbt_utils · dbt_expectations

## 职责
把原方案的 15 条治理规则落地为**可执行、可阻断、可统计**的测试，并扩展到 40~50 条。

## 规则清单

### 财务域

| 编号 | 规则 | dbt 实现 | 严重级别 |
|---|---|---|---|
| F01 | 凭证号+公司+期间唯一 | `unique_combination_of_columns` | error |
| F02 | 借贷金额平衡 | `expression_is_true: debit = credit` | error |
| F03 | 成本中心/科目编码有效 | `relationships` + `not_null` | error |
| F04 | 应收金额与到期日逻辑合理 | singular test：金额≥0 且 到期日 ≥ 凭证日 | warn |
| F05 | 订单金额与财务金额对账 | singular test：跨层差异比对 | warn |

### 生产域

| 编号 | 规则 | dbt 实现 | 严重级别 |
|---|---|---|---|
| P01 | 生产订单号唯一且关键字段完整 | `unique` + `not_null` | error |
| P02 | 计划量/完工量/合格量关系合理 | `expression_is_true`：完工≤计划，合格≤完工 | error |
| P03 | 计划/开工/完工日期顺序正确 | singular test：日期单调 | error |
| P04 | 工序必须属于有效工艺路线 | `relationships` 到 `brg_routing` | error |
| P05 | BOM 需求/库存/到货口径一致 | singular test：三层口径对齐 | warn |

### 设备域

| 编号 | 规则 | dbt 实现 | 严重级别 |
|---|---|---|---|
| E01 | 设备编码在台账中唯一有效 | `unique` + `relationships` | error |
| E02 | 运行/停机/维修状态时间不重叠 | singular test：区间重叠检测 | error |
| E03 | 故障必须关联设备/工单/维修结果 | `relationships` × 3 | warn |
| E04 | 点检周期与结果完整 | singular test：周期缺口检测 | warn |
| E05 | 能耗非负且异常波动可识别 | `expression_is_true` + 基线偏离 | warn |

### 扩展规则（新增，展示治理深度）

| 领域 | 新增内容 |
|---|---|
| 完整性 | 关键表非空率、主数据覆盖率、外键孤儿率 |
| 一致性 | 跨系统同一实体编码一致、单位统一、币种一致 |
| 时效性 | 数据延迟阈值、批次断档检测 |
| 唯一性 | 各表业务主键唯一、代理键唯一 |
| 值域 | 枚举值合法性、比率在 0~1、金额非负 |
| 跨层对账 | ODS 行数 vs DWD 行数 vs DWS 汇总值的三层核对 |

## 干什么事情
1. `dbt test --store-failures` 执行，失败样本自动落表
2. 通过 `ads_dq_issue` 汇总为**治理看板数据源**：规则通过率、问题明细、治理前后对比
3. 演示时对照「坏数据注入清单」逐条验证命中，证明治理真实有效
4. `error` 级失败**阻断下游**——这是"治理"与"检查脚本"的本质区别
