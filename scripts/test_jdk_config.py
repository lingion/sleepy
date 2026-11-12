"""Exercise JDK upgrade and drift detection without a network or installed JDK."""
import contextlib
import io
import json
from pathlib import Path
import re
import tempfile
import unittest

import jdk_config


class JdkConfigTest(unittest.TestCase):
    PLATFORMS = {
        "LINUX.AARCH64": "linux/aarch64",
        "LINUX.X86_64": "linux/x64",
        "MAC_OS.AARCH64": "mac/aarch64",
        "MAC_OS.X86_64": "mac/x64",
        "WINDOWS.X86_64": "windows/x64",
    }

    def test_bootstrap_covers_published_desktop_platforms(self):
        config = jdk_config.daemon_config("25.0.5+8")
        urls = dict(line.split("=", 1) for line in config.splitlines()
                    if line.startswith("toolchainUrl."))
        self.assertEqual({f"toolchainUrl.{platform}" for platform in self.PLATFORMS}, set(urls))
        for platform, path in self.PLATFORMS.items():
            with self.subTest(platform=platform):
                self.assertEqual(
                    f"https\\://api.adoptium.net/v3/binary/version/jdk-25.0.5+8/{path}/jdk/hotspot/normal/eclipse",
                    urls[f"toolchainUrl.{platform}"])

    def test_check_rejects_missing_platform_and_platform_url_drift(self):
        expected = jdk_config.daemon_config("25.0.5+8")
        mac_line = next((line for line in expected.splitlines()
                         if line.startswith("toolchainUrl.MAC_OS.AARCH64=")), "")
        self.assertTrue(mac_line, "Apple Silicon must be bootstrappable")
        for changed in [expected.replace(mac_line + "\n", ""),
                        expected.replace("/mac/aarch64/", "/linux/aarch64/"),
                        expected.replace(mac_line, mac_line.replace("25.0.5+8", "25.0.6+9"))]:
            with self.subTest(changed=changed), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / ".tool-versions").write_text("java temurin-25.0.5+8\n", encoding="utf-8")
                target = root / jdk_config.DAEMON_CONFIG
                target.parent.mkdir(parents=True)
                target.write_text(changed, encoding="utf-8")
                with contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(1, jdk_config.main(["--check", "--root", directory]))
                self.assertEqual(changed, target.read_text(encoding="utf-8"))

    def test_renovate_matches_every_bootstrap_platform(self):
        renovate = json.loads((jdk_config.ROOT / "renovate.json").read_text(encoding="utf-8"))
        patterns = [re.compile(pattern.replace("(?<currentValue>", "(?P<currentValue>"))
                    for manager in renovate["customManagers"] for pattern in manager["matchStrings"]]
        config = jdk_config.daemon_config("25.0.5+8")
        for platform in self.PLATFORMS:
            line = next((line for line in config.splitlines()
                         if line.startswith(f"toolchainUrl.{platform}=")), "")
            with self.subTest(platform=platform):
                matches = [match.group("currentValue") for pattern in patterns
                           for match in pattern.finditer(line)]
                self.assertEqual(["25.0.5+8"], matches)

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
            upgraded = target.read_text(encoding="utf-8")
            for path in self.PLATFORMS.values():
                self.assertIn(f"/jdk-25.0.6+9/{path}/", upgraded)
            self.assertNotIn("25.0.5+8", upgraded)
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
