# ops/monitoring/ — 监控与告警

## 技术栈
Prometheus（指标采集）· Grafana（可视化）· 可选 Loki（日志聚合）· Python Exporter

## 放什么代码

| 文件 | 内容 |
|---|---|
| `prometheus.yml` | 抓取配置（API、Dagster、PostgreSQL Exporter） |
| `exporters/` | 自定义 Exporter：数据新鲜度、质量通过率、行数波动 |
| `grafana/` | 预置看板 JSON（导入即用） |
| `alerts.yml` | 告警规则 |
| `notifier.py` | 告警推送（企业微信 / 钉钉 Webhook） |

## 监控指标

| 类别 | 指标 | 告警阈值 |
|---|---|---|
| 服务 | 容器存活、API 响应时间、错误率 | 错误率 > 5% |
| 数据 | 各层行数、数据新鲜度、批次成功率 | 新鲜度 > 24h |
| 质量 | 规则通过率、error 级失败数 | 通过率 < 90% 或出现 error 失败 |
| 模型 | 预测覆盖率、风险分布漂移 | 覆盖率 < 95% |
| AI | LLM 调用量、延迟、失败率、Token 成本 | 失败率 > 10% |

## 干什么事情
1. **演示价值**：展示"这套系统是按生产级标准建的"，而不只是一个 Demo
2. 数据新鲜度与质量通过率两个指标最能体现治理价值，放在 Grafana 首屏
3. 告警可推到企业微信，演示时现场发一条测试告警
4. **可选部署**：演示机资源紧张时，可只起 Grafana 看预置数据，不起完整 Prometheus
