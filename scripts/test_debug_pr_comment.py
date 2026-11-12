"""Publish only complete, current official APK downloads to the originating PR."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import debug_sign

ROOT = Path(__file__).resolve().parents[1]
MARKER = "<!-- sleepy-official-debug-apks -->"


class PrCommentTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.context = dict(repository="lingion/sleepy", event_name="pull_request_target",
                            workflow_ref="lingion/sleepy/.github/workflows/debug-apk.yml@refs/heads/main",
                            run_id="100", run_attempt="2")
        self.event = {"pull_request": {"number": 423, "head": {"sha": "a" * 40}}}
        self.info = dict(run_id="100", run_attempt="2", run_number="20", abi="all",
                         pr_head_sha="a" * 40, source_sha="b" * 40, signing="project")
        self.items = []
        for index, abi in enumerate(debug_sign.ci.ABIS, 1):
            directory = self.root / abi
            directory.mkdir()
            name = f"sleepy-debug-official-pr423-bbbbbbbbbbbb-{abi}-r20-a2"
            (directory / "build-info.json").write_text(json.dumps({"apk": name + ".apk"}))
            self.items.append(dict(id=index, name=name, expired=False, workflow_run={"id": 100}))
        self.current_pr = {"state": "open", "head": {"sha": "a" * 40}}
        self.comments = []
        self.write_info()

    def write_info(self):
        (self.root / "summary-info.json").write_text(json.dumps(self.info))

    def publish(self):
        with patch.object(debug_sign, "api", return_value=self.current_pr), patch.object(
                debug_sign, "artifacts", return_value=self.items), patch.object(
                debug_sign.subprocess, "check_output", return_value=json.dumps([self.comments])), patch.object(
                debug_sign.subprocess, "run") as send:
            result = debug_sign.comment_pr(self.root, self.event, self.context)
            return result, send.call_args_list

    def test_creates_one_comment_with_all_four_official_links_and_source(self):
        result, calls = self.publish()
        self.assertTrue(result)
        self.assertEqual(len(calls), 1)
        call = calls[0]
        self.assertEqual(call.args[0], ["gh", "api", "--method", "POST",
                                      "repos/lingion/sleepy/issues/423/comments", "--input", "-"])
        body = json.loads(call.kwargs["input"])["body"]
        self.assertIn(MARKER, body)
        for index, abi in enumerate(debug_sign.ci.ABIS, 1):
            self.assertIn(abi, body)
            self.assertIn(f"https://github.com/lingion/sleepy/actions/runs/100/artifacts/{index}", body)
        self.assertIn(self.info["pr_head_sha"], body)
        self.assertIn(self.info["source_sha"], body)
        self.assertIn("7 天", body)
        self.assertNotIn("debug-signing-input", body)

    def test_updates_own_bot_comment_without_touching_human_or_other_bot_comments(self):
        self.comments = [
            {"id": 10, "body": MARKER, "user": {"login": "Kerry1020", "type": "User"}},
            {"id": 11, "body": MARKER, "user": {"login": "other[bot]", "type": "Bot"}},
            {"id": 12, "body": MARKER + " old links", "user": {"login": "github-actions[bot]", "type": "Bot"}},
        ]
        _, calls = self.publish()
        self.assertEqual(calls[0].args[0][3:5], ["PATCH", "repos/lingion/sleepy/issues/comments/12"])

    def test_current_identical_bot_comment_is_not_written_again(self):
        _, calls = self.publish()
        body = json.loads(calls[0].kwargs["input"])["body"]
        self.comments = [{"id": 12, "body": body, "user": {"login": "github-actions[bot]", "type": "Bot"}}]
        _, calls = self.publish()
        self.assertEqual(calls, [])

    def test_stale_head_or_closed_pr_is_not_posted(self):
        for state, sha in [("closed", "a" * 40), ("open", "c" * 40)]:
            self.current_pr = {"state": state, "head": {"sha": sha}}
            with self.subTest(state=state, sha=sha):
                result, calls = self.publish()
                self.assertFalse(result)
                self.assertEqual(calls, [])

    def test_missing_expired_duplicate_or_foreign_artifacts_never_post(self):
        original = copy.deepcopy(self.items)
        cases = [original[:-1], original + [dict(original[0], id=10)],
                 [dict(original[0], expired=True), *original[1:]],
                 [dict(original[0], workflow_run={"id": 101}), *original[1:]],
                 [dict(original[0], id="1/evil"), *original[1:]]]
        for items in cases:
            self.items = items
            with self.subTest(items=items), self.assertRaises(ValueError):
                self.publish()

    def test_selected_single_abi_only_links_that_artifact(self):
        self.info["abi"] = "armeabi-v7a"
        self.write_info()
        _, calls = self.publish()
        body = json.loads(calls[0].kwargs["input"])["body"]
        self.assertIn("armeabi-v7a", body)
        self.assertNotIn("arm64-v8a", body)
        self.assertEqual(body.count("/artifacts/"), 1)

    def test_wrong_run_attempt_head_signing_or_event_is_rejected(self):
        for changes in ({"run_id": "101"}, {"run_attempt": "3"},
                        {"pr_head_sha": "d" * 40}, {"signing": "ephemeral"}):
            original = dict(self.info)
            self.info.update(changes)
            self.write_info()
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.publish()
            self.info = original
        self.write_info()
        for changes in ({"repository": "contributor/sleepy"}, {"event_name": "workflow_dispatch"},
                        {"workflow_ref": "lingion/sleepy/.github/workflows/debug-apk.yml@refs/heads/feature"}):
            original = dict(self.context)
            self.context.update(changes)
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.publish()
            self.context = original

    def test_only_signing_job_has_comment_permission_and_posts_after_cleanup(self):
        build = (ROOT / ".github/workflows/debug-apk.yml").read_text()
        signer = (ROOT / ".github/workflows/debug-sign.yml").read_text()
        self.assertNotIn("pull-requests: write", build.split("  sign-debug:", 1)[0])
        self.assertIn("pull-requests: write", build.split("  sign-debug:", 1)[1])
        self.assertIn("pull-requests: write", signer)
        self.assertLess(signer.index("debug_sign.py cleanup"), signer.index("debug_sign.py comment"))
        step = signer.split("- name: Comment official APK downloads on PR", 1)[1].split("      - name:", 1)[0]
        self.assertIn("success()", step)
        self.assertIn("github.event_name == 'pull_request_target'", step)


if __name__ == "__main__":
    unittest.main()
