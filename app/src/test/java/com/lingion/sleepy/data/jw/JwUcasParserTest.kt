package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwUcasParserTest {
    private fun fixture() = javaClass.classLoader!!.getResourceAsStream("jw/fixtures/ucas/person-schedule.sample.html")!!
        .bufferedReader().use { it.readText() }

    @Test fun `parses UCAS schedule grid and merges adjacent nodes`() {
        val courses = JwUcasParser(fixture()).generateCourseList()
        assertEquals(6, courses.size)
        val theory = courses.single { it.name == "新时代中国特色社会主义理论与实践" }
        assertEquals(1, theory.day)
        assertEquals(1, theory.startNode)
        assertEquals(2, theory.endNode)
        assertEquals(1, theory.startWeek)
        assertEquals(16, theory.endWeek)
        val architecture = courses.single { it.name == "计算机体系结构" && it.day == 1 }
        assertEquals(10, architecture.startNode)
        assertEquals(11, architecture.endNode)
    }

    @Test fun `UCAS parser identifies captured page shape`() {
        val parser = JwUcasParser(fixture())
        assertTrue(parser.confidence() >= 90)
        assertTrue(parser.matchedFeatures().contains("href:/course/coursetime/"))
    }
}
