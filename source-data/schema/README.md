# source-data/schema/ — 源系统建表 DDL

## 技术栈
纯 SQL（PostgreSQL 16 方言）

## 放什么代码
按源系统拆分的建表脚本，每个系统一个文件：

```
schema/
├── mdm.sql       主数据表
├── crm.sql       CRM: customer / opportunity
├── plm.sql       PLM: product / bom / routing / operation
├── srm.sql       SRM: supplier / purchase_order / delivery
├── erp.sql       ERP: sales_order / prod_order / voucher / receivable
├── mes.sql       MES: work_order / work_report / prod_result
├── wms.sql       WMS: inventory / stock_txn / location
├── qms.sql       QMS: inspection / defect / rework
├── eam.sql       EAM: equipment / fault / repair / inspection_plan
└── energy.sql    能源: equip_energy / workshop_energy
```

## 设计约定

| 约定 | 说明 |
|---|---|
| 表数量不固定 | 这些是**代表性核心表**，约 25~30 张。新增系统/表由管理后台登记，本目录同步补 DDL |
| 命名 | 源系统用**业务原名风格**（如 `erp_prod_order`），故意保留编码不统一的现象 |
| 字段克制 | 只保留分析所需字段，避免造数负担 |
| 主键 | 每表必须有业务主键，供增量同步使用 |
| 时间戳 | 每表必须有 `updated_at`，作为增量水位线 |

## 干什么事情
1. 作为造数引擎的写入目标结构
2. 作为「数据字典」的物理依据，字段中文含义需与 `docs/data-dictionary.md` 双向一致
3. 支持幂等重建（`DROP ... IF EXISTS` + `CREATE`），便于反复演示
