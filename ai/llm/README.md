# ai/llm — 千问与 DeepSeek 适配

基础实现见 [`../README.md`](../README.md)。`agent.py` 使用 OpenAI 兼容 Chat Completions 工具调用格式，支持通过配置切换阿里云千问与 DeepSeek，并通过 MCP Client 动态发现、调用 MCP Server 的工具。

密钥从本机 `ai/.env`、`infra/.env` 或进程环境变量读取，不写入代码。
