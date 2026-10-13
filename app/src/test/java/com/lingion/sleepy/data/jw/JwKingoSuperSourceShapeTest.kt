package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwKingoSuperSourceShapeTest {
    @Test fun mytableFourLineWithoutHead() {
        val html = """<table id="mytable"><tr class="H"><th>header</th></tr><tr>
            <td></td><td><div>高等数学<br>张三<br>1-16周[3-4节]<br>博学楼A-101</div></td></tr></table>"""
        val c = JwKingoParser(html).generateCourseList().single()
        assertEquals("高等数学", c.name)
        assertEquals(2, c.day)
        assertEquals(3, c.startNode)
        assertEquals(4, c.endNode)
        assertEquals(16, c.endWeek)
    }

    @Test fun mytableCompactXkinfo() {
        val html = """<table id="mytable"><tr><td><div class="div1"><span class="xkinfo">&nbsp;</span></div>
            <div>大学物理<br>李四&nbsp;1-8周[5-6节]<br>实验楼201</div></td></tr></table>"""
        val c = JwKingoParser(html).generateCourseList().single()
        assertEquals("大学物理", c.name)
        assertEquals("李四", c.teacher)
        assertEquals("实验楼201", c.room)
        assertEquals(5, c.startNode)
    }

    @Test fun formatTwoDayAndSectionsShareCell() {
        val html = """<table><thead><tr><th>课程</th><th>教师</th><th>地点</th><th>周次</th><th>节次</th><th>单双周</th></tr></thead>
            <tbody><tr><td>算法</td><td>王五</td><td>教二</td><td>1-8</td><td>周三[3-4节]</td><td>单</td></tr></tbody></table>"""
        val c = JwKingoParser(html).generateCourseList().single()
        assertEquals("算法", c.name)
        assertEquals(3, c.day)
        assertEquals(3, c.startNode)
        assertEquals(1, c.type)
    }

    @Test fun kbDivUsesRowAndLastSevenColumns() {
        val html = """<table id="kbDiv"><tbody><tr><td>节次1</td><td></td><td></td><td>数据结构(1-16周 教工301)</td>
            <td></td><td></td><td></td><td></td></tr></tbody></table>"""
        val c = JwKingoParser(html).generateCourseList().single()
        assertEquals("数据结构", c.name)
        assertEquals(3, c.day)
        assertEquals(1, c.startNode)
        assertEquals("教工301", c.room)
    }

    @Test fun malformedBlockDoesNotHideFollowingCourse() {
        val html = """<table id="mytable"><tr><td><div>坏课程<br>教师<br>无周次<br>地点</div>
            <div>有效课程<br>赵老师<br>2-8周[1-2节]<br>A101</div></td></tr></table>"""
        val c = JwKingoParser(html).generateCourseList().single()
        assertEquals("有效课程", c.name)
        assertEquals(2, c.endNode)
    }

    @Test fun malformedSectionRangeDoesNotBecomeInventedCourse() {
        val html = """<table id="mytable"><tr><td><div>坏课程<br>教师<br>1-8周[1,3,5节]<br>A101</div>
            <div>有效课程<br>教师<br>1-8周[2节]<br>A102</div></td></tr></table>"""
        val c = JwKingoParser(html).generateCourseList().single()
        assertEquals("有效课程", c.name)
        assertEquals(2, c.startNode)
        assertEquals(2, c.endNode)
    }

    @Test fun mytableRetainsOddWeekQualifier() {
        val html = """<table id="mytable"><tr><td><div>单周课程<br>教师<br>1-15周(单)[5-6节]<br>A101</div></td></tr></table>"""
        val c = JwKingoParser(html).generateCourseList().single()
        assertEquals(1, c.type)
        assertEquals(15, c.endWeek)
    }

    @Test fun genericRegistryPrefersNewLayoutButExplicitZxStillWorks() {
        val html = """<table id="mytable"><tr><td><div>新课<br>教师<br>1-8周[1-2节]<br>A101</div></td></tr></table>"""
        val (courses, attempts) = JwParserRegistry.selectBest(html)
        assertEquals("新课", courses.single().name)
        assertTrue(attempts.any { it.type == JwProtocol.TYPE_KINGO_NEW && it.courseCount == 1 })
        val explicit = JwParserRegistry.parserFor(JwProtocol.TYPE_KINGO_ZX, """<table id="mytable"><tr><td class="td"><div>老课<br>教师<br>1-8[1-2节]<br>A101</div></td></tr></table>""")
        assertEquals("老课", explicit.generateCourseList().single().name)
    }

    @Test fun noLayoutGivesAggregateError() {
        val ex = try { JwKingoParser("<html><body></body></html>").generateCourseList(); null }
            catch (e: IllegalStateException) { e }
        assertTrue(ex?.message.orEmpty().contains("chain1"))
        assertTrue(ex?.message.orEmpty().contains("chain2"))
        assertTrue(ex?.message.orEmpty().contains("chain3"))
    }
}
