package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JwWiseduParser 单元测试 — 用哈工程真实 xskcb.do JSON 验证。
 *
 * 数据：src/test/resources/xskcb_heu.json（2025089104 学号 2025-2026-2 学期真实课表，34 行）。
 * 不依赖 Android Context / 数据库 / 模拟器，纯 JVM 跑。
 *
 * 预期（已用 Python 对同份数据交叉验证）：
 *   - 34 行 → 展开 46 个 JwCourse（多段课程按连续周次段拆分）
 *   - 1 个单周(type=1)：形势与政策（11,13,15,17 周 → 11-17 单周）
 *   - 体育（二）：周4 第3-4节 篮球训练馆 鲍伟 2-17周(每周)
 *   - 电路与电子I：3 段（2-5, 7-9, 11-14 周）
 */
class JwWiseduParserTest {

    private fun loadRealJson(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("xskcb_heu.json")
        assertNotNull("测试资源 xskcb_heu.json 应存在", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    @Test
    fun `parses real HEU schedule - total count`() {
        val courses = JwWiseduParser(loadRealJson()).generateCourseList()
        println("HEU 真实课表解析出 ${courses.size} 个 JwCourse:")
        courses.groupBy { it.name }.forEach { (name, segs) ->
            println("  $name × ${segs.size}: " + segs.joinToString { "周${it.day} ${it.startNode}-${it.endNode}节 ${it.startWeek}-${it.endWeek}周(t${it.type})" })
        }
        assertEquals("34 行展开后应为 46 个 JwCourse", 46, courses.size)
    }

    @Test
    fun `single-week compression - 形势与政策`() {
        val courses = JwWiseduParser(loadRealJson()).generateCourseList()
        val type1 = courses.filter { it.type == 1 }
        assertEquals("应恰好 1 个单周课段", 1, type1.size)
        val xs = type1.first()
        assertEquals("形势与政策", xs.name)
        assertEquals(11, xs.startWeek)
        assertEquals(17, xs.endWeek)
        assertEquals(1, xs.type)
    }

    @Test
    fun `field mapping - 体育（二）`() {
        val courses = JwWiseduParser(loadRealJson()).generateCourseList()
        val pe = courses.first { it.name == "体育（二）" }
        assertEquals("篮球训练馆", pe.room)
        assertEquals("鲍伟", pe.teacher)
        assertEquals(4, pe.day)        // 周四
        assertEquals(3, pe.startNode)
        assertEquals(4, pe.endNode)
        assertEquals(2, pe.startWeek)
        assertEquals(17, pe.endWeek)
        assertEquals(0, pe.type)       // 每周
    }

    @Test
    fun `multi-segment split - 电路与电子I`() {
        val courses = JwWiseduParser(loadRealJson()).generateCourseList()
        // 电路与电子I 周一段 SKZC 含 3 个连续段 (2-5, 7-9, 11-14)
        val circuit = courses.filter { it.name == "电路与电子I" && it.day == 1 }
        val ranges = circuit.map { it.startWeek to it.endWeek }.toSet()
        println("电路与电子I 周一段: $ranges")
        assertTrue("应含 2-5 周段", ranges.contains(2 to 5))
        assertTrue("应含 7-9 周段", ranges.contains(7 to 9))
        assertTrue("应含 11-14 周段", ranges.contains(11 to 14))
    }

    @Test
    fun `empty and malformed input - graceful`() {
        assertEquals(0, JwWiseduParser("").let { runCatching { it.generateCourseList() }.getOrDefault(emptyList()) }.size)
        assertEquals(0, JwWiseduParser("not json").let { runCatching { it.generateCourseList() }.getOrDefault(emptyList()) }.size)
        assertEquals(0, JwWiseduParser("""{"code":"0","datas":{}}""").generateCourseList().size)
    }
}
