package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwUndergradCBoundaryTest {
    @Test fun `xhtd more than fifteen blocks and blank optional teacher remain aligned`() {
        val html="<div id='kbtable'><div id='16-7'><nobr>远端2</nobr><br>类别<br><br>第5周<br>A<br></div></div>"
        val actual=JwUndergradCParser(html,"xhtd").generateCourseList()
        assertEquals(listOf(JwCourse("远端2","A","",7,31,32,5,5)),actual)
        assertEquals(listOf("远端2||A|7|31|5","远端2||A|7|32|5"),actual.flatMap { c ->
            (c.startWeek..c.endWeek).flatMap { w -> (c.startNode..c.endNode).map { n -> "${c.name}|${c.teacher}|${c.room}|${c.day}|$n|$w" } }
        })
    }
    @Test fun `cqu explicit thirty first node remains valid without school bound evidence`() {
        val html="<div class='print-schedule-table'><div class='item-box'><div class='item-name'>高节2</div>"+
            "<div class='item-location'>B</div><div class='item-instr'>王</div><div class='item-time'>5周 31-32节 星期一</div></div></div>"
        val actual=JwUndergradCParser(html,"cqu").generateCourseList()
        assertEquals(listOf(JwCourse("高节2","B","王",1,31,32,5,5)),actual)
        assertEquals(listOf("高节2|王|B|1|31|5","高节2|王|B|1|32|5"),actual.flatMap { c ->
            (c.startWeek..c.endWeek).flatMap { w -> (c.startNode..c.endNode).map { n -> "${c.name}|${c.teacher}|${c.room}|${c.day}|$n|$w" } }
        })
    }
}
