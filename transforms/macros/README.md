# transforms/macros/ — dbt 复用宏

## 技术栈
Jinja2 宏 · dbt_utils 扩展

## 放什么代码

| 宏文件 | 用途 |
|---|---|
| `normalize_code.sql` | 编码归一：去空格、统一大小写、前缀剥离 |
| `safe_cast.sql` | 安全类型转换：转换失败返回 NULL 而非报错 |
| `date_range_spine.sql` | 生成连续日期序列，补齐无数据的日期 |
| `flag_invalid.sql` | 统一的异常标记逻辑（`is_valid` + `invalid_reason`） |
| `surrogate_key.sql` | 代理键生成（封装 dbt_utils 并加项目约定） |
| `interval_overlap.sql` | 时间区间重叠检测（设备状态规则 E02 复用） |
| `bom_explode.sql` | BOM 多层展开递归 CTE |
| `audit_columns.sql` | 统一注入/透传审计列 |

## 干什么事情
1. 把重复 3 次以上的 SQL 片段抽成宏，保证同一规则在所有表上行为一致
2. `interval_overlap` 和 `bom_explode` 是本项目的两个**硬骨头**，必须宏化并单测
   （`tests/unit/test_bom_explode.py` 与 `test_interval_overlap.py`）
3. 每个宏在文件头注释写明参数、返回值、使用示例，`dbt docs` 会展示
