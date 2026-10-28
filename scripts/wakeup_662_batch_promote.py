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
    normalized URL: zero write. Provenance is carried by the source records
    and sourceLinks; a second admission for one catalog row would overwrite
    the operational URL and duplicate the ownership evidence key;
  * rows whose catalog entry is pending/grad_pending and not already
    admitted with this exact URL: an admission is written (new) or the
    existing pending admission is updated in place with the fresher official
    URL, exactly like shiguang_batch_promote.py does;
  * mappedType: established majority remap of the official sourceType from
    the prior WakeUp admission lineage (>50% and routable), else the
    official sourceType when already routable, else the 'sg_asset' vendored
    path ONLY when the catalog name has an exact entry in the shiguang
    manifest — JwSgAssetBridge looks the row name up verbatim, so a
    base-name hit would render a dead "no adapter" entry;
  * no declarable parser path (non-routable type, no manifest adapter):
    registration only, the catalog entry stays pending (a sg_asset row
    without a script is a guaranteed runtime error, not an "unverified"
    import);
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
import json
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'scripts/data'
sys.path.insert(0, str(ROOT / 'scripts'))
from batch_url_admissions import probe_url              # noqa: E402
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

    def decide(source_type, catalog_name):
        # sg_asset has a hard runtime precondition: JwSgAssetBridge resolves
        # the adapter by the catalog ROW NAME verbatim (NFKC). Whatever basis
        # produced sg_asset — majority remap or manifest hit — the row only
        # lands as a working import when the manifest has that exact name;
        # otherwise it is a dead "no adapter" entry, so fall back to
        # registration-only.
        def declarable(mapped):
            return mapped != 'sg_asset' or nfk(catalog_name) in sg_names

        if source_type in tbl:
            top, n = tbl[source_type].most_common(1)[0]
            if n * 2 > sum(tbl[source_type].values()) and top in ROUTABLE and declarable(top):
                return top, 'established-majority'
        if source_type in ROUTABLE and declarable(source_type):
            return source_type, 'official-routable'
        if nfk(catalog_name) in sg_names:
            return 'sg_asset', 'shiguang-manifest-exact-hit'
        return None, 'no-declarable-parser'

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
    for index, x in enumerate(items):
        name = canonical(x['name'])
        raw = (x.get('url') or '').strip()
        if not raw:
            stats['skip-no-url'] += 1
            continue
        su = sanitize_url(raw)[0]
        if 'REDACTED' in su:
            stats['skip-redacted-required-param'] += 1
            continue
        if name in seen_names:
            stats['skip-duplicate-662-name'] += 1
            continue
        seen_names.add(name)
        nfkc = norm_cmp(su)
        same = [a for a in adm_by_name.get(name, [])
                if a.get('sourceUrl') and norm_cmp(a['sourceUrl']) == nfkc]
        key, cat_row = row_by_name.get(name, (None, None))
        if cat_row is None:
            stats['skip-no-catalog-row'] += 1
            continue
        operational = cat_row.get('url') and cat_row.get('status') not in ('pending', 'grad_pending')
        if same:
            if operational:
                stats['skip-already-admitted-operational'] += 1
            else:
                stats['skip-already-admitted-pending-same-url'] += 1
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
        mapped, basis = decide(c['officialType'], c['catName'])
        if mapped is None:
            stats['register-only-' + basis] += 1
            continue
        url = canonical_url(c['url'])
        if not url or 'REDACTED' in url:
            stats['register-only-url-normalization-failed'] += 1
            continue
        p = journal.get(c['sourceRow'], {})
        http = p.get('http', 0)
        title = sanitize_url(p.get('title') or '')[0]
        # Single resolved URL written identically to admission.probe and to
        # the evidence entry — the generator builds row.url from
        # admission.probe.finalUrl and apply_ownership requires
        # evidence.url == row.url (stale-evidence invariant), exactly the
        # convention shiguang_batch_promote.py follows.
        url = canonical_url(p.get('finalUrl') or '') or url
        if 'REDACTED' in title or 'REDACTED' in url:
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

    print(dict(stats))
    print('promoted', len(touched), '| basis', dict(Counter(t['basis'] for t in touched)),
          '| mappedType', dict(Counter(t['mappedType'] for t in touched).most_common()))
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
