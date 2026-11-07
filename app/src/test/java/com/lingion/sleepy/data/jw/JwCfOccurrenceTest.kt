package com.lingion.sleepy.data.jw

import org.jsoup.nodes.Entities
import org.junit.Assert.assertEquals
import org.junit.Test

class JwCfOccurrenceTest {
    private fun assertRoutes(type: String, source: String, expected: List<JwCourse>) {
        assertEquals("direct", expected, JwParserRegistry.parserFor(type, source).generateCourseList())
        assertEquals("declared", expected, JwParserRegistry.selectBest(source, type).first)
        assertEquals("screen", expected, JwImportViewModel.parseHtmlStatic(source, type).first)
    }

    @Test fun `CF sparse unordered sections and repeated weeks never fill holes`() {
        val source = """<script>var kbxx = [{"kcmc":"实验","xq":"7","jcdm2":"06,01,02,06","zcs":"5,1,3,3,9","teaxms":"张/李","jxcdmcs":"A;B"}];</script>"""
        assertRoutes("cf", source, listOf(
            JwCourse("实验", "A;B", "张/李", 7, 1, 2, 1, 5, 1),
            JwCourse("实验", "A;B", "张/李", 7, 1, 2, 9, 9),
            JwCourse("实验", "A;B", "张/李", 7, 6, 6, 1, 5, 1),
            JwCourse("实验", "A;B", "张/李", 7, 6, 6, 9, 9),
        ))
    }

    @Test fun `CF invalid explicit coordinates are not clamped to legal lessons`() {
        for ((day, nodes, weeks) in listOf(
            Triple("0", "1", "1"), Triple("8", "1", "1"), Triple("1", "0", "1"),
            Triple("1", "-1,2", "1"), Triple("1", "1,x", "1"), Triple("1", "1", "0,1"),
            Triple("1", "1", "-1"), Triple("1", "1", "1,x"),
        )) {
            val source = """<script>var kbxx = [{"kcmc":"错误课","xq":"$day","jcdm2":"$nodes","zcs":"$weeks"}];</script>"""
            assertRoutes("cf", source, emptyList())
        }
    }

    private fun tooltip(name: String, day: String = "3", nodes: String = "第3-5节", weeks: String = "第1-7单周,第10-12双周", teacher: String = "张老师", room: String = "A & B") =
        "<span lay-tips=\"" + Entities.escape("""<h3>上课任务[123]</h3><table>
            <tr><th>课程：</th><td>[C001]$name</td></tr><tr><th>星期：</th><td>$day</td></tr>
            <tr><th>节次：</th><td>$nodes</td></tr><tr><th>上课周次：</th><td>$weeks</td></tr>
            <tr><th>教学场地：</th><td>$room</td></tr><tr><th>授课教师：</th><td>$teacher</td></tr>
            </table>""") + "\">课表块</span>"

    @Test fun `CF new lay tips keep local parity and separate course blocks`() {
        val source = tooltip("实验") + tooltip("讨论", "5", "第8节", "第3周,第9周", "李老师", "线上")
        val expected = listOf(
            JwCourse("实验", "A & B", "张老师", 3, 3, 5, 1, 7, 1),
            JwCourse("实验", "A & B", "张老师", 3, 3, 5, 10, 12, 2),
            JwCourse("讨论", "线上", "李老师", 5, 8, 8, 3, 3),
            JwCourse("讨论", "线上", "李老师", 5, 8, 8, 9, 9),
        )
        assertRoutes("cf_new", source, expected)
        assertEquals(expected, JwParserRegistry.selectBest(source).first)
    }

    @Test fun `CF new incomplete tooltip does not manufacture first week or first period`() {
        for (source in listOf(
            tooltip("错误", day="0"), tooltip("错误", nodes=""), tooltip("错误", nodes="第5-3节"),
            tooltip("错误", weeks=""), tooltip("错误", weeks="第0-4周"),
            tooltip("错误", weeks="第1-3周,garbage"),
            tooltip("错误").replace("&lt;th&gt;星期：&lt;/th&gt;", ""),
        )) assertRoutes("cf_new", source, emptyList())
    }

    @Test fun `CF new parenthesized week parity stays local to each segment`() {
        for (weeks in listOf("第1-7周(单周),第10-12周(双周)", "第1-7周（单周），第10-12周（双周）")) {
            assertRoutes("cf_new", tooltip("实验", weeks = weeks), listOf(
                JwCourse("实验", "A & B", "张老师", 3, 3, 5, 1, 7, 1),
                JwCourse("实验", "A & B", "张老师", 3, 3, 5, 10, 12, 2),
            ))
        }
    }
}
