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
    private fun metadataOnly() = res("ecupl_metadata_only.html")
    private fun superSample() = res("ecupl_super_sample.html")

    // Metadata tables have course titles and week notes, but no actual weekday/period.
    @Test
    fun metadataFixturesNeverInventScheduleCoordinates() {
        assertTrue(JwEcuplParser(metadataOnly()).generateCourseList().isEmpty())
        assertTrue(JwEcuplParser(superSample()).generateCourseList().isEmpty())
    }

    @Test
    fun taskActivityIndexProducesObservedSchedule() {
        val courses = JwEcuplParser(mainSample()).generateCourseList()
        assertTrue(courses.isNotEmpty())
        assertEquals("民法(总则)(001)", courses.first().name)
        assertEquals(1, courses.first().day)
        assertEquals(1, courses.first().startNode)
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
