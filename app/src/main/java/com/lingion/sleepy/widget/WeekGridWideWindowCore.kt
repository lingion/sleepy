package com.lingion.sleepy.widget

import java.time.LocalDate

enum class WeekGridWideMode { ORIGINAL, VERTICAL, HORIZONTAL }
enum class WeekGridWideLabelSide { LEFT, RIGHT }
data class WeekGridWideSettings(
    val mode: WeekGridWideMode = WeekGridWideMode.VERTICAL,
    val labelSide: WeekGridWideLabelSide = WeekGridWideLabelSide.LEFT,
)
internal data class WidePeriod(val node: Int, val startMinute: Int?, val endMinute: Int?)
internal data class PeriodWindow(val startIndex: Int, val count: Int)

/** Policies for the dedicated 4x2 receiver; indices are physical slots, not node numbers. */
internal object WeekGridWideWindowCore {
    fun visibleDates(today: LocalDate, mode: WeekGridWideMode): List<LocalDate> {
        val start = if (mode == WeekGridWideMode.HORIZONTAL) today
        else today.minusDays((today.dayOfWeek.value - 1).toLong())
        val count = if (mode == WeekGridWideMode.HORIZONTAL) 3 else 7
        return List(count) { start.plusDays(it.toLong()) }
    }

    fun visiblePeriods(periods: List<WidePeriod>, nowMinute: Int, capacity: Int): PeriodWindow {
        val count = capacity.coerceAtLeast(4).coerceAtMost(periods.size)
        // Do not skip a period with unresolved timing by borrowing its course's end.
        val finished = periods.takeWhile { it.endMinute?.let { end -> nowMinute >= end } == true }.size
        return PeriodWindow(finished.coerceAtMost(periods.size - count), count)
    }

    fun intersectPeriodSpan(startIndex: Int, endExclusive: Int, window: PeriodWindow): IntRange? {
        val first = maxOf(startIndex, window.startIndex)
        val end = minOf(endExclusive, window.startIndex + window.count)
        return if (first < end) (first - window.startIndex)..(end - window.startIndex - 1) else null
    }
}
