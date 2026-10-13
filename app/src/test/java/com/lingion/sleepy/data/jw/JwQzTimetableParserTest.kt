package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwQzTimetableParserTest {
    private fun block(name: String = "示例课程", time: String = "2-4,6(周)[01-02-03-04节]") = """
        <div class="kbcontent" style="display:none">
          <div class="item-box" style="display:none">提示内容</div><font>$name</font><br>
          <font title="教师">示例教师</font><br>
          <font title="周次(节次)">$time</font><br>
          <font title="教室">示例教室</font>
        </div>
    """.trimIndent()

    private fun table(rows: String) = """
        <table id="timetable"><tr><th></th>
        <th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th>
        <th>星期五</th><th>星期六</th><th>星期日</th></tr>$rows</table>
    """.trimIndent()

    @Test fun `explicit sections and discrete weeks survive duplicate rows`() {
        val row = "<tr><th>第1大节</th><td>${block()}</td>" + "<td></td>".repeat(6) + "</tr>"
        val courses = JwQzParser(table(row + row)).generateCourseList()
        assertEquals(listOf(
            JwCourse("示例课程", "示例教室", "示例教师", 1, 1, 4, 2, 4),
            JwCourse("示例课程", "示例教室", "示例教师", 1, 1, 4, 6, 6),
        ), courses)
    }

    @Test fun `td row labels and weekend column map correctly`() {
        val row = "<tr><td>第2大节</td>" + "<td></td>".repeat(6) +
            "<td>${block(time = "1-15(单周)[03-04-05节]")}</td></tr>"
        val course = JwQzParser(table(row)).generateCourseList().single()
        assertEquals(7, course.day)
        assertEquals(3, course.startNode)
        assertEquals(5, course.endNode)
        assertEquals(1, course.type)
    }

    @Test fun `same cell separates courses without importing compact alternate view`() {
        val combined = block(time = "2-16(双周)[06-07节]")
        val row = "<tr><th>第3大节</th><td>" +
            "<div class='kbcontent1'><font>替代视图</font></div>" + combined +
            block("另一门课", "3,6-7(周)[08-09节]") + "</td>" + "<td></td>".repeat(6) + "</tr>"
        val courses = JwQzParser(table(row)).generateCourseList()
        assertEquals(3, courses.size)
        assertEquals(2, courses.first().type)
        assertEquals(listOf(3, 6), courses.drop(1).map { it.startWeek })
    }

    @Test fun `missing names or explicit scheduling never fabricate courses`() {
        val row = "<tr><th>备注</th><td>" + block("") + block(time = "1-19(周)") +
            block(time = "bad[01-02节]") + "</td>" + "<td></td>".repeat(6) + "</tr>"
        assertTrue(JwQzParser(table(row)).generateCourseList().isEmpty())
    }

    @Test fun `separator within one detailed block retains both courses`() {
        val inner1 = "<font>课程甲</font><font title='周次(节次)'>1-4(周)[01-02节]</font>"
        val inner2 = "<font>课程乙</font><font title='周次(节次)'>5-8(周)[03-04节]</font>"
        val row = "<tr><th>第1大节</th><td><div class='kbcontent'>$inner1<br>----------------<br>$inner2</div></td>" +
            "<td></td>".repeat(6) + "</tr>"
        assertEquals(listOf("课程甲", "课程乙"), JwQzParser(table(row)).generateCourseList().map { it.name })
    }

    @Test fun `impossible parity range cannot create a later week`() {
        val row = "<tr><th>第1大节</th><td>${block(time = "6(单周)[01-02节]")}</td>" +
            "<td></td>".repeat(6) + "</tr>"
        assertTrue(JwQzParser(table(row)).generateCourseList().isEmpty())
    }
}
