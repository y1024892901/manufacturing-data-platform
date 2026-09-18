# tests/integration/ — 集成测试

## 技术栈
pytest · httpx（API）· psycopg2（数据库校验）· pytest-asyncio

## 放什么代码

| 文件 | 测什么 |
|---|---|
| `test_ingestion.py` | 采集链路：增量正确性、幂等性、审计列完整、失败重跑 |
| `test_dbt_models.py` | dbt 建模：四层依赖正确、模型可编译、关键模型行数符合预期 |
| `test_dq_rules.py` | **治理规则：每条规则都能正确捕获对应的坏数据** |
| `test_mcp_tools.py` | MCP 九个工具：可调用、返回格式、参数校验、权限拦截 |
| `test_api_endpoints.py` | API 契约：状态码、响应结构、分页、导出 |
| `test_admin_crud.py` | 管理后台：元数据 CRUD、变更审计、配置生效 |
| `test_metric_consistency.py` | 报表 / API / MCP 三处取到的同一指标值完全一致 |

## 重点测试设计

### `test_dq_rules.py` —— 治理有效性的证明

```python
def test_P03_date_order(seeded_warehouse):
    """P03 日期顺序规则应捕获所有注入的日期倒置记录"""
    injected = load_injection_manifest("P03")   # 从注入清单读取
    detected = query_dq_issues("P03")           # 从治理结果读取
    assert set(injected.keys()) == set(detected.keys())   # 完全一致
```

**这个测试是"治理不是摆设"的硬证据**，也是演示时可以现场展示的内容。

### `test_mcp_tools.py` —— 安全边界验证

| 断言 | 验证 |
|---|---|
| `run_sql("DELETE FROM ads_dq_issue")` 被拒绝 | 写操作拦截 |
| `run_sql("SELECT * FROM platform_meta.sys_user")` 被拒绝 | 表白名单 |
| `run_sql` 结果行数 ≤ 1000 | LIMIT 强制 |
| 每次调用在 `mcp_call_log` 有记录 | 审计完整 |

## 干什么事情
1. 每个测试用独立的测试 schema，跑完即清理，不污染演示数据
2. 需要真实数据库的测试标记为 `@pytest.mark.integration`，可单独运行
3. 失败时输出诊断信息（实际值 vs 期望值 + 相关 SQL），便于快速定位
