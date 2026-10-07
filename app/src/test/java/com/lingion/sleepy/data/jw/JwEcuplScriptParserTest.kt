// ECUPL script sub-protocol parser test — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ECUPL script 子协议 (R5/R6/R7) 解析器测试。
 *
 * 覆盖 3 个 regex_invariants + isolation tests。
 */
class JwEcuplScriptParserTest {

    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("jw/$name")!!
            .bufferedReader().use { it.readText() }

    private fun scriptSample() = res("ecupl_script_sample.html")
    private fun superSample() = res("ecupl_super_sample.html")

    // ---- A. R5: CourseTable 锚点 ----

    @Test
    fun `R5 new CourseTable matches across newlines`() {
        val match = JwEcuplScriptParser.RE_COURSE_TABLE.find(scriptSample())
        assertNotNull(match)
        // 起始日: 2024-02-26
        assertEquals("2024-02-26", match!!.groupValues[1])
    }

    // ---- B. R6: 时间矩阵 ≥ 1 对 ----

    @Test
    fun `R6 time pair regex finds at least 12 pairs`() {
        val pairs = JwEcuplScriptParser.RE_TIME_PAIR.findAll(scriptSample()).toList()
        assertTrue("≥ 12 时间对, 实际 ${pairs.size}", pairs.size >= 12)
        assertEquals("480", pairs[0].groupValues[1])
        assertEquals("525", pairs[0].groupValues[2])
    }

    // ---- C. R7: newActivity + addActivityByTime 配对 emit 3 课程 ----

    @Test
    fun `R7 newActivity yields 3 courses via script path`() {
        val courses = JwEcuplScriptParser(scriptSample()).generateCourseList()
        assertEquals(3, courses.size)
        // 第 1 课程: 民法(总则), 张教授
        val civilLaw = courses[0]
        assertEquals("民法(总则)", civilLaw.name)
        assertEquals("张教授", civilLaw.teacher)
        assertEquals("明法楼A101", civilLaw.room)
    }

    // ---- D. End-to-end: super fixture 同时含 HTML + script, script 路径仍 emit 课程 ----

    @Test
    fun `super fixture yields 3 courses via R7 path`() {
        val courses = JwEcuplScriptParser(superSample()).generateCourseList()
        assertEquals(3, courses.size)
        val names = courses.map { it.name }.toSet()
        assertTrue(names.contains("民法(总则)"))
        assertTrue(names.contains("刑法"))
        assertTrue(names.contains("法理学"))
    }

    // ---- E. confidence ----

    @Test
    fun `scriptSample yields high confidence`() {
        val parser = JwEcuplScriptParser(scriptSample())
        val conf = parser.confidence()
        assertTrue("conf ≥ 80, 实际 $conf", conf >= 80)
    }

    // ---- F. Isolation - empty HTML ----

    @Test
    fun `empty HTML yields zero courses and zero confidence`() {
        val parser = JwEcuplScriptParser("<html><body></body></html>")
        val courses = parser.generateCourseList()
        assertEquals(0, courses.size)
        assertEquals(0, parser.confidence())
    }

    // ---- G. Non-ECUPL script ----

    @Test
    fun `non-ecupl script yields empty list`() {
        val html = "<html><body><script>alert('hello');</script></body></html>"
        val courses = JwEcuplScriptParser(html).generateCourseList()
        assertEquals(0, courses.size)
        assertEquals(0, JwEcuplScriptParser(html).confidence())
    }
}