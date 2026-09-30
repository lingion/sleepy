package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.json.JSONArray
import java.time.Duration
import java.time.LocalTime

/** A conservative visual break inferred from adjacent configured bell slots. */
object MealBreakDetector {
    enum class Zone { MIDDAY, EVENING }

    data class Break(val afterRowIndex: Int, val minutes: Long, val zone: Zone)

    /** Returns row indexes (zero based) after which the grid should leave a wider gap. */
    fun detect(timeJson: String, courses: List<CourseEntity>): List<Break> {
        val array = runCatching { JSONArray(timeJson) }.getOrNull() ?: return emptyList()
        if (array.length() < 2) return emptyList()
        // parseTimeSlotRows supplies default bell times for malformed rows. For this visual
        // heuristic, missing data must mean "no inference", never a guessed meal break.
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: return emptyList()
            if (!row.has("node") || !row.has("start") || !row.has("end")) return emptyList()
            if (parse(row.optString("start")) == null || parse(row.optString("end")) == null) return emptyList()
        }
        val rows = TimeTableUtils.parseTimeSlotRows(timeJson)
        if (rows.size != array.length() || rows.any { row ->
                val start = parse(row.start) ?: return@any true
                val end = parse(row.end) ?: return@any true
                !end.isAfter(start)
            }) return emptyList()
        val candidates = mutableListOf<Break>()
        for (i in 0 until rows.lastIndex) {
            val left = rows[i]
            val right = rows[i + 1]
            if (left.edgeClass != null || right.edgeClass != null || right.node != left.node + 1) continue
            val leftEnd = parse(left.end) ?: continue
            val rightStart = parse(right.start) ?: continue
            val gapMinutes = runCatching { Duration.between(leftEnd, rightStart).toMinutes() }.getOrDefault(0)
            if (gapMinutes <= 45 || gapMinutes > 240 || !rightStart.isAfter(leftEnd)) continue
            val zone = when (leftEnd.plusMinutes(gapMinutes / 2)) {
                in LocalTime.of(10, 30)..LocalTime.of(14, 30) -> Zone.MIDDAY
                in LocalTime.of(16, 30)..LocalTime.of(20, 30) -> Zone.EVENING
                else -> continue
            }
            if (courses.any { courseCrosses(it, left.node, right.node, leftEnd, rightStart, timeJson) }) continue
            candidates += Break(i, gapMinutes, zone)
        }
        return Zone.entries.flatMap { zone ->
            candidates.filter { it.zone == zone }.singleOrNull()?.let(::listOf) ?: emptyList()
        }.sortedBy { it.afterRowIndex }
    }

    private fun courseCrosses(
        course: CourseEntity,
        leftNode: Int,
        rightNode: Int,
        gapStart: LocalTime,
        gapEnd: LocalTime,
        timeJson: String
    ): Boolean {
        if (!course.isIrregularTime && course.startNode <= leftNode && course.startNode + course.step - 1 >= rightNode) return true
        val interval = TimeTableUtils.effectiveCourseTime(course.isIrregularTime, course.startTime, course.endTime,
            course.startNode, course.step, timeJson) ?: return false
        val start = parse(interval.first) ?: return false
        val end = parse(interval.second) ?: return false
        return start.isBefore(gapEnd) && end.isAfter(gapStart)
    }

    private fun parse(value: String): LocalTime? = runCatching { LocalTime.parse(value.trim()) }.getOrNull()
}
