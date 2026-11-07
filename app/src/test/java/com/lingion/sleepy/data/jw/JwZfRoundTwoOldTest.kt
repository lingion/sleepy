package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwZfRoundTwoOldTest {
    private fun page(content: String, id: String = "Table1", span: Int = 1): String =
        "<table id='$id'><tr><td>第1节</td><td rowspan='$span'>" + content + "</td></tr></table>"

    private fun course(endNode: Int = 2, endWeek: Int = 4) =
        JwCourse("离散数学", "理科楼305", "陈老师", 1, 1, endNode, 1, endWeek)

    @Test fun `old grid retains rowspan when time does not specify periods`() {
        val html = page("<a href='#'>离散数学<br>{第1-4周}<br>陈老师<br>理科楼305</a>", span = 3)
        assertEquals(listOf(course(endNode = 3)), JwOldZfParser(html).generateCourseList())
    }

    @Test fun `old zf one letter property does not become the course name`() {
        val html = page("离散数学 A {第1-4周}(1-2节) 陈老师 理科楼305")
        assertEquals(listOf(course()), JwOldZfParser(html, 1).generateCourseList())
    }

    @Test fun `lowercase table and kbgrid selectors do not require weekday text`() {
        for (id in listOf("table1", "kbgrid_table")) {
            val html = page("<a href='#'>离散数学<br>{第1-4周}(1-2节)<br>陈老师<br>理科楼305</a>", id = id)
            assertEquals(id, listOf(course()), JwOldZfParser(html).generateCourseList())
        }
    }

    @Test fun `braced singleton creates its actual single teaching week`() {
        val html = page("<a href='#'>离散数学<br>{第1周}(1-2节)<br>陈老师<br>理科楼305</a>")
        assertEquals(listOf(course(endWeek = 1)), JwOldZfParser(html).generateCourseList())
    }
}
