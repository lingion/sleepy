#!/usr/bin/env python3
"""Absorb the official WakeUP 6.6.2 school catalog: full registration, gated promotion.

User ruling 2026-10-28 (reaffirms the standing full-absorption directive):
absorb every row of the shipped official catalog; the only bars kept are the
safety ones — an http(s) URL without credentials or redaction on a public
host, plus the domain-collision gate in catalog_ownership. A missing, failed,
or non-200 probe is NOT a rejection: it is recorded as the honest http value
and the row still lands, marked unverified, importable by user choice.

Input: the decrypted official AdapterInfo dump
(docs/wakeup-catalog-absorb-2026-10-28/wakeup-6.6.2-school-info-decrypted.json
— plaintext factual metadata; the AES-128-CBC key==IV constant belongs to the
reverse-engineering evidence record only and is never used or stored here).

Per-row policy ("a类直接用 开闸 b类检查 c类直接复用但是标记未实测"):
  * every row is registered in scripts/data/wakeup_school_sources.json —
    template-name rows join excludedTemplates (6.1.06 precedent), all others
    join records, with sanitize_url'd sourceUrl (session parameters stay
    REDACTED, matching the 6.1.06/52.36 lineage byte-for-byte);
  * rows already admitted under the same canonical name with the same
    normalized URL and an operational catalog row: zero write. Provenance is
    carried by the source records and sourceLinks; a second admission for one
    catalog row would overwrite the operational URL and duplicate the
    ownership evidence key;
  * rows already admitted with the same URL whose catalog row is still
    pending: the entry is blocked only by the ownership gate (older evidence
    sources demanded a live HTTP 200, which campus-network and behind-login
    entries cannot answer). Per the user ruling a missing, failed or non-200
    probe is NOT a rejection, the declaration is re-stamped in place onto the
    existing admission and its evidence entry (source rewritten to this
    catalog, prior source preserved under priorOwnershipSource, operational
    URL untouched);
  * rows whose catalog entry is pending and not already admitted with this
    exact URL: an admission is written (new) or the existing pending
    admission is updated in place with the fresher official URL, exactly
    like shiguang_batch_promote.py does;
  * mappedType: established majority remap of the official sourceType from
    the prior WakeUp admission lineage (>50% and routable), else the
    official sourceType when already routable, else a URL-fingerprint remap
    via detect_type_from_url (2026-10-28 cross-verified tenant anchors —
    *.jw.chaoxing.com / *.fanya.chaoxing.com — rescue the rows the official
    catalog labels 'ziyan', WakeUp's per-school server-script path we have
    no parser for, while the URL itself carries our own protocol evidence),
    else the 'sg_asset' vendored path ONLY when the catalog name has an
    exact entry in the shiguang manifest — JwSgAssetBridge looks the row
    name up verbatim, so a base-name hit would render a dead "no adapter"
    entry;
  * last resort — no server-side protocol fingerprint at all: the row lands
    as the registration type 'jw_home' (教务主页, unverified). The official
    shipped entry is the credibility claim: the URL is the school's
    production academic-affairs portal (often campus-network only), the
    catalog points a WebView at it, and parseHtml auto-detects收录协议 after
    login. Registration-only (catalog entry stays pending) remains ONLY for
    rows the safety gate rejects — URL with REDACTED in a required
    parameter, or failing the scheme/credential/public-host audit.
  * URLs that still carry REDACTED in a required parameter after
    sanitization, or fail the scheme/credential/public-host safety audit:
    registration only.

The ownership side of each promoted row is written to school_url_evidence.json
as source 'wakeup-6.6.2-official-asset', status 'unknown' — a third-party
ownership declaration from the shipped official list, kept by the
wakeup_662_declared_owner predicate in catalog_ownership.py (full-absorption
contract: no liveness requirement, safety bar intact).

Re-running is idempotent: decisions are deterministic from the same inputs
plus the append-only probe journal scripts/data/wakeup-662-probe-journal.jsonl
(keyed by sourceRow; pass --refresh-probe to re-probe). Already-operational
rows are skipped. Run scripts/generate_school_catalog.py afterwards to rebuild
the five generated outputs.
"""
import argparse
import concurrent.futures
import hashlib
import ipaddress
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'scripts/data'
sys.path.insert(0, str(ROOT / 'scripts'))
from batch_url_admissions import detect_type_from_url, probe_url  # noqa: E402
from catalog_url_safety import sanitize_url             # noqa: E402
from integrate_batch_evidence import canonical_url      # noqa: E402
from routable_types import routable_types               # noqa: E402

DUMP = ROOT / 'docs/wakeup-catalog-absorb-2026-10-28/wakeup-6.6.2-school-info-decrypted.json'
JOURNAL = DATA / 'wakeup-662-probe-journal.jsonl'
SOURCE_ID = 'wakeup-6.6.2-official-asset'
CHECKED_AT = '2026-10-28'
ROUTABLE = routable_types()


def nfk(name):
    return unicodedata.normalize('NFKC', name.strip())


def load_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def norm_cmp(u):
    """Same-URL comparison: scheme/host/port/path/query, no fragment."""
    try:
        p = urlsplit(u)
        host = (p.hostname or '').lower()
        port = p.port
        if (p.scheme == 'https' and port == 443) or (p.scheme == 'http' and port == 80):
            port = None
        netloc = host + (f':{port}' if port else '')
        return urlunsplit((p.scheme.lower(), netloc, p.path or '', p.query or '', ''))
    except ValueError:
        return u


# A catalog row only becomes operational when Android can actually open it:
# JwSchoolInfo.hasUrl parses java.net.URI(url) and demands an http(s) scheme,
# a non-null non-blank host, and rawUserInfo == null. java.net.URI's exact
# acceptance boundary was pinned against temurin-17 (scripts/test_operational_url.py
# locks the corpus):
#   * space/control and " ' < > \ ^ ` { | } are illegal ANYWHERE (URISyntaxException);
#   * '%' must always start a valid %XX escape, else URISyntaxException;
#   * non-ASCII is legal in path/query/fragment but illegal in the authority,
#     where it parses as a valid URI with getHost() == null (hasUrl false);
#   * an invalid port ('http://x.com:abc') parses with getHost() == null;
#   * '@host' yields rawUserInfo == '' (not null), so hasUrl is false too;
#   * IPv6 bracket literals are legal (Java getHost keeps the brackets).
# The gate additionally rejects reserved/private/loopback/multicast IP hosts
# (the same public_host bar catalog_ownership keeps, and the
# Schools179CrossValidationTest RFC1918 assertion). Being stricter than hasUrl
# is safe: a rejected row stays pending instead of shipping unopenable.
_ASCII_HOST = re.compile(r'^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$')
_ILLEGAL_CHARS = re.compile("""[\\x00-\\x20\\x7f\\xa0　"'<>\\\\^`{}|]""")
_BAD_PCT = re.compile(r'%(?![0-9a-fA-F]{2})')


def operational_url_ok(u):
    if not u:
        return False
    if _ILLEGAL_CHARS.search(u) or _BAD_PCT.search(u):
        return False
    try:
        p = urlsplit(u)
    except ValueError:
        return False
    if p.scheme.lower() not in ('http', 'https'):
        return False
    if p.username is not None or p.password is not None:
        return False
    host = (p.hostname or '').lower()
    netloc = p.netloc.rsplit('@', 1)[-1]
    if netloc.startswith('['):
        if not netloc.rstrip(']').count(':') or not netloc.endswith(']'):
            return False
    elif not _ASCII_HOST.fullmatch(host):
        return False
    if host:
        try:
            address = ipaddress.ip_address(host)
        except ValueError:
            # Java's URI parser returns getHost() == null for a dotted
            # numeric-looking authority that is not a valid IPv4 (probed on
            # temurin-17: 'http://119.0.139.143.8899/...' parses but hasUrl
            # is false), and urlsplit keeps it alive. Reject numerically
            # shaped hosts that aren't valid IPs; keep ordinary domains.
            if re.fullmatch(r'[0-9.]+', host):
                return False
        else:
            if not address.is_global:
                return False
    return True


def repair_declared_url(u):
    """Recover the one merge shape the shipped assets carry: '<base> - <suffix>'.

    The 6.6.2 dump (and HEAD-era probe admissions) join a base entry URL and a
    login path with ' - ' (17 rows, e.g. 'https://hbssyys.aixiaoyuan.cn/portal/
    - /login'). The split is unambiguous, so the pair is rejoined. A suffix
    that is another URL or that still carries REDACTED is dropped, keeping the
    base. Every other dirty shape (whitespace-glued multi-URL strings, Chinese
    校外/校内 labels, non-ASCII hosts) has no unambiguous repair and is left
    untouched for operational_url_ok to reject.
    """
    if not u:
        return u
    m = re.match(r'^(https?://\S+)\s+-\s+(\S[^#]*?)\s*$', u)
    if not m:
        return u
    base, suffix = m.group(1), m.group(2)
    if not base.split('#', 1)[0].strip() or suffix.lower().startswith(('http:', 'https:')):
        return u
    if 'REDACTED' in suffix:
        joined = base
    elif suffix.startswith('/'):
        joined = base.rstrip('/') + suffix
    else:
        joined = base.rstrip('/') + '/' + suffix
    return joined if operational_url_ok(joined) else u


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--workers', type=int, default=5)
    ap.add_argument('--refresh-probe', action='store_true')
    ap.add_argument('--dry-run', action='store_true')
    args = ap.parse_args()

    dump = load_json(DUMP)
    items = dump.get('records') or dump['data']
    admissions = load_json(DATA / 'school_import_admissions.json')
    evidence = load_json(DATA / 'school_url_evidence.json')
    sources = load_json(DATA / 'wakeup_school_sources.json')
    renames = load_json(DATA / 'school_catalog_renames.json')
    catalog = load_json(ROOT / 'app/src/main/assets/schools.json')
    sg_manifest = load_json(ROOT / 'app/src/main/assets/third_party/shiguang/manifest.json')
    sg_names = {nfk(s['sgName']) for s in sg_manifest['schools']}
    baseline_names = {b['name'].strip() for b in load_json(DATA / 'school_catalog_baseline.json')}

    def canonical(name):
        stripped = name.strip()
        return renames.get(stripped, {}).get('newName', stripped)

    stats = Counter()

    # ---- phase 1: source registration (idempotent on sourceId) ----
    already_registered = any(r['sourceId'] == SOURCE_ID for r in sources['records'])
    template_names = {t['name'].strip() for t in sources['excludedTemplates']}
    if already_registered:
        stats['sources-already-registered'] += 1
    else:
        for index, x in enumerate(items):
            raw = (x.get('url') or '').strip()
            rec = {'sourceId': SOURCE_ID, 'sourceRow': index,
                   'name': x['name'], 'sourceName': x['name'],
                   'sortKey': x.get('sortKey') or '',
                   'sourceUrl': sanitize_url(raw)[0] if raw else '',
                   'sourceType': x.get('type') or '',
                   'sourceImportType': x.get('importType') or 'wakeup'}
            if x['name'].strip() in template_names:
                sources['excludedTemplates'].append(rec)
                stats['registered-excluded-template'] += 1
            else:
                sources['records'].append(rec)
                stats['registered-record'] += 1
        sha = hashlib.sha256(DUMP.read_bytes()).hexdigest()
        sources['metadata']['wakeup662Asset'] = {
            'assetVersionName': '6.6.2',
            'versionTimeLine': dump.get('versionTimeLine'),
            'decryptedRecordCount': len(items),
            'decryptedSha256': sha,
            'decryptedAsOf': CHECKED_AT,
            'license': 'No redistribution license located for proprietary APK; '
                       'factual metadata only; no application code included.',
        }

    # ---- decision engine ----
    tbl = defaultdict(Counter)
    for a in admissions:
        if a['sourceId'].startswith('wakeup-') and a.get('sourceType'):
            tbl[a['sourceType']][a.get('mappedType')] += 1

    def decide(source_type, catalog_name, url):
        # sg_asset has a hard runtime precondition: JwSgAssetBridge resolves
        # the adapter by the catalog ROW NAME verbatim (NFKC). Whatever basis
        # produced sg_asset — majority remap or manifest hit — the row only
        # lands as a working import when the manifest has that exact name;
        # otherwise it is a dead "no adapter" entry, so fall through.
        def declarable(mapped):
            return mapped != 'sg_asset' or nfk(catalog_name) in sg_names

        if source_type in tbl:
            top, n = tbl[source_type].most_common(1)[0]
            if n * 2 > sum(tbl[source_type].values()) and top in ROUTABLE and declarable(top):
                return top, 'established-majority'
        if source_type in ROUTABLE and declarable(source_type):
            return source_type, 'official-routable'
        # URL-fingerprint remap: the official sourceType may be 'ziyan' —
        # WakeUp's per-school server-script path (a 75-row tenant-domain
        # cluster plus others) that we have no parser for. The URL itself
        # carries our own protocol evidence through the cross-verified
        # detection anchors (batch_url_admissions mirror of
        # JwImportViewModel.detectProtocolFromUrlImpl, including the
        # *.jw.chaoxing.com / *.fanya.chaoxing.com tenant anchors).
        fingerprint = detect_type_from_url(url)
        if fingerprint in ROUTABLE and declarable(fingerprint):
            return fingerprint, 'url-fingerprint'
        if nfk(catalog_name) in sg_names:
            return 'sg_asset', 'shiguang-manifest-exact-hit'
        # Registration type: the shipped official catalog entry is the
        # credibility claim (school production academic-affairs portal,
        # WebView lands there, parseHtml auto-detects after login; often
        # campus-network only, so a failed probe is not a rejection).
        return 'jw_home', 'register-type-home'

    # ---- candidate selection ----
    adm_by_name = defaultdict(list)
    for a in admissions:
        adm_by_name[canonical(a['name'])].append(a)
    row_by_name = {}
    for r in catalog:
        key = r.get('catalogId') or r['sortKey'] + '_' + r['name']
        row_by_name[r['name'].strip()] = (key, r)
    ev_by_id = {}
    for e in evidence['entries']:
        ev_by_id.setdefault(e['catalogId'], []).append(e)

    seen_names = set()
    candidates = []
    restamp = []
    for index, x in enumerate(items):
        name = canonical(x['name'])
        raw = (x.get('url') or '').strip()
        if not raw:
            stats['skip-no-url'] += 1
            continue
        su = repair_declared_url(sanitize_url(raw)[0])
        if 'REDACTED' in su:
            stats['skip-redacted-required-param'] += 1
            continue
        if name in seen_names:
            stats['skip-duplicate-662-name'] += 1
            continue
        seen_names.add(name)
        nfkc = norm_cmp(su)
        same = [a for a in adm_by_name.get(name, [])
                if a.get('sourceUrl')
                and norm_cmp(repair_declared_url(a['sourceUrl'])) == nfkc]
        key, cat_row = row_by_name.get(name, (None, None))
        if cat_row is None:
            stats['skip-no-catalog-row'] += 1
            continue
        operational = cat_row.get('url') and cat_row.get('status') not in ('pending', 'grad_pending')
        if same:
            if operational:
                stats['skip-already-admitted-operational'] += 1
            else:
                # The entry is registered and admitted at this exact URL but
                # still pending — ownership (not detection, not registration)
                # is the only thing holding it back. Re-stamp the 662
                # declaration onto the existing admission and evidence so the
                # wakeup_662_declared_owner predicate can see it (older
                # evidence sources demanded HTTP 200 liveness, which
                # campus-network and behind-login entries cannot answer).
                restamp.append({'admission': same[-1], 'catalogId': key,
                                'catName': cat_row['name'], 'url': same[-1]['sourceUrl'],
                                'officialType': x.get('type') or ''})
            continue
        if operational:
            stats['skip-operational-diff-url-untouched'] += 1
            continue
        if name in baseline_names:
            stats['skip-baseline-untouched'] += 1
            continue
        candidates.append({'sourceRow': index, 'name': name, 'sourceName': x['name'],
                           'officialType': x.get('type') or '', 'importType': x.get('importType') or '',
                           'url': su, 'catalogId': key, 'catName': cat_row['name']})

    # ---- probe round (corroborating evidence, never a precondition) ----
    journal = {}
    if JOURNAL.exists() and not args.refresh_probe:
        for line in JOURNAL.read_text(encoding='utf-8').splitlines():
            if line.strip():
                rec = json.loads(line)
                journal[rec['sourceRow']] = rec
    todo = [c for c in candidates if c['sourceRow'] not in journal]
    if todo:
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
            future = {pool.submit(probe_url, c['url']): c for c in todo}
            done = 0
            for fut in concurrent.futures.as_completed(future):
                c = future[fut]
                p = fut.result()
                rec = {'sourceRow': c['sourceRow'], 'requestedUrl': c['url'],
                       'checkedAt': CHECKED_AT, 'http': p.get('http', 0),
                       'finalUrl': p.get('finalUrl'), 'bytes': p.get('bytes', 0),
                       'title': p.get('title', ''), 'fingerprints': p.get('fingerprints', []),
                       'urlHints': p.get('urlHints', [])}
                if p.get('error'):
                    rec['error'] = p['error']
                journal[c['sourceRow']] = rec
                if not args.dry_run:
                    with open(JOURNAL, 'a', encoding='utf-8') as f:
                        f.write(json.dumps(rec, ensure_ascii=False) + '\n')
                done += 1
                if done % 20 == 0:
                    print(f'  probed {done}/{len(todo)}', flush=True)

    # ---- admission + evidence writes ----
    touched = []
    for c in candidates:
        url = canonical_url(c['url'])
        if not url or 'REDACTED' in url or not operational_url_ok(url):
            stats['register-only-url-normalization-failed'] += 1
            continue
        mapped, basis = decide(c['officialType'], c['catName'], url)
        p = journal.get(c['sourceRow'], {})
        http = p.get('http', 0)
        title = sanitize_url(p.get('title') or '')[0]
        # Single resolved URL written identically to admission.probe and to
        # the evidence entry — the generator builds row.url from
        # admission.probe.finalUrl and apply_ownership requires
        # evidence.url == row.url (stale-evidence invariant), exactly the
        # convention shiguang_batch_promote.py follows.
        url = repair_declared_url(canonical_url(p.get('finalUrl') or '') or url)
        if 'REDACTED' in title or 'REDACTED' in url or not operational_url_ok(url):
            stats['register-only-probe-redaction'] += 1
            continue
        probe_status = 'unverified'
        rows = ev_by_id.get(c['catalogId'], [])
        if len(rows) > 1:
            stats['skip-evidence-duplicated'] += 1
            continue
        priors = adm_by_name.get(c['name'], [])
        if any(a.get('probe', {}).get('finalUrl') and a.get('mappedType') in ROUTABLE for a in priors):
            # The generator applies admissions in file order; a second
            # admission for one catalog row would make the outcome depend on
            # append order. Update the existing (blocked) admission in place,
            # exactly like shiguang_batch_promote.py.
            adm = priors[-1]
            adm['mappedType'] = mapped
            adm['status'] = probe_status
            adm['sourceType662'] = c['officialType']
            probe_fields = adm.setdefault('probe', {})
            probe_fields.update(url=url, finalUrl=url, http=http, checkedAt=CHECKED_AT,
                                title=title, fingerprints=p.get('fingerprints', []),
                                urlHints=p.get('urlHints', []))
            probe_fields.pop('error', None)
            adm['verification'] = ('Official WakeUP 6.6.2 shipped-catalog entry replaces the '
                                   'previously probed URL; current evidence establishes entry '
                                   'and parser-family assignment only, no authenticated '
                                   'timetable import performed.')
            stats['admission-updated-in-place'] += 1
        else:
            admissions.append({'sourceId': SOURCE_ID, 'sourceRow': c['sourceRow'],
                               'name': c['name'], 'sourceName': c['sourceName'],
                               'sortKey': '', 'sourceUrl': c['url'],
                               'sourceType': c['officialType'], 'sourceImportType': c['importType'],
                               'sourceCustomConf': {'androidZiyanType': ''},
                               'sourceMode': '', 'sourceMinVersion': 0,
                               'mappedType': mapped,
                               'probe': {'url': url, 'checkedAt': CHECKED_AT, 'status': probe_status,
                                         'fingerprints': p.get('fingerprints', []), 'http': http,
                                         'finalUrl': url, 'bytes': p.get('bytes', 0),
                                         'title': title, 'urlHints': p.get('urlHints', [])},
                               'status': probe_status,
                               'verification': ('Official WakeUP 6.6.2 shipped-catalog declaration; '
                                                'current evidence establishes entry and parser-family '
                                                'assignment only, no authenticated timetable import '
                                                'performed.')})
            stats['admission-created'] += 1
        liveness = (f'entry page confirmed live with HTTP {http} on {CHECKED_AT}') \
            if http == 200 else ('entry not confirmed live (probe answered HTTP '
                                 f'{http or "nothing"}; declared URL taken from the shipped '
                                 'official catalog)')
        if rows:
            ev = rows[0]
        else:
            ev = {'catalogId': c['catalogId'], 'name': c['catName']}
            evidence['entries'].append(ev)
            ev_by_id.setdefault(c['catalogId'], []).append(ev)
        if ev['name'] != c['catName']:
            stats['skip-evidence-name-mismatch'] += 1
            continue
        ev.update(url=url, http=http, finalUrl=url, source=SOURCE_ID, checkedAt=CHECKED_AT,
                  status='unknown', confidence='low', title=title,
                  availability='public-page' if http == 200 else 'declared-entry',
                  probeError=p.get('error'), authenticatedImportTested=False,
                  sources=[],
                  defects=['Ownership declared by the official WakeUP 6.6.2 shipped catalog '
                           '(third party); ' + liveness + '; no authenticated import performed.'])
        touched.append({'catalogId': c['catalogId'], 'name': c['catName'], 'url': url,
                        'mappedType': mapped, 'basis': basis, 'http': http})
        stats['promoted'] += 1

    # ---- declaration re-stamp for already-admitted pending rows ----
    # These rows are registered and admitted at the exact official URL; the
    # only thing holding them back is the ownership gate, whose older evidence
    # source demanded HTTP 200 liveness. Re-stamping moves the evidence entry
    # onto the 662 shipped-catalog declaration (same contract as the rows
    # promoted above: no liveness requirement, safety bar intact —
    # wakeup_662_declared_owner still enforces no-redaction, valid URL,
    # public host). The operational URL is never touched here: it already
    # equals admission.probe.finalUrl == evidence.url, so the stale-evidence
    # invariant keeps holding across the re-stamp.
    stamped = []
    for r in restamp:
        adm = r['admission']
        evs = ev_by_id.get(r['catalogId'], [])
        if len(evs) > 1:
            stats['restamp-skip-evidence-duplicated'] += 1
            continue
        raw_url = adm.get('probe', {}).get('finalUrl') or canonical_url(adm.get('sourceUrl') or '')
        # The one recoverable dirty shape ('<base> - <suffix>') is rejoined and
        # written back into all four URL fields so admission and evidence stay
        # byte-equal (stale-evidence invariant holds for the repaired value).
        url = repair_declared_url(raw_url)
        if url != raw_url:
            adm['sourceUrl'] = url
            adm.setdefault('sourceUrlBeforeRepair', raw_url)
            probe = adm.get('probe')
            if probe is not None and probe.get('finalUrl'):
                probe['finalUrl'] = url
                if 'url' in probe:
                    probe['url'] = url
            stats['restamp-url-repaired'] += 1
        # Android's JwSchoolInfo.hasUrl (java.net.URI) rejects the remaining
        # dirty shapes outright (NBSP-glued multi-URL strings, 校外/校内
        # labelled pairs, 'http://是', broken numeric hosts) — promoting such a
        # row would ship an unopenable entry, so the admission stays pending.
        if not url or 'REDACTED' in url or not operational_url_ok(url):
            stats['restamp-skip-unusable-url'] += 1
            continue
        if evs:
            ev = evs[0]
        else:
            # Admitted but never operational (e.g. the official 'ziyan'
            # server-script family we have no parser for): the generator
            # skipped the row, so no evidence entry was ever required.
            # One is created as soon as the re-decided family is routable.
            ev = {'catalogId': r['catalogId'], 'name': r['catName']}
            evidence['entries'].append(ev)
            ev_by_id.setdefault(r['catalogId'], []).append(ev)
        if ev['name'] != r['catName']:
            stats['restamp-skip-name-mismatch'] += 1
            continue
        if adm['mappedType'] not in ROUTABLE:
            # Dead admission (e.g. official 'ziyan' server-script type we have
            # no parser for). Re-decide with the current evidence: tenant-domain
            # anchors (url-fingerprint) or the jw_home registration path —
            # update in place, exactly like the promoted rows above. The
            # operational URL is never rewritten: finalUrl stays finalUrl so
            # admission.probe.finalUrl == evidence.url (stale invariant).
            mapped, basis = decide(r['officialType'], r['catName'], url)
            adm['mappedType'] = mapped
            adm['status'] = 'unverified'
            adm['sourceType662'] = r['officialType']
            adm['verification'] = ('Official WakeUP 6.6.2 shipped-catalog entry; the previously '
                                   'registered URL is kept, current evidence establishes entry and '
                                   'parser-family assignment only, no authenticated timetable '
                                   'import performed.')
            stats['restamp-remapped'] += 1
        else:
            mapped, basis = adm['mappedType'], 'kept-existing-family'
        adm['declaredBy662'] = True
        if ev.get('source') != SOURCE_ID:
            ev['priorOwnershipSource'] = ev.get('source', '')
        http = adm.get('probe', {}).get('http', ev.get('http', 0))
        checked = adm.get('probe', {}).get('checkedAt', ev.get('checkedAt', CHECKED_AT))
        liveness = (f'entry page last confirmed live with HTTP {http} on {checked}') \
            if http == 200 else ('entry not confirmed live (last probe answered HTTP '
                                 f'{http or "nothing"}; declared URL taken from the shipped '
                                 'official catalog)')
        ev.update(url=url, http=http, finalUrl=url, source=SOURCE_ID, checkedAt=checked,
                  status='unknown', confidence='low',
                  title=adm.get('probe', {}).get('title', ev.get('title', '')),
                  availability='public-page' if http == 200 else 'declared-entry',
                  probeError=adm.get('probe', {}).get('error', ev.get('probeError')),
                  authenticatedImportTested=False,
                  sources=[],
                  defects=['Ownership declared by the official WakeUP 6.6.2 shipped catalog '
                           '(third party); ' + liveness + '; no authenticated import performed.'])
        stamped.append({'catalogId': r['catalogId'], 'name': r['catName'], 'url': url,
                        'mappedType': mapped, 'basis': basis, 'http': http})
        stats['restamped'] += 1

    print(dict(stats))
    print('promoted', len(touched), '| basis', dict(Counter(t['basis'] for t in touched)),
          '| mappedType', dict(Counter(t['mappedType'] for t in touched).most_common()))
    print('restamped', len(stamped), '| basis', dict(Counter(t['basis'] for t in stamped)),
          '| mappedType', dict(Counter(t['mappedType'] for t in stamped).most_common()))
    for t in touched[:12]:
        print(' ', t['name'], t['mappedType'], t['http'], t['url'][:64])
    if args.dry_run:
        print('dry-run: no files written')
        return
    for path, value in ((DATA / 'wakeup_school_sources.json', sources),
                        (DATA / 'school_import_admissions.json', admissions),
                        (DATA / 'school_url_evidence.json', evidence)):
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('wrote sources, admissions, evidence')


if __name__ == '__main__':
    main()
