package com.lingion.sleepy.data.imports

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalTime

internal data class CourseWindow(val start: LocalTime, val end: LocalTime)

internal fun courseWindow(course: CourseEntity, timeJson: String): CourseWindow? {
    val custom = course.ownTime || course.isIrregularTime
    val endpoints = if (custom) {
        course.startTime to course.endTime
    } else {
        // parseNodes returns empty for malformed JSON, rather than inventing default slots.
        if (course.step <= 0) return null
        val lastNode = course.startNode.toLong() + course.step - 1
        val rows = TimeTableUtils.parseNodes(timeJson).filter {
            it.node.toLong() in course.startNode.toLong()..lastNode
        }
        // Every covered slot must exist exactly once and have a valid clock window,
        // including before-class slots whose node numbers may be zero or negative.
        if (rows.size != course.step || rows.map { it.node }.distinct().size != course.step ||
            rows.any { it.start >= it.end }) return null
        rows.first().start.toString() to rows.last().end.toString()
    }
    val start = runCatching { LocalTime.parse(endpoints.first.trim()) }.getOrNull() ?: return null
    val end = runCatching { LocalTime.parse(endpoints.second.trim()) }.getOrNull() ?: return null
    return if (start < end) CourseWindow(start, end) else null
}

internal fun activeWeeks(course: CourseEntity, maxWeek: Int): Set<Int> =
    if (maxWeek <= 0) emptySet() else (1..maxWeek).filterTo(linkedSetOf()) { course.inWeek(it) }

/** Returns certain overlap weeks, or possible weeks with a node-only uncertain relation. */
internal fun relation(
    first: CourseEntity, second: CourseEntity, firstJson: String, secondJson: String,
    maxWeek: Int
): Pair<Set<Int>, RelationConfidence>? {
    if (first.day != second.day || first.day !in 1..7) return null
    val weeks = activeWeeks(first, maxWeek).intersect(activeWeeks(second, maxWeek))
    if (weeks.isEmpty()) return null
    val a = courseWindow(first, firstJson)
    val b = courseWindow(second, secondJson)
    if (a != null && b != null) {
        return if (a.start < b.end && b.start < a.end) weeks to RelationConfidence.Certain else null
    }
    val aEnd = first.startNode.toLong() + first.step
    val bEnd = second.startNode.toLong() + second.step
    return if (first.step > 0 && second.step > 0 && first.startNode < bEnd && second.startNode < aEnd)
        weeks to RelationConfidence.Uncertain else null
}

internal fun sameArrangement(
    first: CourseEntity, second: CourseEntity, firstJson: String, secondJson: String,
    maxWeek: Int, sourceInternal: Boolean, authoritativeGroups: Boolean
): Boolean {
    if (sourceInternal && authoritativeGroups && first.groupId != second.groupId) return false
    if (first.courseName.trim().lowercase() != second.courseName.trim().lowercase() ||
        first.teacher.trim().lowercase() != second.teacher.trim().lowercase() ||
        first.room.trim().lowercase() != second.room.trim().lowercase() || first.day != second.day ||
        activeWeeks(first, maxWeek) != activeWeeks(second, maxWeek)) return false
    val a = courseWindow(first, firstJson) ?: return false
    val b = courseWindow(second, secondJson) ?: return false
    if (a != b) return false
    // Conservatively preserve either side's distinct user-entered metadata.
    return first.note.trim() == second.note.trim() &&
        first.alias.trim() == second.alias.trim() &&
        first.credit == second.credit && first.level == second.level
}

/** Split the actual remaining occurrences, retaining all original metadata and one original ID. */
internal fun remainingRows(original: CourseEntity, remaining: Set<Int>): List<CourseEntity> {
    if (remaining.isEmpty()) return emptyList()
    val stride = if (original.type == 1 || original.type == 2) 2 else 1
    val runs = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    for (week in remaining.sorted()) {
        if (current.isNotEmpty() && week != current.last() + stride) {
            runs += current
            current = mutableListOf()
        }
        current += week
    }
    if (current.isNotEmpty()) runs += current
    return runs.mapIndexed { index, weeks -> original.copy(
        id = if (index == 0) original.id else 0,
        startWeek = weeks.first(), endWeek = weeks.last()
    ) }
}

/** A source group may only reuse a target group ID when its original-name identity agrees. */
internal fun isolateGroups(
    incoming: List<Pair<Int, CourseEntity>>, retained: List<CourseEntity>
): List<Pair<Int, CourseEntity>> {
    val identities = linkedMapOf<String, String>()
    retained.forEach { identities.putIfAbsent(it.groupId, it.courseName.trim().lowercase()) }
    val mapping = linkedMapOf<Pair<String, String>, String>()
    return incoming.map { (index, row) ->
        val identity = row.courseName.trim().lowercase()
        val key = row.groupId to identity
        val group = mapping.getOrPut(key) {
            val requested = row.groupId.takeIf { it.isNotBlank() } ?: "import:$index"
            if (requested !in identities || identities[requested] == identity) {
                identities[requested] = identity
                requested
            } else {
                var suffix = 1
                var candidate = "import:$index:$suffix"
                while (candidate in identities) {
                    suffix++
                    candidate = "import:$index:$suffix"
                }
                identities[candidate] = identity
                candidate
            }
        }
        index to row.copy(groupId = group)
    }
}
