"""Guard explicit route provenance without promoting offline evidence to verified imports."""
import json
import unittest
from pathlib import Path

from routable_types import routable_types

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return json.loads((ROOT / path).read_text())


class DeclaredProtocolRoutesTest(unittest.TestCase):
    def test_reviewed_routes_match_every_referenced_source_row(self):
        source = {(r['sourceId'], r['sourceRow']): r for r in
                  read('scripts/data/wakeup_school_sources.json')['records']}
        renames = read('scripts/data/school_catalog_renames.json')
        routes = routable_types()
        reviewed = [a for a in read('scripts/data/school_import_admissions.json')
                    if a.get('protocolRouteReview')]
        self.assertTrue(reviewed)
        for admission in reviewed:
            with self.subTest(school=admission['name']):
                review = admission['protocolRouteReview']
                self.assertFalse(review['authenticatedImportTested'])
                self.assertFalse(admission.get('authenticatedImportTested', False))
                self.assertIn(admission['mappedType'], routes)
                self.assertTrue(review['sourceRows'])
                for row in review['sourceRows']:
                    declared = source[(review['sourceId'], row)]
                    name = declared['name'].strip()
                    canonical = renames.get(name, {}).get('newName', name)
                    self.assertEqual(admission['name'], canonical)
                    self.assertEqual(admission['mappedType'], declared['sourceType'])

    def test_generated_routes_keep_unverified_status(self):
        catalog = {r['name']: r for r in read('app/src/main/assets/schools.json')}
        baseline = {r['name'] for r in read('scripts/data/school_catalog_baseline.json')}
        for admission in read('scripts/data/school_import_admissions.json'):
            if not admission.get('protocolRouteReview'):
                continue
            with self.subTest(school=admission['name']):
                self.assertNotIn(admission['name'], baseline)
                row = catalog[admission['name']]
                self.assertIn(row['status'], ('unverified', 'grad_unverified', 'pending', 'grad_pending'))
                if row['url']:
                    self.assertEqual(admission['mappedType'], row['type'])
                    self.assertEqual(admission['probe']['finalUrl'], row['url'])
                self.assertFalse(row['enableFetch'])

    def test_verified_school_routes_keep_independent_evidence(self):
        catalog = {r['name']: r for r in read('app/src/main/assets/schools.json')}
        for name, kind in [('佳木斯大学', 'kingo_new'), ('宁波城市职业技术学院', 'classic_eams'),
                           ('南开大学', 'classic_eams'), ('武汉船舶职业技术学院', 'wisedu')]:
            with self.subTest(school=name):
                self.assertEqual(kind, catalog[name]['type'])
                self.assertIn(catalog[name].get('status'), (None, 'supported'))

    def test_private_legacy_entries_remain_pending(self):
        catalog = {r['name']: r for r in read('app/src/main/assets/schools.json')}
        for name in ('江门职业技术学院', '长春建筑学院'):
            self.assertEqual('', catalog[name]['url'])
            self.assertIsNone(catalog[name]['type'])
            self.assertEqual('pending', catalog[name]['status'])


if __name__ == '__main__':
    unittest.main()
