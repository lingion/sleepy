package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 展示周次的单一事实来源。
 *
 * 这不是课程数据的重排器：课程仍以课表里保存的 day/startWeek/endWeek 为准。
 * 它只决定在“当周没有剩余课程”时，默认展示当前周还是下一周。
 */
enum class WeekDisplayStatus {
    NORMAL,
    NEXT_WEEK,
    WEEKEND_CURRENT,
    CURRENT_ENDED
}

data class WeekDisplayContext(
    val actualWeek: Int,
    val displayWeek: Int,
    val actualWeekEnded: Boolean,
    val status: WeekDisplayStatus,
    val semesterStatus: DateUtils.SemesterStatus,
    val today: LocalDate,
    val enabled: Boolean
)

object WeekDisplayResolver {
    fun resolve(
        startDate: String,
        maxWeek: Int,
        now: LocalDateTime,
        courses: List<CourseEntity>,
        timeJson: String,
        enabled: Boolean
    ): WeekDisplayContext {
        val safeMaxWeek = maxWeek.coerceAtLeast(1)
        val today = now.toLocalDate()
        val semesterStatus = DateUtils.semesterStatus(startDate, safeMaxWeek, today)
        val actualWeek = DateUtils.currentWeek(startDate, today).coerceIn(1, safeMaxWeek)
        val ended = semesterStatus == DateUtils.SemesterStatus.IN_RANGE &&
            !hasRemainingCourse(actualWeek, today, now.toLocalTime(), courses, timeJson)
        val canAdvance = enabled &&
            semesterStatus == DateUtils.SemesterStatus.IN_RANGE &&
            ended && actualWeek < safeMaxWeek
        val displayWeek = if (canAdvance) actualWeek + 1 else actualWeek
        val status = when {
            canAdvance -> WeekDisplayStatus.NEXT_WEEK
            enabled && ended && today.dayOfWeek.value >= 6 -> WeekDisplayStatus.WEEKEND_CURRENT
            enabled && ended -> WeekDisplayStatus.CURRENT_ENDED
            else -> WeekDisplayStatus.NORMAL
        }
        return WeekDisplayContext(
            actualWeek = actualWeek,
            displayWeek = displayWeek,
            actualWeekEnded = ended,
            status = status,
            semesterStatus = semesterStatus,
            today = today,
            enabled = enabled
        )
    }

    /**
     * 给主界面/可手动导航的小组件计算当前选中周的标题状态。
     * 只有自动切换产生的下一周和已结束的真实周显示特殊标题，其他手动周次保持普通周次标题。
     */
    fun statusForSelectedWeek(
        context: WeekDisplayContext,
        selectedWeek: Int
    ): WeekDisplayStatus = when {
        !context.enabled -> WeekDisplayStatus.NORMAL
        context.status == WeekDisplayStatus.NEXT_WEEK && selectedWeek == context.displayWeek ->
            WeekDisplayStatus.NEXT_WEEK
        selectedWeek == context.actualWeek && context.actualWeekEnded &&
            context.today.dayOfWeek.value >= 6 -> WeekDisplayStatus.WEEKEND_CURRENT
        selectedWeek == context.actualWeek && context.actualWeekEnded ->
            WeekDisplayStatus.CURRENT_ENDED
        else -> WeekDisplayStatus.NORMAL
    }

    /** 下周模式下，今日/两日小组件从下一周周一开始，而不是把周末和下周拼在一起。 */
    fun dateOfDisplayWeek(startDate: String, displayWeek: Int, dayOfWeek: Int): LocalDate =
        DateUtils.dateOfWeek(startDate, displayWeek, dayOfWeek)

    private fun hasRemainingCourse(
        actualWeek: Int,
        today: LocalDate,
        now: LocalTime,
        courses: List<CourseEntity>,
        timeJson: String
    ): Boolean {
        val todayDow = today.dayOfWeek.value
        return courses.asSequence()
            .filter { it.inWeek(actualWeek) }
            .any { course ->
                when {
                    course.day > todayDow -> true
                    course.day < todayDow -> false
                    else -> courseEndMinutes(course, timeJson)?.let { it > now.hour * 60 + now.minute }
                        ?: true
                }
            }
    }

    private fun courseEndMinutes(course: CourseEntity, timeJson: String): Int? {
        val effective = TimeTableUtils.effectiveCourseTime(
            course.isIrregularTime || course.ownTime,
            course.startTime,
            course.endTime,
            course.startNode,
            course.step,
            timeJson
        ) ?: return null
        val end = runCatching { LocalTime.parse(effective.second) }.getOrNull() ?: return null
        return end.hour * 60 + end.minute
    }
}
