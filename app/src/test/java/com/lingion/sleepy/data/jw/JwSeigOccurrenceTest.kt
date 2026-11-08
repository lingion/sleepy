package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JwSeigOccurrenceTest {
    @Test fun `both SEIG catalog declarations use the explicit grid parser`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        for (name in listOf("广州大学华软软件学院", "广州软件学院")) {
            val school = schools.single { it.name == name }
            assertEquals("seig", school.type)
            assertEquals(true, school.isUnverified)
        }
    }

    private fun table(rows: List<List<String>>) = "<table><tr><td>节次</td>" +
        listOf("一", "二", "三", "四", "五", "六", "日").joinToString("") { "<td>星期$it</td>" } +
        "</tr>" + rows.joinToString("") { cells -> cells.joinToString("", "<tr><td>标签</td>", "</tr>") { "<td>$it</td>" } } + "</table>"
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { row ->
        (row.startWeek..row.endWeek).filter { row.type == 0 || it % 2 == row.type % 2 }.map {
            listOf(row.name, row.teacher, row.room, row.day, row.startNode, row.endNode, it).joinToString("|")
        }
    }.sorted()

    @Test fun `SEIG row order and last parenthesis preserve sparse weeks Sunday and course title`() {
        val html = table(listOf(
            listOf("程序设计(实验)(A 王老师 1周 3周 7周 [B201])", "", "", "", "", "", "体育(B 李老师 2周 6周 [操场])"),
            listOf("", "数据库(C 张老师 4周 5周 [C302])", "", "", "", "", ""),
        ))
        val expected = (listOf(1, 3, 7).map { "程序设计(实验)|王老师|B201|1|1|2|$it" } +
            listOf(2, 6).map { "体育|李老师|操场|7|1|2|$it" } +
            listOf(4, 5).map { "数据库|张老师|C302|2|3|4|$it" }).sorted()
        assertEquals(expected, occurrences(JwParserRegistry.parserFor("seig", html).generateCourseList()))
        assertEquals(expected, occurrences(JwParserRegistry.selectBest(html, "seig").first))
        assertEquals(expected, occurrences(JwImportViewModel.parseHtmlStatic(html, "seig").first))
        assertFalse(JwParserRegistry.allCandidates(html).any { it.first == "seig" })
    }

    @Test fun `SEIG bad cells and empty rows do not shift later periods or invent weeks`() {
        val html = table(listOf(listOf("坏课", "坏周(A 李老师 -1周 [A])", "缺周(A 李老师 [A])"),
            listOf("", "", ""), listOf("正确(A 王老师 9周 9周 [B])", "", "")))
        assertEquals(listOf("正确|王老师|B|1|5|6|9"), occurrences(JwParserRegistry.selectBest(html, "seig").first))
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.selectBest(html.replace("星期三", "其它"), "seig").first)
    }
}
