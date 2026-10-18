package com.lingion.sleepy.widget

/**
 * 画廊卡片 = ALL_WIDGET_VARIANTS 的 1:1 投影(设计 §4 不变量②)。
 *
 * 数量零硬编码: 注册表新增变体,画廊自动多一张卡片。
 * 纯数据、纯函数 — displayNameRes/previewImageRes 只是资源 id,
 * 不触碰 Android runtime,因此 [buildCards] 可进 JVM 单测。
 */
data class WidgetGalleryCard(
    val variant: WidgetVariantInfo,
) {
    val receiverName: String = variant.receiverClass.name
}

object WidgetGalleryData {
    fun buildCards(): List<WidgetGalleryCard> =
        ALL_WIDGET_VARIANTS.map { WidgetGalleryCard(it) }
}
