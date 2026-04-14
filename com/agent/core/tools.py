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
    }

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


def execute_tool_call(tool_call):
    name = tool_call.function.name
    args = json.loads(tool_call.function.arguments or "{}")
    print(f"[trace][tool] dispatch: {name}, args={args}")
    handler = TOOL_HANDLERS.get(name)
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