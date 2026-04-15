"""
会话隔离 + 主对话绑定 + 上下文压缩。

- 按 session_id 分桶存储 messages 引用，线程安全；fork 快照按 session 读取。
- 可选 ContextVar「当前会话」，便于单进程内 asyncio 链路透传；多线程应在各线程显式 set_current_session / 传 session_id。
- 压缩：在中间段不含 tool/tool_calls 时，将较早消息折叠为一条 system 摘要（可选 LLM）。
"""
from __future__ import annotations

import copy
import json
import os
import threading
import uuid
from contextvars import ContextVar
from dataclasses import dataclass, field
from typing import Any

_lock = threading.RLock()

# 当前协程/线程逻辑会话（可被各线程显式覆盖）
_current_session_id: ContextVar[str | None] = ContextVar("current_session_id", default=None)


@dataclass
class SessionState:
    session_id: str
    messages: list[dict[str, Any]]
    compressions: int = 0
    meta: dict[str, Any] = field(default_factory=dict)


_sessions: dict[str, SessionState] = {}


def _resolve_session_id(explicit: str | None) -> str | None:
    if explicit:
        return explicit
    return _current_session_id.get()


def set_current_session(session_id: str | None) -> None:
    """设置当前逻辑会话 id（同线程后续 bind/snapshot/compress 缺省用它）。"""
    _current_session_id.set(session_id)


def get_current_session_id() -> str | None:
    return _current_session_id.get()


def register_session(session_id: str, messages: list[dict[str, Any]]) -> None:
    """绑定该会话的主 messages 列表（与主循环共享同一 list 引用即可）。"""
    with _lock:
        _sessions[session_id] = SessionState(session_id=session_id, messages=messages)


def unregister_session(session_id: str) -> None:
    with _lock:
        _sessions.pop(session_id, None)


def get_session_state(session_id: str) -> SessionState | None:
    with _lock:
        return _sessions.get(session_id)


def ensure_loop_session(
    messages: list[dict[str, Any]],
    session_id: str | None = None,
) -> str:
    """
    主循环入口：注册会话并设为当前会话。
    未传 session_id 时优先环境变量 SESSION_ID，否则生成 uuid。
    返回实际使用的 session_id。
    """
    sid = (session_id or os.getenv("SESSION_ID") or "").strip() or str(uuid.uuid4())
    with _lock:
        _sessions[sid] = SessionState(session_id=sid, messages=messages)
    set_current_session(sid)
    print(f"[trace][context] session registered: {sid}")
    return sid


def bind_main_messages(
    messages: list[dict[str, Any]],
    session_id: str | None = None,
) -> str:
    """兼容旧 API：等价于 ensure_loop_session。"""
    return ensure_loop_session(messages, session_id=session_id)


def get_main_messages_snapshot(session_id: str | None = None) -> list[dict[str, Any]]:
    """深拷贝指定会话（或当前会话）的主 messages，供 fork 等使用。"""
    sid = _resolve_session_id(session_id)
    if not sid:
        return []
    with _lock:
        st = _sessions.get(sid)
        if not st:
            return []
        return copy.deepcopy(st.messages)


def clear_main_messages_binding() -> None:
    """注销当前 ContextVar 指向的会话绑定（不删 list 本身）。"""
    sid = _current_session_id.get()
    if sid:
        unregister_session(sid)
    set_current_session(None)


def list_session_ids() -> list[str]:
    with _lock:
        return list(_sessions.keys())


def _count_leading_system(messages: list[dict[str, Any]]) -> int:
    n = 0
    for m in messages:
        if m.get("role") == "system":
            n += 1
        else:
            break
    return n


def _message_has_tool_calls(m: dict[str, Any]) -> bool:
    tc = m.get("tool_calls")
    return bool(tc)


def _middle_is_compressible(middle: list[dict[str, Any]]) -> bool:
    for m in middle:
        if m.get("role") == "tool":
            return False
        if m.get("role") == "assistant" and _message_has_tool_calls(m):
            return False
    return True


def _find_safe_tail_start(
    messages: list[dict[str, Any]],
    leading_system: int,
    min_keep_last: int,
) -> int | None:
    """
    从小到大尝试「尾部保留条数」= min_keep_last, min_keep_last+1, ...
    使 middle = messages[leading_system:tail_start] 不含 tool / 带 tool_calls 的 assistant。
    """
    n = len(messages)
    for keep_last in range(min_keep_last, n - leading_system + 1):
        tail_start = n - keep_last
        middle = messages[leading_system:tail_start]
        if not middle:
            return None
        if _middle_is_compressible(middle):
            return tail_start
    return None


def _flatten_content(content: Any, max_len: int) -> str:
    if content is None:
        return ""
    if isinstance(content, list):
        text = json.dumps(content, ensure_ascii=False)
    else:
        text = str(content)
    text = text.replace("\n", " ").strip()
    if len(text) > max_len:
        return text[:max_len] + "…"
    return text


def _heuristic_summary(middle: list[dict[str, Any]], max_chars: int) -> str:
    parts: list[str] = []
    budget = max_chars
    for m in middle:
        role = m.get("role", "?")
        line = f"[{role}] {_flatten_content(m.get('content'), 600)}"
        if len(line) > budget:
            line = line[: max(0, budget)] + "…"
        parts.append(line)
        budget -= len(line) + 1
        if budget <= 0:
            break
    body = "\n".join(parts)
    return (
        f"[上下文已压缩] 以下摘要覆盖 {len(middle)} 条较早消息（中间段无 tool 链）。\n"
        f"后续对话请以上下文与最近消息为准。\n\n{body}"
    )


def _llm_summary(middle: list[dict[str, Any]], max_in: int) -> str | None:
    if os.getenv("CONTEXT_COMPRESS_USE_LLM", "0").strip() not in ("1", "true", "yes"):
        return None
    try:
        from com.agent.core.client_config import MODEL, client
    except Exception:
        return None
    lines: list[str] = []
    for m in middle:
        role = m.get("role", "?")
        lines.append(f"{role}: {_flatten_content(m.get('content'), 800)}")
    blob = "\n".join(lines)[:max_in]
    try:
        resp = client.chat.completions.create(
            model=MODEL,
            temperature=0.2,
            max_tokens=2048,
            messages=[
                {
                    "role": "system",
                    "content": "你是上下文压缩器。将对话历史压缩为简洁中文要点列表，保留目标、约束、已确认事实与未决问题。不要编造。",
                },
                {"role": "user", "content": "请压缩下列对话片段：\n\n" + blob},
            ],
        )
        text = (resp.choices[0].message.content or "").strip()
        if not text:
            return None
        return "[上下文已压缩-模型摘要]\n" + text
    except Exception as exc:
        print(f"[trace][context] llm compress failed: {exc}")
        return None


def maybe_compress(
    session_id: str | None = None,
    *,
    force: bool = False,
) -> dict[str, Any]:
    """
    若消息条数超过阈值且中间段可安全压缩（不含 tool/tool_calls），原地替换 messages 切片。
    返回 {compressed, session_id, before, after, reason?}
    """
    if os.getenv("CONTEXT_COMPRESS_ENABLED", "1").strip() in ("0", "false", "no"):
        return {"compressed": False, "reason": "disabled"}

    sid = _resolve_session_id(session_id)
    if not sid:
        return {"compressed": False, "reason": "no_session"}

    min_messages = int(os.getenv("CONTEXT_COMPRESS_MIN_MESSAGES", "48"))
    min_keep_last = int(os.getenv("CONTEXT_COMPRESS_KEEP_LAST", "24"))
    max_middle_chars = int(os.getenv("CONTEXT_COMPRESS_MAX_MIDDLE_CHARS", "12000"))

    with _lock:
        st = _sessions.get(sid)
        if not st:
            return {"compressed": False, "reason": "unknown_session"}
        messages = st.messages

        if not force and len(messages) <= min_messages:
            return {"compressed": False, "session_id": sid, "before": len(messages), "reason": "below_threshold"}

        leading = _count_leading_system(messages)
        tail_start = _find_safe_tail_start(messages, leading, min_keep_last)
        if tail_start is None:
            return {"compressed": False, "session_id": sid, "before": len(messages), "reason": "middle_not_safe"}

        middle = messages[leading:tail_start]
        if not middle:
            return {"compressed": False, "session_id": sid, "before": len(messages), "reason": "nothing_to_drop"}

        min_drop = int(os.getenv("CONTEXT_COMPRESS_MIN_DROP", "3"))
        if not force and len(middle) < min_drop:
            return {"compressed": False, "session_id": sid, "before": len(messages), "reason": "middle_too_small"}

        before_len = len(messages)
        summary = _llm_summary(middle, max_middle_chars) or _heuristic_summary(middle, max_middle_chars)
        head = messages[:leading]
        tail = messages[tail_start:]
        new_list = head + [{"role": "system", "content": summary}] + tail
        messages[:] = new_list
        st.compressions += 1

        print(
            f"[trace][context] compressed session={sid} {before_len}->{len(messages)} "
            f"(compressions={st.compressions})"
        )
        return {
            "compressed": True,
            "session_id": sid,
            "before": before_len,
            "after": len(messages),
            "dropped": len(middle),
        }
