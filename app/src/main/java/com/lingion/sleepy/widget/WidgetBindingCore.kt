package com.lingion.sleepy.widget

/**
 * Pure-JVM core of the widget binding store. All key shape decisions and
 * map operations live here so they can be unit-tested without Android or
 * Robolectric. The Android [WidgetBindingStore] facade is a thin wrapper
 * that loads / saves the resulting `Map<String, Long>` to SharedPreferences.
 */
internal object WidgetBindingCore {
    /** Prefix every widget binding key in SharedPreferences with this. */
    const val KEY_PREFIX: String = "app_widget_"

    /** SharedPreferences file name; kept in sync with [WidgetBindingStore]. */
    const val PREFS_NAME: String = "widget_bindings"

    fun key(widgetId: Int): String = "$KEY_PREFIX$widgetId"

    /** Parse raw prefs data into a `widgetId -> tableId` map, ignoring foreign keys. */
    fun parseAll(raw: Map<String, Long>): Map<Int, Long> =
        raw.mapNotNull { (k, v) ->
            val id = k.removePrefix(KEY_PREFIX).toIntOrNull()
            if (id != null) id to v else null
        }.toMap()

    fun read(raw: Map<String, Long>, widgetId: Int): Long? = raw[key(widgetId)]

    fun write(raw: MutableMap<String, Long>, widgetId: Int, tableId: Long) {
        raw[key(widgetId)] = tableId
    }

    fun delete(raw: MutableMap<String, Long>, widgetId: Int) {
        raw.remove(key(widgetId))
    }
}
