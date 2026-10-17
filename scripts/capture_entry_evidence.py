#!/usr/bin/env python3
"""Capture entry-page ownership evidence for batch-probed admission URLs.

Second pass over batch probe results: fetch each configured endpoint exactly
(no redirects followed), extract sanitized visible text, and emit a
verified/unknown ownership evidence entry per catalog_ownership.source_proves
entry-page rules. Merges into scripts/data/school_url_evidence.json while
preserving all pre-existing review history entries.
"""
import argparse
import concurrent.futures
import hashlib
import json
import re
import subprocess
import tempfile
import urllib.parse
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'scripts/data'
UA = 'Mozilla/5.0'

# Same visibility rules as catalog_ownership.EvidenceHTMLParser
SCRIPT_STYLE = {'script', 'style'}


class TextParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.text = []
        self.title = []
        self.in_title = False
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in SCRIPT_STYLE:
            self.hidden += 1
        if tag == 'title':
            self.in_title = True

    def handle_endtag(self, tag):
        if tag in SCRIPT_STYLE:
            self.hidden = max(0, self.hidden - 1)
        if tag == 'title':
            self.in_title = False

    def handle_data(self, data):
        if not self.hidden:
            self.text.append(data)
        if self.in_title:
            self.title.append(data)


def identity_name(name):
    # Mirror catalog_ownership.identity_name
    return re.sub(r"（超星）|（新版）|（旧版）", "", name).strip()


def capture(url, timeout=10):
    """GET without following redirects; only an HTTP 200 at this exact endpoint qualifies."""
    result = {'url': url, 'http': 0, 'error': None}
    try:
        with tempfile.TemporaryDirectory() as d:
            header_path = Path(d) / 'h'
            body_path = Path(d) / 'b'
            proc = subprocess.run(
                ['curl', '--noproxy', '*',
                 '--connect-timeout', '4',
                 '--max-time', str(timeout),
                 '--max-filesize', '1500000',
                 '-sS', '-k', '-A', UA,
                 '-D', str(header_path),
                 '-o', str(body_path),
                 '-w', '%{http_code}',
                 url],
                capture_output=True, timeout=timeout + 5)
            code = int(proc.stdout) if (proc.stdout or b'').isdigit() else 0
            headers = header_path.read_text(errors='replace') if header_path.exists() else ''
            raw = body_path.read_bytes() if body_path.exists() else b''
        if proc.returncode != 0:
            result['error'] = f'curl-{proc.returncode}'
        result['http'] = code
        if code != 200:
            return result
        charset_match = re.search(r'charset=[\s"\']*([\w-]+)',
                                  headers + ' ' + raw[:3000].decode('ascii', errors='ignore'), re.I)
        charset = charset_match.group(1) if charset_match else 'utf-8'
        try:
            body = raw.decode(charset)
        except (LookupError, UnicodeDecodeError):
            try:
                body = raw.decode('gb18030', errors='replace')
            except Exception:
                body = raw.decode('utf-8', errors='replace')
        parser = TextParser()
        try:
            parser.feed(body)
            parser.close()
        except Exception:
            pass
        result['title'] = ' '.join(parser.title).strip()[:250]
        observed = ' '.join(t.strip() for t in parser.text if t.strip())
        result['observedText'] = observed[:2600]
        result['bodySha256'] = hashlib.sha256(raw).hexdigest()
    except subprocess.TimeoutExpired:
        result['error'] = 'timeout'
    except Exception as e:
        result['error'] = type(e).__name__ + ': ' + str(e)[:120]
    return result


def sanitize(text):
    from catalog_url_safety import sanitize_url
    safe, _ = sanitize_url(text)
    return safe


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--workers', type=int, default=5)
    ap.add_argument('--resume', action='store_true')
    ap.add_argument('--limit', type=int, default=0)
    args = ap.parse_args()

    admissions = json.loads((DATA / 'school_import_admissions.json').read_text())
    baseline = json.loads((DATA / 'school_catalog_baseline.json').read_text())
    official = json.loads((DATA / 'official_schools.json').read_text())
    renames = json.loads((DATA / 'school_catalog_renames.json').read_text())
    probes_file = DATA / 'batch-probes.jsonl'
    probes = {}
    if probes_file.exists():
        for line in probes_file.read_text().splitlines():
            if line.strip():
                p = json.loads(line)
                probes[p['requestedUrl']] = p

    def canonical(name):
        return renames.get(name.strip(), {}).get('newName', name.strip())

    baseline_names = {x['name'].strip() for x in baseline}
    official_ids = {src['name'].strip(): src['id'] for src in official}
    old_admission_names = {a['name'].strip() for a in admissions[:170]}

    evidence = json.loads((DATA / 'school_url_evidence.json').read_text())
    existing_ids = {e['catalogId'] for e in evidence['entries']}

    # New admissions only (batch additions, non-baseline, not already covered)
    targets = []
    for adm in admissions[170:]:
        name = canonical(adm['name'])
        if name in baseline_names:
            continue
        catalog_id = official_ids.get(name) or ('wakeup:' + hashlib.sha256(
            ('wakeup-school-v1' + chr(0) + name).encode()).hexdigest()[:20])
        if catalog_id in existing_ids:
            continue
        final_url = adm.get('probe', {}).get('finalUrl')
        if not final_url:
            continue
        targets.append((catalog_id, name, adm, final_url))

    print(f'New admissions needing evidence: {len(targets)}', flush=True)

    out_file = DATA / 'entry-evidence-captures.jsonl'
    done = {}
    if args.resume and out_file.exists():
        for line in out_file.read_text().split('\n'):
            if line.strip():
                c = json.loads(line)
                done[c['url']] = c
        print(f'Resumed {len(done)} captures', flush=True)

    # Only re-fetch endpoints that answered 200 in the batch pass
    todo = [t for t in targets if t[3] not in done and (t[2]['probe'].get('http') or 0) == 200]
    if args.limit:
        todo = todo[:args.limit]
    print(f'Fetching {len(todo)} endpoints...', flush=True)

    import threading
    write_lock = threading.Lock()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(capture, t[3]): t for t in todo}
        completed = 0
        for fut in concurrent.futures.as_completed(futures):
            catalog_id, name, adm, final_url = futures[fut]
            cap = fut.result()
            completed += 1
            with write_lock, open(out_file, 'a') as f:
                f.write(json.dumps(cap, ensure_ascii=False).replace('\x85', '\\u0085')
                               .replace('\u2028', '\\u2028').replace('\u2029', '\\u2029') + '\n')
                f.flush()
            if completed % 100 == 0:
                print(f'  {completed}/{len(todo)}', flush=True)

    captures = dict(done)
    if out_file.exists():
        for line in out_file.read_text().split('\n'):
            if line.strip():
                c = json.loads(line)
                captures[c['url']] = c
    print(f'Captures available: {len(captures)}', flush=True)


    print('Capture phase complete. Journal:', out_file, flush=True)


if __name__ == '__main__':
    main()
