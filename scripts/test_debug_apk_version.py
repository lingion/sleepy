"""APK 版本必须可审计，重签前后不得静默倒退。"""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import debug_ci
import test_debug_ci as package_tests
import test_debug_sign as signing_tests


class ApkVersionTest(unittest.TestCase):
    def test_inspection_records_android_version_code(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "app.apk"
            with zipfile.ZipFile(apk, "w"):
                pass
            badging = ("package: name='com.lingion.sleepy.debug' versionCode='10064' "
                       "versionName='1.0.64-debug'\napplication-debuggable\n")
            cert = "Signer #1 certificate SHA-256 digest: " + "c" * 64 + "\n"
            with patch.object(debug_ci, "find_build_tool", return_value="/sdk/tool"), patch.object(
                    debug_ci.subprocess, "check_output", side_effect=[badging, cert]):
                identity = debug_ci.inspect_apk(apk)
            self.assertEqual(identity.get("version_code"), 10064)

    def test_packaging_rejects_apk_version_below_gradle_metadata(self):
        fixture = package_tests.PackageTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        fixture.inventory("arm64-v8a")
        fixture.identity.update(version_code=10059, version_name="1.0.59-debug")
        path = fixture.source / "output-metadata.json"
        metadata = json.loads(path.read_text())
        metadata["elements"][0].update(versionCode=10064, versionName="1.0.64-debug")
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "version"):
            fixture.package("arm64-v8a")
        self.assertFalse(fixture.destination.exists())

    def signing_fixture(self):
        fixture = signing_tests.SigningTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        fixture.identity["version_code"] = 10064
        fixture.write_info()
        return fixture

    def test_signing_rejects_version_code_different_from_recorded_input(self):
        fixture = self.signing_fixture()
        path = fixture.apk.parent / "build-info.json"
        metadata = json.loads(path.read_text())
        metadata["version_code"] = 10059
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "identity"):
            fixture.sign()
        self.assertFalse((fixture.root / "out/summary-info.json").exists())

    def test_resigning_cannot_change_android_version_code(self):
        fixture = self.signing_fixture()
        def inspect(path):
            code = 10064 if fixture.bundle in path.parents else 10059
            return dict(fixture.identity, version_code=code)
        with self.assertRaisesRegex(ValueError, "identity"):
            fixture.sign(inspector=inspect)
        self.assertFalse((fixture.root / "out/summary-info.json").exists())


if __name__ == "__main__":
    unittest.main()
