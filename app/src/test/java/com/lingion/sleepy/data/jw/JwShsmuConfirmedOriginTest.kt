package com.lingion.sleepy.data.jw

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwShsmuConfirmedOriginTest {
    private val capture = """{"sleepyShsmuCapture":true,"rangeStart":"2026-10-12","rangeEnd":"2026-10-18","list":[{"Curriculum":"解剖学","Start":"2026-10-13T08:00:00","End":"2026-10-13T09:30:00","Teacher":"张老师","ClassroomAcademy":"实验室"}]}"""
    private fun origin(source: String) = JwSourceCalendar.resolveStartDate(source,"shsmu","")
    private fun parse(source: String) = assertUndergradAJsonImportRoutes(type = "shsmu", source = source)
    private fun actualDate(origin: String, course: JwCourse) = LocalDate.parse(origin).plusWeeks((course.startWeek-1).toLong()).plusDays((course.day-1).toLong())
    private fun bind(date: String, basis: String): String = JwShsmuCaptureCalendar.bind(
        capture, date, JwShsmuCaptureCalendar.Basis.valueOf(basis),
    )
    @Test fun selectedWeekDoesNotBecomeWeekOne() {
        assertEquals("",origin(capture))
        assertTrue(parse(capture).isEmpty())
    }
    @Test fun oldInferredMondayIsRejectedWithoutConfirmedBasis() {
        val bad = JSONObject(capture).put("semesterStartDate","2026-10-12").toString()
        assertEquals("",origin(bad));assertTrue(parse(bad).isEmpty())
    }
    @Test fun selectedDateRangeIsNotAnAcceptedCalendarBasis() {
        val bad = JSONObject(capture).put("semesterStartDate","2026-10-12").put("semesterStartBasis","selected-date-range").toString()
        assertEquals("",origin(bad));assertTrue(parse(bad).isEmpty())
    }
    @Test fun confirmedSemesterOriginGivesWeekSixAndPreservesAllActualFields() {
        val source = bind("2026-09-07","USER_CONFIRMED")
        val course = parse(source).single()
        assertEquals("2026-09-07",origin(source));assertEquals(6,course.startWeek)
        assertEquals(LocalDate.of(2026,10,13),actualDate(origin(source),course))
        assertEquals("08:00",course.startTime);assertEquals("09:30",course.endTime)
        assertEquals("张老师",course.teacher);assertEquals("实验室",course.room)
        assertEquals("解剖学", course.name); assertEquals(2, course.day)
        assertEquals(1, course.startNode); assertEquals(2, course.endNode)
        assertEquals(6, course.endWeek); assertEquals(0, course.type)
    }
    @Test fun existingTableDateIsAnExplicitIndependentCalendarOrigin() {
        val source = bind("2026-09-14","EXISTING_TABLE")
        val course=parse(source).single();assertEquals(5,course.startWeek)
        assertEquals(LocalDate.of(2026,10,13),actualDate(origin(source),course))
    }
    @Test fun changingFirstWeekRebasesCoursesWithoutMovingLessonDates() {
        val source=bind("2026-09-07","USER_CONFIRMED");val courses=parse(source)
        val rebased = JwShsmuCaptureCalendar.rebase(courses, "2026-09-07", "2026-09-14")
        assertEquals(5,rebased.single().startWeek)
        assertEquals(actualDate("2026-09-07",courses.single()),actualDate("2026-09-14",rebased.single()))
        assertEquals(courses.single().copy(startWeek=5,endWeek=5),rebased.single())
    }
    @Test fun confirmedOriginAfterCapturedLessonIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { bind("2026-10-19","USER_CONFIRMED") }
    }
    @Test fun historicalUnmarkedPayloadRetainsItsPinnedSourceBaseline() {
        val old=JSONObject(capture).apply { remove("sleepyShsmuCapture");getJSONArray("list").getJSONObject(0).put("Start","2025-09-16T08:00:00").put("End","2025-09-16T09:30:00") }.toString()
        assertEquals("2025-09-15",origin(old));assertEquals(1,parse(old).single().startWeek)
    }
}
