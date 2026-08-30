#!/usr/bin/env python3
"""Re-run the compiled Jsoup simulators against every .html fixture in this
directory and compare the actual course output with the expectation recorded
in the sibling .case.json (fields: courseCount / courses).

Usage:  python3 _verify.py     (assumes _sim/Sim*.class are compiled)
"""
import json, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
JSOUP_JAR = "/Users/lingion_k/.gradle/caches/modules-2/files-2.1/org.jsoup/jsoup/1.18.1/cb7cd991d47b44101cbe4655dec611cdc01f8a02/jsoup-1.18.1.jar"
SIM = os.path.join(HERE, "_sim")

# Which sim modes to probe per fixture. case.json courseCount must match at
# least one probed mode (the parser that wins in tryAllParsers order).
MODES = ["zf", "zf_1", "qz", "qz_crazy", "urp", "newzf"]

fail = 0
for name in sorted(os.listdir(HERE)):
    if not name.endswith(".html"):
        continue
    base = name[:-5]
    case_path = os.path.join(HERE, base + ".case.json")
    if not os.path.exists(case_path):
        print(f"SKIP {name} (no case.json)")
        continue
    with open(case_path, encoding="utf-8") as f:
        case = json.load(f)
    expected_count = case["expected"]["courseCount"]
    expected_courses = case["expected"].get("courses")

    results = {}
    for mode in MODES:
        cls = "SimNewZf" if mode == "newzf" else "Sim"
        if mode == "newzf":
            # SimNewZf only takes the path
            cmd = ["java", "-Dfile.encoding=UTF-8", "-cp", f"{JSOUP_JAR}:{SIM}", cls,
                   os.path.join(HERE, name)]
        else:
            cmd = ["java", "-Dfile.encoding=UTF-8", "-cp", f"{JSOUP_JAR}:{SIM}", cls, mode,
                   os.path.join(HERE, name)]
        proc = subprocess.run(cmd, capture_output=True, text=True)
        results[mode] = proc.stdout.strip()

    matched_modes = []
    for mode, out in results.items():
        if out == "[FALLBACK_QZ]":
            out = results.get("qz", "")
        try:
            got = json.loads(out)
        except Exception:
            continue
        if len(got) != expected_count:
            continue
        if expected_courses is not None and got != expected_courses:
            continue
        matched_modes.append(mode)

    status = "PASS" if matched_modes else "FAIL"
    if not matched_modes:
        fail += 1
    print(f"{status} {name} (expected courseCount={expected_count}, matched modes: {matched_modes or 'none'})")
    if not matched_modes:
        for mode, out in results.items():
            print(f"    {mode}: {out}")

sys.exit(1 if fail else 0)
