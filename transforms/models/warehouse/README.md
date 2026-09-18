# transforms/models/warehouse/ — 维度与事实层（DWD）

## 技术栈
dbt（SQL + Jinja2）· Kimball 维度建模 · dbt_utils 代理键生成

## 放什么代码

### 维度表（dim_）

| 模型 | 说明 | 更新方式 |
|---|---|---|
| `dim_customer` | 客户主数据 | SCD2 快照 |
| `dim_product` | 产品 | 全量覆盖 |
| `dim_material` | 物料 | 全量覆盖 |
| `dim_equipment` | 设备台账 | SCD2 快照 |
| `dim_supplier` | 供应商 | 全量覆盖 |
| `dim_cost_center` | 成本中心 | 全量覆盖 |
| `dim_date` | 日期维度 | 静态生成（10 年） |

### 事实表（fct_）

| 模型 | 粒度（一行 = 什么） | 核心度量 |
|---|---|---|
| `fct_prod_order` | 一个生产订单 | 计划量、完工量、合格量、工期 |
| `fct_work_order` | 一张工单 | 工序进度、状态 |
| `fct_work_report` | 一条报工记录 | 报工数量、工时 |
| `fct_inventory` | 物料 × 库位 × 日 | 库存量、可用量 |
| `fct_purchase_recv` | 一条到货记录 | 到货量、到货日期偏差 |
| `fct_quality_inspect` | 一条检验记录 | 检验数、合格数、不良数 |
| `fct_equip_fault` | 一次设备故障 | 停机时长、维修时长 |
| `fct_equip_inspect` | 一次点检 | 点检结果、是否漏检 |
| `fct_energy` | 设备 × 日 | 能耗量、基线偏离 |

### 桥接表（brg_）

| 模型 | 说明 |
|---|---|
| `brg_bom` | BOM 多层展平，支持向上/向下追溯 |
| `brg_routing` | 产品-工艺-工序关系 |

## 关键设计

- **代理键**：所有维度用 `dbt_utils.generate_surrogate_key` 生成 `*_key`，事实表引用代理键
- **一致性维度**：`dim_date` 被所有事实表共用，保证跨域分析可对齐
- **粒度声明**：每个事实表模型顶部注释明确写出粒度，防止后续误 join 导致数据翻倍
- **坏数据标记而非删除**：不合法记录保留并打上 `is_valid = false` 与 `invalid_reason`，
  供治理看板统计"治理前后变化"——**删除坏数据会让治理看板变成空的**

## 干什么事情
1. 物化为**表**（table），因为下游大量复用
2. 所有 join 使用 `{{ ref() }}`，由 dbt 自动解析依赖顺序
3. 支持按 `updated_at` 增量物化（大表）
