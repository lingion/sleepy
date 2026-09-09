import json, re

D = 'docs/bjtu-cross-verify-2026-09-09'
metas = {}
with open(D + '/search-raw/repo-meta.jsonl') as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        m = json.loads(line)
        metas[m['full_name']] = m

hits = {}
files = ['repos-search.txt', 'repos-search-2.txt', 'code-search.txt', 'code-search-2.txt']
for name in files:
    q = None
    with open(D + '/search-raw/' + name) as f:
        for line in f:
            mq = re.match(r'=== (?:CODE )?QUERY: (.*)$', line)
            if mq:
                q = mq.group(1).strip()
                continue
            m2 = re.match(r'([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+) \|', line)
            if m2 and q:
                hits.setdefault(m2.group(1), set()).add(q)

CANDIDATES = [
    'wan300/bjtu_mis_Android',
    'Anyes666/BJTU-MIS-HarmonyOS',
    'HFDLYS/BJTUselfService',
    'fish2lab/bjtu-cli',
    'fish2lab/BJTUselfService-macOS',
    's1y4x1/BJTU-course-assistant',
    'ZiuChen/userscript',
    'ymzhang-cs/BJTU-iCalendar-Generator',
    'Moliseeee/bjtu-timetable',
    'hyskr/BJTU-course-autoget-program',
    '57Darling02/BjtuCoursePlatform',
    'jlytwhx/bjtuDean',
    'jlytwhx/bjtubox_python',
    'Orien233/Campus-Mate',
    'ymzhang-cs/BJTU-STU-MCP',
    'xschur/CourseRobber',
    'Futuremind-BJTU/Futuremind-BJTU',
    'Yukikasu/BJTU_ezRate',
    'xxxand/bjtu_teaching_assessment',
    'Coconut00/BJTU-script',
    'aooxin/BJTU-CC',
    'etherealviator/CourseTable',
    'mcdona1d/ZF-Assistant',
    'ZiuChen/userscript',
]

seen, out = set(), []
for name in CANDIDATES:
    if name in seen:
        continue
    seen.add(name)
    out.append(name)

items = []
for i, name in enumerate(out, 1):
    m = metas.get(name, {})
    qs = sorted(hits.get(name, set()))
    items.append({
        'id': i,
        'full_name': name,
        'stars': m.get('stars', 0),
        'lang': m.get('lang'),
        'license': m.get('license'),
        'default_branch': m.get('default_branch'),
        'pushed_at': m.get('pushed_at', ''),
        'archived': m.get('archived', False),
        'description': m.get('description') or '',
        'html_url': 'https://github.com/' + name,
        'matched_query': ' / '.join(qs) if qs else '(manual add)',
    })

greasy = {
    'id': len(items) + 1,
    'full_name': 'greasyfork.org:430918 北交大iCalender课表生成',
    'stars': None,
    'lang': 'JavaScript (userscript)',
    'license': None,
    'default_branch': None,
    'pushed_at': '',
    'archived': False,
    'description': 'Greasy Fork 上的北交大课表 iCal 生成脚本 (非 GitHub 外部源)',
    'html_url': 'https://greasyfork.org/en/scripts/430918',
    'matched_query': 'searxng: 北交大 课表 ics github',
}

items.append(greasy)

doc = {
    'school': 'Beijing Jiaotong University (BJTU)',
    'date': '2026-09-09',
    'selection_rule': (
        '全量纳入: 检索矩阵命中的 BJTU 特定软件项目'
        ' (教务/MIS/课表/选课/评教/成绩/考试/校历)'
        ' 与列出 BJTU 的多校课程导入配置, 不论 stars/年代全部纳入;'
        ' 唯一否决=archived+pushed>10y (本轮 0 命中否决).'
        ' 通用域名数据集/个人主页/笔记/食堂类仓库非教务软件,'
        ' 不入候选, 原始命中保留于 search-raw/ 供复核.'
    ),
    'search_matrix': {
        'required': ['A repos: bjtu 教务', 'B repos: bjtu 课表', 'C repos: BJTU course/jwgl/timetable/schedule', 'D code: jwc.bjtu.edu.cn'],
        'optional_runs': 9,
        'raw_hits_dir': 'docs/bjtu-cross-verify-2026-09-09/search-raw/',
    },
    'candidates': items,
}
with open(D + '/candidates.json', 'w') as f:
    json.dump(doc, f, ensure_ascii=False, indent=2)
print('candidates:', len(items))
