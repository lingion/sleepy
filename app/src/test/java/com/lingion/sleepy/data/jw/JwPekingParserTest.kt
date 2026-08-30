package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JwPekingParser 单测 — T3（北京大学 pku 协议）
 * fixture: src/test/resources/jw/pku-bnuz/pku_*.html
 */
class JwPekingParserTest {

    private fun html(name: String): String =
        javaClass.classLoader.getResource("jw/pku-bnuz/$name.html")!!.readText()

    @Test
    fun pku_normal_3门课_含未选跳过与多时段拆分() {
        val courses = JwPekingParser(html("pku_normal")).generateCourseList()
        assertEquals(3, courses.size)
        assertTrue("tds[8]=未选 必须跳过", courses.none { it.name == "线性代数" })

        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.day); assertEquals(1, math.startNode); assertEquals(2, math.endNode)
        assertEquals(1, math.startWeek); assertEquals(16, math.endWeek)
        assertEquals(0, math.type); assertEquals("张老师", math.teacher); assertEquals("教101", math.room)

        // 大学英语 tds[7] 两个 <br> 时段 → 2 条
        val eng = courses.filter { it.name == "大学英语" }.sortedBy { it.day }
        assertEquals(2, eng.size)
        assertEquals(3, eng[0].day); assertEquals(3, eng[0].startNode); assertEquals(4, eng[0].endNode)
        assertEquals("教202", eng[0].room)
        assertEquals(5, eng[1].day); assertEquals(5, eng[1].startNode); assertEquals(6, eng[1].endNode)
        assertEquals("教303", eng[1].room)
    }

    @Test
    fun pku_single_double_week_单双周type() {
        val courses = JwPekingParser(html("pku_single_double_week")).generateCourseList()
        assertEquals(2, courses.size)
        assertEquals(1, courses.first { it.name == "大学物理" }.type)   // "周一1~2节(单)"
        assertEquals(2, courses.first { it.name == "化学实验" }.type)   // "周四5~6节(双)"
    }

    @Test
    fun pku_missing_fields_四行边界() {
        val courses = JwPekingParser(html("pku_missing_fields")).generateCourseList()
        // 行1 tds.size<11 跳过；行2 时段空块跳过；
        // 行3 "1~16周 周二5~6节(实验楼201)" 两 token，room 走括号兜底 = "实验楼201"
        // 行4 状态"退课申请中"不含'未' → 正常解析，room=timeInfo[2]="教305"
        assertEquals(2, courses.size)
        val phys = courses.first { it.name == "大学物理" }
        assertEquals(2, phys.day); assertEquals(5, phys.startNode); assertEquals(6, phys.endNode)
        assertEquals("实验楼201", phys.room)
        val eng = courses.first { it.name == "大学英语(二)" }
        assertEquals(5, eng.day); assertEquals(7, eng.startNode); assertEquals(8, eng.endNode)
        assertEquals("教305", eng.room)
    }

    @Test
    fun pku_empty_空tbody返回空() {
        assertEquals(0, JwPekingParser(html("pku_empty")).generateCourseList().size)
    }

    @Test
    fun pku_login_无datagrid返回空不抛() {
        assertEquals(0, JwPekingParser(html("pku_login")).generateCourseList().size)
    }

    @Test
    fun adversarial_pku_iaaa登录页不误产出() {
        val src = javaClass.classLoader
            .getResource("jw/adversarial/pku_iaaa_login_redirect.html")!!.readText()
        assertEquals(0, JwPekingParser(src).generateCourseList().size)
    }
}
