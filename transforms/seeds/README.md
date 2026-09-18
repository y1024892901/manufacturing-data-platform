# transforms/seeds/ — 静态字典表

## 技术栈
dbt seed（CSV 加载）

## 放什么代码

| 文件 | 内容 |
|---|---|
| `dim_status_mapping.csv` | 各系统状态码 → 统一状态语义 |
| `dim_unit.csv` | 计量单位字典 |
| `dim_business_calendar.csv` | 工作日/节假日/生产日历 |
| `dim_risk_level.csv` | 风险等级阈值定义 |
| `dim_glossary_seed.csv` | 业务术语初始词条（供向量库初始化） |

## 干什么事情
1. 这些是**人工维护的小字典**，不是源系统数据，用 seed 管理最简单
2. 版本化：字典变更走代码评审，保证口径变更可追溯
3. `dim_glossary_seed.csv` 是 AI 语义层的初始语料，`ai/rag` 会读取它建立向量索引
