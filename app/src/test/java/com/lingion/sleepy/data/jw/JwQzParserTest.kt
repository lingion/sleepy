package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwQzParserTest {

    // --- 复用现有 mockQzHtml 模式，验证 teacher fallback 不影响基础路径 ---
    private val mockQzHtml = """
<html><body>
<table id="kbtable">
  <tr>
    <td>第一节</td>
    <td>
      <div>
        <div class="kbcontent">
          高等数学<br>
          <font title="老师">张三</font><br>
          <font title="教室">A101</font><br>
          <font title="周次(节次)">1-16(周)</font>
        </div>
      </div>
    </td>
  </tr>
</table>
</body></html>
    """.trimIndent()

    @Test
    fun `JwQzParser parses teacher attribute fallback case`() {
        // edge_teacher_attr.html 含两门课：一门用 title="老师" 一门用 title="教师"
        // T2 修复后，"教师" 属性路径必须解析出 teacher 字段
        val html = """
<html><head><meta charset="UTF-8"><title>个人课表</title></head>
<body>
<table id="kbtable" border="1">
  <tr>
    <td>第一节</td>
    <td>
      <div class="kbcontent">
        普通课老师<br>
        <font title="老师">张老师</font><br>
        <font title="教室">A101</font><br>
        <font title="周次(节次)">1-16(周)</font>
      </div>
    </td>
    <td>
      <div class="kbcontent">
        教师属性课<br>
        <font title="教师">李老师</font><br>
        <font title="教室">B202</font><br>
        <font title="周次(节次)">1-8(单)</font>
      </div>
    </td>
  </tr>
</table>
</body></html>
        """.trimIndent()
        val courses = JwQzParser(html).generateCourseList()
        assertEquals(2, courses.size)
        // 第一门 title="老师" 正常
        assertEquals("普通课老师", courses[0].name)
        assertEquals("张老师", courses[0].teacher)
        // 第二门 title="教师" 走 fallback
        assertEquals("教师属性课", courses[1].name)
        assertEquals("李老师", courses[1].teacher)  // T2 修复前是空串
        assertEquals(1, courses[1].type)  // (单)
    }

    @Test
    fun `JwQzParser basic mock still parses`() {
        // 不回归：现有 JwParserTest 的 mockQzHtml 模式继续通过
        val courses = JwQzParser(mockQzHtml).generateCourseList()
        assertTrue("应至少 1 门课", courses.size >= 1)
        assertEquals("张三", courses[0].teacher)
    }

    @Test
    fun `JwQzParser throws when kbtable missing`() {
        // T8 契约: 缺 #kbtable 抛 JwParseException(不再静默空表)
        try {
            JwQzParser("<html><body>无 kbtable</body></html>").generateCourseList()
            org.junit.Assert.fail("应抛 JwParseException")
        } catch (e: JwParseException) {
            org.junit.Assert.assertTrue("attempts 非空", e.attempts.isNotEmpty())
            org.junit.Assert.assertEquals("NO_TABLE_CONTAINER_MARKER", e.attempts[0].exception)
        }
    }
}
