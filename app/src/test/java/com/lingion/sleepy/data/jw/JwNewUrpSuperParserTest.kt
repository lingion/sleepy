package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwNewUrpSuperParserTest {

    private val fixtureJson: String by lazy {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/new-urp-super/sample-flat-array.json")
            ?: javaClass.getResourceAsStream("/jw/new-urp-super/sample-flat-array.json")
            ?: error("fixture not found")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `parse new urp flat-array bitmap variant`() {
        val parser = JwNewUrpSuperParser(fixtureJson)
        val courses = parser.generateCourseList()

        // 5 entries, each with single id (no nested timeAndPlaceList expansion), so 5 courses total
        assertEquals(5, courses.size)

        // 1. 高等数学: skxq=1 (周一), skjc=1, cxjc=2 → 1-2节, skzc 全部 1 (前20周) → startWeek=1, endWeek=20, type=0
        val c1 = courses[0]
        assertEquals("高等数学", c1.name)
        assertEquals("张教授", c1.teacher)
        assertEquals("主教学楼A301", c1.room)
        assertEquals(1, c1.day)
        assertEquals(1, c1.startNode)
        assertEquals(2, c1.endNode)
        assertEquals(1, c1.startWeek)
        assertEquals(20, c1.endWeek)
        assertEquals(0, c1.type)

        // 2. 线性代数: skxq=2 (周二), skjc=3, cxjc=2 → 3-4节, skzc 1010... → startWeek=1, endWeek=19, type=1 (单周)
        val c2 = courses[1]
        assertEquals("线性代数", c2.name)
        assertEquals("李老师", c2.teacher)
        assertEquals("主教学楼A302", c2.room)
        assertEquals(2, c2.day)
        assertEquals(3, c2.startNode)
        assertEquals(4, c2.endNode)
        assertEquals(1, c2.startWeek)
        assertEquals(19, c2.endWeek)
        assertEquals(1, c2.type)

        // 3. 数据结构: skxq=3 (周三), skjc=5, cxjc=3 → 5-7节, skzc 前15周 → startWeek=1, endWeek=15, type=0
        val c3 = courses[2]
        assertEquals("数据结构", c3.name)
        assertEquals("王教授", c3.teacher)
        assertEquals("信息楼B205", c3.room)
        assertEquals(3, c3.day)
        assertEquals(5, c3.startNode)
        assertEquals(7, c3.endNode)
        assertEquals(1, c3.startWeek)
        assertEquals(15, c3.endWeek)
        assertEquals(0, c3.type)

        // 4. 英语: skxq=4 (周四), skjc=11, cxjc=1 → 11节, skzc 前14周 → type=0
        val c4 = courses[3]
        assertEquals("英语", c4.name)
        assertEquals("外教", c4.teacher)
        assertEquals("文科楼C101", c4.room)
        assertEquals(4, c4.day)
        assertEquals(11, c4.startNode)
        assertEquals(11, c4.endNode)
        assertEquals(1, c4.startWeek)
        assertEquals(14, c4.endWeek)
        assertEquals(0, c4.type)

        // 5. 体育: skxq=5 (周五), skjc=9, cxjc=1 → 9节, skzc 前10周 → type=0
        val c5 = courses[4]
        assertEquals("体育", c5.name)
        assertEquals("教练", c5.teacher)
        assertEquals("体育馆操场", c5.room)
        assertEquals(5, c5.day)
        assertEquals(9, c5.startNode)
        assertEquals(9, c5.endNode)
        assertEquals(1, c5.startWeek)
        assertEquals(10, c5.endWeek)
        assertEquals(0, c5.type)
    }

    @Test
    fun `confidence and matched features`() {
        val parser = JwNewUrpSuperParser(fixtureJson)
        val confidence = parser.confidence()
        assertTrue("expected confidence > 0 for full anchor set, got $confidence", confidence > 0)

        val features = parser.matchedFeatures()
        assertTrue("skzc should be in matchedFeatures", "skzc" in features)
        assertTrue("id should be in matchedFeatures", "id" in features)
        assertTrue("kcm should be in matchedFeatures", "kcm" in features)
    }

    @Test
    fun `empty input returns empty list`() {
        val parser = JwNewUrpSuperParser("")
        val courses = parser.generateCourseList()
        assertNotNull(courses)
        assertEquals(0, courses.size)
    }

    @Test
    fun `garbage input returns empty list without crashing`() {
        val parser = JwNewUrpSuperParser("<html><body>not json</body></html>")
        val courses = parser.generateCourseList()
        assertEquals(0, courses.size)
    }
}