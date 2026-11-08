package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwQzRoundTwoModernTest {
    private fun tooltip(time: String, name: String = "离散数学") = """
        <li class='qz-toolitiplists'><div class='qz-tooltipContent-title'>$name</div>
        <div class='qz-tooltipContent-detailitem'>老师：陈老师</div>
        <div class='qz-tooltipContent-detailitem'>地点：理科楼305</div>
        <div class='qz-tooltipContent-detailitem'>时间：$time</div></li>
    """.trimIndent()

    private fun modern2024(content: String): String = "<table><tr><td>上午</td><td name='kbDataTd'></td>" +
        "<td name='kbDataTd'><ul>$content</ul></td>" + "<td name='kbDataTd'></td>".repeat(5) + "</tr></table>"

    private fun course(day: Int = 2, start: Int = 3, end: Int = 4, from: Int = 1, to: Int = 5, type: Int = 1) =
        JwCourse("离散数学", "理科楼305", "陈老师", day, start, end, from, to, type)

    @Test fun `QZ 2024 tooltip retains local parity and a later isolated week`() {
        val html = modern2024(tooltip("1-5单周,8周[3-4节]"))
        assertEquals(listOf(course(), course(from = 8, to = 8, type = 0)),
            JwQzParser(html).generateCourseList())
    }

    @Test fun `QZ 2024 declared route matches generic family route`() {
        val html = modern2024(tooltip("1-5单周,8周[3-4节]"))
        assertEquals(listOf(course(), course(from = 8, to = 8, type = 0)),
            JwParserRegistry.selectBest(html, "qz_2024").first)
    }

    @Test fun `QZ 2024 multiple tooltip records retain distinct metadata`() {
        val html = modern2024(tooltip("1-5单周[3-4节]") + tooltip("2-6双周[7-8节]", "实验课"))
        assertEquals(listOf(course(), course(start = 7, end = 8, from = 2, to = 6, type = 2).copy(name = "实验课")),
            JwQzParser(html).generateCourseList())
    }

    private fun modern2017(sundayFirst: Boolean): String {
        val days = if (sundayFirst) listOf("日", "一", "二", "三", "四", "五", "六")
            else listOf("一", "二", "三", "四", "五", "六", "日")
        val content = "<div><div>离散数学</div><div>陈老师</div><div>课程属性</div>" +
            "<div>1-5周(单)</div><div>理科楼305</div></div>"
        val emptyRow = "<tr>" + "<td><div class='cell' style='text-align: center;'></div></td>".repeat(7) + "</tr>"
        val courseRow = "<tr><td><div class='cell' style='text-align: center;'>$content</div></td>" +
            "<td><div class='cell' style='text-align: center;'></div></td>".repeat(6) + "</tr>"
        return "<table class='el-table__header'><tr><th>节次</th>" +
            days.joinToString("") { "<th><div>星期$it</div></th>" } + "</tr></table>" +
            "<table class='el-table__body'><tbody>$emptyRow$emptyRow$courseRow</tbody></table>"
    }

    @Test fun `QZ 2017 Sunday first final row retains its three periods`() {
        assertEquals(listOf(course(day = 7, start = 5, end = 7)),
            JwQzParser(modern2017(true)).generateCourseList())
    }

    @Test fun `QZ 2017 Monday first final row keeps Monday`() {
        assertEquals(listOf(course(day = 1, start = 5, end = 7)),
            JwQzParser(modern2017(false)).generateCourseList())
    }

    @Test fun `QZ 2017 slash layout keeps room explicit periods and weeks`() {
        val html = modern2017(true).replace("1-5周(单)", "理科楼305/3-4节/1-5周(单)")
        assertEquals(listOf(course(day = 7)), JwQzParser(html).generateCourseList())
    }

    @Test fun `modern QZ families claim a strong confidence and diagnostic marker`() {
        for ((html, marker) in listOf(modern2024(tooltip("1-5单周[3-4节]")) to "qz-2024-tooltip",
            modern2017(true) to "qz-2017-element-table")) {
            val parser = JwQzParser(html)
            assertEquals(100, parser.confidence())
            assertEquals(true, marker in parser.matchedFeatures())
        }
    }

    @Test fun `QZ 2024 missing explicit periods cannot manufacture a row schedule`() {
        val html = modern2024(tooltip("1-5周"))
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.selectBest(html, "qz").first)
    }
}
