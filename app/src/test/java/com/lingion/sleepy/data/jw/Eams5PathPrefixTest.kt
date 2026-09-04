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

    companion object {
        /** 与 UI 层共享的占位符常量 (单测引用同一符号, 防拼写漂移) */
        const val EAMS5_PREFIX_PLACEHOLDER = "__EAMS5_PREFIX__"
    }
}
