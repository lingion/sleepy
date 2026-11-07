package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwSduOccurrenceTest {
    private fun row(name: String, weeks: String, day: String, node: String, teacher: String = "李老师", room: String = "中心楼A") =
        listOf("序号", "代码", name, "无关", "无关", "无关", "无关", teacher, weeks, day, node, "无关", room)
            .joinToString("", "<tr>", "</tr>") { "<td>$it</td>" }
    private fun table(body: String) = "<table id=\"ysjddDataTableId\"><tr><th>排课</th></tr>$body</table>"

    @Test fun `SDU fixed columns preserve single periods and local week parity`() {
        val html = table(row("课程甲", "1-5单,8-10双,14周", "2", "7") + row("课程甲", "3周", "7", "1", "赵老师", "东楼B"))
        val expected = listOf(
            JwCourse("课程甲", "中心楼A", "李老师", 2, 7, 7, 1, 5, 1),
            JwCourse("课程甲", "中心楼A", "李老师", 2, 7, 7, 8, 10, 2),
            JwCourse("课程甲", "中心楼A", "李老师", 2, 7, 7, 14, 14),
            JwCourse("课程甲", "东楼B", "赵老师", 7, 1, 1, 3, 3),
        )
        assertEquals(expected, JwParserRegistry.parserFor("sdu", html).generateCourseList())
        assertEquals(expected, JwParserRegistry.selectBest(html, "sdu").first)
        assertEquals(expected, JwImportViewModel.parseHtmlStatic(html, "sdu").first)
    }

    @Test fun `SDU malformed rows do not inherit weeks or become twenty week courses`() {
        val html = table(row("空周", "", "1", "1") + row("坏周", "待定", "1", "1") +
            row("混合", "1-4,待定", "1", "1") + row("坏日", "1-2周", "0", "1") +
            row("坏节", "1-2周", "1", "0") + row("负周", "-1-2周", "1", "1") +
            "<tr><td>短行</td></tr>" + row("有效", "12周", "6", "3"))
        assertEquals(listOf(JwCourse("有效", "中心楼A", "李老师", 6, 3, 3, 12, 12)),
            JwParserRegistry.selectBest(html, "sdu").first)
    }
}
