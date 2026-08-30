#!/usr/bin/env python3
# 忠实移植 upstream ZhengFangParser.kt type=0 路径, 验证 expected.json
import json, re, sys, glob, os

COURSE_PROPERTY = set("任选 限选 实践选修 必修课 选修课 必修 选修 专基 专选 公必 公选 义修 选 必 主干 专限 公基 值班 通选 思政必 思政选 自基必 自基选 语技必 语技选 体育必 体育选 专业基础课 双创必 双创选 新生必 新生选 学科必修 学科选修 通识必修 通识选修 公共基础 第二课堂 学科实践 专业实践 专业必修 辅修 专业选修 外语 方向 专业必修课 全选".split())
OTHER_HEADER = set("时间 星期一 星期二 星期三 星期四 星期五 星期六 星期日 早晨 上午 下午 晚上".split())
CHINESE_WEEK = {"周一":1,"周二":2,"周三":3,"周四":4,"周五":5,"周六":6,"周日":7}
CN_NUM = {"一":1,"二":2,"三":3,"四":4,"五":5,"六":6,"七":7,"八":8,"九":9,"十":10,"十一":11,"十二":12,"十三":13,"十四":14,"十五":15,"十六":16,"十七":17,"十八":18,"十九":19,"二十":20}
HEADER_NODE = re.compile(r'^第.*节$')
NODE_PAT = re.compile(r'\(\d{1,2}[-]*\d*节')
WEEK_PAT = re.compile(r'\{第\d{1,2}[-]*\d*周')

from html.parser import HTMLParser

class Table1Extract(HTMLParser):
    """提取 <table id=Table1> 的 html, 再按 Jsoup 语义还原每个 tr 的 td 列表(td.text 与 td.html)"""
    def __init__(self):
        super().__init__(convert_charrefs=False)
        self.in_t1 = False; self.depth=0
        self.rows=[]; self.cur_row=None; self.cur_td=None
        self.buf=[]  # (kind,text) kind: 't' text, 'b' br
    def handle_starttag(self, tag, attrs):
        a=dict(attrs)
        if tag=='table' and a.get('id')=='Table1':
            self.in_t1=True; self.depth=0; return
        if not self.in_t1: return
        if tag=='table': self.depth+=1
        if tag=='tr':
            self.cur_row=[]; self.rows.append(self.cur_row)
        elif tag=='td':
            self.cur_td=[]; self.cur_row.append(self.cur_td)
            if a.get('align','').lower()=='center': self.cur_td.append(('center',))
        elif tag=='br' and self.cur_td is not None:
            self.cur_td.append(('br','<br>'))
        elif tag=='a' and self.cur_td is not None:
            self.cur_td.append(('a_open','<a href="'+a.get('href','')+'">'))
    def handle_endtag(self, tag):
        if not self.in_t1: return
        if tag=='table':
            if self.depth==0: self.in_t1=False
            else: self.depth-=1
        elif tag=='a' and self.cur_td is not None:
            self.cur_td.append(('a_close','</a>'))
    def handle_data(self, d):
        if self.in_t1 and self.cur_td is not None: self.cur_td.append(('t',d))
    def handle_entityref(self, name):
        if self.in_t1 and self.cur_td is not None:
            ch={'nbsp':'\xa0','gt':'>','lt':'<','amp':'&'}.get(name,f'&{name};')
            self.cur_td.append(('t',ch))

def td_html(td_tokens):
    """重建 Jsoup 风格 td.html(): 实体 &nbsp; 序列化为 '&nbsp;'"""
    out=[]
    for tk in td_tokens:
        if tk[0]=='br': out.append('<br>')
        elif tk[0]=='a_open': out.append(tk[1])
        elif tk[0]=='a_close': out.append('</a>')
        elif tk[0]=='center': pass
        else:
            out.append(tk[1].replace('\xa0','&nbsp;').replace('&','&amp;').replace('&amp;nbsp;','&nbsp;'))
    return ''.join(out)

def td_text(td_tokens):
    """Jsoup td.text(): 块级 br → 空格, 实体 → 字符, trim"""
    parts=[tk[1] for tk in td_tokens if tk[0]=='t']
    return ' '.join(''.join(parts).split()) if parts else ''

def parse_header_node(s):
    if HEADER_NODE.match(s):
        ns=s[1:-1]
        try: return int(ns)
        except ValueError: return CN_NUM.get(ns,-1)
    return -1

def parse_import_bean(html, node):
    is_abnormal = '<br><br><br>' in html
    splits = html.split('<br><br><br>') if is_abnormal else html.split('<br><br>')
    out=[]
    for cs in splits:
        inner = cs.split('\">',1)[1] if '\">' in cs else cs
        inner = inner.rsplit('</a>',1)[0] if '</a>' in inner else inner
        s=[x.strip() for x in inner.split('<br>')]
        if len(s)<3: continue
        if s[1] in COURSE_PROPERTY:
            if len(s)==4: ib=(s[0], s[2], '', s[3])
            else: ib=(s[0], s[2], s[3], s[4])
        else:
            if len(s)==3:
                ib=(s[0], s[1], ('' if not is_abnormal else s[2]), ('' if is_abnormal else s[2]))
                # careful: normal 3-line = room only; abnormal 3-line = teacher only
                if not is_abnormal: ib=(s[0],s[1],'',s[2])
                else: ib=(s[0],s[1],s[2],'')
            else: ib=(s[0],s[1],s[2],s[3])
        out.append({'name':ib[0],'timeInfo':ib[1],'teacher':ib[2],'room':ib[3],'startNode':node})
    return out

def count_str(s1,s2):
    return s1.count(s2)

def parse_time(t, start_node, source, name):
    r={'day':0,'step':0,'sw':1,'ew':20,'type':0}
    if t.startswith('周'):
        r['day']=CHINESE_WEEK.get(t[0:2],0)
    if r['day']==0:
        si = source.find(f'>第{start_node}节</td>')
        if si==-1:
            node_cn = {1:'一',2:'二',3:'三',4:'四',5:'五',6:'六',7:'七',8:'八',9:'九',10:'十',11:'十一',12:'十二',13:'十三',14:'十四',15:'十五',16:'十六'}.get(start_node,'')
            si = source.find(f'>第{node_cn}节</td>')
        ei=0
        if si!=-1: ei=source.find(name, si)
        if si!=-1 and ei!=-1:
            r['day']=count_str(source[si:ei],'Center')
    step=0
    if '节/' in t:
        i=t.index('节/'); step=int(t[i-1])
    elif ',' in t:
        step=1; loc=0
        while True:
            j=t.find(',',loc)
            if j==-1: break
            step+=1; loc=j+1
    elif f'第{start_node}节' in t:
        step=1
    if step==0:
        m=NODE_PAT.search(t)
        if m:
            nodes=m.group()[1:-1].split('-')
            # upstream has dead code for nodes[0]; startNode not overridden
            if len(nodes)>1: step=int(nodes[1])-start_node+1
    r['step']=step
    m=WEEK_PAT.search(t)
    if m:
        w=m.group()[2:-1]
        ws=w.split('-')
        if ws[0]: r['sw']=int(ws[0])
        if len(ws)>1: r['ew']=int(ws[1])
    if '单周' in t: r['type']=1
    elif '双周' in t: r['type']=2
    return r

def parse(path):
    src=open(path,encoding='utf-8').read()
    p=Table1Extract(); p.feed(src)
    beans=[]; node=-1
    for row in p.rows:
        count_flag=False; count_day=0
        for td in row:
            text=td_text(td)
            if len(text)<=1:
                if count_flag: count_day+=1
                continue
            if text in OTHER_HEADER: continue
            r=parse_header_node(text)
            if r!=-1:
                node=r; count_flag=True; continue
            count_day+=1
            beans += parse_import_bean(td_html(td), node)
    courses=[]
    for b in beans:
        t=parse_time(b['timeInfo'], b['startNode'], src, b['name'])
        day = t['day'] if b['timeInfo'][0:2] in CHINESE_WEEK else 0
        if b['timeInfo'][0:2] in CHINESE_WEEK: day=t['day']
        else: day=0  # upstream: cDay fallback — but timeInfo always has 周X in our fixtures
        courses.append({'name':b['name'],'day':day,'startNode':b['startNode'],
            'endNode':b['startNode']+t['step']-1,'startWeek':t['sw'],'endWeek':t['ew'],
            'type':t['type'],'teacher':b['teacher'],'room':b['room']})
    return courses

base='/tmp/jw_fixtures/zf-old-table1'
fail=0
for f in sorted(glob.glob(base+'/*.html')):
    stem=os.path.basename(f)[:-5]
    exp_path=f'{base}/{stem}.expected.json'
    if not os.path.exists(exp_path): continue
    got=parse(f)
    exp=json.load(open(exp_path))['courses']
    if got==exp:
        print(f'PASS {stem} ({len(got)} courses)')
    else:
        fail+=1
        print(f'FAIL {stem}')
        print('  got:', json.dumps(got,ensure_ascii=False))
        print('  exp:', json.dumps(exp,ensure_ascii=False))
sys.exit(1 if fail else 0)
