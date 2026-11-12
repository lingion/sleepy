"""PR builds must derive versions from the target repository's release tags."""
from pathlib import Path
import subprocess
import tempfile
import unittest

import debug_ci
from test_debug_ci import CONTEXT, event


class TrustedReleaseTagsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.base = self.root / "upstream"
        self.source = self.root / "fork"
        self.base.mkdir()
        self.git(self.base, "init", "-b", "main")
        self.commit(self.base, "shared.txt", "old release\n")
        self.git(self.base, "tag", "v1.0.59")
        self.git(self.root, "clone", str(self.base), str(self.source))
        self.git(self.source, "checkout", "-b", "feature")
        self.head = self.commit(self.source, "feature.txt", "PR change\n")
        self.main = self.commit(self.base, "main.txt", "new release\n")
        self.git(self.base, "tag", "v1.0.64")
        payload = event()
        payload["pull_request"] = {"number": 425, "head": {
            "repo": {"full_name": "contributor/sleepy"},
            "sha": self.head, "ref": "feature"}}
        self.plan = debug_ci.make_plan(payload, dict(CONTEXT, event_name="pull_request_target"))
        self.plan["base_url"] = str(self.base)

    def git(self, directory, *args):
        return subprocess.check_output([
            "git", "-C", str(directory), "-c", "user.name=CI Test",
            "-c", "user.email=ci-test@example.invalid", "-c", "commit.gpgsign=false",
            *args], text=True, stderr=subprocess.PIPE).strip()

    def commit(self, directory, filename, contents):
        (directory / filename).write_text(contents)
        self.git(directory, "add", filename)
        self.git(directory, "commit", "-m", "release tag fixture")
        return self.git(directory, "rev-parse", "HEAD")

    def version_tag(self):
        # This is the exact tag lookup used by app/build.gradle.kts.
        return self.git(self.source, "describe", "--tags", "--match", "v*.*.*", "--abbrev=0")

    def test_stale_fork_tags_do_not_downgrade_latest_main_version(self):
        prepared = debug_ci.prepare_source(self.source, self.plan)
        self.assertEqual(prepared["main_sha"], self.main)
        self.assertEqual(self.version_tag(), "v1.0.64")

    def test_fork_tag_collision_is_replaced_by_target_release_tag(self):
        self.git(self.source, "tag", "v1.0.64", self.head)
        debug_ci.prepare_source(self.source, self.plan)
        self.assertEqual(self.git(self.source, "rev-parse", "v1.0.64"), self.main)

    def test_fork_only_version_tags_cannot_override_official_version(self):
        self.git(self.source, "tag", "v9.9.9", self.head)
        debug_ci.prepare_source(self.source, self.plan)
        self.assertEqual(self.git(self.source, "tag", "--list", "v9.9.9"), "")
        self.assertEqual(self.version_tag(), "v1.0.64")

    def test_rerun_refreshes_release_tags_with_latest_main(self):
        debug_ci.prepare_source(self.source, self.plan)
        newer_main = self.commit(self.base, "newer.txt", "next release\n")
        self.git(self.base, "tag", "v1.0.65")
        self.git(self.source, "checkout", "--detach", self.head)
        prepared = debug_ci.prepare_source(self.source, self.plan)
        self.assertEqual(prepared["main_sha"], newer_main)
        self.assertEqual(prepared["pr_head_sha"], self.head)
        self.assertEqual(self.version_tag(), "v1.0.65")


if __name__ == "__main__":
    unittest.main()
