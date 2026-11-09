package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test

/** Synthetic structural vectors from the local 6.6.2 parser; not captured student pages. */
class JwCdutOccurrenceTest {
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { row ->
        (row.startWeek..row.endWeek).filter { row.type == 0 || it % 2 == row.type % 2 }.flatMap { week ->
            (row.startNode..row.endNode).map { node ->
                "${row.name}|${row.teacher}|${row.room}|${row.day}|$node|$week|${row.credit}"
            }
        }
    }.sorted()

    private val legend = """
        <table class="tab2"><tr><td><b>(G1)地质学(ID77) 学分[2.5]</b><br>
        班次-A01(一班) 教师[王]<br>班次-B02(二班) 教师[李]</td></tr></table>
    """.trimIndent()

    private fun row(week: String, cells: String) = "<tr><td class='td1'>$week</td>$cells</tr>"
    private fun page(rows: String, metadata: String = legend) = metadata + "<table class='tab1'>$rows</table>"
    private fun course(code: String, span: Int = 1, room: String = "A101") =
        "<td colspan='$span'>$code<span class='roomcss'>$room</span></td>"
    private fun blank(span: Int = 1) = "<td colspan='$span'></td>"

    @Test fun `weekly rows preserve holes room changes teacher groups and all seven days`() {
        val first = course("G1A1", 2) + blank(4) + course("G1B2", 2, "B201") + blank(64) + course("G1A1")
        val second = course("G1A1", 2) + blank(4) + course("G1B2", 2, "C301")
        val result = JwCdutParser(page(row("01周", first) + row("03周", second))).generateCourseList()
        assertEquals(listOf(
            "地质学|王|A101|1|1|1|2.5", "地质学|王|A101|1|2|1|2.5",
            "地质学|王|A101|1|1|3|2.5", "地质学|王|A101|1|2|3|2.5",
            "地质学|李|B201|1|6|1|2.5", "地质学|李|B201|1|7|1|2.5",
            "地质学|李|C301|1|6|3|2.5", "地质学|李|C301|1|7|3|2.5",
            "地质学|王|A101|7|1|1|2.5",
        ).sorted(), occurrences(result))
        assertFalse(result.any { it.startWeek <= 2 && it.endWeek >= 2 && it.type != 1 })
    }

    @Test fun `empty merged cells advance coordinates and room-only nodes are not courses`() {
        val cells = blank(12) + "<td><span class='roomcss'>提示</span></td>" + course("未知课程", 2, "东楼")
        assertEquals(listOf("未知课程||东楼|2|2|12|0.0", "未知课程||东楼|2|3|12|0.0"),
            occurrences(JwCdutParser(page(row("12周", cells), "")).generateCourseList()))
    }

    @Test fun `ambiguous sixth slot and cross-midday spans do not manufacture periods`() {
        val cells = blank(4) + course("跨午间", 3) + course("后续课", 2)
        val sixth = blank(5) + course("占位课") + course("午后课")
        assertEquals(listOf("后续课||A101|1|7|4|0.0", "后续课||A101|1|8|4|0.0", "午后课||A101|1|6|5|0.0").sorted(),
            occurrences(JwCdutParser(page(row("04周", cells) + row("05周", sixth), "")).generateCourseList()))
    }

    @Test fun `invalid spans invalidate their row rather than shifting subsequent classes`() {
        val rows = row("01周", course("不可部分导入") + "<td colspan='1.5'>坏格</td>" + course("错位课")) +
            row("02周", blank(11) + course("跨天", 2) + course("星期二")) +
            row("03周", blank(84) + course("第八天")) + row("04周", course("有效课"))
        assertEquals(listOf("有效课||A101|1|1|4|0.0"), occurrences(JwCdutParser(page(rows, "")).generateCourseList()))
    }

    @Test fun `explicit weeks are required and table rowspans are not guessed`() {
        val badWeeks = listOf("", "-1周", "1.5周", "01到02周", "0周", "367周")
        val rows = badWeeks.joinToString("") { row(it, course("坏周")) } + row("第 8 周", course("有效课"))
        assertEquals(listOf("有效课||A101|1|1|8|0.0"), occurrences(JwCdutParser(page(rows, "")).generateCourseList()))
        assertTrue(JwCdutParser(page(row("01周", "<td rowspan='2'>跨周课</td>") + row("02周", course("错位课")))).generateCourseList().isEmpty())
    }

    @Test fun `conflicting code maps do not silently replace the teacher and name`() {
        val conflicting = """<table class='tab2'><tr><td><b>(G1)甲课(ID1) 学分[2]</b><br>班次-A01(一班) 教师[甲]</td>
            <td><b>(G1)乙课(ID2) 学分[3]</b><br>班次-A01(二班) 教师[乙]</td></tr></table>"""
        assertEquals(listOf("G1A1||A101|1|1|1|0.0"),
            occurrences(JwCdutParser(page(row("01周", course("G1A1")), conflicting)).generateCourseList()))
    }

    @Test fun `unrelated id-only tables cannot satisfy the source class anchors`() {
        assertTrue(JwCdutParser("<table id='tab1'>${row("01周", course("不是协议"))}</table>").generateCourseList().isEmpty())
    }
}
