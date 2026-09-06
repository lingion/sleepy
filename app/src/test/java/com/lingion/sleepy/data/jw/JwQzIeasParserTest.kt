package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwQzIeasParserTest {
    private fun fixture(): String = javaClass.classLoader!!
        .getResourceAsStream("jw/fixtures/qz_ieas/query-grkb.sample.html")!!
        .bufferedReader().use { it.readText() }

    @Test
    fun `parses iEAS structured rows and expands weeks`() {
        val courses = JwQzIeasParser(fixture()).generateCourseList()
        assertEquals(5, courses.size)
        val math = courses.single { it.name == "高等数学" }
        assertEquals(1, math.day)
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
        assertEquals(1, math.startWeek)
        assertEquals(16, math.endWeek)
        assertEquals("张老师", math.teacher)
        assertEquals("主楼101", math.room)
    }

    @Test
    fun `preserves parity and discrete week semantics`() {
        val courses = JwQzIeasParser(fixture()).generateCourseList()
        val english = courses.single { it.name == "大学英语" }
        assertEquals(1, english.type)
        assertEquals(3, english.startWeek)
        assertEquals(15, english.endWeek)
        val programming = courses.filter { it.name == "程序设计" }
        assertEquals(listOf(2, 4, 6), programming.map { it.startWeek })
    }

    @Test
    fun `confidence and feature identify iEAS table`() {
        val parser = JwQzIeasParser(fixture())
        assertTrue(parser.confidence() >= 80)
        assertTrue(parser.matchedFeatures().contains("table#queryGrkb"))
    }
}
