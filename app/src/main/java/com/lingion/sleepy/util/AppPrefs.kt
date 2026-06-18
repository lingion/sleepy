package com.lingion.sleepy.util

import android.content.Context
import android.content.SharedPreferences
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
    private const val KEY_DARK = "dark_mode"
    private const val KEY_REMINDER = "daily_reminder"
    private const val KEY_THEME = "theme_key"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isDarkMode(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_DARK, false)

    fun setDarkMode(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_DARK, v).apply()
    }

    fun isReminderEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_REMINDER, true)

    fun setReminderEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_REMINDER, v).apply()
    }

    fun getThemeKey(ctx: Context): String =
        sp(ctx).getString(KEY_THEME, com.lingion.sleepy.ui.theme.ThemePresets.KEY_DEFAULT)
            ?: com.lingion.sleepy.ui.theme.ThemePresets.KEY_DEFAULT

    fun setThemeKey(ctx: Context, key: String) {
        sp(ctx).edit().putString(KEY_THEME, key).apply()
    }

    /**
     * 监听 theme_key 变化 — 用于主题切换 0 闪重组。
     * SharedPreferences 没有 StateFlow，自己用 callbackFlow 包 OnSharedPreferenceChangeListener。
     */
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
        // 立即 emit 当前值
        trySend(getThemeKey(ctx))
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
