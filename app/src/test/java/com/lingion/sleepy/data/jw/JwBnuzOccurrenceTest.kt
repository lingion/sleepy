package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwBnuzOccurrenceTest {
    private fun modernRow(vararg schedules: String) = """<tr><td><div ng-repeat="c in s.info"><h4>材料 &amp; 实验</h4>
        <ul><li>${schedules.joinToString("") { "<p ng-repeat=\"t in c.times\">$it</p>" }}</li></ul>
        </div></td></tr>"""
    private fun page(rows: String) = "<table id=\"schedule\"><tbody>$rows</tbody></table>"

    private fun assertRoutes(source: String, expected: List<JwCourse>) {
        assertEquals(expected, JwBnuzParser(source).generateCourseList())
        assertEquals(expected, JwParserRegistry.selectBest(source, "bnuz").first)
        assertEquals(expected, JwImportViewModel.parseHtmlStatic(source, "bnuz").first)
    }

    @Test fun `modern rows are weekdays and each paragraph owns its full schedule`() {
        val source = page("<tr><td>周一空课</td></tr>" + modernRow(
            "第1-7单,10双周第3-4节[A &amp; B]<span>张老师</span>",
            "第2,6周第8节[C303]<span>李老师</span>",
        ) + "<tr><td></td></tr>".repeat(4) + modernRow("第9周第11-13节[线上]<span>王老师</span>"))
        val expected = listOf(
            JwCourse("材料 & 实验", "A & B", "张老师", 2, 3, 4, 1, 7, 1),
            JwCourse("材料 & 实验", "A & B", "张老师", 2, 3, 4, 10, 10, 2),
            JwCourse("材料 & 实验", "C303", "李老师", 2, 8, 8, 2, 2),
            JwCourse("材料 & 实验", "C303", "李老师", 2, 8, 8, 6, 6),
            JwCourse("材料 & 实验", "线上", "王老师", 7, 11, 13, 9, 9),
        )
        assertRoutes(source, expected)
        assertEquals(expected, JwParserRegistry.selectBest(source).first)
    }

    @Test fun `modern invalid explicit weeks sections and extra weekdays cannot become courses`() {
        for (text in listOf("第0-3周第1节[A]", "第3-1周第1节[A]", "第1,bad周第1节[A]", "第1周第0节[A]", "第1周第5-2节[A]", "第1周[A]", "周第1节[A]"))
            assertRoutes(page(modernRow(text)), emptyList())
        assertRoutes(page("<tr><td></td></tr>".repeat(7) + modernRow("第1周第1节[A]")), emptyList())
    }

    @Test fun `legacy HTML entities decode once without carrying markup into fields`() {
        val source = """<table id="table1"><tr><td>3</td><td><span></span>材料 &amp; 实验<br>张&amp;李{1-3周}<br>A &amp; B(2节)<br></td></tr></table>"""
        assertRoutes(source, listOf(JwCourse("材料 & 实验", "A & B", "张&李", 1, 3, 4, 1, 3)))
    }

    @Test fun `legacy invalid fields cannot be clamped into valid courses`() {
        for ((node, week, step) in listOf(Triple(0, "1周", 1), Triple(1, "0周", 1), Triple(1, "3-1周", 1), Triple(1, "1周", 0), Triple(1, "-1周", 1))) {
            val source = """<table id="table1"><tr><td>$node</td><td><span></span>错误课<br>张{$week}<br>A(${step}节)<br></td></tr></table>"""
            assertRoutes(source, emptyList())
        }
    }
}
