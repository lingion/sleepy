#!/usr/bin/env python3
"""Export the complete attribution page and original notices to sleepy-site."""
import argparse
import hashlib
import json
from pathlib import Path

from generate_attributions import LOCALES, ROOT


def export(site, check=False):
    assets = ROOT / 'app/src/main/assets'
    manifest = json.loads((assets / 'ATTRIBUTIONS.json').read_text())
    legacy = json.loads((ROOT / 'docs/attribution-audit/legacy-credits.json').read_text())
    data = {
        'manifest': manifest,
        'legacy': legacy,
        'labels': {locale: {'title': title, 'groups': groups, 'introduction': intro, 'notice': notice}
                   for locale, (title, groups, intro, notice) in LOCALES.items()},
    }
    payload = (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode()
    files = {
        site / 'src/data/licenses.json': payload,
        site / 'public/licenses/inventory.json': payload,
        site / 'public/licenses/notices/Sleepy-GPL-3.0.txt': (ROOT / 'LICENSE').read_bytes(),
    }
    for notice in manifest['notices']:
        content = (assets / notice['path']).read_bytes()
        if hashlib.sha256(content).hexdigest() != notice['sha256']:
            raise ValueError(f'Notice fingerprint changed: {notice["path"]}')
        files[site / 'public/licenses/notices' / notice['path']] = content
    stale = []
    for path, content in files.items():
        if check:
            if not path.is_file() or path.read_bytes() != content:
                stale.append(str(path.relative_to(site)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if stale:
        raise ValueError('License exports differ: ' + ', '.join(stale))
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--site', type=Path, required=True)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = export(args.site.resolve(), args.check)
    print(f'License page {"verified" if args.check else "exported"}: '
          f'{len(data["manifest"]["entries"])} source records, '
          f'{len(data["legacy"]["contributors"])} contributors, '
          f'{len(data["manifest"]["notices"])} original notices.')


if __name__ == '__main__':
    main()
