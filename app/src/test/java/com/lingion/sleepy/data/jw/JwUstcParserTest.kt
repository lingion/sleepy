package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JwUstcParserTest {

    private fun loadJson(): String =
        File("src/test/resources/jw/fixtures/ustc/courses.sample.json").readText(Charsets.UTF_8)

    @Test
    fun `parses USTC courses - total count is correct`() {
        val parser = JwUstcParser(loadJson())
        val courses = parser.generateCourseList()
        // 4 activities: 1-16 全周 1 + 2-15单 1 + 2,4,6,8,10,12,14,16 离散 8 + 1-16双 1 = 11
        assertEquals(11, courses.size)
    }

    @Test
    fun `parses USTC courses - 高等数学A maps to correct fields`() {
        val courses = JwUstcParser(loadJson()).generateCourseList()
        val math = courses.first { it.name == "高等数学A" }
        assertEquals("王教授", math.teacher)
        // room: 教二楼301 (customPlace=null, 取 room)
        assertEquals("教二楼301", math.room)
        assertEquals(1, math.day)
        // lessonCode "0102" → start=1, end=2
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
        assertEquals(1, math.startWeek)
        assertEquals(16, math.endWeek)
        assertEquals(0, math.type)  // weeksStr "1-16" 无单双 → type 0
    }

    @Test
    fun `parses USTC courses - 大学物理B weekday 3 nodes 5-6 单周`() {
        val courses = JwUstcParser(loadJson()).generateCourseList()
        val phys = courses.first { it.name == "大学物理B" }
        assertEquals(3, phys.day)
        assertEquals(5, phys.startNode)
        assertEquals(6, phys.endNode)
        // 2-15单: 单周=奇数, 2 偶 → 校正为 3, 终点 15
        assertEquals(3, phys.startWeek)
        assertEquals(15, phys.endWeek)
        assertEquals(1, phys.type)  // weeksStr 含 "单" → type 1
    }

    @Test
    fun `parses USTC courses - 离散周 2,4,6,8,10,12,14,16 expands to eight entries`() {
        val courses = JwUstcParser(loadJson()).generateCourseList()
        val lab = courses.filter { it.name == "数据结构实验" }
        assertEquals(8, lab.size)
        assertEquals(5, lab.first().day)
        assertEquals(7, lab.first().startNode)
        assertEquals(8, lab.first().endNode)
        val weeks = lab.map { it.startWeek }.sorted()
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), weeks)
    }

    @Test
    fun `parses USTC courses - 双周课 type 2`() {
        val courses = JwUstcParser(loadJson()).generateCourseList()
        val eng = courses.first { it.name == "英语口语" }
        assertEquals(2, eng.day)
        assertEquals(9, eng.startNode)
        assertEquals(10, eng.endNode)
        // 1-16双: 双周=偶数, 1 奇 → 校正为 2, 终点 16
        assertEquals(2, eng.startWeek)
        assertEquals(16, eng.endWeek)
        assertEquals(2, eng.type)  // weeksStr 含 "双" → type 2
    }

    @Test
    fun `parses USTC courses - room falls back to customPlace when room null`() {
        // 英语口语 room=null, customPlace="在线" → 兜底为 customPlace
        val courses = JwUstcParser(loadJson()).generateCourseList()
        val eng = courses.first { it.name == "英语口语" }
        assertEquals("在线", eng.room)
    }

    @Test
    fun `parses USTC courses - teachers array joins with space`() {
        val json = """
        {
          "studentTableVm": {
            "activities": [
              {
                "courseName": "组合数学",
                "campus": null,
                "customPlace": null,
                "room": "A101",
                "teachers": ["张老师", "王老师"],
                "weeksStr": "1-8",
                "weekday": 4,
                "lessonCode": "0304"
              }
            ]
          }
        }
        """.trimIndent()
        val courses = JwUstcParser(json).generateCourseList()
        assertEquals(1, courses.size)
        // 多教师以空格连接 (与 wakeup/SEU 风格一致)
        assertEquals("张老师 王老师", courses[0].teacher)
    }

    @Test
    fun `parses USTC courses - confidence is high when studentTableVm anchor present`() {
        val parser = JwUstcParser(loadJson())
        assertTrue("confidence must be >= 80, was ${parser.confidence()}",
            parser.confidence() >= 80)
    }

    @Test
    fun `parses USTC courses - confidence is zero when studentTableVm missing`() {
        val parser = JwUstcParser("""{"other":{}}""")
        assertEquals(0, parser.confidence())
    }

    @Test
    fun `parses USTC courses - matchedFeatures lists studentTableVm anchor`() {
        val parser = JwUstcParser(loadJson())
        val features = parser.matchedFeatures()
        assertTrue("must contain studentTableVm anchor, was $features",
            features.any { it.contains("studentTableVm", ignoreCase = true) })
    }
}
