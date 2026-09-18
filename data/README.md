# data/ — 运行时数据目录

> **本目录内容不进 Git**（`.gitignore` 排除），只保留目录结构与 `samples/` 示例文件。

## 技术栈
文件系统 · Parquet（列存快照）· CSV（导出物）

## 放什么代码 / 文件

```
data/
├── snapshots/    数据库快照（ops/backup 产出）
├── exports/      导出物：报表 CSV、分析报告、截图数据
├── parquet/      ADS 大表的 Parquet 快照（供 DuckDB 快速查询）
├── samples/      小样本数据（进 Git，用于测试与文档示例）
└── tmp/          临时文件（可随时清空）
```

## 各子目录说明

| 目录 | 内容 | 是否进 Git |
|---|---|---|
| `snapshots/` | 数据库快照，用于快速恢复演示状态 | 否 |
| `exports/` | 报表导出的 CSV、AI 分析报告、演示材料数据 | 部分（精选成果进 Git） |
| `parquet/` | ADS 表导出为 Parquet，供 DuckDB 或外部分析 | 否 |
| `samples/` | 每张表 5~10 行的样本数据，作为文档示例与测试夹具 | **是** |
| `tmp/` | 临时文件，可随时清空 | 否 |

## 干什么事情
1. 把运行时产出的文件与代码彻底分离，避免仓库膨胀
2. `samples/` 是唯一进 Git 的数据——用于单元测试、文档示例、数据字典配图
3. `parquet/` 为"可选 DuckDB 加速"预留：ADS 大表导出后前端查询可走 DuckDB
4. 提供 `data/.gitignore` 明确排除规则
