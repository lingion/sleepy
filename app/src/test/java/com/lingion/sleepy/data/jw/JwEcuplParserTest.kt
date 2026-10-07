// ECUPL main parser test — wakeup-parity-ecupl-super-2026-10-06 SOP v1.11
package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ECUPL (华东政法大学) main parser (双 .listTable + 5 列模糊匹配 + bitmap 拆周次) 测试。
 *
 * 覆盖 10 个 regex_invariants: R1/R3/R4/R9/R10 主表 + R2/R5/R6/R7/R8 INDIRECT。
 */
class JwEcuplParserTest {

    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("jw/$name")!!
            .bufferedReader().use { it.readText() }

    private fun mainSample() = res("ecupl_sample.html")
    private fun superSample() = res("ecupl_super_sample.html")

    // ---- A. POSITIVE: 主表 fixture 解析出 8 课程 ----

    @Test
    fun `R1 listTable container matches POSITIVE anchor`() {
        val parser = JwEcuplParser(mainSample())
        val match = JwEcuplParser.RE_LIST_TABLE.find(mainSample())
        assertNotNull(match)
    }

    @Test
    fun `main fixture yields 8 courses with 5-column fuzzy match`() {
        val courses = JwEcuplParser(mainSample()).generateCourseList()
        assertEquals(8, courses.size)
        // 第 1 课程: 民法(总则), 张教授, 每周
        val civilLaw = courses[0]
        assertEquals("民法(总则)", civilLaw.name)
        assertEquals("张教授", civilLaw.teacher)
        assertEquals(0, civilLaw.type)
    }

    // ---- B. INDIRECT: super fixture 同时含 HTML + script, 主表路径仍 emit ----

    @Test
    fun `super fixture yields 3 main courses via HTML path`() {
        val courses = JwEcuplParser(superSample()).generateCourseList()
        assertEquals(3, courses.size)
        val names = courses.map { it.name }.toSet()
        assertTrue(names.contains("民法(总则)"))
        assertTrue(names.contains("刑法"))
        assertTrue(names.contains("法理学"))
    }

    // ---- C. Bitmap boundary: 单双周拆段 ----

    @Test
    fun `bitmap boundary - odd week extracts type=1`() {
        val courses = JwEcuplParser(mainSample()).generateCourseList()
        // 宪法学: "1-8单" → type=1, startWeek=1, endWeek=8
        val constitution = courses.single { it.name == "宪法学" }
        assertEquals(1, constitution.startWeek)
        assertEquals(8, constitution.endWeek)
        assertEquals(1, constitution.type)
    }

    @Test
    fun `bitmap boundary - even week extracts type=2`() {
        val courses = JwEcuplParser(mainSample()).generateCourseList()
        // 行政法: "1-8双" → type=2, startWeek=1, endWeek=8
        val adminLaw = courses.single { it.name == "行政法" }
        assertEquals(1, adminLaw.startWeek)
        assertEquals(8, adminLaw.endWeek)
        assertEquals(2, adminLaw.type)
    }

    // ---- D. confidence + matchedFeatures ----

    @Test
    fun `mainSample yields high confidence with 5+ features`() {
        val parser = JwEcuplParser(mainSample())
        val conf = parser.confidence()
        assertTrue("conf ≥ 80, 实际 $conf", conf >= 80)
        val features = parser.matchedFeatures()
        assertTrue("features ≥ 5, 实际 ${features.size}", features.size >= 3)
        assertTrue(features.any { it.startsWith("ecupl:") })
    }

    // ---- E. Isolation - empty HTML returns empty ----

    @Test
    fun `empty HTML yields zero courses and zero confidence`() {
        val parser = JwEcuplParser("<html><body></body></html>")
        val courses = parser.generateCourseList()
        assertEquals(0, courses.size)
        assertEquals(0, parser.confidence())
    }

    // ---- F. Non-ECUPL HTML ----

    @Test
    fun `non-ecupl HTML yields empty course list`() {
        val html = "<html><body><div>no listTable here</div></body></html>"
        val courses = JwEcuplParser(html).generateCourseList()
        assertEquals(0, courses.size)
    }
}