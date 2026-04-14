import json
from openai import OpenAI
from com.agent.tools import execute_tool_call

client = OpenAI(base_url="https://coding.dashscope.aliyuncs.com/v1", api_key="sk-sp-e2ff9873c897446da7d7e0e78437c13d")
MODEL = "qwen3.5-plus"

messages = [{"role":"system","content":"You are a helpful assistant."},{"role": "user", "content": "看下我今天的安排"}]




def loop():
   while True:
       response = client.chat.completions.create(
           model=MODEL,
           messages=messages,
           tools=TOOLS,
           temperature=0.7,
           max_tokens=8000
       )
       msg = response.choices[0].message
       tool_calls = msg.tool_calls or []

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

if __name__ == "__main__":
    loop()
    