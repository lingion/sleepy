#!/usr/bin/env python3
"""Extract unique repos from C*_p*.json code search results into candidates.json."""
import json
import glob
import os
import re
from collections import defaultdict

WORK = os.path.dirname(os.path.abspath(__file__))

# Map: filename prefix → query keyword
QUERY_BY_PREFIX = {
    "C1": "NewUrpClassListItem",
    "C2": "NewUrpSuperParser",
    "C3": "新 URP",
    "C4": "jwxt 新 URP 课表",
    "C5": "skzc jxlm jasm",
    "C6": "jxlm jasm 课表",
    "C7": "新正方教务 课表",
    "C8": "正方 URP 课表",
    "C9": "jwxt URP class",
    "C10": "jsxsd new",
    "C11": "新 URP 教务 课表",
    "C12": "NewUrp timetable",
}

KEYWORDS_RE = re.compile(
    r"(?i)(new[_\- ]?urp|skzc|jxlm|jasm|newurpclass|新\s*urp|新\s*正方|jsm|kcm|cxjc)",
)

repos = defaultdict(lambda: {
    "queries": set(),
    "html_url": "",
    "stargazers": 0,
    "language": "",
    "description": "",
    "files_matched": [],
})

for path in sorted(glob.glob(os.path.join(WORK, "C*_p*.json"))):
    fname = os.path.basename(path)
    m = re.match(r"(C\d+)_p\d+", fname)
    if not m:
        continue
    prefix = m.group(1)
    query = QUERY_BY_PREFIX.get(prefix, prefix)
    try:
        data = json.load(open(path, "r", encoding="utf-8"))
    except Exception as e:
        print(f"skip {fname}: {e}")
        continue
    items = data.get("items", []) or []
    for item in items:
        repo = item.get("repository") or {}
        full_name = repo.get("full_name")
        if not full_name:
            continue
        key = full_name
        repos[key]["queries"].add(query)
        if not repos[key]["html_url"]:
            repos[key]["html_url"] = repo.get("html_url", "")
        if not repos[key]["stargazers"]:
            repos[key]["stargazers"] = repo.get("stargazers_count", 0) or 0
        if not repos[key]["language"]:
            repos[key]["language"] = repo.get("language") or ""
        if not repos[key]["description"]:
            repos[key]["description"] = (repo.get("description") or "")[:300]
        path_str = item.get("path") or item.get("name") or ""
        kw_match = KEYWORDS_RE.findall((item.get("name", "") + " " + path_str).lower())
        if path_str and len(repos[key]["files_matched"]) < 5:
            repos[key]["files_matched"].append(path_str)

candidates = []
for full_name, info in sorted(repos.items(), key=lambda kv: (-kv[1]["stargazers"], kv[0])):
    candidates.append({
        "query": "; ".join(sorted(info["queries"])),
        "full_name": full_name,
        "html_url": info["html_url"],
        "stargazers": info["stargazers"],
        "language": info["language"],
        "description": info["description"],
        "source": "gh api code search",
        "files_matched": info["files_matched"][:5],
    })

out = {
    "metadata": {
        "generated_at": "2026-10-07",
        "queries_used": list(set(QUERY_BY_PREFIX.values())),
        "unique_candidates": len(candidates),
        "code_search_pages_per_query": 3,
    },
    "candidates": candidates,
}
with open(os.path.join(WORK, "..", "candidates.json"), "w", encoding="utf-8") as f:
    json.dump(out, f, indent=2, ensure_ascii=False)

print(f"wrote {len(candidates)} unique candidates to candidates.json")
print("top 10 by stars:")
for c in candidates[:10]:
    print(f"  {c['stargazers']:>5}★  {c['full_name']:<55}  {c['language']:<12}  {c['description'][:60]}")
