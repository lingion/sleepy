package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwTaskActivityRoutingTest {
    @Test fun `classic seven argument activities keep all indices and weeks through legacy declared route`() {
        val source = """<script>var unitCount=14; var activity=null;
            activity = new TaskActivity("01","甲老师","C01","实验(一)","R01","A101","0111");
            index=1*unitCount+9; index=1*unitCount+10; index=3*unitCount+13;
            table0.marshalTable(1,1,3);</script>"""
        val expected = (1..3).flatMap { week -> listOf(
            JwCourse("实验(一)", "A101", "甲老师", 2, 10, 11, week, week),
            JwCourse("实验(一)", "A101", "甲老师", 4, 14, 14, week, week),
        ) }.toSet()
        assertEquals(expected, JwClassicEamsParser(source).generateCourseList().toSet())
        assertEquals(expected, JwKingoParser(source).generateCourseList().toSet())
        assertEquals(expected, JwParserRegistry.selectBest(source).first.toSet())
        assertEquals(expected, JwImportViewModel.parseHtmlStatic(source, JwProtocol.TYPE_KINGO_NEW).first.toSet())
    }

    @Test fun `four argument invented shape cannot produce plausible timetable`() {
        val source = """<script>var unitCount=12; var activity=null;
            activity = new TaskActivity('课程','教师','教室','111');
            index=0*unitCount+0; table0.marshalTable();</script>"""
        assertEquals(emptyList<JwCourse>(), WakeUpCompat.parseKingoTaskActivity(source))
    }
}
