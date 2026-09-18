# ops/sql/ — 运维 SQL 工具集

## 技术栈
PostgreSQL SQL

## 放什么代码

| 文件 | 用途 |
|---|---|
| `inspect_row_counts.sql` | **一键查看四层各表行数**（演示与排障最常用） |
| `inspect_freshness.sql` | 各表数据新鲜度（最近更新时间、延迟） |
| `inspect_dq_summary.sql` | 治理规则通过率汇总 |
| `inspect_issues.sql` | 质量问题明细查询 |
| `inspect_lineage.sql` | 血缘上下游查询 |
| `cleanup_batches.sql` | 清理历史批次数据 |
| `reset_schemas.sql` | 重建四个 schema |
| `profile_table.sql` | 单表画像：行数、字段空值率、枚举值分布 |

## 干什么事情
1. **演示时会频繁使用** `inspect_row_counts.sql`——现场展示"数据确实从源系统流到了 ADS"
2. 为后端 `/tables/{table}/profile` 接口提供 SQL 参考实现
3. 排障时快速定位是哪一层的行数异常
4. 所有脚本带注释说明输出含义，非技术人员也能看懂
