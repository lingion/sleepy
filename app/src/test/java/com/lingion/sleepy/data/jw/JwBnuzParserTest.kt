package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JwBnuzParser 单测 — T3（北师珠 bnuz 协议）
 * fixture: src/test/resources/jw/pku-bnuz/bnuz_*.html
 */
class JwBnuzParserTest {

    private fun html(name: String): String =
        javaClass.classLoader.getResource("jw/pku-bnuz/$name.html")!!.readText()

    @Test
    fun bnuz_normal_6条含单双周与多教师() {
        val courses = JwBnuzParser(html("bnuz_normal")).generateCourseList()
        assertEquals(6, courses.size)

        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.day); assertEquals(1, math.startNode); assertEquals(2, math.endNode)
        assertEquals(1, math.startWeek); assertEquals(16, math.endWeek)
        assertEquals(0, math.type); assertEquals("张老师", math.teacher); assertEquals("教101", math.room)

        // 大学英语 {1-8周单周,9-16周双周} → 2 条
        val eng = courses.filter { it.name == "大学英语" }.sortedBy { it.startWeek }
        assertEquals(2, eng.size)
        assertEquals(1, eng[0].startWeek); assertEquals(8, eng[0].endWeek); assertEquals(1, eng[0].type)
        assertEquals(9, eng[1].startWeek); assertEquals(16, eng[1].endWeek); assertEquals(2, eng[1].type)
        assertEquals(3, eng[0].day); assertEquals(3, eng[0].startNode); assertEquals(4, eng[0].endNode)

        // 体育 周五 node=5
        val pe = courses.first { it.name == "体育" }
        assertEquals(5, pe.day); assertEquals(5, pe.startNode); assertEquals(6, pe.endNode)
        assertEquals("体育馆", pe.room)

        // 化学实验 td 内 2 个 section（张老师+教401 / 李老师+教402）
        val chem = courses.filter { it.name == "化学实验" }
        assertEquals(2, chem.size)
        assertTrue(chem.any { it.teacher == "张老师" && it.room == "教401" })
        assertTrue(chem.any { it.teacher == "李老师" && it.room == "教402" })
        assertTrue(chem.all { it.day == 2 && it.startNode == 7 && it.endNode == 8 })
    }

    @Test
    fun bnuz_missing_fields_四行边界只产出1门() {
        val courses = JwBnuzParser(html("bnuz_missing_fields")).generateCourseList()
        // 行1 无 </span> → 跳过；行3 教师段无 {} → 跳过；行5 教室无 (N节) → 上游抛 NFE，sleepy 丢弃该 td；
        // 行7 正常（尾部 <br>&nbsp; 保住教室段）
        assertEquals(1, courses.size)
        assertEquals("艺术史", courses[0].name)
        assertEquals(4, courses[0].day); assertEquals(7, courses[0].startNode); assertEquals(8, courses[0].endNode)
        assertEquals("艺术楼201", courses[0].room); assertEquals("艺术老师", courses[0].teacher)
    }

    @Test
    fun bnuz_empty_全空行返回空() {
        assertEquals(0, JwBnuzParser(html("bnuz_empty")).generateCourseList().size)
    }

    @Test
    fun bnuz_login_无table1返回空不抛() {
        assertEquals(0, JwBnuzParser(html("bnuz_login")).generateCourseList().size)
    }

    @Test
    fun adversarial_bnuz_活体WebForm登录页不误产出() {
        val src = javaClass.classLoader
            .getResource("jw/adversarial/bnuz_es_default_login.html")!!.readText()
        assertEquals(0, JwBnuzParser(src).generateCourseList().size)
    }
}
