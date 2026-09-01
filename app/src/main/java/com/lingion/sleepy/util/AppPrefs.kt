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
    const val KEY_GRID_SUB_INFO = "grid_sub_info" // "room" / "teacher" / "none" — 网格卡片副信息（周视图网格卡课程名下方那行；左栏已有节次，故此处不再显示节次/时间）
    const val KEY_START_VIEW = "start_view" // "full" / "cards" — 启动默认视图（仅通用设置里设置；手动切换课表顶部视图不写入）
    const val KEY_SHOW_DATE = "show_date"       // boolean
    const val KEY_VISIBLE_DAYS = "visible_days" // "1,2,3,4,5,6,7"
    const val KEY_VERT_PUNCT_REPLACE = "vert_punct_replace" // bool default false (方案B开关)
    const val KEY_WIDGET_COLORLESS = "widget_colorless" // bool default false
    const val KEY_COURSE_COLORLESS = "course_colorless" // bool default false (App 课程胶囊专用)
    const val KEY_WIDGET_SEPARATOR = "widget_separator" // bool default true (WeekView 纯文字课程间分隔线)
    const val KEY_GRID_SCALE = "grid_scale" // float 0.7~1.3 default 1.0 — 网格课表整体缩放(字号/行高/间距/圆角联动, issue#8)
    const val KEY_THEME_MODE = "theme_mode"  // light/dark/system
    const val THEME_MODE_LIGHT = "light"
    const val THEME_MODE_DARK = "dark"
    const val THEME_MODE_SYSTEM = "system"

    // ===== 节假日灰显开关 =====
    const val KEY_HOLIDAY_GREY_HOLIDAY = "holiday_grey_holiday"   // bool default true
    const val KEY_HOLIDAY_GREY_WEEKEND = "holiday_grey_weekend"   // bool default true
    const val KEY_HOLIDAY_STYLE = "holiday_style"                  // "grey" / "strikethrough" default "grey"
    const val KEY_HOLIDAY_IGNORE_WORKDAY = "holiday_ignore_workday" // bool default true (补班日忽略)
    const val KEY_HOLIDAY_OVERRIDES = "holiday_overrides"           // JSON — 用户范围化覆盖(编辑/新增/删除节日段)

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 实际是否深色：dark→true, light→false, system→isSystemDark。isSystemDark 由调用方传入。 */
    fun isDarkMode(ctx: Context, isSystemDark: Boolean = false): Boolean {
        // 向后兼容：旧 boolean KEY_DARK 在无新三态时生效
        if (!sp(ctx).contains(KEY_THEME_MODE)) {
            val legacy = sp(ctx).all[KEY_DARK] as? Boolean
            if (legacy != null) return legacy
        }
        return when (getThemeMode(ctx)) {
            THEME_MODE_DARK -> true
            THEME_MODE_LIGHT -> false
            else -> isSystemDark
        }
    }

    // isSystemDark 由 UI 层用 isSystemInDarkTheme() 传入，避免在 object 里取系统配置。


    /** 主题模式：light / dark / system。默认 system。 */
    fun getThemeMode(ctx: Context): String =
        sp(ctx).getString(KEY_THEME_MODE, THEME_MODE_SYSTEM) ?: THEME_MODE_SYSTEM

    fun setThemeMode(ctx: Context, mode: String) {
        require(mode == THEME_MODE_LIGHT || mode == THEME_MODE_DARK || mode == THEME_MODE_SYSTEM)
        sp(ctx).edit().putString(KEY_THEME_MODE, mode).apply()
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

    // setBeforeClassFluidFields 死写路径已删（legacy 多选写入口, 全库零调用; 读取仅 BeforeClassNotifyReceiver 用旧数据）

    fun getBeforeClassFluidPrimary(ctx: Context): String =
        sp(ctx).getString(KEY_BEFORE_CLASS_FLUID_PRIMARY, "room") ?: "room"

    fun setBeforeClassFluidPrimary(ctx: Context, value: String) {
        require(value == "name" || value == "time" || value == "room")
        // 只写 PRIMARY；不再覆盖 FIELDS（多选字段集），否则用户配置的多字段组合被冲掉。
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

    // ===== 网格卡片副信息：教室 / 教师 / 无 =====

    fun getGridSubInfo(ctx: Context): String =
        sp(ctx).getString(KEY_GRID_SUB_INFO, "room") ?: "room"

    fun setGridSubInfo(ctx: Context, value: String) {
        require(value == "room" || value == "teacher" || value == "none")
        sp(ctx).edit().putString(KEY_GRID_SUB_INFO, value).apply()
    }

    // ===== 启动默认视图：完整 / 卡片 =====

    fun getStartView(ctx: Context): String =
        sp(ctx).getString(KEY_START_VIEW, "full") ?: "full"

    fun setStartView(ctx: Context, value: String) {
        require(value == "full" || value == "cards")
        sp(ctx).edit().putString(KEY_START_VIEW, value).apply()
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

    // ===== 小组件无色模式 — 默认 false =====

    fun isWidgetColorless(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_WIDGET_COLORLESS, false)

    fun setWidgetColorless(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_WIDGET_COLORLESS, v).apply()
    }

    // ===== App 课程胶囊无色模式 — 默认 false =====

    fun isCourseColorless(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_COURSE_COLORLESS, false)

    fun setCourseColorless(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_COURSE_COLORLESS, v).apply()
    }

    // ===== WeekView 纯文字组件：课程间分隔线 — 默认 true =====

    fun isWidgetSeparator(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_WIDGET_SEPARATOR, true)

    fun setWidgetSeparator(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_WIDGET_SEPARATOR, v).apply()
    }

    // ===== 网格课表整体缩放(issue#8) — 0.7~1.3, 默认 1.0 =====
    // 联动项: 胶囊行高/字号/间距/圆角/内边距; 只影响 App 内网格课表(Cards), 不影响小组件与列表视图

    fun getGridScale(ctx: Context): Float =
        sp(ctx).getFloat(KEY_GRID_SCALE, 1.0f).coerceIn(0.7f, 1.3f)

    fun setGridScale(ctx: Context, v: Float) {
        sp(ctx).edit().putFloat(KEY_GRID_SCALE, v.coerceIn(0.7f, 1.3f)).apply()
    }

    // ===== 节假日灰显 =====

    /** 法定节假日灰显开关 — 默认 true */
    fun isHolidayGreyHoliday(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_HOLIDAY_GREY_HOLIDAY, true)

    fun setHolidayGreyHoliday(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_HOLIDAY_GREY_HOLIDAY, v).apply()
    }

    /** 周末灰显开关 — 默认 true */
    fun isHolidayGreyWeekend(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_HOLIDAY_GREY_WEEKEND, true)

    fun setHolidayGreyWeekend(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_HOLIDAY_GREY_WEEKEND, v).apply()
    }

    /** 灰显样式 — 默认 "grey" */
    fun getHolidayStyle(ctx: Context): String =
        sp(ctx).getString(KEY_HOLIDAY_STYLE, "grey") ?: "grey"

    fun setHolidayStyle(ctx: Context, style: String) {
        require(style == "grey" || style == "strikethrough")
        sp(ctx).edit().putString(KEY_HOLIDAY_STYLE, style).apply()
    }

    /** 忽略补班日 — 默认 true */
    fun isHolidayIgnoreWorkday(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_HOLIDAY_IGNORE_WORKDAY, true)

    fun setHolidayIgnoreWorkday(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_HOLIDAY_IGNORE_WORKDAY, v).apply()
    }

    // ===== 节假日用户覆盖（范围化段）=====

    /** 用户范围化覆盖段: 编辑/新增/删除节日段。JSON 由 HolidayRangeOps 编解码。 */
    fun getHolidayRanges(ctx: Context): List<com.lingion.sleepy.util.HolidayRange> =
        com.lingion.sleepy.util.HolidayRangeOps.decodeOverrides(sp(ctx).getString(KEY_HOLIDAY_OVERRIDES, "[]") ?: "[]")

    fun setHolidayRanges(ctx: Context, ranges: List<com.lingion.sleepy.util.HolidayRange>) {
        sp(ctx).edit().putString(KEY_HOLIDAY_OVERRIDES, com.lingion.sleepy.util.HolidayRangeOps.encodeOverrides(ranges)).apply()
    }
}
