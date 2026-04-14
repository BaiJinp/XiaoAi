import json
import subprocess
from datetime import datetime
from pathlib import Path

from com.agent import plan
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
    return {
        "date": date,
        "weekday": datetime.strptime(date, "%Y-%m-%d").strftime("%A"),
        "events": [
            {"time": "09:30", "title": "站会"},
            {"time": "14:00", "title": "需求评审"}
        ],
        "source": "mock"
    }


def execute_tool_call(tool_call):
    name = tool_call.function.name
    args = json.loads(tool_call.function.arguments or "{}")

    if name == "get_current_weather":
        return mock_get_current_weather(
            location=args.get("location", "北京"),
            unit=args.get("unit", "celsius")
        )
    if name == "get_calendar":
        return mock_get_calendar(args.get("date", datetime.now().strftime("%Y-%m-%d")))
    return {"error": f"unknown tool: {name}"}


def run_bash(command):
    try:
        result = subprocess.run(command, shell=True, capture_output=True, text=True)
        return {"output": result.stdout, "error": result.stderr}
    except Exception as e:
        return {"error": str(e)}

TOOLS_HANDLES = {
    "bash":run_bash,
    "get_current_weather": mock_get_current_weather,
    "get_calendar": mock_get_calendar,
    "todo": lambda **kw: plan.update(kw["items"])
}