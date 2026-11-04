"""Behavior contracts for the debug APK workflow; no third-party dependencies."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import debug_ci


SHA = "a" * 40
CONTEXT = {
    "event_name": "workflow_dispatch", "repository": "lingion/sleepy",
    "ref": "refs/heads/main", "sha": SHA,
    "workflow_sha": "b" * 40, "workflow_ref": "lingion/sleepy/.github/workflows/debug-apk.yml@refs/heads/main",
    "run_id": "100", "run_number": "20", "run_attempt": "1", "server_url": "https://github.com",
}


def event(fork=False):
    return {"repository": {"fork": fork, "default_branch": "main"}, "inputs": {}}


class PlanTest(unittest.TestCase):
    def test_empty_ref_uses_immutable_selected_workflow_commit(self):
        ctx = dict(CONTEXT, ref="refs/heads/feature/selected")
        plan = debug_ci.make_plan(event(), ctx)
        self.assertEqual(plan["source_ref"], SHA)
        self.assertEqual(plan["requested_ref"], "refs/heads/feature/selected")
        self.assertEqual(plan["abi"], "all")

    def test_explicit_ref_overrides_selected_branch_and_keeps_v7a(self):
        payload = event()
        payload["inputs"] = {"ref": "v1.0.63", "abi": "armeabi-v7a"}
        plan = debug_ci.make_plan(payload, CONTEXT)
        self.assertEqual(plan["source_ref"], "v1.0.63")
        self.assertEqual(plan["abi"], "armeabi-v7a")

    def test_pr_checkout_pins_head_as_merge_input(self):
        payload = event()
        payload["pull_request"] = {"number": 286, "draft": False, "head": {
            "repo": {"full_name": "contributor/sleepy"}, "sha": "c" * 40, "ref": "fix/test"}}
        plan = debug_ci.make_plan(payload, dict(CONTEXT, event_name="pull_request"))
        self.assertEqual(plan["source_repository"], "contributor/sleepy")
        self.assertEqual(plan["source_ref"], "c" * 40)
        self.assertEqual(plan["label"], "pr286")
        self.assertEqual(plan["abi"], "all")
        payload["pull_request"]["head"]["sha"] = "d" * 40
        self.assertEqual(plan["concurrency_key"], debug_ci.make_plan(
            payload, dict(CONTEXT, event_name="pull_request"))["concurrency_key"])
        payload["pull_request"]["number"] = 287
        self.assertNotEqual(plan["concurrency_key"], debug_ci.make_plan(
            payload, dict(CONTEXT, event_name="pull_request"))["concurrency_key"])

    def test_draft_skips_and_ready_pr_runs(self):
        payload = event()
        payload["pull_request"] = {"number": 1, "draft": True, "head": {
            "repo": {"full_name": "contributor/sleepy"}, "sha": SHA, "ref": "fix/a"}}
        ctx = dict(CONTEXT, event_name="pull_request")
        self.assertFalse(debug_ci.make_plan(payload, ctx)["enabled"])
        payload["pull_request"]["draft"] = False
        self.assertTrue(debug_ci.make_plan(payload, ctx)["enabled"])

    def test_push_eligibility_and_tag_exclusion(self):
        for fork, ref, enabled in [(True, "refs/heads/feature/a", True),
                                   (False, "refs/heads/main", True),
                                   (False, "refs/heads/feature/a", False),
                                   (True, "refs/tags/v1.0.63", False)]:
            with self.subTest(fork=fork, ref=ref):
                plan = debug_ci.make_plan(event(fork), dict(CONTEXT, event_name="push", ref=ref))
                self.assertEqual(plan["enabled"], enabled)
                if enabled:
                    self.assertEqual(plan["source_ref"], SHA)

    def test_invalid_abi_and_multiline_ref_fail_closed(self):
        for inputs in [{"abi": "v7a"}, {"ref": "main\nsource_ref=evil"}, {"ref": "-bad"}]:
            payload = event()
            payload["inputs"] = inputs
            with self.assertRaises(ValueError):
                debug_ci.make_plan(payload, CONTEXT)

    def test_refs_with_shell_metacharacters_are_data_not_code(self):
        payload = event()
        payload["inputs"] = {"ref": "feature/$(touch-pwned)", "abi": "universal"}
        plan = debug_ci.make_plan(payload, CONTEXT)
        self.assertEqual(plan["source_ref"], payload["inputs"]["ref"])
        self.assertNotIn("$", plan["label"])
        self.assertNotIn("/", plan["label"])

    def test_manual_groups_separate_ref_abi_case_and_entry(self):
        keys = []
        for ref, abi in [("feature/a", "all"), ("feature/A", "all"),
                         ("feature/a", "armeabi-v7a"), ("feature/b", "all")]:
            payload = event()
            payload["inputs"] = {"ref": ref, "abi": abi}
            keys.append(debug_ci.make_plan(payload, CONTEXT)["concurrency_key"])
        keys.append(debug_ci.make_plan(event(), dict(CONTEXT, event_name="push"))["concurrency_key"])
        self.assertEqual(len(keys), len(set(keys)))
        payload = event()
        payload["inputs"] = {"ref": "feature/a", "abi": "all"}
        self.assertEqual(keys[0], debug_ci.make_plan(payload, dict(CONTEXT, sha="e" * 40))["concurrency_key"])


class PackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "apk"
        self.source.mkdir()
        self.destination = self.root / "artifacts"
        self.plan = debug_ci.make_plan(event(), CONTEXT)
        self.identity = {"application_id": "com.lingion.sleepy.debug", "version_name": "1.0.63-debug",
                         "debuggable": True, "certificate_sha256": "c" * 64}

    def inventory(self, abi):
        elements = []
        for name in debug_ci.selected_abis(abi):
            filename = "app-debug.apk" if abi == "universal" else f"app-{name}-debug.apk"
            (self.source / filename).write_bytes(b"example-apk-" + name.encode())
            elements.append({"outputFile": filename, "filters": [] if name == "universal" else [
                {"filterType": "ABI", "value": name}]})
        (self.source / "output-metadata.json").write_text(json.dumps({"applicationId": self.identity["application_id"],
            "variantName": "debug", "elements": elements}))

    def package(self, abi):
        return debug_ci.package_apks(self.source, self.destination, dict(self.plan, abi=abi), SHA,
                                     "ephemeral", lambda path: dict(self.identity), expected_certificate="c" * 64)

    def test_all_architectures_have_independent_identified_checksums(self):
        self.inventory("all")
        result = self.package("all")
        self.assertEqual(set(result), set(debug_ci.ABIS))
        for abi, item in result.items():
            directory = self.destination / abi
            apk = directory / item["apk"]
            self.assertIn(SHA[:12], apk.name)
            self.assertIn("manual-main", apk.name)
            self.assertIn("r20-a1", apk.name)
            info = json.loads((directory / "build-info.json").read_text())
            self.assertEqual(info["source_sha"], SHA)
            self.assertEqual(info["abi"], abi)
            self.assertEqual(info["application_id"], self.identity["application_id"])
            self.assertEqual(info["signing"], "ephemeral")
            for line in (directory / "sha256sums.txt").read_text().splitlines():
                digest, name = line.split("  ")
                self.assertEqual(digest, hashlib.sha256((directory / name).read_bytes()).hexdigest())

    def test_v7a_only_and_universal_only(self):
        for abi in ["armeabi-v7a", "universal"]:
            with self.subTest(abi=abi):
                for path in self.source.iterdir():
                    path.unlink()
                self.inventory(abi)
                result = self.package(abi)
                self.assertEqual(list(result), [abi])

    def test_missing_extra_and_wrong_abi_rejected(self):
        self.inventory("armeabi-v7a")
        metadata_path = self.source / "output-metadata.json"
        original = metadata_path.read_text()
        metadata = json.loads(original)
        metadata["elements"][0]["filters"][0]["value"] = "x86_64"
        metadata_path.write_text(json.dumps(metadata))
        with self.assertRaises(ValueError):
            self.package("armeabi-v7a")
        metadata_path.write_text(original)
        extra = self.source / "app-arm64-v8a-debug.apk"
        extra.write_bytes(b"extra")
        with self.assertRaises(ValueError):
            self.package("armeabi-v7a")
        extra.unlink()
        (self.source / "app-armeabi-v7a-debug.apk").unlink()
        with self.assertRaises(ValueError):
            self.package("armeabi-v7a")

    def test_metadata_path_traversal_and_symlink_rejected(self):
        self.inventory("armeabi-v7a")
        path = self.source / "output-metadata.json"
        data = json.loads(path.read_text())
        data["elements"][0]["outputFile"] = "../escape.apk"
        path.write_text(json.dumps(data))
        with self.assertRaises(ValueError):
            self.package("armeabi-v7a")

    def test_apk_symlink_rejected(self):
        self.inventory("armeabi-v7a")
        apk = self.source / "app-armeabi-v7a-debug.apk"
        outside = self.root / "outside.apk"
        outside.write_bytes(apk.read_bytes())
        apk.unlink()
        apk.symlink_to(outside)
        with self.assertRaises(ValueError):
            self.package("armeabi-v7a")

    def test_mislabelled_native_libraries_rejected(self):
        self.inventory("armeabi-v7a")
        self.identity["native_abis"] = ["arm64-v8a"]
        with self.assertRaises(ValueError):
            self.package("armeabi-v7a")

    def test_universal_accepts_legacy_x86_dependency_libraries(self):
        self.inventory("all")
        def inspect(path):
            return dict(self.identity, native_abis=["x86"] if "universal" in path.name else [])
        result = debug_ci.package_apks(self.source, self.destination, self.plan, SHA, "ephemeral",
                                       inspect, expected_certificate="c" * 64)
        self.assertEqual(set(result), set(debug_ci.ABIS))

    def test_certificate_must_match_selected_keystore(self):
        self.inventory("all")
        self.identity["certificate_sha256"] = "d" * 64
        with self.assertRaisesRegex(ValueError, "certificate"):
            self.package("all")

    def test_source_revision_must_equal_prebuild_snapshot(self):
        self.assertEqual(debug_ci.verify_revision(SHA, SHA), SHA)
        with self.assertRaises(ValueError):
            debug_ci.verify_revision("b" * 40, SHA)
        with self.assertRaises(ValueError):
            debug_ci.verify_revision(SHA, "")

    def test_non_debuggable_artifact_rejected(self):
        self.inventory("all")
        self.identity["debuggable"] = False
        with self.assertRaises(ValueError):
            self.package("all")


class InspectionDiagnosticTest(unittest.TestCase):
    def inspect_with_certificate_text(self, text):
        from unittest.mock import patch
        import zipfile
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "example.apk"
            with zipfile.ZipFile(apk, "w"):
                pass
            badging = "package: name='com.lingion.sleepy.debug' versionCode='10063' versionName='1.0.63-debug'\napplication-debuggable\n"
            with patch("debug_ci.find_build_tool", return_value="/sdk/tool"), patch(
                    "debug_ci.subprocess.check_output", side_effect=[badging, text]):
                return debug_ci.inspect_apk(apk)

    def test_legacy_and_v2_signer_labels(self):
        digest = "b05d0b9872c0c9f5b64a0e1f370b9d6a3dc4170805c497b1e0e64b6e516faef8"
        for prefix in ("Signer #1", "V2 Signer:"):
            with self.subTest(prefix=prefix):
                result = self.inspect_with_certificate_text(f"{prefix} certificate SHA-256 digest: {digest}\n")
                self.assertEqual(result["certificate_sha256"], digest)

    def test_distinct_signing_identities_fail_closed(self):
        text = f"Signer #1 certificate SHA-256 digest: {'a' * 64}\nV2 Signer: certificate SHA-256 digest: {'b' * 64}\n"
        with self.assertRaises(ValueError):
            self.inspect_with_certificate_text(text)

    def test_unknown_signer_output_retains_public_diagnostic(self):
        from unittest.mock import patch
        badging = "package: name='com.lingion.sleepy.debug' versionCode='10063' versionName='1.0.63-debug'\napplication-debuggable\n"
        with patch("debug_ci.find_build_tool", return_value="/sdk/tool"), patch(
                "debug_ci.subprocess.check_output", side_effect=[badging, "public signer diagnostic"]):
            with self.assertRaisesRegex(ValueError, "public signer diagnostic"):
                debug_ci.inspect_apk(Path("example.apk"))


class SummaryTest(unittest.TestCase):
    def test_signing_input_points_to_same_run_artifacts_while_signing(self):
        plan = debug_ci.make_plan(event(), CONTEXT)
        summary = debug_ci.render_summary(dict(plan, signing="ephemeral"), {}, "success")
        self.assertIn("not an official install package", summary)
        self.assertIn("this run's Artifacts", summary)
        self.assertNotIn("actions/workflows/debug-sign.yml", summary)
        self.assertNotIn("| ABI |", summary)

    def test_partial_upload_failure_and_ephemeral_warning(self):
        summary = debug_ci.render_summary({"source_sha": SHA, "source_repository": "contributor/sleepy",
            "requested_ref": "fix/a", "abi": "all", "signing": "ephemeral", "application_id": "com.lingion.sleepy.debug"},
            {"armeabi-v7a": {"outcome": "success", "url": "https://github.com/test/artifacts/1"},
             "arm64-v8a": {"outcome": "failure", "url": ""}}, "failure")
        self.assertIn("https://github.com/test/artifacts/1", summary)
        self.assertIn("failure", summary)
        self.assertIn("ephemeral", summary)
        self.assertIn("7", summary)
        self.assertNotIn("All uploads succeeded", summary)


if __name__ == "__main__":
    unittest.main()
