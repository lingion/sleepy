package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic mixed pages exercise the real annual, fallback and source-period selection paths. */
class JwEcuplMixedSourcePeriodsTest {
    private fun mixedPage(lastAnnualClock: String = "13:55-14:40"): String = """
        <script>var table0=new CourseTable(2024,84);var unitCount=12;
        activity=new TaskActivity("T1","甲","C1","年表课(C1)","R1","A101","${"0".repeat(35)}1${"0".repeat(18)}");
        index=2*unitCount+4;index=2*unitCount+5;table0.marshalTable(36,1,20);</script>
        <table class="listTable"><tr><td>星期</td><td>08:00-08:45</td><td>08:55-09:40</td>
        <td>10:00-10:45</td><td>10:55-11:40</td><td>13:00-13:45</td><td>$lastAnnualClock</td></tr></table>
        <table class="listTable"><tr><td>课程序号</td><td>课程名称</td><td>教师</td><td>学分</td><td>备注</td></tr>
        <tr><td>C1</td><td>年表课</td><td>乙</td><td>2.5</td><td>分组教学</td></tr></table>
        <script>new CourseTable('2026-09-07',[[900,945],[955,1040],[1900,1945]]);
        var activity=newActivity("T2","丙","C2","脚本课(C2)","R2","B202","2026-09-07",2);
        addActivityByTime(activity,2,900,1040);</script>
    """.trimIndent()

    private val annualPeriods = listOf(
        Triple(1, "08:00", "08:45"), Triple(2, "08:55", "09:40"),
        Triple(3, "10:00", "10:45"), Triple(4, "10:55", "11:40"),
        Triple(5, "13:00", "13:45"), Triple(6, "13:55", "14:40"),
    )
    private val annualCourse = JwCourse("年表课", "A101", "乙", 3, 5, 6, 1, 1,
        credit = 2.5f, note = "分组教学")

    @Test fun successfulAnnualCoursesKeepTheirOwnPeriodsWhenAnotherScriptTableIsPresent() {
        val source = mixedPage()
        assertEquals(listOf(annualCourse), JwEcuplParser(source).generateCourseList())
        assertEquals(listOf(annualCourse), JwEcuplSuperParser(source).generateCourseList())
        assertEquals(listOf("脚本课"), JwEcuplScriptParser(source).generateCourseList().map { it.name })
        for (type in listOf(JwProtocol.TYPE_ECUPL, JwProtocol.TYPE_ECUPL_SUPER)) {
            assertEquals(type, annualPeriods, JwSourcePeriods.resolve(source, type, "华东政法大学", emptyList()))
        }
    }

    @Test fun selectedAnnualCoursesCannotBorrowAnotherScriptTableWhenAnnualClocksAreIncomplete() {
        val source = mixedPage(lastAnnualClock = "缺失时间")
        assertEquals(listOf(annualCourse), JwEcuplParser(source).generateCourseList())
        assertEquals(listOf(annualCourse), JwEcuplSuperParser(source).generateCourseList())
        assertEquals(listOf("脚本课"), JwEcuplScriptParser(source).generateCourseList().map { it.name })
        for (type in listOf(JwProtocol.TYPE_ECUPL, JwProtocol.TYPE_ECUPL_SUPER)) {
            assertEquals(type, emptyList<Triple<Int, String, String>>(),
                JwSourcePeriods.resolve(source, type, "华东政法大学", emptyList()))
        }
    }
}
