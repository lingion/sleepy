package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JwNeuParserTest {

    private fun loadJson(): String =
        File("src/test/resources/jw/fixtures/neu/courses.sample.json").readText(Charsets.UTF_8)

    @Test
    fun `parses NEU courses - total count is correct`() {
        val courses = JwNeuParser(loadJson()).generateCourseList()
        // 4 arrangedList:
        //   1-16周 (连续)            → 1 entry
        //   2-15周 (连续)            → 1 entry
        //   2,4,6,8,10,12,14,16 (离散8) → 8 entries
        //   1-16双周 (连续)          → 1 entry (但剥"双"后 1-16, type=0, 端点修正后 startWeek=2)
        assertEquals(11, courses.size)
    }

    @Test
    fun `parses NEU courses - 高等数学 maps to correct fields`() {
        val math = JwNeuParser(loadJson()).generateCourseList().first { it.name == "高等数学" }
        assertEquals(1, math.day)
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
        assertEquals("王教授", math.teacher)
        assertEquals("浑南校区一教A楼301", math.room)
        assertEquals(1, math.startWeek)
        assertEquals(16, math.endWeek)
        assertEquals(0, math.type)
    }

    @Test
    fun `parses NEU courses - 大学物理 weekday 3 nodes 5-6`() {
        val phys = JwNeuParser(loadJson()).generateCourseList().first { it.name == "大学物理" }
        assertEquals(3, phys.day)
        assertEquals(5, phys.startNode)
        assertEquals(6, phys.endNode)
        assertEquals(2, phys.startWeek)
        assertEquals(15, phys.endWeek)
    }

    @Test
    fun `parses NEU courses - 离散周 2,4,6,8,10,12,14,16 expands to eight entries`() {
        val lab = JwNeuParser(loadJson()).generateCourseList().filter { it.name == "数据结构实验" }
        assertEquals(8, lab.size)
        assertEquals(5, lab.first().day)
        assertEquals(7, lab.first().startNode)
        val weeks = lab.map { it.startWeek }.sorted()
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), weeks)
        assertTrue(lab.all { it.startWeek == it.endWeek })
    }

    @Test
    fun `parses NEU courses - 双周课 type 0 (upstream loses odd even info)`() {
        // 1-16双周: 上游协议剥"(双)", Sleepy 仍按 weekly 0 处理;
        // type=0 意味着无端点修正, startWeek=1 endWeek=16 保持原样
        val eng = JwNeuParser(loadJson()).generateCourseList().first { it.name == "英语口语" }
        assertEquals(2, eng.day)
        assertEquals(9, eng.startNode)
        assertEquals(10, eng.endNode)
        assertEquals(1, eng.startWeek)  // 无端点修正 (type=0)
        assertEquals(16, eng.endWeek)
        assertEquals(0, eng.type)  // 上游协议层丢失单/双, type 强制 0
    }

    @Test
    fun `parses NEU courses - weeksAndTeachers extraction`() {
        // teacher 从 "X-16周/王教授[主讲]" 末尾 "/" 后取, 剥 [主讲] 标记
        val math = JwNeuParser(loadJson()).generateCourseList().first { it.name == "高等数学" }
        assertEquals("王教授", math.teacher)
    }

    @Test
    fun `parses NEU courses - titleDetail fallback when only summary`() {
        // 若 titleDetail 只有 1 条 (只有 summary), 应回退到 weeksAndTeachers 提取 week 串,
        // room 用空串 (无 location 信息)
        val json = """
        {
          "datas": {
            "arrangedList": [{
              "courseName": "无地点课程",
              "dayOfWeek": 4,
              "beginSection": 1,
              "endSection": 2,
              "weeksAndTeachers": "1-8周/王老师[主讲]",
              "titleDetail": ["汇总: 1-8周 王老师"]
            }]
          }
        }
        """.trimIndent()
        val courses = JwNeuParser(json).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("无地点课程", courses[0].name)
        assertEquals("王老师", courses[0].teacher)
        assertEquals("", courses[0].room)
        assertEquals(1, courses[0].startWeek)
        assertEquals(8, courses[0].endWeek)
    }

    @Test
    fun `parses NEU courses - confidence is high when arrangedList anchor present`() {
        val parser = JwNeuParser(loadJson())
        assertTrue("confidence must be >= 80, was ${parser.confidence()}",
            parser.confidence() >= 80)
    }

    @Test
    fun `parses NEU courses - confidence is zero when arrangedList missing`() {
        val parser = JwNeuParser("""{"other":{}}""")
        assertEquals(0, parser.confidence())
    }

    @Test
    fun `parses NEU courses - matchedFeatures lists arrangedList anchor`() {
        val features = JwNeuParser(loadJson()).matchedFeatures()
        assertTrue("must contain arrangedList anchor, was $features",
            features.any { it.contains("arrangedList", ignoreCase = true) })
    }
}