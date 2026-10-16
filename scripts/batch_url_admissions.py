#!/usr/bin/env python3
"""Batch HTTP probe for WakeUp-sourced school URLs → admissions entries.

Matches pending catalog schools to WakeUp sourceUrls by exact name,
probes each URL, fingerprints the protocol type, and generates
school_import_admissions.json entries.
"""
import argparse
import concurrent.futures
import hashlib
import html
import json
import re
import socket
import subprocess
import ipaddress
import tempfile
import time
import urllib.parse
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'scripts/data'
DATE = '2026-10-16'
UA = 'Mozilla/5.0'


class TitleParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.text = []
        self.skip = 0
        self.title = []
        self.in_title = False

    def handle_starttag(self, tag, attrs):
        if tag in ('script', 'style'):
            self.skip += 1
        if tag == 'title':
            self.in_title = True

    def handle_endtag(self, tag):
        if tag in ('script', 'style'):
            self.skip = max(0, self.skip - 1)
        if tag == 'title':
            self.in_title = False

    def handle_data(self, data):
        if not self.skip:
            self.text.append(data)
        if self.in_title:
            self.title.append(data)


def clean_url(url):
    """Strip query/fragment for comparison; return None for invalid URLs."""
    try:
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username:
            return None
        return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, parsed.path, '', ''))
    except Exception:
        return None


def is_public_url(url):
    """Check URL resolves to a public (non-private) IP."""
    try:
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme not in ('http', 'https') or parsed.username:
            return False
        hostname = parsed.hostname
        port = parsed.port or (443 if parsed.scheme == 'https' else 80)
        infos = socket.getaddrinfo(hostname, port, type=socket.SOCK_STREAM)
        return any(ipaddress.ip_address(info[4][0]).is_global for info in infos)
    except Exception:
        return False


def detect_type_from_url(url):
    """Mirror JwImportViewModel.detectProtocolFromUrlImpl for fingerprinting."""
    u = url.lower()

    # CAS / authserver gateway — not a protocol fingerprint
    if re.match(r'.*/cas/login.*', u) or re.match(r'.*/authserver/login.*', u):
        return None

    # WebVPN — detect inner type if possible
    if '/webvpn/' in u or re.search(r'/http/[0-9a-f]+/', u) or '.webvpn.' in u:
        if 'jwglxt' in u or re.search(r'.*/xtgl(/|$).*', u) or '/kbcx/' in u:
            return 'zf_new'
        if 'default2.aspx' in u or 'xskbcx.aspx' in u:
            return 'zf'
        if '/jsxsd/' in u or re.search(r'.*/jxd(/|$).*', u) or 'logon.do' in u:
            return 'qz'
        return None

    # WISEDU
    if '/jwapp/' in u:
        return 'wisedu'

    # Classic EAMS
    if 'coursetableforstd' in u or '/edu/lesson/std/timetable' in u:
        return 'classic_eams'

    # EAMS5
    if ('jxglstu' in u or '/eams5-student' in u or '/for-std/' in u
            or re.search(r'.*/for-std(/|$).*', u)):
        return 'eams5'

    # ZF_NEW
    if ('jwglxt' in u or re.search(r'.*/xtgl(/|$).*', u)
            or '/kbcx/' in u or 'xskbcx_cx' in u):
        return 'zf_new'

    # ZF (old)
    if 'default2.aspx' in u or 'xskbcx.aspx' in u:
        return 'zf'

    # QZ
    if '/jsxsd/' in u or re.search(r'.*/jxd(/|$).*', u) or 'logon.do' in u or 'verifycode.servlet' in u:
        return 'qz'

    # URP
    if 'xkaction.do' in u or 'actiontype=6' in u:
        return 'urp'

    # URP_NEW
    if 'thissemestercurriculum' in u or 'courseselect' in u or 'ajaxstudentschedule' in u:
        return 'urp_new'

    # CF
    if '/xsgrkbcx' in u or '/new/xskb' in u:
        return 'cf'

    # CHAOXING
    if re.search(r'.*/xsd(/|$).*', u) or 'querykbforgrdb' in u:
        return 'chaoxing'

    # PKU
    if 'elective.pku' in u or 'iaaa.pku' in u:
        return 'pku'

    # HNUST
    if 'kdjw.hnust' in u or 'xxjw.hnust' in u:
        return 'hnust'

    # Fallback: use sourceType from WakeUp as hint
    return None


def detect_type_from_html(body_text):
    """Mirror JwImportViewModel.detectProtocolFromHtmlImpl for fallback."""
    if not body_text:
        return None
    lower = body_text.lower()

    # ZF_NEW fingerprints
    if 'jwglxt' in lower or 'xtgl' in lower:
        if '教学管理信息服务平台' in body_text or '正方' in body_text:
            return 'zf_new'

    # ZF fingerprints
    if 'default2.aspx' in lower or 'xskbcx.aspx' in lower:
        return 'zf'
    if 'xs_main' in lower and 'aspx' in lower:
        return 'zf'

    # QZ fingerprints
    if '强智' in body_text or 'jsxsd' in lower:
        return 'qz'
    if 'logon.do' in lower and ('课表' in body_text or '教务' in body_text):
        return 'qz'

    # URP
    if 'urp' in lower and ('课表' in body_text or '教务' in body_text):
        return 'urp_new'

    # WISEDU
    if 'jwapp' in lower or '金智' in body_text:
        return 'wisedu'

    # Classic EAMS
    if 'eams' in lower and 'course' in lower:
        return 'classic_eams'

    return None


def probe_url(url, timeout=10):
    """HTTP GET probe with redirect following."""
    result = {
        'requestedUrl': url,
        'checkedAt': DATE,
        'redirects': [],
    }
    current = url

    # Early check for private IPs
    if not is_public_url(current):
        result['error'] = 'non-public-url'
        return result

    try:
        for step in range(6):
            if not is_public_url(current):
                result['error'] = 'non-public-url'
                return result

            with tempfile.TemporaryDirectory() as d:
                header_path = Path(d) / 'h'
                body_path = Path(d) / 'b'
                proc = subprocess.run(
                    [
                        'curl', '--noproxy', '*',
                        '--connect-timeout', '4',
                        '--max-time', str(timeout),
                        '--max-filesize', '1500000',
                        '-sS', '-k', '-A', UA,  # -k: skip SSL verify (many school certs are bad)
                        '-D', str(header_path),
                        '-o', str(body_path),
                        '-w', '%{http_code}',
                        current,  # URL must be last argument
                    ],
                    capture_output=True,
                    timeout=timeout + 5,
                )
                code = int(proc.stdout or 0) if proc.stdout else 0
                headers = header_path.read_text(errors='replace') if header_path.exists() else ''
                raw = body_path.read_bytes() if body_path.exists() else b''

            # Handle curl errors
            if proc.returncode != 0:
                result['http'] = code
                result['finalUrl'] = clean_url(current)
                result['bytes'] = len(raw)
                result['error'] = f'curl-{proc.returncode}'
                # Still try to fingerprint from URL
                fingerprints = []
                url_type = detect_type_from_url(current)
                if url_type:
                    fingerprints.append(url_type)
                result['fingerprints'] = fingerprints
                result['urlHints'] = []
                return result

            result['http'] = code
            result['finalUrl'] = clean_url(current)
            result['bytes'] = len(raw)

            # Handle redirects
            if code in (301, 302, 303, 307, 308):
                loc_match = re.search(r'^location:\s*(.*?)\s*$', headers, re.I | re.M)
                if loc_match:
                    next_url = urllib.parse.urljoin(current, loc_match.group(1))
                    result['redirects'].append({
                        'http': code,
                        'from': clean_url(current),
                        'to': clean_url(next_url),
                    })
                    current = next_url
                    continue

            # Detect charset
            charset_match = re.search(
                r'charset=[\s"\']*([\w-]+)',
                headers + ' ' + raw[:3000].decode('ascii', errors='ignore'),
                re.I,
            )
            charset = charset_match.group(1) if charset_match else 'utf-8'
            try:
                body = raw.decode(charset)
            except (LookupError, UnicodeDecodeError):
                try:
                    body = raw.decode('gb18030', errors='replace')
                except Exception:
                    body = raw.decode('utf-8', errors='replace')

            # Parse title
            parser = TitleParser()
            try:
                parser.feed(body)
                parser.close()
            except Exception:
                pass
            result['title'] = ' '.join(parser.title).strip()[:250]

            # Fingerprints
            fingerprints = []
            url_type = detect_type_from_url(current)
            if url_type:
                fingerprints.append(url_type)
            html_type = detect_type_from_html(body[:50000])
            if html_type and html_type not in fingerprints:
                fingerprints.append(html_type)
            result['fingerprints'] = fingerprints

            # URL hints
            url_hints = []
            if 'jwglxt' in current.lower():
                url_hints.append('zf_new')
            elif 'default2.aspx' in current.lower():
                url_hints.append('zf')
            elif 'jsxsd' in current.lower():
                url_hints.append('qz')
            result['urlHints'] = url_hints

            result['bodySha256'] = hashlib.sha256(raw).hexdigest()
            return result

        result['error'] = 'redirect-limit'
    except subprocess.TimeoutExpired:
        result['error'] = 'timeout'
    except Exception as e:
        result['error'] = type(e).__name__ + ': ' + str(e)[:120]
    return result


def determine_mapped_type(probe_result, source_type):
    """Determine the mappedType from probe fingerprints and source type."""
    fps = probe_result.get('fingerprints', [])
    title = probe_result.get('title', '')
    
    # Use fingerprints first
    if fps:
        return fps[0]

    # Try to detect from title
    if '正方' in title or '教学管理信息服务平台' in title:
        if 'jwglxt' in probe_result.get('finalUrl', '').lower():
            return 'zf_new'
        return 'zf'
    if '强智' in title:
        return 'qz'
    if 'KINGOSOFT' in title or '金智' in title:
        return 'kingo_new'
    if '超星' in title:
        return 'chaoxing'
    if '青果' in title:
        return 'cf'

    # Fallback to sourceType mapping
    source_type_map = {
        'zf': 'zf_new',  # Most zf sources are actually zf_new
        'qz': 'qz',
        'shuwei_json': 'classic_eams',
        'jz': 'qz',
        'qz_with_node': 'qz_with_node',
        'qz_2024': 'qz',
        'qz_2017': 'qz',
        'qz_old': 'qz_old',
        'qz_crazy': 'qz_crazy',
        'qz_br': 'qz_br',
        'urp': 'urp',
        'urp_new': 'urp_new',
        'cf': 'cf',
        'cf_new': 'cf_new',
        'chaoxing': 'chaoxing',
        'login_chaoxing': 'chaoxing',
        'chaoxing_share': 'chaoxing',
        'wisedu': 'wisedu',
        'kingo_new': 'kingo_new',
        'ziyan': 'ziyan',
        'south_soft': 'south_soft',
        'shuwei_new': 'shuwei_new',
        'shuwei_m': 'shuwei_m',
        'hit': 'hit',
        'hitsz': 'hitsz',
        'buaa': 'buaa',
        'cqu': 'cqu',
        'cqupt': 'cqupt',
        'pku': 'pku',
        'thu': 'thu',
        'fdu': 'fdu',
        'zju_post': 'zju_post',
        'uestc_shuwei': 'uestc_shuwei',
        'scau': 'scau',
        'hrbeu_post': 'hrbeu_post',
    }
    return source_type_map.get(source_type, source_type)


def load_data():
    """Load all required data files."""
    schools = json.loads((ROOT / 'app/src/main/assets/schools.json').read_text())
    wakeup = json.loads((DATA / 'wakeup_school_sources.json').read_text())
    renames = json.loads((DATA / 'school_catalog_renames.json').read_text())
    admissions = json.loads((DATA / 'school_import_admissions.json').read_text())
    return schools, wakeup, renames, admissions


def find_matches(schools, wakeup, renames, admissions):
    """Find pending schools that match WakeUp records by exact name."""
    pending_names = {s['name'] for s in schools if s.get('status') == 'pending'}
    admission_names = {a['name'].strip() for a in admissions}

    wakeup_by_name = {}
    for r in wakeup['records']:
        name = renames.get(r['name'].strip(), {}).get('newName', r['name'].strip())
        if r.get('sourceUrl'):
            wakeup_by_name[name] = r

    matches = []
    for name in sorted(pending_names):
        if name in wakeup_by_name and name not in admission_names:
            matches.append((name, wakeup_by_name[name]))

    return matches


def build_admission_entry(name, wakeup_record, probe_result):
    """Build a school_import_admissions.json entry."""
    mapped_type = determine_mapped_type(probe_result, wakeup_record.get('sourceType', ''))
    http_status = probe_result.get('http', 0)

    # Determine status
    if http_status and 200 <= http_status < 400:
        status = 'unverified'
    else:
        status = 'pending'

    entry = {
        'sourceId': wakeup_record['sourceId'],
        'sourceRow': wakeup_record['sourceRow'],
        'name': name,
        'sourceName': wakeup_record.get('sourceName', name),
        'sortKey': wakeup_record.get('sortKey', ''),
        'sourceUrl': wakeup_record['sourceUrl'],
        'sourceType': wakeup_record.get('sourceType', ''),
        'sourceImportType': wakeup_record.get('sourceImportType', 'wakeup'),
        'sourceCustomConf': {'androidZiyanType': ''},
        'sourceMode': '',
        'sourceMinVersion': 0,
        'mappedType': mapped_type,
        'probe': {
            'url': wakeup_record['sourceUrl'],
            'checkedAt': DATE,
            'status': status,
            'fingerprints': probe_result.get('fingerprints', []),
            'http': http_status,
            'finalUrl': probe_result.get('finalUrl', wakeup_record['sourceUrl']),
            'bytes': probe_result.get('bytes', 0),
            'title': probe_result.get('title', ''),
            'urlHints': probe_result.get('urlHints', []),
        },
        'status': status,
        'verification': 'Current public page establishes entry and parser-family evidence only; no authenticated timetable import performed.',
    }

    if probe_result.get('error'):
        entry['probe']['error'] = probe_result['error']

    return entry


def main():
    parser = argparse.ArgumentParser(description='Batch URL admissions probe')
    parser.add_argument('--workers', type=int, default=5, help='Concurrent probe workers')
    parser.add_argument('--limit', type=int, default=0, help='Limit number of probes (0=all)')
    parser.add_argument('--resume', action='store_true', help='Resume from existing probes')
    parser.add_argument('--dry-run', action='store_true', help='Only show matches, no probing')
    args = parser.parse_args()

    print('Loading data...', flush=True)
    schools, wakeup, renames, admissions = load_data()

    print('Finding matches...', flush=True)
    matches = find_matches(schools, wakeup, renames, admissions)
    print(f'Found {len(matches)} matchable pending schools', flush=True)

    if args.limit:
        matches = matches[:args.limit]
        print(f'Limited to {len(matches)} matches', flush=True)

    if args.dry_run:
        # Show sample matches
        for name, rec in matches[:10]:
            print(f'  {name} -> {rec["sourceUrl"][:60]}... ({rec["sourceType"]})')
        return

    # Load existing probes if resuming
    probes_file = ROOT / 'scripts/data/batch-probes.jsonl'
    done_probes = {}
    if args.resume and probes_file.exists():
        for line in probes_file.read_text().splitlines():
            if line.strip():
                p = json.loads(line)
                done_probes[p['requestedUrl']] = p
        print(f'Resumed {len(done_probes)} existing probes', flush=True)

    # Filter to URLs not yet probed
    todo = [(name, rec) for name, rec in matches if rec['sourceUrl'] not in done_probes]
    print(f'Probing {len(todo)} URLs ({len(done_probes)} cached)...', flush=True)

    # Probe URLs
    new_entries = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        future_to_match = {
            executor.submit(probe_url, rec['sourceUrl']): (name, rec)
            for name, rec in todo
        }

        completed = 0
        for future in concurrent.futures.as_completed(future_to_match):
            name, rec = future_to_match[future]
            probe_result = future.result()
            completed += 1

            # Append to probes file
            with open(probes_file, 'a') as f:
                f.write(json.dumps(probe_result, ensure_ascii=False) + '\n')

            # Build admission entry
            entry = build_admission_entry(name, rec, probe_result)
            new_entries.append(entry)

            if completed % 50 == 0:
                print(f'  Probed {completed}/{len(todo)}...', flush=True)

    # Also build entries for cached probes
    for name, rec in matches:
        if rec['sourceUrl'] in done_probes:
            entry = build_admission_entry(name, rec, done_probes[rec['sourceUrl']])
            new_entries.append(entry)

    print(f'\nGenerated {len(new_entries)} admission entries', flush=True)

    # Stats
    status_counts = Counter(e['status'] for e in new_entries)
    type_counts = Counter(e['mappedType'] for e in new_entries)
    print(f'Status: {dict(status_counts)}', flush=True)
    print(f'Types: {dict(type_counts.most_common(10))}', flush=True)

    # Merge with existing admissions
    existing_names = {a['name'] for a in admissions}
    merged = admissions + [e for e in new_entries if e['name'] not in existing_names]
    print(f'Merged: {len(admissions)} + {len(merged) - len(admissions)} = {len(merged)}', flush=True)

    # Write merged admissions
    output_path = DATA / 'school_import_admissions.json'
    output_path.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + '\n')
    print(f'Wrote {output_path}', flush=True)


if __name__ == '__main__':
    main()
