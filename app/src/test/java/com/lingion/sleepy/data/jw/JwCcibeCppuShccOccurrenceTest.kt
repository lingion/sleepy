package com.lingion.sleepy.data.jw

import com.lingion.sleepy.util.DateUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwCcibeCppuShccOccurrenceTest {
    private fun occurrences(rows: List<JwCourse>) = rows.flatMap { r ->
        (r.startWeek..r.endWeek).filter { r.type == 0 || it % 2 == r.type % 2 }.flatMap { w ->
            (r.startNode..r.endNode).map { n -> "${r.name}|${r.teacher}|${r.room}|${r.day}|$n|$w|${r.credit}" }
        }
    }.sorted()
    private fun verify(type: String, source: String, expected: List<String>) {
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.parserFor(type, source).generateCourseList()))
        assertEquals(expected.sorted(), occurrences(JwParserRegistry.selectBest(source, type).first))
        assertEquals(expected.sorted(), occurrences(JwImportViewModel.parseHtmlStatic(source, type).first))
        assertFalse(JwParserRegistry.allCandidates(source).any { it.first == type })
    }
    private fun ccibe(vararg rows: JSONObject) = JSONObject().put("calendar",
        JSONObject().put("startTime", "2026-9-2").put("allWeek", 20)).put("wdkb", JSONArray(rows.toList())).toString()
    private fun ccibeRow(name: String, day: String, nodes: String, from: Any?, to: Any?) = JSONObject()
        .put("kcmc", name).put("xqj", day).put("jc", nodes).put("qsz", from ?: JSONObject.NULL)
        .put("jsz", to ?: JSONObject.NULL).put("jxdd", "A楼").put("jsxm", "张老师")

    @Test fun `CCIBE explicit week bounds and Chinese weekday survive the full parser route`() {
        verify("ccibe", ccibe(ccibeRow("课程甲", "一", "1-2", 1, 3), ccibeRow("课程乙", "日", "11", 9, 9)),
            (1..3).flatMap { w -> listOf(1,2).map { n -> "课程甲|张老师|A楼|1|$n|$w|0.0" } } + "课程乙|张老师|A楼|7|11|9|0.0")
    }

    @Test fun `CCIBE missing week bounds and invalid day never fall back to week one`() {
        verify("ccibe", ccibe(ccibeRow("缺周", "一", "1-2", null, null), ccibeRow("半周", "一", "1-2", 1, null),
            ccibeRow("坏日", "九", "1-2", 1, 2), ccibeRow("小数周", "一", "1-2", 1.5, 2)), emptyList())
    }

    private fun cppu(start: String?, vararg rows: JSONObject) = JSONObject().put("startDate", start ?: JSONObject.NULL)
        .put("rows", JSONArray(rows.toList())).toString()
    private fun cppuRow(name: String, date: String?, node: String?) = JSONObject().put("KCMC", name).put("JS", "李老师")
        .put("DDMC", "实验楼").put("XF", 2.5).put("SKRQ", date ?: JSONObject.NULL).put("JC", node ?: JSONObject.NULL)

    @Test fun `CPPU absolute dates round trip across Monday after a Wednesday term start`() {
        val source = cppu("2026-09-02", cppuRow("第一周", "2026-09-02", "2@上午"),
            cppuRow("周日", "2026-09-06", "3@下午"), cppuRow("第二周", "2026-09-07", "4@晚上"))
        verify("cppu", source, listOf(3,4).map { "第一周|李老师|实验楼|3|$it|1|2.5" } +
            listOf(5,6).map { "周日|李老师|实验楼|7|$it|1|2.5" } + listOf(7,8).map { "第二周|李老师|实验楼|1|$it|2|2.5" })
        val courses = JwParserRegistry.parserFor("cppu", source).generateCourseList()
        val date = JwSourceCalendar.resolveStartDate(source, "cppu", "")
        assertEquals(listOf("2026-09-02", "2026-09-06", "2026-09-07"), courses.map {
            DateUtils.dateOfWeek(date, it.startWeek, it.day).toString()
        })
        val snapshot = JwImportDraftSnapshot(JwSchoolInfo("T", "日期测试"), courses, emptyList(), termStartDate = date)
        assertEquals(snapshot, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot)))
    }

    @Test fun `CPPU missing dates fail and unknown periods cannot default to the first pair`() {
        for (source in listOf(cppu(null, cppuRow("课程", "2026-09-02", "1")),
            cppu("2026-09-02", cppuRow("课程", null, "1")))) {
            assertTrue(JwParserRegistry.selectBest(source, "cppu").first.isEmpty())
        }
        verify("cppu", cppu("2026-09-02", cppuRow("缺节", "2026-09-02", null),
            cppuRow("学期前", "2026-08-23", "1"), cppuRow("坏节", "2026-09-02", "-1@上午")), emptyList())
    }

    private fun shccRow(name: String, teacherWeeks: String?, first: String, length: String) = JSONObject()
        .put("kcS", name).put("js", teacherWeeks ?: JSONObject.NULL).put("jsmc", "A101").put("xqj", "3")
        .put("djj", first).put("skcd", length)

    @Test fun `SHCC reads JSON array without cutting semicolons inside course names`() {
        val html = "<script>var ypData = " + JSONArray(listOf(shccRow("课程;甲", "张老师(1-5(单),8-10(双))", "3", "2"),
            shccRow("课程乙", "李老师(12)", "9", "1"))).toString() + "; $(document).ready(function() {});</script>"
        verify("shcc", html, listOf(1,3,5,8,10).flatMap { w -> listOf(3,4).map { n -> "课程;甲|张老师|A101|3|$n|$w|0.0" } } +
            "课程乙|李老师|A101|3|9|12|0.0")
    }

    @Test fun `SHCC no teacher week suffix or invalid length cannot manufacture weeks`() {
        val html = "<script>var ypData=" + JSONArray(listOf(shccRow("缺周", "张老师", "1", "2"),
            shccRow("坏周", "张老师(-1-4)", "1", "2"), shccRow("坏长度", "张老师(1-4)", "1", "2.5"))).toString() + ";</script>"
        verify("shcc", html, emptyList())
    }

    @Test fun `source calendar is normalized without historical hardcoded defaults`() {
        assertEquals("2026-08-31", JwSourceCalendar.resolveStartDate(ccibe(), "ccibe", ""))
        assertEquals("2026-08-31", JwSourceCalendar.resolveStartDate(cppu("2026-09-02"), "cppu", "2026-09-07"))
        assertEquals("", JwSourceCalendar.resolveStartDate("{}", "ccibe", ""))
        assertEquals("", JwSourceCalendar.resolveStartDate("""{"calendar":{"startTime":"2026-02-30"}}""", "ccibe", ""))
        assertEquals("2026-09-07", JwSourceCalendar.resolveStartDate("{}", "unknown", "2026-09-07"))
    }

    private fun weeklyCcibe(): JSONObject {
        val calendar = JSONObject().put("id", "term-a").put("allWeek", 3).put("startTime", "2026-09-02")
        return JSONObject().put("calendar", calendar).put("weeks", JSONArray((1..3).map { week ->
            JSONObject().put("week", week).put("data", JSONObject().put("calendar", calendar)
                .put("wdkb", JSONArray(if (week == 2) emptyList() else listOf(
                    ccibeRow("逐周课程", "三", "1,3", 1, 20)))))
        }))
    }

    @Test fun `CCIBE request week owns occurrence even when row bounds span semester`() {
        verify("ccibe", weeklyCcibe().toString(), listOf(1, 3).flatMap { w ->
            listOf(1, 3).map { n -> "逐周课程|张老师|A楼|3|$n|$w|0.0" }
        })
    }

    @Test fun `CCIBE incomplete repeated and mixed term weekly envelopes fail as a whole`() {
        val bad = listOf(
            weeklyCcibe().apply { getJSONArray("weeks").remove(1) },
            weeklyCcibe().apply { getJSONArray("weeks").getJSONObject(2).put("week", 1) },
            weeklyCcibe().apply { getJSONArray("weeks").getJSONObject(2).getJSONObject("data").remove("wdkb") },
            weeklyCcibe().apply { getJSONArray("weeks").getJSONObject(2).getJSONObject("data")
                .put("calendar", JSONObject().put("id", "term-b")) },
        )
        for (source in bad) {
            assertTrue(runCatching { JwCcibeParser(source.toString()).generateCourseList() }.isFailure)
            assertTrue(JwParserRegistry.selectBest(source.toString(), "ccibe").first.isEmpty())
        }
    }

    @Test fun `three declared schools use their protocol and stay unverified`() {
        val schools = JwImportViewModel.parseSchoolsJson(javaClass.getResource("/jw/schools.json")!!.readText())
        for ((name, type) in mapOf("重庆对外经贸学院" to "ccibe", "中国人民警察大学" to "cppu", "上海海关学院" to "shcc")) {
            val school = schools.single { it.name == name }
            assertEquals(name, type, school.type)
            assertTrue(name, school.isUnverified)
        }
    }

    @Test fun `production collector envelopes preserve occurrences through Kotlin and draft persistence`() {
        val expected = mapOf(
            "ccibe" to listOf(1, 3).flatMap { w -> listOf(1, 3).map { n -> "逐周课程|张老师|A楼|3|$n|$w|0.0" } },
            "cppu" to listOf(3, 4).map { "课程甲|李老师|实验楼|3|$it|1|2.5" } +
                listOf(7, 8).map { "课程乙|王老师|主楼|1|$it|2|1.0" },
        )
        for ((type, rows) in expected) {
            val source = javaClass.getResource("/jw/fixtures/school-api/$type-offline-envelope.json")!!.readText()
            verify(type, source, rows)
            val courses = JwImportViewModel.parseHtmlStatic(source, type).first
            val snapshot = JwImportDraftSnapshot(JwSchoolInfo("T", "离线采集测试"), courses, emptyList(),
                termStartDate = JwSourceCalendar.resolveStartDate(source, type, ""))
            val restored = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot))!!
            assertEquals(snapshot, restored)
            assertEquals("2026-08-31", restored.termStartDate)
            val script = com.lingion.sleepy.ui.screen.imports.JwSchoolApiCapture.scriptFor(type)
            assertNotNull(script)
            assertTrue(script!!.contains("var type = '$type'"))
        }
        assertNull(com.lingion.sleepy.ui.screen.imports.JwSchoolApiCapture.scriptFor("shcc"))
    }

    @Test fun `explicit semester length survives draft even when its last weeks are empty`() {
        assertEquals(20, JwSourceCalendar.resolveWeekCount(ccibe(), "ccibe"))
        for (invalid in listOf("0", "-1", "2.5", "999999999999", "null")) {
            assertEquals(0, JwSourceCalendar.resolveWeekCount("""{"calendar":{"allWeek":$invalid}}""", "ccibe"))
        }
        assertEquals(0, JwSourceCalendar.resolveWeekCount(ccibe(), "unknown"))
        val snapshot = JwImportDraftSnapshot(JwSchoolInfo("T", "日历测试"), emptyList(), emptyList(), termWeekCount = 20)
        assertEquals(snapshot, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot)))
        val old = JSONObject(JwImportDraftCodec.toJson(snapshot)).apply { remove("termWeekCount") }.toString()
        assertEquals(0, JwImportDraftCodec.fromJson(old)!!.termWeekCount)
        val invalid = JSONObject(JwImportDraftCodec.toJson(snapshot)).put("termWeekCount", 1.5).toString()
        assertNull(JwImportDraftCodec.fromJson(invalid))
    }
}
