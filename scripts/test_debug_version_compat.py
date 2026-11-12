"""旧工作流重跑仍可用新可信签名器，实际版本由APK读取。"""
import json
import unittest

import test_debug_sign as signing_tests


class LegacyVersionMetadataTest(unittest.TestCase):
    def test_signs_legacy_metadata_and_records_actual_version_code(self):
        fixture = signing_tests.SigningTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        # 旧构建元数据无version_code；新inspector必须从真实APK读取。
        def inspect(path):
            return dict(fixture.identity, version_code=10064)
        result = fixture.sign(inspector=inspect)
        self.assertEqual(set(result), {"universal"})
        metadata = json.loads((fixture.root / "out/universal/build-info.json").read_text())
        self.assertEqual(metadata["version_code"], 10064)


if __name__ == "__main__":
    unittest.main()
