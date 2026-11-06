"""PR 构建使用真实 Git 仓库验证最新 main 合并与产物来源。"""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import debug_ci
from test_debug_ci import CONTEXT, event


class MergeSourceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.base = self.root / "upstream"
        self.source = self.root / "fork"
        self.base.mkdir()
        self.git(self.base, "init", "-b", "main")
        self.common = self.commit(self.base, "shared.txt", "initial\n")
        self.git(self.root, "clone", str(self.base), str(self.source))
        self.git(self.source, "checkout", "-b", "feature")
        self.head = self.commit(self.source, "feature.txt", "PR change\n")
        self.main = self.commit(self.base, "main-only.txt", "latest main\n")
        payload = event()
        payload["pull_request"] = {"number": 286, "head": {
            "repo": {"full_name": "contributor/sleepy"}, "sha": self.head, "ref": "feature"}}
        self.plan = debug_ci.make_plan(payload, dict(CONTEXT, event_name="pull_request"))
        # 只替换网络地址，计划仍需选择 PR 目标仓库。
        self.plan["base_url"] = str(self.base)

    def git(self, directory, *args):
        return subprocess.check_output([
            "git", "-C", str(directory), "-c", "user.name=CI Test",
            "-c", "user.email=ci-test@example.invalid", "-c", "commit.gpgsign=false",
            *args], text=True, stderr=subprocess.PIPE).strip()

    def commit(self, directory, filename, content):
        (directory / filename).write_text(content)
        self.git(directory, "add", filename)
        self.git(directory, "commit", "-m", "test fixture")
        return self.git(directory, "rev-parse", "HEAD")

    def test_merges_fixed_pr_head_with_target_main_not_stale_fork_main(self):
        self.assertEqual(self.plan["base_repository"], "lingion/sleepy")
        self.assertEqual(self.git(self.source, "rev-parse", "main"), self.common)
        prepared = debug_ci.prepare_source(self.source, self.plan)
        self.assertEqual((self.source / "feature.txt").read_text(), "PR change\n")
        self.assertEqual((self.source / "main-only.txt").read_text(), "latest main\n")
        self.assertEqual(prepared["pr_head_sha"], self.head)
        self.assertEqual(prepared["main_sha"], self.main)
        self.assertEqual(prepared["merge_sha"], self.git(self.source, "rev-parse", "HEAD"))
        self.assertEqual(prepared["source_sha"], prepared["merge_sha"])
        self.assertEqual(self.git(self.source, "show", "-s", "--format=%P", "HEAD").split(),
                         [self.main, self.head])
        self.assertEqual(self.git(self.source, "status", "--porcelain"), "")

    def test_rerun_fetches_main_again_but_keeps_event_head(self):
        first = debug_ci.prepare_source(self.source, self.plan)
        newer = self.commit(self.base, "newer.txt", "main advanced after event\n")
        self.git(self.source, "checkout", "feature")
        self.commit(self.source, "later-pr.txt", "not in this event\n")
        self.git(self.source, "checkout", "--detach", self.head)
        second = debug_ci.prepare_source(self.source, self.plan)
        self.assertEqual(second["main_sha"], newer)
        self.assertEqual(second["pr_head_sha"], self.head)
        self.assertNotEqual(first["merge_sha"], second["merge_sha"])
        self.assertTrue((self.source / "newer.txt").exists())
        self.assertFalse((self.source / "later-pr.txt").exists())

    def test_conflict_fails_instead_of_building_head_only(self):
        head = self.commit(self.source, "shared.txt", "PR version\n")
        self.commit(self.base, "shared.txt", "main version\n")
        with self.assertRaises(subprocess.CalledProcessError):
            debug_ci.prepare_source(self.source, dict(self.plan, expected_sha=head, pr_head_sha=head))

    def test_fetch_failure_does_not_reuse_stale_main(self):
        with self.assertRaises(subprocess.CalledProcessError):
            debug_ci.prepare_source(self.source, dict(self.plan, base_url=str(self.root / "missing")))

    def test_conflict_cli_reports_files_and_publishes_no_build_revision(self):
        head = self.commit(self.source, "shared.txt", "PR version\n")
        self.commit(self.base, "shared.txt", "main version\n")
        plan_file = self.root / "plan.json"
        output = self.root / "output"
        plan_file.write_text(json.dumps(dict(self.plan, expected_sha=head, pr_head_sha=head)))
        result = subprocess.run([sys.executable, "-I", str(Path(debug_ci.__file__).resolve()),
                                 "prepare", "--plan", str(plan_file), "--source", str(self.source)],
                                env=dict(os.environ, GITHUB_OUTPUT=str(output)),
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(output.exists())
        self.assertNotIn("merge_sha", json.loads(plan_file.read_text()))
        self.assertIn("CONFLICT", result.stderr)
        self.assertIn("shared.txt", result.stderr)

    def test_wrong_pr_checkout_is_rejected_before_merge(self):
        self.git(self.source, "checkout", "--detach", self.common)
        with self.assertRaises(ValueError):
            debug_ci.prepare_source(self.source, self.plan)

    def test_non_pr_builds_keep_selected_commit_without_fetch(self):
        for kind in ("push", "workflow_dispatch"):
            with self.subTest(kind=kind):
                plan = debug_ci.make_plan(event(), dict(CONTEXT, event_name=kind, sha=self.head))
                prepared = debug_ci.prepare_source(self.source, plan)
                self.assertEqual(prepared["source_sha"], self.head)
                self.assertFalse(prepared.get("merge_sha"))
                self.assertFalse((self.source / "main-only.txt").exists())

    def test_prepare_cli_persists_identity_for_packaging_and_summary(self):
        plan_file = self.root / "plan.json"
        output = self.root / "output"
        plan_file.write_text(json.dumps(self.plan))
        subprocess.run([sys.executable, "-I", str(Path(debug_ci.__file__).resolve()),
                        "prepare", "--plan", str(plan_file), "--source", str(self.source)],
                       env=dict(os.environ, GITHUB_OUTPUT=str(output)), check=True,
                       capture_output=True, text=True)
        prepared = json.loads(plan_file.read_text())
        self.assertEqual(output.read_text(), f"sha={prepared['merge_sha']}\n")
        self.assertEqual(prepared["pr_head_sha"], self.head)
        self.assertEqual(prepared["main_sha"], self.main)
        summary = debug_ci.render_summary(prepared, {}, "success")
        for sha in (self.head, self.main, prepared["merge_sha"]):
            self.assertIn(sha, summary)

    def test_package_requires_merge_and_records_all_three_revisions(self):
        apk_dir = self.root / "apk"
        apk_dir.mkdir()
        (apk_dir / "app-debug.apk").write_bytes(b"test APK")
        (apk_dir / "output-metadata.json").write_text(json.dumps({
            "variantName": "debug", "applicationId": "com.lingion.sleepy.debug",
            "elements": [{"outputFile": "app-debug.apk", "filters": []}]}))
        plan = dict(self.plan, abi="universal")
        identity = {"application_id": "com.lingion.sleepy.debug", "debuggable": True,
                    "version_name": "test", "certificate_sha256": "c" * 64}

        def package(prepared, revision, destination):
            return debug_ci.package_apks(apk_dir, destination, prepared, revision, "ephemeral",
                                         lambda path: identity, expected_certificate="c" * 64)

        with self.assertRaises(ValueError):
            package(plan, self.head, self.root / "unmerged")
        self.assertFalse((self.root / "unmerged").exists())
        prepared = debug_ci.prepare_source(self.source, plan)
        with self.assertRaises(ValueError):
            package(prepared, self.head, self.root / "wrong-revision")
        destination = self.root / "artifacts"
        package(prepared, prepared["merge_sha"], destination)
        info = json.loads((destination / "universal/build-info.json").read_text())
        self.assertEqual(info["pr_head_sha"], self.head)
        self.assertEqual(info["main_sha"], self.main)
        self.assertEqual(info["source_sha"], prepared["merge_sha"])
        self.assertEqual(info["merge_sha"], prepared["merge_sha"])
        self.assertIn(prepared["merge_sha"][:12], info["apk"])


if __name__ == "__main__":
    unittest.main()
