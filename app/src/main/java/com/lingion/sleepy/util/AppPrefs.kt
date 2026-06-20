package com.lingion.sleepy.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * App 级别轻量设置 — 避免引入 DataStore 依赖。
 * 进程内 mutableStateOf 同步给 UI，磁盘做持久化。
 */
object AppPrefs {
    private const val FILE = "sleepy_prefs"
    const val KEY_DARK = "dark_mode"
    const val KEY_REMINDER = "daily_reminder"
    const val KEY_THEME = "theme_key"
    const val KEY_LANG = "language"
    const val KEY_DISPLAY_MODE = "display_mode" // "node" or "time"
    const val KEY_SHOW_DATE = "show_date"       // boolean
    const val KEY_VISIBLE_DAYS = "visible_days" // "1,2,3,4,5,6,7"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ===== 深色模式：Boolean 单选开关（最简单，主题色和深色是两轴独立）=====

    fun isDarkMode(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_DARK, false)

    fun setDarkMode(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_DARK, v).apply()
    }

    // ===== 主题色 =====

    fun getThemeKey(ctx: Context): String =
        sp(ctx).getString(KEY_THEME, com.lingion.sleepy.ui.theme.ThemePresets.KEY_DEFAULT)
            ?: com.lingion.sleepy.ui.theme.ThemePresets.KEY_DEFAULT

    fun setThemeKey(ctx: Context, key: String) {
        sp(ctx).edit().putString(KEY_THEME, key).apply()
    }

    fun themeKeyFlow(ctx: Context): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, k ->
            if (k == KEY_THEME) {
                val v = sp.getString(KEY_THEME, com.lingion.sleepy.ui.theme.ThemePresets.KEY_DEFAULT)
                    ?: com.lingion.sleepy.ui.theme.ThemePresets.KEY_DEFAULT
                trySend(v)
            }
        }
        val sp = sp(ctx)
        sp.registerOnSharedPreferenceChangeListener(listener)
        trySend(getThemeKey(ctx))
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    // ===== 提醒 =====

    fun isReminderEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_REMINDER, true)

    fun setReminderEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_REMINDER, v).apply()
    }

    // ===== 语言 =====

    fun getLanguage(ctx: Context): String =
        sp(ctx).getString(KEY_LANG, "zh-CN") ?: "zh-CN"

    fun setLanguage(ctx: Context, lang: String) {
        sp(ctx).edit().putString(KEY_LANG, lang).apply()
    }

    // ===== 显示模式：节次 / 时间 =====

    fun getDisplayMode(ctx: Context): String =
        sp(ctx).getString(KEY_DISPLAY_MODE, "node") ?: "node"

    fun setDisplayMode(ctx: Context, mode: String) {
        sp(ctx).edit().putString(KEY_DISPLAY_MODE, mode).apply()
    }

    // ===== 网格显示日期 =====

    fun isShowDate(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_SHOW_DATE, false)

    fun setShowDate(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_SHOW_DATE, v).apply()
    }

    // ===== 可见天 =====

    fun getVisibleDays(ctx: Context): Set<Int> {
        val raw = sp(ctx).getString(KEY_VISIBLE_DAYS, "1,2,3,4,5,6,7") ?: "1,2,3,4,5,6,7"
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun setVisibleDays(ctx: Context, days: Set<Int>) {
        sp(ctx).edit().putString(KEY_VISIBLE_DAYS, days.sorted().joinToString(",")).apply()
    }
}
