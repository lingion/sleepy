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
    /**
     * 每排卡片的统一框宽高比: 行高由本排「大卡」(声明面积最大档)决定 ——
     * 大卡按自身声明比例正常撑满, 同排小卡等比缩放到与大卡同高后居中(Fit)。
     * 数据驱动自 variants 声明尺寸 + 列数, 新增档位自动跟随, 零硬编码。
     * 用户定则「大的卡片正常撑满, 小的卡片跟它缩到一样高度」。
     */
    fun rowBoxAspectRatios(
        variants: List<WidgetVariantInfo>,
        columns: Int,
    ): List<Float> {
        require(columns > 0) { "columns must be positive" }
        return variants.mapIndexed { index, _ ->
            val rowStart = index / columns * columns
            val row = variants.subList(rowStart, minOf(rowStart + columns, variants.size))
            val dominant = row.maxOf { it.widthDp.toLong() * it.heightDp }
            val leader = row.first { it.widthDp.toLong() * it.heightDp == dominant }
            leader.widthDp.toFloat() / leader.heightDp.toFloat()
        }
    }

    fun buildCards(): List<WidgetGalleryCard> =
        ALL_WIDGET_VARIANTS.map { WidgetGalleryCard(it) }
}
