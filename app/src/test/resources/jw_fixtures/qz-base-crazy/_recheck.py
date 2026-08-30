#!/usr/bin/env python3
"""Cross-check all qz-base-crazy fixtures vs expected.json using the compiled Sim."""
import json, os, subprocess, sys

JSOUP_JAR = "/Users/lingion_k/.gradle/caches/modules-2/files-2.1/org.jsoup/jsoup/1.18.1/cb7cd991d47b44101cbe4655dec611cdc01f8a02/jsoup-1.18.1.jar"
WORK = "/tmp/jw_fixtures/qz-base-crazy/_sim"
DIR = "/tmp/jw_fixtures/qz-base-crazy"

fail = 0
for name in sorted(os.listdir(DIR)):
    if not name.endswith(".html"):
        continue
    base = name[:-5]
    exp_path = os.path.join(DIR, base + ".expected.json")
    if not os.path.exists(exp_path):
        print(f"SKIP {name} (no expected)")
        continue
    with open(exp_path) as f:
        exp = json.load(f)
    exp_courses = exp.get("courses", [])
    proc = subprocess.run(
        ["java", "-Dfile.encoding=UTF-8", "-cp", f"{JSOUP_JAR}:{WORK}", "Sim", "upstream", os.path.join(DIR, name)],
        capture_output=True, text=True)
    got = json.loads(proc.stdout.strip())
    if got == exp_courses:
        print(f"PASS {name} ({len(got)} courses)")
    else:
        fail += 1
        print(f"FAIL {name}")
        print(f"  got   ({len(got)}):")
        for c in got: print(f"    {c}")
        print(f"  expect({len(exp_courses)}):")
        for c in exp_courses: print(f"    {c}")
sys.exit(1 if fail else 0)
