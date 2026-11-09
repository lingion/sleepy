package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwClassicEamsStringLiteralTest {
    @Test fun `literal fields decode escaped unicode quotes and backslashes exactly once`() {
        val source = """<script>var unitCount=12;
            activity=new TaskActivity("T1",'O\'Neil',"C1","\u6570\u5b66,\"实验\"\\一","R1","A\\B\u6559室","01");
            index=2*unitCount+4; table0.marshalTable(2,1,20);</script>"""
        assertEquals(listOf(JwCourse("数学,\"实验\"\\一", "A\\B教室", "O'Neil", 3, 5, 5, 1, 1)),
            JwClassicEamsParser(source).generateCourseList())
    }

    @Test fun `teacher array JSON names use decoded values`() {
        val source = """<script>var unitCount=12;
            var actTeachers=[{"name":"\u7532老师"},{"name":"乙\\老师"}];
            activity=new TaskActivity(actTeacherId.join(','),actTeacherName.join(','),"C1","代数","R1","A101","001");
            index=1*unitCount+2; table0.marshalTable(2,1,20);</script>"""
        assertEquals(listOf(JwCourse("代数", "A101", "甲老师/乙\\老师", 2, 3, 3, 2, 2)),
            JwClassicEamsParser(source).generateCourseList())
    }

    @Test fun `name expression resolves its quoted declaration without truncating escaped quotes`() {
        val source = """<script>var unitCount=12;
            var courseNameLessonNo='\u6982率论\'实验\'';
            activity=new TaskActivity("T1","甲","C1",this.courseNameLessonNo,"R1","A101","01");
            index=0*unitCount+0; table0.marshalTable(2,1,20);</script>"""
        assertEquals(listOf(JwCourse("概率论'实验'", "A101", "甲", 1, 1, 1, 1, 1)),
            JwClassicEamsParser(source).generateCourseList())
    }

    @Test fun `escaped cancellation and literal backslash sequences keep their separate meanings`() {
        val source = """<script>var unitCount=12;
            activity=new TaskActivity("T1","甲","C1","停课记录","R1","\u505c\u8bfe","01");
            index=0*unitCount+0;
            activity=new TaskActivity("T1","甲","C2","\\u6570","R1","A101","001");
            index=0*unitCount+2; table0.marshalTable(2,1,20);</script>"""
        assertEquals(listOf(JwCourse("\\u6570", "A101", "甲", 1, 3, 3, 2, 2)),
            JwClassicEamsParser(source).generateCourseList())
    }
}
