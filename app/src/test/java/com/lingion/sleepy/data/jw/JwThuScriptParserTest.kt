package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwThuScriptParserTest {
    private fun normal(name:String,weeks:String,big:Int=2,day:Int=3,id:String="12345678",room:String="A",teacher:String="王")="""
        strHTML += "<a class='mainHref' href='page?x=1234567890;$id'>";
        strHTML += "<b>$name</b>";
        strHTML1 += "；$teacher";
        strHTML1 += "；必修";
        strHTML1 += "；$weeks";
        strHTML1 += "；$room";
        document.getElementById('a${big}_$day').innerHTML += strHTML;
    """
    private fun html(body:String,metadata:String="")="<script>$metadata\nfunction setInitValue() {\n$body\n}\nEvent.observe();</script>"
    private fun occurrences(courses:List<JwCourse>)=courses.flatMap { c ->
        (c.startWeek..c.endWeek).filter { c.type==0 || it%2==c.type%2 }.flatMap { w ->
            (c.startNode..c.endNode).map { n -> "${c.name}|${c.teacher}|${c.room}|${c.day}|$n|$w" }
        }
    }.sorted()
    private fun expected(name:String,teacher:String,room:String,day:Int,nodes:List<Int>,weeks:List<Int>)=
        weeks.flatMap { w -> nodes.map { n -> "$name|$teacher|$room|$day|$n|$w" } }.sorted()

    @Test fun `normal sparse weeks keep decimal names notes and no identifier credit`() {
        val meta="""var gridColumns = ["课程名","任课教师","选课文字说明"];
            var gridData = [["分析2","备选教师","限选, 实验[甲]"]];"""
        val source=html(normal("分析2","第1,3-4周"),meta)
        val actual=JwThuScriptParser(source).generateCourseList()
        assertEquals(listOf(JwCourse("分析2","A","王",3,3,5,1,1,note="限选, 实验[甲]"),JwCourse("分析2","A","王",3,3,5,3,4,note="限选, 实验[甲]")),actual)
        assertEquals(expected("分析2","王","A",3,listOf(3,4,5),listOf(1,3,4)),occurrences(actual))
        assertTrue(actual.all { it.credit==0f })
    }

    @Test fun `special course retains actual clocks grid note and metadata teacher`() {
        val special="""strHTML = "<a class='blue_red_none' href='page?p_id=12345678'><b><font color='blue'>实验2</font></b></a><font color='blue'>北大(理科楼；第2,5周；时间：8：05-9：40)</font>";
            document.getElementById('a1_7').innerHTML += strHTML;"""
        val meta="""var gridColumns = ["课程名","任课教师","选课文字说明"];
            var gridData = [["实验2","李, 陈","北大教辅, 必选"]];"""
        val actual=JwThuScriptParser(html(special,meta)).generateCourseList()
        assertEquals(listOf(2,5).map { JwCourse("实验2","理科楼(北大)","李, 陈",7,1,2,it,it,startTime="08:05",endTime="09:40",note="北大教辅, 必选") },actual)
        assertEquals(expected("实验2","李, 陈","理科楼(北大)",7,listOf(1,2),listOf(2,5)),occurrences(actual))
    }

    @Test fun `semester rescheduling moves exact original occurrence and suppresses holiday replacement`() {
        val body=normal("周一课程","第1-3周",1,1)+normal("周日课程","第1-3周",3,7,room="B",teacher="李")
        val semester="""{"weekCount":18,"reschedule":[[1,1,2,7],[3,1]]}"""
        val actual=JwThuScriptParser(html(body),semester).generateCourseList()
        assertEquals(listOf(JwCourse("周一课程","A","王",1,1,2,2,2),JwCourse("周一课程","A","王",7,1,2,2,2),
            JwCourse("周日课程","B","李",7,6,7,1,1),JwCourse("周日课程","B","李",7,6,7,3,3)),actual)
        assertEquals((expected("周一课程","王","A",1,listOf(1,2),listOf(2))+expected("周一课程","王","A",7,listOf(1,2),listOf(2))+
            expected("周日课程","李","B",7,listOf(6,7),listOf(1,3))).sorted(),occurrences(actual))
    }

    @Test fun `captured week count applies only to known whole semester markers`() {
        val source=html(normal("全学期","全周",6,6)+normal("后半","后八周",5,5)+normal("单周课","单周",4,4))
        val actual=JwThuScriptParser(source,"""{"weekCount":18}""").generateCourseList()
        assertEquals(JwCourse("全学期","A","王",6,12,14,1,18),actual.first())
        assertEquals(JwCourse("后半","A","王",5,10,11,9,18),actual[1])
        assertEquals(listOf(JwCourse("单周课","A","王",4,8,9,1,18,1)),actual.drop(2))
        assertEquals((expected("全学期","王","A",6,listOf(12,13,14),(1..18).toList())+
            expected("后半","王","A",5,listOf(10,11),(9..18).toList())+expected("单周课","王","A",4,listOf(8,9),(1..17 step 2).toList())).sorted(),occurrences(actual))
    }

    @Test fun `unknown weeks malformed semester and invalid clocks do not create records`() {
        val unknown=html(normal("未知","待定"))
        assertTrue(JwThuScriptParser(unknown).generateCourseList().isEmpty())
        val known=html(normal("已知","第5周"))
        for(json in listOf("""{"weekCount":2.5}""","""{"reschedule":[[1,1,2]]}""","""{"reschedule":[[1,1,2,7],[1,1,3,7]]}"""))
            assertTrue(JwThuScriptParser(known,json).generateCourseList().isEmpty())
        val bad="""strHTML = "<a class='blue_red_none'><b><font color='blue'>坏时间</font></b></a><font color='blue'>本校(A；第5周；时间：10:00-9:00)</font>";
            document.getElementById('a1_1').innerHTML += strHTML;"""
        assertTrue(JwThuScriptParser(html(bad)).generateCourseList().isEmpty())
    }

    @Test fun `comments cannot create courses and missing fields cannot steal next record`() {
        val source=html("// "+normal("注释","第1周").trim().replace("\n","\n// ")+"\n"+
            "strHTML += \"<a href='x1234567890;12345678'>\";strHTML += \"<b>缺周</b>\";document.getElementById('a1_1').innerHTML += strHTML;"+
            normal("保留2","第5周",1,2,room="http://room",teacher="李"))
        val actual=JwThuScriptParser(source).generateCourseList()
        assertEquals(listOf(JwCourse("保留2","http://room","李",2,1,2,5,5)),actual)
        assertEquals(expected("保留2","李","http://room",2,listOf(1,2),listOf(5)),occurrences(actual))
    }
}
