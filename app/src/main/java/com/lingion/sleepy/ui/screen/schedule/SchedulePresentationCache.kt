package com.lingion.sleepy.ui.screen.schedule

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.ConflictLayoutEngine
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayManager
import com.lingion.sleepy.util.HolidayRange
import com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps
import com.lingion.sleepy.util.HolidayTransferEntry
import java.time.LocalDate

internal data class TodayPresentation(
    val courses: List<CourseEntity>,
    val rows: List<ConflictLayoutEngine.WeekLaneRow>,
    val groups: Map<String, List<CourseEntity>>,
)

internal class SchedulePresentationCache {
    private data class Inputs(
        val table: TimeTableEntity?,
        val timeJson: String?,
        val periodTable: PeriodTableEntity?,
        val courses: List<CourseEntity>,
        val transfers: List<HolidayTransferEntry>,
    )

    private var inputs: Inputs? = null
    private val weeks = object : LinkedHashMap<Int, List<CourseEntity>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<CourseEntity>>): Boolean = size > 3
    }
    private var todayDate: LocalDate? = null
    private var todayPresentation: TodayPresentation? = null

    private fun prepare(state: ScheduleState) {
        val next = Inputs(state.currentTable, state.effectiveCurrentTable?.timeJson, state.effectivePeriodTable, state.courses, state.transfers)
        if (next == inputs) return
        inputs = next
        weeks.clear()
        todayDate = null
        todayPresentation = null
    }

    fun weekCourses(state: ScheduleState, week: Int): List<CourseEntity> {
        prepare(state)
        return weeks.getOrPut(week) { buildWeekCourses(state, week) }
    }

    private fun buildWeekCourses(state: ScheduleState, week: Int): List<CourseEntity> {
        val table = state.currentTable
        return if (table == null) {
            val timeJson = state.effectiveCurrentTable?.timeJson
            state.courses.filter { it.inWeek(week) }
                .map { course -> timeJson?.let { course.normalizeNode(it) } ?: course }
        } else {
            (1..7).flatMap { day ->
                val date = DateUtils.dateOfWeek(table.startDate, week, day)
                HolidayTransferOps.coursesForDate(
                    date, table.startDate, table.maxWeek, state.courses, state.transfers,
                ).map { course ->
                    val projected = course.copy(day = day)
                    val timeJson = state.effectiveTableFor(date)?.timeJson
                    timeJson?.let { projected.normalizeNode(it) } ?: projected
                }
            }
        }
    }

    fun today(state: ScheduleState, date: LocalDate): TodayPresentation {
        prepare(state)
        todayPresentation?.takeIf { todayDate == date }?.let { return it }
        return buildToday(state, date).also {
            todayDate = date
            todayPresentation = it
        }
    }

    private fun buildToday(state: ScheduleState, date: LocalDate): TodayPresentation {
        val timeJson = state.effectiveTableFor(date)?.timeJson
        val courses = (state.currentTable?.let { table ->
            HolidayTransferOps.coursesForDate(
                date, table.startDate, table.maxWeek, state.courses, state.transfers,
            )
        } ?: emptyList()).map { course ->
            timeJson?.let { course.normalizeNode(it) } ?: course
        }.sortedBy { it.startNode }
        return TodayPresentation(
            courses = courses,
            rows = ConflictLayoutEngine.weekLaneRows(courses, timeJson),
            groups = courses.groupBy { it.groupId },
        )
    }

    private data class GreyInputs(
        val tableId: Long?,
        val startDate: String?,
        val transfers: List<HolidayTransferEntry>,
        val greyHoliday: Boolean,
        val greyWeekend: Boolean,
        val ignoreWorkday: Boolean,
        val ranges: List<HolidayRange>,
    )

    private var greyInputs: GreyInputs? = null
    private val greyWeeks = object : LinkedHashMap<Int, Set<Int>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Set<Int>>): Boolean = size > 7
    }

    fun weekGreyDays(context: android.content.Context, state: ScheduleState, week: Int): Set<Int> {
        val table = state.effectiveCurrentTable ?: state.currentTable
        val startDate = state.currentTable?.startDate
        if (startDate.isNullOrBlank()) return emptySet()

        val greyHoliday = AppPrefs.isHolidayGreyHoliday(context)
        val greyWeekend = AppPrefs.isHolidayGreyWeekend(context)
        if (!greyHoliday && !greyWeekend) return emptySet()

        val ignoreWorkday = AppPrefs.isHolidayIgnoreWorkday(context)
        val ranges = AppPrefs.getHolidayRanges(context)
        val nextInputs = GreyInputs(
            tableId = table?.id,
            startDate = startDate,
            transfers = state.transfers,
            greyHoliday = greyHoliday,
            greyWeekend = greyWeekend,
            ignoreWorkday = ignoreWorkday,
            ranges = ranges,
        )
        if (nextInputs != greyInputs) {
            greyInputs = nextInputs
            greyWeeks.clear()
        }

        return greyWeeks.getOrPut(week) {
            val datesByDay = (1..7).associateWith { day ->
                DateUtils.dateOfWeek(startDate, week, day)
            }
            HolidayManager.fastComputeGreyDays(
                ctx = context,
                datesByDay = datesByDay,
                tableId = table?.id,
                transfers = state.transfers,
            )
        }
    }
}
