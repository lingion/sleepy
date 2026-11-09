package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Synthetic protocol fixtures derived from the local 6.6.2 RUC field mapping. */
class JwRucOccurrenceTest {
    @Test fun `RUC parser availability does not bypass the existing catalog ownership gate`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        val school = schools.single { it.name == "中国人民大学（微人大） - 研究生" }
        assertEquals(true, JwParserRegistry.supports("ruc"))
        assertEquals(null, school.type)
        assertEquals("grad_pending", school.status)
        assertEquals("", school.url)
    }

    private fun lesson(name: String = "研讨{甲}", day: String = "5", start: String = "3", length: String = "2",
                       weeks: String = "1-7,11-15单周") = JSONObject().apply {
        put("title", name); put("place", "地点：明德楼301"); put("teacher", "教师：王老师")
        put("week", day); put("start", start); put("quittingTime", length); put("weekly", weeks)
    }
    private fun source(rows: List<JSONObject?>, hours: List<String?> = listOf("8:00~8:45", "8:55~9:40", "10:00~10:45", "10:55~11:40")): String {
        val data = JSONObject().put("schoolHours", JSONArray(hours)).put("course", JSONArray(rows))
        return "<script>var unrelated = 'var morning ='; var data = $data; var morning = 4;</script>"
    }
    private fun occurrences(rows: List<JwCourse>): List<String> = rows.flatMap { row ->
        (row.startWeek..row.endWeek).filter { row.type == 0 || it % 2 == row.type % 2 }.map {
            listOf(row.name, row.teacher, row.room, row.day, row.startNode, row.endNode, it, row.startTime, row.endTime).joinToString("|")
        }
    }.sorted()

    @Test fun `RUC duration sparse weeks and clock stay attached to each occurrence`() {
        val html = source(listOf(null, lesson(), lesson("研讨{甲}", "7", "1", "1", "2,6,10周")))
        val expected = (listOf(1, 3, 5, 7, 11, 13, 15).map { "研讨{甲}|王老师|明德楼301|5|3|4|$it|10:00|11:40" } +
            listOf(2, 6, 10).map { "研讨{甲}|王老师|明德楼301|7|1|1|$it|08:00|08:45" }).sorted()
        assertEquals(expected, occurrences(JwParserRegistry.parserFor("ruc", html).generateCourseList()))
        assertEquals(expected, occurrences(JwParserRegistry.selectBest(html, "ruc").first))
        assertEquals(expected, occurrences(JwImportViewModel.parseHtmlStatic(html, "ruc").first))
        assertFalse(JwParserRegistry.allCandidates(html).any { it.first == "ruc" })
    }

    @Test fun `RUC rejects invalid coordinates and malformed week expressions without defaults`() {
        val invalid = listOf(lesson(day = "0"), lesson(day = "8"), lesson(start = "-1"), lesson(length = "0"),
            lesson(length = "2147483647"), lesson(weeks = "待定"), lesson(weeks = "-1-7周"),
            lesson(weeks = "1-7,待定"), lesson(name = ""))
        assertEquals(emptyList<JwCourse>(), JwParserRegistry.selectBest(source(invalid), "ruc").first)
    }

    @Test fun `RUC missing clock bucket does not shift following periods or invent a partial clock`() {
        val html = source(listOf(lesson(start = "1", length = "2", weeks = "1周"), lesson(start = "3", length = "1", weeks = "2周")),
            listOf("8:00~8:45", null, "10:00~10:45"))
        assertEquals(listOf(
            JwCourse("研讨{甲}", "明德楼301", "王老师", 5, 1, 2, 1, 1),
            JwCourse("研讨{甲}", "明德楼301", "王老师", 5, 3, 3, 2, 2, startTime = "10:00", endTime = "10:45"),
        ), JwParserRegistry.parserFor("ruc", html).generateCourseList())
    }

    @Test fun `RUC strings containing delimiter text do not truncate JSON and draft retains clock`() {
        val html = source(listOf(lesson(name = "var morning = 和 \"转义\"", start = "1", length = "2", weeks = "2-4双周")))
        val courses = JwImportViewModel.parseHtmlStatic(html, "ruc").first
        assertEquals(listOf(JwCourse("var morning = 和 \"转义\"", "明德楼301", "王老师", 5, 1, 2, 2, 4, 2, "08:00", "09:40")), courses)
        val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(JwImportDraftSnapshot(
            school = JwSchoolInfo("R", "人大", type = "ruc"), courses = courses, periods = emptyList(),
        )))!!
        val entity = JwImportViewModel(android.app.Application()).toCourseEntities(restored.courses, 7L, "#FF6750A4").single()
        assertEquals("08:00", entity.startTime); assertEquals("09:40", entity.endTime); assertEquals(true, entity.ownTime)
    }
}
