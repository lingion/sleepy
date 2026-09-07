package com.lingion.sleepy.widget

import android.content.Context

/**
 * Per-widget tableId binding store. Backed by app-private SharedPreferences.
 *
 * Bindings survive table deletion (lazy invalidation: callers fall back to
 * `WidgetTableResolver.resolveCurrentTable()` when the bound table id no
 * longer resolves). Receivers clear their own entry in `onDeleted` so a
 * recycled widget id does not inherit a previous widget's binding.
 *
 * All non-trivial logic lives in [WidgetBindingCore] and is tested in
 * pure-JVM unit tests; this class is a thin Android facade.
 */
object WidgetBindingStore {
    fun get(context: Context, widgetId: Int): Long? =
        WidgetBindingCore.read(loadAll(context), widgetId)

    fun put(context: Context, widgetId: Int, tableId: Long) {
        val data = loadAll(context).toMutableMap()
        WidgetBindingCore.write(data, widgetId, tableId)
        saveAll(context, data)
    }

    fun remove(context: Context, widgetId: Int) {
        val data = loadAll(context).toMutableMap()
        WidgetBindingCore.delete(data, widgetId)
        saveAll(context, data)
    }

    fun getAll(context: Context): Map<Int, Long> =
        WidgetBindingCore.parseAll(loadAll(context))

    private fun loadAll(context: Context): Map<String, Long> {
        val sp = context.getSharedPreferences(
            WidgetBindingCore.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        return sp.all.mapNotNull { (k, v) -> if (v is Long) k to v else null }.toMap()
    }

    private fun saveAll(context: Context, data: Map<String, Long>) {
        val sp = context.getSharedPreferences(
            WidgetBindingCore.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val editor = sp.edit().clear()
        data.forEach { (k, v) -> editor.putLong(k, v) }
        editor.apply()
    }
}
