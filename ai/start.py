"""Run the AI API and its local MCP service from a single terminal."""

from __future__ import annotations

import os
from pathlib import Path
import socket
import subprocess
import sys
import time
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ai.config import AI_API_HOST, AI_API_PORT, MCP_HOST, MCP_PORT, MCP_SERVER_URL


def check_port(host: str, port: int) -> None:
    """Check availability without terminating any existing service."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        if os.name == "nt":
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        try:
            probe.bind((host, port))
        except OSError as exc:
            raise RuntimeError(f"端口 {host}:{port} 不可用，请检查配置或已运行的服务。") from exc


def wait_ready(process: subprocess.Popen, host: str, port: int, timeout: float = 30) -> None:
    deadline = time.monotonic() + timeout
    connect_host = "127.0.0.1" if host == "0.0.0.0" else host
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"服务在启动期间退出，退出码 {process.returncode}，请查看上方日志。")
        try:
            with socket.create_connection((connect_host, port), timeout=0.3):
                return
        except OSError:
            time.sleep(0.2)
    raise RuntimeError(f"等待 {host}:{port} 启动超时，请查看上方日志。")


def stop_all(processes: list[subprocess.Popen]) -> None:
    # Only stop processes created by this invocation, never kill by port/name.
    for process in reversed(processes):
        if process.poll() is None:
            process.terminate()
    for process in reversed(processes):
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def main() -> int:
    processes: list[subprocess.Popen] = []
    try:
        if AI_API_PORT == MCP_PORT:
            raise RuntimeError("AI_API_PORT 与 MCP_PORT 不能相同。")
        url = urlsplit(MCP_SERVER_URL)
        local_hosts = {MCP_HOST, "localhost", "127.0.0.1"}
        if url.scheme != "http" or url.hostname not in local_hosts or url.port != MCP_PORT or url.path.rstrip("/") != "/mcp":
            raise RuntimeError("统一启动需要 MCP_SERVER_URL 指向本机 MCP_HOST/MCP_PORT 的 /mcp 地址。")
        for host, port in ((MCP_HOST, MCP_PORT), (AI_API_HOST, AI_API_PORT)):
            check_port(host, port)
        env = {**os.environ, "PYTHONUNBUFFERED": "1", "PYTHONIOENCODING": "utf-8"}
        for module, host, port in (
            ("ai.mcp_server.server", MCP_HOST, MCP_PORT),
            ("ai.api", AI_API_HOST, AI_API_PORT),
        ):
            print(f"正在启动 {module}（端口 {port}）…", flush=True)
            child = subprocess.Popen([sys.executable, "-m", module], cwd=ROOT, env=env)
            processes.append(child)
            wait_ready(child, host, port)
        print(f"AI 服务已启动：API http://{AI_API_HOST}:{AI_API_PORT}；MCP {MCP_SERVER_URL}", flush=True)
        print("按 Ctrl+C 停止本次启动的两个服务。启动成功不代表数据库或模型密钥已验证。", flush=True)
        while True:
            for child in processes:
                if child.poll() is not None:
                    raise RuntimeError(f"AI 子服务退出（退出码 {child.returncode}），正在停止另一服务。")
            time.sleep(0.5)
    except KeyboardInterrupt:
        print("\n正在停止 AI 服务…", flush=True)
        return 0
    except (OSError, RuntimeError, ValueError) as exc:
        print(f"启动失败：{exc}", file=sys.stderr, flush=True)
        return 1
    finally:
        stop_all(processes)


if __name__ == "__main__":
    raise SystemExit(main())
