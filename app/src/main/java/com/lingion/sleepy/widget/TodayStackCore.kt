package com.lingion.sleepy.widget

/**
 * 今日课程小组件 StackView 竖滑翻页 — 纯 JVM 状态/几何核心 (无 Robolectric 可测)。
 *
 * 平台事实 (AOSP 源码实锤, 2026-09-09):
 * - 标准 AppWidget 只能收点击; 白名单里唯一自带手势的视图 = StackView (竖直拖拽翻页),
 *   HorizontalScrollView 无 @RemoteView 注解 (塞入=launcher inflate 崩),
 *   AdapterViewFlipper 只有定时轮播无手势 → 横向滑动物理不可行。
 * - StackView 显示窗口从 adapter position 0 起, 只能向前翻 (swipe up → position+1),
 *   越过首张卡向前滑被框架钳制 → 卡片序 = [锚定日, +1, +2, …], 向过去翻走点击条。
 */
internal object TodayStackCore {

    /** 静态分支预留的底部导航条高度(dp) — 与 widget_today_stack_container.xml 同步。 */
    const val NAV_BAR_DP = 36f

    /** 翻页卡片数: 锚定日..锚定日+13, 前向约两周。 */
    const val ITEM_COUNT = 14

    /** adapter position → 该卡日期 epochDay (卡片序 = 锚定日起向前)。 */
    fun dayForPosition(anchorEpochDay: Long, position: Int): Long =
        anchorEpochDay + position.coerceIn(0, ITEM_COUNT - 1)

    /** 静态 StackView 分支闸门: 内容全展开高度须给底部导航条留出 NAV_BAR_DP。 */
    fun staticFits(contentHeightDp: Float, containerHeightDp: Float): Boolean =
        contentHeightDp <= containerHeightDp - NAV_BAR_DP
}
