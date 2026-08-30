#!/usr/bin/env python3
"""
Verify the expected.json for pku-bnuz fixtures by simulating the upstream
PekingParser.kt and BNUZParser.kt algorithms in Python.

This is a THROWAWAY verification script — it emulates the Kotlin parsing
semantics closely enough to catch trace errors. Not a substitute for the
real Kotlin test run in W2.
"""
import json
import re
import sys
from pathlib import Path

# --- Minimal Jsoup-like HTML parsing (just enough for our fixtures) ---
try:
    from bs4 import BeautifulSoup
except ImportError:
    print("bs4 not available, install with: pip3 install beautifulsoup4", file=sys.stderr)
    sys.exit(2)


def jsoup_html_of(td):
    """Emulate Jsoup's td.html() — the inner HTML of the element.

    BeautifulSoup's decode_contents() is close enough for our purposes.
    """
    return td.decode_contents()


# --- PKU PekingParser.kt simulation ---
CHINESE_WEEK_LIST = ["", "周一", "周二", "周三", "周四", "周五", "周六", "周日"]
NODE_PATTERN_1 = re.compile(r"\d{1,2}[~]*\d*节")

OTHER_HEADER = {"时间", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
                "早晨", "上午", "下午", "晚上"}


def kotlin_substring_after(s, delim):
    """Kotlin substringAfter: returns '' if delim not found (default)."""
    idx = s.find(delim)
    if idx == -1:
        return ""
    return s[idx + len(delim):]


def kotlin_substring_after_last(s, delim):
    idx = s.rfind(delim)
    if idx == -1:
        return ""
    return s[idx + len(delim):]


def kotlin_substring_before_last(s, delim):
    idx = s.rfind(delim)
    if idx == -1:
        return s
    return s[:idx]


def kotlin_substring_before(s, delim):
    idx = s.find(delim)
    if idx == -1:
        return s
    return s[:idx]


def safe_int(s):
    try:
        return int(s)
    except ValueError:
        return None


def parse_pku(html):
    soup = BeautifulSoup(html, "html.parser")
    kbtable = soup.find("table", class_="datagrid")
    if kbtable is None:
        return None  # would NPE upstream
    tbody = kbtable.find("tbody")
    if tbody is None:
        return None
    courses = []
    # Persist across rows like the Kotlin code (var declared at fn scope? no,
    # teacher is declared before tr loop, startWeek etc. inside tr loop)
    teacher = ""
    for tr in tbody.find_all("tr"):
        tds = tr.find_all("td")
        if len(tds) < 11:
            continue
        if "未" in tds[8].get_text():
            continue
        course_name = tds[0].get_text().strip()
        teacher = tds[4].get_text().strip()
        # Jsoup's td.html() preserves source <br> as <br> (HTML5); Python's Bs4
        # may emit <br/>. Normalize both for verification purposes only.
        tds7_html = tds[7].decode_contents().replace("<br/>", "<br>")
        time_infos = tds7_html.split("<br>")
        start_week, end_week = 1, 16
        start_node, end_node = 1, 2
        type_ = 0
        day = 7
        for time_info_html in time_infos:
            # Jsoup.parse(it).text().trim().split(' ')
            # Jsoup.parse on a fragment treats it as HTML body — plain text stays
            frag = BeautifulSoup(time_info_html, "html.parser")
            tokens = frag.get_text().strip().split(" ")
            if len(tokens) < 2:
                continue
            if "~" in tokens[0]:
                sw = safe_int(kotlin_substring_before(tokens[0], "~"))
                ew = safe_int(kotlin_substring_before(kotlin_substring_after(tokens[0], "~"), "周"))
                if sw is None or ew is None:
                    raise ValueError(f"PKU week parse error on {tokens[0]!r}")
                start_week, end_week = sw, ew
            if "单" in tokens[1]:
                type_ = 1
            elif "双" in tokens[1]:
                type_ = 2
            else:
                type_ = 0
            for idx, s in enumerate(CHINESE_WEEK_LIST):
                if idx == 0:
                    continue
                if s in tokens[1]:
                    day = idx
                    break
            m = NODE_PATTERN_1.search(tokens[1])
            if m is not None:
                v = m.group(0)
                sn = safe_int(kotlin_substring_before(v, "~"))
                en = safe_int(kotlin_substring_before(kotlin_substring_after(v, "~"), "节"))
                if sn is None or en is None:
                    raise ValueError(f"PKU node parse error on {v!r}")
                start_node, end_node = sn, en
            if len(tokens) >= 3:
                room = tokens[2]
            else:
                room = kotlin_substring_before(kotlin_substring_after(tokens[1], "("), ")")
            courses.append(dict(
                name=course_name, day=day, startNode=start_node, endNode=end_node,
                startWeek=start_week, endWeek=end_week, type=type_,
                teacher=teacher, room=room,
            ))
    return courses


# --- BNUZ BNUZParser.kt simulation ---
WEEK_P1 = re.compile(r"(\d+)-(\d+)")
WEEK_P2 = re.compile(r"(\d+)")
NODE_P = re.compile(r"^\d+$")


def parse_bnuz(html):
    soup = BeautifulSoup(html, "html.parser")
    table1 = soup.find(id="table1")
    if table1 is None:
        return None
    trs = table1.find_all("tr")
    node = 0
    teacher = ""
    room = ""
    step = 1
    start_week = 0
    end_week = 0
    type_ = 0
    courses = []
    for tr in trs:
        count_flag = False
        count_day = 1
        tds = tr.find_all("td")
        for td in tds:
            course_value = td.get_text().strip()
            if course_value in OTHER_HEADER:
                continue
            if course_value == "":
                if count_flag:
                    count_day += 1
                continue
            if NODE_P.match(course_value):
                node = int(course_value)
                count_flag = True
                continue

            html_content = td.decode_contents().replace("<br/>", "<br>")
            infos = kotlin_substring_after(html_content, "</span>")
            infos = kotlin_substring_before_last(infos, "<br>")
            infos = infos.split("<br>")
            course_name = infos[0]
            for i in range(1, len(infos), 2):
                if i + 1 >= len(infos):
                    continue
                if "{" not in infos[i] or "}" not in infos[i]:
                    continue
                # sleepy 移植版应把 step 解析包 runCatching: 无 (N节) 后缀时
                # upstream toInt() 抛 NumberFormatException, 丢弃该 section 继续
                try:
                    teacher = kotlin_substring_before(infos[i], "{")
                    room = infos[i + 1]
                    # Kotlin: room.substringAfterLast('(').substringBeforeLast('节').toInt()
                    # e.g. "教101(2节)" → "2节)" → "2" → 2
                    step = int(kotlin_substring_before_last(kotlin_substring_after_last(room, "("), "节"))
                except ValueError:
                    continue
                week_list = kotlin_substring_before(kotlin_substring_after(infos[i], "{"), "}").split(",")
                for wk in week_list:
                    if "-" in wk:
                        m = WEEK_P1.search(wk)
                        # matcher.find() must succeed; emulate
                        if m is None:
                            raise ValueError(f"BNUZ week pattern fail on {wk!r}")
                        start_week = int(m.group(1))
                        end_week = int(m.group(2))
                        if "单" in wk:
                            type_ = 1
                        elif "双" in wk:
                            type_ = 2
                        else:
                            type_ = 0
                    else:
                        m = WEEK_P2.search(wk)
                        if m is None:
                            raise ValueError(f"BNUZ week pattern fail on {wk!r}")
                        start_week = int(m.group(1))
                        end_week = start_week
                    courses.append(dict(
                        name=course_name, room=room, teacher=teacher, day=count_day,
                        startNode=node, endNode=node + step - 1,
                        startWeek=start_week, endWeek=end_week, type=type_,
                    ))
            count_day += 1
    return courses


def norm(courses):
    """Normalize course dicts for comparison with expected.json (room stripped of (N节))."""
    out = []
    for c in courses:
        out.append({
            "name": c["name"],
            "day": c["day"],
            "startNode": c["startNode"],
            "endNode": c["endNode"],
            "startWeek": c["startWeek"],
            "endWeek": c["endWeek"],
            "type": c["type"],
            "teacher": c["teacher"],
            # upstream BNUZParser stores room WITH (N节) suffix; PKU stores bare
            "room": c["room"],
        })
    return out


def norm_strip_room(courses):
    out = []
    for c in norm(courses):
        c = dict(c)
        c["room"] = re.sub(r"\(\d+节\)$", "", c["room"])
        return_norm = c
        out.append(return_norm)
    return out


def compare(actual, expected, label, strip_room_step=False):
    exp_courses = expected["courses"]
    if strip_room_step:
        actual = norm_strip_room(actual) if actual is not None else None
    else:
        actual = norm(actual) if actual is not None else None
    ok = True
    if actual is None:
        print(f"  [{label}] parser returned None (upstream would NPE) — expected {len(exp_courses)} courses, sleepy should return []")
        ok = len(exp_courses) == 0
        return ok
    if len(actual) != len(exp_courses):
        print(f"  [{label}] MISMATCH count: actual={len(actual)} expected={len(exp_courses)}")
        ok = False
    for idx, (a, e) in enumerate(zip(actual, exp_courses)):
        for k in ["name", "day", "startNode", "endNode", "startWeek", "endWeek", "type", "teacher", "room"]:
            if a.get(k) != e.get(k):
                print(f"  [{label}] course[{idx}].{k}: actual={a.get(k)!r} expected={e.get(k)!r}")
                ok = False
    if ok:
        print(f"  [{label}] OK — {len(actual)} courses match")
    else:
        print(f"  [{label}] ACTUAL: {json.dumps(actual, ensure_ascii=False, indent=2)}")
    return ok


def main():
    fixture_dir = Path("/tmp/jw_fixtures/pku-bnuz")
    all_ok = True

    cases_pku = ["pku_normal", "pku_single_double_week", "pku_missing_fields", "pku_empty", "pku_login"]
    cases_bnuz = ["bnuz_normal", "bnuz_missing_fields", "bnuz_empty", "bnuz_login"]

    print("=== PKU (PekingParser simulation) ===")
    for name in cases_pku:
        html_path = fixture_dir / f"{name}.html"
        exp_path = fixture_dir / f"{name}.expected.json"
        html = html_path.read_text(encoding="utf-8")
        expected = json.loads(exp_path.read_text(encoding="utf-8"))
        try:
            actual = parse_pku(html)
        except ValueError as e:
            print(f"  [{name}] PARSE ERROR (would crash Kotlin): {e}")
            actual = "ERROR"
            all_ok = False
            continue
        if actual is None:
            print(f"  [{name}] no datagrid → upstream NPE; sleepy should return []")
            ok = len(expected["courses"]) == 0
            print(f"  [{name}] {'OK' if ok else 'MISMATCH — expected courses should be []'}")
            all_ok = all_ok and ok
        else:
            all_ok = compare(actual, expected, name, strip_room_step=False) and all_ok

    print()
    print("=== BNUZ (BNUZParser simulation) ===")
    for name in cases_bnuz:
        html_path = fixture_dir / f"{name}.html"
        exp_path = fixture_dir / f"{name}.expected.json"
        html = html_path.read_text(encoding="utf-8")
        expected = json.loads(exp_path.read_text(encoding="utf-8"))
        try:
            actual = parse_bnuz(html)
        except ValueError as e:
            print(f"  [{name}] PARSE ERROR (would crash Kotlin): {e}")
            all_ok = False
            continue
        if actual is None:
            print(f"  [{name}] no #table1 → upstream NPE; sleepy should return []")
            ok = len(expected["courses"]) == 0
            print(f"  [{name}] {'OK' if ok else 'MISMATCH — expected courses should be []'}")
            all_ok = all_ok and ok
        else:
            # BNUZParser keeps room as-is ("教101(2节)"); expected.json usually strips
            all_ok = compare(actual, expected, name, strip_room_step=True) and all_ok

    print()
    print("ALL OK" if all_ok else "SOME MISMATCHES — see above")
    sys.exit(0 if all_ok else 1)


if __name__ == "__main__":
    main()
