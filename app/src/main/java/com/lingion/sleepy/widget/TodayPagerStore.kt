package com.lingion.sleepy.widget

import android.content.Context

/**
 * 每小组件实例的当前页码持久化 — 与 TodayDateNavStore 同模式 (SharedPreferences)。
 *
 * 页码是易失 UI 态: 数据刷新/日期变化后页数可能变少 → 读取侧永远经
 * [TodayPagerCore.clampPage] 收敛, 存储值只作"上次看到哪页"的提示。
 */
object TodayPagerStore {
    private const val PREFS = "today_pager"
    private const val KEY_PREFIX = "page_"

    fun page(context: Context, widgetId: Int): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PREFIX + widgetId, 0)

    fun setPage(context: Context, widgetId: Int, page: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PREFIX + widgetId, page).apply()
    }

    fun remove(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_PREFIX + widgetId).apply()
    }
}
