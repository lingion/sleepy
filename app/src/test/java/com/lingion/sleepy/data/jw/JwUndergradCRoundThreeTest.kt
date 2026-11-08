package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwUndergradCRoundThreeTest {
    private fun parse(type: String, html: String): List<JwCourse> {
        val courses = JwParserRegistry.parserFor(type, html).generateCourseList()
        assertEquals(courses, JwParserRegistry.selectBest(html, type).first)
        assertEquals(courses, JwImportViewModel.parseHtmlStatic(html, type).first)
        assertDeclaredHtmlFrame(type, html, courses)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "协议回归", type = type), courses, emptyList())
        assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        return courses
    }
    private fun occurrences(courses:List<JwCourse>)=courses.flatMap { c ->
        (c.startWeek..c.endWeek).filter { c.type==0 || it%2==c.type%2 }.flatMap { w ->
            (c.startNode..c.endNode).map { n -> "${c.name}|${c.teacher}|${c.room}|${c.day}|$n|$w" }
        }
    }.sorted()
    private fun expected(name:String,teacher:String,room:String,day:Int,nodes:List<Int>,weeks:List<Int>)=
        weeks.flatMap { w -> nodes.map { n -> "$name|$teacher|$room|$day|$n|$w" } }.sorted()
    private fun span(name:String,weeks:String,teacher:String="王",building:String="主",room:String="101")=
        "<div class='divOneClass'><span class='spLUName'>$name</span><span class='spTeacherName'>$teacher</span>"+
        "<span class='spWeekInfo'>$weeks</span><span class='spBuilding'>$building</span><span class='spClassroom'>$room</span></div>"

    @Test fun `xhtd five field groups keep separate names weeks and block nodes`() {
        val html="<table id='kbtable'><tr><td><div id='2-3'><nobr>分析2</nobr><br>类别<br>王<br>第1-5周(单)<br>A<br>"+
            "数字3<br>类别<br>李<br>第2,4周<br>B</div></td></tr></table>"
        val actual=parse("xhtd",html)
        assertEquals(listOf(JwCourse("分析2","A","王",3,3,4,1,5,1),JwCourse("数字3","B","李",3,3,4,2,2),JwCourse("数字3","B","李",3,3,4,4,4)),actual)
        assertEquals((expected("分析2","王","A",3,listOf(3,4),listOf(1,3,5))+expected("数字3","李","B",3,listOf(3,4),listOf(2,4))).sorted(),occurrences(actual))
    }

    @Test fun `xytc six seven eleven thirteen token shapes retain the bridge records`() {
        fun cell(tokens:List<String>)="<td>"+tokens.joinToString("&nbsp;")+"</td>"
        val html="<table id='report_tSxsgrkbcx'><tr>"+
            cell(listOf("一","1-3单周","星期1","1-2节","A","王"))+
            cell(listOf("二","2-4双周","忽略","星期2","3-4节","B","李"))+
            cell(listOf("三","5周","星期3","5节","C","陈<br>四","7周","星期4","7节","D","周"))+
            cell(listOf("五","9周","忽略","星期5","9节","E","吴<br>六","11周","忽略","星期6","11节","F","赵"))+"</tr></table>"
        val actual=parse("xytc",html)
        val records=listOf(JwCourse("一","A","王",1,1,2,1,3,1),JwCourse("二","B","李",2,3,4,2,4,2),
            JwCourse("三","C","陈",3,5,5,5,5),JwCourse("四","D","周",4,7,7,7,7),JwCourse("五","E","吴",5,9,9,9,9),JwCourse("六","F","赵",6,11,11,11,11))
        assertEquals(records,actual)
        assertEquals((expected("一","王","A",1,listOf(1,2),listOf(1,3))+expected("二","李","B",2,listOf(3,4),listOf(2,4))+
            listOf("三|陈|C|3|5|5","四|周|D|4|7|7","五|吴|E|5|9|9","六|赵|F|6|11|11")).sorted(),occurrences(actual))
    }

    @Test fun `cidp data class is already the first node and circled step is exact`() {
        val html="<div id='scheduleTable'><div class='Content' data-week='7' data-class='3'>"+span("地学2","1-8隔③周")+"</div></div>"
        val actual=parse("cidp",html)
        assertEquals(listOf(1,4,7).map { JwCourse("地学2","主101","王",7,3,4,it,it) },actual)
        assertEquals(expected("地学2","王","主101",7,listOf(3,4),listOf(1,4,7)),occurrences(actual))
        assertTrue(parse("cidp",html.replace("③","X")).isEmpty())
    }

    @Test fun `sdpei labelled teaching rows and rowspans keep weekday coordinates`() {
        val html="<table id='tableMain'><tr><th></th></tr><tr><th>第一节</th><td rowspan='2'>"+span("《训练2》","1,3周")+
            "</td><td>"+span("理论","2-4双周","李","副","202")+"</td></tr><tr><th>第二节</th><td>"+span("实践","5周","陈","","C")+
            "</td></tr><tr><th></th><td>忽略说明</td></tr></table>"
        val actual=parse("sdpei",html)
        assertEquals(listOf(JwCourse("训练2","主101","王",1,1,2,1,1),JwCourse("训练2","主101","王",1,1,2,3,3),
            JwCourse("理论","副202","李",2,1,1,2,4,2),JwCourse("实践","C","陈",2,2,2,5,5)),actual)
        assertEquals((expected("训练2","王","主101",1,listOf(1,2),listOf(1,3))+expected("理论","李","副202",2,listOf(1),listOf(2,4))+
            listOf("实践|陈|C|2|2|5")).sorted(),occurrences(actual))
    }

    @Test fun `shu credits numeric name and separate weekday week sets survive`() {
        val fields=listOf("编号","类别","代数2","2.5","忽略","李","一1-2(1,3周)三5-6(2-6双周)","A")
        val html="<table><tr name='rowclass'>"+fields.joinToString(""){"<td>$it</td>"}+"</tr></table>"
        val actual=parse("shu",html)
        assertEquals(listOf(JwCourse("代数2","A","李",1,1,2,1,1,credit=2.5f),JwCourse("代数2","A","李",1,1,2,3,3,credit=2.5f),
            JwCourse("代数2","A","李",3,5,6,2,6,2,credit=2.5f)),actual)
        assertEquals((expected("代数2","李","A",1,listOf(1,2),listOf(1,3))+expected("代数2","李","A",3,listOf(5,6),listOf(2,4,6))).sorted(),occurrences(actual))
        assertTrue(parse("shu",html.replace("(1,3周)三5-6(2-6双周)","")).isEmpty())
    }

    @Test fun `cqu html explicit time keeps sparse weeks without five day fallback`() {
        fun item(time:String)="<div class='item-box'><div class='item-name'>物理2[代码]</div><div class='item-location'>A</div>"+
            "<div class='item-instr'>王-主讲</div><div class='item-time'>$time</div></div>"
        val html="<div class='print-schedule-table'>"+item("1,3周 3-4节 星期日")+item("1-16周 时间待定")+"</div>"
        val actual=parse("cqu",html)
        assertEquals(listOf(JwCourse("物理2","A","王",7,3,4,1,1),JwCourse("物理2","A","王",7,3,4,3,3)),actual)
        assertEquals(expected("物理2","王","A",7,listOf(3,4),listOf(1,3)),occurrences(actual))
    }

    @Test fun `jmpt day rowspan expires and note inverse parity do not leak between tokens`() {
        fun row(lead:String,name:String,weeks:String,note:String)="<tr>$lead"+
            listOf(weeks,"3","4",name,"忽略","李","B",note,"不单周","其他","其他").joinToString(""){"<td>$it</td>"}+"</tr>"
        val html="<table class='admintable'></table><table class='admintable'>"+
            row("<td rowspan='2'>星期三</td>","工程2","第1-4周、6周","限选")+row("","工程3","第8周","实验")+
            row("","错误继承","第10周","")+"</table>"
        val actual=parse("jmpt",html)
        assertEquals(listOf(JwCourse("工程2","B","李",3,3,4,1,4,2,note="限选"),JwCourse("工程2","B","李",3,3,4,6,6,2,note="限选"),
            JwCourse("工程3","B","李",3,3,4,8,8,2,note="实验")),actual)
        assertEquals((expected("工程2","李","B",3,listOf(3,4),listOf(2,4,6))+expected("工程3","李","B",3,listOf(3,4),listOf(8))).sorted(),occurrences(actual))
    }

    @Test fun `lngd modern and legacy credits are decimal and teacher does not cross courses`() {
        val modern="<div id='root'><div class='kbappTimetableDayColumnRoot___1DlDV'>"+
            "<div class='kbappTimetableCourseRenderCourseItem___MgPtp'><div class='title___3o2RH'>机械2 2学分</div>"+
            "<div class='kbappTimetableCourseRenderCourseItemInfoText___2Zmwu'>1-3周张三 第1节-第2节 A</div></div>"+
            "<div class='kbappTimetableCourseRenderCourseItem___MgPtp'><div class='title___3o2RH'>力学3 2.5学分</div>"+
            "<div class='kbappTimetableCourseRenderCourseItemInfoText___2Zmwu'>5周 第3节-第4节 B</div></div></div></div>"
        val actual=parse("lngd",modern)
        assertEquals(listOf(JwCourse("机械2","A","张三",1,1,2,1,3,credit=2f),JwCourse("力学3","B","",1,3,4,5,5,credit=2.5f)),actual)
        assertEquals((expected("机械2","张三","A",1,listOf(1,2),listOf(1,2,3))+expected("力学3","","B",1,listOf(3,4),listOf(5))).sorted(),occurrences(actual))
        val old="<div id='wdkb-kb'><div class='kbappTimetableContentContainer'><div class='kbappTimetableDayColumnRoot'>"+
            "<div class='kbappTimetableCourseRenderCourseItemContainer'><div class='kbappTimetableCourseRenderCourseItemInfoText'>旧课2 1.5学分</div>"+
            "<div class='kbappTimetableCourseRenderCourseItemInfoText'>2周李四 第5节-第6节 C</div></div></div></div></div>"
        val oldActual=parse("lngd",old)
        assertEquals(listOf(JwCourse("旧课2","C","李四",1,5,6,2,2,credit=1.5f)),oldActual)
        assertEquals(expected("旧课2","李四","C",1,listOf(5,6),listOf(2)),occurrences(oldActual))
    }

    @Test fun `vatuu header offset and parenthesized teacher preserve all four field groups`() {
        val html="<table class='table_border'><tr><td>节次</td><td>星期一</td><td>星期二</td></tr>"+
            "<tr><td>1</td><td>英语2（王）<br>1,3周<br>A<br><br>英语3(李)<br>2-4双周<br>B<br></td><td></td></tr></table>"
        val actual=parse("vatuu",html)
        assertEquals(listOf(JwCourse("英语2","A","王",1,1,1,1,1),JwCourse("英语2","A","王",1,1,1,3,3),JwCourse("英语3","B","李",1,1,1,2,4,2)),actual)
        assertEquals((expected("英语2","王","A",1,listOf(1),listOf(1,3))+expected("英语3","李","B",1,listOf(1),listOf(2,4))).sorted(),occurrences(actual))
        assertTrue(parse("vatuu",html.replace("class='table_border'","class='table_border' id='table3'")).isEmpty())
    }

    @Test fun `yzzy adjacent names with different teachers rooms never blend fields`() {
        fun cell(teacher:String,room:String)="<td><div class='kbcontent'><font onmouseover='kbtc(this);'>护理2</font>"+
            "<font title='周次(节次)'>1,3(周)</font><font title='教室'>$room</font><font title='教师'>$teacher</font></div></td>"
        val html="<table id='timetable'><tr><td>标题</td></tr><tr>"+cell("王","A")+"</tr><tr>"+cell("李","B")+"</tr></table>"
        val actual=parse("yzzy",html)
        assertEquals(listOf(JwCourse("护理2","A","王",1,1,1,1,1),JwCourse("护理2","A","王",1,1,1,3,3),
            JwCourse("护理2","B","李",1,2,2,1,1),JwCourse("护理2","B","李",1,2,2,3,3)),actual)
        assertEquals((expected("护理2","王","A",1,listOf(1),listOf(1,3))+expected("护理2","李","B",1,listOf(2),listOf(1,3))).sorted(),occurrences(actual))
    }

    @Test fun `declared layouts never manufacture classes from foreign JSON`() {
        for(type in listOf("xhtd","xytc","cidp","sdpei","shu","cqu","jmpt","lngd","vatuu","yzzy"))
            assertTrue(type,parse(type,"""{"courseName":"外来课程","day":1,"startWeek":1,"endWeek":20}""").isEmpty())
    }
}
