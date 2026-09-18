# orchestration/resources/ — Dagster 资源配置

## 技术栈
Dagster Resource · Pydantic 配置模型

## 放什么代码

| 文件 | 资源 | 配置内容 |
|---|---|---|
| `postgres.py` | 数仓连接 | 连接串、连接池、statement timeout |
| `dbt_resource.py` | dbt CLI 封装 | profiles 路径、target、选择器、超时 |
| `llm_client.py` | LLM 客户端 | DeepSeek / 千问 的 base_url、key、模型名、超时、重试 |
| `duckdb_resource.py` | DuckDB（可选） | ADS 导出加速用 |
| `notifier.py` | 告警 | 企业微信 / 钉钉 Webhook |

## 干什么事情
1. 资源统一从环境变量读配置，不硬编码连接串
2. `llm_client` 做**限流与重试**，避免演示时因 API 抖动失败
3. `notifier` 在资产失败时推送告警，管理后台同步标红
4. 每个资源带健康检查方法，Dagster UI 可查看连通性
