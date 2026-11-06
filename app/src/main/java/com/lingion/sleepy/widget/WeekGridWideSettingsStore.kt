package com.lingion.sleepy.widget

import android.content.Context

/** New receiver only. Never inherits unrelated global/small-widget window preferences. */
object WeekGridWideSettingsStore {
    fun read(context: Context, widgetId: Int): WeekGridWideSettings {
        val prefs = context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE)
        val mode = prefs.all["mode_$widgetId"] as? String
        val side = prefs.all["side_$widgetId"] as? String
        return WeekGridWideSettings(
            WeekGridWideMode.entries.firstOrNull { it.name == mode } ?: WeekGridWideMode.VERTICAL,
            WeekGridWideLabelSide.entries.firstOrNull { it.name == side } ?: WeekGridWideLabelSide.LEFT,
        )
    }

    fun write(context: Context, widgetId: Int, settings: WeekGridWideSettings) {
        context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE).edit()
            .putString("mode_$widgetId", settings.mode.name).putString("side_$widgetId", settings.labelSide.name).apply()
    }

    fun remove(context: Context, widgetId: Int) {
        context.getSharedPreferences("widget_wide_grid", Context.MODE_PRIVATE).edit()
            .remove("mode_$widgetId").remove("side_$widgetId").apply()
    }
}
