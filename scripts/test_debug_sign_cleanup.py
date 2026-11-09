"""Execute the production cleanup with isolated metadata and mocked GitHub boundaries."""
import copy
from contextlib import redirect_stderr
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import debug_sign


class CleanupVisibilityTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.context = {"repository": "lingion/sleepy", "run_id": "100", "run_attempt": "2"}
        self.info = {"run_id": "100", "run_attempt": "2", "abi": "all"}
        (self.root / "summary-info.json").write_text(json.dumps(self.info))
        self.input = self.root / "input-retained.bin"
        self.input.write_bytes(b"signing input must survive failed verification")
        self.items = [{"id": 1, "name": "debug-signing-input-100-2", "expired": False,
                       "workflow_run": {"id": 100}}]
        for index, abi in enumerate(debug_sign.ci.ABIS, 2):
            name = f"sleepy-debug-official-pr365-abc123-{abi}-r361-a2"
            directory = self.root / abi
            directory.mkdir()
            (directory / "build-info.json").write_text(json.dumps({"apk": name + ".apk"}))
            self.items.append({"id": index, "name": name, "expired": False,
                               "workflow_run": {"id": 100}})
        self.items.append({"id": 6, "name": "sleepy-debug-official-old-attempt", "expired": False,
                           "workflow_run": {"id": 100}})
        self.items.append({"id": 7, "name": "unrelated-test-report", "expired": False,
                           "workflow_run": {"id": 100}})

    def run_cleanup(self, snapshots, *, failure=False, context=None):
        output = io.StringIO()
        before = self.input.read_bytes()
        with patch.object(debug_sign, "artifacts", side_effect=snapshots) as listing, patch.object(
                debug_sign.subprocess, "run") as delete, patch("time.sleep") as sleep, redirect_stderr(output):
            if failure:
                with self.assertRaises((ValueError, KeyError, OSError, subprocess.CalledProcessError)):
                    debug_sign.cleanup_artifacts(self.root, context or self.context)
                delete.assert_not_called()
                self.assertEqual(self.input.read_bytes(), before)
            else:
                debug_sign.cleanup_artifacts(self.root, context or self.context)
                self.assertEqual([call.args[0] for call in delete.call_args_list], [
                    ["gh", "api", "--method", "DELETE", "repos/lingion/sleepy/actions/artifacts/1"],
                    ["gh", "api", "--method", "DELETE", "repos/lingion/sleepy/actions/artifacts/6"]])
            return listing.call_count, [call.args[0] for call in sleep.call_args_list], output.getvalue()

    def test_complete_first_snapshot_cleans_only_input_and_old_official_artifacts(self):
        count, waits, _ = self.run_cleanup([self.items])
        self.assertEqual((count, waits), (1, []))

    def test_previous_attempt_names_do_not_count_as_duplicate_current_names(self):
        items = copy.deepcopy(self.items)
        for index, current in enumerate(self.items[1:5], 20):
            items.append(dict(current, id=index, name=current["name"].replace("-a2", "-a1")))
        with patch.object(debug_sign, "artifacts", return_value=items), patch.object(
                debug_sign.subprocess, "run") as delete, patch("time.sleep") as sleep:
            debug_sign.cleanup_artifacts(self.root, self.context)
            self.assertEqual([call.args[0][-1] for call in delete.call_args_list], [
                "repos/lingion/sleepy/actions/artifacts/" + str(index) for index in (1, 6, 20, 21, 22, 23)])
            sleep.assert_not_called()

    def test_artifact_listing_requests_fresh_paginated_inventory_and_flattens_all_pages(self):
        pages = [{"total_count": len(self.items), "artifacts": self.items[:3]},
                 {"total_count": len(self.items), "artifacts": self.items[3:]}]
        with patch.object(debug_sign.subprocess, "check_output", return_value=json.dumps(pages)) as request:
            self.assertEqual(debug_sign.artifacts(100), self.items)
        command = request.call_args.args[0]
        self.assertIn("--paginate", command)
        self.assertIn("--slurp", command)
        self.assertEqual(command[-1], "repos/lingion/sleepy/actions/runs/100/artifacts?per_page=100")
        self.assertIn("Cache-Control: no-cache", command)
        self.assertEqual(request.call_args.kwargs["stderr"], subprocess.PIPE)

    def test_delayed_last_abi_is_rechecked_before_any_delete(self):
        partial = self.items[:4] + self.items[5:]
        count, waits, output = self.run_cleanup([partial, self.items])
        self.assertEqual((count, waits), (2, [1]))
        self.assertIn('"missing_abis": ["universal"]', output)

    def test_all_four_abis_may_appear_on_the_last_bounded_check(self):
        count, waits, _ = self.run_cleanup([self.items[:1]] * 4 + [self.items])
        self.assertEqual((count, waits), (5, [1, 2, 4, 8]))

    def test_permanently_missing_abi_fails_after_five_checks_and_retains_input(self):
        count, waits, output = self.run_cleanup([self.items[:4] + self.items[5:]] * 5, failure=True)
        self.assertEqual((count, waits), (5, [1, 2, 4, 8]))
        self.assertIn('"check": 5', output)
        self.assertIn('"missing_abis": ["universal"]', output)

    def test_empty_inventory_exhausts_the_same_finite_budget(self):
        count, waits, _ = self.run_cleanup([[]] * 5, failure=True)
        self.assertEqual((count, waits), (5, [1, 2, 4, 8]))

    def test_api_failure_keeps_input_and_does_not_emit_error_payload(self):
        error = subprocess.CalledProcessError(1, ["gh", "api"],
                                             output="secret-response-token", stderr="secret-response-token")
        count, waits, output = self.run_cleanup([error], failure=True)
        self.assertEqual((count, waits), (1, []))
        self.assertIn('"status": "api-error"', output)
        self.assertNotIn("secret-response-token", output)

    def test_api_failure_after_an_incomplete_list_still_never_deletes(self):
        count, waits, _ = self.run_cleanup([[], OSError("secret-network-detail")], failure=True)
        self.assertEqual((count, waits), (2, [1]))

    def test_malformed_api_json_keeps_input_without_logging_the_body(self):
        count, waits, output = self.run_cleanup([json.JSONDecodeError("private-body", "secret-body", 0)], failure=True)
        self.assertEqual((count, waits), (1, []))
        self.assertNotIn("private-body", output)
        self.assertNotIn("secret-body", output)

    def test_duplicate_expected_name_is_an_integrity_failure(self):
        items = copy.deepcopy(self.items) + [dict(self.items[2], id=8)]
        count, waits, output = self.run_cleanup([items], failure=True)
        self.assertEqual((count, waits), (1, []))
        self.assertIn('"status": "integrity-error"', output)

    def test_expired_expected_abi_is_not_counted_as_complete(self):
        items = copy.deepcopy(self.items)
        items[4]["expired"] = True
        count, waits, _ = self.run_cleanup([items], failure=True)
        self.assertEqual((count, waits), (1, []))

    def test_foreign_run_in_a_complete_or_incomplete_list_never_deletes(self):
        for complete in (True, False):
            with self.subTest(complete=complete):
                items = copy.deepcopy(self.items if complete else self.items[:3])
                items[0]["workflow_run"]["id"] = 101
                count, waits, _ = self.run_cleanup([items], failure=True)
                self.assertEqual((count, waits), (1, []))

    def test_invalid_later_delete_id_is_rejected_before_deleting_the_input(self):
        items = copy.deepcopy(self.items)
        items[5]["id"] = "invalid-id-with-private-detail"
        self.run_cleanup([items], failure=True)

    def test_duplicate_artifact_id_fails_before_any_delete(self):
        items = copy.deepcopy(self.items)
        items[5]["id"] = 1
        self.run_cleanup([items], failure=True)

    def test_malformed_inventory_fields_fail_before_any_delete(self):
        for field, value in [("name", 1), ("expired", "false"), ("workflow_run", None), ("id", True)]:
            with self.subTest(field=field):
                items = copy.deepcopy(self.items)
                items[5][field] = value
                self.run_cleanup([items], failure=True)

    def test_identity_changes_fail_before_querying_or_deleting(self):
        for changes in ({"repository": "other/repo"}, {"run_id": "101"}, {"run_attempt": "3"}):
            with self.subTest(changes=changes):
                count, waits, _ = self.run_cleanup([], failure=True, context=dict(self.context, **changes))
                self.assertEqual((count, waits), (0, []))

    def test_duplicate_local_abi_artifact_names_cannot_reduce_the_four_abi_gate(self):
        first = json.loads((self.root / debug_sign.ci.ABIS[0] / "build-info.json").read_text())
        (self.root / debug_sign.ci.ABIS[1] / "build-info.json").write_text(json.dumps(first))
        count, waits, _ = self.run_cleanup([self.items], failure=True)
        self.assertEqual((count, waits), (0, []))

    def test_inventory_diagnostics_use_abi_names_and_hashes_instead_of_raw_names(self):
        items = copy.deepcopy(self.items)
        items[5]["name"] = "sleepy-debug-official-private-token\n::warning::injected"
        _, _, output = self.run_cleanup([items])
        self.assertNotIn("private-token", output)
        self.assertNotIn("::warning::", output)
        self.assertIn('"name_sha256":', output)
        self.assertIn('"expected_abis": ["arm64-v8a", "armeabi-v7a", "x86_64", "universal"]', output)


if __name__ == "__main__":
    unittest.main()
