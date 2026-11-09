package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FourSchoolParserTest {
    private fun resource(path: String) = javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText()

    @Test fun `NWUPL uses classic EAMS unit count ten`() {
        val course = JwClassicEamsParser(resource("jw/fixtures/classic_eams/nwupl-task-activity.html")).generateCourseList().first()
        assertEquals(3, course.day)
        assertEquals(1, course.startNode)
        assertEquals("思想道德与法治(tb1130012.04)", course.name)
    }

    @Test fun `LIXIN fragment missing calendar and period matrix cannot invent weeks or nodes`() {
        // The preserved fragment has table0 = {}. It cannot establish the semester origin
        // of its decimal bitmap or the school's period indices. Full shapes are covered
        // separately in JwEcuplOccurrenceParityTest.
        assertEquals(emptyList<JwCourse>(), JwClassicEamsParser(
            resource("jw/fixtures/classic_eams/lixin-new-activity.html")
        ).generateCourseList())
    }

    @Test fun `NUIT expands every week from class date strings`() {
        val courses = JwNuitParser(resource("jw/fixtures/wisedu/nuit-courses.json")).generateCourseList()
        assertEquals(26, courses.size)
        assertTrue(courses.all { it.name == "程序设计基础" && it.teacher == "穆天朔" })
    }

    @Test fun `KMUST grid deduplicates rowspan cells and merges sections`() {
        val courses = JwKustParser(resource("jw/fixtures/kmust/queryAWeekSchedule.json")).generateCourseList()
        assertEquals(2, courses.size)
        assertEquals(1, courses.first { it.day == 1 }.startNode)
        assertEquals(2, courses.first { it.day == 1 }.endNode)
    }
}
