# ingestion/connectors/ — 源系统连接器

## 技术栈
Python · SQLAlchemy / psycopg2 · pydantic（连接配置模型）· tenacity（重试）

## 放什么代码

| 文件 | 内容 |
|---|---|
| `base.py` | 连接器抽象基类：`connect()` / `extract()` / `test_connection()` |
| `postgres_source.py` | PostgreSQL 源（本项目主力，模拟 9 类系统） |
| `mysql_source.py` | MySQL 源（预留，真实企业环境常见） |
| `excel_source.py` | Excel/CSV 源（**预留：真实客户常以 Excel 交付数据**） |
| `api_source.py` | REST API 源（预留：对接真实 SaaS 系统） |
| `registry.py` | 连接器注册表，按元数据中的 `system_type` 自动选择 |

## 干什么事情
1. **预留多种源类型**：演示用 PostgreSQL，但接口设计支持 MySQL/Excel/API，便于后续换成真实数据源
2. 提供 `test_connection()` 供管理后台的「测试连接」按钮调用
3. 连接信息从 `platform_meta` 元数据读取，**不在代码里硬编码任何连接串**
4. 敏感信息（密码）从环境变量或加密字段读取，不落明文
