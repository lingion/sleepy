package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.TimeSlot
import java.time.LocalTime
import kotlin.math.abs

/**
 * Pure layout policy for the cards timetable.
 *
 * This deliberately stays independent from Compose so the most important parts of
 * adaptive timetable sizing can be verified with JVM tests.
 */
object TimetableViewportPolicy {

    const val DEFAULT_ROW_DP = 56f
    const val FIXED_ROW_DP = 52f // 原固定行高 (issue#8 时代 d(52f)) — 实验室自适应关闭时的基座
    const val MIN_ROW_DP = 36f
    const val MAX_ROW_DP = 96f

    /** 网格行间 gap(与 CourseTableView gapH = 4dp×scale 同值), 占位行几何检测用。 */
    const val ROW_GAP_DP = 4f
    const val VERTICAL_GESTURE_THRESHOLD_DP = 8f
    const val VERTICAL_DOMINANCE_RATIO = 1.25f
    val EVENING_START: LocalTime = LocalTime.of(18, 0)

    /** 用户自定义晚间起始 "HH:mm"; 解析失败回退 18:00 (2026-09-16: 不再机械 18:00) */
    fun parseEveningStart(raw: String?): LocalTime =
        raw?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: EVENING_START

    data class VisibleSlots(
        val slots: List<TimeSlot>,
        val hiddenEveningCount: Int = 0
    )

    /**
     * Hide only trailing slots starting at 18:00 or later when the whole term has
     * no course intersecting the evening. A malformed or unresolvable course keeps
     * the full timetable visible as a safety fallback.
     */
    fun selectVisibleSlots(
        allCourses: List<CourseEntity>,
        timeSlots: List<TimeSlot>,
        visibleDays: Set<Int>,
        timeJson: String?,
        autoHideEmptyEvening: Boolean,
        eveningStart: LocalTime = EVENING_START
    ): VisibleSlots {
        if (!autoHideEmptyEvening || timeSlots.isEmpty()) return VisibleSlots(timeSlots)

        val firstEvening = timeSlots.indexOfFirst { it.start >= eveningStart }
        if (firstEvening < 0) return VisibleSlots(timeSlots)

        val relevantCourses = allCourses.filter { it.day in visibleDays }
        val hasUnknownCourse = relevantCourses.any {
            courseInterval(it, timeSlots, timeJson) == null
        }
        if (hasUnknownCourse) return VisibleSlots(timeSlots)

        val hasEveningCourse = relevantCourses.any { course ->
            val interval = courseInterval(course, timeSlots, timeJson) ?: return@any false
            interval.second > eveningStart
        }
        if (hasEveningCourse) return VisibleSlots(timeSlots)

        return VisibleSlots(
            slots = timeSlots.take(firstEvening),
            hiddenEveningCount = timeSlots.size - firstEvening
        )
    }

    /** Calculate a readable fit height without allowing a short timetable to grow excessively. */
    fun fitRowHeightDp(
        availableGridHeightDp: Float,
        slotWeights: List<Float>?,
        slotCount: Int,
        contentScale: Float
    ): Float {
        val safeScale = contentScale.coerceIn(0.7f, 1.3f)
        val minRow = MIN_ROW_DP * safeScale
        val defaultRow = DEFAULT_ROW_DP * safeScale
        val totalWeight = if (slotWeights.isNullOrEmpty()) {
            slotCount.coerceAtLeast(1).toFloat()
        } else {
            slotWeights.sum().coerceAtLeast(1f)
        }
        return (availableGridHeightDp / totalWeight)
            .coerceIn(minRow, defaultRow)
    }

    /** 行高基座 (2026-09-16 用户令): 实验室自适应开=拟合高度; 默认关=原固定 52dp×scale */
    fun baseRowHeightDp(
        adaptive: Boolean,
        fitRowHeightDp: Float,
        contentScale: Float
    ): Float = if (adaptive) {
        fitRowHeightDp
    } else {
        FIXED_ROW_DP * contentScale.coerceIn(0.7f, 1.3f)
    }

    /** 手动行高 = 基座×手势缩放, 夹在可读上下限内 (缩放可 <1: 允许捏小) */
    fun manualRowHeightDp(
        baseRowHeightDp: Float,
        verticalScale: Float,
        minRowHeightDp: Float,
        maxRowHeightDp: Float
    ): Float = (baseRowHeightDp * verticalScale).coerceIn(minRowHeightDp, maxRowHeightDp)

    /**
     * 长课间留白: 把餐段空隙分钟按 分钟/该左邻行真实课时分钟 折进"餐段前一行"的
     * 行权重, 时间轴按分钟比例拉长背景 — 取代固定 mealGapExtra 间隙(间隙会压扁课程卡行)。
     * weights 与 rows 一一对应; breaksAfter = 餐段后的渲染行下标;
     * enabled=false 或无空隙时原样返回。breakUnitMinutes[锚行] 缺失/≤0 回退 periodMinutes(≤0 再回退 45)。
     */
    fun expandWeightsForLongBreaks(
        weights: List<Float>,
        breaksAfter: Set<Int>,
        breakMinutes: Map<Int, Int>,
        breakUnitMinutes: Map<Int, Int>? = null,
        periodMinutes: Int,
        enabled: Boolean,
    ): List<Float> {
        if (!enabled || breaksAfter.isEmpty() || weights.isEmpty()) return weights
        val fallback = if (periodMinutes > 0) periodMinutes else 45
        return weights.mapIndexed { i, w ->
            if (i !in breaksAfter) w
            else {
                // 分母 = 餐段左邻标准节次的真实分钟 (由调用方按锚行给), 缺失回退 periodMinutes。
                val unit = breakUnitMinutes?.get(i)?.takeIf { it > 0 } ?: fallback
                w + (breakMinutes[i] ?: 0).toFloat() / unit
            }
        }
    }

    /**
     * 长课间薄带卡高 (2026-10-17 #105→#183 回归定位): 卡片高度 = natural 跨度 − 普通行隙,
     * **带厚永远不进卡高** — 带只存在于 yOfRows 坐标层 (悬浮在锚行卡底与下行卡顶之间)。
     * 旧 bug = cardH 减 gapAfterRow(=gapH+带) 而 cardY 走 yOfRows(已加带), 同一带厚双扣,
     * 锚行胶囊回缩一个带厚。签名无带参数 = 该错误口径不可表达。
     * minSpanDp: ownTime 比例卡保底 (0.3 标准行, 防过短课塌缩到不可点)。
     */
    fun longBreakCardHeightDp(naturalSpanDp: Float, gapDp: Float, minSpanDp: Float = 0f): Float =
        naturalSpanDp.coerceAtLeast(minSpanDp) - gapDp

    /** True only after a two-finger gesture has a clear vertical intent. */
    fun locksVerticalResize(
        startVerticalSpan: Float,
        currentVerticalSpan: Float,
        startHorizontalSpan: Float,
        currentHorizontalSpan: Float,
        thresholdDp: Float = VERTICAL_GESTURE_THRESHOLD_DP,
        dominanceRatio: Float = VERTICAL_DOMINANCE_RATIO
    ): Boolean {
        val verticalDelta = abs(currentVerticalSpan - startVerticalSpan)
        val horizontalDelta = abs(currentHorizontalSpan - startHorizontalSpan)
        return verticalDelta >= thresholdDp &&
            verticalDelta >= horizontalDelta * dominanceRatio
    }

    fun rowHeightFromVerticalSpan(
        startRowHeightDp: Float,
        startVerticalSpan: Float,
        currentVerticalSpan: Float,
        minRowHeightDp: Float,
        maxRowHeightDp: Float
    ): Float {
        if (startVerticalSpan <= 0f) return startRowHeightDp.coerceIn(minRowHeightDp, maxRowHeightDp)
        val ratio = (currentVerticalSpan / startVerticalSpan).coerceAtLeast(0.1f)
        return (startRowHeightDp * ratio).coerceIn(minRowHeightDp, maxRowHeightDp)
    }

    // ---- 双指捏合锚点 (2026-11-02 用户令) ----
    // 旧口径: 捏合缩放只改行高, 滚动不补偿 → 全部内容以"顶端第一节课"为基准向下生长,
    // 捏在中部时指下内容被拖走。新口径: 基准线 = 两指中间, 缩放同时按锚点滚动补偿,
    // 让锚点内容钉在捏合中心下。几何与 CourseTableView yOfRows 同口径 (px 版)。

    /** 捏合锚点快照: 手势起点捕获, 之后每帧按它算滚动目标。 */
    data class PinchAnchor(
        /** 手势起点滚动偏移 (px, scrollState.value) */
        val startScrollPx: Float,
        /** 锚点上方的加权行数 (yOfRows 的 acc 口径, 含长休折权) */
        val weightedRowsAbove: Float
    )

    /** yOfRows 的 acc 口径: 行坐标 r(0=网格顶) 上方的加权行数。 */
    fun weightedRowsAbove(r: Float, weights: List<Float>): Float {
        if (r <= 0f || weights.isEmpty()) return 0f
        var acc = 0f
        val full = r.toInt().coerceAtMost(weights.size)
        for (i in 0 until full) acc += weights[i]
        if (full < weights.size && r > full) acc += weights[full] * (r - full)
        return acc
    }

    /** yOfRows 的 crossedBreaks 口径: 行坐标 r 之前跨过的长休带数。 */
    fun crossedMealBreaks(r: Float, breakRows: List<Int>): Int {
        val full = r.toInt().coerceAtLeast(0)
        return breakRows.count { it < full }
    }

    /**
     * 网格体坐标 (bodyY, 不含表头) → 行坐标 r: yOfRows 的逆。
     * bodyYOf 单调不减但有长休带跳变 (在带内无精确解, 返回带边界即可, 带不占行权重),
     * 故用二分而非牛顿 — 饱和度上界处牛顿会震荡 (单测 r=11.99 实测)。
     */
    fun rowCoordinateFromBodyY(
        bodyY: Float,
        rowPx: Float,
        mealGapExtraPx: Float,
        breakRows: List<Int>,
        weights: List<Float>
    ): Float {
        if (rowPx <= 0f || bodyY <= 0f || weights.isEmpty()) return 0f
        fun bodyYOf(r: Float): Float =
            rowPx * weightedRowsAbove(r, weights) + mealGapExtraPx * crossedMealBreaks(r, breakRows)
        val maxR = weights.size.toFloat()
        if (bodyY >= bodyYOf(maxR)) return maxR
        var lo = 0f
        var hi = maxR
        repeat(30) {
            val mid = (lo + hi) / 2f
            if (bodyYOf(mid) < bodyY) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    /**
     * 手势起点捕获锚点: 捏合中心 (视口 px) + 当前滚动 → 钉住该内容点所需的几何。
     * 中心落在表头区内 (bodyY<0) 时 acc=0 → 退化为"顶端固定"旧行为 (捏表头不平移内容)。
     */
    fun capturePinchAnchor(
        scrollPx: Float,
        centerYPx: Float,
        headerPx: Float,
        rowPx: Float,
        mealGapExtraPx: Float,
        breakRows: List<Int>,
        weights: List<Float>
    ): PinchAnchor {
        val bodyY = (scrollPx + centerYPx - headerPx).coerceAtLeast(0f)
        val r = rowCoordinateFromBodyY(bodyY, rowPx, mealGapExtraPx, breakRows, weights)
        return PinchAnchor(startScrollPx = scrollPx, weightedRowsAbove = weightedRowsAbove(r, weights))
    }

    /**
     * 行高 rowPx0→rowPx1 时锚点内容钉在指下所需的滚动目标 (px)。
     * 表头与长休带厚不随行高缩放, 只有锚点上方的加权行高增长 acc×(row1−row0)。
     */
    fun pinchTargetScrollPx(anchor: PinchAnchor, rowPx0: Float, rowPx1: Float): Float =
        (anchor.startScrollPx + anchor.weightedRowsAbove * (rowPx1 - rowPx0)).coerceAtLeast(0f)

    private fun courseInterval(
        course: CourseEntity,
        timeSlots: List<TimeSlot>,
        timeJson: String?
    ): Pair<LocalTime, LocalTime>? {
        val mapped = timeJson?.let {
            TimeTableUtils.courseTimeParts(
                course.startNode,
                course.step,
                it,
                course.ownTime,
                course.startTime,
                course.endTime
            )
        }
        val parts = mapped ?: run {
            val endNode = course.startNode + course.step.coerceAtLeast(1) - 1
            val first = timeSlots.firstOrNull { slot -> slot.nodeStart == course.startNode }
            val last = timeSlots.firstOrNull { slot -> slot.nodeStart == endNode }
            if (first == null || last == null) return null
            first.displayStart to last.displayEnd
        }
        val start = runCatching { LocalTime.parse(parts.first) }.getOrNull() ?: return null
        val end = runCatching { LocalTime.parse(parts.second) }.getOrNull() ?: return null
        return if (end > start) start to end else null
    }
}
