package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JwBfaFduZtvtitOccurrenceTest {
    @Test fun `five existing school entries use their declared layouts and remain unverified`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        for ((name, type) in listOf("北京电影学院" to "bfa", "复旦大学个人数据中心" to "fdu",
            "复旦大学个人数据中心 - 研究生" to "fdu", "复旦大学 - 研究生" to "fdu", "浙江交通职业技术学院" to "ztvtit")) {
            val school = schools.single { it.name == name }
            assertEquals(type, school.type)
            assertEquals(true, school.isUnverified)
        }
    }

    private fun tr(vararg cells: String) = cells.joinToString("", "<tr>", "</tr>") { "<td>$it</td>" }
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { r ->
        (r.startWeek..r.endWeek).filter { r.type == 0 || it % 2 == r.type % 2 }.flatMap { week ->
            (r.startNode..r.endNode).map { node -> "${r.name}|${r.teacher}|${r.room}|${r.day}|$node|$week" }
        }
    }.sorted()
    private fun verify(type: String, html: String, expected: List<String>) {
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.parserFor(type, html).generateCourseList()))
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.selectBest(html, type).first))
        assertEquals(expected.sorted(), occurrences(JwImportViewModel.parseHtmlStatic(html, type).first))
        assertFalse(JwParserRegistry.allCandidates(html).any { it.first == type })
    }

    @Test fun `BFA preserves six columns disjoint periods and each week fragment`() {
        val html = "<table id=\"studentRecordTable\"><tbody>" +
            tr("电影&amp;声音", "李老师", "第1-5单周,第8-10双周", "7", "1-2,5-6", "-") +
            tr("电影&amp;声音", "王老师", "12周", "3", "9", "摄影棚") + "</tbody></table>"
        val expected = listOf(1,3,5,8,10).flatMap { w -> listOf(1,2,5,6).map { n -> "电影&声音|李老师||7|$n|$w" } } +
            "电影&声音|王老师|摄影棚|3|9|12"
        verify("bfa", html, expected)
    }

    @Test fun `BFA invalid nodes and weeks never fall back to another row`() {
        val html = "<table id=\"studentRecordTable\"><tbody>" +
            tr("坏周", "T", "1-4,待定", "1", "1-2", "A") +
            tr("坏节", "T", "1-4周", "1", "-1-2", "A") +
            tr("缺日", "T", "1-4周", "0", "1-2", "A") +
            tr("好课", "T", "9周", "2", "3", "B") + "</tbody></table>"
        verify("bfa", html, listOf("好课|T|B|2|3|9"))
    }

    @Test fun `FDU continuation rows keep their course and independently scheduled room weeks`() {
        val html = "<table class=\"table table-hover\"><tbody>" +
            tr("1", "课程甲", "A", "星期一 第1-2节", "第1,3,7周", "备注") +
            tr("B", "星期日 第5-6节", "第8-10双周", "备注") +
            tr("2", "课程乙", "C", "星期三 第9节", "第4周", "备注") + "</tbody></table>"
        verify("fdu", html, listOf(1,3,7).flatMap { w -> (1..2).map { n -> "课程甲||A|1|$n|$w" } } +
            listOf(8,10).flatMap { w -> (5..6).map { n -> "课程甲||B|7|$n|$w" } } + "课程乙||C|3|9|4")
    }

    @Test fun `FDU malformed rows do not reuse weeks or leak course identity across tables`() {
        val html = "<table class=\"table table-hover\"><tbody>" +
            tr("孤行", "星期一 第1节", "1周", "备注") +
            tr("1", "课程甲", "A", "星期一 第1节", "第1-3,未知周", "备注") +
            tr("2", "", "B", "星期一 第1节", "1周", "备注") +
            tr("C", "星期一 第1节", "1周", "备注") + "</tbody></table>" +
            "<table class=\"table table-hover\"><tbody>" + tr("D", "星期一 第1节", "1周", "备注") + "</tbody></table>"
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.selectBest(html, "fdu").first)
    }

    private fun info(name: String, room: String, teacher: String, weeks: String) = "<div class=\"courseInfo\">" +
        "<span>$name</span><span class=\"place\">$room</span><span class=\"teacher\">$teacher</span>" +
        "<span class=\"WeekDetail\">$weeks</span></div>"

    @Test fun `ZTVTIT English weekday attribute and lesson are independent of DOM position`() {
        val html = "<table class=\"scheduleTable table table-bordered table-hover\"><tr>" +
            "<td week=\"sun\" lesson=\"9\">" + info("课程甲", "A", "张老师", "第1-5(单周),第8-10(双周)") +
            info("课程乙", "B", "李老师", "第12周") + "</td>" +
            "<td week=\"mon\" lesson=\"3\">" + info("课程甲", "C", "王老师", "6,8周") + "</td></tr></table>"
        verify("ztvtit", html, listOf(1,3,5,8,10).map { "课程甲|张老师|A|7|9|$it" } +
            "课程乙|李老师|B|7|9|12" + listOf(6,8).map { "课程甲|王老师|C|1|3|$it" })
    }

    @Test fun `ZTVTIT invalid attributes and missing week detail do not invent schedules`() {
        val html = "<table class=\"scheduleTable table table-bordered table-hover\"><tr>" +
            "<td week=\"bad\" lesson=\"1\">" + info("坏日", "A", "T", "1周") + "</td>" +
            "<td week=\"mon\" lesson=\"0\">" + info("坏节", "A", "T", "1周") + "</td>" +
            "<td week=\"mon\" lesson=\"1\">" + info("缺周", "A", "T", "待定") + "</td></tr></table>"
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.selectBest(html, "ztvtit").first)
    }
}
