package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwUndergradCSourceShapeTest {
    private fun occurrences(courses:List<JwCourse>)=courses.flatMap { c ->
        (c.startWeek..c.endWeek).filter { c.type==0 || it%2==c.type%2 }.flatMap { w ->
            (c.startNode..c.endNode).map { n -> "${c.name}|${c.teacher}|${c.room}|${c.day}|$n|$w" }
        }
    }.sorted()
    @Test fun `xytc three bridge fragments use both source length branches`() {
        fun cell(bridge:String)="<td>前课&nbsp;5周&nbsp;星期1&nbsp;1节&nbsp;A&nbsp;$bridge&nbsp;7周&nbsp;星期2&nbsp;3节&nbsp;B&nbsp;李</td>"
        val html="<table id='report_tSxsgrkbcx'><tr>"+cell("王<br>陈<br>后课")+cell("王<br><b>陈</b><br>后课")+"</tr></table>"
        val actual=assertUndergradBImportRoutes(html,"xytc")
        assertEquals(listOf(JwCourse("前课","A","王陈",1,1,1,5,5),JwCourse("后课","B","李",2,3,3,7,7),
            JwCourse("前课","A","王",1,1,1,5,5),JwCourse("陈后课","B","李",2,3,3,7,7)),actual)
        assertEquals(listOf("前课|王陈|A|1|1|5","后课|李|B|2|3|7","前课|王|A|1|1|5","陈后课|李|B|2|3|7").sorted(),occurrences(actual))
    }
    @Test fun `shu leading parity before explicit weeks does not default whole semester`() {
        val fields=listOf("编号","类别","上海2","2","其他","王","一1-2(单第1-5周)三3-4(双第2-6周)","A")
        val html="<table><tr name='rowclass'>"+fields.joinToString(""){"<td>$it</td>"}+"</tr></table>"
        val actual=assertUndergradBImportRoutes(html,"shu")
        assertEquals(listOf(JwCourse("上海2","A","王",1,1,2,1,5,1,credit=2f),JwCourse("上海2","A","王",3,3,4,2,6,2,credit=2f)),actual)
        assertEquals(listOf("上海2|王|A|1|1|1","上海2|王|A|1|2|1","上海2|王|A|1|1|3","上海2|王|A|1|2|3","上海2|王|A|1|1|5","上海2|王|A|1|2|5",
            "上海2|王|A|3|3|2","上海2|王|A|3|4|2","上海2|王|A|3|3|4","上海2|王|A|3|4|4","上海2|王|A|3|3|6","上海2|王|A|3|4|6").sorted(),occurrences(actual))
    }
    @Test fun `vatuu leading descriptive text does not discard final four source fields`() {
        val html="<table class='table_border'><tr><td>节</td><td>星期一</td></tr><tr><td>1</td><td>课表提示<br>英语2(王)<br>5周<br>A<br></td></tr></table>"
        val actual=assertUndergradBImportRoutes(html,"vatuu")
        assertEquals(listOf(JwCourse("英语2","A","王",1,1,1,5,5)),actual)
        assertEquals(listOf("英语2|王|A|1|1|5"),occurrences(actual))
    }
    @Test fun `xhtd parity parentheses before week character retain exact odd occurrences`() {
        val html="<div id='kbtable'><div id='1-1'><nobr>西华2</nobr><br>类别<br>王<br>第1-5(单)周<br>A</div></div>"
        val actual=assertUndergradBImportRoutes(html,"xhtd")
        assertEquals(listOf(JwCourse("西华2","A","王",1,1,2,1,5,1)),actual)
        assertEquals(listOf("西华2|王|A|1|1|1","西华2|王|A|1|2|1","西华2|王|A|1|1|3","西华2|王|A|1|2|3","西华2|王|A|1|1|5","西华2|王|A|1|2|5").sorted(),occurrences(actual))
    }
}
