package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwNewZfExplicitPeriodsTest {
    @Test fun festivalViewKeepsExplicitPeriodsBeyondDefaultClockLength() {
        for ((first, last) in listOf(17 to 18, 31 to 32, 366 to 366)) {
            assertEquals(listOf(course(first, last)), festival("$first-$last"))
        }
    }

    @Test fun jsonUsesTheSameExplicitPeriodBoundary() {
        for ((first, last) in listOf(17 to 18, 31 to 32, 366 to 366)) {
            assertEquals(listOf(course(first, last)), json("$first-$last"))
        }
        assertEquals(listOf(course(1, 12), course(17, 18)), json("01121718"))
    }

    @Test fun invalidExplicitRangesCannotProduceCourses() {
        for (nodes in listOf("0-1", "2-1", "0201", "366-367", "1-2147483648")) {
            assertEquals(nodes, emptyList<JwCourse>(), festival(nodes))
            assertEquals(nodes, emptyList<JwCourse>(), json(nodes))
        }
    }

    private fun course(first: Int, last: Int) = JwCourse("数学", "A", "王老师", 1, first, last, 1, 4)
    private fun json(nodes: String) = JwNewZfParser(
        """{"kbList":[{"kcmc":"数学","xqj":1,"jc":"$nodes","zcd":"1-4周","cdmc":"A","xm":"王老师"}]}""")
        .generateCourseList()
    private fun festival(nodes: String) = JwNewZfParser(
        "<table id='table1'><tr><td id='1-1' class='festival'><div class='timetable_con'>" +
            "<div class='title'>数学</div><p title='教师'>王老师</p><p title='上课地点'>A</p>" +
            "<p title='节/周'>($nodes" + "节)1-4周</p></div></td></tr></table>").generateCourseList()
}
