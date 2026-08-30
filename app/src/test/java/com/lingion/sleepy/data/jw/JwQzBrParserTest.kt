package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwQzBrParserTest {

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/qz_br/$name")
            ?: error("missing fixture: jw/fixtures/qz_br/$name")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `parses qz_br normal fixture - 8 courses with multi segment and multi section`() {
        val html = loadFixture("timetable_qzbr_normal.html")
        val courses = JwQzBrParser(html).generateCourseList()
        // expected.json: courseCount=8, 含同格双课与逗号多段
        assertEquals(8, courses.size)

        // 高等数学 / 周一第1-2节 / 1-16周
        val highMath = courses[0]
        assertEquals("高等数学", highMath.name)
        assertEquals("张老师", highMath.teacher)
        assertEquals("教一101", highMath.room)
        assertEquals(1, highMath.day)
        assertEquals(1, highMath.startNode)
        assertEquals(2, highMath.endNode)
        assertEquals(1, highMath.startWeek)
        assertEquals(16, highMath.endWeek)
        assertEquals(0, highMath.type)

        // 计算机网络 5-15,17 逗号多段应拆两条
        val networking = courses.filter { it.name == "计算机网络" }
        assertEquals(2, networking.size)
        assertEquals(5, networking[0].startWeek)
        assertEquals(15, networking[0].endWeek)
        assertEquals(17, networking[1].startWeek)
        assertEquals(17, networking[1].endWeek)
    }

    @Test
    fun `parses qz_br empty kbtable without exception`() {
        val html = loadFixture("timetable_kbtable_empty.html")
        val courses = JwQzBrParser(html).generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `parses qz_br login page without exception`() {
        val html = loadFixture("login_page.html")
        val courses = JwQzBrParser(html).generateCourseList()
        assertEquals(0, courses.size)
    }

    @Test
    fun `qz_br course name is clean when span structure is used`() {
        // 区别于基础 QzParser.parseCourseName 用 substringBefore('<font')
        // 在 span 结构上得整段污染，qz_br 改用 substringBefore('<br>') 得干净课名
        val html = """
<html><body>
<table id="kbtable">
  <tr>
    <td>第一节</td>
    <td>
      <div class="kbcontent">测试课程<br>
        <span title="老师">王老师</span><br>
        <span title="教室">C301</span><br>
        <span title="周次(节次)">1-16(周)</span>
      </div>
    </td>
  </tr>
</table>
</body></html>
        """.trimIndent()
        val courses = JwQzBrParser(html).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("测试课程", courses[0].name)  // 不是 "测试课程 王老师 C301 1-16(周)"
        assertEquals("王老师", courses[0].teacher)
        assertEquals("C301", courses[0].room)
    }
}
