package com.lingion.sleepy.widget

import android.appwidget.AppWidgetProvider
import com.lingion.sleepy.R

/**
 * Metadata describing one widget variant (base or small).
 *
 * The list [ALL_WIDGET_VARIANTS] is the single source of truth for both the
 * refresh broadcast dispatched by [WidgetUpdater] and the management screen UI.
 * Adding a new widget must add exactly one entry here; the refresh broadcast
 * is derived automatically.
 *
 * [widthDp]/[heightDp] 是该 variant 在 info XML 里的声明尺寸(minWidth/minHeight)。
 * 添加画廊按它渲染卡片比例, [WidgetPreviewFacesContractTest] 锁两处尺寸一致。
 */
data class WidgetVariantInfo(
    val receiverClass: Class<out AppWidgetProvider>,
    val displayNameRes: Int,
    val previewImageRes: Int = 0,
    val widthDp: Int = 0,
    val heightDp: Int = 0
)

// 三档声明尺寸, 与 res/xml/*_widget_info.xml 的 minWidth/minHeight 逐一对应
private const val S_W = 110; private const val S_H = 110      // 2×2
private const val M_W = 300; private const val M_H = 160      // 4×2
private const val L_W = 300; private const val L_H = 250      // 4×4

val ALL_WIDGET_VARIANTS: List<WidgetVariantInfo> = listOf(
    WidgetVariantInfo(WeekGridWidgetProvider::class.java,      R.string.widget_week_grid_label, R.drawable.widget_preview_weekgrid, L_W, L_H),
    WidgetVariantInfo(WeekGridSmallWidgetProvider::class.java, R.string.widget_week_grid_small_label, R.drawable.widget_preview_weekgrid_small, S_W, S_H),
    WidgetVariantInfo(TodayWidgetReceiver::class.java,        R.string.widget_today_label, R.drawable.widget_preview_today, L_W, L_H),
    WidgetVariantInfo(TodaySmallWidgetReceiver::class.java,   R.string.widget_today_small_label, R.drawable.widget_preview_today_small, S_W, S_H),
    WidgetVariantInfo(WeekListWidgetReceiver::class.java,     R.string.widget_week_list_label, R.drawable.widget_preview_weeklist, L_W, L_H),
    WidgetVariantInfo(WeekListSmallWidgetReceiver::class.java, R.string.widget_week_list_small_label, R.drawable.widget_preview_weeklist_small, S_W, S_H),
    WidgetVariantInfo(WeekViewWidgetReceiver::class.java,     R.string.widget_week_view_label, R.drawable.widget_preview_weekview, L_W, L_H),
    WidgetVariantInfo(WeekViewSmallWidgetReceiver::class.java, R.string.widget_week_view_small_label, R.drawable.widget_preview_weekview_small, S_W, S_H),
    WidgetVariantInfo(TwoDayWidgetReceiver::class.java,       R.string.widget_twoday_label, R.drawable.widget_preview_twoday, L_W, L_H),
    WidgetVariantInfo(TwoDaySmallWidgetReceiver::class.java,  R.string.widget_twoday_small_label, R.drawable.widget_preview_twoday_small, S_W, S_H),
    WidgetVariantInfo(TodayWideWidgetReceiver::class.java,    R.string.widget_today_wide_label, R.drawable.widget_preview_today_wide, M_W, M_H),
    WidgetVariantInfo(TwoDayWideWidgetReceiver::class.java,   R.string.widget_twoday_wide_label, R.drawable.widget_preview_twoday_wide, M_W, M_H),
    WidgetVariantInfo(WeekListWideWidgetReceiver::class.java, R.string.widget_week_list_wide_label, R.drawable.widget_preview_weeklist_wide, M_W, M_H)
)
