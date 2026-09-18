# transforms/snapshots/ — 缓慢变化维（SCD2）

## 技术栈
dbt snapshot（`timestamp` 策略）

## 放什么代码

| 文件 | 维度 | 为什么需要 SCD2 |
|---|---|---|
| `dim_customer_snapshot.sql` | 客户 | 客户的销售区域/信用等级变更需保留历史，否则历史订单分析失真 |
| `dim_equipment_snapshot.sql` | 设备 | 设备所属车间/产线变更需保留历史，用于停机归因 |
| `dim_supplier_snapshot.sql` | 供应商 | 供应商等级变更影响历史到货评价 |
| `dim_material_snapshot.sql` | 物料 | 物料分类变更影响历史齐套分析 |

## 干什么事情
1. 用 `updated_at` 作为 `updated_at` 策略列，`unique_key` 为业务主键
2. 增加 `valid_from` / `valid_to` / `is_current` 三列，事实表 join 时按**业务发生时间**关联
3. 演示"时间点回溯"能力：查看"三个月前这个客户属于哪个区域"
