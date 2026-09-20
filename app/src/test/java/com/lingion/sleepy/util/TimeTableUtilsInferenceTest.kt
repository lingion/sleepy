package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.BreakOption
import com.lingion.sleepy.data.entity.DurationOption
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeTableUtilsInferenceTest {

    private fun row(node: Int, start: String, end: String, edge: TimeTableUtils.EdgeClass? = null) =
        TimeTableUtils.TimeSlotRow(node, start, end, edge)

    @Test
    fun `infers majority duration as primary and assigns minority periods`() {
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "09:50", "10:20"),
            row(4, "10:30", "11:15")
        )

        val config = TimeTableUtils.inferSmartPeriodConfig(rows)

        requireNotNull(config)
        assertEquals(45, config.periodMinutes)
        assertEquals(listOf(45, 45, 30, 45), config.effectivePeriodMinutes())
        assertEquals(listOf("08:00" to "08:45", "08:55" to "09:40", "09:50" to "10:20", "10:30" to "11:15"), config.derive().map { it.start to it.end })
    }

    @Test
    fun `infers all duration and break groups in first-seen deterministic order`() {
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "09:50", "10:20"),
            row(4, "10:30", "11:15"),
            row(5, "12:15", "13:00"),
            row(6, "13:10", "14:00")
        )

        val config = TimeTableUtils.inferSmartPeriodConfig(rows)

        requireNotNull(config)
        assertEquals(45, config.periodMinutes)
        assertEquals(listOf(DurationOption(30, isLong = false), DurationOption(50, isLong = true)), config.durations)
        assertEquals(listOf<Int?>(null, null, 0, null, null, 1), config.periodAssignments)
        assertEquals(listOf(BreakOption(10, isLong = false), BreakOption(60, isLong = true)), config.breaks)
        assertEquals(listOf<Int?>(0, 0, 0, 1, 0), config.transitionAssignments)
        assertEquals(rows.map { it.start to it.end }, config.derive().map { it.start to it.end })
    }

    @Test
    fun `tie between durations chooses the earliest standard node`() {
        val rows = listOf(
            row(1, "08:00", "08:30"),
            row(2, "08:40", "09:25"),
            row(3, "09:35", "10:20"),
            row(4, "10:30", "11:00")
        )

        val config = TimeTableUtils.inferSmartPeriodConfig(rows)

        requireNotNull(config)
        assertEquals(30, config.periodMinutes)
        assertEquals(listOf(DurationOption(45, isLong = true)), config.durations)
        assertEquals(listOf<Int?>(null, 0, 0, null), config.periodAssignments)
        assertEquals(listOf(BreakOption(10)), config.breaks)
        assertEquals(listOf<Int?>(0, 0, 0), config.transitionAssignments)
    }

    @Test
    fun `edge rows are excluded from inference and derivation`() {
        val rows = listOf(
            row(0, "07:00", "07:59", TimeTableUtils.EdgeClass.Before),
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(3, "09:50", "10:35"),
            row(4, "22:00", "23:00", TimeTableUtils.EdgeClass.After)
        )

        val config = TimeTableUtils.inferSmartPeriodConfig(rows)

        requireNotNull(config)
        assertEquals(3, config.totalPeriods)
        assertEquals(listOf("08:00" to "08:45", "08:55" to "09:40", "09:50" to "10:35"), config.derive().map { it.start to it.end })
    }

    @Test
    fun `previous labels and classifications are preserved by minutes`() {
        val previous = SmartPeriodConfig(
            periodMinutes = 45,
            totalPeriods = 4,
            breaks = listOf(BreakOption(10, isLong = false, label = "课间"), BreakOption(60, isLong = true, label = "午休")),
            durations = listOf(DurationOption(30, isLong = true, label = "短课"), DurationOption(45, isLong = false, label = "标准课"))
        )
        val rows = listOf(
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:25"),
            row(3, "10:25", "11:10"),
            row(4, "11:20", "12:05")
        )

        val config = TimeTableUtils.inferSmartPeriodConfig(rows, previous)

        requireNotNull(config)
        assertEquals(DurationOption(30, isLong = true, label = "短课"), config.durations.single())
        assertEquals(BreakOption(10, isLong = false, label = "课间"), config.breaks[0])
        assertEquals(BreakOption(60, isLong = true, label = "午休"), config.breaks[1])
    }

    @Test
    fun `malformed reversed overlapping or noncontiguous standard rows return null`() {
        assertNull(TimeTableUtils.inferSmartPeriodConfig(listOf(row(1, "bad", "08:45"))))
        assertNull(TimeTableUtils.inferSmartPeriodConfig(listOf(row(1, "09:00", "08:45"))))
        assertNull(TimeTableUtils.inferSmartPeriodConfig(listOf(row(1, "08:00", "08:45"), row(2, "08:40", "09:20"))))
        assertNull(TimeTableUtils.inferSmartPeriodConfig(listOf(row(1, "08:00", "08:45"), row(3, "08:55", "09:40"))))
        assertNull(TimeTableUtils.inferSmartPeriodConfig(listOf(row(0, "07:00", "07:30", TimeTableUtils.EdgeClass.Before))))
    }

    @Test
    fun `blank standard times are rejected instead of inferred`() {
        assertNull(TimeTableUtils.inferSmartPeriodConfig(listOf(row(1, "", ""))))
        assertNull(TimeTableUtils.inferSmartPeriodConfig(listOf(row(1, "08:00", ""))))
        assertNull(TimeTableUtils.inferSmartPeriodConfig(listOf(row(1, "08:00", "08:45"), row(2, "", "09:40"))))
    }

    @Test
    fun `round trip preserves standard rows exactly and in node order`() {
        val rows = listOf(
            row(3, "10:00", "10:30"),
            row(1, "08:00", "08:45"),
            row(2, "08:55", "09:40"),
            row(4, "10:40", "11:25")
        )

        val config = TimeTableUtils.inferSmartPeriodConfig(rows)

        requireNotNull(config)
        assertEquals(listOf(1, 2, 3, 4), config.derive().map { it.node })
        assertEquals(listOf("08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:30", "10:40" to "11:25"), config.derive().map { it.start to it.end })
    }
}
