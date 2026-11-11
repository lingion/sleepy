"""Exercise JDK upgrade and drift detection without a network or installed JDK."""
import contextlib
import io
from pathlib import Path
import tempfile
import unittest

import jdk_config


class JdkConfigTest(unittest.TestCase):
    def test_patch_and_build_upgrade_requires_regeneration(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            versions = root / ".tool-versions"
            versions.write_text("java temurin-25.0.5+8\n", encoding="utf-8")
            self.assertEqual(0, jdk_config.main(["--root", directory]))
            target = root / jdk_config.DAEMON_CONFIG
            original = target.read_bytes()
            versions.write_text("java temurin-25.0.6+9\n", encoding="utf-8")
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1, jdk_config.main(["--check", "--root", directory]))
            self.assertEqual(original, target.read_bytes(), "check mode must not repair drift silently")
            self.assertEqual(0, jdk_config.main(["--root", directory]))
            self.assertIn("/jdk-25.0.6+9/linux/x64/", target.read_text(encoding="utf-8"))
            self.assertEqual(0, jdk_config.main(["--check", "--root", directory]))

    def test_invalid_or_ambiguous_versions_are_rejected(self):
        for content in ["", "java 25", "java temurin-26.0.1+1", "java temurin-25.0.6-ea+1",
                        "java temurin-25.0.5+8 extra", "java temurin-25.0.5+8\njava temurin-25.0.6+9",
                        "java temurin-25.0.5+8/../../other"]:
            with self.subTest(content=content), self.assertRaises(ValueError):
                jdk_config.java_version(content)

    def test_comments_other_tools_and_security_revision_are_supported(self):
        self.assertEqual("25.0.4.1+1", jdk_config.java_version(
            "# Runtime tools\nnodejs 24.0.0\njava temurin-25.0.4.1+1 # security update\n"))

    def test_missing_bootstrap_file_fails_check(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".tool-versions").write_text("java temurin-25.0.5+8\n", encoding="utf-8")
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1, jdk_config.main(["--check", "--root", directory]))
            self.assertFalse((root / jdk_config.DAEMON_CONFIG).exists())


if __name__ == "__main__":
    unittest.main()
