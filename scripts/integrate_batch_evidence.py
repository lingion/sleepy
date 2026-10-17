#!/usr/bin/env python3
"""Merge entry-evidence captures into school_url_evidence.json.

Run after capture_entry_evidence.py finishes fetching. For every new batch
admission without an evidence row, emit a verified/unknown verdict from the
captured entry page, re-checked through catalog_ownership.source_proves so
only evidence the authoritative validator accepts can mark a record verified.
"""
import hashlib
import json
import sys
import urllib.parse
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from catalog_url_safety import sanitize_url
from catalog_ownership import source_proves, identity_name
from routable_types import routable_types

ROUTABLE_PROTOCOL_TYPES = routable_types()

DATA = Path(__file__).resolve().parent / 'data'
TODAY = '2026-10-16'


def read_journal_lines(path):
    """Split the journal on \\n only.

    Page text may embed \\x85 (NEL), U+2028/U+2029 inside JSON strings;
    str.splitlines() would treat them as line boundaries and corrupt the
    journal. ensure_ascii=True on write keeps future lines ASCII-safe.
    """
    return path.read_text().split('\n')


def clean_journal():
    path = DATA / 'entry-evidence-captures.jsonl'
    if not path.exists():
        return 0
    good, dropped = [], 0
    for line in read_journal_lines(path):
        if not line.strip():
            continue
        try:
            good.append(json.dumps(json.loads(line), ensure_ascii=True))
        except json.JSONDecodeError:
            dropped += 1
    path.write_text('\n'.join(good) + '\n')
    return dropped


def canonical_url(u):
    """Match the value curl reports for a redirect-free GET, then sanitize."""
    if not u:
        return u
    parts = urllib.parse.urlsplit(u)
    if not parts.scheme:
        # bare host from source data; the probe fetched it over http
        parts = urllib.parse.urlsplit('http://' + u)
    scheme = parts.scheme.lower()
    host = (parts.hostname or '').lower()
    port = parts.port
    if (scheme == 'https' and port == 443) or (scheme == 'http' and port == 80):
        port = None
    netloc = host + (f':{port}' if port else '')
    u = urllib.parse.urlunsplit((scheme, netloc, parts.path, parts.query, parts.fragment))
    return sanitize_url(u)[0]


def normalize_admissions():
    """Canonicalize every probe finalUrl so admissions/evidence agree byte-exactly.

    Any URL whose sanitized form carries a REDACTED session/credential token is
    session-bound and must never become an operational configuration value; such
    admissions are recorded without a URL instead.
    """
    path = DATA / 'school_import_admissions.json'
    admissions = json.loads(path.read_text())
    changed = dropped = 0
    for a in admissions:
        url = a.get('probe', {}).get('finalUrl')
        if not url:
            continue
        c = canonical_url(url)
        if 'REDACTED' in c:
            a['probe']['finalUrl'] = ''
            a['probe']['error'] = (a['probe'].get('error') or '') + \
                ' Session-bound URL redacted; not usable as an operational value.'
            dropped += 1
        elif c != url:
            a['probe']['finalUrl'] = c
            changed += 1
    if changed or dropped:
        path.write_text(json.dumps(admissions, ensure_ascii=False, indent=2) + '\n')
    return changed, dropped


def load_captures():
    """Return {canonical url: capture} preferring 200 responses."""
    path = DATA / 'entry-evidence-captures.jsonl'
    captures = {}
    if not path.exists():
        return captures
    for line in read_journal_lines(path):
        if not line.strip():
            continue
        try:
            rec = json.loads(line)
        except json.JSONDecodeError:
            continue
        url = rec.get('url')
        if not url:
            continue
        c = canonical_url(url)
        if c != url:
            rec = dict(rec)
            rec['url'] = c
            rec['capturedFromUrl'] = url
        prev = captures.get(c)
        if prev is None or (rec.get('http') == 200 and prev.get('http') != 200):
            captures[c] = rec
    return captures


def main():
    dropped = clean_journal()
    print(f'journal: dropped {dropped} corrupt lines')
    changed, redacted = normalize_admissions()
    print(f'admissions: canonicalized {changed}, redacted session-bound {redacted}')
    captures = load_captures()
    print(f'journal: {len(captures)} usable captures')

    admissions = json.loads((DATA / 'school_import_admissions.json').read_text())
    baseline = json.loads((DATA / 'school_catalog_baseline.json').read_text())
    official = json.loads((DATA / 'official_schools.json').read_text())
    renames = json.loads((DATA / 'school_catalog_renames.json').read_text())
    evidence = json.loads((DATA / 'school_url_evidence.json').read_text())

    # Drop previous runs of this batch merge so the pass is idempotent.
    before = len(evidence['entries'])
    evidence['entries'] = [e for e in evidence['entries']
                           if e.get('source') != 'wakeup-batch-probe']
    print(f'evidence: replaced batch entries ({before - len(evidence["entries"])} removed)')

    def canonical(name):
        n = name.strip()
        r = renames.get(n)
        return r['newName'] if r else n

    baseline_names = {x['name'].strip() for x in baseline}
    official_ids = {src['name'].strip(): src['id'] for src in official}
    existing_ids = {e['catalogId'] for e in evidence['entries']}

    new_entries = []
    verdicts = Counter()
    for adm in admissions[170:]:
        name = canonical(adm['name'])
        if name in baseline_names:
            continue
        if adm.get('mappedType') not in ROUTABLE_PROTOCOL_TYPES:
            # The generator refuses non-routable types; no evidence row may exist
            # for a URL that never becomes operational.
            verdicts['unroutable-type'] += 1
            continue
        catalog_id = official_ids.get(name) or ('wakeup:' + hashlib.sha256(
            ('wakeup-school-v1' + chr(0) + name).encode()).hexdigest()[:20])
        if catalog_id in existing_ids:
            continue
        final_url = adm.get('probe', {}).get('finalUrl') or ''
        entry = {
            'catalogId': catalog_id, 'name': name, 'url': final_url,
            'source': 'wakeup-batch-probe', 'checkedAt': TODAY,
            'http': adm['probe'].get('http'),
            'finalUrl': final_url, 'status': 'unknown', 'confidence': 'low',
            'sources': [], 'proposedCorrection': None,
            'title': '', 'probeError': adm['probe'].get('error'),
            'availability': 'unavailable-or-restricted',
            'authenticatedImportTested': False,
        }
        cap = captures.get(final_url)
        if not final_url:
            # Session-bound (REDACTED) or unreachable URLs must not carry an
            # evidence row at all; the catalog keeps the school as pending.
            verdicts['no-url'] += 1
            continue
        if cap is None:
            entry['defects'] = ['Ownership not verified; URL sourced from WakeUp database. '
                                'No entry-page capture was obtained for this endpoint.']
            verdicts['no-capture'] += 1
        elif cap.get('http') == 200:
            title = sanitize_url(cap.get('title') or '')[0]
            observed = sanitize_url(cap.get('observedText') or '')[0]
            entry['http'] = 200
            entry['probeError'] = None
            entry['title'] = title
            entry['availability'] = 'public-page'
            parts = [p for p in identity_name(name).split(' - ') if p]
            head = title + ' ' + observed
            if parts and all(p in head for p in parts):
                source = {
                    'url': final_url, 'kind': 'entry-page',
                    'evidence': 'Public page text contains catalog school name.',
                    'checkedAt': TODAY,
                    'title': title,
                    'observedText': observed,
                    'bodySha256': cap.get('bodySha256', ''),
                    'linkedUrls': [],
                }
                if source_proves(source, name, final_url, renames):
                    entry['status'] = 'verified'
                    entry['confidence'] = 'medium'
                    entry['sources'] = [source]
                    entry['defects'] = []
                    verdicts['verified'] += 1
                else:
                    entry['defects'] = ['Entry page names the school but failed strict '
                                        'entry-page ownership revalidation.']
                    verdicts['unknown'] += 1
            else:
                entry['defects'] = ['Entry page identity does not name this school; '
                                    'ownership unproven.']
                verdicts['unknown'] += 1
        else:
            entry['http'] = cap.get('http', 0)
            entry['probeError'] = cap.get('error') or adm['probe'].get('error')
            entry['defects'] = ['Ownership not verified; URL sourced from WakeUp database. '
                                'Endpoint did not return a public HTTP 200 page at the '
                                'configured URL during the entry-evidence pass.']
            verdicts['unreachable'] += 1
        new_entries.append(entry)

    print(f'Merge verdicts: {dict(verdicts)}')
    evidence['entries'].extend(new_entries)
    counts = Counter(e['status'] for e in evidence['entries'])
    scope = dict(evidence.get('scope') or {})
    scope['records'] = len(evidence['entries'])
    scope['entryEvidencePass'] = TODAY
    summary = dict(evidence.get('summary') or {})
    summary.update({k: counts.get(k, 0) for k in ('verified', 'unknown', 'contradicted')})
    summary['entryEvidenceInterpretation'] = (
        '2026-10-16 batch entry-evidence pass recorded verified/medium verdicts only when the '
        'captured entry page names the exact catalog identity and passes source_proves; all '
        'other WakeUp-sourced endpoints remain unknown.')
    evidence['scope'] = scope
    evidence['summary'] = summary
    out = DATA / 'school_url_evidence.json'
    out.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
    print(f'Total evidence entries: {len(evidence["entries"])}; verdicts {dict(counts)}')


if __name__ == '__main__':
    main()
