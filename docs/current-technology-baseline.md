# 当前技术实施基线

本文件优先于项目中任何早期的 PostgreSQL、pgvector、dbt-postgres、Docker 全栈描述；旧文档保留仅作为历史方案参考，后续逐步清理。

| 领域 | 当前实际选型                              | 已验证状态 |
|---|-------------------------------------|---|
| 数据库 | MySQL 8.0.43，本机 `D:\mysql8`，端口 3306 | 已运行；Java 服务健康检查已连接 |
| 业务系统 | Java 17 + Spring Boot 3.2，多模块、单进程   | 已运行于 8080 |
| 权限 | Spring Security + JWT + MySQL RBAC  | 36 账号、36 角色、325 权限映射已验证 |
| 审批 | 自研审批引擎                              | BOM 串行三级审批及 11 项边界测试已验证 |
| 主数据 | Java MDM + MySQL                    | 物料草稿、审批、发布、分发已验证 |
| 前端 | vue + TypeScript（待实施）               | 未开始 |
| 采集/数仓 | Python + MySQL SQL 转换任务（待实施）        | 未开始 |
| 编排 | Dagster（后期接入）                       | 未开始 |
| AI | DeepSeek/千问 + MCP（ADS 建成后接入）        | 未开始 |

## 不采用的当前实现

- 不启用 Hadoop、Hive、Spark、DataX、DolphinScheduler；
- 不使用 PostgreSQL、pgvector 或 `dbt-postgres` 作为当前项目运行依赖；
- 不把 `DemoController` 生成的虚拟业务单据作为最终业务能力。

## 运行边界

- MySQL 使用本机实例；密码只存于被 Git 忽略的 `infra/.env`；
- Java 服务读取环境变量，不在源代码或 YAML 中写真实数据库密码；
- 所有演示账号采用独立测试密码，生产账号与演示账号隔离。
