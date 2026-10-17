#!/usr/bin/env python3
"""Add evidence-backed locations from the normalized official school catalog.

Usage:
  python3 scripts/enrich_school_cities.py

Existing city entries and overrides are preserved byte-for-value unless a
reviewed additive extension supplies an exact baseline snapshot. The companion
location evidence inventory covers every catalog name; this file adds only
entries with explicit city evidence, never a city inferred from a name.
"""
from __future__ import annotations

import argparse
import json
import hashlib
from copy import deepcopy
from urllib.parse import urlparse
from collections import Counter
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "app/src/main/assets/school_cities.json"


def load_json(path: Path) -> Any:
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


REVIEWED_METHOD = "reviewed-exact-entity-address"
EXTENSION_METHOD = "reviewed-baseline-city-extension"


def _valid_cities(cities: Any) -> bool:
    return (isinstance(cities, list) and bool(cities) and
            all(isinstance(city, str) and city.strip() and city == city.strip()
                and not city.endswith(("省", "自治区")) for city in cities) and
            len(cities) == len(set(cities)))


def _validate_baseline_extension(result: dict, name: str, snapshot: Any,
                                 cities: Any) -> None:
    """Validate baselineExtension={cities, evidence, source, campusOverride}.

    Nullable metadata fields represent absent per-school records. The original
    source stays in _citySources, including when an extension is withdrawn.
    """
    fields = {"cities", "evidence", "source", "campusOverride"}
    if not isinstance(snapshot, dict) or set(snapshot) != fields:
        raise ValueError(f"invalid baselineExtension snapshot for {name}")
    baseline = snapshot["cities"]
    if (not _valid_cities(baseline) or not _valid_cities(cities) or
            len(cities) <= len(baseline) or cities[:len(baseline)] != baseline):
        raise ValueError(f"baselineExtension must be strictly additive for {name}")
    for field in ("evidence", "source", "campusOverride"):
        if snapshot[field] is not None and not isinstance(snapshot[field], dict):
            raise ValueError(f"invalid baselineExtension {field} for {name}")
    evidence = snapshot["evidence"]
    if evidence and evidence.get("method") in {REVIEWED_METHOD, EXTENSION_METHOD}:
        raise ValueError(f"baselineExtension requires an unmanaged baseline for {name}")
    source_id = (evidence or {}).get("sourceId")
    if source_id is not None and not isinstance(source_id, str):
        raise ValueError(f"invalid baselineExtension sourceId for {name}")
    if ((source_id is None and snapshot["source"] is not None) or
            (source_id is not None and snapshot["source"] is None)):
        raise ValueError(f"baselineExtension source metadata is incomplete for {name}")
    if snapshot["source"] != result.get("_citySources", {}).get(source_id):
        raise ValueError(f"baselineExtension source metadata changed for {name}")
    if snapshot["campusOverride"] != result.get("_campusOverrides", {}).get(name):
        raise ValueError(f"baselineExtension campus metadata changed for {name}")


def apply_verified_locations(result: dict, rows: list, directory_names: set[str]) -> int:
    """Apply exact reviewed entries; never derive locations from a name/parent."""
    if not isinstance(rows, list):
        raise ValueError("verified locations must be a list")
    # Validate saved extensions even when withdrawn: never delete or overwrite a
    # manually changed mapping while restoring a stale importer's baseline.
    for name, item in result.get("_cityEvidence", {}).items():
        if item.get("method") != EXTENSION_METHOD:
            continue
        expected_id = "verified-location-" + hashlib.sha256(name.encode()).hexdigest()[:16]
        generated_source = result.get("_citySources", {}).get(expected_id, {})
        if (item.get("sourceId") != expected_id or
                result["schools"].get(name) != item.get("appliedCities") or
                generated_source.get("baselineExtension") != item.get("baselineExtension") or
                generated_source.get("appliedCities") != item.get("appliedCities")):
            raise ValueError(f"baselineExtension applied mapping changed for {name}")
        _validate_baseline_extension(result, name, item.get("baselineExtension"),
                                     item.get("appliedCities"))
    pending = {}
    for row in rows:
        name = row.get("name") if isinstance(row, dict) else None
        if name not in directory_names:
            raise ValueError(f"verified location is not in directory: {name}")
        if name in pending:
            raise ValueError(f"duplicate verified location: {name}")
        cities = row.get("cities")
        if not _valid_cities(cities):
            raise ValueError(f"invalid city-level locations for {name}")
        url = row.get("sourceUrl")
        parsed = urlparse(url) if isinstance(url, str) else None
        if not parsed or parsed.scheme not in {"https", "http"} or not parsed.hostname:
            raise ValueError(f"missing official address source for {name}")
        if not isinstance(row.get("observedText"), str) or not row["observedText"].strip():
            raise ValueError(f"missing address evidence text for {name}")
        existing = result["schools"].get(name)
        metadata = result.get("_cityEvidence", {}).get(name, {})
        method = metadata.get("method")
        managed = method == REVIEWED_METHOD
        if "baselineExtension" in row:
            snapshot = row["baselineExtension"]
            _validate_baseline_extension(result, name, snapshot, cities)
            if method == EXTENSION_METHOD:
                if snapshot != metadata["baselineExtension"]:
                    raise ValueError(f"baselineExtension snapshot changed for {name}")
            elif (managed or existing != snapshot["cities"] or
                  result.get("_cityEvidence", {}).get(name) != snapshot["evidence"]):
                raise ValueError(f"baselineExtension original mapping or metadata changed for {name}")
            source_id = "verified-location-" + hashlib.sha256(name.encode()).hexdigest()[:16]
            if method != EXTENSION_METHOD and source_id in result.get("_citySources", {}):
                raise ValueError(f"baselineExtension source ID collision for {name}")
        else:
            if method == EXTENSION_METHOD:
                raise ValueError(f"baselineExtension snapshot is required for {name}")
            if existing is not None and existing != cities and not managed:
                raise ValueError(f"verified location conflicts with existing cities for {name}")
            if existing is not None and not managed:
                raise ValueError(f"verified location cannot take ownership of baseline mapping for {name}")
        pending[name] = row
    # Reviewed inputs are authoritative for mappings this importer owns. Removing
    # a rejected candidate must also withdraw its previously generated mapping.
    stale = [name for name, item in result.get("_cityEvidence", {}).items()
             if item.get("method") in {REVIEWED_METHOD, EXTENSION_METHOD} and name not in pending]
    for name in stale:
        item = result["_cityEvidence"].pop(name)
        source_id = item.get("sourceId")
        if item.get("method") == EXTENSION_METHOD:
            snapshot = item["baselineExtension"]
            result["schools"][name] = deepcopy(snapshot["cities"])
            if snapshot["evidence"] is not None:
                result["_cityEvidence"][name] = deepcopy(snapshot["evidence"])
        else:
            result["schools"].pop(name, None)
        result.get("_citySources", {}).pop(source_id, None)
    added = 0
    for name, row in sorted(pending.items()):
        source_id = "verified-location-" + hashlib.sha256(name.encode()).hexdigest()[:16]
        result.setdefault("_citySources", {})[source_id] = {
            "url": row["sourceUrl"], "sourceDate": row.get("sourceDate"),
            "observedText": row["observedText"],
            "relationEvidence": row.get("relationEvidence"),
            "coverage": row.get("coverage", "verified-institution-address"),
            "coverageNote": row.get("coverageNote"),
            "retrievedDate": row.get("retrievedDate") or row.get("checkedAt"),
            "bodySha256": row.get("bodySha256"),
            "evidenceTextSha256": row.get("evidenceTextSha256"),
        }
        for field in ("supportingEvidence", "relationSources", "requestMethod",
                      "requestBody", "parentSourceUrl", "extractionMethod",
                      "cityEvidence", "evidencePages", "normalization", "sourceFormat"):
            if row.get(field):
                result["_citySources"][source_id][field] = row[field]
        result.setdefault("_cityEvidence", {})[name] = {
            "sourceId": source_id, "method": REVIEWED_METHOD,
        }
        if "baselineExtension" in row:
            result["_citySources"][source_id].update({
                "baselineExtension": deepcopy(row["baselineExtension"]),
                "appliedCities": deepcopy(row["cities"]),
            })
            result["_cityEvidence"][name].update({
                "method": EXTENSION_METHOD,
                "baselineExtension": deepcopy(row["baselineExtension"]),
                "appliedCities": deepcopy(row["cities"]),
            })
        added += name not in result["schools"]
        result["schools"][name] = deepcopy(row["cities"])
    return added


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--official-json", type=Path, default=ROOT / "scripts/data/official_schools.json",
                        help="Normalized official national and regional directory JSON")
    parser.add_argument("--schools-json", type=Path, default=ROOT / "app/src/main/assets/schools.json",
                        help="Limit new cities to names actually present in the school directory")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT,
                        help=f"Output city metadata (default: {DEFAULT_OUTPUT})")
    parser.add_argument("--evidence", type=Path, default=ROOT / "scripts/data/school_location_evidence.json",
                        help="Complete per-catalogue location evidence inventory")
    parser.add_argument("--verified-json", type=Path,
                        default=ROOT / "scripts/data/school_location_verified.json")
    args = parser.parse_args()

    directory = load_json(args.schools_json)
    if not isinstance(directory, list):
        raise SystemExit("school directory must be a list")
    directory_names = {row["name"] for row in directory if isinstance(row, dict) and isinstance(row.get("name"), str)}
    official = load_json(args.official_json)
    if not isinstance(official, list):
        raise SystemExit("official JSON must be a list of school records")
    result = load_json(args.output)
    if not isinstance(result, dict) or not isinstance(result.get("schools"), dict):
        raise SystemExit("output must be school_cities.json with a schools object")

    old_schools = result["schools"]
    original = json.loads(json.dumps(old_schools, ensure_ascii=False))
    original_result = json.loads(json.dumps(result, ensure_ascii=False))
    evidence = result.setdefault("_cityEvidence", {})
    if not isinstance(evidence, dict):
        raise SystemExit("_cityEvidence must be a JSON object")

    sources = result.setdefault("_citySources", {})
    if not isinstance(sources, dict):
        raise SystemExit("_citySources must be a JSON object")
    overrides = result.get("_campusOverrides", {})
    candidates: dict[str, dict[str, Any]] = {}
    skipped_no_city = Counter()
    skipped_not_in_directory = 0
    skipped_duplicate = Counter()
    for row in official:
        if not isinstance(row, dict):
            raise SystemExit("official JSON entries must be objects")
        name = row.get("name")
        city = row.get("city")
        region = row.get("region")
        category = row.get("category")
        if not isinstance(name, str) or not name.strip():
            raise SystemExit("official entry has no school name")
        if not isinstance(city, str) or not city.strip():
            skipped_no_city[str(category)] += 1
            continue
        if name not in directory_names:
            skipped_not_in_directory += 1
            continue
        if name in old_schools:
            skipped_duplicate[str(region)] += 1
            continue
        override = overrides.get(name, {})
        resolved_cities = override.get("cities") or [city.strip()]
        if name in candidates:
            if candidates[name]["cities"] != resolved_cities:
                raise SystemExit(f"conflicting source cities for {name}: {candidates[name]['cities']} / {city}")
            continue
        source_id = row.get("source_id")
        source_url = row.get("source_url")
        if not source_id or not source_url:
            raise SystemExit(f"missing official source for {name}")
        sources.setdefault(source_id, {
            "url": source_url,
            "pageUrl": row.get("source_page_url"),
            "sourceDate": row.get("source_date"),
            "dataAsOf": row.get("data_as_of"),
        })
        candidates[name] = {
            "cities": resolved_cities,
            "region": region,
            "category": category,
            "sourceId": source_id,
            "sourceRow": row.get("source_row"),
            "campusOverride": bool(override),
        }

    for name in sorted(candidates):
        item = candidates[name]
        old_schools[name] = item["cities"]
        evidence[name] = {key: value for key, value in item.items() if key != "cities"}

    verified_added = apply_verified_locations(result, load_json(args.verified_json), directory_names) if args.verified_json.exists() else 0

    if args.evidence.exists():
        inventory = load_json(args.evidence)
        inventory_names = {row.get("name") for row in inventory.get("records", [])}
        if len(inventory.get("records", [])) != len(directory_names) or inventory_names != directory_names:
            raise SystemExit("location evidence inventory must contain every directory name exactly once")

    if result == original_result:
        print("No new city-backed entries; existing map unchanged.")
        return
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": str(args.output),
        "existing_entries_preserved": len(original),
        "new_entries": len(candidates) + verified_added,
        "new_by_region": dict(Counter(item["region"] for item in candidates.values())),
        "skipped_without_city": dict(skipped_no_city),
        "skipped_not_in_directory": skipped_not_in_directory,
        "skipped_existing": dict(skipped_duplicate),
        "total_city_entries": len(result["schools"]),
        "evidence_inventory": str(args.evidence),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
