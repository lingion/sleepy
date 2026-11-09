package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Independent factory tests: no reference to the new parser classes. */
@RunWith(Parameterized::class)
class JwUndergradCRoutingTest(private val type:String,private val source:String,private val expected:JwCourse) {
    @Test fun `declared school routes own complete records and reject foreign JSON`() {
        val (courses,attempts)=JwParserRegistry.selectBest(source,type)
        assertEquals(type,listOf(expected),courses)
        assertTrue(type,attempts.any { it.type==type && it.courseCount==1 && it.exception==null })
        assertTrue(type,JwParserRegistry.selectBest("""{"activities":[{"courseName":"外来","weekday":1,"weeks":[5]}]}""",type).first.isEmpty())
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun cases():List<Array<Any>> {
            fun c(end:Int=1,credit:Float=0f,note:String="")=JwCourse("课程2","A","王",1,1,end,5,5,credit=credit,note=note)
            val spans="<div class='divOneClass'><span class='spLUName'>课程2</span><span class='spTeacherName'>王</span>"+
                "<span class='spWeekInfo'>5周</span><span class='spClassroom'>A</span></div>"
            val shu=listOf("编号","类别","课程2","2.5","其他","王","一1-1(5周)","A").joinToString(""){"<td>$it</td>"}
            val jmpt=listOf("第5周","1","1","课程2","其他","王","A","限选","每周","其他","其他").joinToString(""){"<td>$it</td>"}
            val thu="""<script>function setInitValue() {
                strHTML += "<a class='mainHref' href='page?x=1234567890;12345678'>";
                strHTML += "<b>课程2</b>";
                strHTML1 += "；王";
                strHTML1 += "；必修";
                strHTML1 += "；第5周";
                strHTML1 += "；A";
                document.getElementById('a1_1').innerHTML += strHTML;
                } Event.observe();</script>"""
            return listOf(
                arrayOf("xhtd","<div id='kbtable'><div id='1-1'><nobr>课程2</nobr><br>类别<br>王<br>第5周<br>A</div></div>",c(2)),
                arrayOf("xytc","<table id='report_tSxsgrkbcx'><tr><td>课程2&nbsp;5周&nbsp;星期1&nbsp;1节&nbsp;A&nbsp;王</td></tr></table>",c()),
                arrayOf("cidp","<div id='scheduleTable'><div class='Content' data-week='1' data-class='1'>$spans</div></div>",c(2)),
                arrayOf("sdpei","<table id='tableMain'><tr><th>第一节</th><td>$spans</td></tr></table>",c()),
                arrayOf("shu","<table><tr name='rowclass'>$shu</tr></table>",c(credit=2.5f)),
                arrayOf("cqu","<div class='print-schedule-table'><div class='item-box'><div class='item-name'>课程2[代码]</div><div class='item-location'>A</div><div class='item-instr'>王-主讲</div><div class='item-time'>5周 1节 星期一</div></div></div>",c()),
                arrayOf("jmpt","<table class='admintable'></table><table class='admintable'><tr><td rowspan='1'>星期一</td>$jmpt</tr></table>",c(note="限选")),
                arrayOf("lngd","<div id='root'><div class='kbappTimetableDayColumnRoot___1DlDV'><div class='kbappTimetableCourseRenderCourseItem___MgPtp'><div class='title___3o2RH'>课程2 2.5学分</div><div class='kbappTimetableCourseRenderCourseItemInfoText___2Zmwu'>5周王 第1节-第1节 A</div></div></div></div>",c(credit=2.5f)),
                arrayOf("vatuu","<table class='table_border'><tr><td>节</td><td>星期一</td></tr><tr><td>1</td><td>课程2(王)<br>5周<br>A<br></td></tr></table>",c()),
                arrayOf("yzzy","<table id='timetable'><tr><td>标题</td></tr><tr><td><div class='kbcontent'><font onmouseover='kbtc(this);'>课程2</font><font title='周次(节次)'>5(周)</font><font title='教室'>A</font><font title='教师'>王</font></div></td></tr></table>",c()),
            )
        }
    }
}
