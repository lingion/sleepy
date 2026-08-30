package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T8 — JwNewZfParser 的 marker 互斥白名单。
 */
class JwNewZfParserMarkerTest {

    private fun loadFixture(path: String): String =
        File("src/test/resources/jw_fixtures/$path").readText(Charsets.UTF_8)

    @Test
    fun `CF page with var kbxx is not parsed by JwNewZfParser`() {
        val html = loadFixture("cf-chengfang/typical_two_courses.html")
        val courses = JwNewZfParser(html).generateCourseList()
        assertEquals("CF 页面不应被 JwNewZfParser 解析", 0, courses.size)
    }

    @Test
    fun `CF multi-segment weeks not parsed by JwNewZfParser`() {
        val html = loadFixture("cf-chengfang/multi_segment_weeks.html")
        val courses = JwNewZfParser(html).generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `ZF_NEW kblist with kcmc plus xqj is parsed normally`() {
        val html = loadFixture("detection-pages/zf-new-xskbcx-kblist.html")
        val courses = JwNewZfParser(html).generateCourseList()
        assertTrue("至少 2 门课", courses.size >= 2)
    }

    @Test
    fun `ZF_NEW kblist missing fields yields empty not crash`() {
        val html = loadFixture("detection-pages/zf-new-xskbcx-missing-fields.html")
        val courses = JwNewZfParser(html).generateCourseList()
        assertTrue(courses.isEmpty() || courses.all { it.name.isNotBlank() })
    }

    @Test
    fun `JwNewZfParser confidence for CF page is 0 or low`() {
        val cfHtml = loadFixture("cf-chengfang/typical_two_courses.html")
        val conf = JwNewZfParser(cfHtml).confidence()
        assertTrue("CF 页面 JwNewZfParser confidence 应 < 80, 实际 $conf", conf < 80)
    }

    @Test
    fun `JwNewZfParser confidence for ZF_NEW kblist is high`() {
        val html = loadFixture("detection-pages/zf-new-xskbcx-kblist.html")
        val conf = JwNewZfParser(html).confidence()
        assertTrue("ZF_NEW kblist confidence 应 >= 80, 实际 $conf", conf >= 80)
    }
}
