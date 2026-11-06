#!/usr/bin/env python3
"""Check attribution completeness across the manifest, UI, documents and shipped notices."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import unittest
import xml.etree.ElementTree as ET

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

    def test_all_sources_are_visible_in_every_locale(self):
        for locale in LOCALES:
            strings = {node.attrib['name']: node.text or '' for node in ET.parse(
                ROOT / 'app/src/main/res' / locale / 'attributions.xml').getroot()}
            for entry in self.entries:
                with self.subTest(locale=locale, project=entry['project']):
                    key = 'license_recent_' + entry['group'].replace('-', '_')
                    self.assertTrue(strings.get(key + '_title'))
                    for value in (entry['project'], entry['license'], entry['url']):
                        self.assertIn(value.replace("'", "\\'"), strings.get(key + '_body', ''))
            self.assertIn('xiaomanjun233', strings['license_recent_source_notice'])
            self.assertIn('BSD-3-Clause', strings['license_recent_source_notice'])
            for name in self.data['unresolved_names']:
                self.assertIn(name, strings['license_recent_background_body'])

    def test_ui_groups_follow_document_order_and_notice_is_always_visible(self):
        source = (ROOT / 'app/src/main/java/com/lingion/sleepy/ui/screen/mine/LicenseScreen.kt').read_text()
        section = source.split('private val recentAttributionGroups = listOf(', 1)[1].split('\n)', 1)[0]
        actual = re.findall(r'R.string.license_recent_(\w+)_title', section)
        self.assertEqual([g.replace('-', '_') for g in self.data['groups']], actual)
        notice_pos = source.index('text = stringResource(R.string.license_recent_source_notice)')
        self.assertLess(notice_pos, source.index('items(recentAttributionGroups'))
        self.assertLess(source.index('items = contributorEntries'), notice_pos)
        self.assertLess(notice_pos, source.index('items = attributionEntries'))

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

    def test_readmes_point_to_full_inventory_without_obsolete_counts(self):
        for name in ('README.md', 'README_EN.md'):
            text = (ROOT / name).read_text()
            self.assertIn('(ACKNOWLEDGEMENTS.md)', text)
            self.assertIn('xiaomanjun233', text)
            self.assertNotIn('9 个跨校普适项目', text)
            self.assertNotIn('9 foundational', text)
            self.assertNotIn('9 cross-school', text)

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
