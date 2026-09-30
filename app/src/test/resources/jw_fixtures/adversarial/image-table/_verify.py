#!/usr/bin/env python3
"""Re-run the compiled Jsoup simulators against every .html fixture in this
directory and compare the actual course output with the expectation recorded
in the sibling .case.json (fields: courseCount / courses).

Usage:  python3 _verify.py     (assumes _sim/Sim*.class are compiled)
"""
import json, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
# 解析顺序: 环境变量 JSOUP_JAR → Gradle 缓存自动发现 (任意版本/机器)。
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
SIM = os.path.join(HERE, "_sim")

if not JSOUP_JAR or not os.path.isfile(JSOUP_JAR):
    sys.exit("jsoup jar not found: set JSOUP_JAR=<path-to-jsoup.jar>")

# Windows 的 classpath 分隔符是 ';', POSIX 是 ':'
CP_SEP = ";" if os.name == "nt" else ":"

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
            cmd = ["java", "-Dfile.encoding=UTF-8", "-cp", f"{JSOUP_JAR}{CP_SEP}{SIM}", cls,
                   os.path.join(HERE, name)]
        else:
            cmd = ["java", "-Dfile.encoding=UTF-8", "-cp", f"{JSOUP_JAR}{CP_SEP}{SIM}", cls, mode,
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
