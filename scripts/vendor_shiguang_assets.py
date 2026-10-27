#!/usr/bin/env python3
"""Vendor shiguang_warehouse adapter JS into app assets with a school manifest.

Source of truth: a local clone of https://github.com/ShiGuangSchedule/
shiguang_warehouse (MIT, Copyright (c) 2025 星河欲转). The clone lives at
~/sleepy-worktrees/shiguang-source/clone-<date>/ (persistent, pinned commit;
recorded in the manifest). This script never downloads.

Output layout (all committed):
  app/src/main/assets/third_party/shiguang/LICENSE-MIT.txt   vendored license
  app/src/main/assets/third_party/shiguang/js/<FOLDER>/<file>.js  adapter scripts
  app/src/main/assets/third_party/shiguang/manifest.json     school -> scripts
  scripts/types/shiguang-bridge.d.ts   vendored bridge contract (dev-only,
                                       not packaged; SgAssetVendorInvariantTest
                                       reads it as the method-name source of truth)

Manifest keeps only adapters that (a) declare an import_url and (b) resolve to
an existing JS file — the empty component-test/demo adapters are excluded.
School entries are keyed by the warehouse's own school name (trimmed);
SgAssetVendorInvariantTest asserts the manifest covers the full gap-rows 264
school set by the same NFKC normalization the promotion script uses.

Re-running is idempotent: same inputs produce byte-identical outputs
(sorted keys, fixed JSON formatting), so CI --check style verification works.
"""
import argparse
import hashlib
import json
import shutil
import unicodedata
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/main/assets/third_party/shiguang'


def nfk(name: str) -> str:
    return unicodedata.normalize('NFKC', name.strip())


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(source: Path) -> dict:
    index = yaml.safe_load((source / 'index/root_index.yaml').read_text())
    schools_out = {}
    js_seen = {}
    for entry in index['schools']:
        folder = source / 'resources' / entry['resource_folder']
        adapters = yaml.safe_load((folder / 'adapters.yaml').read_text())
        items = adapters.get('adapters') if isinstance(adapters, dict) else adapters
        for a in items or []:
            import_url = str(a.get('import_url') or '').strip()
            if not import_url:
                continue
            js_rel = str(a.get('asset_js_path') or '').strip()
            js_src = folder / js_rel
            if not js_src.exists():
                raise SystemExit(f'missing adapter js: {entry["id"]}/{js_rel}')
            dest_rel = f'js/{entry["resource_folder"]}/{Path(js_rel).name}'
            digest = sha256(js_src)
            js_seen.setdefault(dest_rel, digest)
            if digest != js_seen[dest_rel]:
                raise SystemExit(f'path collision with different bytes: {dest_rel}')
            key = nfk(entry['name'])
            record = {
                'sgId': entry['id'],
                'sgName': entry['name'],
                'adapters': [],
            }
            prev = schools_out.get(key)
            if prev:
                prev['adapters'].append({
                    'adapterId': a.get('adapter_id'),
                    'adapterName': a.get('adapter_name'),
                    'category': a.get('category'),
                    'jsPath': f'third_party/shiguang/{dest_rel}',
                    'importUrl': import_url,
                    'maintainer': a.get('maintainer'),
                })
            else:
                rec = dict(record)
                rec['adapters'] = [{
                    'adapterId': a.get('adapter_id'),
                    'adapterName': a.get('adapter_name'),
                    'category': a.get('category'),
                    'jsPath': f'third_party/shiguang/{dest_rel}',
                    'importUrl': import_url,
                    'maintainer': a.get('maintainer'),
                }]
                schools_out[key] = rec
    manifest = {
        'schema': 'sleepy-sg-asset-manifest-v1',
        'source': {
            'repo': 'ShiGuangSchedule/shiguang_warehouse',
            'license': 'MIT',
            'licenseHolder': '星河欲转',
            'cloneCommit': (source / '.git_commit').read_text().strip()
            if (source / '.git_commit').exists() else None,
        },
        'schools': [schools_out[k] | {'matchKey': k} for k in sorted(schools_out)],
        'scripts': [{'path': p, 'sha256': js_seen[p]} for p in sorted(js_seen)],
    }
    return manifest, js_seen, source


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--source', default=str(Path.home() / 'sleepy-worktrees/shiguang-source/clone-2026-10-27'))
    ap.add_argument('--check', action='store_true', help='verify committed outputs match, write nothing')
    args = ap.parse_args()
    source = Path(args.source)
    if not source.exists():
        raise SystemExit(f'vendor source clone missing: {source}')
    manifest, js_seen, source = build(source)

    if args.check:
        committed = json.loads((OUT / 'manifest.json').read_text())
        assert committed == manifest, 'committed manifest differs from clone; re-run vendor script'
        dts_committed = ROOT / 'scripts/types/shiguang-bridge.d.ts'
        assert (source / 'scripts/types/shiguang-bridge.d.ts').read_bytes() == (
            dts_committed.read_bytes() if dts_committed.exists() else b''), \
            'committed shiguang-bridge.d.ts differs from clone; re-run vendor script'
        for entry in manifest['scripts']:
            p = OUT / entry['path']
            assert p.exists() and sha256(p) == entry['sha256'], f'asset drifted: {entry["path"]}'
        print(json.dumps({'schools': len(manifest['schools']), 'scripts': len(js_seen), 'check': 'ok'}))
        return

    (OUT / 'js').mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source / 'LICENSE', OUT / 'LICENSE-MIT.txt')
    # 桥合同 d.ts 随 vendor 落仓 (dev-only, 不进 APK; 不变量测试的唯一方法名真源)
    types_out = ROOT / 'scripts/types'
    types_out.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source / 'scripts/types/shiguang-bridge.d.ts',
                    types_out / 'shiguang-bridge.d.ts')
    copied = 0
    for entry in manifest['scripts']:
        rel = entry['path'].removeprefix('third_party/shiguang/')
        src = source / 'resources' / rel.removeprefix('js/')
        dst = OUT / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        if not dst.exists() or sha256(dst) != entry['sha256']:
            shutil.copyfile(src, dst)
        copied += 1
    (OUT / 'manifest.json').write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=False) + '\n')
    print(json.dumps({'schools': len(manifest['schools']), 'scriptsCopied': copied}))


if __name__ == '__main__':
    main()
