package com.lingion.sleepy.widget

/** 「最近两天 · 小」变体壳 — 只标记 SMALL, 渲染/数据全部继承基类 */
class TwoDaySmallWidgetReceiver : TwoDayWidgetReceiver() {
    override val variantHint: WidgetVariant = WidgetVariant.SMALL
}
