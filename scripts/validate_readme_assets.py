#!/usr/bin/env python3
"""Validate README images and reviewer hashes. Exit 0=pass, 1=error, 2=pending."""
import argparse
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]

class Images(HTMLParser):
    def __init__(self):
        super().__init__()
        self.sources = []

    def handle_starttag(self, tag, attrs):
        if tag == 'img':
            self.sources.extend(v for k, v in attrs if k == 'src' and v)

def references(path):
    content = path.read_text(encoding='utf-8')
    parser = Images()
    parser.feed(content)
    sources = parser.sources + re.findall(r'!\[[^\]]*\]\(\s*<?([^\s)>]+)>?(?:\s+[^)]*)?\)', content)
    result = set()
    for source in sources:
        url = urlsplit(source)
        if url.scheme or url.netloc:
            continue
        resolved = (path.parent / unquote(url.path)).resolve()
        result.add(resolved.relative_to(ROOT).as_posix())
    return result

def validate():
    errors, pending, notes = [], [], []
    try:
        from PIL import Image
    except ImportError:
        return {'status': 'error', 'errors': ['Pillow required in screenshot tooling environment'], 'pending': []}
    sets = {name: references(ROOT / name) for name in ('README.md', 'README_EN.md')}
    if sets['README.md'] != sets['README_EN.md']:
        for name, other in [('README.md', 'README_EN.md'), ('README_EN.md', 'README.md')]:
            errors.append(f"Only in {name}: {sorted(sets[name] - sets[other])}")
    images = set.union(*sets.values())
    acceptance = ROOT / 'docs/readme-refresh/VISUAL-ACCEPTANCE.json'
    records = json.loads(acceptance.read_text()) if acceptance.exists() else {}
    if not records:
        pending.append('Visual acceptance records absent or empty')
    dimensions = {}
    for relative in sorted(images | {f'docs/screenshots/{name}' for name in records}):
        path = ROOT / relative
        if not path.is_file():
            errors.append(f'Missing asset: {relative}')
            continue
        try:
            with Image.open(path) as im:
                if path.suffix.lower() == '.png' and im.format != 'PNG':
                    raise ValueError('PNG extension has a different image format')
                im.verify()
            with Image.open(path) as im:
                im.load()
                dimensions[relative] = list(im.size)
        except Exception as exc:
            errors.append(f'Invalid image {relative}: {exc}')
    for name, record in sorted(records.items()):
        relative = f'docs/screenshots/{name}'
        path = ROOT / relative
        if not path.is_file():
            continue
        if record.get('sha256') != hashlib.sha256(path.read_bytes()).hexdigest():
            errors.append(f'Acceptance SHA mismatch: {relative}')
        if record.get('size') != dimensions.get(relative):
            errors.append(f'Acceptance size mismatch: {relative}')
        if not str(record.get('result', '')).startswith('accepted:'):
            pending.append(f'Not accepted: {relative}: {record.get("result", "missing result")}')
        if relative not in images:
            notes.append(f'Candidate not yet referenced: {relative}')
    screenshots = sorted(p for p in images if p.startswith('docs/screenshots/'))
    for relative in screenshots:
        if Path(relative).name not in records:
            pending.append(f'Awaiting visual acceptance: {relative}')
    matrix = ROOT / 'docs/readme-refresh/CAPTURE-MATRIX.md'
    if matrix.exists():
        for line in matrix.read_text().splitlines():
            cells = [part.strip() for part in line.split('|')]
            if len(cells) < 4:
                continue
            for name in re.findall(r'(?<![A-Za-z0-9_-])(?:[0-9]{2}-[a-z][a-z0-9-]*|widget-[a-z][a-z0-9-]*)(?:[.]png)?', cells[2]):
                name = name if name.endswith('.png') else name + '.png'
                relative = 'docs/screenshots/' + name
                if not (ROOT / relative).is_file():
                    pending.append(f'Planned capture missing: {relative}')
                elif name not in records:
                    item = f'Awaiting visual acceptance: {relative}'
                    if item not in pending:
                        pending.append(item)
                if relative not in images:
                    pending.append(f'Planned capture not referenced in both READMEs: {relative}')
    return {'status': 'error' if errors else 'pending' if pending else 'pass',
            'readme_local_images': {k: len(v) for k, v in sets.items()},
            'referenced_screenshots': len(screenshots), 'acceptance_records': len(records),
            'validated_images': len(dimensions), 'errors': errors, 'pending': pending, 'notes': notes}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--json', action='store_true')
    args = parser.parse_args()
    try:
        report = validate()
    except Exception as exc:
        report = {'status': 'error', 'errors': [f'Validation could not complete: {exc}'], 'pending': []}
    print(json.dumps(report, ensure_ascii=False, indent=2) if args.json else report)
    return {'pass': 0, 'error': 1, 'pending': 2}[report['status']]

if __name__ == '__main__':
    sys.exit(main())
