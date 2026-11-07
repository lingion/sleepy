package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwEcuplAnnualWeekTest {
    private fun page(year: Int, origin: Int, indices: Set<Int>) = """
        <script>var table0=new CourseTable($year,84); var unitCount=12;
        activity=new TaskActivity("T1","甲","C1","法学(一)(C1)","R1","A101","${(0..53).joinToString("") { if (it in indices) "1" else "0" }}");
        index=2*unitCount+4; index=2*unitCount+5; table0.marshalTable($origin,1,20);</script>
        <table class="listTable"><tr><td>课程序号</td><td>课程名称</td><td>教师</td><td>学分</td><td>备注</td></tr>
        <tr><td>C1</td><td>法学(一)</td><td>乙</td><td>2.5</td><td>分组教学</td></tr></table>
    """

    private fun expected(vararg weeks: Int) = weeks.map {
        JwCourse("法学(一)", "A101", "乙", 3, 5, 6, it, it, credit=2.5f, note="分组教学")
    }

    @Test fun `annual bitmap uses marshal origin and details replace incomplete fields`() {
        val source = page(2024,36,setOf(35,37))
        assertEquals(expected(1,3), JwEcuplParser(source).generateCourseList())
        assertEquals(expected(1,3), JwParserRegistry.selectBest(source).first)
        assertEquals(expected(1,3), JwImportViewModel.parseHtmlStatic(source, JwProtocol.TYPE_ECUPL).first)
    }

    @Test fun `next year bitmap shifts into current academic semester`() {
        assertEquals(expected(7,9), JwEcuplParser(page(2024,50,setOf(3,5))).generateCourseList())
    }

    @Test fun `Saturday year end uses the extra calendar week`() {
        assertEquals(expected(8,10), JwEcuplParser(page(2022,50,setOf(3,5))).generateCourseList())
    }

    @Test fun `super keeps successful annual branch instead of merging a second timetable`() {
        val script = """<script language="JavaScript">new CourseTable('2026-09-07',[[800,845]]);
            newActivity("T1","甲","C2","另一课(02)","R1","A101","2026-09-07",2);
            addActivityByTime(activity,1,800,845);</script>"""
        assertEquals(expected(1,3), JwEcuplSuperParser(page(2024,36,setOf(35,37))+script).generateCourseList())
    }
}
