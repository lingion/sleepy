package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 博雅研究生平台解析器测试。
 *
 * 数据源: 燕山大学研究生平台 (yjsxt.ysu.edu.cn/pp, 2026-09-06)。
 * byStudent 逐周实采 (周 1..19 并集 390 行 — 无参请求实测为不完整子集)
 * 按 5 门课抽周裁剪至 56 行, 期望值由真实行预演得出, 并与页面渲染 DOM
 * (第 2 周视图) 逐格比对通过。研究生集中授课特点: 同课不同周可能落在
 * 不同节次/教室 (材料与化工现代研究方法 day6 第 2 周占 5-8 节、第 5-7 周
 * 占 5-6 节、第 6 周另占 3-4 节), 等周次集合合并规则把它们拆成独立课块。
 */
class JwBoyaPpParserTest {

    private fun loadFixture(): String =
        File("src/test/resources/jw/fixtures/boya_pp/ysu-2026-2027-1.json")
            .readText(Charsets.UTF_8)

    private fun parse(source: String = loadFixture()): List<JwCourse> =
        JwBoyaPpParser(source).generateCourseList()

    @Test
    fun `parses 56 rows into 18 course blocks`() {
        assertEquals(18, parse().size)
    }

    @Test
    fun `consecutive single-period rows merge into continuous block`() {
        // 心理健康教育专题: day2 节 5/6/7/8 各一行 (week 2) → 5-8 连堂
        val c = parse().first { it.name == "心理健康教育专题" }
        assertEquals(2, c.day)
        assertEquals(5, c.startNode)
        assertEquals(8, c.endNode)
        assertEquals(2, c.startWeek)
        assertEquals(2, c.endWeek)
        assertEquals(0, c.type)
        assertEquals("李亚蕾", c.teacher)
        assertEquals("西(四)201多媒体", c.room)
    }

    @Test
    fun `gapped week set splits into two runs of same slot`() {
        // 新时代 (weeks 裁剪为 2,3,10): day1 5-6 → [2-3] + [10] 两段
        val cs = parse().filter { it.name == "新时代中国特色社会主义理论与实践" && it.day == 1 }
        assertEquals(2, cs.size)
        val cont = cs.first { it.startWeek == 2 }
        assertEquals(3, cont.endWeek)
        assertEquals(0, cont.type)
        val gap = cs.first { it.startWeek == 10 }
        assertEquals(10, gap.endWeek)
        assertEquals(5 to 6, gap.startNode to gap.endNode)
        assertEquals("何茜曦", cs[0].teacher)
        assertEquals("（里）J207多媒体", cs[0].room)
    }

    @Test
    fun `same slot across different weeks stays split when node ranges differ`() {
        // 集中授课: 材料 day6 第2周占 5-8 节(拆 5-6/7-8 两块), 第5-7周占 5-6 节, 第6周另占 3-4 节
        val cs = parse().filter { it.name == "材料与化工现代研究方法" && it.day == 6 }
        assertEquals(5, cs.size)
        assertTrue(cs.any { it.startNode == 3 && it.endNode == 4 && it.startWeek == 6 && it.endWeek == 6 })
        assertTrue(cs.any { it.startNode == 5 && it.endNode == 6 && it.startWeek == 2 && it.endWeek == 2 })
        assertTrue(cs.any { it.startNode == 5 && it.endNode == 6 && it.startWeek == 5 && it.endWeek == 7 })
        assertTrue(cs.any { it.startNode == 7 && it.endNode == 8 && it.startWeek == 2 && it.endWeek == 2 })
        assertTrue(cs.any { it.startNode == 7 && it.endNode == 8 && it.startWeek == 5 && it.endWeek == 5 })
        assertEquals("田克松", cs[0].teacher)
    }

    @Test
    fun `same course different rooms stay separate blocks`() {
        // 高等催化原理: day3 9-12 @AD401 与 day7 9-12 @J105, weeks 裁剪为 2,7 → 各拆 2 段
        val cs = parse().filter { it.name == "高等催化原理" }
        assertEquals(4, cs.size)
        val d3 = cs.filter { it.day == 3 }
        assertEquals(2, d3.size)
        assertTrue(d3.all { it.startNode == 9 && it.endNode == 12 && it.room == "（里）AD401" })
        val d7 = cs.filter { it.day == 7 }
        assertEquals(2, d7.size)
        assertTrue(d7.all { it.room == "（里）J105" })
        assertEquals("张亚茹", d3[0].teacher)
    }

    @Test
    fun `suspension rows are excluded`() {
        assertTrue(parse().none { it.name == "测试停课课" })
    }

    @Test
    fun `string whichWeek is tolerated and empty room falls back to classroomCode`() {
        val c = parse().first { it.name == "测试字符串周次课" }
        assertEquals(4, c.day)
        assertEquals(3, c.startNode)
        assertEquals(6, c.startWeek)
        assertEquals("999888", c.room)
        assertEquals("李四", c.teacher)
    }

    @Test
    fun `teacher names are joined and deduplicated`() {
        val cs = parse().filter { it.name == "学科前沿专题" }
        assertTrue(cs.isNotEmpty())
        // 真实行: 单教师 钟金玲, 两个课块教师一致
        for (c in cs) {
            assertEquals("钟金玲", c.teacher)
        }
    }

    @Test
    fun `raw bare-array form parses like wrapped form`() {
        // byStudent 不带信封时 data 就是裸数组 — 同一批行应得到同样结果
        val bare = extractRowsForTest(loadFixture())
        assertEquals(18, JwBoyaPpParser(bare).generateCourseList().size)
    }

    @Test
    fun `code-envelope form parses like fetch form`() {
        // 完整 {code,data} 信封形态 (fetch JS 已剥壳, 兜底容忍)
        val envelope = """{"code":200,"message":"操作成功","data":${extractRowsForTest(loadFixture())}}"""
        assertEquals(18, JwBoyaPpParser(envelope).generateCourseList().size)
    }

    @Test
    fun `non-json garbage yields empty list not crash`() {
        assertTrue(JwBoyaPpParser("<html>404</html>").generateCourseList().isEmpty())
        assertTrue(JwBoyaPpParser("").generateCourseList().isEmpty())
        assertTrue(JwBoyaPpParser("""{"code":200,"data":[]}""").generateCourseList().isEmpty())
    }

    @Test
    fun `confidence is high only for boya anchors`() {
        assertTrue(JwBoyaPpParser(loadFixture()).confidence() >= 80)
        assertEquals(0, JwBoyaPpParser("""{"kbList":[]}""").confidence())
    }

    /** 从 fixture 抽 rows 数组原文 (供裸数组/信封形态变体测试) */
    private fun extractRowsForTest(source: String): String {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(source)
        return (root as kotlinx.serialization.json.JsonObject)["rows"]!!.toString()
    }
}
