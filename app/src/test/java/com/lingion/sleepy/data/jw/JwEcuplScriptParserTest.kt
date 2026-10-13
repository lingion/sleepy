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
    fun `R7 newActivity yields 4 observed slots via script path`() {
        val courses = JwEcuplScriptParser(scriptSample()).generateCourseList()
        assertEquals(4, courses.size)
        // 第 1 课程: 民法(总则), 张教授
        val civilLaw = courses[0]
        assertEquals("民法(总则)", civilLaw.name)
        assertEquals("张教授", civilLaw.teacher)
        assertEquals("明法楼A101", civilLaw.room)
        assertEquals(1, civilLaw.day)
        assertEquals(1, civilLaw.startNode)
        assertEquals(2, civilLaw.endNode)
        assertEquals(1, civilLaw.startWeek)
        assertEquals(16, civilLaw.endWeek)
        assertTrue(courses.any { it.name == "民法(总则)" && it.day == 4 && it.startNode == 3 && it.endNode == 4 })
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

    @Test
    fun `unmatched minutes and overflowing bitmap emit no invented courses`() {
        val unmatched = scriptSample().replace("activity,1,480,580", "activity,1,481,580")
        assertEquals(3, JwEcuplScriptParser(unmatched).generateCourseList().size)
        val overflow = scriptSample().replace("65535", "18446744073709551616")
        assertEquals(0, JwEcuplScriptParser(overflow).generateCourseList().size)
    }

    @Test
    fun `sparse decimal bitmap retains gaps and date offset`() {
        val html = scriptSample().replace("\"2024-02-26\",65535", "\"2024-03-04\",21")
        val courses = JwEcuplScriptParser(html).generateCourseList().filter { it.name == "民法(总则)" && it.day == 1 }
        assertEquals(listOf(2, 4, 6), courses.flatMap { course ->
            (course.startWeek..course.endWeek step if (course.type == 0) 1 else 2).toList()
        })
    }

    @Test
    fun `separate scripts do not cross pair slots`() {
        val unrelated = "<script language=\"JavaScript\">newActivity(\"bad\",\"bad\",\"x\",\"bad\",\"x\",\"bad\",\"2024-02-26\",65535);addActivityByTime(activity,7,480,525);</script>"
        assertEquals(4, JwEcuplScriptParser(unrelated + scriptSample()).generateCourseList().size)
    }

    @Test
    fun `classic routing uses script matrix for spaced activity with empty room`() {
        val html = scriptSample()
            .replace("\"明法楼A101\"", "\"\"")
            .replace("newActivity(\"C001\"", "newActivity(  \"C001\"")
        val courses = JwImportViewModel.parseHtmlStatic(html, JwProtocol.TYPE_CLASSIC_EAMS).first
        assertEquals(4, courses.size)
        assertTrue(courses.any { it.name == "民法(总则)" && it.room.isEmpty() && it.day == 4 && it.startNode == 3 })
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
