package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 东南大学 SEUTimetable 协议 (正方 URP 系 JSON, sakimidare/SEUTimetable Apache-2.0 同源) 解析器测试。
 *
 * 数据：src/test/resources/jw/fixtures/seu/courses.sample.json
 * 该 JSON 形态取自 SEUTimetable TableParserUtils.parseCoursesFromJsonArray 输入数组 (用户粘入)。
 *
 * 字段契约（与 SEUTimetable TableParserUtils.kt 对齐）：
 *   KCM  课程名   → name
 *   SKJS 教师     → teacher
 *   JASMC 教室    → room
 *   SKXQ 星期(1-7) → day
 *   KSJC 起始节   → startNode
 *   JSJC 结束节   → endNode
 *   ZCMC 周次串   → 范围/单双周 (parseWeekRange 自写)
 *   KCH  课程号   → room 末尾附加（note-like；Sleepy 不单存 note 字段）
 *   JXBQH 教学班群号 → 忽略
 *
 * v1 限制（详见 JwSeuParser KDoc）：
 *   1. 周次按 ZCMC 串直接解析 (1-16, 1-10周(单), 2,4,6周) — 一行多种周次产多条 JwCourse
 *   2. 无 bitmap (与 EAMS5 不同, zju-ical 的"全表一次性"形态)
 *   3. KSJC/JSJC 即节点号 (同 zju-ical 形态, 不做时间→节次推断)
 */
class JwSeuParserTest {

    private fun loadJson(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("jw/fixtures/seu/courses.sample.json")
        assertNotNull("测试资源 courses.sample.json 应存在", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    private fun parse() = JwSeuParser(loadJson()).generateCourseList()

    @Test
    fun `parses SEU courses - total count is correct`() {
        val courses = parse()
        println("SEU 样张解析出 ${courses.size} 个 JwCourse:")
        courses.forEach {
            println("  ${it.name} 周${it.day} node${it.startNode}-${it.endNode} 周${it.startWeek}-${it.endWeek}(t${it.type}) 老师=${it.teacher} 教室=${it.room}")
        }
        // 4 个示例课程 (含单/双周展开); 总数 = 1-16周 1 + 2-15单 1 + 2,4,6周 3 + 1-16双 1 = 6
        assertEquals("期望 6 条 JwCourse", 6, courses.size)
    }

    @Test
    fun `parses SEU courses - 高等数学 maps to correct fields`() {
        val courses = parse()
        val c = courses.firstOrNull { it.name == "高等数学" }
        assertNotNull("应找到 高等数学", c)
        c!!
        assertEquals("周1", 1, c.day)
        assertEquals("KSJC=1", 1, c.startNode)
        assertEquals("JSJC=2", 2, c.endNode)
        assertEquals("ZCMC=1-16 → startWeek=1", 1, c.startWeek)
        assertEquals("endWeek=16", 16, c.endWeek)
        assertEquals("type=0 (每周)", 0, c.type)
        assertEquals("老师", "张老师", c.teacher)
        assertEquals("教室", "九龙湖A楼301", c.room)
    }

    @Test
    fun `parses SEU courses - 大学物理 weekday 3 nodes 5-6`() {
        val courses = parse()
        val c = courses.firstOrNull { it.name == "大学物理" }
        assertNotNull(c)
        c!!
        assertEquals("SKXQ=3", 3, c.day)
        assertEquals("KSJC=5", 5, c.startNode)
        assertEquals("JSJC=6", 6, c.endNode)
        assertEquals("ZCMC=2-15周(单) → 单周段首=3 (2 是偶数不在单周集合)", 3, c.startWeek)
        assertEquals("endWeek=15", 15, c.endWeek)
        assertEquals("type=1 (单周)", 1, c.type)
    }

    @Test
    fun `parses SEU courses - 离散周 2,4,6 expands to three entries`() {
        // 实验课 ZCMC="2,4,6周" — 应产出 3 条 JwCourse (type=0 每周)
        val courses = parse()
        val lab = courses.filter { it.name == "数据结构实验" }
        assertEquals("离散周展开为 3 条", 3, lab.size)
        assertEquals("week 2", 2, lab[0].startWeek)
        assertEquals("week 4", 4, lab[1].startWeek)
        assertEquals("week 6", 6, lab[2].startWeek)
        for (c in lab) {
            assertEquals("实验室都是同一天", 5, c.day)
            assertEquals("实验室都是 7-8 节", 7, c.startNode)
            assertEquals("实验室都是 7-8 节", 8, c.endNode)
        }
    }

    @Test
    fun `parses SEU courses - 双周课 type 2`() {
        val courses = parse()
        val c = courses.firstOrNull { it.name == "英语口语" }
        assertNotNull(c)
        c!!
        assertEquals("type=2 (双周)", 2, c.type)
        assertEquals("ZCMC=1-16周(双) → 双周段首=2", 2, c.startWeek)
        assertEquals("末双周 16", 16, c.endWeek)
    }

    @Test
    fun `parses SEU courses - null or missing fields tolerate`() {
        // 缺字段: SKJS=null, JASMC="null" 字串
        val parser = JwSeuParser("""
            [
              {
                "KCM": "测试课",
                "SKXQ": 4,
                "KSJC": 3,
                "JSJC": 4,
                "ZCMC": "1-8周",
                "SKJS": null,
                "JASMC": "null"
              }
            ]
        """.trimIndent())
        val courses = parser.generateCourseList()
        assertEquals(1, courses.size)
        assertEquals("null teacher → ''", "", courses[0].teacher)
        assertEquals("字符串 'null' room → ''", "", courses[0].room)
    }

    @Test
    fun `confidence is high when JSON shape matches`() {
        val p = JwSeuParser(loadJson())
        assertTrue("confidence 应 >= 80", p.confidence() >= 80)
    }

    @Test
    fun `matchedFeatures lists KCM anchor`() {
        val p = JwSeuParser(loadJson())
        val feats = p.matchedFeatures()
        assertTrue("应含 KCM 字段", feats.any { it.contains("KCM") })
    }
}
