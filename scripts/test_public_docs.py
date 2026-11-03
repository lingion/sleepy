"""Public Android release facts, not a prose snapshot. Standard library only.

Run: python3 -m unittest discover -s scripts -p 'test_public_docs.py'
PUBLIC_DOCS_ROOT can point at a staged documentation tree during review.
"""
import os
from pathlib import Path
import re
import unittest

ROOT = Path(os.environ.get("PUBLIC_DOCS_ROOT", Path(__file__).resolve().parents[1]))


class PublicDocsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.faq = (ROOT / "FAQ.md").read_text(encoding="utf-8")
        cls.llms = (ROOT / "llms.txt").read_text(encoding="utf-8")

    def test_release_provenance_and_revision_are_separate(self):
        for text in (self.faq, self.llms):
            with self.subTest(document=text[:40]):
                self.assertIn("2026-11-02", text)
                self.assertIn("v1.0.63", text)
                self.assertIn("https://github.com/lingion/sleepy/tree/v1.0.63", text)
                self.assertIn("2026-11-01", text)

    def test_undo_documents_depth_lifetime_and_redo_boundary(self):
        self.assertRegex(self.faq, r"默认[^。\n]*5[^。\n]*步")
        for token in ("实验室", "0", "负数", "进程", "重做"):
            self.assertIn(token, self.faq)
        for token in ("default 5", "0 disables", "negative", "process", "redo"):
            self.assertIn(token, self.llms)
        self.assertNotIn("撤回最近一次课表数据修改", self.faq)

    def test_occurrence_drag_and_reference_controls(self):
        for token in ("单次", "500", "空白", "幽灵", "原始", "表头", "时间对齐"):
            self.assertIn(token, self.faq)
        for token in ("occurrence", "500 ms", "empty cell", "ghost", "original", "header"):
            self.assertIn(token, self.llms)

    def test_school_counts_are_status_counts_not_live_guarantees(self):
        for text in (self.faq, self.llms):
            for count in ("4140", "352", "350", "2528", "2480", "48", "1260", "1007", "253", "348", "264", "43"):
                self.assertIn(count, text)
            for status in ("supported", "grad_supported", "unverified", "pending", "sg_asset"):
                self.assertIn(status, text)
        self.assertRegex(self.faq, r"不代表[^。\n]*(逐校|全部)[^。\n]*(验证|成功)")
        self.assertIn("not a live-verified", self.llms)

    def test_online_wakeup_is_distinct_from_offline_exchange(self):
        for text in (self.faq, self.llms):
            self.assertIn("api.wakeup.fun", text)
            self.assertIn("POST", text)
            self.assertIn("sleepy-v1", text)
            self.assertIn("ICS", text)
        self.assertIn("两次", self.faq)
        self.assertIn("two POST", self.llms)
        self.assertNotIn("WakeUp share text containing URL-encoded JSON", self.llms)
        self.assertIn("third-party", self.llms)

    def test_graphics_and_launcher_api_boundaries(self):
        for text in (self.faq, self.llms):
            self.assertIn("Material 3 Expressive", text)
            self.assertIn("API 33", text)
            self.assertIn("Android 13", text)
            self.assertIn("Android 12", text)
        self.assertIn("旧设备", self.faq)
        self.assertIn("known limitations", self.llms)
        self.assertNotRegex(self.faq, r"旧设备[^。\n]*(已全部|完全兼容)")

    def test_language_and_color_modes(self):
        for token in ("简体中文", "繁体中文", "英文", "日文", "西班牙文", "经典撒色", "跟随主题", "自定义"):
            self.assertIn(token, self.faq)
        for token in ("Simplified Chinese", "Traditional Chinese", "Japanese", "Spanish", "classic", "theme-following", "custom seed"):
            self.assertIn(token, self.llms)
        self.assertNotIn("Course colors use a stable golden-angle", self.llms)

    def test_console_is_opt_in_authenticated_and_write_capable(self):
        for text in (self.faq, self.llms):
            self.assertIn("127.0.0.1:3737", text)
            self.assertIn("X-Sleepy-Token", text)
        for token in ("主动启用", "修改", "删除", "公网"):
            self.assertIn(token, self.faq)
        for token in ("opt-in", "create", "delete", "Internet"):
            self.assertIn(token, self.llms)

    def test_widget_and_reminder_boundaries(self):
        for text in (self.faq, self.llms):
            for family in ("Today", "TwoDay", "WeekList", "WeekView", "WeekGrid"):
                self.assertIn(family, text)
            self.assertIn("13", text)
            self.assertIn("OriginOS", text)
        self.assertIn("所有课表合并", self.faq)
        self.assertIn("all timetables", self.llms)
        self.assertNotIn("仅当前课表", self.faq)

    def test_accounts_and_ios_are_not_overclaimed(self):
        self.assertIn("Sleepy 账号", self.faq)
        self.assertIn("学校账号", self.faq)
        self.assertIn("school account", self.llms)
        self.assertNotIn("与 Android 版功能对齐", self.faq)
        self.assertNotIn("feature-aligned", self.llms)
        self.assertNotIn("aligned feature-wise", self.llms)

    def test_no_private_contributor_or_secret_instructions(self):
        for text in (self.faq, self.llms):
            for private in ("lingion@hrbeu.edu.cn", "Co-Authored-By", "Claude", "项目 SOP", "默认口令", "default password"):
                self.assertNotIn(private, text)
        self.assertIn("CONTRIBUTING.md", self.faq)

    def test_original_faq_coverage_is_retained(self):
        # Questions are a public navigation contract; answers may be rewritten.
        for question in ("如何支持这个项目？", "提供哪些 APK ABI？", "可以导出到小爱课程表吗？", "可以直接写入系统日历吗？", "如何完整备份或换机？", "同一时间有两门课会丢课吗？", "长课间怎么显示？", "为什么 OPPO / 一加 / ColorOS 桌面上的 widget 可能空白？", "选中主题卡片会改变卡片尺寸吗？", "关闭提醒后还会收到吗？", "如何跑测试？"):
            self.assertIn("### " + question, self.faq)
        self.assertGreaterEqual(len(re.findall(r"^### ", self.faq, re.M)), 44)


if __name__ == "__main__":
    unittest.main()
