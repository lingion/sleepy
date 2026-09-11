package com.lingion.sleepy.widget

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.ConflictLayoutEngine

/**
 * Today 内容行几何单一真值 (v8, 2026-09-10 真机「巨型圆角卡片」报障修复):
 *
 * 历史: renderTodayRegular 与 todayContentHeightDp 各自手写一份布局常量镜像
 * (38/10/3 + 起点 14/38 + 底 pad 14)。v6 引入 headerSpace 时只改了内容高度侧,
 * 渲染侧 viewport 常量硬编码 (14+24)dp → 去头条带顶部 24dp 死带 + 末行课程
 * 被可见过滤丢弃 (Today 是唯一 headerSpace=true 条带调用方, TwoDay/WeekList
 * 从未中招)。本对象把行几何收成一份纯函数, 渲染/内容高度/条带切片三处同调。
 *
 * 全部 dp, 零 Android 依赖 (纯 JVM 可测)。
 */
object TodayRowGeometry {

    /** 单课胶囊高 — renderTodayRegular 同款 (改这边必同步那边, 测试锁死)。 */
    const val ROW_H_DP = 38f

    /** 行间距 — 课程胶囊间距 (用户反馈"太紧凑"后放大值)。 */
    const val ROW_GAP_DP = 10f

    /** 同栏堆叠课间距。 */
    const val STACK_GAP_DP = 3f

    /** 顶 pad — 位图顶部内边距。 */
    const val PAD_TOP_DP = 14f

    /** 标题行前进量 (headerSpace=false 时头部空档)。 */
    const val HEADER_ADVANCE_DP = 24f

    /** 底 pad — 内容区底部内边距。 */
    const val PAD_BOTTOM_DP = 14f

    /** 一行的纵向 span (dp) — 行序 + [topDp, bottomDp) 区间。 */
    data class RowSpan(
        val rowIndex: Int,
        val row: ConflictLayoutEngine.WeekLaneRow,
        val topDp: Float,
        val bottomDp: Float
    )

    /** 行起点 dp — headerSpace=true 条带去头从第一行课程直接起 (14dp 顶 pad)。 */
    fun contentTopDp(headerSpace: Boolean): Float =
        if (headerSpace) PAD_TOP_DP else PAD_TOP_DP + HEADER_ADVANCE_DP

    /**
     * 全部渲染行的纵向 span, 顺序 = 渲染顺序 (weekLaneRows 行序)。
     * 冲突行高 = 最高栏堆叠数 × ROW_H + (堆叠数−1) × STACK_GAP —
     * 与分栏渲染 (drawCourse 逐栏堆叠) 同一公式, 一处定义。
     */
    fun rowSpans(courses: List<CourseEntity>, headerSpace: Boolean): List<RowSpan> {
        val spans = ArrayList<RowSpan>()
        var y = contentTopDp(headerSpace)
        ConflictLayoutEngine.weekLaneRows(courses).forEachIndexed { idx, row ->
            val h = rowHeightDp(row)
            spans += RowSpan(idx, row, y, y + h)
            y += h + ROW_GAP_DP
        }
        return spans
    }

    /** 单行高 — 无冲突 38dp; 冲突行按最高栏堆叠数。 */
    fun rowHeightDp(row: ConflictLayoutEngine.WeekLaneRow): Float =
        if (row.laneCount == 1) ROW_H_DP
        else {
            val maxStack = row.courses.groupBy { row.laneOf[it.id] }.values
                .maxOf { it.size }.coerceAtLeast(1)
            maxStack * ROW_H_DP + (maxStack - 1) * STACK_GAP_DP
        }

    /**
     * 内容全展开高度 (dp) — 末行底 + 底 pad。
     * 空列表 = 起点 + 底 pad (与渲染的空态分支自洽, 调用方负责空态语义行)。
     */
    fun contentHeightDp(courses: List<CourseEntity>, headerSpace: Boolean): Float =
        rowSpans(courses, headerSpace).lastOrNull()?.let { it.bottomDp + PAD_BOTTOM_DP }
            ?: contentTopDp(headerSpace) + PAD_BOTTOM_DP

    /**
     * v11: 分页模型 (v9.x pageOffsetsDp / MAX_PAGES) 与逐行子项 (v10 renderTodayRow)
     * 两者都已退场, 仅留 rowSpans/rowHeightDp 作为 Today 渲染几何唯一真值。
     *
     * 真实机翻车链: v9.x 固定视口分页 = 轻微溢出场景跨缝重复 (用户原话: 「3 节课
     * 只看到 2 节半 + 拖到底末页把前 2 节又画了一遍」); v10 逐行子项 = 多 child 在
     * OPPO launcher 上复发了 v1 extent 冻结 (无法拖动) + 透明背景叠加壳图错位 +
     * 第一行覆盖标题区 TopBar 消失 — 三症状同根。回归 v9.1: 单 child 整张不透明长图,
     * 与 TwoDay/WeekList 同构 (同台 OPPO 一直正常), 整图一次画到底, 滚动 = launcher
     * 原生 ListView 在单 child 上完成, 不再有分页不再有多子项。
     */
}
