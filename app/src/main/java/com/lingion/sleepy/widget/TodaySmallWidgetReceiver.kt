package com.lingion.sleepy.widget

/** 「今日课程 · 小」变体壳 — 只标记 SMALL, 渲染/数据全部继承基类 */
class TodaySmallWidgetReceiver : TodayWidgetReceiver() {
    override val variantHint: WidgetVariant = WidgetVariant.SMALL
}
