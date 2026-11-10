package com.lingion.sleepy.data.entity

import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import kotlinx.serialization.Serializable

/**
 * v1.0.16 智慧节次配置（自动模式）
 *
 * 用户输入：
 *  - periodMinutes (a)        每节时长
 *  - totalPeriods  (N)        总节数
 *  - startTime                第一节开始时间 "HH:mm"
 *  - breaks                   break 模板列表（每项 = 一个分组）
 *  - transitionAssignments    每个 transition 选哪个 break 索引
 *                              null = 默认 0 分钟（连续）
 *                              长度 = N - 1
 *
 * 推导：
 *  第 i 节开始时间 = startTime + i × periodMinutes + Σbreaks_before_i
 *  transition i 表示第 i 节与第 i+1 节之间的课间
 */
@Serializable
data class SmartPeriodConfig(
    val startTime: String = "08:00",
    val periodMinutes: Int = 45,
    val totalPeriods: Int = 12,
    val breaks: List<BreakOption> = emptyList(),
    val transitionAssignments: List<Int?> = emptyList(),
    val durations: List<DurationOption> = emptyList(),
    val periodAssignments: List<Int?> = emptyList(),
) {
    /**
     * 取每个 period 的 duration 索引（带范围保护 + 默认填充）。
     * null 或越界表示使用主课程时长 [periodMinutes]。
     */
    fun effectivePeriodAssignments(): List<Int?> {
        val n = totalPeriods.coerceIn(0, MAX_PERIODS_PER_DAY)
        val base = periodAssignments.take(n)
        return base.map { value ->
            if (value != null && value in durations.indices) value else null
        } + List((n - base.size).coerceAtLeast(0)) { null }
    }

    /** 每个 period 的实际分钟数，未分配时使用主课程时长。 */
    fun effectivePeriodMinutes(): List<Int> {
        return effectivePeriodAssignments().map { index ->
            if (index != null && index in durations.indices) durations[index].minutes else periodMinutes
        }
    }

    /**
     * 取每个 transition 的 break 索引（带范围保护 + 默认填充）
     * 长度 = max(0, totalPeriods - 1)
     * 未填的位置默认 null（0 分钟连续）
     */
    fun effectiveAssignments(): List<Int?> {
        val n = (totalPeriods.coerceIn(0, MAX_PERIODS_PER_DAY) - 1).coerceAtLeast(0)
        val base = transitionAssignments.take(n)
        return base.map { v ->
            if (v != null && v in breaks.indices) v else null
        } + List((n - base.size).coerceAtLeast(0)) { null }
    }

    /**
     * 推导所有 transition 的实际分钟数
     * 默认（null 或越界）= 0 分钟
     */
    fun effectiveTransitionMinutes(): List<Int> {
        val n = (totalPeriods.coerceIn(0, MAX_PERIODS_PER_DAY) - 1).coerceAtLeast(0)
        val assigns = effectiveAssignments()
        return (0 until n).map { i ->
            val idx = assigns[i]
            if (idx != null && idx in breaks.indices) breaks[idx].minutes else 0
        }
    }

    /** Validate before deriving: persisted clocks and the manual editor both stop at 23:59. */
    fun validationError(): SmartPeriodValidationError? {
        val start = startMinute() ?: return SmartPeriodValidationError.StartTime
        if (totalPeriods !in 1..MAX_PERIODS_PER_DAY) return SmartPeriodValidationError.PeriodCount
        if (periodMinutes < 1 || durations.any { it.minutes < 1 } || breaks.any { it.minutes < 0 }) {
            return SmartPeriodValidationError.Duration
        }
        // Use Long and inspect assignments directly, so a corrupt imported count or duration
        // cannot overflow an Int or allocate an unbounded preview before being rejected.
        var minute = start.toLong()
        repeat(totalPeriods) { index ->
            val duration = periodAssignments.getOrNull(index)?.let { durations.getOrNull(it) }?.minutes
                ?: periodMinutes
            minute += duration.toLong()
            if (minute > LAST_MINUTE_OF_DAY) return SmartPeriodValidationError.ExceedsDay
            if (index < totalPeriods - 1) {
                minute += transitionAssignments.getOrNull(index)?.let { breaks.getOrNull(it) }?.minutes?.toLong() ?: 0L
            }
        }
        return null
    }

    /** 推导节次（不含 transition，仅节本身）；非法配置不生成或截断作息。 */
    fun derive(): List<TimeSlotRow> {
        if (validationError() != null) return emptyList()
        val rows = mutableListOf<TimeSlotRow>()
        val transMins = effectiveTransitionMinutes()
        val periodMins = effectivePeriodMinutes()
        val firstMinute = startMinute()!!
        var curH = firstMinute / 60
        var curM = firstMinute % 60
        for (i in 0 until totalPeriods) {
            val startStr = "%02d:%02d".format(curH, curM)
            curM += periodMins[i]
            curH += curM / 60
            curM %= 60
            val endStr = "%02d:%02d".format(curH, curM)
            rows.add(TimeSlotRow(i + 1, startStr, endStr))
            if (i < transMins.size) {
                curM += transMins[i]
                curH += curM / 60
                curM %= 60
            }
        }
        return rows
    }

    private fun startMinute(): Int? {
        if (!Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(startTime)) return null
        return startTime.substring(0, 2).toInt() * 60 + startTime.substring(3, 5).toInt()
    }

    companion object {
        const val LAST_MINUTE_OF_DAY = 23 * 60 + 59
        const val MAX_PERIODS_PER_DAY = LAST_MINUTE_OF_DAY
    }
}

enum class SmartPeriodValidationError { StartTime, PeriodCount, Duration, ExceedsDay }

/**
 * Break 模板（用于"智慧节次"自动模式 UI）
 * @param minutes 该 break 的分钟数
 * @param isLong true=大课间, false=小课间（仅用于颜色/标签展示）
 * @param label 用户自定义名称（可选），默认"小课间 X"/"大课间 X"
 */
@Serializable
data class BreakOption(
    val minutes: Int,
    val isLong: Boolean = false,
    val label: String? = null,
) {
    fun displayLabel(index: Int): String {
        if (!label.isNullOrBlank()) return label
        val prefix = if (isLong) "大课间" else "小课间"
        return "$prefix $minutes 分钟"
    }
}

@Serializable
data class DurationOption(
    val minutes: Int,
    val isLong: Boolean = false,
    val label: String? = null,
) {
    fun displayLabel(index: Int): String {
        if (!label.isNullOrBlank()) return label
        val prefix = if (isLong) "长课时" else "短课时"
        return "$prefix $minutes 分钟"
    }
}

/** Remap assignments after removing one duration group. */
fun remapDurationAssignmentsAfterDeletion(
    assignments: List<Int?>,
    deletedIndex: Int,
): List<Int?> = assignments.map { value ->
    when {
        value == deletedIndex -> null
        value != null && value > deletedIndex -> value - 1
        else -> value
    }
}
