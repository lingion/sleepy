"""Security and trigger contracts for debug-apk.yml."""
import ast
import itertools
import os
from pathlib import Path
import re
import subprocess
import tempfile
import textwrap
import unittest

SHA = "a" * 40


def evaluate_condition(expression, values):
    """Interpret the gate's small boolean subset, without eval or a YAML dependency."""
    tree = ast.parse(expression.replace("&&", " and ").replace("||", " or "), mode="eval")

    def visit(node):
        if isinstance(node, ast.Expression):
            return visit(node.body)
        if isinstance(node, ast.Constant):
            return node.value
        if isinstance(node, ast.Name):
            return values[node.id]
        if isinstance(node, ast.Attribute):
            return visit(node.value)[node.attr]
        if isinstance(node, ast.Compare) and len(node.ops) == 1 and isinstance(node.ops[0], ast.Eq):
            left, right = visit(node.left), visit(node.comparators[0])
            # GitHub Actions uses case-insensitive string comparisons.
            return left.lower() == right.lower() if isinstance(left, str) and isinstance(right, str) else left == right
        if isinstance(node, ast.BoolOp):
            parts = [visit(value) for value in node.values]
            return all(parts) if isinstance(node.op, ast.And) else any(parts)
        raise AssertionError(f"Unexpected gate syntax: {ast.dump(node)}")

    return visit(tree)


class WorkflowContractTest(unittest.TestCase):
    def test_actual_workflow_stable_key_gate_truth_table(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/debug-apk.yml").read_text()
        section = workflow.split("id: project-signing\n", 1)[1].split("        env:", 1)[0]
        expression = " ".join(section.split("if: >-", 1)[1].split())
        canonical = "lingion/sleepy/.github/workflows/debug-apk.yml@refs/heads/main"
        for repository, workflow_ref, ref, kind, override in itertools.product(
                ["lingion/sleepy", "contributor/sleepy"], [canonical, canonical.replace("main", "other")],
                ["refs/heads/main", "refs/heads/other", "refs/tags/main"],
                ["push", "pull_request", "workflow_dispatch"], ["", "main", SHA]):
            expected = (repository == "lingion/sleepy" and workflow_ref == canonical
                        and ref == "refs/heads/main" and (kind == "push" or (kind == "workflow_dispatch" and override == "")))
            values = {"github": {"repository": repository, "workflow_ref": workflow_ref,
                                  "ref": ref, "event_name": kind}, "inputs": {"ref": override},
                      "steps": {"authorize": {"outputs": {"stable": str(expected).lower()}}}}
            with self.subTest(values=values):
                self.assertEqual(evaluate_condition(expression, values), expected)
        secret_step = workflow.split("id: project-signing\n", 1)[1].split("      - name:", 1)[0]
        self.assertNotIn("needs.", secret_step)
        self.assertNotIn("steps.plan", secret_step)
        self.assertIn('test -n "$KEYSTORE_B64"', secret_step)
        self.assertIn("exit 1", secret_step)
        self.assertEqual(workflow.count("secrets.DEBUG_KEYSTORE_BASE64"), 1)
        self.assertLess(workflow.index("Verify source commit"), workflow.index("id: project-signing"))

    def test_case_sensitive_authorization_script_before_secret_and_source_code(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/debug-apk.yml").read_text()
        section = workflow.split("id: authorize\n", 1)[1].split("      - name:", 1)[0]
        script = textwrap.dedent(section.split("        run: |\n", 1)[1])
        for repository, ref, kind, override, expected in [
                ("lingion/sleepy", "refs/heads/main", "push", "", True),
                ("lingion/sleepy", "refs/heads/main", "workflow_dispatch", "", True),
                ("lingion/sleepy", "refs/heads/Main", "workflow_dispatch", "", False),
                ("lingion/sleepy", "refs/heads/MAIN", "workflow_dispatch", "", False),
                ("contributor/sleepy", "refs/heads/main", "push", "", False),
                ("lingion/sleepy", "refs/heads/main", "workflow_dispatch", "main", False),
                ("lingion/sleepy", "refs/heads/main", "pull_request", "", False)]:
            with self.subTest(ref=ref, kind=kind, override=override), tempfile.TemporaryDirectory() as temp:
                output = Path(temp) / "output"
                environment = dict(os.environ, GITHUB_REPOSITORY=repository, GITHUB_REF=ref,
                    GITHUB_WORKFLOW_REF=f"{repository}/.github/workflows/debug-apk.yml@{ref}",
                    GITHUB_EVENT_NAME=kind, SOURCE_REF_INPUT=override, GITHUB_OUTPUT=str(output))
                subprocess.run(["bash", "-e", "-o", "pipefail", "-c", script], env=environment, check=True)
                self.assertEqual(output.read_text(), f"stable={str(expected).lower()}\n")
        self.assertLess(workflow.index("id: authorize"), workflow.index("path: source"))
        self.assertIn("SOURCE_SHA: ${{ steps.revision.outputs.sha }}", workflow)
        self.assertIn("EXPECTED_CERTIFICATE_SHA256: ${{ steps.certificate.outputs.sha256 }}", workflow)

    def test_pr_concurrency_cancels_whole_workflow_before_prepare(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/debug-apk.yml").read_text()
        global_config = workflow.split("\njobs:", 1)[0]
        self.assertIn("github.event.pull_request.number", global_config)
        self.assertIn("|| github.run_id", global_config)
        self.assertIn("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", global_config)

    def test_selected_uploads_survive_previous_failure_without_masking_job(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/debug-apk.yml").read_text()
        upload_conditions = re.findall(r"id: (?:arm64|v7a|x86|universal)\n        if: (.+)", workflow)
        self.assertEqual(len(upload_conditions), 4)
        for condition in upload_conditions:
            self.assertIn("!cancelled()", condition)
            self.assertIn("steps.package.outcome == 'success'", condition)
        self.assertNotIn("continue-on-error", workflow)
        self.assertIn("always() && steps.tooling.outcome == 'success'", workflow)

    def test_automatic_entries_and_security_wiring(self):
        root = Path(__file__).resolve().parents[1]
        workflow = (root / ".github/workflows/debug-apk.yml").read_text()
        self.assertIn("pull_request:", workflow)
        self.assertIn("push:", workflow)
        self.assertNotIn("pull_request_target", workflow)
        self.assertNotIn("RELEASE_KEYSTORE", workflow)
        self.assertIn("cache-read-only: true", workflow)
        self.assertIn("github.workflow_sha", workflow)
        self.assertIn("needs.prepare.outputs.concurrency_key", workflow)
        self.assertIn("debug-abi.init.gradle", workflow)
        self.assertEqual(workflow.count("persist-credentials: false"), workflow.count("uses: actions/checkout@"))
        self.assertEqual(workflow.count("uses: actions/upload-artifact@"), 4)
        self.assertIn("python3 -m unittest discover -s scripts -p 'test_debug_*.py'", (root / ".github/workflows/ci.yml").read_text())


if __name__ == "__main__":
    unittest.main()
