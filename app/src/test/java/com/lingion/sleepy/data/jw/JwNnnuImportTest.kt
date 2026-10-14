package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwNnnuImportTest {
    private fun fixture(): String = javaClass.getResource("/jw/qz/nnnu_timetable.html")!!.readText()

    @Test fun `complete captured timetable preserves every course week and period`() {
        val courses = JwParserRegistry.parserFor(JwProtocol.TYPE_QZ, fixture()).generateCourseList()
        val expected = setOf(
            "课程1|1|1|2|10|16",
            "课程1|4|3|4|2|9",
            "课程10|4|6|7|12|17",
            "课程10|4|6|7|2|11",
            "课程11|5|6|7|12|13",
            "课程11|5|6|7|5|11",
            "课程12|3|8|9|2|3",
            "课程13|4|8|9|2|16",
            "课程14|5|8|9|2|16",
            "课程15|1|10|11|13|13",
            "课程15|2|10|11|13|13",
            "课程2|1|1|4|17|17",
            "课程2|1|6|9|17|17",
            "课程3|2|1|2|17|17",
            "课程3|2|1|2|2|5",
            "课程3|2|1|2|7|16",
            "课程4|3|1|2|3|17",
            "课程4|6|1|2|17|17",
            "课程5|3|3|4|10|16",
            "课程5|4|1|2|2|9",
            "课程6|4|1|4|10|17",
            "课程7|2|3|5|2|17",
            "课程7|5|1|2|2|17",
            "课程8|1|3|3|16|16",
            "课程8|1|3|5|3|8",
            "课程8|1|3|5|11|15",
            "课程8|3|6|8|7|7",
            "课程8|4|10|12|11|11",
            "课程9|1|6|7|3|3",
            "课程9|1|6|7|6|7",
            "课程9|2|6|7|8|8",
            "课程9|3|6|7|2|4",
            "课程9|3|6|7|6|6",
        )
        val actual = courses.map { "${it.name}|${it.day}|${it.startNode}|${it.endNode}|${it.startWeek}|${it.endWeek}" }
        assertEquals(expected, actual.toSet())
        assertEquals(33, courses.size)
        assertEquals(15, courses.map { it.name }.distinct().size)
        assertTrue(courses.all { it.teacher.startsWith("教师") && it.room.startsWith("教室") })
        assertTrue(courses.all { it.type == 0 })
    }

    @Test fun `automatic detection agrees with declared qz and exposes timetable evidence`() {
        val html = fixture()
        val parser = JwQzParser(html)
        val expected = parser.generateCourseList()
        assertTrue(parser.confidence() >= 80)
        assertTrue(parser.matchedFeatures().contains("id=timetable"))
        assertEquals(expected.toSet(), JwParserRegistry.selectBest(html).first.toSet())
    }
}
