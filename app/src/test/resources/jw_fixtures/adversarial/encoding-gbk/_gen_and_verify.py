#!/usr/bin/env python3
# encoding-gbk 对抗样本组: 生成器 + JwOldZfParser(type=0)/JwNewZfParser(JSON 路径) 忠实移植验证
# 移植自 /Users/lingion_k/Desktop/sleepy/app/src/main/java/com/lingion/sleepy/data/jw/JwOldZfParser.kt
# 只写 /tmp, 不碰 sleepy 仓库。
import json, re, os, sys
from html.parser import HTMLParser

BASE = '/tmp/jw_fixtures/adversarial/encoding-gbk'

# ─── JwOldZfParser companion 词表/正则 (kotlin L279-L346 逐项对齐) ───
COURSE_PROPERTY = set("任选 限选 实践选修 必修课 选修课 必修 选修 专基 专选 公必 公选 义修 选 必 主干 专限 公基 值班 通选 思政必 思政选 自基必 自基选 语技必".split())
OTHER_HEADER = set("时间 星期一 星期二 星期三 星期四 星期五 星期六 星期日 早晨 上午 下午 晚上".split())
CHINESE_WEEK = ["", "周一", "周二", "周三", "周四", "周五", "周六", "周日"]
CN_NUM = {"一":1,"二":2,"三":3,"四":4,"五":5,"六":6,"七":7,"八":8,"九":9,"十":10,"十一":11,"十二":12,"十三":13,"十四":14,"十五":15,"十六":16,"十七":17,"十八":18,"十九":19,"二十":20}
NODE_PAT = re.compile(r'\(\d{1,2}[-]*\d*节')
WEEK_PAT = re.compile(r'\{第\d{1,2}[-]*\d*周')
HEADER_NODE = re.compile(r'^第.*节$')

GETNODESTR = {1:'一',2:'二',3:'三',4:'四',5:'五',6:'六',7:'七',8:'八',9:'九',10:'十',11:'十一',12:'十二',13:'十三',14:'十四',15:'十五',16:'十六'}

class Table1Extract(HTMLParser):
    """提取 <table id=Table1>, 还原 Jsoup 语义的 td.text() / td.html()。
    convert_charrefs=False + handle_charref 模拟浏览器/Jsoup 对数字实体的解码。"""
    def __init__(self):
        super().__init__(convert_charrefs=False)
        self.in_t1 = False; self.depth = 0
        self.rows = []; self.cur_row = None; self.cur_td = None
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'table' and a.get('id') == 'Table1':
            self.in_t1 = True; self.depth = 0; return
        if not self.in_t1: return
        if tag == 'table': self.depth += 1
        if tag == 'tr':
            self.cur_row = []; self.rows.append(self.cur_row)
        elif tag == 'td':
            self.cur_td = []; self.cur_row.append(self.cur_td)
        elif tag == 'br' and self.cur_td is not None:
            self.cur_td.append(('b', '<br>'))
        elif tag == 'a' and self.cur_td is not None:
            self.cur_td.append(('ao', '<a href="%s">' % a.get('href', '')))
    def handle_endtag(self, tag):
        if not self.in_t1: return
        if tag == 'table':
            if self.depth == 0: self.in_t1 = False
            else: self.depth -= 1
        elif tag == 'a' and self.cur_td is not None:
            self.cur_td.append(('ac', '</a>'))
    def handle_data(self, d):
        if self.in_t1 and self.cur_td is not None: self.cur_td.append(('t', d))
    def handle_entityref(self, name):  # &nbsp; 等
        if self.in_t1 and self.cur_td is not None:
            self.cur_td.append(('t', {'nbsp': '\xa0', 'gt': '>', 'lt': '<', 'amp': '&'}.get(name, '&%s;' % name)))
    def handle_charref(self, name):    # &#39640; / &#x8BFE; — 浏览器 DOM 解码点
        if self.in_t1 and self.cur_td is not None:
            try:
                cp = int(name[1:], 16) if name[0] in 'xX' else int(name)
                self.cur_td.append(('t', chr(cp)))
            except Exception:
                self.cur_td.append(('t', '&#%s;' % name))  # 非法引用按字面保留

def esc_text(s):
    """Jsoup 序列化 text node: & → &amp; (&nbsp; 的 NBSP 还原为实体, 其余原样)"""
    return s.replace('\xa0', '&nbsp;').replace('&', '&amp;').replace('&amp;nbsp;', '&nbsp;')

def td_html(td):
    out = []
    for tk in td:
        if tk[0] == 'b': out.append('<br>')
        elif tk[0] == 'ao': out.append(tk[1])
        elif tk[0] == 'ac': out.append('</a>')
        elif tk[0] == 't': out.append(esc_text(tk[1]))
    return ''.join(out)

def td_text(td):
    parts = [tk[1] for tk in td if tk[0] == 't']
    return ' '.join(''.join(parts).split()) if parts else ''

def parse_header_node(s):
    if HEADER_NODE.match(s):
        ns = s[1:-1]
        try: return int(ns)
        except ValueError: return CN_NUM.get(ns, -1)
    return -1

def count_str(s1, s2):
    """Kotlin countStr 忠实移植(含 findIndex==length-1 尾判定)"""
    times = 0; start = 0
    f = s1.find(s2, start)
    while f != -1 and f != len(s1) - 1:
        times += 1; start = f + 1; f = s1.find(s2, start)
    if f == len(s1) - 1: times += 1
    return times

def strip_anchor(s):
    if s.startswith('<a'):
        gt = s.find('>')
        return s[gt+1:] if gt >= 0 else s[2:]
    return s

def parse_import_bean(html, node, cday):
    abnormal = '<br><br><br>' in html
    splits = html.split('<br><br><br>') if abnormal else html.split('<br><br>')
    out = []
    for cs in splits:
        inner = cs.split('">', 1)[1] if '">' in cs else cs
        inner = inner.rsplit('</a>', 1)[0] if '</a>' in inner else inner
        s = [strip_anchor(x.strip()) for x in inner.split('<br>')]
        if len(s) < 3: continue
        if s[1] in COURSE_PROPERTY:
            if len(s) == 4: ib = [s[0], s[2], '', s[3]]
            else: ib = [s[0], s[2], s[3], s[4]]
        else:
            if len(s) == 3:
                ib = [s[0], s[1], s[2], ''] if abnormal else [s[0], s[1], '', s[2]]
            else: ib = [s[0], s[1], s[2], s[3]]
        out.append({'name': ib[0], 'timeInfo': ib[1], 'teacher': ib[2], 'room': ib[3], 'startNode': node, 'cDay': cday})
    return out

def parse_time(t, start_node, source, name):
    r = {'day': 0, 'step': 0, 'sw': 1, 'ew': 20, 'type': 0}
    if t.startswith('周'):
        ds = t[0:2]
        if ds in CHINESE_WEEK:
            idx = CHINESE_WEEK.index(ds)
            if idx > 0: r['day'] = idx
    if r['day'] == 0:
        si = source.find('>第%d节</td>' % start_node)
        if si == -1:
            si = source.find('>第%s节</td>' % GETNODESTR.get(start_node, ''))
        ei = 0
        if si != -1: ei = source.find(name, si)
        if si != -1 and ei != -1:
            r['day'] = count_str(source[si:ei], 'Center')
    step = 0
    if '节/' in t:
        i = t.index('节/'); step = int(t[i-1]) if t[i-1].isdigit() else 0
    elif ',' in t:
        step = 1; loc = 0
        while t.find(',', loc) != -1 and loc < len(t):
            step += 1; loc = t.find(',', loc) + 1
    elif '第%d节' % start_node in t:
        step = 1
    if step == 0:
        m = NODE_PAT.search(t)
        if m:
            nodes = m.group()[1:-1].split('-')
            # kotlin: nodes[0].toIntOrNull()?.let { } — 死代码, 不覆盖 startNode
            if len(nodes) > 1:
                s0 = int(nodes[0]) if nodes[0].lstrip('-').isdigit() and nodes[0] else start_node
                e0 = int(nodes[1]) if nodes[1].lstrip('-').isdigit() and nodes[1] else s0
                step = e0 - s0 + 1
    if step == 0: step = 1
    r['step'] = step
    m = WEEK_PAT.search(t)
    if m:
        w = m.group()[2:-1]
        ws = w.split('-')
        if ws[0] and ws[0].isdigit(): r['sw'] = int(ws[0])
        if len(ws) > 1 and ws[1].isdigit(): r['ew'] = int(ws[1])
    else:
        r['sw'] = 1; r['ew'] = 20
    if '单周' in t: r['type'] = 1
    elif '双周' in t: r['type'] = 2
    return r

def parse_oldzf(path, encoding='utf-8'):
    src = open(path, encoding=encoding).read()
    p = Table1Extract(); p.feed(src)
    beans = []; node = -1
    for row in p.rows:
        count_flag = False; count_day = 0
        for td in row:
            text = td_text(td)
            if len(text) <= 1:
                if count_flag: count_day += 1
                continue
            if text in OTHER_HEADER: continue
            h = parse_header_node(text)
            if h != -1:
                node = h; count_flag = True; continue
            count_day += 1
            beans += parse_import_bean(td_html(td), node, count_day)
    courses = []
    for b in beans:
        t = parse_time(b['timeInfo'], b['startNode'], src, b['name'])
        day = t['day'] if (len(b['timeInfo']) >= 2 and b['timeInfo'][0:2] in CHINESE_WEEK) else b['cDay']
        courses.append({'name': b['name'], 'day': day, 'startNode': b['startNode'],
                        'endNode': b['startNode'] + t['step'] - 1, 'startWeek': t['sw'],
                        'endWeek': t['ew'], 'type': t['type'],
                        'teacher': b['teacher'], 'room': b['room']})
    return courses

# ─── JwNewZfParser JSON 路径忠实移植 (JwNewZfParser.kt) ───
ZF_MARKERS = ['"kbxx"', '"tmp_list"', '"xskbcx"', 'xskbcx_json']

def parse_newzf_json(src):
    """返回 (courses, reason)"""
    for mk in ZF_MARKERS:
        if src.find(mk) >= 0:
            return [], 'marker_hit:' + mk
    t = src.strip()
    if not (t.startswith('{') or t.startswith('[')):
        return [], 'no_marker_no_json'
    try:
        data = json.loads(t)
    except Exception:
        return [], 'json_broken'
    if isinstance(data, list):
        arr = data
    else:
        arr = None
        for k, v in data.items():
            if isinstance(v, list) and v:
                arr = v; break
        if arr is None: return [], 'no_array_attr'
    courses = []
    for o in arr:
        if not isinstance(o, dict): continue
        def first_str(*keys):
            for k in keys:
                v = str(o.get(k, '') or '').strip()
                if v: return v
            return ''
        def first_int(*keys):
            for k in keys:
                v = str(o.get(k, '') or '').strip()
                if v:
                    try: return int(v)
                    except ValueError: pass  # Kotlin toIntOrNull → null → 下一个 key
            return None
        name = first_str('kcmc', 'kcm', 'kc_mc', 'courseName', 'rlkcmc', 'jxbmc')
        if not name: continue
        teacher = first_str('jsxm', 'jsmc', 'teacher', 'attendClassTeacher', 'skjs')
        room = first_str('jasmc', 'jsmc', 'classroomName', 'jxlh', 'jasdm')
        day = first_int('kcxq', 'xq', 'xqj', 'classDay', 'skxq')
        if day is None: continue
        start = first_int('ksjcsd', 'ksjc', 'jc', 'classSessions', 'ksjcd')
        if start is None: start = first_int('ksjc')
        if start is None: continue
        end = first_int('jsjcsd', 'jsjc', 'jsjssd', 'continuingSession')
        if end is None or end < start: end = start
        courses.append({'name': name, 'day': max(1, min(7, day)), 'startNode': max(1, start),
                        'endNode': max(start, end), 'teacher': teacher, 'room': room,
                        'zcd': o.get('zcd', '')})
    return courses, 'parsed' if courses else 'all_rows_skipped'

def cp(s):
    return ''.join('&#%d;' % ord(c) for c in s)

def cpx(s):
    return ''.join('&#x%X;' % ord(c) for c in s)

def table(rows):
    """rows: list of (header, cells[7]) ; cells 元素为 html str 或 None→&nbsp;"""
    out = ['<table id="Table1" border="1" cellspacing="0" cellpadding="0">']
    hdr, wd = rows[0]
    out.append('  <tr>\n    <td colspan="2" align="Center">%s</td>' % hdr)
    for i, w in enumerate(['星期一','星期二','星期三','星期四','星期五','星期六','星期日']):
        out.append('    <td align="Center">%s</td>' % w)
    out.append('  </tr>')
    for hdr, cells in rows[1:]:
        out.append('  <tr>\n    <td colspan="2" align="Center">%s</td>' % hdr)
        for c in cells:
            out.append('    <td>%s</td>' % (c if c is not None else '&nbsp;'))
        out.append('  </tr>')
    out.append('</table>')
    return '\n'.join(out)

def page(title, body, charset='gb2312'):
    return ('<!DOCTYPE html>\n<html>\n<head>\n<meta http-equiv="Content-Type" content="text/html; charset=%s" />\n'
            '<title>%s</title>\n</head>\n<body>\n%s\n</body>\n</html>\n' % (charset, title, body))

def skeleton(rows_body_cells):
    """标准骨架: 时间表头行 + 上午组头 + 第一/二节 + 下午组头 + 第三节 + 晚上组头"""
    rows = [('时间', [None]*7)]
    for hdr, cells in rows_body_cells:
        rows.append((hdr, cells))
        if hdr in ('第一节', '第三节'):
            nxt = '第二节' if hdr == '第一节' else '第四节'
            rows.append((nxt, [None]*7))
            rows.append(('下午' if hdr == '第三节' else '上午', [None]*7))
    rows.append(('晚上', [None]*7))
    return table(rows)

def A(href, inner):
    return '<a href="%s">%s</a>' % (href, inner)

# ═══ 1. entity_decimal_control ═══
cell1 = A('kcmc.aspx?xh=01',
          '%s<br>周二第1,2节{第1-16周}<br>%s<br>%sA101' % (cp('高等数学'), cp('张老师'), cp('教学楼')))
cell2 = A('kcmc.aspx?xh=02',
          '%s<br>周三第1,2节{第2-8周|单周}<br>李老师<br>B202' % cpx('大学英语'))
p1 = page('学生个人课表', skeleton([('第一节', [None, cell1, cell2, None, None, None, None])]))
open(BASE + '/entity_decimal_control.html', 'w', encoding='utf-8').write(p1)

# ═══ 2. entity_double_escaped_name ═══
dcell = A('kcmc.aspx?xh=01',
          '&amp;%s&amp;%s&amp;%s&amp;%s<br>周二第1,2节{第1-16周}<br>张老师<br>教学楼A101'
          % ('#39640;', '#31561;', '#25968;', '#23398;'))
ncell = A('kcmc.aspx?xh=02', '大学英语<br>周四第1,2节{第2-8周|双周}<br>李老师<br>B202')
p2 = page('学生个人课表', skeleton([('第一节', [None, dcell, None, ncell, None, None, None])]))
open(BASE + '/entity_double_escaped_name.html', 'w', encoding='utf-8').write(p2)

# ═══ 3. entity_in_script_kblist (zf_new 家族) ═══
kb = ('{"kbList":['
      '{"kcmc":"%s","xqj":2,"cdmc":"%sA101","xm":"%s","zcd":"1-16周","jc":"1-2"},'
      '{"kcmc":"%s","xqj":3,"cdmc":"B202","xm":"李老师","zcd":"2-8周(单)","jc":"6-7"}]}'
      % (cp('高等数学'), cp('教学楼'), cp('张老师'), cp('大学英语')))
p3 = ('<!DOCTYPE html>\n<html>\n<head>\n<meta charset="gb2312">\n<title>学生个人课表</title>\n</head>\n<body>\n'
      '<div id="container" class="zftal-ui-v5">\n'
      '<script>\nvar kbList = %s;\n</script>\n'
      '<table class="courselist"><tr><td>本页面数据由前端渲染</td></tr></table>\n'
      '</div>\n</body>\n</html>\n' % kb)
open(BASE + '/entity_in_script_kblist.html', 'w', encoding='utf-8').write(p3)

# ═══ 4. charset_declared_gbk_actual_utf8 (页面声明 gb2312, 字节实为 UTF-8 → WebView 用 GBK 解码) ═══
healthy = page('学生个人课表', skeleton([
    ('第一节', [None, A('kcmc.aspx?xh=01', '高等数学<br>周二第1,2节{第1-16周}<br>张老师<br>教学楼A101'), None, None, None, None, None]),
    ('第三节', [None, None, A('kcmc.aspx?xh=02', '大学英语<br>周三第3,4节{第2-8周|双周}<br>李老师<br>B202'), None, None, None, None]),
]))
mojibake = healthy.encode('utf-8').decode('gbk', 'replace')
open(BASE + '/charset_declared_gbk_actual_utf8.html', 'w', encoding='utf-8').write(mojibake)

# ═══ 5. charset_declared_utf8_actual_gbk (声明 utf-8, 字节实为 GBK → WebView 用 UTF-8 解码 → U+FFFD) ═══
fffd = healthy.encode('gbk').decode('utf-8', 'replace')
open(BASE + '/charset_declared_utf8_actual_gbk.html', 'w', encoding='utf-8').write(fffd)

# ═══ 6. raw_gbk_correct_charset — 真实 GBK 字节页 (正确声明) ═══
gbk_page = page('学生个人课表', skeleton([
    ('第一节', [None, A('kcmc.aspx?xh=01', '高等数学<br>周二第1,2节{第1-16周}<br>张老师<br>教学楼A101'), None, None, None, None, None]),
    ('第三节', [None, None, None, A('kcmc.aspx?xh=02', '体育<br>周四第3,4节{第1-16周}<br>王老师<br>体育馆'), None, None, None]),
]), charset='gb2312')
open(BASE + '/raw_gbk_correct_charset.html', 'wb').write(gbk_page.encode('gbk'))

# ═══ 验证 ═══
print('=== 1 entity_decimal_control ===');  r = parse_oldzf(BASE + '/entity_decimal_control.html'); print(json.dumps(r, ensure_ascii=False, indent=1))
print('=== 2 entity_double_escaped_name ==='); r2 = parse_oldzf(BASE + '/entity_double_escaped_name.html'); print(json.dumps(r2, ensure_ascii=False, indent=1))
print('=== 3 entity_in_script_kblist (JwNewZfParser JSON 路径) ===')
print('markers_hit:', [m for m in ZF_MARKERS if m in p3])
r3, why3 = parse_newzf_json(p3); print('courses=%d why=%s' % (len(r3), why3)); print(json.dumps(r3, ensure_ascii=False, indent=1))
print('  (若修 kbList marker + jc 字符串解析后应得 2 课, name 为字面实体串)')
print('=== 4 charset_declared_gbk_actual_utf8 (文件内容=已乱码的 DOM 视图) ===')
print('--- 样例乱码映射 ---')
for w in ['时间','星期一','第一节','高等数学','周二第1,2节{第1-16周}','张老师','上午']:
    print('  %s -> %r' % (w, w.encode('utf-8').decode('gbk', 'replace')))
r4 = parse_oldzf(BASE + '/charset_declared_gbk_actual_utf8.html'); print(json.dumps(r4, ensure_ascii=False, indent=1))
print('=== 5 charset_declared_utf8_actual_gbk (U+FFFD) ===')
for w in ['时间','星期一','第一节','高等数学','周二第1,2节{第1-16周}','单周']:
    print('  %s -> %r' % (w, w.encode('gbk').decode('utf-8', 'replace')))
r5 = parse_oldzf(BASE + '/charset_declared_utf8_actual_gbk.html'); print(json.dumps(r5, ensure_ascii=False, indent=1))
print('=== 6 raw_gbk_correct_charset (以 gbk 解码后解析) ===')
r6 = parse_oldzf(BASE + '/raw_gbk_correct_charset.html', encoding='gbk'); print(json.dumps(r6, ensure_ascii=False, indent=1))
print('=== 6b raw_gbk_correct_charset (误用 utf-8 解码 → 无 WebView 的 fetch 路径风险) ===')
try:
    r6b = parse_oldzf(BASE + '/raw_gbk_correct_charset.html', encoding='utf-8')
    print(json.dumps(r6b, ensure_ascii=False, indent=1))
except Exception as e:
    print('DECODE ERROR: %s: %s' % (type(e).__name__, e))
