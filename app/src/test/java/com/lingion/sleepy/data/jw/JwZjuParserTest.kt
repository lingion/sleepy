package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JwZjuParserTest {

    private fun loadJson(): String =
        File("src/test/resources/jw/fixtures/zju/courses.sample.json").readText(Charsets.UTF_8)

    @Test
    fun `parses ZJU courses - total count is correct`() {
        val parser = JwZjuParser(loadJson())
        val courses = parser.generateCourseList()
        // 4 rows: 1-16周(全)1 + 2-15周(单)1 + 2,4,6,8,10,12,14,16 8 + 1-16周(双)1 = 11
        assertEquals(11, courses.size)
    }

    @Test
    fun `parses ZJU courses - 高等数学 maps to correct fields`() {
        val courses = JwZjuParser(loadJson()).generateCourseList()
        val math = courses.first { it.name == "高等数学" }
        assertEquals("王教授", math.teacher)
        assertEquals("紫金港东1A-301", math.room)
        assertEquals(1, math.day)
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
        assertEquals(1, math.startWeek)
        assertEquals(16, math.endWeek)
        assertEquals(0, math.type)  // dsz=2 → type 0 (每周)
    }

    @Test
    fun `parses ZJU courses - 大学物理 weekday 3 nodes 5-6 单周`() {
        val courses = JwZjuParser(loadJson()).generateCourseList()
        val phys = courses.first { it.name == "大学物理" }
        assertEquals(3, phys.day)
        assertEquals(5, phys.startNode)
        assertEquals(6, phys.endNode)
        // 单周=奇数, 2-15(单) → 起点 3 (2 偶 → 校正为 3), 终点 15
        assertEquals(3, phys.startWeek)
        assertEquals(15, phys.endWeek)
        assertEquals(1, phys.type)  // dsz=0 → type 1 (单周)
    }

    @Test
    fun `parses ZJU courses - 离散周 2,4,6,8,10,12,14,16 expands to eight entries`() {
        val courses = JwZjuParser(loadJson()).generateCourseList()
        val lab = courses.filter { it.name == "数据结构实验" }
        assertEquals(8, lab.size)
        assertEquals(5, lab.first().day)
        assertEquals(7, lab.first().startNode)
        // 离散周每周为一段
        val weeks = lab.map { it.startWeek }.sorted()
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), weeks)
    }

    @Test
    fun `parses ZJU courses - 双周课 type 2`() {
        val courses = JwZjuParser(loadJson()).generateCourseList()
        val eng = courses.first { it.name == "英语口语" }
        assertEquals(2, eng.day)
        assertEquals(9, eng.startNode)
        assertEquals(10, eng.endNode)
        // 双周=偶数, 1-16(双) → 起点 2 (1 奇 → 校正为 2), 终点 16
        assertEquals(2, eng.startWeek)
        assertEquals(16, eng.endWeek)
        assertEquals(2, eng.type)  // dsz=1 → type 2 (双周)
    }

    @Test
    fun `parses ZJU courses - teacher falls back when missing in kcb`() {
        // 实际数据中 kcb 长度不足 4 段时不应崩
        val json = """
        {
          "kbList": [
            {
              "xkkh": "TEST0000000000000001",
              "xqj": "4",
              "dsz": "2",
              "djj": "1",
              "skcd": "2",
              "kcb": "微积分<br>1-8周<br> ",
              "xxq": "秋冬"
            }
          ]
        }
        """.trimIndent()
        val courses = JwZjuParser(json).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("微积分", courses[0].name)
        // teacher 段为空/缺失 → 兜底空字符串
        assertEquals("", courses[0].teacher)
    }

    @Test
    fun `parses ZJU courses - confidence is high when kbList anchor present`() {
        val parser = JwZjuParser(loadJson())
        assertTrue("confidence must be >= 80, was ${parser.confidence()}",
            parser.confidence() >= 80)
    }

    @Test
    fun `parses ZJU courses - confidence is zero when kbList missing`() {
        val parser = JwZjuParser("""{"other":[]}""")
        assertEquals(0, parser.confidence())
    }

    @Test
    fun `parses ZJU courses - matchedFeatures lists kbList anchor`() {
        val parser = JwZjuParser(loadJson())
        val features = parser.matchedFeatures()
        assertTrue("must contain kbList anchor, was $features",
            features.any { it.contains("kbList", ignoreCase = true) })
    }
}
