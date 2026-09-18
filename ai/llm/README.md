# ai/llm/ — LLM 统一网关

## 技术栈
Python 3.11 · OpenAI 兼容协议（DeepSeek 与千问均支持）· httpx · tenacity（重试）

## 职责
屏蔽不同 LLM 供应商的差异，提供统一的调用接口，并保证演示时的**稳定性**。

## 放什么代码

| 文件 | 内容 |
|---|---|
| `gateway.py` | 统一入口 `chat()` / `chat_stream()` / `tool_call()`，自动路由到配置的供应商 |
| `providers/deepseek.py` | DeepSeek 适配（`https://api.deepseek.com`，OpenAI 兼容） |
| `providers/qwen.py` | 千问适配（DashScope OpenAI 兼容模式） |
| `providers/base.py` | 供应商抽象基类 |
| `router.py` | 路由策略：主供应商失败自动切换备用供应商（**演示兜底**） |
| `token_counter.py` | Token 计数与成本估算 |
| `cache.py` | 提示词缓存，相同问题不重复计费 |

## 供应商选择

| 供应商 | 模型 | 特点 | 环境变量 |
|---|---|---|---|
| **DeepSeek**（主） | `deepseek-chat` | 中文强、推理好、价格低、支持 Function Calling | `DEEPSEEK_API_KEY` |
| **千问 Qwen**（备） | `qwen-plus` / `qwen-max` | 阿里云生态、稳定、支持 Function Calling | `DASHSCOPE_API_KEY` |

> 两者都提供 **OpenAI 兼容接口**，因此 `gateway.py` 可以用同一套调用代码，
> 供应商差异只在 `base_url` / `model` / 少量参数上。**切换供应商只需改 `.env`。**

## 关键设计

| 设计 | 目的 |
|---|---|
| **双供应商自动降级** | 主供应商超时/限流 → 自动切备用，演示不会因单一 API 抖动失败 |
| 重试带指数退避 | 最多 3 次，避免瞬时网络问题导致 Agent 中断 |
| 流式输出（SSE） | 报表页「AI 分析」按钮逐字返回，演示体验好 |
| Function Calling 封装 | 统一 DeepSeek 与千问的工具调用格式差异（**本项目的主要适配工作量**） |
| 结构化输出约束 | 归因类任务强制 JSON 输出，便于程序解析 |
| 全量调用日志 | 记录 prompt、响应、token、耗时、成本，写入 `app_meta.llm_call_log` |

## 干什么事情
1. 为 MCP Server 与 Agent 提供底层模型能力
2. 支持按场景选择模型（归因用 `deepseek-chat`，快速摘要用更便宜的模型）
3. 提供成本与延迟监控，管理后台可查看
4. **API Key 只从环境变量读取，绝不写入代码或日志**
