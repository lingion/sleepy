package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity

/** Shared by preview and commit so the switch filters exactly the conflicts shown. */
internal object CourseImportPolicy {
    /** Preserve existing slots (including edge slots); imports only fill missing nodes. */
    fun mergeTimeJson(current: String, incoming: String, requiredNodes: Int): String {
        val currentRows = if (current.isBlank()) emptyList() else TimeTableUtils.parseTimeSlotRows(current)
        val incomingRows = if (incoming.isBlank()) emptyList() else TimeTableUtils.parseTimeSlotRows(incoming)
        val extended = TimeTableUtils.mergeMostComplete(incoming, current, requiredNodes)
        val rows = (TimeTableUtils.parseTimeSlotRows(extended) + incomingRows + currentRows)
            .associateBy { it.node }.values.sortedBy { it.node }
        return if (rows == currentRows.sortedBy { it.node }) current else TimeTableUtils.buildTimeJsonFromRows(rows)
    }

    fun conflicts(a: CourseEntity, b: CourseEntity, timeJson: String = ""): Boolean {
        if (a.day != b.day) return false
        val commonWeeks = maxOf(a.startWeek, b.startWeek)..minOf(a.endWeek, b.endWeek)
        if (commonWeeks.none { a.inWeek(it) && b.inWeek(it) }) return false
        fun interval(c: CourseEntity): Pair<String, String>? = TimeTableUtils.effectiveCourseTime(
            c.ownTime || c.isIrregularTime, c.startTime, c.endTime, c.startNode, c.step, timeJson
        )?.takeIf { (start, end) ->
            runCatching { java.time.LocalTime.parse(start) < java.time.LocalTime.parse(end) }.getOrDefault(false)
        }
        val first = interval(a)
        val second = interval(b)
        if (first != null && second != null) {
            return first.first < second.second && second.first < first.second
        }
        return a.startNode <= b.startNode + b.step - 1 && b.startNode <= a.startNode + a.step - 1
    }

    fun selectIncoming(
        incoming: List<CourseEntity>,
        existing: List<CourseEntity>,
        appendConflicts: Boolean,
        timeJson: String,
    ): List<CourseEntity> = if (appendConflicts) incoming else incoming.filterNot { candidate ->
        existing.any { conflicts(candidate, it, timeJson) }
    }
}
