package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JwChengFangParser 单测 — T3（青果/乘方 cf 协议）
 * fixture: src/test/resources/jw/cf-chengfang/
 */
class JwChengFangParserTest {

    private fun html(name: String): String =
        javaClass.classLoader.getResource("jw/cf-chengfang/$name.html")!!.readText()

    // ── 主路径 ────────────────────────────────────────────

    @Test
    fun typical_two_courses_连续周() {
        val courses = JwChengFangParser(html("typical_two_courses")).generateCourseList()
        assertEquals(2, courses.size)

        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.day); assertEquals(1, math.startNode); assertEquals(2, math.endNode)
        assertEquals(1, math.startWeek); assertEquals(16, math.endWeek)
        assertEquals(0, math.type); assertEquals("张老师", math.teacher); assertEquals("A101", math.room)

        val eng = courses.first { it.name == "大学英语" }
        assertEquals(3, eng.day); assertEquals(3, eng.startNode); assertEquals(4, eng.endNode)
        assertEquals("B202", eng.room)
    }

    @Test
    fun single_double_weeks_单双周() {
        val courses = JwChengFangParser(html("single_double_weeks")).generateCourseList()
        assertEquals(2, courses.size)
        // jcdm2="06,07" 补零串 → 6/7
        val pe = courses.first { it.name == "体育" }
        assertEquals(5, pe.day); assertEquals(6, pe.startNode); assertEquals(7, pe.endNode)
        assertEquals(1, pe.type); assertEquals(1, pe.startWeek); assertEquals(15, pe.endWeek)
        val lab = courses.first { it.name == "程序设计实验" }
        assertEquals(2, lab.day); assertEquals(9, lab.startNode); assertEquals(10, lab.endNode)
        assertEquals(2, lab.type); assertEquals(2, lab.startWeek); assertEquals(16, lab.endWeek)
    }

    @Test
    fun multi_segment_weeks_多段周次与三节连堂() {
        val courses = JwChengFangParser(html("multi_segment_weeks")).generateCourseList()
        assertEquals(4, courses.size)
        // 线性代数 zcs 1..8,10..16 → gap 切两段，各 type=0
        val la = courses.filter { it.name == "线性代数" }.sortedBy { it.startWeek }
        assertEquals(2, la.size)
        assertEquals(1, la[0].startWeek); assertEquals(8, la[0].endWeek); assertEquals(0, la[0].type)
        assertEquals(10, la[1].startWeek); assertEquals(16, la[1].endWeek); assertEquals(0, la[1].type)
        // 思政课 zcs=5,7,9,11,13,15 → 单周 type=1
        val sz = courses.first { it.name == "思政课" }
        assertEquals(1, sz.type); assertEquals(5, sz.startWeek); assertEquals(15, sz.endWeek)
        assertEquals(11, sz.startNode); assertEquals(11, sz.endNode)   // jcdm2="11" 单节
        // 工程制图 jcdm2="1,2,3" → 3 节连堂
        val draw = courses.first { it.name == "工程制图" }
        assertEquals(1, draw.startNode); assertEquals(3, draw.endNode)
        assertEquals(7, draw.day)
    }

    @Test
    fun escaped_quotes_multiline_多行脚本与转义引号() {
        val courses = JwChengFangParser(html("escaped_quotes_multiline")).generateCourseList()
        assertEquals(2, courses.size)
        val mao = courses.first { it.name.contains("毛泽东思想") }
        assertEquals(4, mao.day)
        assertEquals("周老师 / 课程组", mao.teacher)   // 教师整体保留，不拆分
        assertTrue("教室应含 304（引号容差）", mao.room.replace("\"", "").contains("304"))
        val cpp = courses.first { it.name.contains("C++") }
        // jcdm2="09,10,11" 补零串 → 9
        assertEquals(9, cpp.startNode)
        assertEquals(11, cpp.endNode); assertEquals(2, cpp.startWeek); assertEquals(9, cpp.endWeek)
    }

    // ── 边界 ────────────────────────────────────────────

    @Test
    fun empty_timetable_空数组返回空() {
        assertEquals(0, JwChengFangParser(html("empty_timetable")).generateCourseList().size)
    }

    @Test
    fun login_page_no_kbxx_登录页返回空不抛() {
        assertEquals(0, JwChengFangParser(html("login_page_no_kbxx")).generateCourseList().size)
    }

    @Test
    fun missing_fields_缺周次跳过缺教师教室为空串() {
        val courses = JwChengFangParser(html("missing_fields")).generateCourseList()
        assertEquals(3, courses.size)   // "缺周次" zcs="" 被跳过
        assertTrue(courses.none { it.name == "缺周次" })
        assertTrue(courses.first { it.name == "缺教室" }.room.isEmpty())
        assertTrue(courses.first { it.name == "缺教师" }.teacher.isEmpty())
    }

    @Test
    fun adversarial_cf_authserver登录页不误产出() {
        val src = javaClass.classLoader
            .getResource("jw/adversarial/cf_qingguo_authserver_login.html")!!.readText()
        assertEquals(0, JwChengFangParser(src).generateCourseList().size)
    }

    @Test
    fun 提取器_纯数组_与_带分号教室值() {
        // 直接测提取函数：值内含分号也不被截断（对上游 substringBefore(';') 的偏离点）
        val h1 = """<script>var kbxx = [{"kcmc":"A","teaxms":"t","jxcdmcs":"x;y","xq":"1","jcdm2":"1,2","zcs":"1,2"}];</script>"""
        val j1 = JwChengFangParser.extractKbxxJsonForTest(h1)
        assertTrue(j1!!.endsWith("]"))
        assertEquals(1, JwChengFangParser(h1).generateCourseList().size)
        // 无 marker → null
        assertEquals(null, JwChengFangParser.extractKbxxJsonForTest("<html>login</html>"))
    }

    @Test
    fun weekIntList2WeekBeanList_归并语义() {
        fun m(vararg xs: Int) = JwChengFangParser.weekIntList2WeekBeanList(xs.toList())
        // 1..16 连续 → 1 段 type=0
        m(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16).let {
            assertEquals(1, it.size); assertEquals(Triple(1,16,0), it[0])
        }
        // 1..8 + 10..16 → 两段 type=0
        m(1,2,3,4,5,6,7,8,10,11,12,13,14,15,16).let {
            assertEquals(2, it.size); assertEquals(Triple(1,8,0), it[0]); assertEquals(Triple(10,16,0), it[1])
        }
        // 奇数 → 单周；偶数 → 双周
        assertEquals(Triple(5,15,1), m(5,7,9,11,13,15)[0])
        assertEquals(Triple(2,16,2), m(2,4,6,8,10,12,14,16)[0])
        // 单元素
        assertEquals(Triple(11,11,0), m(11)[0])
        // 带大缺口 1,2,15,16 → 1,2 一段 + 15,16 一段
        m(1,2,15,16).let { assertEquals(2, it.size); assertEquals(Triple(1,2,0), it[0]); assertEquals(Triple(15,16,0), it[1]) }
    }
}
