package com.lingion.sleepy.widget

/** 「本周课表（网格）· 小」变体壳 — 只标记 SMALL, 渲染/数据全部继承基类 */
class WeekGridSmallWidgetProvider : WeekGridWidgetProvider() {
    override val variantHint: WidgetVariant = WidgetVariant.SMALL
}
