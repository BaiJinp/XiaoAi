from pathlib import Path

SKILLS = []
SKILL_REGISTRY = {}
SKILLS_DIR = Path(__file__).resolve().parents[1] / "skills"


def _extract_name_description(md_path: Path):
    content = md_path.read_text(encoding="utf-8")
    lines = content.splitlines()

    name = md_path.stem
    description = f"Read skill: {name}"

    for line in lines:
        text = line.strip()
        if text.startswith("#"):
            header = text.lstrip("#").strip()
            if header:
                name = header
                break

    for line in lines:
        text = line.strip()
        if not text or text.startswith("#"):
            continue
        description = text[:120]
        break

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