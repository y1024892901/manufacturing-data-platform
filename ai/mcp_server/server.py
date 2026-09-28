"""MCP tools for exploring and querying the ten manufacturing source systems."""

from __future__ import annotations

import asyncio
import json

from mcp.server.mcpserver import MCPServer
from mcp.types import ToolAnnotations

from ai.config import MCP_HOST, MCP_PORT, SOURCES
from ai.mcp_server.database import (
    DataAccessError,
    describe_table as describe_table_data,
    list_tables as list_tables_data,
    query_system_data as query_system_data_impl,
    search_schema as search_schema_data,
)


mcp = MCPServer(
    "mfg-multi-system-query",
    instructions=(
        "查询制造业平台十个源系统的只读 MCP 服务。先通过 list_data_sources、"
        "search_data_schema 或 list_system_tables 发现真实表结构，再调用 query_system_data。"
        "每次只查询一个源系统数据库；不要臆造字段或跨系统关联关系。"
    ),
)

READ_ONLY = ToolAnnotations(read_only_hint=True, destructive_hint=False, open_world_hint=False)


def _error_json(exc: Exception) -> str:
    return json.dumps({"error": str(exc)}, ensure_ascii=False)


@mcp.tool(title="列出业务系统", annotations=READ_ONLY)
def list_data_sources() -> str:
    """列出可查询的十个业务系统及其 MySQL 数据库代码。"""
    return json.dumps(
        [{"system_code": source.code, "system_name": source.name, "database": source.database} for source in SOURCES],
        ensure_ascii=False,
        indent=2,
    )


@mcp.tool(title="搜索数据表和字段", annotations=READ_ONLY)
async def search_data_schema(keyword: str, system_code: str = "") -> str:
    """按中文或字段关键词搜索系统表、字段及注释；system_code 留空时搜索全部系统。"""
    try:
        return await asyncio.to_thread(search_schema_data, keyword, system_code)
    except Exception as exc:
        return _error_json(exc)


@mcp.tool(title="列出系统数据表", annotations=READ_ONLY)
async def list_system_tables(system_code: str) -> str:
    """列出指定系统中的表、视图和表注释。system_code 可用 mdm/crm/erp/plm/srm/wms/mes/qms/eam/energy。"""
    try:
        return await asyncio.to_thread(list_tables_data, system_code)
    except Exception as exc:
        return _error_json(exc)


@mcp.tool(title="查看数据表结构", annotations=READ_ONLY)
async def describe_data_table(system_code: str, table_name: str) -> str:
    """查看指定系统数据表的列名、类型、主键提示和字段注释。"""
    try:
        return await asyncio.to_thread(describe_table_data, system_code, table_name)
    except Exception as exc:
        return _error_json(exc)


@mcp.tool(title="执行只读数据查询", annotations=READ_ONLY)
async def query_system_data(system_code: str, sql: str) -> str:
    """在指定系统库执行单条只读 SELECT。SQL 必须使用 describe_data_table 返回的真实表和字段。"""
    try:
        return await asyncio.to_thread(query_system_data_impl, system_code, sql)
    except Exception as exc:
        return _error_json(exc)


if __name__ == "__main__":
    print(f"Starting manufacturing MCP server at http://{MCP_HOST}:{MCP_PORT}/mcp")
    mcp.run(
        transport="streamable-http",
        host=MCP_HOST,
        port=MCP_PORT,
        streamable_http_path="/mcp",
        stateless_http=True,
        json_response=True,
    )
