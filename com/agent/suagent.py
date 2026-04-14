from com.agent.Loop import client, MODEL, messages
from com.agent.tools import TOOLS

messages = [{"role":"system","content":"You are a helpful assistant."},{"role": "user", "content": "看下我今天的安排"}]

def createSubAgent(self) :
    response = client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=TOOLS,
        temperature=0.7,
        max_tokens=8000
    )
    msg = response.choices[0].message
    tool_calls = msg.tool_calls or []
