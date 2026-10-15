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
        row = self.row | {"supportingEvidence": supporting, "relationSources": relation,
                          "requestMethod": "POST", "requestBody": {"code": ""},
                          "parentSourceUrl": "https://school.example/contact",
                          "extractionMethod": "public-json-field",
                          "cityEvidence": [{"city": "珠海市", "unit": "Campus"}],
                          "evidencePages": [1, 3]}
        apply_verified_locations(self.result, [row], self.names)
        source = self.result["_cityEvidence"]["Campus"]["sourceId"]
        metadata = self.result["_citySources"][source]
        self.assertEqual(supporting, metadata["supportingEvidence"])
        self.assertEqual(relation, metadata["relationSources"])
        self.assertEqual("POST", metadata["requestMethod"])
        self.assertEqual({"code": ""}, metadata["requestBody"])
        self.assertEqual(row["parentSourceUrl"], metadata["parentSourceUrl"])
        self.assertEqual("public-json-field", metadata["extractionMethod"])
        self.assertEqual(row["cityEvidence"], metadata["cityEvidence"])
        self.assertEqual([1, 3], metadata["evidencePages"])

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


class BaselineExtensionTest(unittest.TestCase):
    def setUp(self):
        self.name = "新疆农业职业技术大学"
        self.names = {self.name, "Other"}
        self.result = {
            "schools": {self.name: ["昌吉回族自治州"]},
            "_cityEvidence": {self.name: {"sourceId": "official", "sourceRow": 2939}},
            "_citySources": {"official": {"url": "https://example.gov.cn/list",
                                             "sourceDate": "2026-06-18"}},
        }
        self.before = copy.deepcopy(self.result)
        self.snapshot = {
            "cities": ["昌吉回族自治州"],
            "evidence": copy.deepcopy(self.result["_cityEvidence"][self.name]),
            "source": copy.deepcopy(self.result["_citySources"]["official"]),
            "campusOverride": None,
        }
        self.row = {
            "name": self.name, "cities": ["昌吉回族自治州", "乌鲁木齐市"],
            "sourceUrl": "https://example.edu.cn/campus",
            "observedText": "New campus: 乌鲁木齐市",
            "baselineExtension": self.snapshot,
        }

    def test_explicit_extension_is_additive_idempotent_and_does_not_count_new_school(self):
        self.assertEqual(0, apply_verified_locations(self.result, [self.row], self.names))
        self.assertEqual(self.row["cities"], self.result["schools"][self.name])
        metadata = self.result["_cityEvidence"][self.name]
        self.assertEqual("reviewed-baseline-city-extension", metadata["method"])
        self.assertEqual(self.snapshot, metadata["baselineExtension"])
        self.assertEqual(self.before["_citySources"]["official"],
                         self.result["_citySources"]["official"])
        applied = copy.deepcopy(self.result)
        self.assertEqual(0, apply_verified_locations(self.result, [self.row], self.names))
        self.assertEqual(applied, self.result)

    def test_withdrawal_restores_original_mapping_and_metadata_exactly(self):
        apply_verified_locations(self.result, [self.row], self.names)
        apply_verified_locations(self.result, [], self.names)
        self.assertEqual(self.before, self.result)

    def test_snapshot_and_applied_cities_are_not_aliased_to_input(self):
        apply_verified_locations(self.result, [self.row], self.names)
        self.snapshot["cities"].append("北京市")
        self.snapshot["evidence"]["sourceRow"] = 1
        self.row["cities"].append("上海市")
        apply_verified_locations(self.result, [], self.names)
        self.assertEqual(self.before, self.result)

    def test_extension_can_be_corrected_but_never_changes_baseline(self):
        apply_verified_locations(self.result, [self.row], self.names)
        revised = self.row | {"cities": ["昌吉回族自治州", "乌鲁木齐市", "伊犁哈萨克自治州"]}
        apply_verified_locations(self.result, [revised], self.names)
        self.assertEqual(revised["cities"], self.result["schools"][self.name])
        apply_verified_locations(self.result, [], self.names)
        self.assertEqual(self.before, self.result)

    def test_default_baseline_protection_is_unchanged(self):
        row = {k: v for k, v in self.row.items() if k != "baselineExtension"}
        for cities in (["昌吉回族自治州"], ["昌吉回族自治州", "乌鲁木齐市"]):
            with self.subTest(cities=cities), self.assertRaises(ValueError):
                apply_verified_locations(self.result, [row | {"cities": cities}], self.names)
            self.assertEqual(self.before, self.result)

    def test_non_additive_or_incomplete_snapshots_fail_atomically(self):
        variants = [
            self.row | {"cities": ["乌鲁木齐市"]},
            self.row | {"cities": ["乌鲁木齐市", "昌吉回族自治州"]},
            self.row | {"cities": ["昌吉回族自治州"]},
            self.row | {"baselineExtension": True},
            self.row | {"baselineExtension": None},
            self.row | {"baselineExtension": {"cities": ["昌吉回族自治州"]}},
            self.row | {"baselineExtension": self.snapshot | {"unexpected": 1}},
        ]
        for row in variants:
            with self.subTest(row=row), self.assertRaises(ValueError):
                apply_verified_locations(self.result, [row], self.names)
            self.assertEqual(self.before, self.result)

    def test_original_values_and_metadata_must_match_snapshot(self):
        changes = [
            ("schools", self.name, ["北京市"]),
            ("_cityEvidence", self.name, {"sourceId": "official", "sourceRow": 1}),
            ("_citySources", "official", {"url": "https://changed.example/list"}),
            ("_campusOverrides", self.name, {"cities": ["乌鲁木齐市"]}),
        ]
        for section, key, value in changes:
            result = copy.deepcopy(self.before)
            result.setdefault(section, {})[key] = value
            before = copy.deepcopy(result)
            with self.subTest(section=section), self.assertRaises(ValueError):
                apply_verified_locations(result, [self.row], self.names)
            self.assertEqual(before, result)

    def test_drift_after_extension_blocks_reapply_and_withdrawal(self):
        for section, key, value in [
                ("schools", self.name, ["北京市"]),
                ("_citySources", "official", {"url": "https://changed.example/list"}),
                ("_campusOverrides", self.name, {"cities": ["北京市"]})]:
            result = copy.deepcopy(self.before)
            apply_verified_locations(result, [self.row], self.names)
            result.setdefault(section, {})[key] = value
            before = copy.deepcopy(result)
            for rows in ([self.row], []):
                with self.subTest(section=section, withdrawal=not rows), self.assertRaises(ValueError):
                    apply_verified_locations(result, rows, self.names)
                self.assertEqual(before, result)

    def test_existing_extension_requires_same_snapshot(self):
        apply_verified_locations(self.result, [self.row], self.names)
        before = copy.deepcopy(self.result)
        variants = [
            {k: v for k, v in self.row.items() if k != "baselineExtension"},
            self.row | {"baselineExtension": self.snapshot | {"cities": ["乌鲁木齐市"]}},
        ]
        for row in variants:
            with self.subTest(row=row), self.assertRaises(ValueError):
                apply_verified_locations(self.result, [row], self.names)
            self.assertEqual(before, self.result)

    def test_later_bad_row_prevents_extension_and_withdrawal(self):
        invalid = self.row | {"name": "Missing"}
        with self.assertRaises(ValueError):
            apply_verified_locations(self.result, [self.row, invalid], self.names)
        self.assertEqual(self.before, self.result)
        apply_verified_locations(self.result, [self.row], self.names)
        applied = copy.deepcopy(self.result)
        with self.assertRaises(ValueError):
            apply_verified_locations(self.result, [invalid], self.names)
        self.assertEqual(applied, self.result)

    def test_no_metadata_baseline_restores_absence_and_keeps_other_sources(self):
        result = {"schools": {self.name: ["昌吉回族自治州"]},
                  "_cityEvidence": {}, "_citySources": {"other": {"url": "kept"}}}
        before = copy.deepcopy(result)
        row = self.row | {"baselineExtension": self.snapshot | {"evidence": None, "source": None}}
        apply_verified_locations(result, [row], self.names)
        apply_verified_locations(result, [], self.names)
        self.assertEqual(before, result)

    def test_generated_source_collision_is_rejected(self):
        import hashlib
        source_id = "verified-location-" + hashlib.sha256(self.name.encode()).hexdigest()[:16]
        self.result["_citySources"][source_id] = {"url": "unrelated-source"}
        before = copy.deepcopy(self.result)
        with self.assertRaisesRegex(ValueError, "collision"):
            apply_verified_locations(self.result, [self.row], self.names)
        self.assertEqual(before, self.result)

    def test_corrupted_saved_provenance_cannot_restore_an_invented_baseline(self):
        apply_verified_locations(self.result, [self.row], self.names)
        metadata = self.result["_cityEvidence"][self.name]
        metadata["baselineExtension"]["evidence"]["sourceRow"] = 1
        before = copy.deepcopy(self.result)
        with self.assertRaises(ValueError):
            apply_verified_locations(self.result, [], self.names)
        self.assertEqual(before, self.result)

    def test_missing_generated_source_blocks_withdrawal(self):
        apply_verified_locations(self.result, [self.row], self.names)
        source_id = self.result["_cityEvidence"][self.name]["sourceId"]
        self.result["_citySources"].pop(source_id)
        before = copy.deepcopy(self.result)
        with self.assertRaises(ValueError):
            apply_verified_locations(self.result, [], self.names)
        self.assertEqual(before, self.result)


if __name__ == '__main__':
    unittest.main()
