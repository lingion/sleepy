package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwNwpuPostParserTest {

    private val fixtureHtml: String by lazy {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/nwpu/sample-table-1.html")
            ?: javaClass.getResourceAsStream("/jw/nwpu/sample-table-1.html")
            ?: error("fixture not found")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `parse nwpu graduate schedule sample-table-1 table`() {
        val parser = JwNwpuPostParser(fixtureHtml)
        val courses = parser.generateCourseList()

        // 3 entries in table:
        // CP001: 2 time slots (Mon 上1-2 -> 1,2; Wed 中1-2 -> base 4 -> 5,6)
        // NS002: 2 time slots (Tue 下1-2 -> base 6 -> 7,8; Thu 晚1-3 -> base 10 -> 11,13)
        // AE003: 1 time slot (Fri 上3-4 -> 3,4)
        // Total = 5 course segments
        assertEquals(5, courses.size)

        // 1. CP001 Slot 1: Mon 上1-2
        val c1 = courses[0]
        assertEquals("高等计算机体系结构(2024硕01班)", c1.name)
        assertEquals(1, c1.day)
        assertEquals(1, c1.startNode)
        assertEquals(2, c1.endNode)
        assertEquals(1, c1.startWeek)
        assertEquals(16, c1.endWeek)
        assertEquals(0, c1.type)
        assertEquals("教学西楼D 101", c1.room)
        assertEquals("张老师", c1.teacher)

        // 2. CP001 Slot 2: Wed 中1-2 (base 4 + 1..2 -> nodes 5..6)
        val c2 = courses[1]
        assertEquals("高等计算机体系结构(2024硕01班)", c2.name)
        assertEquals(3, c2.day)
        assertEquals(5, c2.startNode)
        assertEquals(6, c2.endNode)
        assertEquals("实验大楼B 204", c2.room)

        // 3. NS002 Slot 1: Tue 下1-2 (base 6 + 1..2 -> nodes 7..8, double week)
        val c3 = courses[2]
        assertEquals("应用密码学(2024硕02班)", c3.name)
        assertEquals(2, c3.day)
        assertEquals(7, c3.startNode)
        assertEquals(8, c3.endNode)
        assertEquals(2, c3.startWeek)
        assertEquals(17, c3.endWeek)
        assertEquals(2, c3.type) // 双周
        assertEquals("长安校区教学东楼A 201", c3.room)

        // 4. NS002 Slot 2: Thu 晚1-3 (base 10 + 1..3 -> nodes 11..13, single week)
        val c4 = courses[3]
        assertEquals("应用密码学(2024硕02班)", c4.name)
        assertEquals(4, c4.day)
        assertEquals(11, c4.startNode)
        assertEquals(13, c4.endNode)
        assertEquals(1, c4.startWeek)
        assertEquals(15, c4.endWeek)
        assertEquals(1, c4.type) // 单周
        assertEquals("友谊校区毅字楼 302", c4.room)

        // 5. AE003: Fri 上3-4 (nodes 3..4, single week 第5周)
        val c5 = courses[4]
        assertEquals("飞行器动力学", c5.name) // 没有班级名称时直接为课程名
        assertEquals(5, c5.day)
        assertEquals(3, c5.startNode)
        assertEquals(4, c5.endNode)
        assertEquals(5, c5.startWeek)
        assertEquals(5, c5.endWeek)
        assertEquals(0, c5.type)
        assertEquals("新楼 401", c5.room)
    }

    @Test
    fun `confidence and matched features`() {
        val parser = JwNwpuPostParser(fixtureHtml)
        assertTrue(parser.confidence() >= 90)
        assertTrue(parser.matchedFeatures().contains("id:sample-table-1"))
        assertTrue(parser.matchedFeatures().contains("col:上课时间"))
    }
}
