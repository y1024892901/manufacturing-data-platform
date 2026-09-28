# ai/mcp_server — 多系统只读 MCP 服务

基础实现见 [`../README.md`](../README.md)。服务基于官方 MCP Python SDK，默认使用 Streamable HTTP (`http://127.0.0.1:8001/mcp`)，并提供系统目录发现、Schema 搜索、表结构描述和只读查询工具。

当前工具按 `src_*` 系统库隔离查询上下文；仅允许 SELECT，MySQL 查询设置只读事务、执行超时和 100 行返回上限。服务默认只监听本机回环地址。
