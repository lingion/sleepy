package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwQzRoundTwoSchoolTest {
    private fun table(content: String) = "<table id='kbtable'><tr><th>星期一</th><th>星期二</th>" +
        "<th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>" +
        "<tr><td><div><div class='kbcontent'>$content</div></div></td>" + "<td></td>".repeat(6) + "</tr></table>"

    @Test fun `USTB room field separates weeks and preserves adjacent records`() {
        val html = table("""
            离散数学<br><font title='老师'>陈老师</font><br>
            <font title='教室'>理科楼305&nbsp;&nbsp;1-5单周</font><br><font title='节次'>1-2节</font><br>
            实验课<br><font title='老师'>李老师</font><br>
            <font title='教室'>实验楼201&nbsp;&nbsp;8周</font><br><font title='节次'>1-2节</font>
        """.trimIndent())
        assertEquals(listOf(JwCourse("离散数学", "理科楼305", "陈老师", 1, 1, 2, 1, 5, 1),
            JwCourse("实验课", "实验楼201", "李老师", 1, 1, 2, 8, 8)),
            JwParserRegistry.selectBest(html, "qz_ustb").first)
    }

    @Test fun `FSPT provides all twelve source periods`() {
        val expected = listOf("08:40-09:20", "09:25-10:05", "10:25-11:05", "11:10-11:50",
            "14:00-14:40", "14:45-15:25", "15:45-16:25", "16:30-17:10",
            "19:00-19:40", "19:45-20:25", "20:35-21:15", "21:20-22:00")
            .mapIndexed { index, value -> Triple(index + 1, value.substringBefore('-'), value.substringAfter('-')) }
        assertEquals(expected, JwSourcePeriods.resolve("", "qz_fspt", "佛山职业技术学院", emptyList()))
    }

    @Test fun `USTB room name spaces are not the week delimiter`() {
        val html = table("""
            离散数学<br><font title='老师'>陈老师</font><br>
            <font title='上课地点'>[教学区]</font><br>
            <font title='教室'>理科楼 305&nbsp;&nbsp;1-5单周</font><br><font title='节次'>9-10节</font>
        """.trimIndent())
        assertEquals(listOf(JwCourse("离散数学", "教学区理科楼 305", "陈老师", 1, 1, 2, 1, 5, 1)),
            JwParserRegistry.selectBest(html, "qz_ustb").first)
    }

    @Test fun `FSPT uses the source row map even when a section label disagrees`() {
        val html = table("""
            离散数学<br><font title='老师'>陈老师</font><br><font title='教室'>理科楼305</font><br>
            <font title='周次(节次)'>1-5(单),8(周)[9-10节]</font>
        """.trimIndent())
        assertEquals(listOf(JwCourse("离散数学", "理科楼305", "陈老师", 1, 1, 2, 1, 5, 1),
            JwCourse("离散数学", "理科楼305", "陈老师", 1, 1, 2, 8, 8)),
            JwParserRegistry.selectBest(html, "qz_fspt").first)
    }

    @Test fun `FSPT retains a captured source period override`() {
        val captured = listOf(Triple(1, "09:00", "09:45"))
        assertEquals(captured, JwSourcePeriods.resolve("", "qz_fspt", "佛山职业技术学院", captured))
    }
}
