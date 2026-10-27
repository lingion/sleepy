#!/usr/bin/env python3
"""Promote ShiGuang-sourced schools whose entry page passed a live probe.

Reads gap-rows.json (the 264-school catalog comparison) and the batch probe
journal, then, for every 目录补 school whose generated catalog row is empty,
whose probe reached HTTP 200, and whose admitted parser type is routable:

  1. rewrites the admission decision's probe finalUrl/http/title/checkedAt
     to the probed ShiGuang entry (canonical_url + sanitize_url, exactly the
     normalization integrate_batch_evidence.py enforces), and
  2. rewrites the matching ownership evidence row to source
     'shiguang-batch-probe' with status 'unknown' — a third-party ownership
     declaration corroborated by live HTTP 200, never a verified verdict.

Rows admitted this way land as unverified (importable, marked not tested),
which is what generate_school_catalog and catalog_ownership already enforce.
Run scripts/generate_school_catalog.py afterwards to rebuild the outputs.
Re-running is idempotent: already-promoted decisions are recognized by their
probe date and re-verified rather than duplicated.
"""
import argparse
import hashlib
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
CHECKED_AT = '2026-10-27'
SOURCE = 'shiguang-batch-probe'
ROUTABLE = routable_types()


def nfk(name):
    # Cross-file joins must survive half/full-width bracket drift; the
    # generator's own by_name keys are plain trimmed names.
    return unicodedata.normalize('NFKC', name.strip())


def load_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--probe', default='/tmp/gate_probe_264.jsonl')
    ap.add_argument('--dry-run', action='store_true')
    args = ap.parse_args()

    probe = {}
    for line in Path(args.probe).read_bytes().decode('utf-8', errors='replace').splitlines():
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
    for row in gap:
        # 目录补 (C) rows and 直接开闸 (A) rows whose catalog entry is still
        # pending both need the same evidence-backed URL write. 入口比对 (B)
        # rows are a comparison report, never a promotion path.
        if row['action'].startswith('入口比对'):
            continue
        name = nfk(row['name'])
        key, cat_row = row_by_nfkc.get(name, (None, None))
        if cat_row is None:
            stats['no-catalog-row'] += 1
            continue
        if cat_row.get('url'):
            if cat_row.get('status') in ('pending', 'grad_pending'):
                stats['still-pending'] += 1  # operational URL but blocked: re-check below
            else:
                stats['already-operational'] += 1
                continue
        if cat_row['name'].strip() in baseline_names:
            stats['baseline-untouched'] += 1
            continue
        p = probe.get(name)
        if not p or p.get('http') != 200:
            stats['probe-not-200'] += 1
            continue
        adm = adm_by_name.get(cat_row['name'])
        if adm is None:
            # Admissions join is the generator's trimmed canonical name.
            adm = adm_by_name.get(cat_row['name'].strip())
        if not adm or adm.get('mappedType') not in ROUTABLE:
            stats['no-admission-or-unroutable'] += 1
            continue
        rows = ev_by_id.get(key, [])
        if len(rows) != 1 or rows[0]['name'] != cat_row['name']:
            stats['evidence-not-exactly-one'] += 1
            continue
        url = canonical_url(p.get('final') or p.get('url') or '')
        title = sanitize_url(p.get('title') or '')[0]
        if not url or 'REDACTED' in url or 'REDACTED' in title:
            stats['session-bound-url'] += 1
            continue
        ev = rows[0]
        ev.update(url=url, http=200, finalUrl=url, source=SOURCE, checkedAt=CHECKED_AT,
                  status='unknown', confidence='low', title=title,
                  availability='public-page', probeError=None, authenticatedImportTested=False,
                  defects=['Ownership declared by shiguang_warehouse (third party); entry page '
                           'confirmed live with HTTP 200 on 2026-10-27; no authenticated import '
                           'performed.'])
        probe_fields = adm.setdefault('probe', {})
        probe_fields.update(url=url, finalUrl=url, http=200, checkedAt=CHECKED_AT, title=title)
        probe_fields.pop('error', None)
        adm['verification'] = ('Third-party ShiGuang entry with live HTTP 200 page check on '
                               + CHECKED_AT + '; current public page establishes entry and '
                               'parser-family evidence only, no authenticated timetable import '
                               'performed.')
        stats['promoted'] += 1
        touched.append({'catalogId': key, 'name': cat_row['name'], 'url': url,
                        'mappedType': adm['mappedType']})

    print(dict(stats))
    print('touched', len(touched))
    for t in touched[:10]:
        print(' ', t['name'], t['mappedType'], t['url'][:60])
    if args.dry_run:
        return
    (DATA / 'school_url_evidence.json').write_text(
        json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (DATA / 'school_import_admissions.json').write_text(
        json.dumps(admissions, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
