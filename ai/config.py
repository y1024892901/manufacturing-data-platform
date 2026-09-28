"""Environment-backed configuration for the AI and MCP services."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Final

from dotenv import load_dotenv


AI_DIR: Final[Path] = Path(__file__).resolve().parent
REPO_ROOT: Final[Path] = AI_DIR.parent

# Local secrets stay in ignored .env files. Explicit process environment values win.
load_dotenv(AI_DIR / ".env", override=False)
load_dotenv(REPO_ROOT / "infra" / ".env", override=False)


@dataclass(frozen=True)
class DataSource:
    code: str
    name: str
    database: str


_SOURCE_DEFINITIONS: Final[tuple[tuple[str, str, str], ...]] = (
    ("mdm", "主数据管理 MDM", "SRC_MDM_DB"),
    ("crm", "客户关系 CRM", "SRC_CRM_DB"),
    ("erp", "企业资源 ERP", "SRC_ERP_DB"),
    ("plm", "产品生命周期 PLM", "SRC_PLM_DB"),
    ("srm", "供应商协同 SRM", "SRC_SRM_DB"),
    ("wms", "仓储管理 WMS", "SRC_WMS_DB"),
    ("mes", "制造执行 MES", "SRC_MES_DB"),
    ("qms", "质量管理 QMS", "SRC_QMS_DB"),
    ("eam", "设备资产 EAM", "SRC_EAM_DB"),
    ("energy", "能源管理", "SRC_ENERGY_DB"),
)

SOURCES: Final[tuple[DataSource, ...]] = tuple(
    DataSource(code, name, os.getenv(env_name, f"src_{code}"))
    for code, name, env_name in _SOURCE_DEFINITIONS
)
SOURCE_BY_CODE: Final[dict[str, DataSource]] = {source.code: source for source in SOURCES}

MYSQL_HOST = os.getenv("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "")
MYSQL_CONNECT_TIMEOUT = int(os.getenv("AI_MYSQL_CONNECT_TIMEOUT", "5"))
MYSQL_READ_TIMEOUT = int(os.getenv("AI_MYSQL_READ_TIMEOUT", "25"))
MYSQL_QUERY_TIMEOUT_MS = int(os.getenv("AI_MYSQL_QUERY_TIMEOUT_MS", "15000"))
MAX_QUERY_ROWS = int(os.getenv("AI_MAX_QUERY_ROWS", "100"))
MAX_SCHEMA_RESULTS = int(os.getenv("AI_MAX_SCHEMA_RESULTS", "100"))

AI_PROVIDER = os.getenv("AI_PROVIDER", os.getenv("LLM_PRIMARY", "deepseek")).strip().lower()
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "").strip()
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com").strip()
_configured_deepseek_model = os.getenv("DEEPSEEK_MODEL", "deepseek-flash").strip()
# Keep older local .env files usable after DeepSeek retired these aliases.
DEEPSEEK_MODEL = (
    "deepseek-flash"
    if _configured_deepseek_model in {"deepseek-chat", "deepseek-reasoner"}
    else _configured_deepseek_model
)
QWEN_API_KEY = os.getenv("QWEN_API_KEY", os.getenv("DASHSCOPE_API_KEY", "")).strip()
QWEN_BASE_URL = os.getenv(
    "QWEN_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"
).strip()
QWEN_MODEL = os.getenv("QWEN_MODEL", "qwen-plus").strip()
LLM_TIMEOUT_SECONDS = float(os.getenv("AI_LLM_TIMEOUT_SECONDS", "90"))
MAX_TOOL_ROUNDS = max(1, int(os.getenv("AI_MAX_TOOL_ROUNDS", "8")))

MCP_HOST = os.getenv("MCP_HOST", "127.0.0.1").strip()
MCP_PORT = int(os.getenv("MCP_PORT", "8001"))
MCP_SERVER_URL = os.getenv("MCP_SERVER_URL", f"http://{MCP_HOST}:{MCP_PORT}/mcp").strip()
AI_API_HOST = os.getenv("AI_API_HOST", "127.0.0.1").strip()
AI_API_PORT = int(os.getenv("AI_API_PORT", "8000"))


def provider_config(provider: str | None = None) -> tuple[str, str, str, str]:
    """Return (provider, api_key, base_url, model) for a configured provider."""
    name = (provider or AI_PROVIDER).strip().lower()
    if name == "deepseek":
        return name, DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL, DEEPSEEK_MODEL
    if name in {"qwen", "dashscope"}:
        return "qwen", QWEN_API_KEY, QWEN_BASE_URL, QWEN_MODEL
    raise ValueError("模型供应商只支持 deepseek 或 qwen")


def configured_providers() -> list[dict[str, str | bool]]:
    return [
        {"id": "deepseek", "name": "DeepSeek", "model": DEEPSEEK_MODEL, "configured": bool(DEEPSEEK_API_KEY)},
        {"id": "qwen", "name": "阿里云千问", "model": QWEN_MODEL, "configured": bool(QWEN_API_KEY)},
    ]
