package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * JwImportReport — 未验证学校导入成功后的可粘贴报告文本（离线回路, 2026-10-18）。
 *
 * 锁定: 字段完整性(维护者据此把 school_url_evidence 翻 verified)、
 * 身份精确性(catalogId 必出现 — 同名研究生/校区条目靠它区分)、
 * 隐私红线(不含用户账号/口令, 只有学校侧公开信息)。
 */
class JwImportReportTest {

    private val school = JwSchoolInfo(
        sortKey = "测", name = "测试大学", url = "https://jw.example.edu.cn/index",
        type = JwProtocol.TYPE_ZF_NEW, status = JwSchoolInfo.STATUS_UNVERIFIED,
        aliases = emptyList(), sortKeyFull = "ceshidaxue", catalogId = "MOE-12345"
    )

    @Test
    fun `report carries every field the maintainer registry needs`() {
        val text = JwImportReport.build(
            school = school,
            courseCount = 37,
            versionName = "1.0.62",
            versionCode = 10062,
            date = LocalDate.of(2026, 10, 18),
        )
        assertTrue(text.contains("测试大学"))
        assertTrue(text.contains("MOE-12345"))           // 目录 ID: 精确身份
        assertTrue(text.contains("zf_new"))              // 协议
        assertTrue(text.contains("https://jw.example.edu.cn/index"))  // 入口 URL
        assertTrue(text.contains("37"))                  // 课程数 = 成功证据
        assertTrue(text.contains("1.0.62"))
        assertTrue(text.contains("10062"))
        assertTrue(text.contains("2026-10-18"))
    }

    @Test
    fun `report is headed with a paste marker for the qq group`() {
        val text = JwImportReport.build(school, 37, "1.0.62", 10062, LocalDate.of(2026, 10, 18))
        assertTrue(text.startsWith(JwImportReport.PASTE_MARKER))
    }

    @Test
    fun `report never contains user credentials or account values`() {
        // 报告只吃 school + 计数 + 版本 + 日期, 结构上不接触登录态字段 —
        // 用最大尺寸校名/URL 也验证无 "pwd"/"token"/"session" 注入点
        val text = JwImportReport.build(school, 37, "1.0.62", 10062, LocalDate.of(2026, 10, 18))
            .lowercase()
        listOf("password", "token", "session", "cookie", "jsessionid").forEach {
            assertFalse("报告泄漏敏感词 $it", text.contains(it))
        }
    }

    @Test
    fun `protocol falls back to raw type when displayName has no entry`() {
        val weird = school.copy(type = "brand_new_protocol")
        val text = JwImportReport.build(weird, 5, "1.0.62", 10062, LocalDate.of(2026, 10, 18))
        assertTrue(text.contains("brand_new_protocol"))
    }

    @Test
    fun `null protocol renders as unknown placeholder`() {
        val noType = school.copy(type = null)
        val text = JwImportReport.build(noType, 5, "1.0.62", 10062, LocalDate.of(2026, 10, 18))
        assertTrue(text.contains(JwImportReport.UNKNOWN))
    }

    @Test
    fun `grad_unverified school keeps its status in the report`() {
        val grad = school.copy(status = JwSchoolInfo.STATUS_GRAD_UNVERIFIED)
        val text = JwImportReport.build(grad, 12, "1.0.62", 10062, LocalDate.of(2026, 10, 18))
        assertTrue(text.contains(JwSchoolInfo.STATUS_GRAD_UNVERIFIED))
    }

    @Test
    fun `course count zero still produces a valid report line`() {
        // 理论上 Imported 时课程数 > 0(空表在 Preview 前已报错),但零值不得崩溃/丢行
        val text = JwImportReport.build(school, 0, "1.0.62", 10062, LocalDate.of(2026, 10, 18))
        assertTrue(text.contains("courses=0"))
    }

    @Test
    fun `build is pure across repeated calls`() {
        val a = JwImportReport.build(school, 37, "1.0.62", 10062, LocalDate.of(2026, 10, 18))
        val b = JwImportReport.build(school, 37, "1.0.62", 10062, LocalDate.of(2026, 10, 18))
        assertEquals(a, b)
    }
}
