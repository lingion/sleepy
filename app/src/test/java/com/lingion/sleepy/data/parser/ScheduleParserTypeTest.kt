package com.lingion.sleepy.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ScheduleParser.parseType] 的语义 — 类型列缺失/未知时返回 3=按周次,
 * 而不是 0=每周. 这是修复"用户截图实验课 type=0 误标每周都上"的关键.
 */
class ScheduleParserTypeTest {

    private fun parseType(s: String): Int {
        // 通过反射访问 private parseType — parseType 是 private, 通过 parse() 路径验证
        // 此处走 parse() 的反向路径: 用 scheduleParser 测试包内可见性, 直接调用
        val m = ScheduleParser::class.java.getDeclaredMethod("parseType", String::class.java)
        m.isAccessible = true
        return m.invoke(ScheduleParser, s) as Int
    }

    @Test
    fun empty_returnsType3_byWeek() {
        assertEquals(3, parseType(""))
        assertEquals(3, parseType("   "))
    }

    @Test
    fun explicit0_returns0_weekly() {
        assertEquals(0, parseType("0"))
        assertEquals(0, parseType(" 每周 "))
    }

    @Test
    fun explicit1_returns1_oddWeek() {
        assertEquals(1, parseType("1"))
        assertEquals(1, parseType("单周"))
    }

    @Test
    fun explicit2_returns2_evenWeek() {
        assertEquals(2, parseType("2"))
        assertEquals(2, parseType("双周"))
    }

    @Test
    fun explicit3_returns3_byWeek() {
        assertEquals(3, parseType("3"))
        assertEquals(3, parseType("按周次"))
        assertEquals(3, parseType("自定义"))
    }

    @Test
    fun unknownGarbage_returns3_notZero() {
        // 关键: 未知值不再回退到 0=每周, 否则"周次=6 单次实验"会被错误标成每周
        assertEquals(3, parseType("每周都上但我不确定"))
        assertEquals(3, parseType("??"))
        assertEquals(3, parseType("99"))
    }

    /**
     * 端到端验证: 解析真实粘贴数据时, 类型列缺失应得到 type=3
     */
    @Test
    fun endToEnd_missingTypeColumn_resultsInType3() {
        val text = """
            <<<SLEEPY-BEGIN>>>
            <<<SLEEPY-TIME-BEGIN>>>
            第1节 08:00-08:45
            <<<SLEEPY-TIME-END>>>
            迈克尔逊-11#2003-3	李平	-	4	11-13	6
            <<<SLEEPY-END>>>
        """.trimIndent()
        val result = ScheduleParser.parse(text, defaultTableId = 0L).getOrThrow()
        assertEquals(1, result.courses.size)
        val c = result.courses.first()
        assertEquals("类型缺失应得 type=3", 3, c.type)
        assertEquals("第 6 周一次实验", 6, c.startWeek)
        assertEquals(6, c.endWeek)
        // 关键: inWeek 第 6 周 true, 第 5/7 周 false
        assertEquals(true, c.inWeek(6))
        assertEquals(false, c.inWeek(5))
        assertEquals(false, c.inWeek(7))
    }

    /**
     * 端到端: 类型列显式写 0 应保留 0=每周 (兼容旧数据)
     */
    @Test
    fun endToEnd_explicitType0_remains0() {
        val text = """
            高数	张老师	A101	1	1-2	1-16	0
        """.trimIndent()
        val result = ScheduleParser.parse(text, defaultTableId = 0L).getOrThrow()
        assertEquals(0, result.courses.first().type)
    }
}