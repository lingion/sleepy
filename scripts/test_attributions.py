#!/usr/bin/env python3
"""Check attribution data, website exports, documents and shipped notices."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
LOCALES = ('values', 'values-zh-rCN', 'values-zh-rTW', 'values-en', 'values-ja', 'values-es')


class AttributionCoverageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads((ASSETS / 'ATTRIBUTIONS.json').read_text())
        cls.entries = cls.data['entries']

    def test_each_source_has_traceable_evidence(self):
        names = [entry['project'].casefold() for entry in self.entries]
        self.assertEqual(len(names), len(set(names)))
        self.assertEqual(set(self.data['groups']), {entry['group'] for entry in self.entries})
        for entry in self.entries:
            with self.subTest(project=entry['project']):
                for key in ('author', 'license', 'kind', 'usage', 'evidence', 'verification'):
                    self.assertTrue(entry[key])
                self.assertTrue(entry['url'].startswith('https://'))
                for path in entry['evidence']:
                    self.assertTrue((ROOT / path).is_file(), path)

    def test_website_export_preserves_inventory_history_and_original_notices(self):
        from export_licenses import export
        with tempfile.TemporaryDirectory() as directory:
            site = Path(directory)
            data = export(site)
            self.assertEqual(self.data, data['manifest'])
            self.assertEqual(set(LOCALES), set(data['legacy']['locales']))
            self.assertEqual(set(LOCALES), set(data['labels']))
            self.assertTrue(data['legacy']['contributors'])
            self.assertTrue(data['legacy']['schools'])
            self.assertEqual((site / 'src/data/licenses.json').read_bytes(),
                             (site / 'public/licenses/inventory.json').read_bytes())
            for notice in self.data['notices']:
                self.assertEqual((ASSETS / notice['path']).read_bytes(),
                                 (site / 'public/licenses/notices' / notice['path']).read_bytes())
            export(site, check=True)

    def test_original_license_texts_have_not_been_truncated(self):
        for notice in self.data['notices']:
            data = (ASSETS / notice['path']).read_bytes()
            self.assertEqual(notice['sha256'], hashlib.sha256(data).hexdigest(), notice['path'])
        by_name = {x['project']: x for x in self.entries}
        required = {'flutter/flutter': 'BSD-3-Clause', 'chen08209/FlClash': 'GPL-3.0',
                    'Kyant0/AndroidLiquidGlass': 'Apache-2.0',
                    'xiaomanjun233/SleepDown-Schedule': 'SleepDown source-available non-commercial 1.1'}
        for project, license_name in required.items():
            self.assertEqual(license_name, by_name[project]['license'])
            self.assertTrue((ASSETS / by_name[project]['license_file']).is_file())
        self.assertIn('Copyright 2013 The Flutter Authors.', (ASSETS / by_name['flutter/flutter']['license_file']).read_text())

    def test_local_document_links_resolve(self):
        for name in ('ACKNOWLEDGEMENTS.md', 'docs/attribution-audit/glass-research-sources.md',
                     'docs/attribution-audit/platform-sources.md'):
            file = ROOT / name
            for link in re.findall(r'\]\(([^)]+)\)', file.read_text()):
                if '://' not in link and not link.startswith('#'):
                    self.assertTrue((file.parent / link.split('#')[0]).exists(), (name, link))

    def test_research_does_not_silently_become_a_dependency(self):
        by_name = {x['project']: x for x in self.entries}
        self.assertEqual('historical', by_name['chrisbanes/haze']['kind'])
        self.assertEqual('glass-research', by_name['chrisbanes/haze']['group'])
        self.assertEqual('behavior', by_name['HaoZai000/NexioSchedule']['kind'])
        self.assertEqual('source-assets', by_name['xiaomanjun233/SleepDown-Schedule']['kind'])
        self.assertIn('not confirmed', by_name['DataV administrative boundaries']['license'])

    def test_generated_documents_and_resources_are_current(self):
        result = subprocess.run([sys.executable, str(ROOT / 'scripts/generate_attributions.py'), '--check'],
                                capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
