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
    val displayNameRes: Int
)

val ALL_WIDGET_VARIANTS: List<WidgetVariantInfo> = listOf(
    WidgetVariantInfo(WeekGridWidgetProvider::class.java,      R.string.widget_week_grid_label),
    WidgetVariantInfo(WeekGridSmallWidgetProvider::class.java, R.string.widget_week_grid_small_label),
    WidgetVariantInfo(TodayWidgetReceiver::class.java,        R.string.widget_today_label),
    WidgetVariantInfo(TodaySmallWidgetReceiver::class.java,   R.string.widget_today_small_label),
    WidgetVariantInfo(WeekListWidgetReceiver::class.java,     R.string.widget_week_list_label),
    WidgetVariantInfo(WeekListSmallWidgetReceiver::class.java, R.string.widget_week_list_small_label),
    WidgetVariantInfo(WeekViewWidgetReceiver::class.java,     R.string.widget_week_view_label),
    WidgetVariantInfo(WeekViewSmallWidgetReceiver::class.java, R.string.widget_week_view_small_label),
    WidgetVariantInfo(TwoDayWidgetReceiver::class.java,       R.string.widget_twoday_label),
    WidgetVariantInfo(TwoDaySmallWidgetReceiver::class.java,  R.string.widget_twoday_small_label)
)
