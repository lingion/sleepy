#!/usr/bin/env python3
"""Export observed language-menu geometry without interpolating missing frames."""
import csv
import json
import statistics
import sys
from pathlib import Path


def role(view, views_by_path, menu_sizes):
    name, path = view["class"], view["path"]
    ancestors = [views_by_path.get(path.rsplit("/", count)[0], {})
                 for count in range(1, path.count("/") + 1)]
    if name == "UISDFElementView":
        return "sdf-contour"
    if name == "_UIMagicMorphTransformView":
        return "morph-parent"
    if name == "TransformView" and any(child["class"] in ("_UIMagicMorphView", "_UIMagicMorphTransformView")
                                       and child["path"].startswith(path + "/")
                                       for child in views_by_path.values()):
        return "moving-parent"
    if name == "_UIPortalView" and any(parent.get("class") in ("_UIMagicMorphView", "_UIMagicMorphTransformView") for parent in ancestors):
        if any(abs(view["boundsW"] - width) < 0.001 and abs(view["boundsH"] - height) < 0.001
               for width, height in menu_sizes):
            return "menu-portal"
        if view["boundsW"] > 0 and view["boundsH"] > 0:
            return "anchor-portal"
    if name == "_UIContextMenuView":
        return "target-container"
    if name == "UICollectionView":
        return "content-collection"
    return None


def percentile(values, fraction):
    return sorted(values)[round((len(values) - 1) * fraction)] if values else None


if len(sys.argv) != 3:
    raise SystemExit("Usage: export-language.py TRACE_FILE_OR_DIRECTORY OUTPUT_DIRECTORY")
source, destination = map(Path, sys.argv[1:])
destination.mkdir(parents=True, exist_ok=True)
traces = [source] if source.is_file() else sorted(source.glob("**/trace.jsonl"))
if not traces:
    raise SystemExit(f"No trace.jsonl found at {source}")
for trace in traces:
    session = json.loads((trace.parent / "session.json").read_text())
    output = destination / trace.parent.name
    output.mkdir(parents=True, exist_ok=True)
    times, intervals, costs, actions, animations = [], [], [], [], {}
    truncated = sublayer_truncated = estimated_clocks = 0
    menu_sizes = set()
    fields = ["frame", "capture_start_t", "capture_end_t", "capture_time_basis", "enqueue_t",
              "display_t", "sample_interval_ms", "observer_cost_ms", "role",
              "path", "class", "x", "y", "w", "h", "boundsW", "boundsH", "opacity",
              "effectiveOpacity", "hidden", "effectiveHidden", "m11", "m22", "m41", "m42",
              "presentationAvailable"]
    with (output / "visible-layers.csv").open("w") as handle:
        writer = csv.DictWriter(handle, fields)
        writer.writeheader()
        for line in trace.open():
            row = json.loads(line)
            if row["event"] != "frame":
                if row["event"] == "language-selection" or (row["event"] == "touch" and row["source"] == "delivery"):
                    actions.append(row)
                continue
            times.append(row["displayTimestamp"])
            if row["displayIntervalMs"] > 0:
                intervals.append(row["displayIntervalMs"])
            costs.append(row["captureCostMs"])
            truncated += int(row["truncated"])
            sublayer_truncated += int(any(view.get("animatedSublayersTruncated", False) for view in row["views"]))
            exact_clock = "captureStartT" in row and "captureEndT" in row
            estimated_clocks += int(not exact_clock)
            capture_end = row.get("captureEndT", row["t"])
            capture_start = row.get("captureStartT", capture_end - row["captureCostMs"] / 1000)
            views_by_path = {view["path"]: view for view in row["views"]}
            menu_sizes.update((view["boundsW"], view["boundsH"]) for view in row["views"]
                              if view["class"] == "_UIContextMenuView")
            for view in row["views"]:
                kind = role(view, views_by_path, menu_sizes)
                if kind:
                    writer.writerow({"frame": row["frame"], "capture_start_t": capture_start,
                                     "capture_end_t": capture_end,
                                     "capture_time_basis": "explicit-interval" if exact_clock else "legacy-approximation",
                                     "enqueue_t": row["t"],
                                     "display_t": row["displayTimestamp"] - session["originUptimeSeconds"],
                                     "sample_interval_ms": row["displayIntervalMs"],
                                     "observer_cost_ms": row["captureCostMs"], "role": kind,
                                     **{key: view.get(key, "") for key in fields[9:]}})
                observations = [(view["path"], view.get("animations", []))]
                observations += [(view["path"] + "/" + child["path"], child["animations"])
                                 for child in view.get("animatedSublayers", [])]
                for path, values in observations:
                    for animation in values:
                        key = json.dumps([path, animation], sort_keys=True)
                        animations.setdefault(key, {"first_capture_start_t": capture_start,
                                                   "first_capture_end_t": capture_end, "path": path,
                                                   "view_class": view["class"], "animation": animation})
    if not times:
        raise SystemExit(f"No frame observations in {trace}")
    summary = {
        "session": session, "frames": len(times),
        "observed_callback_hz_average": (len(times) - 1) / (times[-1] - times[0]) if len(times) > 1 else None,
        "interval_ms": {"median": statistics.median(intervals) if intervals else None, "p95": percentile(intervals, .95),
                        "maximum": max(intervals, default=None), "over_25_ms": sum(value > 25 for value in intervals)},
        "observer_cost_ms": {"median": statistics.median(costs), "p95": percentile(costs, .95), "maximum": max(costs)},
        "truncated_frames": truncated,
        "animated_sublayer_truncated_frames": sublayer_truncated if session.get("schema", 1) >= 2 else None,
        "estimated_capture_clock_frames": estimated_clocks,
        "limits": ["CADisplayLink timestamps describe observed callbacks, not physical screen refresh.",
                   "Presentation reads occur sequentially between capture_start_t and capture_end_t; display_t may precede them.",
                   "Legacy clocks approximate start as enqueue_t minus captureCostMs; row assembly overhead is not recoverable.",
                   "Roles follow observed native class names and morph ancestry; anchor-portal means the non-menu source portal in this reference.",
                   "effectiveOpacity multiplies UIView ancestor opacity; extra CALayer ancestors, masks and shader coverage are excluded.",
                   "SDF contour geometry does not expose internal optical shader coverage.",
                   "No missing samples were interpolated. Negative or above-one presentation opacity is retained."]
    }
    (output / "summary.json").write_text(json.dumps(summary, indent=2))
    (output / "input-and-selection.json").write_text(json.dumps(actions, indent=2))
    (output / "native-animations.json").write_text(json.dumps(list(animations.values()), indent=2))
    print(output)
