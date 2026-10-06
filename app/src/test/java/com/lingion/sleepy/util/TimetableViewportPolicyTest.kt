package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

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

    @Test
    fun parse_evening_start_falls_back_to_default_on_garbage() {
        assertEquals(
            TimetableViewportPolicy.EVENING_START,
            TimetableViewportPolicy.parseEveningStart("not-a-time")
        )
        assertEquals(
            TimetableViewportPolicy.EVENING_START,
            TimetableViewportPolicy.parseEveningStart(null)
        )
        assertEquals(
            LocalTime.of(19, 30),
            TimetableViewportPolicy.parseEveningStart("19:30")
        )
    }

    @Test
    fun custom_evening_start_beyond_last_slot_keeps_full_table() {
        val result = TimetableViewportPolicy.selectVisibleSlots(
            allCourses = listOf(course(id = 1, startNode = 9)),
            timeSlots = slots,
            visibleDays = (1..5).toSet(),
            timeJson = timeJson,
            autoHideEmptyEvening = true,
            eveningStart = LocalTime.of(23, 0)
        )

        assertEquals(12, result.slots.size)
        assertEquals(0, result.hiddenEveningCount)
    }

    @Test
    fun custom_time_course_reaching_default_evening_keeps_evening_slots() {
        val result = TimetableViewportPolicy.selectVisibleSlots(
            allCourses = listOf(
                course(
                    id = 1,
                    startNode = 8,
                    ownTime = true,
                    startTime = "16:30",
                    endTime = "18:10"
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
    fun base_row_height_is_fixed_when_adaptive_disabled() {
        assertEquals(
            52f,
            TimetableViewportPolicy.baseRowHeightDp(adaptive = false, fitRowHeightDp = 40f, contentScale = 1f),
            0.001f
        )
        assertEquals(
            40f,
            TimetableViewportPolicy.baseRowHeightDp(adaptive = true, fitRowHeightDp = 40f, contentScale = 1f),
            0.001f
        )
    }

    @Test
    fun manual_row_height_scales_both_ways_within_bounds() {
        assertEquals(
            36f,
            TimetableViewportPolicy.manualRowHeightDp(
                baseRowHeightDp = 52f, verticalScale = 0.5f, minRowHeightDp = 36f, maxRowHeightDp = 96f
            ),
            0.001f
        )
        assertEquals(
            96f,
            TimetableViewportPolicy.manualRowHeightDp(
                baseRowHeightDp = 52f, verticalScale = 3f, minRowHeightDp = 36f, maxRowHeightDp = 96f
            ),
            0.001f
        )
        assertEquals(
            52f,
            TimetableViewportPolicy.manualRowHeightDp(
                baseRowHeightDp = 52f, verticalScale = 1f, minRowHeightDp = 36f, maxRowHeightDp = 96f
            ),
            0.001f
        )
    }

    // ===== 长课间留白 redesign: 空隙分钟折进行权重 (替代固定 mealGapExtra 间隙) =====

    @Test
    fun long_break_weight_expansion_is_identity_when_disabled() {
        val w = listOf(1f, 1f, 1f)
        val out = TimetableViewportPolicy.expandWeightsForLongBreaks(
            w, setOf(1), mapOf(1 to 40), periodMinutes = 45, enabled = false)
        assertEquals(w.map { "%.4f".format(it) }, out.map { "%.4f".format(it) })
    }

    @Test
    fun long_break_folds_break_minutes_into_preceding_row_weight() {
        val out = TimetableViewportPolicy.expandWeightsForLongBreaks(
            listOf(1f, 1f, 1f), setOf(1), mapOf(1 to 45), periodMinutes = 45, enabled = true)
        // 45min 空隙 / 45min 主课时 = +1.0 → 行权重翻倍, 时间轴按分钟比例拉长
        assertEquals(listOf(1f, 2f, 1f), out)
    }

    @Test
    fun fractional_break_minutes_scale_fractionally_and_unknown_rows_are_ignored() {
        val out = TimetableViewportPolicy.expandWeightsForLongBreaks(
            listOf(1f, 1f), setOf(1, 7), mapOf(1 to 20, 7 to 60), periodMinutes = 40, enabled = true)
        assertEquals(1.5f, out[1], 1e-5f)   // 20/40 = +0.5
        assertEquals(2, out.size)           // 越界行号忽略, 不新增元素
    }

    @Test
    fun non_positive_period_minutes_falls_back_to_45() {
        val out = TimetableViewportPolicy.expandWeightsForLongBreaks(
            listOf(1f), setOf(0), mapOf(0 to 45), periodMinutes = 0, enabled = true)
        assertEquals(2f, out[0], 1e-5f)
    }

    @Test
    fun per_break_unit_minutes_override_the_period_default() {
        // 交叉验证 minor #3: 非 45 分钟课时制 — 分母取餐段左邻真实分钟 (40),
        // 20min 空隙 → +0.5 行, 而不是 fallback 45 → 20/45。
        val out = TimetableViewportPolicy.expandWeightsForLongBreaks(
            listOf(1f, 1f), setOf(1), mapOf(1 to 20),
            breakUnitMinutes = mapOf(1 to 40),
            periodMinutes = 45, enabled = true)
        assertEquals(1.5f, out[1], 1e-5f)
        // 缺失条目回落 periodMinutes
        val out2 = TimetableViewportPolicy.expandWeightsForLongBreaks(
            listOf(1f, 1f), setOf(1), mapOf(1 to 45),
            breakUnitMinutes = emptyMap(),
            periodMinutes = 45, enabled = true)
        assertEquals(2f, out2[1], 1e-5f)
    }
}
