# ai/skills/ — Skill 能力封装

## 技术栈
**Anthropic Agent Skills 规范** · SKILL.md（Markdown + YAML frontmatter）· 可选附带 Python 脚本

## 职责
把**高频分析动作**封装成可复用能力。每个 Skill = 一套固定的「调用哪些工具、按什么顺序、产出什么格式」的配方。

## Skill 与 MCP 的关系

```
MCP 工具  = 原子能力（查指标、跑 SQL、查血缘）    —— 相当于"函数库"
Skill     = 组合配方（先查什么、后查什么、怎么表述）—— 相当于"菜谱"
Agent     = 自主决策用哪个 Skill 或用哪几个工具    —— 相当于"厨师"
```

## 五个 Skill

| Skill | 触发场景 | 编排的工具调用 | 产出 |
|---|---|---|---|
| `order-delay-brief` | "这单为什么有延期风险" | `get_order_risk` → `query_metric` → `get_lineage` | 风险简报：原因链 + 证据 + 建议 |
| `shortage-impact` | "缺料影响哪些订单" | `run_sql(BOM 缺口)` → `query_metric` → `describe_table` | 缺料影响面清单 + 优先级排序 |
| `equipment-impact-chain` | "设备异常影响多大" | `get_lineage` → `query_metric` → `run_sql` | 设备 → 工序 → 订单影响链 |
| `dq-daily-report` | "今天数据质量怎么样" | `get_dq_status` → `get_dq_issue` | 质量日报 + 问题明细 + 影响评估 |
| `finance-collection-brief` | "回款情况如何" | `query_metric` → `lookup_glossary` → `run_sql` | 逾期分析 + 关注清单 |

## SKILL.md 结构

```
ai/skills/order-delay-brief/
├── SKILL.md          触发条件、执行步骤、输出模板、边界说明
├── template.md       报告模板（固定结构，保证输出稳定）
└── scripts/
    └── format.py     格式化辅助（可选）
```

`SKILL.md` 的 frontmatter：

```yaml
---
name: order-delay-brief
description: 生成生产订单延期风险简报。当用户询问某个订单为什么有延期风险、
             哪些原因导致延期、该订单的处置建议时使用。
tools: [get_order_risk, query_metric, get_lineage, lookup_glossary]
---
```

## 设计要点

| 要点 | 说明 |
|---|---|
| **description 要写全触发词** | Agent 靠它决定是否启用该 Skill，写不全就不会被调用 |
| **步骤写死工具顺序** | 减少 Agent 自由发挥导致的结果不稳定 |
| **输出用固定模板** | 保证同一个 Skill 每次产出结构一致，演示可预期 |
| **写明边界** | 明确"什么情况下不要用我"，防止误触发 |
| **数字必须来自工具返回** | 在 SKILL.md 中硬性约束，禁止 AI 自行推算 |
| **控制篇幅** | SKILL.md 建议 500 行以内，过长的指令会稀释关键约束 |

## 干什么事情
1. 把 FDE 人员的分析经验沉淀为可复用资产（对应原方案"Skills 可封装查询、解释和生成报告等重复动作"）
2. 新增分析场景只需加一个 Skill 目录，**不改 Agent 代码**
3. 每个 Skill 可独立测试：给定固定输入，检查输出结构是否符合模板
