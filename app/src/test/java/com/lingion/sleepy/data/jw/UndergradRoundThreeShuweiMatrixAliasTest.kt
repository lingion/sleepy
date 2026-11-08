package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeShuweiMatrixAliasTest {
    @Test fun matrixPositionUsesDeclaredUnitCountAndKeepsEveryTeacherRoom() {
        val source = matrix(32, listOf(31 to course("物理(理论)", "王老师", "A", "010001001"), 31 to course("英语", "李老师", "B", "0010001"), 32 to course("数学", "陈老师", "C", "0111")))
        assertEquals(listOf(
            JwCourse("物理(理论)", "A", "王老师", 1, 32, 32, 1, 1), JwCourse("物理(理论)", "A", "王老师", 1, 32, 32, 5, 5), JwCourse("物理(理论)", "A", "王老师", 1, 32, 32, 8, 8),
            JwCourse("英语", "B", "李老师", 1, 32, 32, 2, 2), JwCourse("英语", "B", "李老师", 1, 32, 32, 6, 6),
            JwCourse("数学", "C", "陈老师", 2, 1, 1, 1, 1), JwCourse("数学", "C", "陈老师", 2, 1, 1, 2, 2), JwCourse("数学", "C", "陈老师", 2, 1, 1, 3, 3),
        ), JwShuweiParser(source).generateCourseList())
    }

    @Test fun matrixUsesCourseUnitsLengthThenSourceDefaultTwelve() {
        val base = JSONObject(matrix(12, listOf(12 to course("物理", "王老师", "A", "010"))))
        base.remove("unitCount")
        assertEquals(listOf(JwCourse("物理", "A", "王老师", 2, 1, 1, 1, 1)), JwShuweiParser(base.toString()).generateCourseList())
        base.put("courseUnits", JSONArray(List(4) { JSONObject() }))
        assertEquals(listOf(JwCourse("物理", "A", "王老师", 4, 1, 1, 1, 1)), JwShuweiParser("index.js');" + base).generateCourseList())
    }

    @Test fun matrixDoesNotShiftReservedWeekZeroOrAcceptMalformedBitsAndCounts() {
        val source = matrix(12, listOf(0 to course("物理", "王老师", "A", "110")))
        assertEquals(listOf(JwCourse("物理", "A", "王老师", 1, 1, 1, 1, 1)), JwShuweiParser(source).generateCourseList())
        for (mask in listOf("100", "", "01x1", "0000")) assertEquals(emptyList<JwCourse>(), JwShuweiParser(matrix(12, listOf(0 to course("物理", "王老师", "A", mask)))).generateCourseList())
        for (count in listOf(0, -1)) assertEquals(emptyList<JwCourse>(), JwShuweiParser(matrix(count, listOf(0 to course("物理", "王老师", "A", "01")))).generateCourseList())
        assertEquals(emptyList<JwCourse>(), JwShuweiParser(matrix(12, listOf(84 to course("物理", "王老师", "A", "01")))).generateCourseList())
    }

    @Test fun allEightSourceAliasesReachTheMatrixContract() {
        val source = matrix(12, listOf(0 to course("物理", "王老师", "A", "010001")))
        val expected = listOf(JwCourse("物理", "A", "王老师", 1, 1, 1, 1, 1), JwCourse("物理", "A", "王老师", 1, 1, 1, 5, 5))
        for (type in listOf("shuwei_m", "shuwei_json", "xatu_shuwei", "sias_shuwei", "snut_shuwei", "uestc_shuwei", "hunnu_shuwei", "xsyu_shuwei"))
            assertEquals(type, expected, assertUndergradAJsonImportRoutes(type,source))
    }

    private fun course(name: String, teacher: String, room: String, weeks: String) = JSONObject().put("courseName", name).put("teacherName", teacher).put("roomName", room).put("vaildWeeks", weeks)
    private fun matrix(units: Int, entries: List<Pair<Int, JSONObject>>): String {
        val slots = JSONArray()
        repeat((entries.maxOfOrNull { it.first } ?: 0) + 1) { slots.put(JSONArray()) }
        for ((index, item) in entries) slots.getJSONArray(index).put(item)
        return JSONObject().put("unitCount", units).put("activities", slots).toString()
    }
}
