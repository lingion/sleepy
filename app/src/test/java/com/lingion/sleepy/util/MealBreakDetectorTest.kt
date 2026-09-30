package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealBreakDetectorTest {
    private val timetable = """[
      {"node":1,"start":"08:00","end":"08:45"},
      {"node":2,"start":"08:55","end":"09:40"},
      {"node":3,"start":"10:40","end":"11:25"},
      {"node":4,"start":"13:00","end":"13:45"},
      {"node":5,"start":"14:00","end":"14:45"},
      {"node":6,"start":"18:30","end":"19:15"},
      {"node":7,"start":"19:30","end":"20:15"}
    ]"""

    @Test
    fun finds_unique_midday_and_evening_gaps() {
        val result = MealBreakDetector.detect(timetable, emptyList())
        assertEquals(listOf(2, 4), result.map { it.afterRowIndex })
    }

    @Test
    fun rejects_a_candidate_crossed_by_a_course() {
        val crossing = CourseEntity(
            groupId = "g", tableId = 1, courseName = "Long class", day = 1,
            startNode = 3, step = 2, startWeek = 1, endWeek = 16, color = "#FF000000"
        )
        assertTrue(MealBreakDetector.detect(timetable, listOf(crossing)).none { it.afterRowIndex == 2 })
    }

    @Test
    fun rejects_ambiguous_multiple_midday_gaps() {
        val ambiguous = """[
          {"node":1,"start":"10:40","end":"11:10"},
          {"node":2,"start":"12:00","end":"12:30"},
          {"node":3,"start":"13:30","end":"14:00"}
        ]"""
        assertTrue(MealBreakDetector.detect(ambiguous, emptyList()).isEmpty())
    }

    @Test
    fun malformed_rows_do_not_fall_back_to_guessed_default_times() {
        assertTrue(MealBreakDetector.detect("[{},{}]", emptyList()).isEmpty())
    }
}
