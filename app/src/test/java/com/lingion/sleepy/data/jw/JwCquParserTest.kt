package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JwCquParser 单元测试 — 重庆大学 my.cqu.edu.cn my-table-detail JSON。
 *
 * 数据：src/test/resources/my_table_cqu.json（按 my-table-detail 真实响应形状构造的样张）。
 * 不依赖 Android Context / 数据库 / 模拟器，纯 JVM 跑。
 *
 * 字段契约（my-table-detail classTimetableVOList 行）：
 *   courseName → name
 *   instructorName "张三-数学与统计学院" → teacher 取首个 '-' 前段
 *   position ?: roomName → room
 *   weekDay → day
 *   periodFormat "3-4" / "5" → startNode/endNode（单值时起止相同）
 *   teachingWeek "1111…0" bitmap → 周次段（复用 SKZC 同一套压缩规则）
 *   wholeWeekOccupy=true → 整周课（实习/军训），照常输出节次段
 *
 * 预期（已用 Python 对同份数据交叉验证）：
 *   - 6 行 → 6 个 JwCourse（大学物理双周 bitmap 压缩成 1 个 type=2 段，不拆行）
 *   - 高等数学Ⅰ-1：周1 1-2节 1-17周(每周)
 *   - 大学物理：周3 3-4节 双周(type=2, 2/4/…/16 压缩成 2-16)
 *   - 体育（三）：周5 6-7节 7-24周
 *   - 金工实习：wholeWeekOccupy=true 照常出 17-20 周 1 节段
 */
class JwCquParserTest {

    private fun loadJson(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("my_table_cqu.json")
        assertNotNull("测试资源 my_table_cqu.json 应存在", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    private fun parse() = JwCquParser(loadJson()).generateCourseList()

    @Test
    fun `parses CQU schedule - total count`() {
        val courses = parse()
        assertEquals("6 行压缩后应为 6 个 JwCourse", 6, courses.size)
    }

    @Test
    fun `field mapping - 高等数学`() {
        val math = parse().first { it.name == "高等数学Ⅰ-1" }
        assertEquals("张三", math.teacher)               // instructorName 首个 '-' 前段
        assertEquals("A区第一教学楼101", math.room)
        assertEquals(1, math.day)
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
        assertEquals(1, math.startWeek)
        assertEquals(17, math.endWeek)
        assertEquals(0, math.type)
    }

    @Test
    fun `even-week compression - 大学物理`() {
        val physics = parse().filter { it.name == "大学物理" }
        assertEquals("双周 bitmap 应压缩成 1 个 type=2 段", 1, physics.size)
        val p = physics.single()
        assertEquals(2, p.startWeek)
        assertEquals(16, p.endWeek)
        assertEquals(2, p.type)
        assertEquals(3, p.day)
    }

    @Test
    fun `late-week course - 体育三`() {
        val pe = parse().first { it.name == "体育（三）" }
        assertEquals(5, pe.day)
        assertEquals(6, pe.startNode)
        assertEquals(7, pe.endNode)
        assertEquals(7, pe.startWeek)
        assertEquals(24, pe.endWeek)
    }

    @Test
    fun `teacher fallback strips multi-dash - 电路与电子`() {
        val cct = parse().first { it.name == "电路与电子Ⅱ" }
        assertEquals("多 '-' 教师串只取第一段", "赵六", cct.teacher)
        assertEquals(4, cct.day)
        assertEquals(3, cct.startNode)
        assertEquals(4, cct.endNode)
    }

    @Test
    fun `whole-week course - 金工实习`() {
        val gx = parse().first { it.name == "金工实习" }
        assertEquals(17, gx.startWeek)
        assertEquals(20, gx.endWeek)
        assertEquals(1, gx.startNode)
        assertEquals(1, gx.endNode)
        assertEquals("工程培训中心", gx.room)
    }

    @Test
    fun `null instructor falls back to empty teacher - 军事理论`() {
        val mil = parse().first { it.name == "军事理论" }
        assertEquals("", mil.teacher)
        assertEquals("", mil.room)
        assertEquals(10, mil.startNode)
        assertEquals(11, mil.endNode)
    }

    @Test
    fun `confidence anchors on my-table marker`() {
        val p = JwCquParser(loadJson())
        assertTrue("classTimetableVOList 锚点应有置信度", p.confidence() >= 80)
        assertTrue(p.matchedFeatures().isNotEmpty())
        assertEquals(0, JwCquParser("random text").confidence())
    }

    @Test
    fun `empty and malformed input - graceful`() {
        assertEquals(0, JwCquParser("").let { runCatching { it.generateCourseList() }.getOrDefault(emptyList()) }.size)
        assertEquals(0, JwCquParser("not json").let { runCatching { it.generateCourseList() }.getOrDefault(emptyList()) }.size)
        assertEquals(0, JwCquParser("""{"classTimetableVOList":null}""").generateCourseList().size)
    }

    @Test
    fun `detectProtocolFromUrl routes my cqu edu cn to CQU type`() {
        assertEquals("cqu", JwImportViewModel.detectProtocolFromUrlForTest("https://my.cqu.edu.cn/"))
        assertEquals("cqu", JwImportViewModel.detectProtocolFromUrlForTest("https://my.cqu.edu.cn/enroll/Home"))
        assertEquals("URL 含 cqu.edu.cn 子域但非门户 host 不得误判",
            null, JwImportViewModel.detectProtocolFromUrlForTest("https://jw.cqu.edu.cn/"))
    }

    @Test
    fun `registry dispatches CQU json to JwCquParser`() {
        val (courses, attempts) = JwParserRegistry.selectBest(loadJson(), "cqu")
        assertEquals(6, courses.size)
        val cquAttempt = attempts.firstOrNull { it.type == "cqu" }
        assertEquals("cqu 行必须被尝试", "cqu", cquAttempt?.type)
        assertTrue("CQU parser 置信度应 >= 80", (cquAttempt?.confidence ?: 0) >= 80)
    }

    @Test
    fun `CQU entry in schools json is registered and supported`() {
        val text = javaClass.classLoader?.getResourceAsStream("jw/schools.json")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        assertNotNull(text)
        val cqu = JwImportViewModel.parseSchoolsJson(text!!).single { it.name == "重庆大学" }
        assertEquals("cqu", cqu.type)
        assertEquals(JwProtocol.TYPE_CQU, cqu.type)
        assertEquals("https://my.cqu.edu.cn/", cqu.url)
        assertTrue(cqu.isSupported)
        assertTrue(cqu.aliases.contains("cqu"))
        assertEquals("chongqingdaxue", cqu.sortKeyFull)
    }
}
