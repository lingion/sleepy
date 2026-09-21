package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwCfNewParserTest {

    private fun fixture(): String =
        javaClass.classLoader
            .getResource("jw_fixtures/cf-new/ntss_mixed.synthetic.json")!!
            .readText()

    @Test
    fun `parses direct periods and aggregates rows from full semester bucket`() {
        val courses = JwCfNewParser(fixture()).generateCourseList()

        val math = courses.single { it.name == "高等数学" }
        assertEquals(1, math.day)
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
        assertEquals(1, math.startWeek)
        assertEquals(3, math.endWeek)
        assertEquals(0, math.type)
        assertEquals("A101", math.room)
        assertEquals("张老师", math.teacher)
    }

    @Test
    fun `infers empty ps and pe from periods and folds mixed weeks`() {
        val courses = JwCfNewParser(fixture()).generateCourseList()
        val os = courses.filter { it.name == "操作系统" }.sortedBy { it.startWeek }

        assertEquals(2, os.size)
        assertEquals(2, os[0].day)
        assertEquals(1, os[0].startNode)
        assertEquals(2, os[0].endNode)
        assertEquals(1, os[0].startWeek)
        assertEquals(3, os[0].endWeek)
        assertEquals(0, os[0].type)
        assertEquals(5, os[1].startWeek)
        assertEquals(5, os[1].endWeek)
        assertEquals(0, os[1].type)
    }

    @Test
    fun `falls back to request bucket week when row zc is empty`() {
        val courses = JwCfNewParser(fixture()).generateCourseList()
        val fallback = courses.single { it.name == "逐周回退" }

        assertEquals(4, fallback.startWeek)
        assertEquals(4, fallback.endWeek)
        assertEquals(5, fallback.day)
        assertEquals(4, fallback.startNode)
    }

    @Test
    fun `maps no-room marker and skips malformed rows`() {
        val courses = JwCfNewParser(fixture()).generateCourseList()
        val sport = courses.single { it.name == "体育" }

        assertEquals("不用场地", sport.room)
        assertTrue(courses.none { it.name in setOf("", "非法星期", "无时间映射") })
    }

    @Test
    fun `invalid source and missing weeks are empty`() {
        assertTrue(JwCfNewParser("not json").generateCourseList().isEmpty())
        assertTrue(JwCfNewParser("{}").generateCourseList().isEmpty())
    }

    @Test
    fun `confidence and features require cf envelope markers`() {
        val parser = JwCfNewParser(fixture())
        assertEquals(100, parser.confidence())
        assertTrue(parser.matchedFeatures().containsAll(listOf("sleepyCfNtss", "weeks", "字段=kcmc", "字段=ps/pe")))

        val other = JwCfNewParser("{\"weeks\":[]}")
        assertEquals(0, other.confidence())
        assertTrue(other.matchedFeatures().isEmpty())
    }

    @Test
    fun `parseWeekList supports single range mixed values and invalid tokens`() {
        assertEquals(listOf(3), JwCfNewParser.parseWeekList("3"))
        assertEquals(listOf(1, 2, 3), JwCfNewParser.parseWeekList("1-3"))
        assertEquals(listOf(1, 2, 4, 6, 7), JwCfNewParser.parseWeekList("1-2,4,bad,6~7"))
        assertTrue(JwCfNewParser.parseWeekList("3-1,0,-2").isEmpty())
    }
}
