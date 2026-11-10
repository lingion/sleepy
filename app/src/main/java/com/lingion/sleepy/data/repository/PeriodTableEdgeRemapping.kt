package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.TimeTableUtils

/** Preserve edge-course identity when adding standard periods shifts After node numbers. */
internal fun remapRenumberedEdgeCourses(
    courses: List<CourseEntity>,
    oldTimeJson: String,
    newTimeJson: String
): List<CourseEntity> {
    val oldEdges = TimeTableUtils.parseTimeSlotRows(oldTimeJson).filter { it.edgeClass != null }
    if (oldEdges.isEmpty()) return courses
    val newEdges = TimeTableUtils.parseTimeSlotRows(newTimeJson).filter { it.edgeClass != null }
    val identities = mutableMapOf<Int, Int>()
    TimeTableUtils.EdgeClass.entries.forEach { direction ->
        val previous = oldEdges.filter { it.edgeClass == direction }.sortedBy { it.node }
        val next = newEdges.filter { it.edgeClass == direction }.sortedBy { it.node }
        if (previous.size == next.size) {
            // Adding a standard period preserves the order of each edge group, even when
            // its clock was edited in the same draft. Clocks are not stable identities.
            previous.zip(next).forEach { (old, updated) -> identities[old.node] = updated.node }
        } else {
            // An edge was added or deleted: ordinal pairing could attach a removed edge's
            // course to its neighbour. Only migrate surviving unambiguous clock matches.
            previous.forEach { old ->
                next.singleOrNull { it.start == old.start && it.end == old.end }
                    ?.let { identities[old.node] = it.node }
            }
        }
    }
    return courses.map { course ->
        if (!course.isIrregularNode || course.step < 1) return@map course
        val start = identities[course.startNode] ?: return@map course
        val end = identities[course.startNode + course.step - 1] ?: return@map course
        if (end - start + 1 != course.step) return@map course
        course.copy(startNode = start)
    }
}
