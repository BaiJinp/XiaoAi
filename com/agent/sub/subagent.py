"""
子 Agent：fork（继承父消息快照）与 fresh（全新上下文）两种模式。
各自可配置「允许调用的工具名」子集，与 Claude Code 子代理/Plan 模式思路类似：权限 + 独立工具表 + 独立消息轨迹。
"""
from __future__ import annotations

import copy
import json
import os
from dataclasses import dataclass, field
from typing import Any, Callable, Literal

from com.agent.core.client_config import MODEL, client
from com.agent.core.tools import TOOL_HANDLERS, TOOLS, execute_tool_call

SubAgentMode = Literal["fork", "fresh", "new"]
ForkPolicy = Literal["full", "last_n"]

# 主流程通过工具委派子 Agent 时使用的工具名；子 Agent 会话中禁止再暴露，避免递归委派。
SUBAGENT_DELEGATE_TOOL_NAME = "run_subagent"

_AGENT_KINDS: dict[str, "SubAgentSpec"] = {}


@dataclass(frozen=True)
class SubAgentPermissions:
    """子 Agent 允许的工具名（必须为 TOOL_HANDLERS 中已存在的 key）。"""

    allowed_tool_names: frozenset[str]

    @staticmethod
    def none() -> "SubAgentPermissions":
        return SubAgentPermissions(frozenset())

    @staticmethod
    def plan_readonly() -> "SubAgentPermissions":
        """仅可加载技能文档，不可 bash（类 Plan 只读阶段）。"""
        return SubAgentPermissions(frozenset({"load_skill"}))

    @staticmethod
    def explore() -> "SubAgentPermissions":
        """读技能 + 只读类查询，不写 todo。"""
        return SubAgentPermissions(frozenset({"load_skill", "get_current_weather", "get_calendar"}))

    @staticmethod
    def executor() -> "SubAgentPermissions":
        """可执行脚本与技能加载。"""
        return SubAgentPermissions(frozenset({"bash", "load_skill", "todo"}))

    @staticmethod
    def full() -> "SubAgentPermissions":
        return SubAgentPermissions(
            frozenset(k for k in TOOL_HANDLERS.keys() if k != SUBAGENT_DELEGATE_TOOL_NAME)
        )


@dataclass
class SubAgentSpec:
    """可注册的 Agent 定义；后续自定义 agent 即新增 SubAgentSpec 并 register_agent_kind。"""

    name: str
    system_prompt: str
    permissions: SubAgentPermissions
    model: str | None = None
    extra_tools: list[dict[str, Any]] = field(default_factory=list)
    extra_handlers: dict[str, Callable[..., Any]] = field(default_factory=dict)

    def merged_tooling(self) -> tuple[list[dict[str, Any]] | None, dict[str, Callable[..., Any]]]:
        allowed = self.permissions.allowed_tool_names

        def _allowed_tool(n: str | None) -> bool:
            return bool(n) and n in allowed and n != SUBAGENT_DELEGATE_TOOL_NAME

        base_tools = [t for t in TOOLS if _allowed_tool(t.get("function", {}).get("name"))]
        handlers = {k: v for k, v in TOOL_HANDLERS.items() if _allowed_tool(k)}
        for t in self.extra_tools:
            fn = t.get("function", {})
            n = fn.get("name")
            if n and n in self.extra_handlers:
                base_tools = base_tools + [t]
                handlers[n] = self.extra_handlers[n]
        if not base_tools:
            return None, {}
        return base_tools, handlers


def register_agent_kind(kind: str, spec: SubAgentSpec) -> None:
    """注册命名 agent，供 run_subagent(agent_kind=...) 使用。"""
    _AGENT_KINDS[kind] = spec


def get_agent_kind(kind: str) -> SubAgentSpec | None:
    return _AGENT_KINDS.get(kind)


def list_registered_agent_kinds() -> list[str]:
    return sorted(_AGENT_KINDS.keys())


def _normalize_mode(mode: str) -> Literal["fork", "fresh"]:
    m = (mode or "").strip().lower()
    if m in ("new", "fresh", "clean"):
        return "fresh"
    if m in ("fork", "branch", "copy"):
        return "fork"
    raise ValueError(f"invalid subagent mode: {mode!r}, expect fork|fresh|new")


def _fork_messages(
    parent_messages: list[dict[str, Any]],
    *,
    fork_policy: ForkPolicy = "full",
    last_n: int = 30,
    sub_system: str,
    task: str,
    task_id: str,
) -> list[dict[str, Any]]:
    snap = copy.deepcopy(parent_messages)
    if fork_policy == "last_n" and len(snap) > last_n:
        snap = snap[-last_n:]
    header = {
        "role": "system",
        "content": (
            sub_system
            + "\n\n【fork 模式】以下为父对话消息快照（只读继承），请在此基础上完成子任务，"
            "不要假设父对话中未出现的权限；你的工具集以本子 Agent 配置为准。"
        ),
    }
    tail = {"role": "user", "content": f"[子任务 {task_id}]\n{task}"}
    return [header, *snap, tail]


def _fresh_messages(
    *,
    spec: SubAgentSpec,
    task: str,
    task_id: str,
    parent_summary: str | None,
) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = [{"role": "system", "content": spec.system_prompt}]
    if parent_summary:
        out.append(
            {
                "role": "user",
                "content": "【父对话摘要（非完整上下文）】\n" + parent_summary.strip(),
            }
        )
    out.append({"role": "user", "content": f"[子任务 {task_id}]\n{task}"})
    return out


def build_messages(
    *,
    mode: str,
    spec: SubAgentSpec,
    task: str,
    task_id: str,
    parent_messages: list[dict[str, Any]] | str | None = None,
    parent_summary: str | None = None,
    fork_policy: ForkPolicy = "full",
    fork_last_n: int = 30,
) -> list[dict[str, Any]]:
    m = _normalize_mode(mode)
    sub_sys = (
        f"你是子 Agent「{spec.name}」。{spec.system_prompt}\n"
        f"当前允许的工具（仅可调用这些）: {', '.join(sorted(spec.permissions.allowed_tool_names)) or '无'}"
    )
    if m == "fresh":
        return _fresh_messages(spec=spec, task=task, task_id=task_id, parent_summary=parent_summary)
    if parent_messages is None:
        raise ValueError("fork 模式需要 parent_messages（父消息列表的快照）")
    if isinstance(parent_messages, str):
        parent_messages = [{"role": "user", "content": parent_messages}]
    return _fork_messages(
        parent_messages,
        fork_policy=fork_policy,
        last_n=fork_last_n,
        sub_system=sub_sys,
        task=task,
        task_id=task_id,
    )


def run_subagent(
    *,
    mode: str,
    task: str,
    task_id: str,
    spec: SubAgentSpec | None = None,
    agent_kind: str | None = None,
    parent_messages: list[dict[str, Any]] | str | None = None,
    parent_summary: str | None = None,
    fork_policy: ForkPolicy = "full",
    fork_last_n: int = 30,
    max_llm_rounds: int | None = None,
    max_tool_rounds: int | None = None,
    temperature: float = 0.4,
) -> dict[str, Any]:
    """
    运行子 Agent 直至本轮无 tool_calls 或触达上限。

    - fork：深拷贝 parent_messages 为起点，再追加子任务 user。
    - fresh：仅 system + 可选父摘要 + 子任务，与父对话隔离。
    """
    if spec is None:
        if not agent_kind:
            raise ValueError("需要提供 spec 或 agent_kind")
        spec = _AGENT_KINDS.get(agent_kind)
        if spec is None:
            return {"ok": False, "error": f"unknown agent_kind: {agent_kind}", "task_id": task_id}
    max_llm = max_llm_rounds if max_llm_rounds is not None else int(os.getenv("SUBAGENT_MAX_LLM_ROUNDS", "24"))
    max_tools = max_tool_rounds if max_tool_rounds is not None else int(os.getenv("SUBAGENT_MAX_TOOL_ROUNDS", "12"))

    tools_list, handlers = spec.merged_tooling()
    model = spec.model or MODEL

    try:
        messages = build_messages(
            mode=mode,
            spec=spec,
            task=task,
            task_id=task_id,
            parent_messages=parent_messages,
            parent_summary=parent_summary,
            fork_policy=fork_policy,
            fork_last_n=fork_last_n,
        )
    except ValueError as e:
        return {"ok": False, "error": str(e), "task_id": task_id}

    llm_round = 0
    tool_round = 0
    stop_reason = "natural"
    final_content: str | None = None

    while True:
        llm_round += 1
        if llm_round > max_llm:
            stop_reason = "max_llm_rounds"
            break

        kwargs: dict[str, Any] = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": 8000,
        }
        if tools_list:
            kwargs["tools"] = tools_list
        resp = client.chat.completions.create(**kwargs)
        msg = resp.choices[0].message
        tool_calls = msg.tool_calls or []
        messages.append(msg.model_dump())

        if tool_calls:
            if tool_round >= max_tools:
                stop_reason = "max_tool_rounds"
                break
            for tc in tool_calls:
                result = execute_tool_call(tc, handlers)
                messages.append(
                    {
                        "role": "tool",
                        "tool_call_id": tc.id,
                        "content": json.dumps(result, ensure_ascii=False),
                    }
                )
            tool_round += 1
            continue

        final_content = msg.content
        stop_reason = "natural"
        break

    return {
        "ok": stop_reason == "natural",
        "task_id": task_id,
        "mode": _normalize_mode(mode),
        "agent": spec.name,
        "stop_reason": stop_reason,
        "final_content": final_content,
        "messages": messages,
        "llm_rounds": llm_round,
        "tool_rounds": tool_round,
        "error": None if stop_reason == "natural" else stop_reason,
    }


def create_subagent(
    type_: str,
    task: str,
    task_id: str,
    message_history: list[dict[str, Any]] | str | None = None,
    *,
    spec: SubAgentSpec | None = None,
    agent_kind: str | None = "executor",
    parent_summary: str | None = None,
    fork_policy: ForkPolicy = "full",
    fork_last_n: int = 30,
) -> dict[str, Any]:
    """
    兼容旧命名的入口：type 'new'|'fresh' -> fresh；'fork' -> fork。
    message_history：fork 时传父 messages；fresh 时可不传，父摘要用 parent_summary。
    """
    return run_subagent(
        mode=type_,
        task=task,
        task_id=task_id,
        spec=spec,
        agent_kind=None if spec else agent_kind,
        parent_messages=message_history,
        parent_summary=parent_summary,
        fork_policy=fork_policy,
        fork_last_n=fork_last_n,
    )


def _register_builtin_kinds() -> None:
    register_agent_kind(
        "plan_readonly",
        SubAgentSpec(
            name="plan_readonly",
            system_prompt="你只读分析：可加载技能文档理解流程，不要执行 shell。输出结构化计划与风险点。",
            permissions=SubAgentPermissions.plan_readonly(),
        ),
    )
    register_agent_kind(
        "explore",
        SubAgentSpec(
            name="explore",
            system_prompt="你负责探索与检索：可加载技能、使用日历/天气等只读工具，不写文件、不跑 bash。",
            permissions=SubAgentPermissions.explore(),
        ),
    )
    register_agent_kind(
        "executor",
        SubAgentSpec(
            name="executor",
            system_prompt="你负责按技能执行：可 load_skill、bash、todo，直到子任务可交付。",
            permissions=SubAgentPermissions.executor(),
        ),
    )
    register_agent_kind(
        "full",
        SubAgentSpec(
            name="full",
            system_prompt="你拥有与主 Agent 相同的默认工具集（仍受本表约束）。",
            permissions=SubAgentPermissions.full(),
        ),
    )


_register_builtin_kinds()
