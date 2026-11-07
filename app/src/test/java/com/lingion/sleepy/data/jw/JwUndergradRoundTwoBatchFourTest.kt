package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwUndergradRoundTwoBatchFourTest {
    private fun imported(type: String, source: String): List<JwCourse> {
        val courses = JwParserRegistry.parserFor(type, source).generateCourseList()
        assertEquals(courses, JwParserRegistry.selectBest(source, type).first)
        assertEquals(courses, JwImportViewModel.parseHtmlStatic(source, type).first)
        val draft = JwImportDraftSnapshot(JwSchoolInfo("T", "协议回归", type = type), courses, emptyList())
        assertEquals(courses, JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(draft))!!.courses)
        return courses
    }

    @Test fun shsmuUsesPinnedMondayBaselineAndBothActualTimes() {
        val source = """{"list":[
          {"Curriculum":"医学基础","Teacher":"王老师","ClassroomAcademy":"A","Start":"2025-09-21 08:00:00","End":"2025-09-21 09:30:00"},
          {"Curriculum":"护理学","Teacher":null,"ClassroomAcademy":null,"Start":"2025-09-22T13:30:00","End":"2025-09-22T15:00:00"}
        ]}"""
        assertEquals(listOf(
            JwCourse("医学基础", "A", "王老师", 7, 1, 2, 1, 1, startTime = "08:00", endTime = "09:30"),
            JwCourse("护理学", "", "", 1, 6, 7, 2, 2, startTime = "13:30", endTime = "15:00"),
        ), imported("shsmu", source))
    }

    @Test fun shsmuKeepsCompleteIntervalWhenOnlyOneEndpointMatchesSchoolClock() {
        val source = """{"list":[
          {"Curriculum":"实验一","Start":"2025-09-23 08:00:00","End":"2025-09-23 09:25:00"},
          {"Curriculum":"实验二","Start":"2025-09-23 08:05:00","End":"2025-09-23 09:30:00"},
          {"Curriculum":"实验三","Start":"2025-09-23 08:05:00","End":"2025-09-23 09:25:00"}
        ]}"""
        assertEquals(listOf(
            JwCourse("实验一", day = 2, startNode = 1, endNode = 2, startWeek = 2, endWeek = 2, startTime = "08:00", endTime = "09:25"),
            JwCourse("实验二", day = 2, startNode = 1, endNode = 2, startWeek = 2, endWeek = 2, startTime = "08:05", endTime = "09:30"),
            JwCourse("实验三", day = 2, startNode = 1, endNode = 2, startWeek = 2, endWeek = 2, startTime = "08:05", endTime = "09:25"),
        ), imported("shsmu", source))
    }

    @Test fun shsmuRejectsInvalidDatesCrossDayAndReversedTimes() {
        for ((start, end) in listOf(
            "2025-09-14 08:00:00" to "2025-09-14 09:30:00",
            "2025-02-30 08:00:00" to "2025-02-30 09:30:00",
            "2025-09-22 24:00:00" to "2025-09-22 25:30:00",
            "2025-09-22 23:00:00" to "2025-09-23 01:00:00",
            "2025-09-22 09:30:00" to "2025-09-22 08:00:00",
            "2025-09-22 08:00:00" to "2025-09-22 08:00:00",
            "unknown" to "2025-09-22 09:30:00",
        )) {
            val source = """{"list":[{"Curriculum":"医学","Start":"$start","End":"$end"}]}"""
            assertEquals(emptyList<JwCourse>(), imported("shsmu", source))
        }
    }

    @Test fun yguUsesBuildingSpecificClocksForThirdAndFourthPeriods() {
        for (building in listOf("文科楼", "商学楼", "音艺楼", "行政楼", "操场", "理科楼")) {
            val special = building != "理科楼"
            assertEquals(listOf(JwCourse("医学", building + "201", "王老师", 7, 3, 4, 1, 8,
                startTime = if (special) "10:05" else "10:20", endTime = if (special) "11:40" else "12:00")),
                JwParserRegistry.parserFor("ygu", yguSource("3,4", "1-8", room = "$building 201")).generateCourseList())
        }
    }

    @Test fun yguDoesNotBroadenSparsePeriodsOrWeeks() {
        assertEquals(listOf(
            JwCourse("医学", "文科楼201", "王老师", 7, 3, 3, 1, 1, 1, "10:05", "10:50"),
            JwCourse("医学", "文科楼201", "王老师", 7, 3, 3, 3, 3, 1, "10:05", "10:50"),
            JwCourse("医学", "文科楼201", "王老师", 7, 3, 3, 7, 7, 1, "10:05", "10:50"),
            JwCourse("医学", "文科楼201", "王老师", 7, 5, 5, 1, 1, 1, "14:00", "14:45"),
            JwCourse("医学", "文科楼201", "王老师", 7, 5, 5, 3, 3, 1, "14:00", "14:45"),
            JwCourse("医学", "文科楼201", "王老师", 7, 5, 5, 7, 7, 1, "14:00", "14:45"),
        ), JwParserRegistry.parserFor("ygu", yguSource("3,5", "1,3,7", "单")).generateCourseList())
    }

    @Test fun yguRejectsUnmappedClocksAndMalformedBounds() {
        for (source in listOf(
            yguSource("12", "1-8"), yguSource("0", "1-8"), yguSource("4-3", "1-8"),
            yguSource("3", "8-1"), yguSource("3", "0,8"), yguSource("3", "367"),
            yguSource("3", "1-8", day = 8), yguSource("3", "1-8", "未知"),
            yguSource("3", "1-8", name = ""), yguSource("3", ""),
            "{}", "{\"data\":[{\"weekList\":[]}]}", "not json",
        )) assertEquals(emptyList<JwCourse>(), imported("ygu", source))
    }

    private fun yguSource(nodes: String, weeks: String, parity: String = "", room: String = "文科楼201", day: Int = 7, name: String = "医学") =
        """{"data":[{"weekList":[{"kcbVoList":[{"xq":$day,"jcs":"$nodes","zcs":"$weeks","dsz":"$parity","kcmc":"$name","rkls":"王老师","jsmc":"$room"}]}]}]}"""
    @Test fun shsmuCalendarUsesTheSameReferenceDateAsItsCourseWeeks() {
        assertEquals("2025-09-15", JwSourceCalendar.resolveStartDate("{\"list\":[]}", "shsmu", "2026-09-07"))
    }
}
