import re
from pathlib import Path

SKILLS = []
SKILL_REGISTRY = {}
SKILLS_DIR = Path(__file__).resolve().parents[1] / "skills"


def _parse_front_matter(raw: str) -> tuple[dict[str, str], str]:
    """解析开头的 YAML front matter（简单 key: value 行），返回 (字段, 正文)."""
    if not raw.lstrip().startswith("---"):
        return {}, raw
    lines = raw.splitlines()
    if not lines or lines[0].strip() != "---":
        return {}, raw
    fields: dict[str, str] = {}
    i = 1
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if stripped == "---":
            body = "\n".join(lines[i + 1 :])
            return fields, body
        if stripped and not stripped.startswith("#") and ":" in stripped:
            key, _, rest = stripped.partition(":")
            key = key.strip()
            val = rest.strip().strip('"').strip("'")
            if key:
                fields[key] = val
        i += 1
    return {}, raw


def _strip_inline_md(text: str) -> str:
    """去掉常见 Markdown 标记，便于生成简短 description。"""
    s = text.strip()
    s = re.sub(r"^>\s*", "", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"\1", s)
    s = re.sub(r"\*([^*]+)\*", r"\1", s)
    s = re.sub(r"`([^`]+)`", r"\1", s)
    s = re.sub(r"^\s*[-*+]\s+", "", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def _is_noise_line(line: str) -> bool:
    t = line.strip()
    if not t:
        return True
    if t.startswith("#"):
        return True
    if t.startswith("```"):
        return True
    if t.startswith("|"):
        return True
    if t.startswith("<!--"):
        return True
    if t in ("---", "***", "**", "*", "`"):
        return True
    if re.fullmatch(r"[*\-_`:~\s]+", t):
        return True
    if re.fullmatch(r"-{3,}", t) or re.fullmatch(r"\*{3,}", t):
        return True
    return False


def _first_heading_title(body: str) -> str | None:
    for line in body.splitlines():
        text = line.strip()
        if text.startswith("#"):
            title = text.lstrip("#").strip()
            if title:
                return _strip_inline_md(title)
    return None


def _first_summary_line(body: str, max_len: int = 200) -> str | None:
    for line in body.splitlines():
        if _is_noise_line(line):
            continue
        cleaned = _strip_inline_md(line)
        if not cleaned or len(cleaned) < 2:
            continue
        if cleaned in ("---", "**", "*"):
            continue
        return cleaned[:max_len]
    return None


def _extract_name_description(md_path: Path):
    content = md_path.read_text(encoding="utf-8")
    fm, body = _parse_front_matter(content)

    name = (fm.get("name") or "").strip() or md_path.parent.name or md_path.stem
    description = (fm.get("description") or "").strip()

    if not description:
        description = _first_summary_line(body) or ""

    if not description:
        description = f"Read skill: {name}"

    title = _first_heading_title(body)
    if not (fm.get("name") or "").strip() and title:
        name = title

    description = description[:200]

    return name, description, content


def register_skill(name, description, path):
    item = {"name": name, "description": description, "path": str(path)}
    SKILLS.append(item)
    SKILL_REGISTRY[name] = item


def load():
    SKILLS.clear()
    SKILL_REGISTRY.clear()
    if not SKILLS_DIR.exists():
        print(f"[trace][skills] skills dir not found: {SKILLS_DIR}")
        return SKILLS

    print(f"[trace][skills] loading markdown skills from: {SKILLS_DIR}")
    for md_file in sorted(SKILLS_DIR.rglob("*.md")):
        if "templates" in md_file.parts:
            continue
        if md_file.name.lower() == "readme.md":
            continue
        name, description, content = _extract_name_description(md_file)
        register_skill(name, description, md_file)
        SKILL_REGISTRY[name]["content"] = content
        print(f"[trace][skills] loaded: {name} ({md_file.name})")
    print(f"[trace][skills] total loaded: {len(SKILLS)}")
    return SKILLS


def load_skill(name):
    item = SKILL_REGISTRY.get(name)
    if not item:
        print(f"[trace][skills] load_skill miss: {name}")
        return {"error": f"skill not found: {name}"}
    print(f"[trace][skills] load_skill hit: {name}")
    return {
        "name": item["name"],
        "description": item["description"],
        "content": item.get("content", ""),
    }


def get_skill_summaries():
    return [{"name": item["name"], "description": item["description"]} for item in SKILLS]