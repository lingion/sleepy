"""Negative inputs must not silently publish guessed or partial locations."""
import copy
import unittest
from enrich_school_cities import apply_verified_locations


class VerifiedLocationsTest(unittest.TestCase):
    def setUp(self):
        self.result = {"schools": {"Existing": ["广州市"]}}
        self.row = {"name": "Campus", "cities": ["珠海市"],
                    "sourceUrl": "https://example.edu.cn/campus",
                    "observedText": "Campus address: 珠海市"}
        self.names = {"Campus", "Existing"}

    def test_exact_address_adds_city_and_provenance(self):
        self.assertEqual(1, apply_verified_locations(self.result, [self.row], self.names))
        self.assertEqual(["珠海市"], self.result["schools"]["Campus"])
        source = self.result['_cityEvidence']['Campus']['sourceId']
        self.assertEqual(self.row['observedText'], self.result['_citySources'][source]['observedText'])
        self.assertEqual(0, apply_verified_locations(self.result, [self.row], self.names))

    def test_research_metadata_survives_import(self):
        row = self.row | {"checkedAt": "2026-10-15", "evidenceTextSha256": "abc"}
        apply_verified_locations(self.result, [row], self.names)
        source = self.result["_cityEvidence"]["Campus"]["sourceId"]
        metadata = self.result["_citySources"][source]
        self.assertEqual("2026-10-15", metadata["retrievedDate"])
        self.assertEqual("abc", metadata["evidenceTextSha256"])

    def test_identity_relation_sources_survive_generated_metadata(self):
        supporting = [{"sourceUrl": "https://education.example/rename",
                       "observedText": "Former school became Campus", "bodySha256": "abc"}]
        relation = [{"sourceUrl": "https://school.example/about",
                     "observedText": "Campus current identity", "evidenceTextSha256": "def"}]
        row = self.row | {"supportingEvidence": supporting, "relationSources": relation}
        apply_verified_locations(self.result, [row], self.names)
        source = self.result["_cityEvidence"]["Campus"]["sourceId"]
        metadata = self.result["_citySources"][source]
        self.assertEqual(supporting, metadata["supportingEvidence"])
        self.assertEqual(relation, metadata["relationSources"])

    def test_bad_records_fail_without_partial_mutation(self):
        for bad in ({"cities": ["广东省"]}, {"cities": ["广西壮族自治区"]},
                    {"cities": []}, {"cities": ["珠海市", "珠海市"]},
                    {"name": "Guessed parent"}, {"sourceUrl": "file:///tmp/page"},
                    {"observedText": ""}, {"name": "Existing"}):
            result = copy.deepcopy(self.result)
            row = self.row | bad
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                apply_verified_locations(result, [row], self.names)
            self.assertEqual(self.result, result)

    def test_later_invalid_record_does_not_apply_earlier_record(self):
        before = copy.deepcopy(self.result)
        with self.assertRaises(ValueError):
            apply_verified_locations(self.result, [self.row, self.row | {"name": "Unknown"}], self.names)
        self.assertEqual(before, self.result)

    def test_duplicate_names_are_rejected_even_when_identical(self):
        with self.assertRaisesRegex(ValueError, "duplicate"):
            apply_verified_locations(self.result, [self.row, self.row], self.names)

    def test_withdrawn_candidate_removes_generated_mapping_but_preserves_baseline(self):
        apply_verified_locations(self.result, [self.row], self.names)
        apply_verified_locations(self.result, [], self.names)
        self.assertEqual({"Existing": ["广州市"]}, self.result["schools"])
        self.assertEqual({}, self.result["_cityEvidence"])
        self.assertEqual({}, self.result["_citySources"])

    def test_reviewed_correction_replaces_only_owned_mapping(self):
        apply_verified_locations(self.result, [self.row], self.names)
        corrected = self.row | {"cities": ["广州市"], "observedText": "Campus address: 广州市"}
        apply_verified_locations(self.result, [corrected], self.names)
        self.assertEqual(["广州市"], self.result["schools"]["Campus"])
        with self.assertRaisesRegex(ValueError, "baseline"):
            apply_verified_locations(self.result, [corrected | {"name": "Existing"}], self.names)


if __name__ == '__main__':
    unittest.main()
