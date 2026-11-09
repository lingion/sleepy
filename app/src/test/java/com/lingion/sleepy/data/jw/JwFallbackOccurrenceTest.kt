package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

class JwFallbackOccurrenceTest {
    @Test fun `generic JSON fallback keeps supplied sparse weeks and all location fields`() {
        val source = """[{"courseName":"课程","weekday":3,"beginSection":5,"endSection":6,
          "weeks":[2,6,10],"teacher":"教师","room":"A101"}]"""
        assertEquals(listOf(2,6,10).map { JwCourse("课程", "A101", "教师", 3,5,6,it,it) },
            JwJzParser(source).generateCourseList())
    }

    @Test fun `unknown weeks never silently become sixteen weeks`() {
        for (source in listOf(
            """[{"courseName":"课程","weekday":3,"beginSection":5,"endSection":6}]""",
            """<table><tr data-day="3" data-node="5" data-course="课程"><td>课程</td><td>周三第5节</td><td>教师</td></tr></table>""",
            "课程,3,5,A101,教师",
        )) assertTrue(source, JwJzParser(source).generateCourseList().isEmpty())
    }

    @Test fun `invalid negative or out of range weekdays cannot move a class to Monday or Sunday`() {
        for (day in listOf(-1, 0, 8)) {
            val source = """[{"courseName":"课程","weekday":$day,"beginSection":5,"weeks":[1,2]}]"""
            assertTrue(source, JwJzParser(source).generateCourseList().isEmpty())
        }
    }

    @Test fun `explicit period range and even weeks survive generic fallback`() {
        val source = """[{"name":"课程","day":5,"startNode":7,"endNode":9,"startWeek":2,"endWeek":12,"type":2}]"""
        assertEquals(listOf(JwCourse("课程", day=5, startNode=7, endNode=9, startWeek=2, endWeek=12, type=2)),
            JwJzParser(source).generateCourseList())
    }
}
