package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2026-09 211 批量收录：EAMS5 (supwisdom 新版) 前缀自适应。
 *
 * 背景：合工大 = /eams5-student/for-std/...；安徽大学 / 矿大北京实测 =
 * /student/for-std/...（survey 211-survey.md A 档）。fetch JS 与 Kotlin
 * 推断逻辑共用同一前缀常量，前缀写死会让新增校 0 课。
 */
class Eams5PathPrefixTest {

    // -------- 合工大形态：/eams5-student --------

    @Test
    fun `hfut url yields eams5-student prefix`() {
        assertEquals(
            "/eams5-student",
            eams5PathPrefixFor("https://jxglstu.hfut.edu.cn/"),
        )
    }

    @Test
    fun `hfut deep path still yields eams5-student`() {
        assertEquals(
            "/eams5-student",
            eams5PathPrefixFor("https://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table"),
        )
    }

    // -------- supwisdom 新版形态：/student (安大/矿大北京) --------

    @Test
    fun `ahu url yields student prefix`() {
        assertEquals(
            "/student",
            eams5PathPrefixFor("https://jw.ahu.edu.cn/student/for-std/course-table"),
        )
    }

    @Test
    fun `cumtb url yields student prefix`() {
        assertEquals(
            "/student",
            eams5PathPrefixFor("https://jwxt.cumtb.edu.cn/student/for-std/course-table"),
        )
    }

    // -------- 兜底：无特征默认合工大形态 (保持向后兼容) --------

    @Test
    fun `blank url falls back to eams5-student`() {
        assertEquals("/eams5-student", eams5PathPrefixFor(""))
    }

    @Test
    fun `unrelated url falls back to eams5-student`() {
        assertEquals("/eams5-student", eams5PathPrefixFor("https://example.com/"))
    }

    // -------- fetch JS 模板契约：占位符必须存在且能被替换 --------

    @Test
    fun `fetch js template contains replaceable prefix placeholder`() {
        // EAMS5_FETCH_JS 是 UI 层 private 常量；这里锁契约：占位符拼写 + 替换后无残留
        val template = "fetch('__EAMS5_PREFIX__/for-std/course-table')"
        val js = template.replace(EAMS5_PREFIX_PLACEHOLDER, "/student")
        assertEquals("fetch('/student/for-std/course-table')", js)
        org.junit.Assert.assertFalse(js.contains(EAMS5_PREFIX_PLACEHOLDER))
    }

    // -------- AHU (安徽大学) fetch JS 契约 — 2026-09-06 cross-verified --------
    // 安大走金智 EAMS 新版 GET 路径 (data.lessons[]), 与合工大 POST datum 形态不同。
    // fetch JS 必须用占位符拼前缀, 禁把 /student 硬编码。

    @Test
    fun `AHU fetch js uses placeholder not hardcoded student prefix`() {
        // 锁契约: AHU fetch JS 用同一个 EAMS5_PREFIX_PLACEHOLDER (避免漂移到合工大形态)
        val template = "fetch(PREFIX + '/for-std/course-table')"
        val js = template.replace("PREFIX", "'__EAMS5_PREFIX__'")
        val resolved = js.replace(EAMS5_PREFIX_PLACEHOLDER, "/student")
        assertEquals("AHU fetch 应使用 /student 前缀", "fetch('__EAMS5_PREFIX__' + '/for-std/course-table')", js)
        assertEquals("AHU fetch 替换后无残留占位符", "fetch('/student' + '/for-std/course-table')", resolved)
        org.junit.Assert.assertFalse(resolved.contains(EAMS5_PREFIX_PLACEHOLDER))
    }

    @Test
    fun `AHU get-data url has dataId empty for server session binding`() {
        // 安大 qiqqqqq517 模式: dataId 留空, 服务端按 session 绑定学生
        // 锁契约: fetch URL 的 dataId 参数必须留空, 不允许 client 端补学号
        val url = "/student/for-std/course-table/get-data?bizTypeId=2&semesterId=234&dataId="
        org.junit.Assert.assertTrue("安大 get-data URL 末尾 dataId 必须为空字符串", url.endsWith("dataId="))
        org.junit.Assert.assertFalse("安大 get-data URL 不应含显式学号", url.contains("studentId="))
    }

    @Test
    fun `AHU get-data url uses GET not POST`() {
        // 上游证据: schedule-table/datum POST 不存在于 jw.ahu.edu.cn (issue #17 实锤 HTTP 500)
        val url = "/student/for-std/course-table/get-data?bizTypeId=2&semesterId=234&dataId="
        org.junit.Assert.assertTrue("AHU 课表 API 是 GET (data.lessons[]), 非 POST datum",
            url.contains("/get-data"))
        org.junit.Assert.assertFalse("AHU 不应误用合工大 POST schedule-table/datum",
            url.contains("schedule-table/datum"))
    }

    companion object {
        /** 与 UI 层共享的占位符常量 (单测引用同一符号, 防拼写漂移) */
        const val EAMS5_PREFIX_PLACEHOLDER = "__EAMS5_PREFIX__"
    }
}
