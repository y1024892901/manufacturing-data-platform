# tests/ — 测试体系

## 技术栈
pytest · pytest-asyncio · httpx（API 测试）· dbt test · Playwright（端到端，可选）

## 职责
保证演示系统**在任何时候跑起来都是对的**。演示系统的测试重点不是覆盖率，
而是**关键路径不会断**。

## 放什么代码 / 文件

```
tests/
├── unit/          单元测试：造数逻辑、规则实现、工具函数
├── integration/   集成测试：采集链路、MCP 工具、API 接口
├── e2e/           端到端：完整链路 + 演示场景
├── fixtures/      测试数据夹具与预期结果
└── conftest.py    共享 fixture（测试库连接、临时 schema）
```

## 测试分层

| 层 | 测什么 | 数量 | 运行时机 |
|---|---|---|---|
| **单元** | 造数约束（BOM 展开、时序）、注入器、归因聚合逻辑 | 多 | 每次提交 |
| **集成** | 采集幂等性、MCP 的 9 个工具、API 接口契约 | 中 | 每次提交 |
| **数据质量** | dbt test（40~50 条治理规则） | 多 | 每次建模后 |
| **端到端** | 从造数到报表数据的完整链路 + 演示场景脚本 | 少 | 演示前 |
| **AI 专项** | MCP 工具可调用、Agent 能完成一次完整根因分析 | 少 | 演示前 |

## 关键测试项（演示保障）

| 测试 | 验证什么 |
|---|---|
| `test_idempotency.py` | 同一批数据采集两次，结果完全一致 |
| `test_bad_data_detected.py` | **每个注入器注入的问题，都被对应规则捕获**（治理有效性的核心证明） |
| `test_metric_consistency.py` | 报表、API、MCP 三处取到的同一指标值完全一致 |
| `test_mcp_tools.py` | 9 个 MCP 工具全部可用且返回格式符合三段式约定 |
| `test_agent_root_cause.py` | Agent 能完成一次完整根因分析（工具调用链正确） |
| `test_api_no_write.py` | 报表接口无法执行写操作 |
| `test_mcp_readonly.py` | MCP 的 `run_sql` 拒绝非查询语句与白名单外的表 |

## 干什么事情
1. `pytest -m "not e2e"` 日常快速回归；`pytest -m e2e` 演示前完整验证
2. `test_bad_data_detected.py` 是**最有说服力的演示材料**：证明治理规则不是摆设
3. AI 相关测试用固定输入 + 结构化断言（不比对自然语言原文，避免 LLM 波动导致测试假失败）
4. 测试库与演示库物理隔离（独立 schema），测试不污染演示数据
