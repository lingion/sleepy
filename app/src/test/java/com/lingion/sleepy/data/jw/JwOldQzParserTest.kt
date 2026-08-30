package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwOldQzParserTest {

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/qz_old/$name")
            ?: error("missing fixture: jw/fixtures/qz_old/$name")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `parses qz_old normal fixture - 5 courses including sunday`() {
        val html = loadFixture("timetable_kbtable_normal.html")
        val courses = JwOldQzParser(html).generateCourseList()
        assertEquals(5, courses.size)

        // 周日 (day=7) 大学物理实验 第9-10节 10-17周
        val sunday = courses.first { it.day == 7 }
        assertEquals("大学物理实验", sunday.name)
        assertEquals("孙老师", sunday.teacher)
        assertEquals("物理实验楼201", sunday.room)
        assertEquals(9, sunday.startNode)
        assertEquals(10, sunday.endNode)
        assertEquals(10, sunday.startWeek)
        assertEquals(17, sunday.endWeek)
        assertEquals(0, sunday.type)  // OldQzParser type 恒 0
    }

    @Test
    fun `filters display_none divs and empty cells`() {
        val html = loadFixture("timetable_kbtable_hidden.html")
        val courses = JwOldQzParser(html).generateCourseList()
        // expected.json: courseCount=1, 两个 hidden div + 整格空白都被过滤
        assertEquals(1, courses.size)
        assertEquals("高等数学", courses[0].name)
        assertEquals("张老师", courses[0].teacher)
    }

    @Test
    fun `parses empty kbtable without exception`() {
        val html = loadFixture("timetable_kbtable_empty.html")
        val courses = JwOldQzParser(html).generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `returns empty for login page without exception`() {
        // OldQzParser L12 与 L15: 上游对 kbtable null 直接 NPE
        // 我们用 ?: return courseList 与 JwQzParser 静默语义保持一致
        val html = loadFixture("login_page.html")
        val courses = JwOldQzParser(html).generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `existing JwParserTest mockQzHtml is recognized as empty by qz_old`() {
        // JwParserTest.mockQzHtml 用 title='老师' 属性 + kbcontent div
        // 这不是 qz_old 格式 (qz_old 无 title 属性)，应返回 0 门课
        val mockOldStyleHtml = """
<html><body>
<table id="kbtable">
  <tr><td>第一节</td><td>
    <div><div class="kbcontent">高数<br><span title="老师">张三</span></div></div>
  </td></tr>
</table></body></html>
        """.trimIndent()
        val courses = JwOldQzParser(mockOldStyleHtml).generateCourseList()
        // qz_old 查找含 [ ] 周 节 四要素的时间串, mock 不含 → 0 门
        assertEquals(0, courses.size)
        assertTrue("验证 qz_old 与基础 QzParser 走完全不同的代码路径", courses.isEmpty())
    }
}
