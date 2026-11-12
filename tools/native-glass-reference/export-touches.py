#!/usr/bin/env python3
"""Convert recorded, observed UIKit input to replay-ready JSON / CSV.

No interpolation, smoothing, estimated timestamps, or private injection APIs.
Usage: python3 export-touches.py Documents/reference RUN_EXPORT_DIRECTORY
"""
import argparse
import csv
import json
from pathlib import Path


def export_run(trace_path, output):
    rows = []
    malformed = 0
    for line in trace_path.read_text().splitlines():
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            malformed += 1
            continue
        if row.get("event") == "touch":
            rows.append(row)
    if not rows:
        return
    # A coalesced batch can contain the main delivery sample. Keep one sample for
    # each touch / timestamp / phase / location, preferring the main delivery.
    unique = {}
    for row in rows:
        key = (row["touchID"], row["timestampUptimeSeconds"], row["phase"], row["x"], row["y"])
        if key not in unique or row.get("source") == "delivery":
            unique[key] = row
    rows = sorted(unique.values(), key=lambda row: (row["sampleT"], row["touchID"]))
    grouped = {}
    for row in rows:
        grouped.setdefault(row["touchID"], []).append(row)
    gestures = []
    for identifier, samples in grouped.items():
        start = samples[0]["sampleT"]
        gestures.append({
            "touchID": identifier,
            "startT": start,
            "durationMs": (samples[-1]["sampleT"] - start) * 1000,
            "complete": samples[0]["phase"] == 0 and samples[-1]["phase"] in (3, 4),
            "samples": [dict(row, dtMs=(row["sampleT"] - start) * 1000) for row in samples],
        })
    destination = output / trace_path.parent.name
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "replay-touches.json").write_text(json.dumps({
        "schema": 1,
        "source": str(trace_path),
        "phases": {"0": "began", "1": "moved", "2": "stationary", "3": "ended", "4": "cancelled"},
        "coordinateUnits": "actual UIKit window points; nx/ny normalized to recorded window bounds",
        "timing": "actual UITouch.timestamp, relative to observed gesture start; no interpolation",
        "discardedMalformedLines": malformed,
        "gestures": gestures,
    }, indent=2))
    columns = ["touchID", "source", "phase", "sampleT", "deliveredT", "x", "y", "nx", "ny",
               "windowWidthPt", "windowHeightPt", "windowFrameX", "windowFrameY", "hitClass", "hitIdentifier"]
    with (destination / "touches.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    print(f"{trace_path.parent.name}: {len(rows)} samples, {len(gestures)} gestures, {malformed} malformed lines")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    traces = [args.reference] if args.reference.is_file() else sorted(args.reference.glob("*/trace.jsonl"))
    if not traces:
        parser.error("no run trace.jsonl files found")
    for path in traces:
        export_run(path, args.output)


if __name__ == "__main__":
    main()
