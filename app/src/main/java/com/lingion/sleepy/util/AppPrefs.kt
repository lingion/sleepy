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
}
