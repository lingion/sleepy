package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * sg_asset 通路金标:消费拾光桥 saveImportedCourses 的课程 JSON 数组
 * (字段合同 = clone scripts/types/shiguang-bridge.d.ts,拾光仓 MIT)。
 */
class JwSgAssetParserTest {

    private fun courses(json: String) = JwSgAssetParser(json).generateCourseList()

    @Test
    fun `maps contiguous week range to one weekly course`() {
        val c = courses("""[{"name":"高等数学","teacher":"张","position":"A101","day":1,"startSection":1,"endSection":2,"weeks":[1,2,3,4,5,6,7,8],"credit":4}]""")
        assertEquals(1, c.size)
        with(c[0]) {
            assertEquals("高等数学", name); assertEquals("张", teacher); assertEquals("A101", room)
            assertEquals(1, day); assertEquals(1, startNode); assertEquals(2, endNode)
            assertEquals(1, startWeek); assertEquals(8, endWeek); assertEquals(0, type)
        }
    }

    @Test
    fun `odd-only week set becomes single-week course`() {
        val c = courses("""[{"name":"实验","day":3,"startSection":5,"endSection":6,"weeks":[1,3,5,7,9]}]""")
        assertEquals(1, c.size)
        assertEquals(1, c[0].type)
        assertEquals(1, c[0].startWeek); assertEquals(9, c[0].endWeek)
    }

    @Test
    fun `even-only week set becomes double-week course`() {
        val c = courses("""[{"name":"研讨","day":5,"startSection":7,"endSection":8,"weeks":[2,4,6,8]}]""")
        assertEquals(1, c.size)
        assertEquals(2, c[0].type)
        assertEquals(2, c[0].startWeek); assertEquals(8, c[0].endWeek)
    }

    @Test
    fun `mixed non-contiguous weeks split into contiguous runs`() {
        // {1..4} ∪ {7,8} ∪ {11,12,13,14}: 三段连续区间, 全 type=0
        val weeks = (1..4).toList() + listOf(7, 8) + (11..14).toList()
        val json = """[{"name":"体育","day":2,"startSection":1,"endSection":1,"weeks":${weeks.joinToString(",", "[", "]")}}]"""
        val c = courses(json).sortedBy { it.startWeek }
        assertEquals(3, c.size)
        assertEquals(listOf(1 to 4, 7 to 8, 11 to 14), c.map { it.startWeek to it.endWeek })
        assertTrue(c.all { it.type == 0 })
    }

    @Test
    fun `custom-time entries without sections are skipped not crashed`() {
        val json = """[
          {"name":"讲座","day":6,"isCustomTime":true,"customStartTime":"19:00","customEndTime":"20:30","weeks":[1,2]},
          {"name":"正常课","day":1,"startSection":3,"endSection":4,"weeks":[1,2,3]}
        ]"""
        val c = courses(json)
        assertEquals(1, c.size)
        assertEquals("正常课", c[0].name)
    }

    @Test
    fun `dirty entries are skipped and good ones kept`() {
        val json = """[
          {"name":"缺节次","day":1,"weeks":[1,2]},
          {"name":"非法星期","day":8,"startSection":1,"endSection":2,"weeks":[1]},
          {"name":"空周次","day":2,"startSection":1,"endSection":2,"weeks":[]},
          {"name":"颠倒区间","day":3,"startSection":6,"endSection":2,"weeks":[1]},
          {"name":"好课","day":4,"startSection":1,"endSection":2,"weeks":[1,2],"teacher":"","position":""}
        ]"""
        val c = courses(json)
        assertEquals(1, c.size)
        assertEquals("好课", c[0].name)
    }

    @Test
    fun `malformed json returns empty list`() {
        assertTrue(courses("not json").isEmpty())
        assertTrue(courses("").isEmpty())
        assertTrue(courses("""{"name":"object not array"}""").isEmpty())
    }

    @Test
    fun `confidence is high for course json array and zero otherwise`() {
        assertTrue(JwSgAssetParser("""[{"name":"x","day":1,"startSection":1,"endSection":1,"weeks":[1]}]""").confidence() >= 80)
        assertEquals(0, JwSgAssetParser("<html></html>").confidence())
    }
}
