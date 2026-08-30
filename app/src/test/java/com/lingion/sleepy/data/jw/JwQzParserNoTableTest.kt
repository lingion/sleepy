package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T8 — JwQzParser 找不到 #kbtable 必须抛 JwParseException，不再静默返回空。
 */
class JwQzParserNoTableTest {

    @Test
    fun `JwQzParser throws JwParseException when kbtable missing`() {
        val html = "<html><body><div>no kbtable here</div></body></html>"
        try {
            JwQzParser(html).generateCourseList()
            org.junit.Assert.fail("应抛 JwParseException")
        } catch (e: JwParseException) {
            assertTrue("attempts 非空", e.attempts.isNotEmpty())
            assertEquals(0, e.attempts[0].courseCount)
            assertEquals("NO_TABLE_CONTAINER_MARKER", e.attempts[0].exception)
            assertEquals("JwQzParser", e.attempts[0].parserName)
        }
    }

    @Test
    fun `JwQzBrParser inherits throw on missing kbtable`() {
        val html = "<html><body></body></html>"
        try {
            JwQzBrParser(html).generateCourseList()
            org.junit.Assert.fail("应抛 JwParseException")
        } catch (e: JwParseException) {
            assertEquals("NO_TABLE_CONTAINER_MARKER", e.attempts[0].exception)
        }
    }

    @Test
    fun `JwQzWithNodeParser inherits throw on missing kbtable`() {
        try {
            JwQzWithNodeParser("<html></html>").generateCourseList()
            org.junit.Assert.fail("应抛 JwParseException")
        } catch (e: JwParseException) {
            assertEquals("NO_TABLE_CONTAINER_MARKER", e.attempts[0].exception)
        }
    }

    @Test
    fun `JwQzCrazyParser inherits throw on missing kbtable`() {
        try {
            JwQzCrazyParser("<html></html>").generateCourseList()
            org.junit.Assert.fail("应抛 JwParseException")
        } catch (e: JwParseException) {
            assertEquals("NO_TABLE_CONTAINER_MARKER", e.attempts[0].exception)
        }
    }

    @Test
    fun `mockQzHtml with kbtable still parses regression`() {
        val mockQzHtml = """
<html><body>
<table id="kbtable">
  <tr><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
  <tr>
    <td>1</td>
    <td>
      <div>
        <div class="kbcontent">
          高数<br>
          <span title="老师">张三</span><br>
          <span title="教室">A101</span><br>
          <span title="周次(节次)">1-16周</span>
        </div>
      </div>
    </td>
    <td></td><td></td><td></td><td></td><td></td><td></td>
  </tr>
</table>
</body></html>
        """.trimIndent()
        val courses = JwQzParser(mockQzHtml).generateCourseList()
        assertTrue("至少 1 门课", courses.isNotEmpty())
    }
}
