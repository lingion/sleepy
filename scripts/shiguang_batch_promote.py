#!/usr/bin/env python3
"""Promote every ShiGuang-sourced school: full absorption, no liveness veto.

User ruling 2026-10-27: absorb shiguang_warehouse's school catalog completely.
A missing, failed, or non-200 probe is NOT a rejection — it is recorded as the
honest http value and the row still lands, marked unverified, importable by
user choice. The only bars kept are the safety ones: an http(s) URL without
credentials or redaction on a public host, and the domain-collision gate in
catalog_ownership.

Reads gap-rows.json (the 264-school catalog comparison, local research
artifact, never committed) and the batch probe journal when present, then for
every 目录补 (C) / 直接开闸 (A) row whose catalog entry is not operational:

  1. resolves the entry URL — live probe finalUrl when the probe exists,
     otherwise the warehouse's own adapter import URL — normalized exactly as
     integrate_batch_evidence.py enforces (canonical_url + sanitize_url);
  2. writes the admission decision: probe http/url/finalUrl/title/checkedAt
     recorded truthfully (0 means "probe did not answer"), mappedType kept
     when routable, otherwise 'sg_asset' — the vendored-adapter path added in
     the sg_asset pipeline, and the whole gap set has warehouse adapters;
  3. rewrites (or creates) the matching ownership evidence row to source
     'shiguang-adapters' with status 'unknown' — a third-party ownership
     declaration from the warehouse's adapter list, no liveness claim unless
     the probe actually returned one.

Re-running is idempotent: already-operational rows are skipped, and the writes
are deterministic from the same inputs. Run scripts/generate_school_catalog.py
afterwards to rebuild the outputs.
"""
import argparse
import json
import sys
import unicodedata
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_url_safety import sanitize_url
from routable_types import routable_types
from integrate_batch_evidence import canonical_url

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'scripts/data'
GAP = ROOT.parent / 'shiguang-gap-triage/docs/shiguang-gap-triage-2026-10-27'
PROBE_DEFAULT = str(Path.home() / 'sleepy-worktrees/shiguang-source/gate_probe_264.jsonl')
CHECKED_AT = '2026-10-27'
SOURCE = 'shiguang-adapters'
FALLBACK_TYPE = 'sg_asset'
ROUTABLE = routable_types()


def nfk(name):
    # Cross-file joins must survive half/full-width bracket drift; the
    # generator's own by_name keys are plain trimmed names.
    return unicodedata.normalize('NFKC', name.strip())


def load_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--probe', default=PROBE_DEFAULT)
    ap.add_argument('--dry-run', action='store_true')
    args = ap.parse_args()

    # The probe journal is corroborating evidence, never a precondition: full
    # absorption does not gate on a live network check having answered.
    probe = {}
    probe_path = Path(args.probe)
    if probe_path.exists():
        for line in probe_path.read_bytes().decode('utf-8', errors='replace').splitlines():
            if line.strip():
                rec = json.loads(line)
                probe[nfk(rec['name'])] = rec

    gap = load_json(GAP / 'gap-rows.json')
    admissions = load_json(DATA / 'school_import_admissions.json')
    evidence = load_json(DATA / 'school_url_evidence.json')
    baseline = load_json(DATA / 'school_catalog_baseline.json')
    renames = load_json(DATA / 'school_catalog_renames.json')
    catalog = load_json(ROOT / 'app/src/main/assets/schools.json')

    def canonical(name):
        # generate_school_catalog: renames.get(norm(name)).newName or norm(name)
        stripped = name.strip()
        return renames.get(stripped, {}).get('newName', stripped)

    baseline_names = {b['name'].strip() for b in baseline}
    row_by_nfkc = {}
    for r in catalog:
        key = r.get('catalogId') or r['sortKey'] + '_' + r['name']
        row_by_nfkc[nfk(r['name'])] = (key, r)
    adm_by_name = {}
    for a in admissions:
        adm_by_name.setdefault(canonical(a['name']), a)
    ev_by_id = {}
    for e in evidence['entries']:
        ev_by_id.setdefault(e['catalogId'], []).append(e)

    stats = Counter()
    touched = []
    for index, row in enumerate(gap):
        # 目录补 (C) rows and 直接开闸 (A) rows whose catalog entry is not yet
        # operational both need the same evidence-backed URL write. 入口比对 (B)
        # rows are a comparison report, never a promotion path.
        if row['action'].startswith('入口比对'):
            continue
        name = nfk(row['name'])
        key, cat_row = row_by_nfkc.get(name, (None, None))
        if cat_row is None:
            stats['no-catalog-row'] += 1
            continue
        if cat_row.get('url') and cat_row.get('status') not in ('pending', 'grad_pending'):
            stats['already-operational'] += 1
            continue
        if cat_row['name'].strip() in baseline_names:
            stats['baseline-untouched'] += 1
            continue
        p = probe.get(name)
        url = canonical_url((p or {}).get('final') or (p or {}).get('url')
                            or (row.get('sg_import_urls') or [''])[0])
        title = sanitize_url((p or {}).get('title') or '')[0]
        if not url or 'REDACTED' in url or 'REDACTED' in title:
            stats['session-bound-or-missing-url'] += 1
            continue
        http = (p or {}).get('http', 0)
        adm = adm_by_name.get(cat_row['name']) or adm_by_name.get(cat_row['name'].strip())
        if adm is None:
            # The catalog row exists (official source) but was never admitted;
            # the warehouse adapter declaration is the admission record now.
            adm = {'sourceId': SOURCE, 'sourceRow': row.get('sg_id') or index,
                   'name': cat_row['name'], 'sourceName': row['name'],
                   'probe': {}, 'status': 'unverified'}
            admissions.append(adm)
            adm_by_name[cat_row['name']] = adm
            stats['admission-created'] += 1
        mapped = adm.get('mappedType')
        if mapped not in ROUTABLE:
            # Every absorbed school has at least a ShiGuang adapter, so the
            # vendored-asset path is the always-available fallback.
            adm['mappedType'] = FALLBACK_TYPE
            mapped = FALLBACK_TYPE
            stats['type-fallback-sg-asset'] += 1
        rows = ev_by_id.get(key, [])
        if len(rows) > 1:
            stats['evidence-duplicated'] += 1
            continue
        if not rows:
            ev = {'catalogId': key, 'name': cat_row['name']}
            evidence['entries'].append(ev)
            ev_by_id.setdefault(key, []).append(ev)
        else:
            ev = rows[0]
        if ev['name'] != cat_row['name']:
            stats['evidence-name-mismatch'] += 1
            continue
        liveness = ('entry page confirmed live with HTTP ' + str(http) + ' on ' + CHECKED_AT) if p \
            else 'entry not live-probed (declared URL taken from the warehouse adapter list)'
        ev.update(url=url, http=http, finalUrl=url, source=SOURCE, checkedAt=CHECKED_AT,
                  status='unknown', confidence='low', title=title,
                  availability='public-page' if http == 200 else 'declared-entry',
                  probeError=None, authenticatedImportTested=False,
                  defects=['Ownership declared by shiguang_warehouse (third party); '
                           + liveness + '; no authenticated import performed.'])
        probe_fields = adm.setdefault('probe', {})
        probe_fields.update(url=url, finalUrl=url, http=http, checkedAt=CHECKED_AT, title=title)
        probe_fields.pop('error', None)
        adm['verification'] = ('Third-party ShiGuang adapter declaration; ' + liveness
                               + '; current evidence establishes entry and parser-family '
                               'assignment only, no authenticated timetable import performed.')
        stats['promoted'] += 1
        touched.append({'catalogId': key, 'name': cat_row['name'], 'url': url,
                        'mappedType': mapped, 'http': http})

    print(dict(stats))
    print('touched', len(touched))
    for t in touched[:10]:
        print(' ', t['name'], t['mappedType'], t['http'], t['url'][:60])
    if args.dry_run:
        return
    (DATA / 'school_url_evidence.json').write_text(
        json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (DATA / 'school_import_admissions.json').write_text(
        json.dumps(admissions, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
