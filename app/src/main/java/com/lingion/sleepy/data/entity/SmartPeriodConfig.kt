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
) {
    /**
     * 取每个 transition 的 break 索引（带范围保护 + 默认填充）
     * 长度 = max(0, totalPeriods - 1)
     * 未填的位置默认 null（0 分钟连续）
     */
    fun effectiveAssignments(): List<Int?> {
        val n = (totalPeriods - 1).coerceAtLeast(0)
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
        val n = (totalPeriods - 1).coerceAtLeast(0)
        val assigns = effectiveAssignments()
        return (0 until n).map { i ->
            val idx = assigns[i]
            if (idx != null && idx in breaks.indices) breaks[idx].minutes else 0
        }
    }

    /** 推导节次（不含 transition，仅节本身） */
    fun derive(): List<TimeSlotRow> {
        val rows = mutableListOf<TimeSlotRow>()
        val transMins = effectiveTransitionMinutes()
        val (h0, m0) = parseStart()
        var curH = h0
        var curM = m0
        for (i in 0 until totalPeriods) {
            val startStr = "%02d:%02d".format(curH, curM)
            curM += periodMinutes
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

    private fun parseStart(): Pair<Int, Int> {
        val parts = startTime.split(":")
        return if (parts.size == 2) parts[0].toInt() to parts[1].toInt() else 8 to 0
    }
}

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