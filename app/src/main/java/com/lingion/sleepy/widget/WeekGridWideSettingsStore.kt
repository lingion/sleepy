package com.lingion.sleepy.widget

import android.content.Context

/** The provider fixes the layout. Only the horizontal date-label side is persisted. */
object WeekGridWideSettingsStore {
    fun read(context: Context, widgetId: Int): WeekGridWideSettings {
        val prefs = context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE)
        val side = prefs.all["side_$widgetId"] as? String
        val receiverName = android.appwidget.AppWidgetManager.getInstance(context)
            .getAppWidgetInfo(widgetId)?.provider?.className
        return WeekGridWideSettings(
            WeekGridWideWidgetProvider.modeForReceiverName(receiverName) ?: WeekGridWideMode.VERTICAL,
            WeekGridWideLabelSide.entries.firstOrNull { it.name == side } ?: WeekGridWideLabelSide.LEFT,
        )
    }

    fun write(context: Context, widgetId: Int, settings: WeekGridWideSettings) {
        context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE).edit()
            .remove("mode_$widgetId").putString("side_$widgetId", settings.labelSide.name).apply()
    }

    fun remove(context: Context, widgetId: Int) {
        context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE).edit()
            .remove("mode_$widgetId").remove("side_$widgetId").apply()
    }
}
