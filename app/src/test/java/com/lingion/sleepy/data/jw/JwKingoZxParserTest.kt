package com.lingion.sleepy.data.jw

import com.lingion.sleepy.ui.screen.imports.FrameTraversalTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwKingoZxParserTest {
    @Test fun reportRowsFollowUpstreamFields() {
        val html = javaClass.getResource("/jw/kingo-zx/kingo_zx_sample.html")!!.readText()
        val parser = JwKingoZxParser(html)
        val courses = parser.generateCourseList()
        assertEquals(2, courses.size)
        assertEquals("线性代数", courses[0].name)
        assertEquals("赵老师", courses[0].teacher)
        assertEquals("XB309", courses[0].room)
        assertEquals(1, courses[0].day)
        assertEquals(5, courses[0].startNode)
        assertEquals(6, courses[0].endNode)
        assertEquals(1, courses[0].startWeek)
        assertEquals(16, courses[0].endWeek)
        assertEquals(3, courses[1].day)
        assertEquals(90, parser.confidence())
    }

    @Test fun gridKeepsSparseWeeksAndSectionRange() {
        val html = """<table id='mytable'><tr><td>节次</td>
            <td class='td'><div>数据结构<br>钱老师<br>5,7,9,13-16,1-18 单[5-6]<br>教2-301</div></td>
            <td class='td'><div>大学英语<br>孙老师<br>2-8 双[1-2]<br>教1-102</div></td>
            </tr></table>"""
        val courses = JwKingoZxParser(html).generateCourseList()
        assertTrue(courses.any { it.name == "数据结构" && it.room == "教2-301" && it.day == 1 && it.startNode == 5 && it.endNode == 6 })
        assertTrue(courses.any { it.name == "大学英语" && it.day == 2 && it.type == 2 && it.startWeek == 2 && it.endWeek == 8 })
        assertFalse(courses.any { it.name == "数据结构" && it.startWeek <= 10 && it.endWeek >= 10 && it.type == 0 })
    }

    @Test fun reportAcceptsEveryWeekdaySpelling() {
        val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        for ((index, label) in days.withIndex()) {
            val cells = MutableList(11) { "<td></td>" }
            cells[1] = "<td>[A01]数学</td>"
            cells[10] = "<td>1-16周 ${label} 5-6节/教室</td>"
            val html = "<table id='pageRpt'><tr><td><table></table><table><tr>" +
                cells.joinToString("") + "</tr></table></td></tr></table>"
            val courses = JwKingoZxParser(html).generateCourseList()
            assertEquals(label, index + 1, courses.single().day)
        }
    }

    @Test fun reportAcceptsDaySectionWeeksOrderFromUpstreamCode() {
        val cells = MutableList(11) { "<td></td>" }
        cells[1] = "<td>[A02]物理</td>"
        cells[10] = "<td>星期四 3-4节 1-16周/教室A<br>周日 5-6节 2-14周 双/教室B</td>"
        val html = "<table id='pageRpt'><tr><td><table></table><table><tr>" +
            cells.joinToString("") + "</tr></table></td></tr></table>"
        val courses = JwKingoZxParser(html).generateCourseList()
        assertEquals(2, courses.size)
        assertEquals(4, courses[0].day)
        assertEquals(1, courses[0].startWeek)
        assertEquals(16, courses[0].endWeek)
        assertEquals(7, courses[1].day)
        assertEquals(2, courses[1].startWeek)
        assertEquals(14, courses[1].endWeek)
        assertEquals(2, courses[1].type)
    }

    @Test fun emptyWeekdayColumnDoesNotShiftLaterGridCourses() {
        val html = """<table id='mytable'><tr><td>节次</td><td>空星期一</td>
            <td class='td'><div>体育<br>李老师<br>1-8[傍晚]<br>操场</div></td></tr></table>"""
        val course = JwKingoZxParser(html).generateCourseList().single()
        assertEquals(2, course.day)
        assertEquals(9, course.startNode)
        assertEquals(9, course.endNode)
        assertEquals("操场", course.room)
    }

    @Test fun noHeaderGridKeepsMondayAndBlankTuesdayPositions() {
        val html = """<table id='mytable'><tr>
            <td class='td'><div>周一课<br>甲老师<br>1-8[1-2]<br>A101</div></td>
            <td></td><td class='td'><div>周三课<br>乙老师<br>1-8[3-4]<br>C303</div></td>
            <td></td><td></td><td></td><td></td></tr></table>"""
        val courses = JwKingoZxParser(html).generateCourseList()
        assertTrue(courses.any { it.name == "周一课" && it.day == 1 && it.startNode == 1 })
        assertTrue(courses.any { it.name == "周三课" && it.day == 3 && it.startNode == 3 })
    }

    @Test fun unsupportedFixtureAndExpiredLoginYieldNoCourses() {
        val unsupported = "<div id='pageRpt'><table><tr><td>高等数学</td><td>张三</td><td>1-8周</td><td>六教</td></tr></table></div>"
        assertTrue(JwKingoZxParser(unsupported).generateCourseList().isEmpty())
        val expired = "<html><form action='/login'><input type='password'/></form></html>"
        assertTrue(JwKingoZxParser(expired).generateCourseList().isEmpty())
        assertEquals(0, JwKingoZxParser(expired).confidence())
    }

    @Test fun captureRecognizesKingoFrameAnchors() {
        assertTrue(FrameTraversalTree.findAnchors("<table id='pageRpt'></table>").contains("pageRpt"))
        assertTrue(FrameTraversalTree.findAnchors("<table id='mytable'></table>").contains("mytable"))
    }
}
