package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic DOMs derived from the local 6.6.2 parser; these are not account captures. */
class JwZptcOccurrenceTest {
    private fun course(id: String, name: String, parity: String, nodes: String, weeks: String,
        teacher: String = "张老师", room: String = "A101") =
        "<div class='pbtd' id='$id'>$name/$parity/第${nodes}节[教学班][$weeks]【$teacher】【$room】</div>"

    private fun table(body: String, headers: List<String> = listOf("时间", "周日", "周二", "周一")) =
        "<table id='kb'><tr>" + headers.joinToString("") { "<th>$it</th>" } +
            "</tr><tr><td>说明列</td><td>$body</td></tr></table>"

    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { row ->
        (row.startWeek..row.endWeek).filter { row.type == 0 || it % 2 == row.type % 2 }.flatMap { week ->
            (row.startNode..row.endNode).map { node ->
                "${row.name}|${row.teacher}|${row.room}|${row.day}|$node|$week"
            }
        }
    }.sorted()

    @Test fun `header day and two period span preserve each course and reset weekly parity`() {
        val html = table(course("x1_1", "奇数课", "单周", "3-4", "1-7") +
            course("x2_1", "每周课", "每周", "5-6", "2-4", "李老师", "B202") +
            course("x3_1", "偶数课", "双周", "9-10", "1-6", "王老师", "C303"))
        val expected = listOf(1,3,5,7).flatMap { w -> listOf(3,4).map { "奇数课|张老师|A101|7|$it|$w" } } +
            listOf(2,3,4).flatMap { w -> listOf(5,6).map { "每周课|李老师|B202|2|$it|$w" } } +
            listOf(2,4,6).flatMap { w -> listOf(9,10).map { "偶数课|王老师|C303|1|$it|$w" } }
        assertEquals(expected.sorted(), occurrences(JwZptcParser(html).generateCourseList()))
    }

    @Test fun `explicit sparse weeks and overlapping duplicate records do not fill holes`() {
        val a = course("x1_1", "网络&amp;协议", "每周", "1-2", "1,3,7-8", "张&amp;李", "A&amp;B")
        val html = table(a + a + course("x1_2", "网络&amp;协议", "每周", "1-2", "7-9", "张&amp;李", "A&amp;B"))
        val expected = listOf(1,3,7,8,9).flatMap { w -> listOf(1,2).map { "网络&协议|张&李|A&B|7|$it|$w" } }
        assertEquals(expected.sorted(), occurrences(JwZptcParser(html).generateCourseList()))
    }

    @Test fun `invalid id label header node week or parity does not invent an arrangement`() {
        val invalid = course("x0_1", "标签课", "每周", "1-2", "1-4") +
            course("x8_1", "越界日", "每周", "1-2", "1-4") +
            course("x1.5_1", "小数日", "每周", "1-2", "1-4") +
            course("x-1_1", "负日", "每周", "1-2", "1-4") +
            course("x1_1", "未知单双周", "待定", "1-2", "1-4") +
            course("x1_1", "零节", "每周", "0-1", "1-4") +
            course("x1_1", "小数节", "每周", "1.5-2", "1-4") +
            course("x1_1", "倒序节", "每周", "4-3", "1-4") +
            course("x1_1", "跨度不符", "每周", "1-4", "1-4") +
            course("x1_1", "坏周", "每周", "1-2", "1-4,待定") +
            course("x1_1", "负周", "每周", "1-2", "-1-4") +
            course("x1_1", "小数周", "每周", "1-2", "1.5-4") +
            course("x1_1", "好课", "每周", "3-4", "9", "", "")
        assertEquals(listOf("好课|||7|3|9", "好课|||7|4|9"),
            occurrences(JwZptcParser(table(invalid)).generateCourseList()))
    }

    @Test fun `headers belong to their own table and unrelated pbtd is ignored`() {
        val html = course("x1_1", "表外课", "每周", "1-2", "1-2") +
            table(course("x1_1", "周日课", "每周", "1-2", "5")) +
            table(course("x1_1", "周三课", "每周", "7-8", "6"), listOf("时间", "周三"))
        assertEquals(listOf("周三课|张老师|A101|3|7|6", "周三课|张老师|A101|3|8|6",
            "周日课|张老师|A101|7|1|5", "周日课|张老师|A101|7|2|5"),
            occurrences(JwZptcParser(html).generateCourseList()))
    }

    @Test fun `teacher room metadata can precede the independent square bracket week fields`() {
        val html = table("<div class='pbtd' id='x1_1'>课程甲/每周/第5-6节【张老师】【B202】[教学班][7-8]</div>")
        assertEquals(listOf(7,8).flatMap { w -> listOf(5,6).map { "课程甲|张老师|B202|7|$it|$w" } }.sorted(),
            occurrences(JwZptcParser(html).generateCourseList()))
    }
}
