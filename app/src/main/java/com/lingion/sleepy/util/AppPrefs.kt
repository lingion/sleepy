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
    const val KEY_REMINDER = "reminder_master"      // master toggle (default false)
    const val KEY_DAILY_ENABLED = "daily_reminder"   // daily sub-toggle (default true)
    const val KEY_DAILY_TIME = "daily_reminder_time" // "HH:mm" default "07:00"
    const val KEY_BEFORE_CLASS_ENABLED = "before_class_enabled"       // bool default false
    const val KEY_BEFORE_CLASS_MINUTES = "before_class_minutes"       // int default 10
    const val KEY_BEFORE_CLASS_BANNER = "before_class_banner"         // bool default true
    const val KEY_BEFORE_CLASS_FLUID = "before_class_fluid"            // bool default false
    const val KEY_BEFORE_CLASS_FLUID_FIELDS = "before_class_fluid_fields" // legacy multi-select
    const val KEY_BEFORE_CLASS_FLUID_PRIMARY = "before_class_fluid_primary" // name/time/room
    const val KEY_THEME = "theme_key"
    const val KEY_LANG = "language"
    const val KEY_DISPLAY_MODE = "display_mode" // "node" or "time"
    const val KEY_SHOW_DATE = "show_date"       // boolean
    const val KEY_VISIBLE_DAYS = "visible_days" // "1,2,3,4,5,6,7"
    const val KEY_VERT_PUNCT_REPLACE = "vert_punct_replace" // bool default false (方案B开关)

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

    /** Master toggle — default false */
    fun isReminderEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_REMINDER, false)

    fun setReminderEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_REMINDER, v).apply()
    }

    /** Daily reminder sub-toggle — default true (only active when master on) */
    fun isDailyReminderEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_DAILY_ENABLED, true)

    fun setDailyReminderEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_DAILY_ENABLED, v).apply()
    }

    /** Daily reminder time "HH:mm" — default "07:00" */
    fun getDailyReminderTime(ctx: Context): String =
        sp(ctx).getString(KEY_DAILY_TIME, "07:00") ?: "07:00"

    fun setDailyReminderTime(ctx: Context, time: String) {
        sp(ctx).edit().putString(KEY_DAILY_TIME, time).apply()
    }

    /** Before-class reminder sub-toggle — default false */
    fun isBeforeClassEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_BEFORE_CLASS_ENABLED, false)

    fun setBeforeClassEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_BEFORE_CLASS_ENABLED, v).apply()
    }

    /** Minutes before class to notify — default 10 */
    fun getBeforeClassMinutes(ctx: Context): Int =
        sp(ctx).getInt(KEY_BEFORE_CLASS_MINUTES, 10)

    fun setBeforeClassMinutes(ctx: Context, minutes: Int) {
        sp(ctx).edit().putInt(KEY_BEFORE_CLASS_MINUTES, minutes).apply()
    }

    fun isBeforeClassBannerEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_BEFORE_CLASS_BANNER, true)

    fun setBeforeClassBannerEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_BEFORE_CLASS_BANNER, v).apply()
    }

    fun isBeforeClassFluidEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_BEFORE_CLASS_FLUID, false)

    fun setBeforeClassFluidEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_BEFORE_CLASS_FLUID, v).apply()
    }

    fun getBeforeClassFluidFields(ctx: Context): Set<String> =
        (sp(ctx).getString(KEY_BEFORE_CLASS_FLUID_FIELDS, "name,time,room,teacher")
            ?: "name,time,room,teacher").split(",").filter { it.isNotBlank() }.toSet()

    fun setBeforeClassFluidFields(ctx: Context, fields: Set<String>) {
        sp(ctx).edit().putString(KEY_BEFORE_CLASS_FLUID_FIELDS, fields.joinToString(",")).apply()
    }

    fun getBeforeClassFluidPrimary(ctx: Context): String =
        sp(ctx).getString(KEY_BEFORE_CLASS_FLUID_PRIMARY, "room") ?: "room"

    fun setBeforeClassFluidPrimary(ctx: Context, value: String) {
        require(value == "name" || value == "time" || value == "room")
        // ★ 只写 PRIMARY；不再覆盖 FIELDS（多选字段集），否则用户配置的多字段组合被冲掉。
        sp(ctx).edit().putString(KEY_BEFORE_CLASS_FLUID_PRIMARY, value).apply()
    }


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

    // ===== 竖排标点优化(方案B: 标点替换为 Unicode Vertical Forms) — 默认 false =====

    fun isVertPunctReplace(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_VERT_PUNCT_REPLACE, false)

    fun setVertPunctReplace(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_VERT_PUNCT_REPLACE, v).apply()
    }
}
