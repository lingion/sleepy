package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

/** Synthetic source-layout cases. No assertion here represents a live authenticated import. */
class JwGzhuyjsOccurrenceTest {
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { row ->
        (row.startWeek..row.endWeek).filter { row.type == 0 || it % 2 == row.type % 2 }.flatMap { week ->
            (row.startNode..row.endNode).map { node -> "${row.name}|${row.teacher}|${row.room}|${row.day}|$node|$week" }
        }
    }.sorted()

    private fun block(name: String, time: String, teacher: String = "王", room: String = "研教楼") =
        "<div>$name<br>课程代码<br>培养环节<br>$teacher<br>$time<br>$room</div>"
    private fun row(cells: String) = "<tr>$cells</tr>"
    private fun page(vararg rows: String) = "<table class='tab_0'><tbody>" +
        (1..4).joinToString("") { row("<td colspan='8'>表头$it</td>") } + rows.joinToString("") + "</tbody></table>"

    @Test fun `three source rows retain individual teachers rooms periods and week holes`() {
        val source = page(
            row("<td>上午</td><td>${block("方法论", "1-2周 1到2小节")}${block("方法论", "5-5周 3到4小节", "李", "B")}</td>" + "<td></td>".repeat(5) + "<td>${block("周日课", "8-9周 2到3小节")}</td>"),
            row("<td>下午</td><td></td><td>${block("实验", "3-4周 7到8小节", "赵", "C")}</td>"),
            row("<td>晚上</td><td></td><td></td><td>${block("专题", "6-6周 11到12小节", "孙", "D")}</td>"),
            row("<td>说明</td><td>${block("不能读第八行", "1-20周 1到2小节")}</td>"),
        )
        val expected = listOf(1,2).flatMap { w -> listOf(1,2).map { "方法论|王|研教楼|1|$it|$w" } } +
            listOf(3,4).map { "方法论|李|B|1|$it|5" } +
            listOf(8,9).flatMap { w -> listOf(2,3).map { "周日课|王|研教楼|7|$it|$w" } } +
            listOf(3,4).flatMap { w -> listOf(7,8).map { "实验|赵|C|2|$it|$w" } } +
            listOf(11,12).map { "专题|孙|D|3|$it|6" }
        assertEquals(expected.sorted(), occurrences(JwGzhuyjsParser(source).generateCourseList()))
    }

    @Test fun `nested wrappers html entities and empty fields retain one course per block`() {
        val source = page(row("<td>上午</td><td><div>${block("<b>R&amp;D</b>", "2-3周 1到2小节", "", "")}</div></td>"))
        assertEquals(listOf(2,3).flatMap { w -> listOf(1,2).map { "R&D|||1|$it|$w" } }.sorted(),
            occurrences(JwGzhuyjsParser(source).generateCourseList()))
    }

    @Test fun `blank colspans and label rowspans preserve weekday columns`() {
        val source = page(
            row("<td rowspan='2'>白天</td><td colspan='2'></td><td>${block("周三课", "2-2周 3到4小节")}</td>"),
            row("<td>${block("周一课", "3-3周 7到8小节")}</td>"),
        )
        assertEquals((listOf(3,4).map { "周三课|王|研教楼|3|$it|2" } + listOf(7,8).map { "周一课|王|研教楼|1|$it|3" }).sorted(),
            occurrences(JwGzhuyjsParser(source).generateCourseList()))
    }

    @Test fun `explicit sparse weeks and periods do not fill gaps or duplicate overlaps`() {
        val course = block("离散课", "1-3,3,7周 1,3-4小节")
        val source = page(row("<td>上午</td><td>$course$course</td>"))
        assertEquals(listOf(1,2,3,7).flatMap { w -> listOf(1,3,4).map { "离散课|王|研教楼|1|$it|$w" } }.sorted(),
            occurrences(JwGzhuyjsParser(source).generateCourseList()))
    }

    @Test fun `malformed and missing coordinates are skipped without full-semester defaults`() {
        val malformed = listOf("全周 1到2小节", "-1-3周 1到2小节", "1.5-3周 1到2小节", "4-1周 1到2小节",
            "1-3周 0到2小节", "1-3周 4到2小节", "1-3周 -1到2小节", "1-3周 1.5到2小节", "待定", "")
        val source = page(row("<td>上午</td><td>" + malformed.joinToString("") { block("坏课", it) } +
            "<div>短块<br>不能导致整表崩溃</div>" + block("有效课", "6-6周 1到2小节") + "</td>"))
        assertEquals(listOf(1,2).map { "有效课|王|研教楼|1|$it|6" }, occurrences(JwGzhuyjsParser(source).generateCourseList()))
    }

    @Test fun `ambiguous course colspans never expand one course into several weekdays`() {
        val source = page(row("<td>上午</td><td colspan='2'>${block("跨日未知课", "1-3周 1到2小节")}</td><td>${block("周三课", "1-1周 3到4小节")}</td>"))
        assertEquals(listOf(3,4).map { "周三课|王|研教楼|3|$it|1" }, occurrences(JwGzhuyjsParser(source).generateCourseList()))
    }

    @Test fun `source row and class anchors exclude incidental course-shaped text`() {
        assertTrue(JwGzhuyjsParser("<table class='other'><tr><td>${block("误报", "1-20周 1到2小节")}</td></tr></table>").generateCourseList().isEmpty())
        assertTrue(JwGzhuyjsParser("<table class='tab_0'><tr><td>标题</td><td>${block("误报", "1-20周 1到2小节")}</td></tr></table>").generateCourseList().isEmpty())
    }
}
