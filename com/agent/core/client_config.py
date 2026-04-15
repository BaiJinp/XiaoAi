"""OpenAI 兼容客户端与模型名（供 loop / subagent 共用，避免子模块 import loop 产生副作用）。"""
import os
import sys
from pathlib import Path

from openai import OpenAI


def load_env_file() -> None:
    env_candidates = [
        Path(__file__).resolve().parents[1] / ".env",
        Path(__file__).resolve().parents[3] / ".env",
    ]
    for env_path in env_candidates:
        if not env_path.exists():
            continue
        for line in env_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key and key not in os.environ:
                os.environ[key] = value
        break


load_env_file()

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

client = OpenAI(base_url=os.getenv("BASE_URL"), api_key=os.getenv("API_KEY"))
MODEL = os.getenv("CHAT_MODEL", "qwen3.5-plus")
