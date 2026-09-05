package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 超星学习通/ChaoXing 综合教务解析器测试。
 *
 * 数据源: 吉林工商学院采集包 (sleepy-adapt-0905-215140, 2026-09-05)。
 * queryKbForGrdb 个人课表 JSON, 22 行单节粒度行, xjc=节号, xingqi=星期,
 * zcstr 逗号周次串, kcmc/tmc/croommc 需剥离 <a> 标签。
 */
class JwChaoxingParserTest {

    private fun loadFixture(): String =
        File("src/test/resources/jw/fixtures/chaoxing/jlbtc-grdb.json")
            .readText(Charsets.UTF_8)

    private fun parse(source: String = loadFixture()): List<JwCourse> =
        JwChaoxingParser(source).generateCourseList()

    @Test
    fun `parses 22 single-period rows into 11 merged continuous-block courses`() {
        val courses = parse()
        // 22 行单节 → 相邻同课合并连堂后 11 条
        assertEquals(11, courses.size)
    }

    @Test
    fun `monday algorithm course merges periods 1-2 with weeks 1-12`() {
        val c = parse().first { it.name == "算法设计与分析" && it.day == 1 && it.startNode == 1 }
        assertEquals(2, c.endNode)
        assertEquals(1, c.startWeek)
        assertEquals(12, c.endWeek)
        assertEquals(0, c.type) // 1..12 连续 → 每周
        assertEquals("王锐", c.teacher)
        assertEquals("三教337", c.room)
    }

    @Test
    fun `strips anchor tags from course teacher and room`() {
        val courses = parse()
        // 采集包班级表里 kcmc/tmc/croommc 带openKckb等onclick链接; 个人表行也可能带 —
        // 解析后任何字段都不应含 '<' 或 "javascript:"
        for (c in courses) {
            assertTrue("${c.name} 含HTML", !c.name.contains('<') && !c.name.contains("javascript:"))
            assertTrue("${c.teacher} 含HTML", !c.teacher.contains('<'))
            assertTrue("${c.room} 含HTML", !c.room.contains('<'))
        }
    }

    @Test
    fun `pe course with empty room keeps blank room`() {
        val courses = parse()
        val pe = courses.first { it.name == "体育3" }
        assertEquals("", pe.room)
        assertEquals("徐义山", pe.teacher)
        assertEquals(2, pe.day)
        assertEquals(16, pe.endWeek)
    }

    @Test
    fun `thursday linux course spans periods 9-10`() {
        val c = parse().first { it.name == "Linux操作系统" && it.day == 4 }
        assertEquals(9, c.startNode)
        assertEquals(10, c.endNode)
        assertEquals(14, c.endWeek)
    }

    @Test
    fun `odd-week-only course yields type 1 single week`() {
        // 构造: 周次串全是奇数 → 单周
        val src = """{"xnxq":"2026-2027-1","rows":[
            {"kcmc":"测试单周课","xjc":"3","xingqi":3,"zcstr":"1,3,5,7,9","tmc":"师","croommc":"室"}
        ]}"""
        val c = JwChaoxingParser(src).generateCourseList().single()
        assertEquals(1, c.type)
        assertEquals(1, c.startWeek)
        assertEquals(9, c.endWeek)
    }

    @Test
    fun `even-week-only course yields type 2 double week`() {
        val src = """{"xnxq":"2026-2027-1","rows":[
            {"kcmc":"测试双周课","xjc":"5","xingqi":5,"zcstr":"2,4,6,8,10,12","tmc":"","croommc":""}
        ]}"""
        val c = JwChaoxingParser(src).generateCourseList().single()
        assertEquals(2, c.type)
        assertEquals(2, c.startWeek)
        assertEquals(12, c.endWeek)
    }

    @Test
    fun `gapped week list splits into segments with same type`() {
        // 1,2,3,8,9 → 两段 [1-3] [8-9], 每周
        val src = """{"xnxq":"2026-2027-1","rows":[
            {"kcmc":"分段课","xjc":"1","xingqi":1,"zcstr":"1,2,3,8,9","tmc":"","croommc":""}
        ]}"""
        val cs = JwChaoxingParser(src).generateCourseList()
        assertEquals(2, cs.size)
        assertEquals(1 to 3, cs[0].startWeek to cs[0].endWeek)
        assertEquals(8 to 9, cs[1].startWeek to cs[1].endWeek)
    }

    @Test
    fun `missing xjc falls back to rqxl remainder`() {
        // xjc 缺位 → rqxl=403 → 周四第3节
        val src = """{"xnxq":"2026-2027-1","rows":[
            {"kcmc":"节次回退课","rqxl":"403","zcstr":"1-16","tmc":"","croommc":""}
        ]}"""
        val c = JwChaoxingParser(src).generateCourseList().single()
        assertEquals(4, c.day)
        assertEquals(3, c.startNode)
        assertEquals(3, c.endNode)
    }

    @Test
    fun `zcstr dash range form expands like comma form`() {
        // 部分部署 zcstr 可能是 "1-16" 区间形态
        val src = """{"xnxq":"2026-2027-1","rows":[
            {"kcmc":"区间周次课","xjc":"2","xingqi":2,"zcstr":"1-16","tmc":"","croommc":""}
        ]}"""
        val c = JwChaoxingParser(src).generateCourseList().single()
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
    }

    @Test
    fun `rows without weekday are skipped without crashing`() {
        val src = """{"xnxq":"2026-2027-1","rows":[
            {"kcmc":"无星期课","zcstr":"1,2"},
            {"kcmc":"正常课","xjc":"1","xingqi":1,"zcstr":"1,2","tmc":"","croommc":""}
        ]}"""
        val cs = JwChaoxingParser(src).generateCourseList()
        assertEquals(1, cs.size)
        assertEquals("正常课", cs[0].name)
    }

    @Test
    fun `non-json garbage yields empty list not crash`() {
        assertTrue(JwChaoxingParser("<html>404</html>").generateCourseList().isEmpty())
        assertTrue(JwChaoxingParser("").generateCourseList().isEmpty())
    }
}
