package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwThuIntegrationTest {
    private fun skipped(parser: JwThuparser): Int =
        parser.skippedCourseCount
    private fun html(body:String,meta:String="")="<html><input name='p_xnxq' value='2026-2027-1'><script>$meta\nfunction setInitValue() {\n$body\n} Event.observe(window,'load',setInitValue);</script></html>"
    private fun normal(id:String="ABCDEFG1",name:String="数学",weeks:String="单周",day:Int=1)= """
        strHTML += "<a class='mainHref' href='/syxk;$id'>";
        strHTML += "<b>$name</b>";
        strHTML1 += "；王老师";
        strHTML1 += "；必修";
        strHTML1 += "；$weeks";
        strHTML1 += "；A楼";
        getElementById('a1_$day').innerHTML += strHTML;
    """.trimIndent()

    @Test fun sameCourseIdCanReuseAllFieldsInLaterGrid() {
        val body=normal(weeks="第1-3周")+"\nstrHTML += \"<a class='mainHref' href='/syxk;ABCDEFG1'>\";\ngetElementById('a2_4').innerHTML += strHTML;"
        assertEquals(listOf(JwCourse("数学","A楼","王老师",1,1,2,1,3),JwCourse("数学","A楼","王老师",4,3,5,1,3)),JwThuparser(html(body)).generateCourseList())
    }

    @Test fun oddAndEvenWholeSemesterStayCompactWithoutMoves() {
        assertEquals(listOf(JwCourse("数学","A楼","王老师",1,1,2,1,16,1),JwCourse("数学","A楼","王老师",2,1,2,2,16,2)),
            JwThuparser(html(normal()+normal(id="ABCDEFG2",weeks="双周",day=2))).generateCourseList())
    }

    @Test fun unsupportedAndTruncatedCandidatesAreCountedOnceAndReset() {
        val body=normal(weeks="待定")+normal(id="ABCDEFG2",weeks="第2周")+"\nstrHTML += \"<a class='mainHref' href='/syxk;ABCDEFG3'>\";"
        val parser=JwThuparser(html(body))
        assertEquals(1,parser.generateCourseList().size)
        assertEquals(2,skipped(parser))
        parser.generateCourseList()
        assertEquals(2,skipped(parser))
    }

    @Test fun wrapperKeepsExistingDiagnosticsAndFiveCourseFixture() {
        val fixture=javaClass.classLoader!!.getResourceAsStream("jw_fixtures/thu/zhkbcx-courses.sample.html")!!.bufferedReader().use { it.readText() }
        val parser=JwThuparser(fixture)
        assertEquals(5,parser.generateCourseList().size)
        assertTrue(parser.confidence()>=80)
        assertTrue(parser.matchedFeatures().contains("thu:week-text-grammar"))
        val special="strHTML = \"<a class='blue_red_none' href='/x?p_id=ABCDEFG1'><b><font color='blue'>缺失详情</font></b></a><font color='blue'>北大</font>\";\ngetElementById('a1_1').innerHTML += strHTML;"
        val invalid=JwThuparser(html(special))
        assertTrue(invalid.generateCourseList().isEmpty())
        assertEquals(1,invalid.skippedCourseCount)
        assertTrue(invalid.matchedFeatures().contains("thu:skipped-unsupported=1"))
    }

    @Test fun sameNameMetadataDoesNotOverwriteOtherClass() {
        val meta="""var gridColumns = ["课程名","任课教师","选课文字说明"]; var gridData = [["数学","甲老师","甲班说明"],["数学","乙老师","乙班说明"]];"""
        val special="""strHTML = "<a class='blue_red_none' href='/x?p_id=ABCDEFG1'><b><font color='blue'>数学</font></b></a><font color='blue'>北大(A；第1周)</font>"; getElementById('a1_1').innerHTML += strHTML;"""
        val actual=JwThuparser(html(special,meta)).generateCourseList().single()
        assertEquals("",actual.teacher);assertEquals("",actual.note)
    }

    @Test fun explicitCourseIdInMetadataLinkHasPriorityOverRepeatedName() {
        val meta="""var gridColumns = ["课程名","任课教师","选课文字说明"]; var gridData = [["<a href='/x?p_id=ABCDEFG1'>数学</a>","甲老师","甲班说明"],["<a href='/x?p_id=ABCDEFG2'>数学</a>","乙老师","乙班说明"]];"""
        val special="""strHTML = "<a class='blue_red_none' href='/x?p_id=ABCDEFG1'><b><font color='blue'>数学</font></b></a><font color='blue'>北大(A；第1周)</font>"; getElementById('a1_1').innerHTML += strHTML;"""
        val actual=JwThuparser(html(special,meta)).generateCourseList().single()
        assertEquals("甲老师",actual.teacher);assertEquals("甲班说明",actual.note)
    }
}
