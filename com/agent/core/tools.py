import json
import subprocess
from datetime import datetime
from pathlib import Path

from com.agent.core import skills
from com.agent.task import plan

WORKDIR = Path.cwd()

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "get_current_weather",
            "description": "Get the current weather in a given location",
            "parameters": {
                "type": "object",
                "properties": {
                    "location": {"type": "string", "description": "城市名，例如北京"},
                    "unit": {
                        "type": "string",
                        "enum": ["celsius", "fahrenheit"],
                        "description": "温度单位"
                    }
                },
                "required": ["location"]
            }
        }
    }, {
        "type": "function",
        "function": {
            "name": "get_calendar",
            "description": "Get the calendar of a given day",
            "parameters": {
                "type": "object",
                "properties": {
                    "date": {"type": "string", "description": "日期，格式 YYYY-MM-DD"}
                },
                "required": ["date"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "todo",
            "description": "Rewrite the current session plan for multi-step work.",
            "parameters": {
                "type": "object",
                "properties": {
                    "items": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "content": {"type": "string"},
                                "status": {
                                    "type": "string",
                                    "enum": ["pending", "in_progress", "completed"]
                                },
                                "activeForm": {
                                    "type": "string",
                                    "description": "Optional present-continuous label."
                                }
                            },
                            "required": ["content", "status"]
                        }
                    }
                },
                "required": ["items"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "bash",
            "description": "在本地 shell 执行命令（技能文档中的脚本/查询常需此工具）。Windows 下为 cmd 语义；请使用对当前仓库有效的路径。",
            "parameters": {
                "type": "object",
                "properties": {
                    "command": {
                        "type": "string",
                        "description": "完整 shell 命令，例如 python path/to/script.py --help",
                    }
                },
                "required": ["command"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "load_skill",
            "description": "Load detailed skill markdown by skill name.",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Skill name from available skill list"
                    }
                },
                "required": ["name"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "run_subagent",
            "description": (
                "委派子 Agent 独立会话完成子任务：权限与工具集小于主 Agent。"
                "fresh=全新上下文，可带 parent_summary；fork=继承主对话快照（需主循环已绑定）。"
                "agent_kind：plan_readonly / explore / executor / full 或通过 register_agent_kind 注册的名称。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "mode": {
                        "type": "string",
                        "enum": ["fresh", "fork", "new"],
                        "description": "fresh|new 为隔离上下文；fork 为继承主会话快照",
                    },
                    "task": {"type": "string", "description": "子 Agent 要完成的具体任务说明"},
                    "task_id": {"type": "string", "description": "本次委派的唯一标识，便于日志与追踪"},
                    "agent_kind": {
                        "type": "string",
                        "description": "子 Agent 类型，决定系统提示与允许的工具子集",
                    },
                    "parent_summary": {
                        "type": "string",
                        "description": "fresh 模式下可选：父任务的简短摘要，便于对齐目标",
                    },
                    "fork_policy": {
                        "type": "string",
                        "enum": ["full", "last_n"],
                        "description": "fork 时全量或仅最近若干条主消息",
                    },
                    "fork_last_n": {
                        "type": "integer",
                        "description": "fork_policy=last_n 时保留的主消息条数，默认 30",
                    },
                    "session_id": {
                        "type": "string",
                        "description": "可选。fork 时从哪条主会话取快照；缺省为当前逻辑会话（见 context.set_current_session）",
                    },
                },
                "required": ["mode", "task", "task_id", "agent_kind"],
            },
        },
    },
]



def register(name: str, description: str, parameters: dict):
    TOOLS.append({
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": parameters
        }
    })


def discovery():
    for index in TOOLS:
        print(index)

def mock_get_current_weather(location, unit="celsius"):
    # 这里是模拟数据；你可以改成真实 API 调用
    mock_db = {
        "北京": {"temp_c": 22, "condition": "晴"},
        "上海": {"temp_c": 26, "condition": "多云"},
        "广州": {"temp_c": 30, "condition": "小雨"}
    }
    weather = mock_db.get(location, {"temp_c": 25, "condition": "未知"})
    temp = weather["temp_c"] if unit == "celsius" else round(weather["temp_c"] * 9 / 5 + 32, 1)
    return {
        "location": location,
        "unit": unit,
        "temperature": temp,
        "condition": weather["condition"],
        "source": "mock"
    }


def mock_get_calendar(date):
    if not date:
        date = datetime.now().strftime("%Y-%m-%d")
    return {
        "date": date,
        "weekday": datetime.strptime(date, "%Y-%m-%d").strftime("%A"),
        "events": [
            {"time": "09:30", "title": "董事会"},
            {"time": "14:00", "title": "会见政府领导"}
        ],
        "source": "mock"
    }


def execute_tool_call(tool_call, tool_handlers=None):
    name = tool_call.function.name
    args = json.loads(tool_call.function.arguments or "{}")
    print(f"[trace][tool] dispatch: {name}, args={args}")
    handlers = tool_handlers if tool_handlers is not None else TOOL_HANDLERS
    handler = handlers.get(name)
    if not handler:
        return {"error": f"unknown tool: {name}"}
    try:
        result = handler(**args)
        preview = str(result)
        if len(preview) > 200:
            preview = preview[:200] + "..."
        print(f"[trace][tool] result: {name} -> {preview}")
        return result
    except Exception as exc:
        print(f"[trace][tool] failed: {name}, error={exc}")
        return {"error": f"tool execute failed: {name}", "detail": str(exc)}


def run_bash(command):
    try:
        result = subprocess.run(command, shell=True, capture_output=True, text=True)
        return {"output": result.stdout, "error": result.stderr}
    except Exception as e:
        return {"error": str(e)}

def _run_subagent_tool(**kw):
    """主流程工具：启动子 Agent；延迟 import 避免循环依赖。"""
    from com.agent.core import context
    from com.agent.sub.subagent import run_subagent

    mode = kw.get("mode") or "fresh"
    task = (kw.get("task") or "").strip()
    task_id = (kw.get("task_id") or "sub-1").strip()
    agent_kind = (kw.get("agent_kind") or "executor").strip()
    parent_summary = kw.get("parent_summary")
    if parent_summary is not None and isinstance(parent_summary, str):
        parent_summary = parent_summary.strip() or None
    fork_policy = kw.get("fork_policy") or "full"
    if fork_policy not in ("full", "last_n"):
        fork_policy = "full"
    fork_last_n = int(kw.get("fork_last_n") or 30)

    if not task:
        return {"ok": False, "error": "task 不能为空"}

    m = str(mode).lower()
    parent_messages = None
    fork_session = (kw.get("session_id") or "").strip() or None
    if m in ("fork", "branch", "copy"):
        parent_messages = context.get_main_messages_snapshot(fork_session)
        if not parent_messages:
            return {
                "ok": False,
                "error": "fork 需要该 session 已注册主 messages：loop() 会 ensure_loop_session；多会话请传 session_id 或在该线程 context.set_current_session(...)。",
            }

    raw = run_subagent(
        mode=mode,
        task=task,
        task_id=task_id,
        agent_kind=agent_kind,
        parent_messages=parent_messages,
        parent_summary=parent_summary,
        fork_policy=fork_policy,
        fork_last_n=fork_last_n,
    )
    return {
        "ok": raw.get("ok"),
        "task_id": raw.get("task_id"),
        "agent": raw.get("agent"),
        "mode": raw.get("mode"),
        "stop_reason": raw.get("stop_reason"),
        "final_content": raw.get("final_content"),
        "llm_rounds": raw.get("llm_rounds"),
        "tool_rounds": raw.get("tool_rounds"),
        "error": raw.get("error"),
    }


TOOL_HANDLERS = {
    "bash": lambda **kw: run_bash(kw["command"]),
    "get_current_weather": lambda **kw: mock_get_current_weather(
        location=kw.get("location", "北京"),
        unit=kw.get("unit", "celsius"),
    ),
    "get_calendar": lambda **kw: mock_get_calendar(
        kw.get("date", datetime.now().strftime("%Y-%m-%d"))
    ),
    "todo": lambda **kw: plan.update(kw.get("items", [])),
    "load_skill": lambda **kw: skills.load_skill(kw["name"]),
    "run_subagent": _run_subagent_tool,
}

# backward compatibility for old name
TOOLS_HANDLES = TOOL_HANDLERS


def _register_skills_into_tools():
    skill_items = skills.load()
    print(f"[trace][tool] register skill metadata into load_skill, count={len(skill_items)}")
    for tool in TOOLS:
        fn = tool.get("function", {})
        if fn.get("name") == "load_skill":
            name_prop = fn.get("parameters", {}).get("properties", {}).get("name", {})
            name_prop["enum"] = [item["name"] for item in skill_items]
            fn["description"] = "Load detailed skill markdown by skill name. Available: " + ", ".join(
                [f"{item['name']}({item['description']})" for item in skill_items]
            )
            break


_register_skills_into_tools()


def _patch_run_subagent_tool_enum():
    try:
        from com.agent.sub.subagent import list_registered_agent_kinds
    except Exception:
        return
    kinds = list_registered_agent_kinds()
    if not kinds:
        return
    for tool in TOOLS:
        fn = tool.get("function", {})
        if fn.get("name") != "run_subagent":
            continue
        props = fn.get("parameters", {}).get("properties", {})
        ak = props.get("agent_kind", {})
        ak["enum"] = kinds
        ak["description"] = "子 Agent 类型：" + ", ".join(kinds)
        break


_patch_run_subagent_tool_enum()