# 制造业生产数据分析平台（演示系统）

> 定位：**纯演示系统**。用于向管理层与技术评审演示「多源数据融合 → 四层数仓 → 数据治理 → AI 归因分析」的完整闭环。
> 后端不部署 Hadoop 生态，全部能力以**可见、可点、可交互**的形式呈现。

> **当前实施基线（2026-09-18）**：十系统不按“单个页面/单个按钮”建设，而按可维护的端到端业务闭环建设；实施顺序、每个系统的最小完整业务范围与验收条件见 [implementation-roadmap.md](docs/implementation-roadmap.md)。

---

## 一、这个系统要回答的四个管理问题

| 管理问题 | 参与系统 | 系统给出的答案 |
|---|---|---|
| 订单能否按期交付 | CRM + ERP + MES + WMS + SRM + EAM + QMS | 延期概率、预计延期天数、主要原因、处置建议 |
| 缺料影响哪些订单 | PLM + WMS + SRM + ERP | 齐套率、缺口清单、预计到料、受影响订单 |
| 设备异常影响多大 | EAM + MES + QMS + 能源 | 停机影响面、设备健康、关联订单 |
| 经营结果如何追溯 | ERP 财务 + 应收 + 成本中心 | 订单收入、成本、回款、差异追踪 |

---

## 二、相对原方案的三处根本性调整

### 调整一：删除 Hadoop 全栈

原方案的三节点 HDFS + Hive + Spark + DataX + DolphinScheduler，为 22 张表服务，
且**在演示现场完全不可见**（你自己也确认了"看不见摸不着，没法展示"）。已全部移除。

**替换为**：**本机 MySQL 8.0.43 单实例多库**（18 个库统一承载源系统 + 数仓 + 平台）
+ `dw_kit` 自研建模框架 + Dagster 资产编排。
省下的 1.5~2 周环境调试时间，全部投入到**后管系统**与 **AI 能力**建设。

> 若汇报话术需要"大数据技术栈"作为概念背书，仅在文档中用一张架构对比图提及，不落地组件。

### 调整二：新增后台管理系统（原方案完全缺失）

原方案只有"看"的页面，没有"管"的地方。本系统新增 **Admin 管理后台**，
这是把演示从"看板展示"升级为"平台产品"的关键。

**核心设计：元数据驱动（Metadata-Driven）**

```
管理后台录入  →  元数据表登记  →  自动生成采集任务  →  自动生成 ODS/STG 模型
   （配置）        （描述）          （执行）              （落地）
```

新增一个业务系统时：

1. 管理员在后台登记「系统名称、连接方式、负责人」
2. 登记「表清单」，逐字段填写 `源字段名 / 类型 / 中文业务含义 / 是否主键 / 是否敏感`
3. 登记「同步策略」「质量规则」「指标口径」
4. 点「同步」→ 采集自动运行 → 数仓模型自动生成 → 质量结果自动呈现 → 血缘图自动更新

> **表数量不固定**由这套机制保证：新增系统只是新增元数据记录，不是新增代码。
>
> 可选增强：调用 LLM 从字段中文名自动生成 `stg_*` 的 dbt 模型初稿（`ai/agents/dbt_generator/`），
> 演示"AI 加速数据开发"。核心链路保持确定性，AI 只做初稿生成，人工确认后入库。

### 调整三：放弃帆软，自研报表

原方案 5 个帆软页面改为自研 React + ECharts 报表系统。

**收益**：
1. 报表与 AI 分析按钮**共用同一套 ADS 数据源**，口径天然一致
2. 可实现「一键 AI 分析」深度联动（帆软难以嵌入流式 Agent 面板）
3. 无 License 依赖，可自由分发给评审方体验

> 注：D 盘已安装 FineReport 11。如需回归帆软做对比演示，`apps/api` 的 `/metrics` 接口
> 可直接作为其 JDBC/HTTP 数据源，无需任何改造。

---

## 三、完整数据流向

```
┌──────────────────────────────────────────────────────────────────────────┐
│ ① 源系统层   source-data/ + 多 database 实例（模拟 9 类业务系统）           │
│   CRM · ERP · MES · WMS · EAM · QMS · SRM · PLM · 能源                    │
│   表数量不固定：由管理后台的元数据登记决定，新增系统不改代码                │
│   ⚠ 造数时故意注入坏数据（重复凭证/日期倒置/无效工序/孤立设备）             │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ ② 采集 ingestion/
                                 │ Python dlt · watermark 增量 · 批次审计列
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ ③ ODS 层（Postgres schema: ods）  原样落地，绝不修改业务值                  │
│   每行附 _batch_id / _src_system / _extract_ts / _row_hash                 │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ ④ 标准化 transforms/ (dbt stg_*)
                                 │ 编码归一 · 去重 · 类型规整 · 主数据关联
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ ⑤ DWD 层（schema: dwd）  维度 + 事实 + 桥接                                 │
│   dim_customer/product/material/equipment/supplier/cost_center/date        │
│   fct_prod_order / work_order / work_report / inventory / purchase_recv    │
│   fct_quality_inspect / equip_fault / equip_inspect / energy               │
│   brg_bom / brg_routing                                                    │
│   ⚙ 治理：dbt test 在此层执行，失败即阻断下游                                │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ ⑥ 主题聚合 (dbt dws_*)
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ ⑦ DWS 层（schema: dws）  三业务域主题宽表 + 公共指标                         │
│   财务域  dws_fin_receivable_snapshot / dws_fin_order_recon                 │
│   生产域  dws_prod_order_progress / dws_prod_kitting                        │
│          dws_prod_operation_progress                                        │
│          dws_prod_delay_features ★模型特征表（一单一行）                     │
│   设备域  dws_equip_daily_availability / fault_summary / energy_baseline     │
│   指标    metric_order_delivery / production / inventory / equipment / finance│
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ ⑧ 服务化 (dbt ads_*)
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ ⑨ ADS 层（schema: ads）  应用直取，不再二次加工                              │
│   报表集 ads_dashboard_exec / order_delay / kitting / equip_quality          │
│   模型集 ads_order_delay_risk / fin_overdue_score / equip_health_score       │
│   治理集 ads_dq_issue / dq_rule_pass_rate / recon_diff                       │
│   语义集 ads_business_glossary →(embedding)→ mfg_app 向量表                   │
│          ads_lineage_snapshot ←(Dagster 导出)                                │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        ▼                        ▼                        ▼
  ┌───────────┐          ┌──────────────┐        ┌──────────────────┐
  │ 自研报表   │          │ FastAPI 网关  │        │  MCP Server      │
  │ 5 个页面   │          │ /metrics      │        │  9 个工具         │
  │ + AI按钮   │          │ /lineage      │        │  权限+审计收口    │
  └───────────┘          │ /admin/*      │        └────────┬─────────┘
                         └──────────────┘                 │
                                                          ▼
                                            ┌──────────────────────────┐
                                            │ Agent (DeepSeek/千问)     │
                                            │  + Skills 封装            │
                                            │  根因分析 / 风险简报       │
                                            └────────────┬─────────────┘
                                                         │
                                            ┌────────────▼─────────────┐
                                            │ 处置反馈回流 → 特征表      │
                                            │ 形成持续优化闭环           │
                                            └──────────────────────────┘
                                            管理后台：登记系统/表/字段/规则/指标
                                            驱动 ①②③ 全链路自动适配
```

---

## 四、技术栈清单

| 层 | 技术选型 | 版本 | 为什么是它 |
|---|---|---|---|
| **数据库** | **MySQL 8.0.43**（本机 `D:\mysql8`，端口 3306） | 8.0.43 | 18 个库统一承载源系统 + 数仓 + 平台；Navicat 可直接查看 |
| 源系统存储 | MySQL 多 database（`src_mdm` + 9 个业务库） | — | 一实例多库隔离；业务系统只持有主数据**只读副本** |
| 数仓存储计算 | MySQL 8（`mfg_ods/dwd/dws/ads`） | — | 千万行内足够；窗口函数与 CTE 齐备 |
| 向量检索 | MySQL 存向量 + NumPy 余弦相似度 | — | 演示仅几千条，无需独立向量库（毫秒级） |
| 采集 | Python + dlt | latest | 装饰器式增量同步，替代 DataX 的 XML 配置 |
| 建模治理 | **dw_kit**（自研轻量框架）+ SQL | — | dbt 无官方 MySQL 适配器；自研约 400 行保留 DAG/测试/血缘 |
| 调度编排 | **Dagster** | 1.7+ | 资产编排，血缘自动生成，替代 DolphinScheduler |
| 特征与模型 | Python + LightGBM + SHAP | — | 可解释归因，能回答"**为什么**有风险" |
| 规则评分 | MySQL 向量化 SQL | — | 规则可被测试覆盖，不藏在 Python 里 |
| **源系统后端** | **Java 17 + Spring Boot 3.2** | — | 10 个模块单进程；BCrypt 密码体系、审批全程留痕 |
| 服务端 | Python 3.11 + FastAPI + SQLModel | — | 类型安全、自动 OpenAPI、异步 |
| 管理后台 | FastAPI + SQLModel + Alembic | — | 元数据 CRUD 与数仓物理隔离 |
| 前端 | React 18 + TypeScript + Vite + ECharts | — | 报表与后台同一栈，共用组件库 |
| AI 接入 | **DeepSeek / 千问（Qwen）** 在线 API | — | 国内可用，OpenAI 兼容协议，支持 Function Calling |
| AI 工具协议 | **MCP (FastMCP)** | — | 工具化暴露数仓能力，权限与审计的唯一收口 |
| AI 能力封装 | **Agent Skills** 规范 | — | 高频分析动作可复用 |
| Agent 编排 | Anthropic Agent SDK / 自研工具循环 | — | 多轮工具编排与归因 |

---

## 五、目录结构与职责索引

```
manufacturing-data-platform/
├── docs/                文档中心：架构、数据字典、指标口径、演示脚本
├── infra/               基础设施：Docker Compose、初始化 SQL、环境配置
├── source-data/         源系统模拟：造数引擎 + 9 类系统建表脚本
├── ingestion/           采集接入：连接器、dlt 管道、增量位点管理
├── transforms/          dbt 工程：stg / dwd / dws / ads 四层模型与治理规则
├── models/              机器学习：特征工程、模型训练、SHAP 归因解释
├── orchestration/       Dagster 编排：资产定义、调度、血缘导出
├── ai/                  AI 能力：LLM 网关、RAG、MCP Server、Skills、Agent
├── apps/                应用服务：API 网关、管理后台、报表前端、后台前端
├── ops/                 运维：启停脚本、巡检、备份、SQL 工具模板
├── data/                运行时数据：快照、导出物、Parquet、样本
└── tests/               测试：单元测试、集成测试、端到端演示验收
```

每个子目录内均有 `README.md`，说明**放什么代码、用什么技术栈、干什么事情**。

---

## 六、快速开始

```bash
cd infra
cp .env.example .env          # 填入 DEEPSEEK_API_KEY
docker compose up -d
docker compose logs -f api
```

| 服务 | 地址 | 说明 |
|---|---|---|
| 报表系统 | http://localhost:5173 | 5 个分析页面 + AI 分析按钮 |
| 管理后台 | http://localhost:5174 | 系统/表/字段/规则/指标配置 |
| API 文档 | http://localhost:8000/docs | FastAPI 自动生成 |
| 编排血缘 | http://localhost:3000 | Dagster 资产图与运行历史 |

---

## 七、五周实施计划

| 周 | 交付 |
|---|---|
| 1 | 环境与骨架、数据字典冻结、页面原型 |
| 2 | 造数引擎（含坏数据注入）、采集链路、ODS/DWD |
| 3 | DWS/ADS、40~50 条 dbt test、对账、DQ 结果表 |
| 4 | 延期模型 + SHAP 归因、规则评分、MCP Server、语义层 |
| 5 | Skills 封装、Agent 编排、自研报表、管理后台、双入口演示彩排 |

**演示终局：** 在报表页点开一个高风险订单 → 点「AI 分析」→ Agent 自动调 MCP 工具查缺料、停机、质量、血缘 → 流式输出原因链与处置建议。
