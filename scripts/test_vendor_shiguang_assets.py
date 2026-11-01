import contextlib
import hashlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import vendor_shiguang_assets as vendor


class VendorLineEndingsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'source'
        self.output = self.root / 'app/src/main/assets/third_party/shiguang'
        self.script = '// 测试脚本\nwindow.example = true;\n'.encode('utf-8')
        self.source_files = {
            'index/root_index.yaml': json.dumps({'schools': [
                {'id': 'EXAMPLE', 'name': '测试大学', 'resource_folder': 'EXAMPLE'}
            ]}, ensure_ascii=False).encode('utf-8') + b'\n',
            'resources/EXAMPLE/adapters.yaml': json.dumps({'adapters': [{
                'adapter_id': 'EXAMPLE',
                'adapter_name': '测试大学教务',
                'asset_js_path': 'example.js',
                'import_url': 'https://example.edu.cn/',
            }]}, ensure_ascii=False).encode('utf-8') + b'\n',
            'resources/EXAMPLE/example.js': self.script,
            'scripts/types/shiguang-bridge.d.ts': b'interface Example {\n}\n',
            'LICENSE': b'MIT License\nCopyright Example\n',
            '.git_commit': b'example-commit\n',
        }

    def write_source(self, newline):
        for name, data in self.source_files.items():
            path = self.source / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data.replace(b'\n', newline))

    def run_vendor(self, check=False):
        args = ['vendor_shiguang_assets.py', '--source', str(self.source)]
        if check:
            args.append('--check')
        with patch.multiple(vendor, ROOT=self.root, OUT=self.output), \
                patch.object(sys, 'argv', args), contextlib.redirect_stdout(io.StringIO()):
            vendor.main()

    def snapshot(self):
        paths = list(self.output.rglob('*')) + [self.root / 'scripts/types/shiguang-bridge.d.ts']
        return {str(p.relative_to(self.root)): p.read_bytes() for p in paths if p.is_file()}

    def test_vendor_and_check_are_stable_across_source_line_endings(self):
        self.write_source(b'\r\n')
        self.run_vendor()
        expected = self.snapshot()
        for name, data in expected.items():
            self.assertNotIn(b'\r', data, name)
        manifest = json.loads((self.output / 'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual('测试大学', manifest['schools'][0]['sgName'])
        self.assertEqual(hashlib.sha256(self.script).hexdigest(), manifest['scripts'][0]['sha256'])
        self.assertEqual(self.script, (self.output / 'js/EXAMPLE/example.js').read_bytes())
        for newline in (b'\r\n', b'\n', b'\r'):
            with self.subTest(newline=newline):
                self.write_source(newline)
                self.run_vendor(check=True)
                self.run_vendor()
                self.assertEqual(expected, self.snapshot())
                self.run_vendor(check=True)

    def test_check_accepts_checkout_line_endings_without_writing(self):
        self.write_source(b'\r\n')
        self.run_vendor()
        expected = self.snapshot()
        for newline in (b'\r\n', b'\n', b'\r'):
            with self.subTest(newline=newline):
                for name, data in expected.items():
                    (self.root / name).write_bytes(data.replace(b'\n', newline))
                before_check = self.snapshot()
                self.run_vendor(check=True)
                self.assertEqual(before_check, self.snapshot())
                self.run_vendor()
                self.assertEqual(expected, self.snapshot())

    def test_check_rejects_modified_content_for_every_line_ending(self):
        self.write_source(b'\r\n')
        self.run_vendor()
        script_path = self.output / 'js/EXAMPLE/example.js'
        for newline in (b'\r\n', b'\n', b'\r'):
            with self.subTest(newline=newline):
                changed = (self.script + b'// changed\n').replace(b'\n', newline)
                script_path.write_bytes(changed)
                with self.assertRaisesRegex(AssertionError, 'asset drifted'):
                    self.run_vendor(check=True)
                self.run_vendor()
                self.assertEqual(self.script, script_path.read_bytes())
                self.run_vendor(check=True)


if __name__ == '__main__':
    unittest.main()
