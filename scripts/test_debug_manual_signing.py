"""Manual branch builds must reach the trusted signer in the same run."""
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import debug_sign
from test_debug_ci import CONTEXT, event

ROOT = Path(__file__).resolve().parents[1]
PREFIX = "lingion/sleepy/.github/workflows/debug-apk.yml@refs/heads/"


class ManualSigningTest(unittest.TestCase):
    def setUp(self):
        self.context = dict(CONTEXT, ref="refs/heads/fix/build-network",
                            workflow_ref=PREFIX + "fix/build-network")
        self.run = dict(id=100, run_number=20, run_attempt=1,
                        event="workflow_dispatch", head_sha=CONTEXT["sha"],
                        head_branch="fix/build-network", status="in_progress", conclusion=None,
                        path=".github/workflows/debug-apk.yml",
                        repository={"full_name": "lingion/sleepy"})
        self.jobs = [{"jobs": [{"name": "assemble-debug", "conclusion": "success"}]}]

    def current(self, payload=None, **context):
        with patch.object(debug_sign, "api", return_value=self.run), patch.object(
                debug_sign.subprocess, "check_output", return_value=json.dumps(self.jobs)):
            return debug_sign.current_build(payload or event(), dict(self.context, **context))

    def test_selected_branch_is_officially_signed_without_changing_source_or_abi(self):
        for inputs in ({}, {"ref": "c" * 40, "abi": "armeabi-v7a"}):
            payload = event()
            payload["inputs"] = inputs
            with self.subTest(inputs=inputs):
                plan = self.current(payload)["expected_plan"]
                self.assertEqual(plan["source_ref"], inputs.get("ref", CONTEXT["sha"]))
                self.assertEqual(plan["abi"], inputs.get("abi", "all"))
                self.assertEqual(plan["workflow_ref"], self.context["workflow_ref"])

    def test_branch_context_cannot_authorize_other_events_repositories_or_workflows(self):
        for changes in (
                {"event_name": "push"}, {"event_name": "pull_request_target"},
                {"repository": "contributor/sleepy"}, {"repository": "Lingion/Sleepy"},
                {"workflow_ref": PREFIX.replace("debug-apk.yml", "other.yml") + "fix/build-network"},
                {"workflow_ref": PREFIX.replace("refs/heads/", "refs/tags/") + "v1"},
                {"workflow_ref": PREFIX},
                {"ref": "refs/heads/different"},
                {"workflow_ref": PREFIX + "different", "ref": "refs/heads/different"}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.current(**changes)

    def test_manual_signer_checks_run_attempt_and_successful_build(self):
        for changes in ({"run_attempt": 2}, {"head_sha": "d" * 40}, {"head_branch": "other"}):
            original = dict(self.run)
            self.run.update(changes)
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.current()
            self.run = original
        self.jobs[0]["jobs"][0]["conclusion"] = "failure"
        with self.assertRaises(ValueError):
            self.current()

    def test_branch_caller_uses_main_signer_and_main_tooling(self):
        build = (ROOT / ".github/workflows/debug-apk.yml").read_text()
        caller = build.split("  sign-debug:", 1)[1]
        self.assertIn("github.event_name == 'workflow_dispatch'", caller)
        self.assertIn("uses: lingion/sleepy/.github/workflows/debug-sign.yml@main", caller)
        signer = (ROOT / ".github/workflows/debug-sign.yml").read_text()
        self.assertIn("ref: refs/heads/main", signer)
        self.assertNotIn("ref: ${{ github.workflow_sha }}", signer)


if __name__ == "__main__":
    unittest.main()
