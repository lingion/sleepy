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

/**
 * 画廊组件家族分组结构 (Milestone 1, PR 295)。
 *
 * 将 ALL_WIDGET_VARIANTS 按 5 个主家族 (WeekGrid, Today, WeekList, WeekView, TwoDay) 聚类。
 * [primaryVariant] 恒为该组面积最大档位 (4×4, 300×250),
 * [isMultiSize] 标识组内是否有多种尺寸变体,
 * [remainingVariants] 为展开时露出的次级尺寸变体列表。
 */
data class WidgetGalleryGroup(
    val key: String,
    val primaryVariant: WidgetVariantInfo,
    val allVariants: List<WidgetVariantInfo>,
    val isMultiSize: Boolean = allVariants.size > 1,
    val remainingVariants: List<WidgetVariantInfo> = allVariants.filter { it != primaryVariant },
) {
    /**
     * 格式化展示名称: 若为多尺寸组件则追加 "(多尺寸)", 单尺寸只显示原名。
     */
    fun formattedName(baseName: String): String =
        if (isMultiSize) "$baseName(多尺寸)" else baseName
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

    /**
     * 派生组件家族分组: 将 ALL_WIDGET_VARIANTS 聚类为 5 组,
     * 每组选取面积最大变体为 primaryVariant, 其余变体按尺寸有序排布。
     */
    fun buildGroups(): List<WidgetGalleryGroup> {
        val grouped = LinkedHashMap<String, MutableList<WidgetVariantInfo>>()
        for (variant in ALL_WIDGET_VARIANTS) {
            val key = widgetFamilyKey(variant)
            grouped.getOrPut(key) { mutableListOf() }.add(variant)
        }
        return grouped.map { (key, variants) ->
            val sortedVariants = variants.sortedByDescending { it.widthDp.toLong() * it.heightDp }
            val primary = sortedVariants.first()
            WidgetGalleryGroup(
                key = key,
                primaryVariant = primary,
                allVariants = sortedVariants,
            )
        }
    }

    fun buildCards(): List<WidgetGalleryCard> =
        ALL_WIDGET_VARIANTS.map { WidgetGalleryCard(it) }

    private fun widgetFamilyKey(variant: WidgetVariantInfo): String =
        variant.receiverClass.simpleName
            .removeSuffix("SmallWidgetProvider")
            .removeSuffix("SmallWidgetReceiver")
            .removeSuffix("WideWidgetReceiver")
            .removeSuffix("WideWidgetProvider")
            .removeSuffix("WidgetProvider")
            .removeSuffix("WidgetReceiver")
}
