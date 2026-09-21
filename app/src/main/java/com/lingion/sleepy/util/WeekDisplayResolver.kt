package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 展示日期的单一事实来源。
 *
 * v2 (用户 2026-09-20 改向):
 *   焦点从"切下周"改成"显示最近一个还有课的日子"。
 *   - 今天还有未结束的课 → 落在今天
 *   - 今天之后（学期范围内）还有任何课 → 落在那个日子
 *   - 全空（学期结束/开关关/无课） → 落在今天，让 UI 用 semesterStatus 自己画状态文案
 *
 * 老 API (displayWeek / NEXT_WEEK / WEEKEND_CURRENT / CURRENT_ENDED) 已彻底删除；
 * widget/UI 在后续 slice 内切到 targetDate / targetWeek。
 */
enum class WeekDisplayStatus {
    /** 落在今天或用户手动选择周，没有自动跳转。 */
    NORMAL,
    /** 已自动跳到今天之后最近的某一天有课那天。 */
    NEAREST_BUSY_DAY
}

data class WeekDisplayContext(
    /** 当前学期周数（不随开关或跳转变化）。 */
    val actualWeek: Int,
    /** 默认应该展示的日期（语义：null 已不可能——今日或跳到的未来某天）。 */
    val targetDate: LocalDate,
    /** targetDate 所在周（1-based）。 */
    val targetWeek: Int,
    /** 今天是否还有未结束的课；false 表示今天已空但跳到了之后某天。 */
    val todayHasRemaining: Boolean,
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

        val todayHasRemaining = semesterStatus == DateUtils.SemesterStatus.IN_RANGE &&
            hasRemainingCourse(actualWeek, today, now.toLocalTime(), courses, timeJson)

        val targetDate = when {
            !enabled -> today
            semesterStatus != DateUtils.SemesterStatus.IN_RANGE -> today
            todayHasRemaining -> today
            else -> findNearestBusyDay(
                startDate = startDate,
                actualWeek = actualWeek,
                maxWeek = safeMaxWeek,
                today = today,
                courses = courses
            )
        }
        val targetWeek = if (targetDate == today) actualWeek
            else DateUtils.currentWeek(startDate, targetDate).coerceIn(1, safeMaxWeek)

        val status = when {
            !enabled || semesterStatus != DateUtils.SemesterStatus.IN_RANGE ->
                WeekDisplayStatus.NORMAL
            targetDate == today -> WeekDisplayStatus.NORMAL
            else -> WeekDisplayStatus.NEAREST_BUSY_DAY
        }
        return WeekDisplayContext(
            actualWeek = actualWeek,
            targetDate = targetDate,
            targetWeek = targetWeek,
            todayHasRemaining = todayHasRemaining,
            status = status,
            semesterStatus = semesterStatus,
            today = today,
            enabled = enabled
        )
    }

    /**
     * 给主界面 / 可手动导航的小组件计算当前选中周的标题状态：
     * 仅当用户停留在自动选中的最近有课日（非今天）时显示 NEAREST_BUSY_DAY 文案，
     * 其他手动周次 / 今天 / 关闭开关时统一 NORMAL。
     */
    fun statusForSelectedWeek(
        context: WeekDisplayContext,
        selectedWeek: Int
    ): WeekDisplayStatus = when {
        !context.enabled -> WeekDisplayStatus.NORMAL
        context.status == WeekDisplayStatus.NEAREST_BUSY_DAY &&
            selectedWeek == context.targetWeek -> WeekDisplayStatus.NEAREST_BUSY_DAY
        else -> WeekDisplayStatus.NORMAL
    }

    /**
     * 从今天（不含）开始，按日期逐天在学期 [actualWeek..maxWeek] 范围内找到
     * 第一个日历上有课的日子；找不到则返回 today（行为降级为 NORMAL）。
     *
     * 关键不变量：扫描上限是 maxWeek 的周天，不会跨学期跳出。
     */
    private fun findNearestBusyDay(
        startDate: String,
        actualWeek: Int,
        maxWeek: Int,
        today: LocalDate,
        courses: List<CourseEntity>
    ): LocalDate {
        val endDate = DateUtils.dateOfWeek(startDate, maxWeek, 7) // 最后一周周日
        var cursor = today.plusDays(1)
        while (!cursor.isAfter(endDate)) {
            val cursorWeek = DateUtils.currentWeek(startDate, cursor).coerceIn(1, maxWeek)
            if (cursorWeek < actualWeek) {
                cursor = cursor.plusDays(1)
                continue
            }
            val dow = cursor.dayOfWeek.value
            // cursor 恒 > today（起点 today+1），日历上有课 = 该课的课还没上，直接命中
            val hasAny = courses.any { it.inWeek(cursorWeek) && it.day == dow }
            if (hasAny) return cursor
            cursor = cursor.plusDays(1)
        }
        return today
    }

    /** 今天是否还有未结束的课：course.day > today → 未来；< today → 已过；= today → 解析 endTime。 */
    private fun hasRemainingCourse(
        actualWeek: Int,
        today: LocalDate,
        now: LocalTime,
        courses: List<CourseEntity>,
        timeJson: String
    ): Boolean {
        val todayDow = today.dayOfWeek.value
        return courses.asSequence()
            .filter { it.inWeek(actualWeek) && it.day == todayDow }
            .any { course ->
                courseEndMinutes(course, timeJson)
                    ?.let { it > now.hour * 60 + now.minute }
                    ?: true
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
