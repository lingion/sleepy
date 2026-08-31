package com.lingion.sleepy.widget

/** 「本周课表（列表）· 小」变体壳 — 只标记 SMALL, 渲染/数据全部继承基类 */
class WeekListSmallWidgetReceiver : WeekListWidgetReceiver() {
    override val variantHint: WidgetVariant = WidgetVariant.SMALL
}
