from unittest import result

PLAN = [{
    "content": "这一步是干嘛的",
    "activeFrom":"当它正在进行中时，可以用更自然的进行时描述",
    "state": "pending" | "in_process" | "completed"
}]

PLAN_ITEMS = {
    "items":PLAN,
    "rounds_since_update":0
}

def __init__(self):
    self.items = []

# 新增任务，更新任务列表
def update(self,task : list) ->str:
    validated = []
    in_process_count = 0
    for index in task:
        status = index.get("state")
        if status == "in_process":
            in_process_count += 1
        validated.append({
            "content": index["content"],
            "activeFrom": index.get("activeFrom",""),
            "state": status
        })
        if in_process_count > 1:
            return "只能有一个正在进行的任务"
        self.items = validated
        if in_process_count >=3:
            result.insert(0,{"type":"text","content":"任务列表过长，请精简"})
        return  self.render()

def render(self) -> str:
    lines=[]
    for index in self.items:
        marker = {
            "pending": "[]",
            "in_process": "[>]",
            "completed": "[x]"
        }[index["state"]]
        lines.append(f"{marker} {index['content']}")
        return "\n".join(lines)

