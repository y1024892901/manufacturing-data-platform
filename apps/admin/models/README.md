# apps/admin/models/ — 元数据模型

## 技术栈
SQLModel（SQLAlchemy + Pydantic 融合）· PostgreSQL

## 放什么代码

| 文件 | 模型 | 说明 |
|---|---|---|
| `system.py` | `SourceSystem` | 来源系统 |
| `table.py` | `SourceTable` | 源表登记 |
| `column.py` | `SourceColumn` | 字段登记（**含中文业务含义**） |
| `sync_task.py` | `SyncTask` | 同步任务与运行历史 |
| `rule.py` | `GovernanceRule` | 治理规则 |
| `metric.py` | `MetricDef` | 指标口径 |
| `audit.py` | `AuditLog` | 操作审计 |
| `user.py` | `User` / `Role` | 用户与权限 |

## 为什么用 SQLModel 而不是纯 SQLAlchemy

| 理由 | 说明 |
|---|---|
| 一份定义两用 | 同一个类既做数据库表，又做 API 的请求/响应模型，避免双份维护 |
| 类型安全 | 字段类型与业务含义在 IDE 中可提示 |
| Pydantic v2 集成 | 天然接入 FastAPI 的校验与文档生成 |

## `SourceColumn` 模型是数据字典的根基

```python
class SourceColumn(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    table_id: int = Field(foreign_key="source_table.id")
    src_name: str           # 源字段名，如 ORDER_NO
    dst_name: str           # 目标字段名，如 prod_order_no
    business_name: str      # ★ 中文业务含义：生产订单号
    data_type: str          # 类型
    is_primary_key: bool    # 是否主键（增量同步依据）
    is_sensitive: bool      # 是否敏感（返回时脱敏）
    in_scope: bool          # 是否纳入分析范围
    notes: str | None       # 备注：枚举值含义、取值范围等
```

**`business_name` 是整条链路的语义来源**：
① 自动生成数据字典；② 供 AI 生成 dbt 模型初稿；③ 写入向量库供 AI 理解字段含义。

## 干什么事情
1. 作为元数据驱动的唯一数据来源
2. 提供 Alembic 迁移脚本，模型变更可版本化
3. 所有模型的变更自动写审计日志
