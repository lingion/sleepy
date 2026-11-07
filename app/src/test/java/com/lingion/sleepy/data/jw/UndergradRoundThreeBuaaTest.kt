package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeBuaaTest {
    private fun parse(source: String): List<JwCourse> {
        val type = "buaa"
        val courses = JwParserRegistry.parserFor(type, source).generateCourseList()
        assertEquals(courses, JwParserRegistry.selectBest(source, type).first)
        assertEquals(courses, JwImportViewModel.parseHtmlStatic(source, type).first)
        
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "协议回归", type = type), courses, emptyList())
        assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        return courses
    }

    @Test fun buaaAssociatesEveryTeacherWithTheirOwnWeeks() {
        val source = buaaSource(listOf("王老师[1-5周(单)] 李老师[2-8周(双)]"))
        assertEquals(listOf(
            JwCourse("数学", "A", "王老师", 7, 3, 4, 1, 5, 1, "09:50", "11:25", 2.5f, "课程备注"),
            JwCourse("数学", "A", "李老师", 7, 3, 4, 2, 8, 2, "09:50", "11:25", 2.5f, "课程备注"),
        ), parse(source))
    }

    @Test fun buaaReadsBothWeekFirstFormsAndPlaceOverrides() {
        val source = buaaSource(listOf("1-5周(单),8周 王老师[主讲] B", "2,4,8周 李老师 C 3节"))
        assertEquals(listOf(
            JwCourse("数学", "B", "王老师", 7, 3, 4, 1, 5, 1, "09:50", "11:25", 2.5f, "课程备注"),
            JwCourse("数学", "B", "王老师", 7, 3, 4, 8, 8, 0, "09:50", "11:25", 2.5f, "课程备注"),
            JwCourse("数学", "C", "李老师", 7, 3, 4, 2, 2, 0, "09:50", "11:25", 2.5f, "课程备注"),
            JwCourse("数学", "C", "李老师", 7, 3, 4, 4, 4, 0, "09:50", "11:25", 2.5f, "课程备注"),
            JwCourse("数学", "C", "李老师", 7, 3, 4, 8, 8, 0, "09:50", "11:25", 2.5f, "课程备注"),
        ), parse(source))
    }

    @Test fun buaaRejectsInvalidArrangementInsteadOfDefaultingWholeSemester() {
        for (text in listOf("王老师[8-1周]", "王老师[0-8周]", "王老师[1-367周]", "王老师[未知周]", "1-8周", "A101", "王老师[1-8周] 无法识别尾注"))
            assertEquals(emptyList<JwCourse>(), parse(buaaSource(listOf(text))))
        val source = JSONObject(buaaSource(listOf("王老师[1-8周]")))
        val record = source.getJSONObject("datas").getJSONArray("arrangedList").getJSONObject(0)
        for ((key, value) in listOf("dayOfWeek" to 8, "beginSection" to 0, "endSection" to 2, "beginTime" to "23:00", "endTime" to "")) {
            val copy = JSONObject(source.toString())
            copy.getJSONObject("datas").getJSONArray("arrangedList").getJSONObject(0).put(key, value)
            assertEquals(emptyList<JwCourse>(), parse(copy.toString()))
        }
        record.remove("cellDetail")
        assertEquals(emptyList<JwCourse>(), parse(source.toString()))
    }

    @Test fun buaaKeepsPeriodOnlyDataAndNormalizesInvalidCredit() {
        for (credit in listOf("NaN", "Infinity", "-2")) assertEquals(
            listOf(JwCourse("数学", "A", "王老师", 7, 3, 4, 1, 8, note = "课程备注")),
            parse(buaaSource(listOf("王老师[1-8周]"), credit, "", "")))
    }

    private fun buaaSource(texts: List<String>, credit: String = "2.5", startTime: String = "09:50", endTime: String = "11:25"): String = JSONObject().put("datas", JSONObject().put("arrangedList", JSONArray().put(JSONObject()
        .put("courseName", "数学").put("dayOfWeek", 7).put("beginSection", 3).put("endSection", 4).put("placeName", "A")
        .put("credit", credit).put("beginTime", startTime).put("endTime", endTime)
        .put("titleDetail", JSONArray(List(8) { "" } + "课程备注"))
        .put("cellDetail", JSONArray(texts.map { JSONObject().put("text", it) }))))).toString()
}
