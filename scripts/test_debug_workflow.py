"""构建和官方重签工作流的事件、凭证隔离及发布约束。"""
import os
from pathlib import Path
import re
import subprocess
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]


class WorkflowContractTest(unittest.TestCase):
    def test_merge_preparation_gates_build_and_supplies_artifact_revision(self):
        workflow = (ROOT / ".github/workflows/debug-apk.yml").read_text()
        revision_step = workflow.split("id: revision\n", 1)[1].split("      - uses:", 1)[0]
        self.assertIn("python3 -I tooling/scripts/debug_ci.py prepare", revision_step)
        self.assertIn('--plan "$RUNNER_TEMP/debug-plan.json"', revision_step)
        self.assertIn('--source "$GITHUB_WORKSPACE/source"', revision_step)
        self.assertNotIn("if:", revision_step)
        self.assertNotIn("continue-on-error", workflow)
        self.assertLess(workflow.index("id: revision"), workflow.index("Assemble selected debug ABI"))
        self.assertIn("SOURCE_SHA: ${{ steps.revision.outputs.sha }}", workflow)

    def test_build_runner_never_receives_official_key(self):
        workflow = (ROOT / ".github/workflows/debug-apk.yml").read_text()
        build = workflow.split("  sign-debug:", 1)[0]
        self.assertNotIn("secrets.", build)
        self.assertNotIn("project-signing", workflow)
        self.assertIn("SIGNING_MODE: ephemeral", workflow)
        self.assertIn("EXPECTED_CERTIFICATE_SHA256: ${{ steps.certificate.outputs.sha256 }}", workflow)
        self.assertNotIn("setup-gradle", build)
        self.assertNotIn("actions: write", build)
        self.assertIn("debug-signing-input-${{ github.run_id }}-${{ github.run_attempt }}", workflow)
        self.assertEqual(workflow.count("uses: actions/upload-artifact@"), 1)
        self.assertIn("retention-days: 1", workflow)

    def test_official_signer_uses_only_trusted_tooling_and_never_builds_target_code(self):
        workflow = (ROOT / ".github/workflows/debug-sign.yml").read_text()
        self.assertIn("workflow_call:", workflow)
        self.assertNotIn("workflow_run:", workflow)
        caller = (ROOT / ".github/workflows/debug-apk.yml").read_text()
        self.assertIn("needs: [prepare, assemble-debug]", caller)
        self.assertIn("uses: ./.github/workflows/debug-sign.yml", caller)
        self.assertIn("repository: lingion/sleepy", workflow)
        self.assertIn("ref: ${{ github.workflow_sha }}", workflow)
        self.assertEqual(workflow.count("uses: actions/checkout@"), 1)
        self.assertEqual(workflow.count("secrets.DEBUG_KEYSTORE_BASE64"), 1)
        self.assertNotIn("head_sha", workflow)
        self.assertNotIn("./gradlew", workflow)
        self.assertNotIn("setup-gradle", workflow)
        self.assertNotIn("RELEASE_KEYSTORE", workflow)
        self.assertIn("actions: write", workflow)
        self.assertNotIn("contents: write", workflow)
        self.assertLess(workflow.index("debug_sign.py download"), workflow.index("secrets.DEBUG_KEYSTORE_BASE64"))
        self.assertIn('test -n "$KEYSTORE_B64"', workflow)
        self.assertIn("exit 1", workflow)
        self.assertNotIn("genkeypair", workflow)
        self.assertIn('rm -f "$RUNNER_TEMP/official-debug.keystore"', workflow)

    def test_case_sensitive_official_context_guard_runs_before_secret(self):
        workflow = (ROOT / ".github/workflows/debug-sign.yml").read_text()
        section = workflow.split("- name: Verify official signing context\n", 1)[1].split("      - uses:", 1)[0]
        script = textwrap.dedent(section.split("        run: |\n", 1)[1])
        canonical = "lingion/sleepy/.github/workflows/debug-apk.yml@refs/heads/main"
        for repo, kind, ref, expected in [
            ("lingion/sleepy", "pull_request_target", canonical, True),
            ("lingion/sleepy", "push", canonical, True),
            ("lingion/sleepy", "workflow_dispatch", canonical, True),
            ("lingion/sleepy", "workflow_run", canonical, False),
            ("contributor/sleepy", "pull_request_target", canonical, False),
            ("Lingion/Sleepy", "pull_request_target", canonical, False),
            ("lingion/sleepy", "pull_request", canonical, False),
            ("lingion/sleepy", "pull_request_target", canonical.replace("main", "feature"), False),
            ("lingion/sleepy", "pull_request_target", canonical.replace("main", "MAIN"), False),
        ]:
            with self.subTest(repo=repo, kind=kind, ref=ref):
                result = subprocess.run(["bash", "-e", "-o", "pipefail", "-c", script], env=dict(
                    os.environ, GITHUB_REPOSITORY=repo, GITHUB_EVENT_NAME=kind, GITHUB_WORKFLOW_REF=ref))
                self.assertEqual(result.returncode == 0, expected)
        self.assertLess(workflow.index("Verify official signing context"), workflow.index("secrets.DEBUG_KEYSTORE_BASE64"))

    def test_pr_concurrency_cancels_whole_workflow_before_prepare(self):
        workflow = (ROOT / ".github/workflows/debug-apk.yml").read_text()
        global_config = workflow.split("\njobs:", 1)[0]
        self.assertIn("github.event.pull_request.number", global_config)
        self.assertIn("|| github.run_id", global_config)
        self.assertIn("cancel-in-progress: ${{ github.event_name == 'pull_request_target' }}", global_config)

    def test_final_uploads_require_all_signatures_verified_and_keep_partial_upload_diagnostics(self):
        workflow = (ROOT / ".github/workflows/debug-sign.yml").read_text()
        conditions = re.findall(r"id: (?:arm64|v7a|x86|universal)\n        if: (.+)", workflow)
        self.assertEqual(len(conditions), 4)
        for condition in conditions:
            self.assertIn("!cancelled()", condition)
            self.assertIn("steps.sign.outcome == 'success'", condition)
        self.assertNotIn("continue-on-error", workflow)
        self.assertIn("always() && steps.sign.outcome == 'success'", workflow)

    def test_automatic_entries_and_security_wiring(self):
        workflow = (ROOT / ".github/workflows/debug-apk.yml").read_text()
        self.assertIn("pull_request_target:", workflow)
        self.assertIn("push:", workflow)
        self.assertNotIn("  pull_request:", workflow)
        self.assertIn("github.workflow_sha", workflow)
        self.assertIn("needs.prepare.outputs.concurrency_key", workflow)
        self.assertIn("debug-abi.init.gradle", workflow)
        self.assertEqual(workflow.count("persist-credentials: false"), workflow.count("uses: actions/checkout@"))
        self.assertIn("python3 -m unittest discover -s scripts -p 'test_debug_*.py'", (ROOT / ".github/workflows/ci.yml").read_text())


if __name__ == "__main__":
    unittest.main()
