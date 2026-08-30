#!/usr/bin/env python3
"""
merged-rows 对抗样本组: 逐 token 重放验证脚本。
只读 /Users/lingion_k/Desktop/sleepy 源码 + /tmp 样本, 不写 sleepy 仓库。
"""
import json, re, os, sys
from html.parser import HTMLParser

BASE = '/tmp/jw_fixtures/adversarial/merged-rows'

# ──────────── JwOldZfParser (type=0 / type=1) companion 词表/正则 (L279-L346) ────────────
COURSE_PROPERTY = set("任选 限选 实践选修 必修课 选修课 必修 选修 专基 专选 公必 公选 义修 选 必 主干 专限 公基 值班 通选 思政必 思政选 自基必 自基选 语技必".split())
OTHER_HEADER = set("时间 星期一 星期二 星期三 星期四 星期五 星期六 星期日 早晨 上午 下午 晚上".split())
CHINESE_WEEK = ["", "周一", "周二", "周三", "周四", "周五", "周六", "周日"]
CN_NUM = {"一":1,"二":2,"三":3,"四":4,"五":5,"六":6,"七":7,"八":8,"九":9,"十":10,"十一":11,"十二":12,"十三":13,"十四":14,"十五":15,"十六":16,"十七":17,"十八":18,"十九":19,"二十":20}
NODE_PAT = re.compile(r'\(\d{1,2}[-]*\d*节')
WEEK_PAT = re.compile(r'\{第\d{1,2}[-]*\d*周')
HEADER_NODE = re.compile(r'^第.*节$')

GETNODESTR = {1:'一',2:'二',3:'三',4:'四',5:'五',6:'六',7:'七',8:'八',9:'九',10:'十',11:'十一',12:'十二',13:'十三',14:'十四',15:'十五',16:'十六'}


class Table1Extract(HTMLParser):
    """提取 <table id=Table1>, 还原 Jsoup 语义的 td.text() / td.html()"""
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
    def handle_entityref(self, name):
        if self.in_t1 and self.cur_td is not None:
            self.cur_td.append(('t', {'nbsp': '\xa0', 'gt': '>', 'lt': '<', 'amp': '&'}.get(name, '&%s;' % name)))
    def handle_charref(self, name):
        if self.in_t1 and self.cur_td is not None:
            try:
                cp = int(name[1:], 16) if name[0] in 'xX' else int(name)
                self.cur_td.append(('t', chr(cp)))
            except Exception:
                self.cur_td.append(('t', '&#%s;' % name))

    def cell_text(self, parts):
        return ''.join(p for kind, p in parts if kind == 't').strip()
    def cell_html(self, parts):
        out = []
        for kind, p in parts:
            if kind == 'b': out.append('<br>')
            elif kind == 'ao': out.append(p)
            elif kind == 'ac': out.append('</a>')
            elif kind == 't': out.append(p)
        return ''.join(out)


def parse_header_node(s):
    """L306-313: 严格按上游语义。"""
    if HEADER_NODE.match(s):
        # s = "第1节", substring(1, len-1) = "1"
        if len(s) >= 2:
            node_str = s[1:-1]
            try: return int(node_str)
            except: pass
            if node_str in CN_NUM: return CN_NUM[node_str]
    return -1


def count_str(src, target):
    """L322-335: 上游 countStr 计数"""
    cnt = 0
    pos = 0
    while True:
        i = src.find(target, pos)
        if i == -1: break
        cnt += 1
        pos = i + 1
    return cnt


def parse_time_old_zf(time, start_node, source, course_name):
    """L175-267: 严格按上游语义。"""
    result = [0, 0, 0, 0, 0]   # day, step, startWeek, endWeek, type — Kotlin IntArray(5) 全 0 初始; sleepy 缺 result[1]=step
    if time.startswith('周'):
        day_str = time[:2]
        if day_str in CHINESE_WEEK:
            idx = CHINESE_WEEK.index(day_str)
            if idx > 0:
                result[0] = idx
    if result[0] == 0:
        # 找 >第N节</td>
        pat1 = f'>第{start_node}节</td>'
        pat2 = f'>第{GETNODESTR.get(start_node, "")}节</td>'
        si = source.find(pat1)
        if si == -1:
            si = source.find(pat2)
        if si != -1:
            ei = source.find(course_name, si)
            if ei != -1:
                result[0] = count_str(source[si:ei], 'Center')
    # step
    step = 0
    if '节/' in time:
        nl = time.index('节/')
        ch = time[nl-1]
        step = int(ch) if ch.isdigit() else 0
    elif ',' in time:
        step = 1
        loc = 0
        while True:
            i = time.find(',', loc)
            if i == -1: break
            step += 1
            loc = i + 1
    elif f'第{start_node}节' in time:
        step = 1
    if step == 0:
        m = NODE_PAT.search(time)
        if m:
            ni = m.group(0)
            inner = ni[1:-1]   # 去掉首 '(' 尾 '节'
            nodes = [n for n in inner.split('-') if n]
            if nodes and nodes[0].isdigit(): pass
            if len(nodes) > 1:
                s = int(nodes[0]) if nodes[0].isdigit() else start_node
                e = int(nodes[1]) if nodes[1].isdigit() else s
                step = e - s + 1
    if step == 0: step = 1
    # week: sleepy 无 else 分支 — result[2]/result[3] 无花括号周次时保持 0
    wm = WEEK_PAT.search(time)
    if wm:
        wi = wm.group(0)
        inner = wi[2:-1]
        weeks = [n for n in inner.split('-') if n]
        if weeks and weeks[0].isdigit():
            result[2] = int(weeks[0])
        if len(weeks) > 1 and weeks[1].isdigit():
            result[3] = int(weeks[1])
    if '单周' in time: result[4] = 1
    elif '双周' in time: result[4] = 2
    return result


def import_list2_course(import_list, source):
    out = []
    for ib in import_list:
        time = parse_time_old_zf(ib['timeInfo'], ib['startNode'], source, ib['name'])
        if len(ib['timeInfo']) >= 2 and ib['timeInfo'][:2] in CHINESE_WEEK:
            day = time[0]
        else:
            day = ib['cDay']
        out.append({
            'name': ib['name'], 'day': day,
            'teacher': ib.get('teacher') or '',
            'room': ib.get('room') or '',
            'startNode': ib['startNode'],
            'endNode': ib['startNode'] + time[1] - 1,
            'startWeek': time[2], 'endWeek': time[3], 'type': time[4],
        })
    return out


def parse_import_bean0(c_day, html, node):
    """L61-105: type=0"""
    beans = []
    is_abnormal = False
    inner = html.rsplit('</td>', 1)[0]
    if '<br><br><br>' in inner:
        is_abnormal = True
        parts = inner.split('<br><br><br>')
    else:
        parts = inner.split('<br><br>')
    for cs in parts:
        m = re.search(r'<a href="[^"]*">', cs)
        split_text = cs
        if m:
            split_text = cs[m.end():]
        split_text = split_text.rsplit('</a>', 1)[0]
        segs = [s.strip() for s in split_text.split('<br>')]
        if len(segs) < 3: continue
        bean = {'startNode': node, 'cDay': c_day}
        try:
            if segs[1] in COURSE_PROPERTY:
                if len(segs) == 4:
                    bean.update({'name': segs[0], 'timeInfo': segs[2],
                                 'room': segs[3], 'teacher': ''})
                else:
                    bean.update({'name': segs[0], 'timeInfo': segs[2],
                                 'room': segs[4], 'teacher': segs[3]})
            else:
                if len(segs) == 3:
                    if not is_abnormal:
                        bean.update({'name': segs[0], 'timeInfo': segs[1],
                                     'room': segs[2], 'teacher': ''})
                    else:
                        bean.update({'name': segs[0], 'timeInfo': segs[1],
                                     'room': '', 'teacher': segs[2]})
                else:
                    bean.update({'name': segs[0], 'timeInfo': segs[1],
                                 'room': segs[3], 'teacher': segs[2]})
            beans.append(bean)
        except IndexError as e:
            beans.append(('ERROR', str(e)))
    return beans


def parse_import_bean1(c_day, source_text, node):
    """L108-152: type=1 (zf_1)"""
    beans = []
    split = [s for s in re.split(r'\s+', source_text.strip()) if s]
    pre_index = -1
    has_type_flag = False
    last_err = None
    try:
        for i, tok in enumerate(split):
            has_curly = ('{' in tok and '}' in tok)
            if has_curly:
                if pre_index != -1:
                    if split[pre_index - 1] in COURSE_PROPERTY:
                        has_type_flag = True
                    bean = {
                        'startNode': node, 'cDay': c_day,
                        'name': split[pre_index - 2] if (has_type_flag and pre_index >= 2) else split[pre_index - 1],
                        'timeInfo': split[pre_index],
                        'room': '', 'teacher': '',
                    }
                    if (i - pre_index - 2) == 1:
                        bean['teacher'] = split[pre_index + 1]
                    else:
                        bean['teacher'] = split[pre_index + 1]
                        bean['room'] = split[pre_index + 2]
                    beans.append(bean)
                    pre_index = i
                else:
                    pre_index = i
            if i == len(split) - 1:
                if pre_index == -1: continue
                if split[pre_index - 1] in COURSE_PROPERTY:
                    has_type_flag = True
                bean = {
                    'startNode': node, 'cDay': c_day,
                    'name': split[pre_index - 2] if (has_type_flag and pre_index >= 2) else split[pre_index - 1],
                    'timeInfo': split[pre_index],
                    'room': '', 'teacher': '',
                }
                if (i - pre_index) == 1:
                    bean['teacher'] = split[pre_index + 1]
                else:
                    bean['teacher'] = split[pre_index + 1]
                    bean['room'] = split[pre_index + 2]
                beans.append(bean)
    except IndexError as e:
        last_err = f'IndexError: {e}'
    return beans, last_err


def run_old_zf(html, zf_type=0):
    """L22-58: 主循环"""
    if zf_type == 1 and 'Table1' not in html:
        # zf_1 变体也用 Table1, 但语义不同; 这里我们按 Table1 解析
        pass
    ex = Table1Extract()
    ex.feed(html)
    node = -1
    import_beans = []
    for row in ex.rows:
        tds = []
        for cell in row:
            txt = ex.cell_text(cell)
            html_c = ex.cell_html(cell)
            tds.append({'text': txt, 'html': html_c})
        count_flag = False
        count_day = 0
        for td in tds:
            cs = td['text']
            if len(cs) <= 1:
                if count_flag: count_day += 1
                continue
            if cs in OTHER_HEADER: continue
            r = parse_header_node(cs)
            if r != -1:
                node = r
                count_flag = True
                continue
            count_day += 1
            if zf_type == 0:
                import_beans.extend(parse_import_bean0(count_day, td['html'], node))
            else:
                beans, err = parse_import_bean1(count_day, cs, node)
                import_beans.extend(beans)
                if err: return import_beans, err, ex.rows
    courses = import_list2_course(import_beans, html)
    return courses, None, ex.rows


# ──────────── JwQzParser 移植 (L87-126) ────────────
def run_qz(html):
    """L87-126: QZ 基础"""
    # 简化: 仅匹配 id=kbtable 表格
    m = re.search(r'<table[^>]*id="kbtable"[^>]*>(.*?)</table>', html, re.S)
    if not m: return []
    table_html = m.group(1)
    trs = re.findall(r'<tr[^>]*>(.*?)</tr>', table_html, re.S)
    node_count = 0
    out = []
    for tr in trs:
        tds = re.findall(r'<td[^>]*>(.*?)</td>', tr, re.S)
        if not tds: continue
        node_count += 1
        day = 0
        for td in tds:
            day += 1
            divs = re.findall(r'<div[^>]*>(.*?)</div>', td, re.S)
            for div in divs:
                kbcs = re.findall(r'<div[^>]*class="kbcontent"[^>]*>(.*?)</div>', div, re.S)
                if not kbcs: continue
                for cell_html in kbcs:
                    if not re.sub(r'<[^>]+>', '', cell_html).strip(): continue
                    pos = 0
                    while True:
                        si = cell_html.find('-----', pos)
                        if si == -1:
                            out.extend(convert_qz(day, node_count, cell_html[pos:]))
                            break
                        out.extend(convert_qz(day, node_count, cell_html[pos:si]))
                        pos = cell_html.find('<br>', si)
                        if pos == -1: break
                        pos += 4
    return out


def convert_qz(day, node_count, info_str):
    """L33-85"""
    node = node_count * 2 - 1
    # courseName
    name = re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', info_str.split('<font')[0])).strip()
    # teacher / room / week
    teacher_m = re.search(r'title="老师"[^>]*>([^<]+)', info_str)
    teacher = teacher_m.group(1).strip() if teacher_m else ''
    room_m = re.search(r'title="教室"[^>]*>([^<]+)', info_str)
    room = room_m.group(1).strip() if room_m else ''
    room2_m = re.search(r'title="分组"[^>]*>([^<]+)', info_str)
    if room2_m: room += room2_m.group(1).strip()
    week_m = re.search(r'title="周次\(节次\)"[^>]*>([^<]+)', info_str)
    week_str = ''
    if week_m:
        week_str = week_m.group(1).split('(周)')[0]
    week_list = [w for w in week_str.split(',') if w]
    courses = []
    for w in week_list:
        sw, ew, ty = 1, 1, 0
        if '-' in w:
            parts = w.split('-')
            try: sw = int(parts[0])
            except: sw = 1
            if len(parts) > 1:
                p1 = parts[1].replace('周','').replace('(','').replace(')','').strip()
                if '单' in parts[1]: ty = 1
                elif '双' in parts[1]: ty = 2
                try: ew = int(p1)
                except: ew = sw
        else:
            v = w.replace('周','').split('(')[0]
            try: v = int(v)
            except: v = 1
            sw = ew = v
        courses.append({
            'name': name, 'teacher': teacher, 'room': room,
            'day': day, 'startNode': node, 'endNode': node + 1,
            'startWeek': sw, 'endWeek': ew, 'type': ty,
        })
    return courses


# ──────────── JwNewZfParser HTML 兜底 移植 (L241-323) ────────────
def run_new_zf_html(html):
    """L241-323: HTML 路径兜底"""
    # 简化选择器
    container = None
    for pat, sel in [
        (r'<table[^>]*id="kbtable"[^>]*>', '<table id="kbtable">'),
        (r'<table[^>]*id="kbgrid"[^>]*>', '<table id="kbgrid">'),
        (r'<table[^>]*class="[^"]*el-table__body[^"]*"[^>]*>', 'table.el-table__body'),
        (r'class="[^"]*kbcapi-table[^"]*"', '.kbcapi-table'),
        (r'id="[^"]*kb[^"]*"', '[id*=kb]'),
    ]:
        if re.search(pat, html):
            container = sel
            break
    if not container: return [], 'no_container'
    # 提取首个匹配 table
    if container.startswith('[id*='):
        m = re.search(r'<(\w+)[^>]*id="[^"]*kb[^"]*"[^>]*>(.*?)</\1>', html, re.S)
        if not m: return [], 'id_kb_no_match'
        # 解析 tr/td
        trs = re.findall(r'<tr[^>]*>(.*?)</tr>', m.group(2), re.S)
    elif container.startswith('<table'):
        m = re.search(r'<table[^>]*id="(?:kbtable|kbgrid)"[^>]*>(.*?)</table>', html, re.S)
        if not m: return [], 'no_table_match'
        trs = re.findall(r'<tr[^>]*>(.*?)</tr>', m.group(1), re.S)
    else:
        m = re.search(r'<table[^>]*class="[^"]*el-table__body[^"]*"[^>]*>(.*?)</table>', html, re.S)
        if not m: return [], 'no_el_table'
        trs = re.findall(r'<tr[^>]*>(.*?)</tr>', m.group(1), re.S)
    node_count = 0
    out = []
    for tr in trs:
        tds = re.findall(r'<td[^>]*>(.*?)</td>', tr, re.S)
        if not tds: continue
        first_text = re.sub(r'\s+', '', re.sub(r'<[^>]+>', '', tds[0]))
        is_header = '节' in first_text and not any('class="kbcontent"' in td for td in tds)
        if is_header: continue
        node_count += 1
        day = 0
        for td in tds:
            day += 1
            cells = re.findall(r'<div[^>]*class="kbcontent"[^>]*>(.*?)</div>', td, re.S)
            if not cells: continue
            for ch in cells:
                if not re.sub(r'<[^>]+>', '', ch).strip(): continue
                parts = ch.split('-----')
                for p in parts:
                    out.extend(parse_cell_new_zf(p.strip(), day, node_count))
    return out, None


def parse_cell_new_zf(html, day, node_count):
    """L287-318: parseCell"""
    name = re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', html.split('<font')[0])).strip()
    if not name: return []
    teacher_m = re.search(r'title="老师"[^>]*>([^<]+)', html)
    teacher = teacher_m.group(1).strip() if teacher_m else ''
    room_m = re.search(r'title="教室"[^>]*>([^<]+)', html)
    room = room_m.group(1).strip() if room_m else ''
    week_m = re.search(r'title="周次\(节次\)"[^>]*>([^<]+)', html)
    week_str = week_m.group(1).split('(周)')[0] if week_m else ''
    node = node_count * 2 - 1
    # parseWeekStr 简化
    if not week_str:
        ranges = [(1, 16, 0)]
    else:
        parts = re.split(r'[,，;；]', week_str)
        ranges = []
        for p in parts:
            cleaned = p.replace('周','').replace('(','').replace(')','').strip()
            ty = 0
            if '单' in p: ty = 1
            elif '双' in p: ty = 2
            if '-' in cleaned:
                segs = cleaned.split('-')
                st = int(''.join(c for c in segs[0] if c.isdigit()) or '1')
                en = int(''.join(c for c in segs[1] if c.isdigit()) or str(st))
                ranges.append((st, en, ty))
            else:
                v = int(''.join(c for c in cleaned if c.isdigit()) or '0')
                if v: ranges.append((v, v, ty))
        if not ranges: ranges = [(1, 16, 0)]
    out = []
    for (s, e, t) in ranges:
        out.append({
            'name': name, 'teacher': teacher, 'room': room,
            'day': day, 'startNode': node, 'endNode': node + 1,
            'startWeek': s, 'endWeek': e, 'type': t,
        })
    return out


# ──────────── 主测试 ────────────

if __name__ == '__main__':
    for f in sorted(os.listdir(BASE)):
        if not f.endswith('.html'): continue
        path = os.path.join(BASE, f)
        with open(path, encoding='utf-8') as fp:
            html = fp.read()
        print(f'\n=== {f} ===')
        try:
            cs, err, rows = run_old_zf(html, 0)
            print(f'  zf_type=0 (sleepy真实bug行为) → {len(cs)} courses' + (f' (err={err})' if err else ''))
            for c in cs: print(f'    {c}')
        except Exception as e:
            print(f'  zf_type=0 → EXCEPTION: {type(e).__name__}: {e}')
        try:
            cs, err, rows = run_old_zf(html, 1)
            print(f'  zf_type=1 → {len(cs)} courses' + (f' (err={err})' if err else ''))
            for c in cs[:5]: print(f'    {c}')
        except Exception as e:
            print(f'  zf_type=1 → EXCEPTION: {type(e).__name__}: {e}')
        try:
            cs = run_qz(html)
            print(f'  qz → {len(cs)} courses')
            for c in cs[:5]: print(f'    {c}')
        except Exception as e:
            print(f'  qz → EXCEPTION: {type(e).__name__}: {e}')
        try:
            cs, err = run_new_zf_html(html)
            print(f'  new_zf_html → {len(cs)} courses' + (f' (err={err})' if err else ''))
            for c in cs[:5]: print(f'    {c}')
        except Exception as e:
            print(f'  new_zf_html → EXCEPTION: {type(e).__name__}: {e}')
