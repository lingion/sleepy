package com.lingion.sleepy.widget

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.ConflictLayoutEngine
import com.lingion.sleepy.util.TimeTableUtils
import java.time.LocalTime

/**
 * FIXED 窗口核心 (设计文稿 widget-redesign-2026-09 §3, 纯 JVM 可测)。
 *
 * 默认不滚动 = 固定窗口: 在 availH 预算内选一段连续渲染行(行原子), 窗外课程以
 * 页脚「+N 节待上」提示。锚点规则:
 * - TIME_WINDOW(今天): 进行中行(真实分钟 start≤now<end)优先; 无进行中 → 最近将来行;
 *   全部结束且分钟全可解析 → ALL_DONE 公共闸(§3.0, fits/overflow 之上统一判)。
 * - HEAD(过去/未来日): 自首行贪心。
 * 分钟口径与渲染行序解耦(评审 #5): 行时间 = 行内课程 effectiveCourseTime 真实分钟,
 * 调休 ownTime 与节次序倒挂时锚点仍正确。
 *
 * 页脚 best-effort(评审 #1/#2): 先无页脚贪心; 有隐藏才预留页脚重算;
 * 预留后 0 行 → 丢页脚保 1 行, 永不出现「纯页脚 0 行」。
 */
object FixedWindowCore {

    /** 页脚行高 — 「+N 节待上 · 截至 HH:mm」单行。 */
    const val FOOTER_H_DP = 20f

    enum class Mode { TIME_WINDOW, HEAD }

    enum class Status { NONE, ALL_DONE }

    /** 一个候选渲染行: 行体 + 高(dp) + 真实分钟(不可解析 = null)。 */
    data class WindowEntry(
        val row: ConflictLayoutEngine.WeekLaneRow,
        val heightDp: Float,
        val startMin: Int?,
        val endMin: Int?
    )

    data class WindowResult(
        val visible: List<WindowEntry>,
        val footer: Boolean,
        /** 窗尾之后被隐藏的课程数(行前已下课不计)。 */
        val hiddenAheadCourses: Int,
        val status: Status
    )

    /** 课的真实起止分钟 — effectiveCourseTime 同一契约; 无法解析 → null。 */
    fun courseStartEndMin(c: CourseEntity, timeJson: String?): Pair<Int, Int>? {
        val eff = TimeTableUtils.effectiveCourseTime(
            c.isIrregularTime || c.ownTime,
            c.startTime, c.endTime,
            c.startNode, c.step, timeJson.orEmpty()
        ) ?: return null
        val s = runCatching { LocalTime.parse(eff.first) }.getOrNull() ?: return null
        val e = runCatching { LocalTime.parse(eff.second) }.getOrNull() ?: return null
        if (e <= s) return null
        return s.toSecondOfDay() / 60 to e.toSecondOfDay() / 60
    }

    /** 行窗口候选 — 行时间 = 行内课程 min(start)/max(end); 全不可解析 → null。 */
    fun entriesOf(
        rows: List<ConflictLayoutEngine.WeekLaneRow>,
        timeJson: String?,
        heightOf: (ConflictLayoutEngine.WeekLaneRow) -> Float
    ): List<WindowEntry> = rows.map { row ->
        val times = row.courses.mapNotNull { courseStartEndMin(it, timeJson) }
        WindowEntry(
            row = row,
            heightDp = heightOf(row),
            startMin = times.minOfOrNull { it.first },
            endMin = times.maxOfOrNull { it.second }
        )
    }

    /**
     * 选窗。不变量 (FixedWindowCoreTest 逐条锁死):
     * 1 rows 空 → 空结果(空态分支归调用方);
     * 2 ALL_DONE: 今天 + 分钟全可解析 + max(end) < now → 状态行, 无课程行;
     * 3 锚点行必在可视段(超高也保留, 渲染侧底裁);
     * 4 贪心按行序连续段, 行原子;
     * 5 页脚 best-effort, 0 行回退。
     */
    fun window(
        entries: List<WindowEntry>,
        availH: Float,
        mode: Mode,
        nowMin: Int?,
        gapDp: Float = 0f,
        footerH: Float = FOOTER_H_DP
    ): WindowResult {
        if (entries.isEmpty()) return WindowResult(emptyList(), false, 0, Status.NONE)

        val anchorIdx = if (mode == Mode.TIME_WINDOW && nowMin != null) {
            if (entries.all { it.endMin != null } && entries.maxOf { it.endMin!! } < nowMin) {
                return WindowResult(emptyList(), false, 0, Status.ALL_DONE)
            }
            val inProgress = entries.indexOfFirst {
                it.startMin != null && it.endMin != null &&
                    it.startMin <= nowMin && nowMin < it.endMin
            }
            if (inProgress >= 0) inProgress
            else {
                val upcoming = entries.filter { it.startMin != null && it.startMin > nowMin }
                upcoming.minByOrNull { it.startMin!! }?.let { entries.indexOf(it) } ?: 0
            }
        } else {
            0
        }

        val tail = entries.subList(anchorIdx, entries.size)
        val full = greedy(tail, availH, gapDp, forceFirst = true)
        if (full.size == tail.size) return WindowResult(full, false, 0, Status.NONE)

        // footerH=0 → 填满档 (Today 底部导航条 2026-09-15): 底部条高已在 availH 外恒扣,
        // 窗口不再二次预留 → 装得下 2 行就画 2 行, 隐藏课只点亮「+N」胶囊。
        if (footerH <= 0f) {
            return WindowResult(full, true, hiddenCourses(entries, anchorIdx + full.size), Status.NONE)
        }

        val withFooter = greedy(tail, availH - footerH, gapDp, forceFirst = false)
        return if (withFooter.isEmpty()) {
            WindowResult(full, false, hiddenCourses(entries, anchorIdx + full.size), Status.NONE)
        } else {
            WindowResult(withFooter, true, hiddenCourses(entries, anchorIdx + withFooter.size), Status.NONE)
        }
    }

    private fun hiddenCourses(entries: List<WindowEntry>, fromIndex: Int): Int =
        entries.drop(fromIndex).sumOf { it.row.courses.size }

    /** 连续段贪心; forceFirst=true 时首行无条件保留(锚行超高底裁语义)。 */
    private fun greedy(
        tail: List<WindowEntry>,
        budget: Float,
        gapDp: Float,
        forceFirst: Boolean
    ): List<WindowEntry> {
        if (tail.isEmpty()) return emptyList()
        if (!forceFirst && tail.first().heightDp > budget + 0.01f) return emptyList()
        val out = ArrayList<WindowEntry>(tail.size)
        var used = 0f
        for (e in tail) {
            val add = if (out.isEmpty()) e.heightDp else gapDp + e.heightDp
            if (out.isEmpty() || used + add <= budget + 0.01f) {
                out += e
                used += add
            } else {
                break
            }
        }
        return out
    }
}
