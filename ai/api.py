"""HTTP API consumed by the portal's AI data query page."""

from __future__ import annotations

import logging
from typing import Literal

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from ai.config import AI_API_HOST, AI_API_PORT, AI_PROVIDER, MCP_SERVER_URL, configured_providers
from ai.llm.agent import ask_data


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI(
    title="制造业 AI 数据问答",
    description="通过 MCP 探索并只读查询制造业十个源系统。",
    version="0.1.0",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type"],
)


class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=6000)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    provider: Literal["deepseek", "qwen"] | None = None
    history: list[ChatTurn] = Field(default_factory=list, max_length=20)


@app.get("/health")
def health() -> dict[str, object]:
    providers = configured_providers()
    return {
        "status": "ok",
        "service": "mfg-ai-query-api",
        "mcp_server_url": MCP_SERVER_URL,
        "default_provider": AI_PROVIDER,
        "configured_providers": [item["id"] for item in providers if item["configured"]],
    }


@app.get("/api/ai/providers")
def providers() -> dict[str, object]:
    """Return provider/model labels and key-configured flags without exposing secrets."""
    return {"default_provider": AI_PROVIDER, "providers": configured_providers()}


@app.post("/api/ai/chat")
async def chat(request: ChatRequest) -> dict[str, object]:
    message = request.message.strip()
    if not message:
        raise HTTPException(status_code=422, detail="问题不能为空")
    try:
        result = await ask_data(
            message,
            request.provider,
            [turn.model_dump() for turn in request.history],
        )
        return result
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except RuntimeError as exc:
        logger.warning("AI query returned an upstream error: %s", exc)
        raise HTTPException(status_code=502, detail=str(exc)) from exc


def run() -> None:
    uvicorn.run("ai.api:app", host=AI_API_HOST, port=AI_API_PORT, reload=False)


if __name__ == "__main__":
    run()
