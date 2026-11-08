package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class UndergradRoundThreeHfuAliasTest {
    @Test fun hfuShapeAndThreeAliasesRetainCompleteCourseAssociations() {
        val source = wrap(listOf(row(1, 1, 1, 800, 940, "王老师", "A"), row(2, 7, 8, 1400, 1650, "李老师", "B"), row(1, 2, 5, 1900, 1950, "赵老师", "C")))
        val expected = listOf(
            JwCourse("物理", "A", "王老师", 1, 1, 2, 1, 1, startTime = "08:00", endTime = "09:40"),
            JwCourse("英语", "B", "李老师", 7, 5, 7, 8, 8, startTime = "14:00", endTime = "16:50"),
            JwCourse("物理", "C", "赵老师", 2, 9, 9, 5, 5, startTime = "19:00", endTime = "19:50"))
        assertEquals(expected, JwCumtbParser(source).generateCourseList())
        for (type in listOf("cumtb", "ahnu", "hfu")) assertEquals(type, expected, assertUndergradAJsonImportRoutes(type,source))
    }

    @Test fun absentLessonAndNullableTeacherRoomKeepKnownOccurrenceAndClock() {
        val item = row(99, 2, 5, 1000, 1050, "", "").put("personName", JSONObject.NULL).put("room", JSONObject.NULL)
        assertEquals(listOf(JwCourse("未知", "", "", 2, 3, 3, 5, 5, startTime = "10:00", endTime = "10:50")), JwCumtbParser(wrap(listOf(item))).generateCourseList())
    }

    @Test fun equalNodeInferenceNeverMergesDifferentActualClockPairsOrTeachers() {
        val source = wrap(listOf(row(1, 1, 1, 800, 850, "甲", "A"), row(1, 1, 3, 805, 855, "甲", "A"), row(1, 1, 5, 800, 850, "乙", "B")))
        assertEquals(listOf(JwCourse("物理", "A", "甲", 1, 1, 1, 1, 1, startTime = "08:00", endTime = "08:50"), JwCourse("物理", "A", "甲", 1, 1, 1, 3, 3, startTime = "08:05", endTime = "08:55"), JwCourse("物理", "B", "乙", 1, 1, 1, 5, 5, startTime = "08:00", endTime = "08:50")), JwCumtbParser(source).generateCourseList())
    }

    @Test fun sourceShortDurationBranchIsTwoNodesAndActualTimeStaysExact() {
        assertEquals(listOf(JwCourse("物理", "A", "王老师", 1, 1, 2, 1, 1, startTime = "08:00", endTime = "08:40")), JwCumtbParser(wrap(listOf(row(1, 1, 1, 800, 840, "王老师", "A")))).generateCourseList())
    }

    @Test fun invalidDayWeekClockAndNonIntegralFieldsNeverManufactureOccurrences() {
        val invalid = listOf("weekday" to 8, "weekIndex" to 0, "startTime" to 860, "endTime" to 2400, "endTime" to 800, "weekday" to 1.5, "weekIndex" to 2.5, "startTime" to 800.5, "endTime" to 940.5, "lessonId" to 1.5, "weekIndex" to "bad", "startTime" to JSONObject.NULL)
        for ((key, value) in invalid) assertEquals("$key=$value", emptyList<JwCourse>(), JwCumtbParser(wrap(listOf(row(1,1,1,800,940,"王老师","A").put(key,value)))).generateCourseList())
    }

    private fun row(id: Int, day: Int, week: Int, first: Int, last: Int, teacher: String, room: String) = JSONObject().put("lessonId",id).put("weekday",day).put("weekIndex",week).put("startTime",first).put("endTime",last).put("personName",teacher).put("room",JSONObject().put("nameZh",room))
    private fun wrap(rows: List<JSONObject>) = JSONObject().put("result",JSONObject().put("lessonList",JSONArray().put(JSONObject().put("id",1).put("courseName","物理")).put(JSONObject().put("id",2).put("courseName","英语"))).put("scheduleList",JSONArray(rows))).toString()
}
