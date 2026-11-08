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
    @Test fun `rowspans reserve weekdays across following rows and expire`() {
        val html = """<table id="Table1"><tr><td>第1节</td><td rowspan="2"><a>数学<br>{第1-4周}<br>甲<br>A</a></td><td><a>物理<br>{第1-4周}<br>乙<br>B</a></td></tr>
          <tr><td>第2节</td><td><a>英语<br>{第5周}<br>丙<br>C</a></td></tr>
          <tr><td>第3节</td><td><a>历史<br>{第6周}<br>丁<br>D</a></td></tr></table>"""
        assertEquals(listOf(
            JwCourse("数学", "A", "甲", 1, 1, 2, 1, 4),
            JwCourse("物理", "B", "乙", 2, 1, 1, 1, 4),
            JwCourse("英语", "C", "丙", 2, 2, 2, 5, 5),
            JwCourse("历史", "D", "丁", 1, 3, 3, 6, 6),
        ), JwOldZfParser(html).generateCourseList())
    }
}
