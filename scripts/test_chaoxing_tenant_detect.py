#!/usr/bin/env python3
"""Cross-language invariant tests: scripts/batch_url_admissions.detect_type_from_url
must stay rule-for-rule aligned with JwImportViewModel.detectProtocolFromUrlImpl
(CLAUDE.md 铁律 3). Locks the chaoxing per-school tenant host anchors added by the
WakeUP 6.6.2 catalog investigation (GitHub cross-verification: hbut.jw.chaoxing.com
= 泛雅教务租户入口, 9+ 学生仓库同端点)."""
import unittest
from batch_url_admissions import detect_type_from_url


class ChaoxingTenantHostTest(unittest.TestCase):
    def test_tenant_root_selects_chaoxing(self):
        for url in [
            "https://hbut.jw.chaoxing.com/admin",
            "http://ynzi.fanya.chaoxing.com/portal/index!utp",
            "https://jzw.teach.qlu.edu.cn/xsd/pkgl/",
        ]:
            with self.subTest(url=url):
                self.assertEqual(detect_type_from_url(url), "chaoxing")

    def test_tenant_host_inside_webvpn_rewrite(self):
        # /http/<hex>/ 重写把原 URL 原样留在 path 里 → 子串锚点仍命中
        self.assertEqual(
            detect_type_from_url("https://tju.webvpn.tju.edu.cn/http/68747470732f2f/"
                                 "hbut.jw.chaoxing.com/admin"), "chaoxing")

    def test_substring_lookalikes_not_matched(self):
        # 裸 "chaoxing" 学习通聚合域不是教务判型依据; 反向拼接不误伤
        for url in [
            "https://chaoxing.com/mooc-enc/video",
            "https://jw.chaoxing.example.org/login",
            "https://notjw.chaoxing.com.cn/x",
            "https://mirrorjw.chaoxing.com.net/portal",
        ]:
            with self.subTest(url=url):
                self.assertIsNone(detect_type_from_url(url))

    def test_path_anchors_still_work(self):
        self.assertEqual(detect_type_from_url("https://jkw.sysu.edu.cn/queryKbForGrdb"), "chaoxing")
        self.assertEqual(detect_type_from_url("https://xkb.xhu.edu.cn/xsd/pkgl/index"), "chaoxing")


if __name__ == "__main__":
    unittest.main()
