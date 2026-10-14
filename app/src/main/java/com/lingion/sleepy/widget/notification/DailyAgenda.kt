package com.lingion.sleepy.widget.notification

import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalDate
import java.time.LocalTime

internal data class DailyAgendaItem(val sources: List<DailyScheduleEntry>, val endTime: LocalTime?) {
    val first get() = sources.first()
    fun sourceNames(): String = sources.map { it.table.name }.joinToString(" · ")
    fun timeText(unknownTime: String): String = first.startTime?.let { start ->
        "$start${endTime?.let { "–$it" }.orEmpty()}"
    } ?: unknownTime
    fun line(unknownTime: String, unknownRoom: String): String =
        "${timeText(unknownTime)} ${first.course.courseName} · ${first.course.room.ifBlank { unknownRoom }} [${sourceNames()}]"
}

internal data class DailyAgenda(val date: LocalDate, val items: List<DailyAgendaItem>) {
    val tableCount get() = items.flatMap { it.sources }.map { it.table.id }.distinct().size
    fun preview(limit: Int = 6): Pair<List<DailyAgendaItem>, Int> {
        require(limit > 0)
        return items.take(limit) to (items.size - limit).coerceAtLeast(0)
    }
}

/** Merge only matching occurrences in different tables; incomplete times never identify a duplicate. */
internal fun buildDailyAgenda(date: LocalDate, summary: DailyScheduleSummary): DailyAgenda {
    data class Identity(val date: LocalDate, val start: LocalTime, val end: LocalTime,
        val name: String, val room: String, val teacher: String)
    val groups = mutableListOf<DailyAgendaItem>()
    val identities = mutableMapOf<Identity, MutableList<Int>>()
    summary.entries.forEach { entry ->
        val course = entry.course
        val end = if (course.ownTime) {
            runCatching { LocalTime.parse(course.endTime) }.getOrNull()
        } else {
            TimeTableUtils.parseNodes(entry.table.timeJson)
                .firstOrNull { it.node == course.startNode + course.step - 1 }?.end
        }
        val start = entry.startTime
        val identity = if (start != null && end != null && end > start && course.courseName.isNotBlank() && course.room.isNotBlank()) {
            Identity(date, start, end, course.courseName, course.room, course.teacher)
        } else null
        val index = identity?.let { identities[it] }?.firstOrNull { index ->
            groups[index].sources.none { it.table.id == entry.table.id }
        }
        if (index == null) {
            identity?.let { identities.getOrPut(it) { mutableListOf() }.add(groups.size) }
            groups.add(DailyAgendaItem(listOf(entry), end))
        } else {
            groups[index] = groups[index].copy(sources = groups[index].sources + entry)
        }
    }
    return DailyAgenda(date, groups)
}
