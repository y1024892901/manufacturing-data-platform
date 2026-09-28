"""Metadata discovery and guarded, read-only MySQL queries for MCP tools."""

from __future__ import annotations

import json
import re
from typing import Any

import pymysql
import sqlglot
from pymysql.cursors import DictCursor
from sqlglot import exp

from ai.config import (
    MAX_QUERY_ROWS,
    MAX_SCHEMA_RESULTS,
    MYSQL_CONNECT_TIMEOUT,
    MYSQL_HOST,
    MYSQL_PASSWORD,
    MYSQL_PORT,
    MYSQL_QUERY_TIMEOUT_MS,
    MYSQL_READ_TIMEOUT,
    MYSQL_USER,
    SOURCE_BY_CODE,
)


_IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,63}$")
_BLOCKED_FUNCTIONS = {"SLEEP", "BENCHMARK", "LOAD_FILE", "GET_LOCK", "RELEASE_LOCK"}
_QUERY_TYPES = (exp.Select, exp.Union, exp.Intersect, exp.Except)


class DataAccessError(ValueError):
    """A safe and user-readable error from metadata or query access."""


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, default=str, indent=2)


def _source(system_code: str):
    source = SOURCE_BY_CODE.get(system_code.strip().lower())
    if source is None:
        allowed = ", ".join(SOURCE_BY_CODE)
        raise DataAccessError(f"未知系统代码 {system_code!r}，可选值：{allowed}")
    if not source.database.startswith("src_") or not _IDENTIFIER.fullmatch(source.database):
        raise DataAccessError(f"系统 {source.code} 配置的数据库名不符合 src_* 约定")
    return source


def _connect(database: str | None = None):
    kwargs: dict[str, Any] = {
        "host": MYSQL_HOST,
        "port": MYSQL_PORT,
        "user": MYSQL_USER,
        "password": MYSQL_PASSWORD,
        "charset": "utf8mb4",
        "cursorclass": DictCursor,
        "autocommit": True,
        "connect_timeout": MYSQL_CONNECT_TIMEOUT,
        "read_timeout": MYSQL_READ_TIMEOUT,
        "write_timeout": MYSQL_CONNECT_TIMEOUT,
        "local_infile": False,
    }
    if database:
        kwargs["database"] = database
    return pymysql.connect(**kwargs)


def _db_error(exc: Exception) -> str:
    return f"MySQL 数据访问失败（{MYSQL_HOST}:{MYSQL_PORT}）：{type(exc).__name__}: {exc}"


def list_tables(system_code: str) -> str:
    source = _source(system_code)
    try:
        with _connect(source.database) as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT TABLE_NAME AS table_name,
                       TABLE_TYPE AS table_type,
                       TABLE_COMMENT AS table_comment
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = %s
                ORDER BY TABLE_NAME
                """,
                (source.database,),
            )
            rows = cursor.fetchall()
        return _json({"system": source.code, "database": source.database, "tables": rows})
    except pymysql.MySQLError as exc:
        raise DataAccessError(_db_error(exc)) from exc


def search_schema(keyword: str, system_code: str = "") -> str:
    needle = keyword.strip()
    if not needle:
        raise DataAccessError("keyword 不能为空；要查看某个系统的全部表，请调用 list_system_tables")

    if system_code.strip():
        sources = [_source(system_code)]
    else:
        sources = list(SOURCE_BY_CODE.values())

    schemas = [source.database for source in sources]
    placeholders = ", ".join(["%s"] * len(schemas))
    pattern = f"%{needle}%"
    try:
        with _connect() as connection, connection.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT c.TABLE_SCHEMA AS database_name,
                       c.TABLE_NAME AS table_name,
                       t.TABLE_COMMENT AS table_comment,
                       c.COLUMN_NAME AS column_name,
                       c.COLUMN_TYPE AS column_type,
                       c.COLUMN_COMMENT AS column_comment
                FROM information_schema.COLUMNS c
                JOIN information_schema.TABLES t
                  ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
                WHERE c.TABLE_SCHEMA IN ({placeholders})
                  AND CONCAT_WS(' ', c.TABLE_NAME, t.TABLE_COMMENT,
                                    c.COLUMN_NAME, c.COLUMN_COMMENT) LIKE %s
                ORDER BY c.TABLE_SCHEMA, c.TABLE_NAME, c.ORDINAL_POSITION
                LIMIT %s
                """,
                (*schemas, pattern, MAX_SCHEMA_RESULTS),
            )
            rows = cursor.fetchall()
        return _json({"keyword": needle, "matches": rows, "truncated": len(rows) >= MAX_SCHEMA_RESULTS})
    except pymysql.MySQLError as exc:
        raise DataAccessError(_db_error(exc)) from exc


def describe_table(system_code: str, table_name: str) -> str:
    source = _source(system_code)
    name = table_name.strip()
    if not _IDENTIFIER.fullmatch(name):
        raise DataAccessError("table_name 只能包含字母、数字和下划线")

    try:
        with _connect(source.database) as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT TABLE_NAME AS table_name,
                       TABLE_TYPE AS table_type,
                       TABLE_COMMENT AS table_comment
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s
                """,
                (source.database, name),
            )
            table = cursor.fetchone()
            if table is None:
                raise DataAccessError(f"系统 {source.code} 中不存在表 {name}")
            cursor.execute(
                """
                SELECT COLUMN_NAME AS column_name,
                       COLUMN_TYPE AS column_type,
                       IS_NULLABLE AS is_nullable,
                       COLUMN_KEY AS column_key,
                       COLUMN_DEFAULT AS column_default,
                       EXTRA AS extra,
                       COLUMN_COMMENT AS column_comment
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s
                ORDER BY ORDINAL_POSITION
                """,
                (source.database, name),
            )
            columns = cursor.fetchall()
        return _json({"system": source.code, "database": source.database, **table, "columns": columns})
    except DataAccessError:
        raise
    except pymysql.MySQLError as exc:
        raise DataAccessError(_db_error(exc)) from exc


def _validated_limited_sql(sql: str, database: str) -> str:
    text = sql.strip()
    if not text:
        raise DataAccessError("sql 不能为空")
    if len(text) > 12_000:
        raise DataAccessError("SQL 长度超过 12000 个字符")

    try:
        statements = sqlglot.parse(text, read="mysql")
    except sqlglot.errors.ParseError as exc:
        raise DataAccessError(f"SQL 解析失败：{exc}") from exc
    statements = [statement for statement in statements if statement is not None]
    if len(statements) != 1:
        raise DataAccessError("每次只允许执行一条 SELECT 查询")

    statement = statements[0]
    if not isinstance(statement, _QUERY_TYPES):
        raise DataAccessError("只允许 SELECT 查询，禁止写入、DDL、管理命令和存储过程")
    if any(statement.find(node_type) for node_type in (exp.Into, exp.Lock)):
        raise DataAccessError("SELECT INTO、文件写出和行锁语句不允许执行")

    for table in statement.find_all(exp.Table):
        qualified_database = table.args.get("db")
        catalog = table.args.get("catalog")
        if catalog is not None:
            raise DataAccessError("不允许使用三段式跨实例表名")
        if qualified_database is not None:
            qualifier = qualified_database.name if isinstance(qualified_database, exp.Identifier) else str(qualified_database)
            if qualifier.lower() != database.lower():
                raise DataAccessError(f"查询引用了当前系统之外的数据库：{qualifier}")

    for function in statement.find_all(exp.Func):
        function_name = (getattr(function, "name", "") or function.sql_name()).upper()
        if function_name in _BLOCKED_FUNCTIONS:
            raise DataAccessError(f"不允许调用 {function_name} 函数")

    limit = statement.args.get("limit")
    if limit is None:
        statement.limit(MAX_QUERY_ROWS + 1, copy=False)
    else:
        limit_expression = limit.args.get("expression")
        requested_limit: int | None = None
        if isinstance(limit_expression, exp.Literal) and not limit_expression.is_string:
            try:
                requested_limit = int(limit_expression.this)
            except (TypeError, ValueError):
                requested_limit = None
        if requested_limit is None or requested_limit > MAX_QUERY_ROWS + 1:
            statement.limit(MAX_QUERY_ROWS + 1, copy=False)

    return statement.sql(dialect="mysql")


def query_system_data(system_code: str, sql: str) -> str:
    source = _source(system_code)
    limited_sql = _validated_limited_sql(sql, source.database)
    try:
        with _connect(source.database) as connection, connection.cursor() as cursor:
            # Database-enforced read-only transaction backs up the SQL AST allowlist.
            cursor.execute("SET SESSION max_execution_time = %s", (MYSQL_QUERY_TIMEOUT_MS,))
            cursor.execute("SET TRANSACTION READ ONLY")
            connection.begin()
            cursor.execute(limited_sql)
            rows = cursor.fetchmany(MAX_QUERY_ROWS + 1)
            connection.rollback()

        truncated = len(rows) > MAX_QUERY_ROWS
        rows = rows[:MAX_QUERY_ROWS]
        return _json(
            {
                "system": source.code,
                "database": source.database,
                "row_count": len(rows),
                "truncated": truncated,
                "executed_sql": limited_sql,
                "rows": rows,
            }
        )
    except DataAccessError:
        raise
    except pymysql.MySQLError as exc:
        raise DataAccessError(_db_error(exc)) from exc
