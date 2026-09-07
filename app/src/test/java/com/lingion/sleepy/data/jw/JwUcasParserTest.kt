package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwUcasParserTest {
    private fun htmlFixture() = javaClass.classLoader!!.getResourceAsStream("jw/fixtures/ucas/person-schedule.sample.html")!!
        .bufferedReader().use { it.readText() }

    private fun jsonFixture() = javaClass.classLoader!!.getResourceAsStream("jw/fixtures/ucas/course-time-list.sample.json")!!
        .bufferedReader().use { it.readText() }

    // ---- HTML 路径 (fallback) ----

    @Test fun `parses UCAS schedule grid and merges adjacent nodes`() {
        val courses = JwUcasParser(htmlFixture()).generateCourseList()
        assertEquals(6, courses.size)
        val theory = courses.single { it.name == "新时代中国特色社会主义理论与实践" }
        assertEquals(1, theory.day)
        assertEquals(1, theory.startNode)
        assertEquals(2, theory.endNode)
        assertEquals(JwUcasParser.PROVISIONAL_START_WEEK, theory.startWeek)
        assertEquals(JwUcasParser.PROVISIONAL_END_WEEK, theory.endWeek)
        val architecture = courses.single { it.name == "计算机体系结构" && it.day == 1 }
        assertEquals(10, architecture.startNode)
        assertEquals(11, architecture.endNode)
    }

    @Test fun `UCAS HTML parser identifies captured page shape`() {
        val parser = JwUcasParser(htmlFixture())
        assertTrue(parser.confidence() >= 90)
        assertTrue(parser.matchedFeatures().contains("href:/course/coursetime/"))
    }

    // ---- JSON 路径 (ldiex 协议契约) ----

    @Test fun `parses UCAS JSON courseTimeList with bitmap weeks and nodes`() {
        val parser = JwUcasParser(jsonFixture())
        assertEquals(95, parser.confidence())
        assertTrue(parser.matchedFeatures().contains("json:courseTimeList"))
        assertTrue(parser.matchedFeatures().contains("json:selectedCourse"))

        val courses = parser.generateCourseList()
        // 第 4 条 (courseWeek=0/courseTime=0) 应被跳过
        assertEquals(3, courses.size)

        // 全周 1-16: 新时代中国特色社会主义理论与实践, 周一 第 1-2 节, type=0
        val theory = courses.single { it.name == "新时代中国特色社会主义理论与实践" }
        assertEquals(1, theory.day)
        assertEquals(1, theory.startNode)
        assertEquals(2, theory.endNode)
        assertEquals(1, theory.startWeek)
        assertEquals(16, theory.endWeek)
        assertEquals(JwUcasParser.TYPE_DEFAULT, theory.type)
        assertEquals("教学楼A101", theory.room)

        // 单周: 计算机体系结构, 周三 第 10-11 节, type=1
        val architecture = courses.single { it.name == "计算机体系结构" }
        assertEquals(3, architecture.day)
        assertEquals(10, architecture.startNode)
        assertEquals(11, architecture.endNode)
        assertEquals(1, architecture.startWeek)
        assertEquals(15, architecture.endWeek)
        assertEquals(JwUcasParser.TYPE_ODD, architecture.type)

        // 双周: 羽毛球, 周五 第 5 节, type=2
        val badminton = courses.single { it.name == "羽毛球" }
        assertEquals(5, badminton.day)
        assertEquals(5, badminton.startNode)
        assertEquals(5, badminton.endNode)
        assertEquals(2, badminton.startWeek)
        assertEquals(16, badminton.endWeek)
        assertEquals(JwUcasParser.TYPE_EVEN, badminton.type)
    }

    @Test fun `UCAS JSON parser skips courseTimeList entries with invalid bitmap`() {
        // courseWeek=0 + courseTime=0 应被跳过, 不抛异常
        val courses = JwUcasParser(jsonFixture()).generateCourseList()
        assertTrue(courses.none { it.name == "无效条目" })
    }

    @Test fun `UCAS parser decodes dayBits to day per ldiex dictionary`() {
        // 直接用单课 courseInfo 形态 (顶层 courseTimeList) 验证字典全覆盖
        // 8 个 dayBits 都覆盖
        val samples = linkedMapOf(
            "周一" to "10",
            "周二" to "100",
            "周三" to "110",
            "周四" to "1000",
            "周五" to "1010",
            "周六" to "1100",
            "周日" to "1110",
        )
        samples.forEach { (label, dayBits) ->
            val timeInt = Integer.parseInt(dayBits + "000000000001", 2)
            val json = """{"courseTimeList":[{"courseName":"x","coursePlace":"","courseWeek":1,"courseTime":$timeInt}]}"""
            val courses = JwUcasParser(json).generateCourseList()
            assertEquals(label, 1, courses.size)
            assertEquals(label, samples.keys.indexOf(label) + 1, courses.single().day)
        }
    }

    @Test fun `UCAS parser returns empty list when source has neither JSON nor HTML anchor`() {
        val courses = JwUcasParser("nothing relevant").generateCourseList()
        assertEquals(0, courses.size)
        assertEquals(0, JwUcasParser("nothing relevant").confidence())
    }

    @Test fun `UCAS parser handles malformed JSON gracefully and falls back`() {
        // JSON 不合法但包含 selectedCourse 字符串 -> confidence 仍判 JSON 路径; 但解析时抛异常 → 回退 HTML
        // 现实里 source 通常只含 JSON 或只含 HTML, 不会两者拼接
        val malformedJson = """{"selectedCourse":{"list":[]},"courseTimeList":"not-an-array"}"""
        val courses = JwUcasParser(malformedJson).generateCourseList()
        // extractCourseTimeList 在非数组时返回 null → 走 HTML 路径, HTML 也没命中 → 空
        assertNotNull(courses)
        assertEquals(0, courses.size)
    }
}