package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeShuweiStudentAliasTest {
    @Test fun studentTablesAndAllAliasesPreserveCompleteFieldAssociations() {
        val source = JSONObject().put("studentTableVms", JSONArray().put(JSONObject().put("activities", JSONArray().put(activity("物理", 1, 31, 32, JSONArray(listOf(1, 5, 9)), "A", "王老师")))))
            .put("studentTableVm", JSONObject().put("activities", JSONArray().put(activity("英语", 7, 3, 4, JSONArray(listOf(2, 6)), "B", "李老师")))).toString()
        val expected = listOf(1, 5, 9).map { JwCourse("物理", "A", "王老师", 1, 31, 32, it, it, credit = 2.5f, note = "CODE备注") } +
            listOf(2, 6).map { JwCourse("英语", "B", "李老师", 7, 3, 4, it, it, credit = 2.5f, note = "CODE备注") }
        assertEquals(expected, JwEams5Parser(source).generateCourseList())
        for (type in listOf("nwpu", "jmu", "shuwei_new")) assertEquals(type, expected, assertUndergradAJsonImportRoutes(type,source))
    }

    @Test fun sourceNumericWeeksKeepEverySparseOccurrenceAndDuplicateHandling() {
        val source = source(JSONArray(listOf(8, 2, 8, 5)))
        assertEquals(listOf(2, 5, 8).map { JwCourse("物理", "A", "王老师", 1, 3, 4, it, it, credit = 2.5f, note = "CODE备注") },
            JwEams5Parser(source).generateCourseList())
    }

    @Test fun existingTextWeekExtensionRejectsMalformedAndEmptyParityRanges() {
        for (weeks in listOf("1-bad", "8-1", "1,garbage5", "6-6单", "0-8", "1-8(单双)", "1-8单(双)", "1-8,"))
            assertEquals(weeks, emptyList<JwCourse>(), JwEams5Parser(source(weeks)).generateCourseList())
        assertEquals(listOf(JwCourse("物理", "A", "王老师", 1, 3, 4, 2, 8, 2, credit = 2.5f, note = "CODE备注"), JwCourse("物理", "A", "王老师", 1, 3, 4, 11, 15, 1, credit = 2.5f, note = "CODE备注")),
            JwEams5Parser(source("1-8周(双),11-15 odd")).generateCourseList())
    }

    @Test fun numericWeekArrayRejectsInvalidMembersInsteadOfKeepingAPartialCourse() {
        for (weeks in listOf(JSONArray(listOf(1, "bad", 5)), JSONArray(listOf(0, 2)), JSONArray(listOf(1.5, 5))))
            assertEquals(emptyList<JwCourse>(), JwEams5Parser(source(weeks)).generateCourseList())
    }

    @Test fun studentInvalidBoundsRejectAndInvalidCreditBecomesZero() {
        for ((key, value) in listOf("weekday" to 8, "startUnit" to 0, "endUnit" to 2, "courseName" to "")) {
            val item = activity("物理", 1, 3, 4, JSONArray(listOf(2)), "A", "王老师").put(key, value)
            assertEquals(emptyList<JwCourse>(), JwEams5Parser(wrap(item)).generateCourseList())
        }
        for (credit in listOf("NaN", "Infinity", "-2")) {
            val item = activity("物理", 1, 3, 4, JSONArray(listOf(2)), "A", "王老师").put("credits", credit)
            assertEquals(listOf(JwCourse("物理", "A", "王老师", 1, 3, 4, 2, 2, note = "CODE备注")), JwEams5Parser(wrap(item)).generateCourseList())
        }
    }

    private fun activity(name: String, day: Int, first: Int, last: Int, weeks: Any, room: String, teacher: String) = JSONObject().put("courseName", name).put("weekday", day).put("startUnit", first).put("endUnit", last)
        .put("weekIndexes", weeks).put("room", room).put("teachers", JSONArray(listOf(teacher))).put("credits", 2.5).put("lessonCode", "CODE").put("lessonRemark", "备注")
    private fun source(weeks: Any) = wrap(activity("物理", 1, 3, 4, weeks, "A", "王老师"))
    private fun wrap(item: JSONObject) = JSONObject().put("studentTableVm", JSONObject().put("activities", JSONArray().put(item))).toString()
}
