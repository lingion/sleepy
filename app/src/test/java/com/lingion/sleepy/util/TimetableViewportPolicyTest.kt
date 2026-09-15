package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableViewportPolicyTest {

    private val timeJson = TimeTableUtils.DEFAULT_TIME_JSON
    private val slots = TimeTableUtils.timeSlotsFor(timeJson)

    private fun course(
        id: Long,
        day: Int = 1,
        startNode: Int = 1,
        step: Int = 1,
        ownTime: Boolean = false,
        startTime: String = "",
        endTime: String = ""
    ) = CourseEntity(
        id = id,
        groupId = "group-$id",
        tableId = 1L,
        courseName = "Course $id",
        day = day,
        startNode = startNode,
        step = step,
        startWeek = 1,
        endWeek = 16,
        color = "#FF6750A4",
        ownTime = ownTime,
        startTime = startTime,
        endTime = endTime
    )

    @Test
    fun no_term_evening_course_hides_trailing_evening_slots() {
        val result = TimetableViewportPolicy.selectVisibleSlots(
            allCourses = listOf(course(id = 1, startNode = 1)),
            timeSlots = slots,
            visibleDays = (1..5).toSet(),
            timeJson = timeJson,
            autoHideEmptyEvening = true
        )

        assertEquals(8, result.slots.size)
        assertEquals(4, result.hiddenEveningCount)
        assertEquals(17, result.slots.last().end.hour)
    }

    @Test
    fun any_term_evening_course_keeps_all_evening_slots() {
        val result = TimetableViewportPolicy.selectVisibleSlots(
            allCourses = listOf(course(id = 1, startNode = 9)),
            timeSlots = slots,
            visibleDays = (1..5).toSet(),
            timeJson = timeJson,
            autoHideEmptyEvening = true
        )

        assertEquals(12, result.slots.size)
        assertEquals(0, result.hiddenEveningCount)
    }

    @Test
    fun custom_course_crossing_18_00_keeps_evening_slots() {
        val result = TimetableViewportPolicy.selectVisibleSlots(
            allCourses = listOf(
                course(
                    id = 1,
                    startNode = 8,
                    ownTime = true,
                    startTime = "17:30",
                    endTime = "18:20"
                )
            ),
            timeSlots = slots,
            visibleDays = (1..5).toSet(),
            timeJson = timeJson,
            autoHideEmptyEvening = true
        )

        assertEquals(12, result.slots.size)
    }

    @Test
    fun evening_course_on_hidden_weekend_does_not_affect_weekday_view() {
        val result = TimetableViewportPolicy.selectVisibleSlots(
            allCourses = listOf(course(id = 1, day = 6, startNode = 9)),
            timeSlots = slots,
            visibleDays = (1..5).toSet(),
            timeJson = timeJson,
            autoHideEmptyEvening = true
        )

        assertEquals(8, result.slots.size)
    }

    @Test
    fun unresolvable_course_keeps_full_table_as_safe_fallback() {
        val result = TimetableViewportPolicy.selectVisibleSlots(
            allCourses = listOf(course(id = 1, startNode = 99)),
            timeSlots = slots,
            visibleDays = (1..5).toSet(),
            timeJson = timeJson,
            autoHideEmptyEvening = true
        )

        assertEquals(12, result.slots.size)
    }

    @Test
    fun disabling_auto_hide_keeps_full_table() {
        val result = TimetableViewportPolicy.selectVisibleSlots(
            allCourses = emptyList(),
            timeSlots = slots,
            visibleDays = (1..5).toSet(),
            timeJson = timeJson,
            autoHideEmptyEvening = false
        )

        assertEquals(12, result.slots.size)
        assertEquals(0, result.hiddenEveningCount)
    }

    @Test
    fun fit_height_respects_weighted_slots_and_readability_bounds() {
        assertEquals(
            36f,
            TimetableViewportPolicy.fitRowHeightDp(
                availableGridHeightDp = 50f,
                slotWeights = listOf(1f, 1f, 0.5f),
                slotCount = 3,
                contentScale = 1f
            ),
            0.001f
        )
        assertEquals(
            56f,
            TimetableViewportPolicy.fitRowHeightDp(
                availableGridHeightDp = 300f,
                slotWeights = listOf(1f, 1f),
                slotCount = 2,
                contentScale = 1f
            ),
            0.001f
        )
    }

    @Test
    fun horizontal_span_does_not_lock_vertical_resize() {
        assertTrue(
            !TimetableViewportPolicy.locksVerticalResize(
                startVerticalSpan = 100f,
                currentVerticalSpan = 102f,
                startHorizontalSpan = 100f,
                currentHorizontalSpan = 140f
            )
        )
    }

    @Test
    fun vertical_span_locks_and_clamps_row_height() {
        assertTrue(
            TimetableViewportPolicy.locksVerticalResize(
                startVerticalSpan = 100f,
                currentVerticalSpan = 140f,
                startHorizontalSpan = 100f,
                currentHorizontalSpan = 102f
            )
        )
        assertEquals(
            96f,
            TimetableViewportPolicy.rowHeightFromVerticalSpan(
                startRowHeightDp = 56f,
                startVerticalSpan = 100f,
                currentVerticalSpan = 300f,
                minRowHeightDp = 36f,
                maxRowHeightDp = 96f
            ),
            0.001f
        )
        assertEquals(
            36f,
            TimetableViewportPolicy.rowHeightFromVerticalSpan(
                startRowHeightDp = 56f,
                startVerticalSpan = 100f,
                currentVerticalSpan = 10f,
                minRowHeightDp = 36f,
                maxRowHeightDp = 96f
            ),
            0.001f
        )
    }
}
