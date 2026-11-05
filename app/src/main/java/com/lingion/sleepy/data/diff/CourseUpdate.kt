package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity

data class CourseUpdateDiff(
    val added: List<CourseEntity>,
    val changed: List<Pair<CourseEntity, CourseEntity>>,
    val removed: List<CourseEntity>,
    val merged: List<CourseEntity>,
)

private fun CourseEntity.identityKey(): String =
    listOf(courseName.trim().lowercase(), day, startNode, step, startWeek, endWeek, type).joinToString("|")

/** Matches stable rows first, then a unique name/day candidate; ambiguous rows stay add/remove. */
fun diffImportedCourses(existing: List<CourseEntity>, incoming: List<CourseEntity>): CourseUpdateDiff {
    val remaining = existing.toMutableList()
    val added = mutableListOf<CourseEntity>()
    val changed = mutableListOf<Pair<CourseEntity, CourseEntity>>()
    val merged = mutableListOf<CourseEntity>()
    incoming.forEach { fresh ->
        val exact = remaining.firstOrNull { it.identityKey() == fresh.identityKey() }
        val candidate = exact ?: run {
            val matches = remaining.filter {
                it.courseName.trim().equals(fresh.courseName.trim(), true) && it.day == fresh.day
            }
            matches.singleOrNull()
        }
        if (candidate == null) {
            added += fresh
            merged += fresh
        } else {
            remaining.remove(candidate)
            val preserved = fresh.copy(
                id = candidate.id, groupId = candidate.groupId,
                alias = candidate.alias, note = candidate.note,
                color = candidate.color, colorMode = candidate.colorMode,
                ownTime = candidate.ownTime, isIrregularNode = candidate.isIrregularNode,
                isIrregularTime = candidate.isIrregularTime,
                startTime = candidate.startTime, endTime = candidate.endTime,
                credit = candidate.credit, level = candidate.level, fixedDate = candidate.fixedDate
            )
            merged += preserved
            if (candidate != preserved) changed += candidate to preserved
        }
    }
    return CourseUpdateDiff(added, changed, remaining, merged)
}
