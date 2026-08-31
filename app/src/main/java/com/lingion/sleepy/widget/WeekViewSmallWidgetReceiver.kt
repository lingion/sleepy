package com.lingion.sleepy.widget

/** 「本周课表（周视图）· 小」变体壳 — 只标记 SMALL, 渲染/数据全部继承基类 */
class WeekViewSmallWidgetReceiver : WeekViewWidgetReceiver() {
    override val variantHint: WidgetVariant = WidgetVariant.SMALL
}
