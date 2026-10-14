package com.lingion.sleepy.widget.notification

import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.data.entity.TimeTableEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.Duration

internal data class DailyAgendaItem(val sources: List<DailyScheduleEntry>, val endTime: LocalTime?) {
    val first get() = sources.first()
    fun sourceNames(unknownTable: String): String = sources.map { it.table.name.ifBlank { unknownTable } }.joinToString(" · ")
    fun timeText(unknownTime: String): String = first.startTime?.let { start ->
        "$start${endTime?.let { "–$it" }.orEmpty()}"
    } ?: unknownTime
    fun notificationLine(unknownTime: String, unknownRoom: String, sourceLabels: Map<Long, String>): String {
        val source = sources.mapNotNull { sourceLabels[it.table.id] }.joinToString("／")
        return "${first.startTime ?: unknownTime} · ${first.course.courseName} · ${first.course.room.ifBlank { unknownRoom }}" +
            if (source.isEmpty()) "" else "〔$source〕"
    }
}

internal data class DailyAgenda(val date: LocalDate, val items: List<DailyAgendaItem>, val sourceTables: List<TimeTableEntity> = emptyList()) {
    fun notificationSources(unknownTable: String): Map<Long, String> = notificationSourceLabels(sourceTables, unknownTable)
    val tableCount get() = items.flatMap { it.sources }.map { it.table.id }.distinct().size
    fun preview(limit: Int = 6): Pair<List<DailyAgendaItem>, Int> {
        require(limit > 0)
        return items.take(limit) to (items.size - limit).coerceAtLeast(0)
    }
}

/** Merge only matching occurrences in different tables; incomplete times never identify a duplicate. */
internal fun buildDailyAgenda(
    date: LocalDate,
    summary: DailyScheduleSummary,
    sourceTables: List<TimeTableEntity> = summary.entries.map { it.table }.distinctBy { it.id }
): DailyAgenda {
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
    return DailyAgenda(date, groups, sourceTables)
}

/** Display-only middle ellipsis preserves both ends; every final label must remain distinguishable. */
internal fun notificationSourceLabels(tables: List<TimeTableEntity>, unknownTable: String): Map<Long, String> {
    val distinct = tables.distinctBy { it.id }
    if (distinct.size <= 1) return emptyMap()
    var labels = distinct.associate { table ->
        val name = table.name.trim().ifBlank { unknownTable }
        val points = name.codePoints().toArray()
        table.id to if (points.size <= 16) name else
            String(points, 0, 8) + "…" + String(points, points.size - 7, 7)
    }
    while (labels.values.toSet().size != labels.size) {
        val counts = labels.values.groupingBy { it }.eachCount()
        labels = labels.mapValues { (id, name) -> if (counts.getValue(name) > 1) "$name #$id" else name }
    }
    return labels
}

/** Relative today/tomorrow wording expires at the next local midnight, including DST days. */
internal fun dailyNotificationLifetime(now: ZonedDateTime): Long =
    Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1)
