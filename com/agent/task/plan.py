ALLOWED_STATES = {"pending", "in_progress", "completed"}

PLAN_ITEMS = {
    "items": [],
    "rounds_since_update": 0,
}


def update(task: list):
    validated = []
    in_progress_count = 0

    for item in task:
        status = item.get("status", "pending")
        if status not in ALLOWED_STATES:
            return {"error": f"invalid status: {status}"}

        if status == "in_progress":
            in_progress_count += 1

        validated.append(
            {
                "content": item.get("content", "").strip(),
                "activeForm": item.get("activeForm", ""),
                "status": status,
            }
        )

    if in_progress_count > 1:
        return {"error": "只能有一个 in_progress 任务"}

    PLAN_ITEMS["items"] = validated
    PLAN_ITEMS["rounds_since_update"] = 0
    return {"ok": True, "plan_text": render(), "items": validated}


def render() -> str:
    lines = []
    for item in PLAN_ITEMS["items"]:
        marker = {
            "pending": "[]",
            "in_progress": "[>]",
            "completed": "[x]",
        }[item["status"]]
        lines.append(f"{marker} {item['content']}")
    return "\n".join(lines)

