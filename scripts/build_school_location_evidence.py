#!/usr/bin/env python3
"""Build a complete, source-auditable location status inventory.

This deliberately does not infer a city from a name.  A row is a city mapping
only when it already has an evidence-backed entry in school_cities.json.
Unmapped WakeUp/import rows remain explicit unresolved records so coverage is
measurable without turning a parent, campus, or historical name into a false
location.
"""
from __future__ import annotations

import argparse
import json
import hashlib
from collections import Counter
from pathlib import Path
from catalog_url_safety import audit

ROOT = Path(__file__).resolve().parents[1]


def load(path: Path):
    value = json.loads(path.read_text(encoding="utf-8"))
    findings = audit(value, path.name)
    if findings:
        raise ValueError("Unsanitized source URL parameters: " + str(findings))
    return value


def classify(name: str, official: dict) -> tuple[str, str]:
    row = official.get(name)
    if row and row.get("category") == "adult" and not row.get("city"):
        return "official_no_city", "MOE 2026 adult directory has no city field"
    if "研究生" in name:
        return "graduate_relation_unverified", "graduate entry requires explicit parent identity and current teaching location"
    if any(x in name for x in ("原", "旧", "历史", "2017", "2023届", "2024新版")):
        return "historical_or_alias_unverified", "historical or renamed entry requires dated institutional evidence"
    if any(x in name for x in ("校区", "研究院", "学院", "分校", "备用", "个人数据中心")):
        return "campus_or_import_unverified", "campus/import entry requires an official address for this exact entity"
    return "legacy_unresolved", "no exact-name location source in the frozen research snapshot"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", type=Path, default=ROOT / "scripts/data/school_location_evidence.json")
    ap.add_argument("--doc", type=Path, default=ROOT / "docs/catalog/location-evidence.md")
    args = ap.parse_args()
    catalog = load(ROOT / "app/src/main/assets/schools.json")
    cities_doc = load(ROOT / "app/src/main/assets/school_cities.json")
    official = {x["name"]: x for x in load(ROOT / "scripts/data/official_schools.json")}
    city_map = cities_doc["schools"]
    city_evidence = cities_doc.get("_cityEvidence", {})
    city_sources = cities_doc.get("_citySources", {})
    research_path = ROOT / "scripts/data/school_location_research.json"
    research = load(research_path)
    research_by_name = {row["name"]: row for row in research["entries"]}
    records = []
    names = [row["name"] for row in catalog]
    if len(names) != len(set(names)):
        raise ValueError("catalogue contains duplicate names")
    for row in catalog:
        name = row["name"]
        if name in city_map:
            ev = city_evidence.get(name, {})
            source_id = ev.get("sourceId")
            source = city_sources.get(source_id, {})
            canonical = cities_doc.get("_nameMappings", {}).get(name, name)
            official_row = official.get(canonical, {})
            override = cities_doc.get("_campusOverrides", {}).get(canonical, {})
            supplement = cities_doc.get("_supplements", {}).get(name, {})
            urls = override.get("sources") or ([supplement["source"]] if supplement.get("source") else [])
            source_url = source.get("url") or official_row.get("source_url")
            if not urls and source_url:
                urls = [source_url]
            records.append({"name": name, "status": "mapped", "cities": city_map[name],
                            "precision": "city", "sourceId": source_id or official_row.get("source_id"),
                            "sourceUrl": urls[0] if urls else None, "sourceUrls": urls,
                            "sourceDate": source.get("sourceDate") or official_row.get("source_date"),
                            "canonicalName": canonical,
                            "observedText": source.get("observedText") or override.get("reason"),
                            "relationEvidence": source.get("relationEvidence"),
                            "coverage": source.get("coverage", "recorded-city-mapping"),
                            "coverageNote": source.get("coverageNote"),
                            "retrievedDate": source.get("retrievedDate"),
                            "bodySha256": source.get("bodySha256"),
                            "evidenceTextSha256": source.get("evidenceTextSha256"),
                            "reason": "existing city mapping; explicit source/override metadata retained"})
        else:
            status, reason = classify(name, official)
            records.append({"name": name, "status": status, "cities": None,
                            "precision": "none", "sourceId": (official.get(name) or {}).get("source_id"),
                            "sourceUrl": (official.get(name) or {}).get("source_url"),
                            "sourceDate": (official.get(name) or {}).get("source_date"),
                            "reason": reason,
                            "researchEvidence": {"path": "scripts/data/school_location_research.json",
                                                 "name": name,
                                                 "searchCount": len(research_by_name.get(name, {}).get("searches", [])),
                                                 "probeCount": len(research_by_name.get(name, {}).get("probes", []))}})
    missing_research = [r["name"] for r in records if r["status"] != "mapped" and r["name"] not in research_by_name]
    if missing_research:
        raise ValueError("Missing unresolved research records: " + str(missing_research))
    records.sort(key=lambda x: x["name"])
    counts = Counter(x["status"] for x in records)
    coverage_counts = Counter(r.get("coverage", "unresolved") for r in records)
    input_paths = ["app/src/main/assets/schools.json", "app/src/main/assets/school_cities.json",
                   "scripts/data/official_schools.json", "scripts/data/school_location_verified.json",
                   "scripts/data/school_location_research.json"]
    inputs = [{"path": p, "sha256": hashlib.sha256((ROOT / p).read_bytes()).hexdigest()} for p in input_paths]
    payload = {"schemaVersion": 1, "generatedAt": "2026-10-15", "catalogCount": len(records),
               "cityMappedCount": counts["mapped"], "unresolvedCount": len(records) - counts["mapped"],
               "coverageCounts": dict(sorted(coverage_counts.items())), "inputs": inputs,
               "statusCounts": dict(sorted(counts.items())), "records": records}
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# Catalogue Location Evidence", "", "Generated by `scripts/build_school_location_evidence.py`; evidence snapshot 2026-10-15.",
             "", f"Every one of the {len(records):,} catalogue names has one record. `mapped` entries are the existing city map and retain their source metadata. Unmapped entries remain null until an exact institution/address source verifies them; names are never used as a city inference.", "",
             "| Status | Count | Meaning |", "|---|---:|---|"]
    meanings = {"mapped": "Existing evidence-backed city mapping", "official_no_city": "MOE adult record has no city field", "graduate_relation_unverified": "Graduate relation/location needs explicit evidence", "historical_or_alias_unverified": "Historical/renamed entry needs dated evidence", "campus_or_import_unverified": "Campus or import variant needs exact address", "legacy_unresolved": "No exact source found in frozen snapshot"}
    for status, count in sorted(counts.items()): lines.append(f"| `{status}` | {count} | {meanings[status]} |")
    lines += ["", "The report intentionally distinguishes catalogue participation from city coverage: adult institutions with a null Ministry city and unresolved WakeUp variants participate in the audit but are not assigned a guessed city.", ""]
    lines += ["A mapped institution has at least one recorded city. This does not imply every campus is covered or that campus coordinates are known. Graduate-office records identify the published office or training-unit city only. Per-record coverage notes and input hashes are retained in the JSON inventory.", ""]
    lines += ["Actual search queries, result URLs, probe outcomes and source snapshot hashes are recorded in school_location_research.json for unresolved entries. Search results and HTTP success are research traces, not verified address evidence.", ""]
    args.doc.write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({"catalogCount": len(records), "statusCounts": dict(sorted(counts.items()))}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
