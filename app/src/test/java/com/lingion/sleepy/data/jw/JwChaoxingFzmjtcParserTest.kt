package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 闽江师范高等专科学校 (edums.app.fzmjtc.cn) 超星教务适配契约测试。
 *
 * 数据源: 闽江师范采集包 (sleepy-adapt-20260930-153532, 2026-09-30)。
 * Syswin 变体: 个人课表走 /xsd/pkgl/xskb/sdpkkbList (需 xhid), 42 行单节粒度,
 * xjc=节号, xingqi=星期, zcstr 逗号周次串, kcmc/tmc/croommc 带 <a> 标签。
 *
 * 与吉林工商学院 queryKbForGrdb 形态差异 (本测试锁死的关键行为):
 *  1. 同一节因按周次换教室返回多行、各带不同周次串 (安全与急救处理:
 *     5..15 周仓-3-703 + 17,19 周急危重症实训室) — 必须保留周次与教室的对应关系。
 *  2. 每行带 djc 节号 (xjc), 与 rqxl 后两位一致。
 */
class JwChaoxingFzmjtcParserTest {

    private fun loadFixture(): String =
        File("src/test/resources/jw/fixtures/chaoxing/fzmjtc-grdb.json")
            .readText(Charsets.UTF_8)

    private fun parse(source: String = loadFixture()): List<JwCourse> =
        JwChaoxingParser(source).generateCourseList()

    @Test
    fun `parses rows without duplicate overlapping cards`() {
        val courses = parse()
        // 原始42行逐周核对；换教室的两个周次段分别保留。
        assertEquals(41, courses.size)
        // 关键回归: 同一(课名,星期,节次段,周次段)只允许一条 (重叠课程卡 bug)
        val keys = courses.map {
            listOf(it.name, it.day, it.startNode, it.endNode, it.startWeek, it.endWeek)
        }
        assertEquals("存在重叠课程卡", keys.size, keys.distinct().size)
    }

    @Test
    fun `english course merges periods 1-2 and splits week gap on wednesday`() {
        // 大学英语 周三 1-2 节: 周次并集 3,4,5,7..19 → 间隙拆 [3-5][7-19] 两段
        val cs = parse().filter { it.name == "大学英语(一)" && it.day == 3 }
        assertEquals(2, cs.size)
        for (c in cs) {
            assertEquals(1, c.startNode)
            assertEquals(2, c.endNode)
            assertEquals("叶月英", c.teacher)
            assertEquals("仓-3-501", c.room)
        }
        assertTrue(cs.any { it.startWeek == 3 && it.endWeek == 5 })
        assertTrue(cs.any { it.startWeek == 7 && it.endWeek == 19 })
    }

    @Test
    fun `room swap keeps the classroom attached to its original weeks`() {
        val cs = parse().filter { it.name == "安全与急救处理" && it.day == 1 && it.startNode == 3 }
        assertEquals(2, cs.size)
        val early = cs.single { it.room == "仓-3-703" }
        val late = cs.single { it.room == "仓-1-703-急危重症实训室" }
        assertEquals(5 to 15, early.startWeek to early.endWeek)
        assertEquals(17 to 19, late.startWeek to late.endWeek)
        for (c in cs) {
            assertEquals(4, c.endNode)
            assertEquals(1, c.type)
            assertEquals("叶珊", c.teacher)
        }
    }

    @Test
    fun `three-period course spans periods 5-7 on day 4`() {
        val cs = parse().filter { it.name == "思想道德与法治" && it.day == 4 }
        // 周次并集 3,6..19 → 间隙拆 [3-3][6-19] 两段, 都拉通 5-7 节
        assertEquals(2, cs.size)
        for (c in cs) {
            assertEquals(5, c.startNode)
            assertEquals(7, c.endNode)
            assertEquals("刘姝辰", c.teacher)
        }
    }

    @Test
    fun `strips anchor tags from course teacher and room`() {
        val courses = parse()
        for (c in courses) {
            assertTrue("${c.name} 含HTML", !c.name.contains('<') && !c.name.contains("javascript:"))
            assertTrue("${c.teacher} 含HTML", !c.teacher.contains('<'))
            assertTrue("${c.room} 含HTML", !c.room.contains('<'))
        }
    }

    @Test
    fun `odd-week-only course yields type 1 single week`() {
        // 书写训练与工程素养 day2: zcstr=3,5,...,17 → 单周
        val c = parse().first { it.name == "书写训练与工程素养" && it.day == 2 }
        assertEquals(1, c.type)
        assertEquals(3, c.startWeek)
        assertEquals(17, c.endWeek)
    }

    @Test
    fun `even-week-only course yields type 2 double week`() {
        // 大学生心理健康与促进 day5: zcstr=10,12,14 → 双周
        val c = parse().first { it.name.startsWith("大学生心理健康") && it.day == 5 }
        assertEquals(2, c.type)
        assertEquals(10, c.startWeek)
        assertEquals(14, c.endWeek)
    }

    @Test
    fun `non-json garbage yields empty list not crash`() {
        assertTrue(JwChaoxingParser("<html>404</html>").generateCourseList().isEmpty())
        assertTrue(JwChaoxingParser("").generateCourseList().isEmpty())
    }
}
