"""原构建页交付、target 事件身份及凭证边界回归。"""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import debug_ci
import debug_sign
from test_debug_ci import CONTEXT, event
import test_debug_sign as signing_tests

ROOT = Path(__file__).resolve().parents[1]


class SameRunTest(unittest.TestCase):
    def setUp(self):
        self.context = dict(CONTEXT, event_name="pull_request_target")
        self.event = event()
        self.event["pull_request"] = {"number": 310, "head": {
            "sha": "c" * 40, "ref": "feature/test", "repo": {"full_name": "contributor/sleepy"}}}
        self.run = {"id": 100, "run_number": 20, "run_attempt": 1, "event": "pull_request_target",
                    "head_sha": "c" * 40, "path": ".github/workflows/debug-apk.yml",
                    "status": "in_progress", "conclusion": None,
                    "repository": {"full_name": "lingion/sleepy"}}

    def test_target_plan_uses_event_pr_head_not_base_sha(self):
        plan = debug_ci.make_plan(self.event, self.context)
        self.assertEqual(plan["pr_head_sha"], "c" * 40)
        self.assertEqual(plan["source_ref"], "c" * 40)
        self.assertEqual(plan["abi"], "all")
        self.assertEqual(plan["base_repository"], "lingion/sleepy")

    def test_same_run_requires_exact_context_and_successful_assemble_job(self):
        jobs = [{"jobs": [{"name": "assemble-debug", "conclusion": "success"}]}]
        with patch.object(debug_sign, "api", return_value=self.run), patch.object(
                debug_sign.subprocess, "check_output", return_value=json.dumps(jobs)):
            run = debug_sign.current_build(self.event, self.context)
            self.assertEqual(run["expected_plan"]["pr_head_sha"], "c" * 40)
        for field, value in [("id", 101), ("run_attempt", 2), ("event", "pull_request"),
                             ("head_sha", "d" * 40), ("status", "completed")]:
            with self.subTest(field=field), patch.object(debug_sign, "api", return_value=dict(self.run, **{field: value})):
                with self.assertRaises(ValueError):
                    debug_sign.current_build(self.event, self.context)
        for conclusion in ("failure", "skipped", None):
            jobs[0]["jobs"][0]["conclusion"] = conclusion
            with patch.object(debug_sign, "api", return_value=self.run), patch.object(
                    debug_sign.subprocess, "check_output", return_value=json.dumps(jobs)):
                with self.assertRaises(ValueError):
                    debug_sign.current_build(self.event, self.context)

    def test_target_run_head_is_pr_head_not_base_sha(self):
        jobs = [{"jobs": [{"name": "assemble-debug", "conclusion": "success"}]}]
        with patch.object(debug_sign, "api", return_value=self.run), patch.object(
                debug_sign.subprocess, "check_output", return_value=json.dumps(jobs)):
            run = debug_sign.current_build(self.event, self.context)
            self.assertEqual(run["head_sha"], self.event["pull_request"]["head"]["sha"])
            self.assertNotEqual(run["head_sha"], self.context["sha"])
            self.assertEqual(run["expected_plan"]["workflow_sha"], self.context["workflow_sha"])
        with patch.object(debug_sign, "api", return_value=dict(self.run, head_sha=self.context["sha"])):
            with self.assertRaisesRegex(ValueError, "identity differs"):
                debug_sign.current_build(self.event, self.context)

    def test_push_and_manual_run_head_still_match_context_sha(self):
        jobs = [{"jobs": [{"name": "assemble-debug", "conclusion": "success"}]}]
        for kind in ("push", "workflow_dispatch"):
            context = dict(self.context, event_name=kind)
            payload = event()
            payload["inputs"] = {"ref": "feature/requested", "abi": "universal"}
            for sha in (context["sha"], "c" * 40):
                run = dict(self.run, event=kind, head_sha=sha, head_branch="main")
                with self.subTest(event=kind, sha=sha), patch.object(
                        debug_sign, "api", return_value=run), patch.object(
                        debug_sign.subprocess, "check_output", return_value=json.dumps(jobs)):
                    if sha == context["sha"]:
                        self.assertEqual(debug_sign.current_build(payload, context)["head_sha"], sha)
                    else:
                        with self.assertRaisesRegex(ValueError, "identity differs"):
                            debug_sign.current_build(payload, context)

    def test_same_page_signer_is_a_dependent_job_not_another_run(self):
        build = (ROOT / ".github/workflows/debug-apk.yml").read_text()
        sign = (ROOT / ".github/workflows/debug-sign.yml").read_text()
        self.assertIn("pull_request_target:", build)
        self.assertNotIn("  pull_request:", build)
        self.assertIn("needs: [prepare, assemble-debug]", build)
        self.assertIn("uses: ./.github/workflows/debug-sign.yml", build)
        self.assertIn("workflow_call:", sign)
        self.assertNotIn("workflow_run:", sign)
        self.assertEqual(sign.count("uses: actions/upload-artifact@"), 4)
        self.assertIn("debug_sign.py cleanup", sign)

    def test_untrusted_gradle_has_no_runner_credentials_or_cache(self):
        build = (ROOT / ".github/workflows/debug-apk.yml").read_text()
        assemble = build.split("  assemble-debug:", 1)[1].split("  sign-debug:", 1)[0]
        self.assertNotIn("secrets.", assemble)
        self.assertNotIn("actions: write", assemble)
        self.assertNotIn("setup-gradle", assemble)
        self.assertIn("docker run --rm", assemble)
        self.assertNotIn("--env-file", assemble)
        self.assertNotIn("docker.sock", assemble)
        self.assertNotIn("--privileged", assemble)
        self.assertIn('source/.git:/workspace/.git:ro', assemble)

    def test_cleanup_keeps_input_until_every_selected_abi_is_published(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "summary-info.json").write_text(json.dumps({
                "run_id": "100", "run_attempt": "1", "abi": "all"}))
            items = [{"id": 1, "name": "debug-signing-input-100-1", "expired": False,
                      "workflow_run": {"id": 100}}]
            for index, abi in enumerate(debug_ci.ABIS, 2):
                (root / abi).mkdir()
                name = f"sleepy-debug-official-test-{abi}"
                (root / abi / "build-info.json").write_text(json.dumps({"apk": name + ".apk"}))
                items.append({"id": index, "name": name, "expired": False, "workflow_run": {"id": 100}})
            with patch.object(debug_sign, "artifacts", return_value=items[:-1]), patch.object(
                    debug_sign.subprocess, "run") as delete:
                with self.assertRaises(ValueError):
                    debug_sign.cleanup_artifacts(root, self.context)
                delete.assert_not_called()
            items.append({"id": 6, "name": "sleepy-debug-official-old-attempt", "expired": False,
                          "workflow_run": {"id": 100}})
            with patch.object(debug_sign, "artifacts", return_value=items), patch.object(
                    debug_sign.subprocess, "run") as delete:
                debug_sign.cleanup_artifacts(root, self.context)
                self.assertEqual([call.args[0][-1] for call in delete.call_args_list], [
                    "repos/lingion/sleepy/actions/artifacts/1", "repos/lingion/sleepy/actions/artifacts/6"])
            with patch.object(debug_sign, "artifacts", return_value=items), patch.object(
                    debug_sign.subprocess, "run") as delete:
                with self.assertRaises(ValueError):
                    debug_sign.cleanup_artifacts(root, dict(self.context, run_attempt="2"))
                delete.assert_not_called()


class TargetSigningTest(unittest.TestCase):
    def test_target_uses_trusted_pr_head_and_rejects_plan_tampering(self):
        fixture = signing_tests.SigningTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        fixture.run.update(event="pull_request_target", status="in_progress", conclusion=None,
                        head_sha="e" * 40, expected_plan={
                            "pr_head_sha": fixture.info["pr_head_sha"], "abi": "universal",
                            "source_repository": fixture.info["source_repository"]})
        self.assertEqual(set(fixture.sign()), {"universal"})
        for field, value in [("pr_head_sha", "f" * 40), ("abi", "all"),
                             ("source_repository", "other/sleepy")]:
            original = fixture.info[field]
            fixture.info[field] = value
            fixture.write_info()
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "trusted event plan"):
                fixture.sign()
            fixture.info[field] = original


if __name__ == "__main__":
    unittest.main()
