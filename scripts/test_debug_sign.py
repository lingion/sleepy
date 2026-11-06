"""官方签名边界：只处理已完成构建的 APK 数据，不执行来源仓库代码。"""
import copy
import hashlib
import json
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile
from unittest.mock import patch

import debug_sign


HEAD = "a" * 40
MERGE = "b" * 40
CERT = "c" * 64


def build_run():
    return {"id": 123, "run_number": 10, "run_attempt": 2, "status": "completed",
            "conclusion": "success", "event": "pull_request", "head_sha": HEAD,
            "path": ".github/workflows/debug-apk.yml", "repository": {"full_name": "lingion/sleepy"}}


class SigningTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / "bundle"
        self.bundle.mkdir()
        self.run = build_run()
        self.info = {"run_id": "123", "run_number": "10", "run_attempt": "2", "abi": "universal",
                     "label": "pr306", "source_sha": MERGE, "pr_head_sha": HEAD, "main_sha": "d" * 40,
                     "merge_sha": MERGE, "source_repository": "contributor/sleepy",
                     "requested_ref": "feature/test", "workflow_sha": "e" * 40,
                     "workflow_ref": "lingion/sleepy/.github/workflows/debug-apk.yml@refs/pull/306/merge",
                     "base_repository": "lingion/sleepy", "signing": "ephemeral"}
        self.identity = {"application_id": "com.lingion.sleepy.debug", "version_name": "test",
                         "debuggable": True, "native_abis": [], "certificate_sha256": CERT}
        self.apk = self.bundle / "universal/sleepy-debug-test.apk"
        self.apk.parent.mkdir()
        self.apk.write_bytes(b"APK fixture")
        self.write_info()

    def write_info(self):
        (self.bundle / "summary-info.json").write_text(json.dumps(self.info))
        per_abi = dict(self.info, **self.identity, apk=self.apk.name,
                       apk_sha256=hashlib.sha256(self.apk.read_bytes()).hexdigest())
        (self.apk.parent / "build-info.json").write_text(json.dumps(per_abi))
        (self.apk.parent / "sha256sums.txt").write_text("unused input checksum file\n")

    def sign(self, **overrides):
        def signer(source, target, keystore):
            target.write_bytes(source.read_bytes() + b" signed")
        arguments = {"inspector": lambda path: dict(self.identity), "signer": signer}
        arguments.update(overrides)
        return debug_sign.sign_bundle(self.bundle, self.root / "out", self.run,
                                      self.root / "key", CERT, **arguments)

    def test_only_successful_official_build_workflow_is_accepted(self):
        self.assertEqual(debug_sign.validate_run(self.run), self.run)
        for field, value in [("conclusion", "failure"), ("status", "in_progress"),
                             ("path", ".github/workflows/other.yml"), ("event", "workflow_run"),
                             ("repository", {"full_name": "contributor/sleepy"}), ("run_attempt", 0)]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                debug_sign.validate_run(dict(self.run, **{field: value}))

    def test_official_output_records_existing_certificate_and_source_revisions(self):
        outputs = self.sign()
        self.assertEqual(set(outputs), {"universal"})
        directory = self.root / "out/universal"
        info = json.loads((directory / "build-info.json").read_text())
        self.assertEqual(info["certificate_sha256"], CERT)
        self.assertEqual(info["signing"], "project")
        for field in ("pr_head_sha", "main_sha", "merge_sha", "source_sha"):
            self.assertEqual(info[field], self.info[field])
        self.assertIn("official", info["apk"])
        self.assertEqual(info["run_url"], "https://github.com/lingion/sleepy/actions/runs/123")
        for line in (directory / "sha256sums.txt").read_text().splitlines():
            digest, name = line.split("  ")
            self.assertEqual(digest, hashlib.sha256((directory / name).read_bytes()).hexdigest())

    def test_all_abis_are_resigned_and_selected_set_is_preserved(self):
        self.info["abi"] = "all"
        (self.bundle / "summary-info.json").write_text(json.dumps(self.info))
        for abi in ("arm64-v8a", "armeabi-v7a", "x86_64"):
            directory = self.bundle / abi
            directory.mkdir()
            (directory / self.apk.name).write_bytes(self.apk.read_bytes())
            metadata = json.loads((self.apk.parent / "build-info.json").read_text())
            metadata["abi"] = abi
            (directory / "build-info.json").write_text(json.dumps(metadata))
            (directory / "sha256sums.txt").write_text("")
        outputs = self.sign()
        self.assertEqual(set(outputs), set(debug_sign.ci.ABIS))
        self.assertEqual(json.loads((self.root / "out/summary-info.json").read_text())["abi"], "all")

    def test_other_attempt_head_or_missing_merge_is_rejected(self):
        for changes in ({"run_attempt": "1"}, {"run_id": "456"}, {"pr_head_sha": "f" * 40},
                        {"main_sha": ""}, {"merge_sha": HEAD}):
            with self.subTest(changes=changes):
                original = copy.deepcopy(self.info)
                self.info.update(changes)
                self.write_info()
                with self.assertRaises(ValueError):
                    self.sign()
                self.info = original

    def test_unsigned_input_or_mismatched_output_certificate_is_rejected(self):
        def wrong_certificate(path):
            return dict(self.identity, certificate_sha256="f" * 64)
        with self.assertRaises(ValueError):
            self.sign(inspector=wrong_certificate)
        self.assertFalse((self.root / "out/summary-info.json").exists())

    def test_resigned_certificate_must_match_official_key(self):
        def inspect(path):
            certificate = CERT if self.bundle in path.parents else "f" * 64
            return dict(self.identity, certificate_sha256=certificate)
        with self.assertRaisesRegex(ValueError, "Official APK certificate"):
            self.sign(inspector=inspect)
        self.assertFalse((self.root / "out/summary-info.json").exists())

    def test_signer_failure_never_creates_publishable_summary(self):
        def fail_signing(*args):
            raise ValueError("signing failed")
        with self.assertRaisesRegex(ValueError, "signing failed"):
            self.sign(signer=fail_signing)
        self.assertFalse((self.root / "out/summary-info.json").exists())

    def test_tampered_apk_is_rejected_before_signing(self):
        self.apk.write_bytes(b"changed")
        with self.assertRaises(ValueError):
            self.sign(signer=lambda *args: self.fail("must not sign tampered input"))

    def test_arbitrary_package_and_non_debuggable_apk_are_rejected(self):
        for identity in (dict(self.identity, application_id="com.example.other"),
                         dict(self.identity, debuggable=False)):
            with self.subTest(identity=identity), self.assertRaises(ValueError):
                self.sign(inspector=lambda path: identity)

    def test_input_metadata_cannot_name_external_file(self):
        path = self.apk.parent / "build-info.json"
        info = json.loads(path.read_text())
        info["apk"] = "../../key"
        path.write_text(json.dumps(info))
        with self.assertRaises(ValueError):
            self.sign()


class ArchiveTest(unittest.TestCase):
    def extract(self, entries):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            archive = root / "artifact.zip"
            with zipfile.ZipFile(archive, "w") as output:
                for name, body in entries:
                    output.writestr(name, body)
            debug_sign.extract_bundle(archive, root / "out")
            return sorted(str(path.relative_to(root / "out")) for path in (root / "out").rglob("*") if path.is_file())

    def test_only_plain_apks_and_metadata_are_extracted(self):
        self.assertEqual(self.extract([("summary-info.json", "{}"),
                                       ("universal/build-info.json", "{}"),
                                       ("universal/sleepy-debug-test.apk", "apk")]),
                         ["summary-info.json", "universal/build-info.json", "universal/sleepy-debug-test.apk"])

    def test_traversal_scripts_duplicate_entries_and_symlinks_are_rejected(self):
        link = zipfile.ZipInfo("universal/sleepy-debug-link.apk")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        for entries in [[("../escape", "x")], [("/absolute", "x")],
                        [("universal/run.py", "x")], [(link, "../../key")],
                        [("summary-info.json", "{}"), ("summary-info.json", "{}")]]:
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                self.extract(entries)


class DownloadTest(unittest.TestCase):
    def test_download_verifies_github_archive_digest_before_extraction(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            archive = root / "fixture.zip"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("summary-info.json", "{}")
            data = archive.read_bytes()
            run = build_run()
            artifact = {"name": "debug-signing-input-123-2", "id": 42, "expired": False,
                        "size_in_bytes": len(data), "digest": "sha256:" + hashlib.sha256(data).hexdigest(),
                        "workflow_run": {"id": 123, "head_sha": HEAD}}

            def download(command, *, stdout, check):
                self.assertEqual(command, ["gh", "api", "repos/lingion/sleepy/actions/artifacts/42/zip"])
                stdout.write(data)

            with patch.object(debug_sign, "api", return_value=run), patch.object(
                    debug_sign.subprocess, "check_output", return_value=json.dumps([
                        {"artifacts": [artifact]}])), patch.object(debug_sign.subprocess, "run", side_effect=download):
                self.assertEqual(debug_sign.download_bundle({"workflow_run": run}, root / "valid"), run)
                self.assertTrue((root / "valid/summary-info.json").is_file())
            artifact["digest"] = "sha256:" + "f" * 64
            with patch.object(debug_sign, "api", return_value=run), patch.object(
                    debug_sign.subprocess, "check_output", return_value=json.dumps([
                        {"artifacts": [artifact]}])), patch.object(debug_sign.subprocess, "run", side_effect=download):
                with self.assertRaisesRegex(ValueError, "digest"):
                    debug_sign.download_bundle({"workflow_run": run}, root / "tampered")
                self.assertFalse((root / "tampered").exists())

    def test_newer_attempt_does_not_get_signed_by_old_completion_event(self):
        run = build_run()
        with patch.object(debug_sign, "api", return_value=dict(run, run_attempt=3)):
            with self.assertRaisesRegex(ValueError, "stale"):
                debug_sign.download_bundle({"workflow_run": run}, Path("unused"))

    def test_download_requires_exact_attempt_name_and_valid_digest(self):
        run = build_run()
        artifact = {"name": "debug-signing-input-123-2", "id": 42, "expired": False,
                    "size_in_bytes": 100, "digest": "sha256:" + "f" * 64,
                    "workflow_run": {"id": 123, "head_sha": HEAD}}
        for changes in ({"name": "debug-signing-input-123-1"}, {"digest": ""},
                        {"expired": True}, {"workflow_run": {"id": 456, "head_sha": HEAD}}):
            with self.subTest(changes=changes), patch.object(debug_sign, "api", return_value=run), patch.object(
                    debug_sign.subprocess, "check_output", return_value=json.dumps([
                        {"artifacts": [dict(artifact, **changes)]}])):
                with self.assertRaises(ValueError):
                    debug_sign.download_bundle({"workflow_run": run}, Path("unused"))


if __name__ == "__main__":
    unittest.main()
