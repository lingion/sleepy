package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import java.time.LocalDate

/** Identifies the stored occurrence, independently of its projected day/node. */
data class CourseOccurrence(
    val course: CourseEntity,
    val sourceDate: LocalDate,
    val displayDate: LocalDate,
)

object CourseOccurrences {
    /** Coalesce identical recurring rows only when their exact teaching weeks fit one row. */
    fun mergeMatchingRows(rows: List<CourseEntity>): List<CourseEntity> = rows
        .groupBy { it.copy(id = 0, startWeek = 0, endWeek = 0, type = 0) }
        .values.flatMap { matching ->
            if (matching.first().fixedDate != null || matching.first().groupId.isBlank()) return@flatMap matching
            val result = matching.sortedWith(compareBy({ it.startWeek }, { it.id })).toMutableList()
            fun merge(left: CourseEntity, right: CourseEntity): CourseEntity? {
                val leftWeeks = (left.startWeek..left.endWeek).filter(left::inWeek).toSet()
                val rightWeeks = (right.startWeek..right.endWeek).filter(right::inWeek).toSet()
                // Do not silently remove duplicate occurrences or repair malformed empty rows.
                if (leftWeeks.isEmpty() || rightWeeks.isEmpty() || leftWeeks.any { it in rightWeeks }) return null
                val weeks = leftWeeks + rightWeeks
                val retained = listOf(left, right).minBy { if (it.id > 0) it.id else Long.MAX_VALUE }
                val candidate = retained.copy(startWeek = minOf(left.startWeek, right.startWeek),
                    endWeek = maxOf(left.endWeek, right.endWeek))
                return (listOf(retained.type) + listOf(0, 1, 2)).distinct().map { candidate.copy(type = it) }
                    .firstOrNull { row -> (row.startWeek..row.endWeek).filter(row::inWeek).toSet() == weeks }
            }
            var changed = true
            while (changed) {
                changed = false
                search@ for (i in result.indices) {
                    for (j in i + 1 until result.size) {
                        val merged = merge(result[i], result[j]) ?: continue
                        result[i] = merged
                        result.removeAt(j)
                        changed = true
                        break@search
                    }
                }
            }
            result
        }

    fun resolve(course: CourseEntity, date: LocalDate, transfers: List<HolidayTransferEntry>): CourseOccurrence? {
        val source = course.fixedDate?.let(LocalDate::parse)
            ?: HolidayRangeOps.HolidayTransferOps.courseDateForDate(date, transfers)
            ?: return null
        return CourseOccurrence(course, source, date)
    }

    /** Remove exactly one actual teaching week, retaining parity and all other fields. */
    fun without(course: CourseEntity, sourceDate: LocalDate, startDate: String): List<CourseEntity> {
        if (course.fixedDate != null) {
            require(course.fixedDate == sourceDate.toString())
            return emptyList()
        }
        val week = DateUtils.currentWeek(startDate, sourceDate)
        require(course.day == sourceDate.dayOfWeek.value && course.inWeek(week))
        return listOf(course.startWeek until week, (week + 1)..course.endWeek).mapNotNull { range ->
            val weeks = range.filter(course::inWeek)
            if (weeks.isEmpty()) null else course.copy(startWeek = weeks.first(), endWeek = weeks.last())
        }.mapIndexed { index, row -> row.copy(id = if (index == 0) course.id else 0) }
    }

    fun atDate(course: CourseEntity, date: LocalDate, table: TimeTableEntity, fixed: Boolean): CourseEntity {
        require(DateUtils.semesterStatus(table.startDate, table.maxWeek, date) == DateUtils.SemesterStatus.IN_RANGE)
        val week = DateUtils.currentWeek(table.startDate, date)
        return course.copy(id = 0, tableId = table.id, day = date.dayOfWeek.value,
            startWeek = week, endWeek = week, type = 0, fixedDate = if (fixed) date.toString() else null)
    }

    /** Dates used by both the save preview and conflict detection, including cross-week transfers. */
    fun dates(table: TimeTableEntity, transfers: List<HolidayTransferEntry>): List<LocalDate> =
        ((1..table.maxWeek).flatMap { week -> (1..7).map { day -> DateUtils.dateOfWeek(table.startDate, week, day) } } +
            transfers.map { it.targetDate }).distinct().sorted()

    fun conflictDetails(
        drafts: List<CourseEntity>, stored: List<CourseEntity>, table: TimeTableEntity,
        transfers: List<HolidayTransferEntry>, dayNames: Array<String>, template: String,
    ): List<String> = dates(table, transfers).flatMap { date ->
        fun projected(rows: List<CourseEntity>) = HolidayRangeOps.HolidayTransferOps
            .coursesForDate(date, table.startDate, table.maxWeek, rows, transfers)
            .map { it.copy(day = date.dayOfWeek.value,
                startWeek = DateUtils.currentWeek(table.startDate, date),
                endWeek = DateUtils.currentWeek(table.startDate, date), type = 0) }
        val incoming = projected(drafts)
        val existing = projected(stored)
        val external = ConflictDetailReporter.draftConflictDetails(incoming, existing, dayNames, table.timeJson)
        val internal = incoming.flatMapIndexed { i, row ->
            ConflictDetailReporter.draftConflictDetails(listOf(row), incoming.drop(i + 1), dayNames, table.timeJson)
        }
        (external + internal).map { "$date · ${ConflictDetailReporter.formatDetail(it, template)}" }
    }.distinct()
}
