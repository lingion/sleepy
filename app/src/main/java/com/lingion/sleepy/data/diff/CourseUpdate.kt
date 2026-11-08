package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.CourseColorMode

data class CourseUpdateDiff(
    val added: List<CourseEntity>,
    val changed: List<Pair<CourseEntity, CourseEntity>>,
    val removed: List<CourseEntity>,
    val merged: List<CourseEntity>,
)

private fun CourseEntity.identityKey(): String =
    listOf(courseName.trim().lowercase(), day, startNode, step, startWeek, endWeek, type).joinToString("|")

/** 全量精确匹配优先；仅双向唯一的名称/星期候选允许回退，避免输入顺序串换本地字段。 */
fun diffImportedCourses(existing: List<CourseEntity>, incoming: List<CourseEntity>): CourseUpdateDiff {
    val remaining = existing.toMutableList()
    val matches = mutableMapOf<Int, CourseEntity>()
    incoming.forEachIndexed { index, fresh ->
        remaining.firstOrNull { it.identityKey() == fresh.identityKey() }?.let {
            matches[index] = it
            remaining.remove(it)
        }
    }
    val unmatched = incoming.indices.filterNot(matches::containsKey)
        .groupBy { incoming[it].courseName.trim().lowercase() to incoming[it].day }
    unmatched.forEach { (key, indices) ->
        if (indices.size == 1) {
            remaining.filter { (it.courseName.trim().lowercase() to it.day) == key }
                .singleOrNull()?.let {
                    matches[indices.single()] = it
                    remaining.remove(it)
                }
        }
    }
    // 新增节次继承唯一已有课程组；组别名/组色可继承，逐行备注和自定义时间不能复制。
    val groupsByName = existing.groupBy { it.courseName.trim().lowercase() }
        .mapValues { (_, rows) ->
            rows.takeIf { it.map { row -> row.groupId }.distinct().size == 1 && it.first().groupId.isNotBlank() }
        }
    val added = mutableListOf<CourseEntity>()
    val changed = mutableListOf<Pair<CourseEntity, CourseEntity>>()
    val merged = mutableListOf<CourseEntity>()
    incoming.forEachIndexed { index, fresh ->
        val candidate = matches[index]
        if (candidate == null) {
            val group = groupsByName[fresh.courseName.trim().lowercase()]
            val newRow = fresh.copy(
                id = 0,
                groupId = group?.first()?.groupId ?: fresh.groupId,
                alias = group?.first()?.alias ?: fresh.alias,
                color = group?.firstOrNull { it.colorMode == CourseColorMode.GROUP }?.color ?: fresh.color,
            )
            added += newRow
            merged += newRow
        } else {
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
