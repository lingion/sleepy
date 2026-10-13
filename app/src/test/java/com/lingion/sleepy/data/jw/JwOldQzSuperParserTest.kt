package com.lingion.sleepy.data.jw

import com.lingion.sleepy.ui.screen.imports.FrameTraversalTree
import org.junit.Assert.*
import org.junit.Test

class JwOldQzSuperParserTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("jw/fixtures/old_qz_super/$name.html")!!.readText()
    private fun grid(block: String, prefix: String = "") = "<table id='kbtable'>$prefix<tr><td>节次</td><td>$block</td>" + "<td></td>".repeat(6) + "</tr></table>"
    private fun parse(html: String) = JwOldQzParser(html).generateCourseList()

    @Test fun `source shapes all route through production qz old`() {
        for (name in listOf("base", "jlict", "ccut", "ccsu")) {
            val html = fixture(name)
            val courses = JwParserRegistry.parserFor(JwProtocol.TYPE_QZ_OLD, html).generateCourseList()
            assertTrue(name, courses.isNotEmpty())
            assertTrue(name, FrameTraversalTree.findAnchors(html).isNotEmpty())
            assertEquals(courses, JwParserRegistry.selectBest(html, JwProtocol.TYPE_QZ_OLD).first)
        }
    }
    @Test fun `distinct source forms preserve values`() {
        val jlict = parse(fixture("jlict")).single()
        assertEquals("标题课", jlict.name); assertEquals(1, jlict.day); assertEquals(1, jlict.startNode); assertEquals(2, jlict.endNode); assertEquals(1, jlict.type)
        val ccut = parse(fixture("ccut"))
        assertEquals(listOf(1, 9), ccut.map { it.startWeek }); assertTrue(ccut.all { it.name == "分组课" && it.startNode == 3 && it.endNode == 4 })
        val ccsu = parse(fixture("ccsu")).single()
        assertEquals("固定格课", ccsu.name); assertEquals(3, ccsu.day); assertEquals(3, ccsu.startNode); assertEquals(1, ccsu.type)
    }
    @Test fun `compact nodes and sparse weeks preserved`() {
        val c = parse(grid("<div>课<br>教师<br>1-4,9-12周[0304节]<br>室</div>"))
        assertEquals(listOf(1,9), c.map { it.startWeek }); assertEquals(listOf(4,12), c.map { it.endWeek })
        assertTrue(c.all { it.startNode == 3 && it.endNode == 4 })
    }
    @Test fun `single section and blank optional fields retained`() {
        val c = parse(grid("<div>课<br><br>3周[3节]<br></div>")).single()
        assertEquals(3, c.startNode); assertEquals(3, c.endNode); assertEquals("", c.teacher); assertEquals("", c.room)
    }
    @Test fun `nested wrappers do not duplicate and independent courses retain names`() {
        val c = parse(grid("<div><div>A<br>T<br>1周[1节]<br>R<br>B<br>T<br>2周[2节]<br>R</div></div>"))
        assertEquals(listOf("A","B"), c.map { it.name })
    }
    @Test fun `same course multiple arrangements keeps its name`() {
        val c = parse(grid("<div>A<br>T1<br>1周[1节]<br>R1<br>T2<br>2周[3节]<br>R2</div>"))
        assertEquals(listOf("A", "A"), c.map { it.name }); assertEquals(listOf("T1", "T2"), c.map { it.teacher })
    }
    @Test fun `malformed or overflowing record does not abort following valid course`() {
        val c = parse(grid("<div>坏<br>T<br>99999999999周[1-2节]<br>R</div><div>好<br>T<br>2周[3节]<br>R</div>"))
        assertEquals(listOf("好"), c.map { it.name })
    }
    @Test fun `reject reversed ranges missing weeks and ambiguous columns`() {
        for (time in listOf("4-2周[1节]", "1周[4-2节]", "周[1节]", "65周[1节]")) assertTrue(parse(grid("<div>课<br>T<br>$time<br>R</div>")).isEmpty())
        assertTrue(parse("<table id='kbtable'><tr><td><div>课<br>T<br>1周[1节]<br>R</div></td></tr></table>").isEmpty())
    }
    @Test fun `header order determines weekday and partial rows never shift`() {
        val header = "<tr><th>节</th><th>星期日</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th></tr>"
        assertEquals(7, parse(grid("<div>课<br>T<br>1周[1节]<br>R</div>", header)).single().day)
    }
    @Test fun `hidden ancestor and login page rejected`() {
        assertTrue(parse(grid("<div style='display: none !important'><div>课<br>T<br>1周[1节]<br>R</div></div>")).isEmpty())
        assertTrue(parse("<form><input type='password'></form>").isEmpty())
    }
}
