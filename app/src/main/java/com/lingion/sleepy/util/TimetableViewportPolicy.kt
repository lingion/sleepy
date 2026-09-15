package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.TimeSlot
import java.time.LocalTime
import kotlin.math.abs

/**
 * Pure layout policy for the cards timetable.
 *
 * This deliberately stays independent from Compose so the most important parts of
 * adaptive timetable sizing can be verified with JVM tests.
 */
object TimetableViewportPolicy {

    const val DEFAULT_ROW_DP = 56f
    const val MIN_ROW_DP = 36f
    const val MAX_ROW_DP = 96f
    const val VERTICAL_GESTURE_THRESHOLD_DP = 8f
    const val VERTICAL_DOMINANCE_RATIO = 1.25f
    val EVENING_START: LocalTime = LocalTime.of(18, 0)

    data class VisibleSlots(
        val slots: List<TimeSlot>,
        val hiddenEveningCount: Int = 0
    )

    /**
     * Hide only trailing slots starting at 18:00 or later when the whole term has
     * no course intersecting the evening. A malformed or unresolvable course keeps
     * the full timetable visible as a safety fallback.
     */
    fun selectVisibleSlots(
        allCourses: List<CourseEntity>,
        timeSlots: List<TimeSlot>,
        visibleDays: Set<Int>,
        timeJson: String?,
        autoHideEmptyEvening: Boolean,
        eveningStart: LocalTime = EVENING_START
    ): VisibleSlots {
        if (!autoHideEmptyEvening || timeSlots.isEmpty()) return VisibleSlots(timeSlots)

        val firstEvening = timeSlots.indexOfFirst { it.start >= eveningStart }
        if (firstEvening < 0) return VisibleSlots(timeSlots)

        val relevantCourses = allCourses.filter { it.day in visibleDays }
        val hasUnknownCourse = relevantCourses.any {
            courseInterval(it, timeSlots, timeJson) == null
        }
        if (hasUnknownCourse) return VisibleSlots(timeSlots)

        val hasEveningCourse = relevantCourses.any { course ->
            val interval = courseInterval(course, timeSlots, timeJson) ?: return@any false
            interval.second > eveningStart
        }
        if (hasEveningCourse) return VisibleSlots(timeSlots)

        return VisibleSlots(
            slots = timeSlots.take(firstEvening),
            hiddenEveningCount = timeSlots.size - firstEvening
        )
    }

    /** Calculate a readable fit height without allowing a short timetable to grow excessively. */
    fun fitRowHeightDp(
        availableGridHeightDp: Float,
        slotWeights: List<Float>?,
        slotCount: Int,
        contentScale: Float
    ): Float {
        val safeScale = contentScale.coerceIn(0.7f, 1.3f)
        val minRow = MIN_ROW_DP * safeScale
        val defaultRow = DEFAULT_ROW_DP * safeScale
        val totalWeight = if (slotWeights.isNullOrEmpty()) {
            slotCount.coerceAtLeast(1).toFloat()
        } else {
            slotWeights.sum().coerceAtLeast(1f)
        }
        return (availableGridHeightDp / totalWeight)
            .coerceIn(minRow, defaultRow)
    }

    fun manualRowHeightDp(
        fitRowHeightDp: Float,
        verticalScale: Float,
        contentScale: Float
    ): Float {
        val safeScale = contentScale.coerceIn(0.7f, 1.3f)
        return (fitRowHeightDp * verticalScale.coerceAtLeast(1f))
            .coerceIn(MIN_ROW_DP * safeScale, MAX_ROW_DP * safeScale)
    }

    /** True only after a two-finger gesture has a clear vertical intent. */
    fun locksVerticalResize(
        startVerticalSpan: Float,
        currentVerticalSpan: Float,
        startHorizontalSpan: Float,
        currentHorizontalSpan: Float,
        thresholdDp: Float = VERTICAL_GESTURE_THRESHOLD_DP,
        dominanceRatio: Float = VERTICAL_DOMINANCE_RATIO
    ): Boolean {
        val verticalDelta = abs(currentVerticalSpan - startVerticalSpan)
        val horizontalDelta = abs(currentHorizontalSpan - startHorizontalSpan)
        return verticalDelta >= thresholdDp &&
            verticalDelta >= horizontalDelta * dominanceRatio
    }

    fun rowHeightFromVerticalSpan(
        startRowHeightDp: Float,
        startVerticalSpan: Float,
        currentVerticalSpan: Float,
        minRowHeightDp: Float,
        maxRowHeightDp: Float
    ): Float {
        if (startVerticalSpan <= 0f) return startRowHeightDp.coerceIn(minRowHeightDp, maxRowHeightDp)
        val ratio = (currentVerticalSpan / startVerticalSpan).coerceAtLeast(0.1f)
        return (startRowHeightDp * ratio).coerceIn(minRowHeightDp, maxRowHeightDp)
    }

    private fun courseInterval(
        course: CourseEntity,
        timeSlots: List<TimeSlot>,
        timeJson: String?
    ): Pair<LocalTime, LocalTime>? {
        val mapped = timeJson?.let {
            TimeTableUtils.courseTimeParts(
                course.startNode,
                course.step,
                it,
                course.ownTime,
                course.startTime,
                course.endTime
            )
        }
        val parts = mapped ?: run {
            val endNode = course.startNode + course.step.coerceAtLeast(1) - 1
            val first = timeSlots.firstOrNull { slot -> slot.nodeStart == course.startNode }
            val last = timeSlots.firstOrNull { slot -> slot.nodeStart == endNode }
            if (first == null || last == null) return null
            first.displayStart to last.displayEnd
        }
        val start = runCatching { LocalTime.parse(parts.first) }.getOrNull() ?: return null
        val end = runCatching { LocalTime.parse(parts.second) }.getOrNull() ?: return null
        return if (end > start) start to end else null
    }
}
