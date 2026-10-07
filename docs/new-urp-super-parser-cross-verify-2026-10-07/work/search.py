#!/usr/bin/env python3
"""Search GitHub for repos implementing the NewUrp flat-array protocol."""
from __future__ import annotations

import json
import subprocess
import sys
import time
from pathlib import Path
from urllib.parse import quote_plus

ROOT = Path("/Users/lingion_k/sleepy/docs/new-urp-super-parser-cross-verify-2026-10-07")
WORK = ROOT / "work"
WORK.mkdir(parents=True, exist_ok=True)
OUT = ROOT / "candidates.json"

ACCEPT = "Accept: application/vnd.github.v3.text-match+json"

QUERIES = [
    {"id": "C1", "type": "code", "q": "NewUrpClassListItem", "tokens": ["NewUrpClassListItem"]},
    {"id": "C2", "type": "code", "q": "NewUrpSuperParser", "tokens": ["NewUrpSuperParser"]},
    {"id": "C3", "type": "code", "q": "skzc jxlm jasm", "tokens": ["skzc", "jxlm", "jasm"]},
    {"id": "C4", "type": "code", "q": "cxjc skzc", "tokens": ["cxjc", "skzc"]},
    {"id": "C5", "type": "code", "q": "skxq skzc jxlm", "tokens": ["skxq", "skzc", "jxlm"]},
    {"id": "C6", "type": "code", "q": "kcm jsm jxlm jasm", "tokens": ["kcm", "jsm", "jxlm", "jasm"]},
    {"id": "C7", "type": "code", "q": "skzc selectCourseList", "tokens": ["skzc", "selectCourseList"]},
    {"id": "C8", "type": "code", "q": "id skxq skzc jasm", "tokens": ["skxq", "skzc", "jasm"]},
    {"id": "C9", "type": "code", "q": "新 URP 课表", "tokens": ["新", "URP", "课表"]},
    {"id": "C10", "type": "code", "q": "NewUrpClassListItem language:Kotlin OR language:Java OR language:Python", "tokens": ["NewUrpClassListItem"]},
    {"id": "C11", "type": "code", "q": "skzc jasm language:Kotlin", "tokens": ["skzc", "jasm"]},
    {"id": "C12", "type": "code", "q": "jwxt URP skzc", "tokens": ["URP", "skzc"]},
    {"id": "R1", "type": "repo", "q": "URP jwxt course language:Java language:Kotlin language:Python", "tokens": ["URP", "jwxt", "course"]},
    {"id": "R2", "type": "repo", "q": "new-urp course", "tokens": ["new-urp", "course"]},
    {"id": "R3", "type": "repo", "q": "URP 课表", "tokens": ["URP", "课表"]},
    {"id": "R4", "type": "repo", "q": "正方 URP 课表", "tokens": ["正方", "URP", "课表"]},
    {"id": "R5", "type": "repo", "q": "jwxt URP class", "tokens": ["jwxt", "URP", "class"]},
    {"id": "R6", "type": "repo", "q": "jsxsd new", "tokens": ["jsxsd"]},
    {"id": "R7", "type": "repo", "q": "新 URP 教务 课表", "tokens": ["新", "URP", "教务", "课表"]},
    {"id": "R8", "type": "repo", "q": "NewUrp timetable", "tokens": ["NewUrp"]},
    {"id": "R9", "type": "repo", "q": "jxlm jasm", "tokens": ["jxlm", "jasm"]},
    {"id": "R10", "type": "repo", "q": "skzc jxlm", "tokens": ["skzc", "jxlm"]},
]


def gh_api(path):
    try:
        result = subprocess.run(
            ["gh", "api", "-H", ACCEPT, path],
            capture_output=True, text=True, timeout=90,
        )
    except subprocess.TimeoutExpired:
        print(f"    TIMEOUT", file=sys.stderr)
        return {"__network__": True}
    if result.returncode != 0:
        stderr = result.stderr.strip()[:400]
        print(f"    FAIL: {stderr}", file=sys.stderr)
        low = result.stderr.lower()
        if "rate limit" in low or "secondary rate limit" in low or "abuse" in low:
            return {"__ratelimited__": True, "stderr": stderr}
        if "tls handshake" in low or "timeout" in low or "connection reset" in low or "i/o timeout" in low:
            return {"__network__": True, "stderr": stderr}
        if "not found" in low:
            return {"__notfound__": True, "stderr": stderr}
        return None
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as e:
        print(f"    JSON parse fail: {e}", file=sys.stderr)
        return None


def fetch_with_retry(path, max_retries=4, base_sleep=12.0):
    for attempt in range(max_retries + 1):
        data = gh_api(path)
        if data is None:
            return None
        if isinstance(data, dict) and data.get("__ratelimited__"):
            wait = base_sleep * (attempt + 1)
            print(f"    rate-limited, sleeping {wait:.0f}s (attempt {attempt + 1}/{max_retries})", file=sys.stderr)
            time.sleep(wait)
            continue
        if isinstance(data, dict) and data.get("__network__"):
            wait = base_sleep * (attempt + 1)
            print(f"    network err, sleeping {wait:.0f}s (attempt {attempt + 1}/{max_retries})", file=sys.stderr)
            time.sleep(wait)
            continue
        if isinstance(data, dict) and data.get("__notfound__"):
            return None
        return data
    return None


def run_query(q, max_pages=3):
    out = []
    total = 0
    is_code = q["type"] == "code"
    # code_search bucket is 10/min, so throttle harder for code queries
    inter_page_sleep = 7.0 if is_code else 3.0
    for page in range(1, max_pages + 1):
        path = f"/search/{q['type']}?q={quote_plus(q['q'])}&per_page=100&page={page}"
        cache_path = WORK / f"{q['id']}_p{page}.json"
        if cache_path.exists():
            try:
                data = json.loads(cache_path.read_text())
            except json.JSONDecodeError:
                data = None
        else:
            data = fetch_with_retry(path, base_sleep=15 if is_code else 10)
            if data is not None and not (isinstance(data, dict) and data.get("__ratelimited__")):
                cache_path.write_text(json.dumps(data, ensure_ascii=False))
            elif data is None:
                cache_path.write_text(json.dumps({"__error__": True}, ensure_ascii=False))

        if data is None or (isinstance(data, dict) and (data.get("__ratelimited__") or data.get("__error__"))):
            break

        items = data.get("items", [])
        if page == 1:
            total = data.get("total_count", 0)

        for item in items:
            if q["type"] == "code":
                tokens = set()
                for tm in item.get("text_matches", []) or []:
                    for m in tm.get("matches", []) or []:
                        t = m.get("text")
                        if t:
                            tokens.add(t)
                repo = item.get("repository") or {}
                fn = repo.get("full_name") or ""
                out.append({
                    "full_name": fn,
                    "matched_tokens": tokens,
                    "meta": {
                        "description": repo.get("description"),
                        "stars": repo.get("stargazers_count", 0) or 0,
                        "html_url": repo.get("html_url"),
                        "fork": repo.get("fork", False),
                    },
                    "source_query_id": q["id"],
                })
            else:
                lic = item.get("license")
                lic_id = (lic or {}).get("spdx_id") if lic else None
                fn = item.get("full_name") or ""
                out.append({
                    "full_name": fn,
                    "matched_tokens": set(q["tokens"]),
                    "meta": {
                        "description": item.get("description"),
                        "stars": item.get("stargazers_count", 0) or 0,
                        "language": item.get("language"),
                        "default_branch": item.get("default_branch"),
                        "archived": item.get("archived", False),
                        "license": lic_id,
                        "pushed_at": item.get("pushed_at"),
                        "html_url": item.get("html_url"),
                        "fork": item.get("fork", False),
                        "topics": item.get("topics", []),
                    },
                    "source_query_id": q["id"],
                })

        if len(items) < 100:
            break
        time.sleep(inter_page_sleep)

    return out, total


def fetch_full_repo(full_name):
    path = f"/repos/{full_name}"
    cache_path = WORK / f"repo_{full_name.replace('/', '__')}.json"
    if cache_path.exists():
        try:
            data = json.loads(cache_path.read_text())
            if data and not (data.get("__error__") or data.get("__ratelimited__") or data.get("__network__") or data.get("__notfound__")):
                return data
        except json.JSONDecodeError:
            pass

    data = fetch_with_retry(path, max_retries=4, base_sleep=20)
    if data is None:
        cache_path.write_text(json.dumps({"__error__": True}))
        return None
    if isinstance(data, dict) and (data.get("__ratelimited__") or data.get("__network__") or data.get("__notfound__")):
        # leave cache empty so we retry next run
        return None
    cache_path.write_text(json.dumps(data, ensure_ascii=False))
    return data


def main():
    candidates = {}
    query_totals = {}
    query_status = {}

    for q in QUERIES:
        kind = q["type"].upper()
        print(f"[{q['id']}] ({kind}) q={q['q']!r}", file=sys.stderr)
        items, total = run_query(q)
        for it in items:
            it["matched_tokens"] = set(it["matched_tokens"]) | set(q["tokens"])

        for it in items:
            fn = it["full_name"]
            if not fn:
                continue
            if fn not in candidates:
                candidates[fn] = {
                    "repo": fn,
                    "search_hits": set(),
                    "search_query_ids": set(),
                    **it["meta"],
                }
            candidates[fn]["search_hits"] |= it["matched_tokens"]
            candidates[fn]["search_query_ids"].add(q["id"])
            if not candidates[fn].get("description") and it["meta"].get("description"):
                candidates[fn]["description"] = it["meta"]["description"]

        query_totals[q["id"]] = total
        query_status[q["id"]] = "ok"

        # Sleep between queries to avoid secondary rate limit on the heavier endpoint
        time.sleep(7 if q["type"] == "code" else 3)

    print(f"\nTotal unique candidates: {len(candidates)}\n", file=sys.stderr)

    URP_TOKENS = {"NewUrpClassListItem", "NewUrpSuperParser", "kcm", "jsm", "jxlm", "jasm", "skzc", "skxq", "cxjc", "skjc", "zxjxjhh", "selectCourseList"}
    HIGH_PRIO_QUERIES = {"C1", "C2", "C7", "C10", "C11"}

    # Decide which candidates need full /repos/ back-fill
    missing = []
    for c in candidates.values():
        # Already have full data from repo search
        already_complete = (c.get("default_branch") is not None
                          and c.get("archived") is not None
                          and c.get("license") is not None
                          and c.get("language") is not None)
        urp_hits = c.get("search_hits", set()) & URP_TOKENS
        has_high_prio = bool(set(c.get("search_query_ids", set())) & HIGH_PRIO_QUERIES)
        relevant = (len(urp_hits) >= 2
                    or has_high_prio
                    or (c.get("stars", 0) or 0) >= 1)
        if not already_complete and relevant:
            missing.append(c["repo"])

    print(f"Back-filling {len(missing)} high-priority candidates via /repos/...\n", file=sys.stderr)
    for fn in missing:
        data = fetch_full_repo(fn)
        if not data:
            continue
        lic = data.get("license")
        lic_id = (lic or {}).get("spdx_id") if lic else None
        c = candidates[fn]
        c["description"] = c.get("description") or data.get("description")
        c["stars"] = c.get("stars") or data.get("stargazers_count", 0) or 0
        c["language"] = c.get("language") or data.get("language")
        c["default_branch"] = c.get("default_branch") or data.get("default_branch")
        c["archived"] = c.get("archived") if c.get("archived") is not None else data.get("archived", False)
        c["license"] = c.get("license") or lic_id
        c["pushed_at"] = c.get("pushed_at") or data.get("pushed_at")
        c["html_url"] = c.get("html_url") or data.get("html_url")
        time.sleep(0.7)

    def sort_key(c):
        return (
            -len(c["search_query_ids"]),
            -(c.get("stars", 0) or 0),
            c["repo"].lower(),
        )
    sorted_candidates = sorted(candidates.values(), key=sort_key)

    output = {
        "metadata": {
            "parser_name": "NewUrpSuperParser (o0O0o variant)",
            "scope": ("Flat-array variant: JSON ARRAY of items with fields "
                      "kcm, jsm, id.{skxq,skjc,skzc,zxjxjhh}, cxjc, jxlm, jasm; "
                      "id.skzc is a 0/1 bitmap string of length N; "
                      "endSection = skjc + cxjc - 1."),
            "source_of_truth": "WakeUp smali NewUrpSuperParser + obfuscated o0O0o + NewUrpClassListItem",
            "search_queries": QUERIES,
            "query_totals": query_totals,
            "query_status": query_status,
            "candidate_count": len(sorted_candidates),
            "archived_count": sum(1 for c in sorted_candidates if c.get("archived")),
            "with_license": sum(1 for c in sorted_candidates if c.get("license")),
            "queries_run_at": "2026-10-07",
            "tooling": "gh api (no curl), Python 3, GitHub text-match JSON Accept header",
            "rate_limit_handling": "Backoff up to 24s for primary rate limit, 60s for secondary",
        },
        "candidates": [
            {
                "repo": c["repo"],
                "stars": c.get("stars", 0) or 0,
                "default_branch": c.get("default_branch"),
                "license": c.get("license"),
                "archived": c.get("archived", False),
                "description": c.get("description"),
                "language": c.get("language"),
                "search_hits": sorted(c.get("search_hits", set())),
                "search_query_ids": sorted(c.get("search_query_ids", set())),
                "pushed_at": c.get("pushed_at"),
                "html_url": c.get("html_url"),
                "fork": c.get("fork", False),
            }
            for c in sorted_candidates
        ],
    }

    OUT.write_text(json.dumps(output, indent=2, ensure_ascii=False))
    print(f"Wrote {OUT}", file=sys.stderr)
    print(f"Total candidates: {output['metadata']['candidate_count']}", file=sys.stderr)
    print(f"Archived: {output['metadata']['archived_count']}", file=sys.stderr)
    print(f"With license: {output['metadata']['with_license']}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())