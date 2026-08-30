package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JwHnustParser 单测 — T3（hnust 协议，kbtable + div display:none）
 * fixture: src/test/resources/jw/hnust-urp/hnust_*.html
 */
class JwHnustParserTest {

    private fun html(name: String): String =
        javaClass.classLoader.getResource("jw/hnust-urp/$name.html")!!.readText()

    @Test
    fun hnust_kbtable_hidden_div_6条() {
        val courses = JwHnustParser(html("hnust_kbtable_hidden_div"), 0).generateCourseList()
        assertEquals(6, courses.size)

        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.day); assertEquals(1, math.startNode); assertEquals(2, math.endNode)
        assertEquals(1, math.startWeek); assertEquals(16, math.endWeek)
        assertEquals(0, math.type); assertEquals("张老师", math.teacher); assertEquals("主楼A101", math.room)

        // div id="2-2" → startNode = 2*2-1 = 3；周次 "2-15周,17周" 拆 2 条
        val eng = courses.filter { it.name == "大学英语" }.sortedBy { it.startWeek }
        assertEquals(2, eng.size)
        assertEquals(2, eng[0].day); assertEquals(3, eng[0].startNode); assertEquals(4, eng[0].endNode)
        assertEquals(2, eng[0].startWeek); assertEquals(15, eng[0].endWeek)
        assertEquals(17, eng[1].startWeek); assertEquals(17, eng[1].endWeek)

        // div id="3-3" → startNode=5；id="5-5" → startNode=9
        assertEquals(5, courses.first { it.name == "线性代数" }.startNode)
        val cs = courses.first { it.name == "计算机基础" }
        assertEquals(9, cs.startNode); assertEquals(10, cs.endNode); assertEquals(5, cs.day)
        assertEquals(5, cs.startWeek); assertEquals(12, cs.endWeek)
    }

    @Test
    fun hnust_empty_kbtable_返回空() {
        assertEquals(0, JwHnustParser(html("hnust_empty_kbtable"), 0).generateCourseList().size)
    }

    @Test
    fun hnust_login_page_无kbtable返回空不抛() {
        assertEquals(0, JwHnustParser(html("hnust_login_page"), 0).generateCourseList().size)
    }

    @Test
    fun hnust_style两种写法都识别() {
        // 真实页 "display: none;"（冒号后空格）与 fixture "display:none;"（无空格）必须都能命中
        val withSpace = """
<html><body><table id="kbtable"><tr>
<td><div id="1-1" style="display: none;">课A<br>王老师<br>1-10周<br>教室X</div></td>
<td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
</tr></table></body></html>""".trimIndent()
        val noSpace = withSpace.replace("display: none;", "display:none;")
        val c1 = JwHnustParser(withSpace, 0).generateCourseList()
        val c2 = JwHnustParser(noSpace, 0).generateCourseList()
        assertEquals("带空格写法应命中", 1, c1.size)
        assertEquals("无空格写法应命中", 1, c2.size)
        assertEquals("课A", c1[0].name); assertEquals(1, c1[0].startNode); assertEquals(2, c1[0].endNode)
        assertEquals(1, c1[0].day); assertEquals(1, c1[0].startWeek); assertEquals(10, c1[0].endWeek)
    }

    @Test
    fun hnust_oldQzType1_反向过滤() {
        // oldQzType=1（东北石油大学模式）：style != display:none 的 div 才是课
        val src = """
<html><body><table id="kbtable"><tr>
<td><div id="1-1">课B<br>李老师<br>1-8周<br>教室Y</div></td>
<td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>
</tr></table></body></html>""".trimIndent()
        val courses = JwHnustParser(src, 1).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("课B", courses[0].name)
        // 同一页面用 oldQzType=0 应 0 产出（div 无 display:none 样式）
        assertEquals(0, JwHnustParser(src, 0).generateCourseList().size)
    }

    @Test
    fun hnust_构造默认参数为0() {
        // 默认 oldQzType=0：无 display:none 样式的页面 0 产出
        val plain = """<html><body><table id="kbtable"><tr><td><div id="1-1">课C<br>师<br>1-2周<br>房</div></td></tr></table></body></html>"""
        assertEquals(0, JwHnustParser(plain).generateCourseList().size)
        assertEquals(1, JwHnustParser(plain, 1).generateCourseList().size)
    }
}
