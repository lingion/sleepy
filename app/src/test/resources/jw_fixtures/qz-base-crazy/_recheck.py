#!/usr/bin/env python3
"""Cross-check all qz-base-crazy fixtures vs expected.json using the compiled Sim."""
import json, os, subprocess, sys

def _find_jsoup_jar():
    env = os.environ.get("JSOUP_JAR")
    if env:
        return env
    cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/org.jsoup/jsoup")
    if os.path.isdir(cache):
        for version in sorted(os.listdir(cache), reverse=True):
            jar_dir = os.path.join(cache, version)
            for root, _dirs, files in os.walk(jar_dir):
                for f in files:
                    if f == f"jsoup-{version}.jar":
                        return os.path.join(root, f)
    return None

JSOUP_JAR = _find_jsoup_jar()
if not JSOUP_JAR or not os.path.isfile(JSOUP_JAR):
    sys.exit("jsoup jar not found: set JSOUP_JAR=<path-to-jsoup.jar>")

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(HERE, "_sim")
DIR = HERE

# Windows 的 classpath 分隔符是 ';', POSIX 是 ':'
CP_SEP = ";" if os.name == "nt" else ":"

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
        ["java", "-Dfile.encoding=UTF-8", "-cp", f"{JSOUP_JAR}{CP_SEP}{WORK}", "Sim", "upstream", os.path.join(DIR, name)],
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
