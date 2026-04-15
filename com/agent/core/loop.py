import json
import os
import sys
from pathlib import Path

try:
    from com.agent.core import context
    from com.agent.core.client_config import client, MODEL
    from com.agent.core.tools import execute_tool_call, TOOLS
    from com.agent.core.skills import get_skill_summaries
except ModuleNotFoundError:
    # 兼容直接运行该文件（非 -m 模式）
    project_root = Path(__file__).resolve().parents[3]
    if str(project_root) not in sys.path:
        sys.path.insert(0, str(project_root))
    from com.agent.core import context
    from com.agent.core.client_config import client, MODEL
    from com.agent.core.tools import execute_tool_call, TOOLS
    from com.agent.core.skills import get_skill_summaries
skill_summaries = get_skill_summaries()
skills_prompt = "当前可用技能列表:\n" + "\n".join(
    [f"- {item['name']}: {item['description']}" for item in skill_summaries]
) if skill_summaries else "当前无可用技能文档。"

# 与 Claude Code 类似：摘要进上下文；启用技能后应把 SKILL 正文当作待办流程跑完，而不是只对话一轮就停。
SYSTEM_BASE = (
    "你是个人助手，可以通过技能列表与工具完成相关任务。\n"
    "**技能执行**：当任务匹配某技能时，先调用 load_skill 加载该技能全文；加载后必须按文档中的步骤顺序执行到底。"
    "若技能要求执行脚本、命令行或查询，应使用 bash 等工具实际执行并依据输出继续，直到得到技能所要求的最终结果。"
    "禁止在仅复述技能要点、仅表示「已了解」或尚未产出最终结果时停止；若上一步工具输出仍不足以交付结果，必须继续调用工具或推理。\n"
    "**子 Agent**：需要隔离上下文、只读规划、或受限工具集时，调用 run_subagent（fresh=新会话；fork=带主对话快照）。"
    "子任务完成后根据返回的 final_content 向用户汇总；不要假设子 Agent 已自动修改主会话。"
)
messages = [
    {"role": "system", "content": SYSTEM_BASE},
    {"role": "system", "content": skills_prompt},
    {"role": "user", "content": "查询软件工程部未提交工时，不需要确认"},
]

# 每轮「模型请求 API」计数，防止续写死循环
max_llm_rounds = int(os.getenv("MAX_LLM_ROUNDS", "40"))
# 含工具的多轮对话上限（仅在有 tool_calls 时递增，兼容旧语义）
max_count = int(os.getenv("MAX_TURNS", "20"))
# load_skill 成功后，若模型连续多轮只回复文字不调工具，自动插入「请继续」推动执行（对齐「技能注入后仍继续干活」）
max_skill_nudges = int(os.getenv("MAX_SKILL_NUDGES", "8"))

count = 0
llm_round = 0
_skill_loaded_ok = False
_no_tool_streak_after_skill = 0

_SKILL_CONTINUE_USER = (
    "请继续：严格按已加载技能中的步骤执行。需要脚本/终端/查询时务必调用 bash（或相关工具）并基于输出推进，"
    "直到完成技能要求的交付物（例如表格、汇总或提交结果）；若当前步骤未完成，不要结束。"
)


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
   global count, llm_round, _skill_loaded_ok, _no_tool_streak_after_skill
   count = 0
   llm_round = 0
   _skill_loaded_ok = False
   _no_tool_streak_after_skill = 0
   session_id = context.ensure_loop_session(messages)
   tool_names = [tool.get("function", {}).get("name", "<unknown>") for tool in TOOLS]
   print(
       f"[trace][loop] start, session_id={session_id}, max_llm_rounds={max_llm_rounds}, "
       f"max_tool_rounds={max_count}, max_skill_nudges={max_skill_nudges}, tools={tool_names}"
   )
   print(f"[trace][loop] skill summaries loaded: {len(skill_summaries)}")
   for item in skill_summaries:
       print(f"[trace][loop] skill: {item['name']} - {item['description']}")
   while True:
       print_messages()
       context.maybe_compress(session_id)
       llm_round += 1
       if llm_round > max_llm_rounds:
           print("max_llm_rounds reached")
           break
       print(f"[trace][loop] llm_round={llm_round}, tool_round={count + 1}")
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

       # 先把 assistant 消息（包含 tool_calls）放进上下文
       messages.append(msg.model_dump())

       if tool_calls:
           _no_tool_streak_after_skill = 0
           if count >= max_count:
               print("max tool rounds reached before executing pending tools")
               break
           for tool_call in tool_calls:
               result = execute_tool_call(tool_call)
               print(f"[mock tool] {tool_call.function.name} -> {result}")
               if tool_call.function.name == "load_skill" and isinstance(result, dict) and "error" not in result:
                   _skill_loaded_ok = True
                   _no_tool_streak_after_skill = 0
               messages.append({
                   "role": "tool",
                   "tool_call_id": tool_call.id,
                   "content": json.dumps(result, ensure_ascii=False)
               })
           count = count + 1
           continue

       # 无 tool_calls：参照 Claude Code「技能注入后仍继续执行」——在已成功 load_skill 后，用有限次 user 续推，避免只聊一句就结束
       if _skill_loaded_ok:
           _no_tool_streak_after_skill += 1
           if _no_tool_streak_after_skill <= max_skill_nudges:
               print(
                   f"[trace][loop] skill active, no tools this round; nudge {_no_tool_streak_after_skill}/{max_skill_nudges}"
               )
               messages.append({"role": "user", "content": _SKILL_CONTINUE_USER})
               continue

       print(msg.content)
       break

if __name__ == "__main__":
    loop()
    