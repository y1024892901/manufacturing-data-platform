# ai/prompts/ — 提示词模板库

## 技术栈
Markdown + YAML frontmatter（Agent Skills 风格）· Jinja2 渲染 · 版本化管理

## 放什么代码

| 文件 | 用途 | 使用方 |
|---|---|---|
| `system_analyst.md` | 数据分析师角色设定（含业务背景、术语约定、禁止行为） | Agent |
| `text2sql.md` | 自然语言转 SQL（含表结构、口径约束、安全边界） | Agent / MCP |
| `metric_explain.md` | 指标口径解释 | MCP `lookup_glossary` |
| `root_cause.md` | 根因分析推理链 | Agent |
| `suggest_action.md` | 处置建议生成 | Agent |
| `report_brief.md` | 风险简报撰写 | Skill |
| `data_dict_gen.md` | 字段中文含义生成 dbt 模型初稿 | 管理后台 |

## Prompt 版本化约定

每个模板文件头部带元数据：

```markdown
---
name: root_cause
version: 3
model: deepseek-chat
temperature: 0.1        # 归因分析要求稳定，温度必须低
max_tokens: 2000
updated_at: 2026-09-17
changelog: 增加"必须引用具体订单号与物料编码"的约束
---
```

## 关键提示词设计原则

| 原则 | 说明 |
|---|---|
| **低温度** | 归因类任务 `temperature ≤ 0.2`，保证同样输入给同样结论 |
| **强制引用证据** | 要求输出必须包含具体订单号、物料编码、设备编号——防止编造 |
| **禁止的行为写清楚** | 明确写出"不得自行推算数字""不得使用未在工具返回中出现的数据" |
| **Few-shot 示例** | 每个模板至少 2 个完整示例（正确输出 + 错误输出对比） |
| **输出结构化** | 优先要求 JSON 输出，便于程序解析与展示 |

## 干什么事情
1. Prompt 变更走代码评审，历史版本可追溯（**演示"Prompt 也是代码"的工程化理念**）
2. 模板中注入真实表结构与指标口径（从元数据动态读取，不硬编码）
3. 提供 Prompt 调试脚本，改完立即试跑不用重启服务
