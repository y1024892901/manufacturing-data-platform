# transforms/ — dbt 数仓建模与治理

## 技术栈
**dbt-core 1.8+ + dbt-postgres**（替代原方案的手工 SQL）· Jinja2 宏 · YAML 配置 · dbt_utils / dbt_expectations

## 职责
全项目的**建模与治理核心**。四层数仓建模（ODS 由采集负责）+ 全部治理规则的执行。

> 为什么必须用 dbt 而不是手写 SQL：
> 1. 每条治理规则成本降到**一行 YAML**（原方案每条规则一个 SQL 脚本）；
> 2. 模型依赖自动解析，天然形成血缘；
> 3. `dbt test` 失败可阻断下游，这是"治理"与"写几个检查脚本"的本质区别；
> 4. `dbt docs generate` 一键生成数据字典网站，直接作为交付物。

## 放什么代码 / 文件

```
transforms/
├── models/
│   ├── staging/      stg_*：标准化
│   ├── warehouse/    dim_* / fct_* / brg_*：维度与事实（DWD）
│   ├── marts/        三业务域主题宽表（DWS）
│   └── metrics/      ★ 统一指标口径层
├── tests/            自定义治理规则（原方案 15 条 → 扩展至 40~50 条）
├── macros/           复用宏（编码归一、日期处理、异常标记、区间重叠）
├── snapshots/        缓慢变化维（SCD2）
├── seeds/            小字典表
└── dbt_project.yml   工程配置
```

## 四层模型命名与职责

| 层 | schema | 命名 | 职责 | 谁读 |
|---|---|---|---|---|
| Staging | `dwd` | `stg_<系统>_<表>` | 编码归一、去重、类型规整、主数据关联 | 仅 warehouse 层 |
| Warehouse | `dwd` | `dim_*` / `fct_*` / `brg_*` | 维度事实建模，分析底座 | marts 层 |
| Marts | `dws` | `dws_<域>_<主题>` | 三域主题宽表 | metrics / ads |
| Metrics | `dws` | `metric_*` | **指标口径唯一出口** | ADS / API / AI |
| Marts(ADS) | `ads` | `ads_*` | 应用直取数据集 | 报表 / AI |

## 治理规则落地方式

原方案的 15 条规则改写为 dbt tests，每条一行 YAML：

```yaml
models:
  - name: fct_fin_voucher
    tests:
      - dbt_utils.unique_combination_of_columns:      # F01 凭证号+公司+期间唯一
          combination_of_columns: [voucher_no, company_code, period]
      - dbt_utils.expression_is_true:                  # F02 借贷平衡
          expression: "debit_amount = credit_amount"
    columns:
      - name: cost_center_code
        tests:
          - relationships:                             # F03 成本中心编码有效
              to: ref('dim_cost_center')
              field: cost_center_code
          - not_null
```

**严重级别**：`error` 级失败阻断下游模型构建；`warn` 级只记录不阻断。
治理结果由 `dbt test --store-failures` 落到审计表，再由 `ads_dq_issue` 汇总成看板数据源。

## 干什么事情
1. `dbt run` 构建四层模型，`dbt test` 执行治理，`dbt docs generate` 产出数据字典
2. 支持 `--select` 按域/按表增量构建，演示时只跑受影响的链路
3. 产出物写入运行日志表，供管理后台展示"最近一次建模结果"
