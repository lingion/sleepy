package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwPostgradRoundTwoNwpuTest {
    private fun source(times: String): String = """
        <select id="xq"><option selected>2026-2027秋</option></select>
        <table id="sample-table-1">
        <thead><tr><th>院系</th><th>课程编号</th><th>课程名称</th><th>班级名称</th>
        <th>主讲教师</th><th>学分</th><th>班级说明</th><th>上课时间</th></tr></thead>
        <tbody><tr><td>数学</td><td>M501</td><td>数学分析2</td><td>硕一班</td>
        <td>陈老师</td><td>2.5</td><td>限选</td><td>$times</td></tr></tbody></table>
    """.trimIndent()

    @Test fun `credit and note survive every separated occurrence`() {
        val html = source("长安-教一(第1-7周 单周 星期一上1节上3节)<br>长安-教二(第2-6周 双周 星期三下1节下2节)")
        val actual = JwNwpuPostParser(html).generateCourseList()
        val expected = listOf(
            JwCourse("数学分析2(硕一班)", "长安 教一", "陈老师", 1, 1, 1, 1, 7, 1, credit = 2.5f, note = "数学 M501 限选"),
            JwCourse("数学分析2(硕一班)", "长安 教一", "陈老师", 1, 3, 3, 1, 7, 1, credit = 2.5f, note = "数学 M501 限选"),
            JwCourse("数学分析2(硕一班)", "长安 教二", "陈老师", 3, 7, 8, 2, 6, 2, credit = 2.5f, note = "数学 M501 限选"),
        )
        assertEquals(expected, actual)
        assertEquals(14, actual.sumOf { course ->
            (course.startWeek..course.endWeek).count { week -> course.type == 0 || week % 2 == course.type % 2 } *
                (course.endNode - course.startNode + 1)
        })
    }

    @Test fun `blank metadata does not create note separators`() {
        val html = source("长安-教一(第5周 星期五上2节)")
            .replace("<td>数学</td>", "<td></td>").replace("<td>限选</td>", "<td></td>")
        val actual = JwNwpuPostParser(html).generateCourseList().single()
        assertEquals("M501", actual.note)
        assertEquals(2.5f, actual.credit, 0f)
        assertEquals(5, actual.startWeek)
        assertEquals(5, actual.endWeek)
    }
}
