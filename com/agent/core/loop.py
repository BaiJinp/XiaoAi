import json
import os
import sys
from pathlib import Path

from openai import OpenAI
try:
    from com.agent.core.tools import execute_tool_call, TOOLS
    from com.agent.core.skills import get_skill_summaries
except ModuleNotFoundError:
    # 兼容直接运行该文件（非 -m 模式）
    project_root = Path(__file__).resolve().parents[3]
    if str(project_root) not in sys.path:
        sys.path.insert(0, str(project_root))
    from com.agent.core.tools import execute_tool_call, TOOLS
    from com.agent.core.skills import get_skill_summaries


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
MODEL = "qwen3.5-plus"
skill_summaries = get_skill_summaries()
skills_prompt = "当前可用技能列表:\n" + "\n".join(
    [f"- {item['name']}: {item['description']}" for item in skill_summaries]
) if skill_summaries else "当前无可用技能文档。"
messages = [
    {"role": "system", "content": "你是个人助手，可以通过技能列表，工具列表来完成我的日常生活和工作的安排，如果我提出了什么问题你需要根据实际场景来回答，包括受影响的安排等"},
    {"role": "system", "content": skills_prompt},
    {"role": "user", "content": "查询软件工程部未提交工时，不需要确认"},
]

max_count = int(os.getenv("MAX_TURNS", "5"))

count = 0


def print_messages():
   print("\n===== messages =====")
   for i, item in enumerate(messages):
       role = item.get("role", "unknown")
       content = item.get("content", "")
       if isinstance(content, list):
           content = json.dumps(content, ensure_ascii=False)
       text = str(content).replace("\n", " ")
       if len(text) > 120:
           text = text[:120] + "..."
       print(f"{i:02d} | {role}: {text}")
   print("====================\n")


def loop():
   global count
   tool_names = [tool.get("function", {}).get("name", "<unknown>") for tool in TOOLS]
   print(f"[trace][loop] start, max_count={max_count}, tools={tool_names}")
   print(f"[trace][loop] skill summaries loaded: {len(skill_summaries)}")
   for item in skill_summaries:
       print(f"[trace][loop] skill: {item['name']} - {item['description']}")
   while True:
       print_messages()
       if count > max_count:
           print("max turns reached")
           break
       print(f"[trace][loop] request round={count + 1}")
       response = client.chat.completions.create(
           model=MODEL,
           messages=messages,
           tools=TOOLS,
           temperature=0.7,
           max_tokens=8000
       )
       msg = response.choices[0].message
       tool_calls = msg.tool_calls or []
       print(f"[trace][loop] response tool_calls={len(tool_calls)}")

       # 第一步：先把 assistant 消息（包含 tool_calls）放进上下文
       messages.append(msg.model_dump())

       if not tool_calls:
           print(msg.content)
           break

       # 第二步：本地执行工具，并把结果以 tool 角色回传
       for tool_call in tool_calls:
           result = execute_tool_call(tool_call)
           print(f"[mock tool] {tool_call.function.name} -> {result}")
           messages.append({
               "role": "tool",
               "tool_call_id": tool_call.id,
               "content": json.dumps(result, ensure_ascii=False)
           })
       count = count + 1

if __name__ == "__main__":
    loop()
    