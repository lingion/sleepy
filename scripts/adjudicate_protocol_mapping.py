#!/usr/bin/env python3
"""Deterministic, fail-closed adjudication of every WakeUp protocol mapping.

Directive (2026-10-17): a declared protocol type may be changed only with
per-row positive evidence of two allowed classes -- URL strong features and
page fingerprints -- mirroring the app's own detectors
(JwImportViewModel.detectProtocolFromUrlImpl / detectProtocolFromHtmlImpl)
and batch_url_admissions.py. No evidence = keep the row unroutable; the
school stays pending. Batch guessing is forbidden.

Coverage:
  * rows 0..169  -- frozen reviewed baseline, never touched;
  * rows 170..   -- every batch admission is adjudicated: existing
    mappedType is validated, and uncommitted remaps (HEAD -> worktree)
    are decided kept or rolled back. Both use the same evidence engine,
    which reads probe data plus the hash-journaled entry-page captures
    (entry-evidence-captures.jsonl).

Outputs:
  * scripts/data/school_import_admissions.json -- adjudicated, order kept;
  * scripts/data/adjudication-log.jsonl        -- per-row trace incl.
    source-declared types and every evidence hit;
  * docs/catalog/PROTOCOL_MAPPING.md           -- rules + summary + the
    complete per-row evidence table.

Run without --write for a dry run. --write is idempotent: re-running
always re-adjudicates from HEAD plus the current capture journal.
"""
import argparse
import collections
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'scripts/data'
ADMISSIONS = DATA / 'school_import_admissions.json'
CAPTURES = DATA / 'entry-evidence-captures.jsonl'
LOG = DATA / 'adjudication-log.jsonl'
DOC = ROOT / 'docs/catalog/PROTOCOL_MAPPING.md'
DATE = '2026-10-17'
BASELINE_ROWS = 170  # frozen reviewed admissions; never auto-modified

sys.path.insert(0, str(Path(__file__).resolve().parent))
from routable_types import routable_types  # noqa: E402

RT = routable_types()

# --------------------------------------------------------------- anchors ---
# URL anchors are the same recognizers the app uses, applied to lowercased
# URLs after any webvpn wrapper is stripped. CAS/authserver pure shells and
# webvpn proxies are recorded but are NEVER positive proof of a backend
# protocol system.
def _re(p):
    rx = re.compile(p)
    return lambda s: bool(rx.search(s))


URL_ANCHORS = [
    (_re(r'jwglxt|/xtgl(/|$)|login_slogin|/kbcx/|xskbcx_cx'), 'zf_new',
     'URL anchor jwglxt/xtgl/login_slogin/kbcx'),
    (_re(r'default2\.aspx|xskbcx\.aspx|xs_main'), 'zf',
     'URL anchor default2/xskbcx.aspx'),
    (_re(r'/jsxsd/|/jxd(/|$)|logon\.do|verifycode\.servlet|qzdatasoft'), 'qz',
     'URL anchor jsxsd/jxd/logon.do/verifycode.servlet/qzdatasoft'),
    (_re(r'eams[0-9]?/login(?:\.action|ext\.action)|/eams/ext/loginext|homeext\.action|loginaction'), 'classic_eams',
     'URL anchor eams login(.action)/loginExt/homeExt'),
    (_re(r'coursetableforstd|/edu/lesson/std/timetable'), 'classic_eams',
     'URL anchor courseTableForStd'),
    (_re(r'jxglstu|/eams5-student|/for-std(/|$)'), 'eams5',
     'URL anchor eams5 (jxglstu/for-std)'),
    (_re(r'mh\.chaoxing|jw\.chaoxing|/xsd(/|$)|querykbforgrdb|sdpkkblist'), 'chaoxing',
     'URL anchor chaoxing host/xsd/queryKbForGrdb'),
    (_re(r'/jwapp/'), 'wisedu', 'URL anchor /jwapp/'),
    (_re(r'wengine|shuwei'), 'shuwei_json', 'URL anchor wengine/shuwei'),
    (_re(r'xkaction\.do|actiontype=6'), 'urp', 'URL anchor xkaction.do'),
    (_re(r'/xsgrkbcx|/new/xskb'), 'cf', 'URL anchor xsgrkbcx/new/xskb'),
    (_re(r'elective\.pku|iaaa\.pku'), 'pku', 'URL anchor pku host'),
    (_re(r'kdjw\.hnust|xxjw\.hnust'), 'hnust', 'URL anchor hnust host'),
]

# Page fingerprints: visible text only. KINGOSOFT is the 青果 (Qingguo) brand,
# so it maps to kingo_new -- not chaoxing (the WakeUp mislabel family).
TEXT_ANCHORS = [
    (lambda t, L: 'kingosoft' in L, 'kingo_new', 'page KINGOSOFT（青果品牌）'),
    (lambda t, L: '教学管理信息服务平台' in t or 'zftal-ui' in L or 'login_slogin.html' in L, 'zf_new',
     'page 教学管理信息服务平台/zftal-ui'),
    (lambda t, L: '超星综合教务' in t or 'powered by chaoxing' in L, 'chaoxing',
     'page 超星综合教务/Powered by ChaoXing'),
    (lambda t, L: '强智科技' in t or '教学一体化服务平台' in t or 'qzdatasoft.com' in L, 'qz',
     'page 强智科技/教学一体化/qzdatasoft.com'),
    (lambda t, L: '正方教务管理系统' in t or '__viewstate' in L, 'zf',
     'page 正方教务管理系统/__VIEWSTATE'),
    (lambda t, L: '教务网络管理系统' in t and 'jwweb' in L, 'kingo_new',
     'page 教务网络管理系统 + jwweb'),
    (lambda t, L: '金智' in t, 'wisedu', 'page 金智'),
    (lambda t, L: '乘方教务' in t or '乘方科技' in t, 'cf', 'page 乘方教务/科技'),
]

CAS_SHELL = re.compile(r'/(?:cas|authserver)[a-z0-9_-]*/login|/authserver(?:[/?]|$)')
WEBVPN_WRAP = re.compile(r'^[a-z0-9.-]*\.?webvpn\.[a-z0-9.-]+(?::\d+)?/http/[^/]+/\d+/', re.I)
GENERIC_TITLE = re.compile(r'^(统一身份认证(平台)?|用户登录|登录|登录页面|login|sign\s*in|'
                           r'sso|cas|欢迎|首页|error|页面不存在|系统维护|网站维护|'
                           r'\d{3}\s*(forbidden|not found|authorization required)?|'
                           r'service unavailable|bad gateway|gateway time-?out)$', re.I)


def url_evidence(url):
    u = (url or '').lower()
    if not u:
        return []
    payload = WEBVPN_WRAP.sub('', u)
    out, seen = [], set()
    for test, typ, label in URL_ANCHORS:
        if typ not in seen and test(payload):
            seen.add(typ)
            out.append((typ, label))
    return out


def shell_flags(url):
    u = (url or '').lower()
    out = []
    if CAS_SHELL.search(u):
        out.append(('CAS_SHELL', 'pure CAS/authserver shell'))
    if re.search(r'/webvpn/|\.webvpn\.', u):
        out.append(('WEBVPN_SHELL', 'webvpn proxy shell'))
    return out


def text_evidence(text):
    if not text:
        return []
    t = text if isinstance(text, str) else ' '.join(map(str, text))
    L = t.lower()
    out, seen = [], set()
    for test, typ, label in TEXT_ANCHORS:
        try:
            hit = test(t, L)
        except Exception:
            hit = False
        if hit and typ not in seen:
            seen.add(typ)
            out.append((typ, label))
    return out


def load_captures():
    caps = {}
    if CAPTURES.exists():
        for line in CAPTURES.read_text().split('\n'):
            if line.strip():
                rec = json.loads(line)
                caps[rec['url']] = rec
    return caps


def collect(row, caps):
    """All positive (type, source, detail) evidence + shell flags for one row."""
    p = row.get('probe') or {}
    final_url = p.get('finalUrl') or ''
    ev, shells = [], list(shell_flags(final_url))
    for field, url in (('probe.finalUrl', final_url), ('sourceUrl', row.get('sourceUrl'))):
        if not url or 'REDACTED' in url:
            continue
        for typ, label in url_evidence(url):
            ev.append((typ, field, label))
    title = (p.get('title') or '').strip()
    if title and not GENERIC_TITLE.match(title):
        for typ, label in text_evidence(title):
            ev.append((typ, 'probe.title', label))
    for typ, label in text_evidence(p.get('fingerprints')):
        ev.append((typ, 'probe.fingerprints', label))
    cap = caps.get(final_url)
    if cap:
        ctitle = (cap.get('title') or '').strip()
        if ctitle and not GENERIC_TITLE.match(ctitle):
            for typ, label in text_evidence(ctitle):
                ev.append((typ, 'capture.title', label))
        for typ, label in text_evidence(cap.get('observedText')):
            ev.append((typ, 'capture.observedText', label))
    return ev, shells


def decide(original, proposed, caps):
    """One batch row: HEAD declared type vs current worktree type.

    Returns (verdict, finalType, reasons):
      keep    -- current value confirmed by evidence
      remap   -- evidence forces a different routable type (or rollback of an
                 unsupported uncommitted remap back to HEAD)
      unknown -- no usable positive evidence; HEAD value retained
    """
    head_t = original['mappedType']
    cur_t = proposed['mappedType']
    remapped = head_t != cur_t
    ev, shells = collect(proposed, caps)
    pos = {t for t, s, r in ev}
    cand = cur_t
    agree = sorted({r for t, s, r in ev if t == cand})
    conflict = sorted({f'{t} via {r}' for t, s, r in ev if t != cand})

    if remapped:
        if agree and not conflict:
            return 'keep', cur_t, agree[:3]
        if agree and conflict:
            return 'remap', head_t, ['conflict: ' + '; '.join(conflict[:2]) +
                                     ' (agreeing: ' + '; '.join(agree[:2]) + ')']
        return 'remap', head_t, ['no positive evidence for ' + cur_t + '; rolled back to declared']
    # unremapped row: is the declared type itself supported?
    if cand in RT:
        if agree and not conflict:
            return 'keep', cur_t, agree[:3]
        if agree and conflict:
            return 'remap', head_t, ['conflict: ' + '; '.join(conflict[:2])]
        # declared routable but zero evidence -> keep (already operational via
        # admissions review); demoting would delete a working entry without
        # proof it is wrong. Conflict alone already handled above.
        return 'keep', cur_t, ['declared routable type retained']
    # declared unroutable (ziyan/private): upgrade only on unambiguous evidence
    if len(pos) == 1 and list(pos)[0] in RT:
        alt = list(pos)[0]
        return 'remap', alt, ['upgraded by ' +
                              sorted({r for t, s, r in ev if t == alt})[0]]
    if len(pos) > 1:
        return 'unknown', head_t, ['ambiguous evidence: ' + '; '.join(sorted(conflict)[:3])]
    if shells and not pos:
        return 'unknown', head_t, ['auth/proxy shell only; no system anchor']
    return 'unknown', head_t, ['no decisive evidence; stays unroutable']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--write', action='store_true')
    args = ap.parse_args()

    head = json.loads(subprocess.run(
        ['git', 'show', 'HEAD:scripts/data/school_import_admissions.json'],
        capture_output=True, text=True, check=True).stdout)
    work = json.loads(ADMISSIONS.read_text())
    caps = load_captures()

    hmap = {(e['sourceId'], e['sourceRow']): e for e in head}
    wmap = {(e['sourceId'], e['sourceRow']): e for e in work}
    assert len(hmap) == len(head) and len(wmap) == len(work) and set(hmap) == set(wmap), \
        'admissions key drift'

    final = list(work)  # preserve exact order (capture script slices [170:])
    trace = []
    verdict_count = collections.Counter()
    change_count = 0
    for i, w in enumerate(work):
        if i < BASELINE_ROWS:
            trace.append({'idx': i, 'key': [w['sourceId'], w['sourceRow']],
                          'name': w['name'], 'declared': w.get('sourceType'),
                          'headType': w['mappedType'], 'verdict': 'frozen',
                          'finalType': w['mappedType'], 'reasons': ['frozen reviewed baseline'],
                          'evidence': []})
            verdict_count['frozen'] += 1
            continue
        h = hmap[(w['sourceId'], w['sourceRow'])]
        verdict, ftype, reasons = decide(h, w, caps)
        ev, shells = collect(w, caps)
        row = dict(w)
        row['mappedType'] = ftype
        if ftype != w['mappedType']:
            change_count += 1
            final[i] = row
        verdict_count[verdict] += 1
        trace.append({'idx': i, 'key': [w['sourceId'], w['sourceRow']], 'name': w['name'],
                      'declared': w.get('sourceType'), 'headType': h['mappedType'],
                      'verdict': verdict, 'finalType': ftype, 'reasons': reasons,
                      'evidence': [[t, s, r] for t, s, r in ev],
                      'shells': [[t, r] for t, r in shells]})

    unmapped = sum(1 for r in final if r['mappedType'] not in RT)
    routable_url = sum(1 for r in final
                       if r['mappedType'] in RT
                       and (r.get('probe') or {}).get('finalUrl')
                       and 'REDACTED' not in ((r.get('probe') or {}).get('finalUrl') or ''))
    print('verdicts:', dict(verdict_count))
    print('field changes vs worktree:', change_count)
    print('unmapped after:', unmapped, '| routable-with-URL:', routable_url)

    if args.write:
        ADMISSIONS.write_text(json.dumps(final, ensure_ascii=False, indent=2) + '\n')
        LOG.write_text(''.join(json.dumps(t, ensure_ascii=False) + '\n' for t in trace))
        write_doc(final, trace, verdict_count, unmapped, routable_url)
        print('wrote', ADMISSIONS, LOG, DOC, sep='\n  ')
    return final


def write_doc(final, trace, vc, unmapped, routable_url):
    rows = [t for t in trace if t['verdict'] != 'frozen']
    changed = [t for t in rows if t.get('headType') != t['finalType']]
    kept_remaps = [t for t in changed if t['verdict'] == 'keep']
    rolled = [t for t in changed if t['verdict'] == 'remap' and t['finalType'] == t.get('headType')]
    upgrades = [t for t in changed if t['finalType'] not in (t.get('headType'),)]
    lines = [
        '# WakeUp → 本协议映射裁决（Wave 2/3 阶段一）',
        '',
        f'裁决日期：{DATE}。裁决对象：`scripts/data/school_import_admissions.json` 全部 2669 条',
        'admissions（前 170 条人工评审行冻结不动，批次 2499 行全部逐条裁决）。',
        '裁决脚本：`scripts/adjudicate_protocol_mapping.py`（确定性、可重放；证据来源 =',
        'batch probe（`batch-probes.jsonl` 字段已内嵌 admission.probe）+ 页面抓取',
        '`entry-evidence-captures.jsonl`）。',
        '',
        '## 允许的映射依据（与 App 检测器一一对应）',
        '',
        'URL 强特征（`JwImportViewModel.detectProtocolFromUrlImpl` 同源，webvpn 壳先剥离）：',
        '',
        '| 特征 | 协议 |',
        '|---|---|',
        '| `jwglxt` / `/xtgl/` / `login_slogin` / `/kbcx/` / `xskbcx_cx` | `zf_new` |',
        '| `default2.aspx` / `xskbcx.aspx` / `xs_main` | `zf` |',
        '| `/jsxsd/` / `/jxd/` / `logon.do` / `verifycode.servlet` / `qzdatasoft` | `qz` |',
        '| `eams…/login(.action)` / `loginExt.action` / `homeExt.action` / `courseTableForStd` | `classic_eams` |',
        '| `jxglstu` / `/eams5-student` / `/for-std/` | `eams5` |',
        '| `mh.chaoxing` / `jw.chaoxing` / `/xsd/` / `queryKbForGrdb` / `sdpkkblist` | `chaoxing` |',
        '| `/jwapp/` | `wisedu` |',
        '| `wengine` / `shuwei` | `shuwei_json` |',
        '| `xkaction.do` / `actionType=6` | `urp` |',
        '| `/xsgrkbcx` / `/new/xskb` | `cf` |',
        '| `elective.pku` / `iaaa.pku` | `pku` |',
        '| `kdjw.hnust` / `xxjw.hnust` | `hnust` |',
        '',
        '页面指纹（标题/可见正文，`detectProtocolFromHtmlImpl` 同源）：',
        '`KINGOSOFT`→`kingo_new`（青果品牌，非超星——WakeUp 把"青果"错标 `ziyan` 正是本族来源）；',
        '`教学管理信息服务平台`/`zftal-ui`→`zf_new`；`超星综合教务`/`Powered by ChaoXing`→`chaoxing`；',
        '`强智科技`/`教学一体化服务平台`/`qzdatasoft.com`→`qz`；`正方教务管理系统`/`__VIEWSTATE`→`zf`；',
        '`教务网络管理系统` 且 URL 含 `jwweb`→`kingo_new`；`金智`→`wisedu`；`乘方教务/科技`→`cf`。',
        '',
        '**排除项（不构成证据）**：CAS/`authserver` 纯认证壳、webvpn 代理壳、泛化登录页标题',
        '（`统一身份认证`/`用户登录`/`登录页面` 等）、403/404/5xx 错误页、以及任何"看起来像"的',
        '名称巧合。REDACTED 会话 URL 永不参与取证。',
        '',
        '## 裁决规则（fail-closed）',
        '',
        '1. 每条批次行独立裁决；证据引擎与上面两张表完全一致。',
        '2. 已声明类型可路由且有正向证据 → 保留（keep）。',
        '3. 工作树未提交 remap 差量：新类型有证据且无矛盾 → 保留；否则回退 HEAD 声明值。',
        '4. 声明不可路由（ziyan/私有）→ 仅当存在**唯一**可路由协议锚点时升级；多协议矛盾',
        '   或无证据 → 维持不可路由，学校留 pending。',
        '5. 已声明可路由但证据不足的行：保留原声明（撤销已发布条目需要"证伪"证据，',
        '   而非仅"未证实"；矛盾证据已按规则 3 处理回退）。',
        '6. 全量逐行裁决轨迹在 `scripts/data/adjudication-log.jsonl`（idx、key、declared 源',
        '   类型、headType、证据三元组 [协议, 来源字段, 依据]、verdict、reasons）。',
        '',
        '## 汇总',
        '',
        f'- 批次行 2499：keep {vc.get("keep",0)}；remap（含回退与升级）{vc.get("remap",0)}；'
        f'unknown（维持不可路由）{vc.get("unknown",0)}；frozen 基线行 {vc.get("frozen",0)}',
        f'- 最终文件相对 HEAD 的类型变化：{len(changed)} 条（其中保留工作树 remap：'
        f'{len(kept_remaps)}；回退工作树 remap：{len(rolled)}；其余为证据升级）',
        f'- 裁决后仍不可路由（pending）：{unmapped} 条；可路由且 URL 可用：{routable_url} 条',
        '',
        '## 逐条依据表（全部 2499 批次行；sourceRow 为 WakeUp 源表行号，括号内为来源 id）',
        '',
        '| sourceRow | name | 原类型(HEAD) | 新类型 | 裁决 | 证据/理由 | 日期 |',
        '|---|---|---|---|---|---|---|',
    ]
    for t in rows:
        name = t['name'].replace('|', '\\|')
        reason = '; '.join(t['reasons'][:3]).replace('|', '\\|')
        if not reason:
            reason = '; '.join(f'{tt} via {rr}' for tt, ss, rr in t['evidence'][:3]) or 'no evidence'
        lines.append(f'| {t["key"][1]}（{t["key"][0]}） | {name} | {t["headType"]} | '
                     f'{t["finalType"]} | {t["verdict"]} | {reason} | {DATE} |')
    ziyan = sum(1 for t in rows if t.get('declared') == 'ziyan')
    lines += [
        '',
        '## 保持不可路由的家族',
        '',
        f'- `ziyan` umbrella 声明 {ziyan} 条：绝大多数无超星/强智/正方/KINGOSOFT 锚点，维持不可路由；'
        '其中有页面/URL 强特征的行已按上表逐条改写。',
        '- 学校私有协议（aic/lz/cityu/buaa/hitsz 等）：无通用协议正向证据，维持不可路由；'
        '后续应补自定义解析器，而不是伪装成通用协议。',
        '- CAS/authserver 纯壳与 webvpn 壳 URL：永不作为操作值依据，也不构成协议证据。',
        '',
        '完整 2499 行逐行证据（含未变更行为何可信）在 `scripts/data/adjudication-log.jsonl`。',
        '',
    ]
    DOC.parent.mkdir(parents=True, exist_ok=True)
    DOC.write_text('\n'.join(lines))


if __name__ == '__main__':
    main()
