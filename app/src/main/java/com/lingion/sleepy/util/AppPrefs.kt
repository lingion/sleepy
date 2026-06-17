package com.lingion.sleepy.util

import android.content.Context
import android.content.SharedPreferences

/**
 * App 级别轻量设置 — 避免引入 DataStore 依赖。
 * 进程内 mutableStateOf 同步给 UI，磁盘做持久化。
 */
object AppPrefs {
    private const val FILE = "sleepy_prefs"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_REMINDER = "daily_reminder"

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
}
