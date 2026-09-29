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
 */
data class WidgetVariantInfo(
    val receiverClass: Class<out AppWidgetProvider>,
    val displayNameRes: Int,
    val previewImageRes: Int = 0
)

val ALL_WIDGET_VARIANTS: List<WidgetVariantInfo> = listOf(
    WidgetVariantInfo(WeekGridWidgetProvider::class.java,      R.string.widget_week_grid_label, R.drawable.widget_preview_weekgrid),
    WidgetVariantInfo(WeekGridSmallWidgetProvider::class.java, R.string.widget_week_grid_small_label, R.drawable.widget_preview_weekgrid_small),
    WidgetVariantInfo(TodayWidgetReceiver::class.java,        R.string.widget_today_label, R.drawable.widget_preview_today),
    WidgetVariantInfo(TodaySmallWidgetReceiver::class.java,   R.string.widget_today_small_label, R.drawable.widget_preview_today_small),
    WidgetVariantInfo(WeekListWidgetReceiver::class.java,     R.string.widget_week_list_label, R.drawable.widget_preview_weeklist),
    WidgetVariantInfo(WeekListSmallWidgetReceiver::class.java, R.string.widget_week_list_small_label, R.drawable.widget_preview_weeklist_small),
    WidgetVariantInfo(WeekViewWidgetReceiver::class.java,     R.string.widget_week_view_label, R.drawable.widget_preview_weekview),
    WidgetVariantInfo(WeekViewSmallWidgetReceiver::class.java, R.string.widget_week_view_small_label, R.drawable.widget_preview_weekview_small),
    WidgetVariantInfo(TwoDayWidgetReceiver::class.java,       R.string.widget_twoday_label, R.drawable.widget_preview_twoday),
    WidgetVariantInfo(TwoDaySmallWidgetReceiver::class.java,  R.string.widget_twoday_small_label, R.drawable.widget_preview_twoday_small),
    WidgetVariantInfo(TodayWideWidgetReceiver::class.java,    R.string.widget_today_wide_label, R.drawable.widget_preview_today_wide),
    WidgetVariantInfo(TwoDayWideWidgetReceiver::class.java,   R.string.widget_twoday_wide_label, R.drawable.widget_preview_twoday_wide),
    WidgetVariantInfo(WeekListWideWidgetReceiver::class.java, R.string.widget_week_list_wide_label, R.drawable.widget_preview_weeklist_wide)
)
