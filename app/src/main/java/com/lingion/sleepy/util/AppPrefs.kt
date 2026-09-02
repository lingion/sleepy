package com.lingion.sleepy.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * App 级别轻量设置 — 避免引入 DataStore 依赖。
 * 进程内 mutableStateOf 同步给 UI，磁盘做持久化。
 */
object AppPrefs {
    private const val FILE = "sleepy_prefs"

    /**
     * 全局 prefs key 变化广播 — UI 用它主动 recompose 而非依赖 SharedPreferences 监听器
     * (主视图在 Compose 里读 prefs, 没显式订阅者就感知不到 change)
     */
    private val _changeBus = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val changeBus: Flow<String> = _changeBus.asSharedFlow()
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
    const val KEY_CONFLICT_STYLE = "conflict_style" // "stack" / "fold" / "rail" — 冲突课程显示样式（网格视图同格冲突时；stack=叠层偏移, fold=折角揭示, rail=侧边竖轨, 默认 "rail"）
    const val KEY_CONFLICT_TOP_INSET = "conflict_top_inset" // Float dp — 冲突顶卡收窄量: A=右/下偏移 d, C=右缘让宽; 滑杆范围 CONFLICT_TOP_INSET_RANGE, 默认 7dp(用户 2026-09-01 实测定版)
    val CONFLICT_TOP_INSET_RANGE = 4f..20f        // 滑杆量程(dp)
    const val CONFLICT_TOP_INSET_DEFAULT = 7f     // 默认(dp) — 用户实测定版
    const val KEY_START_VIEW = "start_view" // "full" / "cards" — 启动默认视图（仅通用设置里设置；手动切换课表顶部视图不写入）
    const val KEY_SHOW_DATE = "show_date"       // boolean
    const val KEY_VISIBLE_DAYS = "visible_days" // "1,2,3,4,5,6,7"
    const val KEY_VERT_PUNCT_REPLACE = "vert_punct_replace" // bool default false (方案B开关)
    const val KEY_WIDGET_COLORLESS = "widget_colorless" // bool default false
    const val KEY_COURSE_COLORLESS = "course_colorless" // bool default false (App 课程胶囊专用)
    const val KEY_WIDGET_SEPARATOR = "widget_separator" // bool default true (WeekView 纯文字课程间分隔线)
    const val KEY_GRID_SCALE = "grid_scale" // float 0.7~1.3 default 1.0 — 网格视图整体缩放(字号/行高/间距/圆角联动, issue#8)
    const val KEY_WEEK_SCALE = "week_scale" // float 0.7~1.3 default 1.0 — 周视图整体缩放(与网格视图互相独立, issue#8)
    const val KEY_GRID_CORNER_RATIO = "grid_corner_ratio" // float 0.0~2.0 default 1.0 — 网格/周视图圆角比例系数(乘基准 12/16dp, issue#8)
    const val KEY_WEEK_TWO_COLUMN = "week_two_column" // bool default false — 周视图两栏开关, issue#8
    const val KEY_WEEK_TWO_COLUMN_MODE = "week_two_column_mode" // "days"=按天对半分 / "balance"=按课程数动态平衡, issue#8
    const val KEY_WEEK_HIDE_EMPTY_DAYS = "week_hide_empty_days" // bool default false — 周视图隐藏无课日(仅两栏下生效, issue#8)
    const val KEY_UPDATE_CHECK_ENABLED = "update_check_enabled" // bool default true — 启动检查 GitHub releases latest
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
    const val KEY_CONFLICT_DEFAULT_TOP = "conflict_default_top"      // JSON {"day:startNode:step": layerRepId} — 冲突簇默认置顶图层; 默认空 = 全由 primaryComparator 决

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

    // ===== 冲突课程显示样式：叠层 / 折角 / 竖轨 =====

    fun getConflictStyle(ctx: Context): String =
        sp(ctx).getString(KEY_CONFLICT_STYLE, "rail") ?: "rail"

    fun setConflictStyle(ctx: Context, value: String) {
        require(value == "stack" || value == "fold" || value == "rail")
        sp(ctx).edit().putString(KEY_CONFLICT_STYLE, value).apply()
    }

    // ===== 冲突顶层卡收窄量(A 偏移 d / C 右缘让宽共用,用户可调) =====

    fun getConflictTopInset(ctx: Context): Float =
        sp(ctx).getFloat(KEY_CONFLICT_TOP_INSET, CONFLICT_TOP_INSET_DEFAULT)

    fun setConflictTopInset(ctx: Context, value: Float) {
        require(value in CONFLICT_TOP_INSET_RANGE)
        sp(ctx).edit().putFloat(KEY_CONFLICT_TOP_INSET, value).apply()
    }

    // ===== 冲突簇默认置顶图层 =====
    // JSON Map<clusterKey, layerRepId>: clusterKey = "${day}:${startNode}:${step}",
    // layerRepId = 该簇某图层的 representative course id(选 layer = 整图层置顶,保持图层原子性)。
    // 写入空串视为清空(unset 单值);整个 map 用 JSON 编码,简易 org.json 实现,避免引第三方。

    fun getConflictDefaultTop(ctx: Context): Map<String, Long> {
        // 内存真相源优先:点击 → putConflictDefaultTop 同步更新 StateFlow → 订阅方
        // 同帧 recompose(用户 2026-09-02:「勾选的那一瞬间课表就应该完成置顶更新」)。
        // SharedPreferences 监听器回调不保证同帧(apply 异步落盘后才触发)。
        _conflictDefaultTopState.value.let { return it }
    }

    fun setConflictDefaultTop(ctx: Context, map: Map<String, Long>) {
        _conflictDefaultTopState.value = map.toMap()   // 同步内存 → 瞬时驱动 UI
        sp(ctx).edit().putString(KEY_CONFLICT_DEFAULT_TOP, encodeDefaultTopMap(map)).apply()
        _changeBus.tryEmit(KEY_CONFLICT_DEFAULT_TOP)
    }

    /** 修改单个 clusterKey;传 null = 删除该键(回退系统默认)。 */
    fun putConflictDefaultTop(ctx: Context, clusterKey: String, layerRepId: Long?) {
        val current = getConflictDefaultTop(ctx).toMutableMap()
        if (layerRepId == null) current.remove(clusterKey) else current[clusterKey] = layerRepId
        setConflictDefaultTop(ctx, current)
    }

    /**
     * 冷启动加载磁盘值进内存真相源 — 在 App 首个 Composable 订阅前调用一次即可,
     * 幂等(磁盘值与内存一致时 StateFlow 不发射)。
     */
    fun primeConflictDefaultTop(ctx: Context) {
        val disk = decodeDefaultTopMap(sp(ctx).getString(KEY_CONFLICT_DEFAULT_TOP, "{}") ?: "{}")
        if (_conflictDefaultTopState.value != disk) _conflictDefaultTopState.value = disk
    }

    /**
     * v7.10.2 瞬时更新通道 — StateFlow 调用 setValue 即同步换值,
     * collectAsState 订阅方同一帧 recompose;替代原 callbackFlow+SharedPreferences
     * 监听器路径(apply 异步 → 监听回调晚于点击帧,网格刷新滞后)。
     */
    private val _conflictDefaultTopState = MutableStateFlow<Map<String, Long>>(emptyMap())
    val conflictDefaultTopState: StateFlow<Map<String, Long>> = _conflictDefaultTopState.asStateFlow()

    fun conflictDefaultTopFlow(ctx: Context): Flow<Map<String, Long>> {
        primeConflictDefaultTop(ctx)
        return conflictDefaultTopState
    }

    private fun encodeDefaultTopMap(map: Map<String, Long>): String {
        val obj = org.json.JSONObject()
        for ((k, v) in map) obj.put(k, v)
        return obj.toString()
    }

    private fun decodeDefaultTopMap(json: String): Map<String, Long> {
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            val out = mutableMapOf<String, Long>()
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = obj.optLong(k, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: continue
            }
            out
        } catch (_: Exception) { emptyMap() }
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

    // ===== 视图整体缩放(issue#8) — 0.7~1.3, 默认 1.0 =====
    // 网格视图与周视图各一个系数, 互相独立(用户: 两边最优缩放不一样)
    // 联动项: 字号/行高/间距/圆角/内边距; 不影响小组件与列表视图

    fun getGridScale(ctx: Context): Float =
        sp(ctx).getFloat(KEY_GRID_SCALE, 1.0f).coerceIn(0.7f, 1.3f)

    fun setGridScale(ctx: Context, v: Float) {
        sp(ctx).edit().putFloat(KEY_GRID_SCALE, v.coerceIn(0.7f, 1.3f)).apply()
        _changeBus.tryEmit(KEY_GRID_SCALE)
    }

    fun getWeekScale(ctx: Context): Float =
        sp(ctx).getFloat(KEY_WEEK_SCALE, 1.0f).coerceIn(0.7f, 1.3f)

    fun setWeekScale(ctx: Context, v: Float) {
        sp(ctx).edit().putFloat(KEY_WEEK_SCALE, v.coerceIn(0.7f, 1.3f)).apply()
        _changeBus.tryEmit(KEY_WEEK_SCALE)
    }

    // ===== 网格/周视图圆角比例(issue#8) — 0.0~2.0, 默认 1.0 =====
    // 系数乘基准圆角(网格卡 12dp/列表外框 16dp): 0=直角, 1=默认, 2=超圆。两视图共用一个值。

    fun getGridCornerRatio(ctx: Context): Float =
        sp(ctx).getFloat(KEY_GRID_CORNER_RATIO, 1.0f).coerceIn(0f, 2f)

    fun setGridCornerRatio(ctx: Context, v: Float) {
        sp(ctx).edit().putFloat(KEY_GRID_CORNER_RATIO, v.coerceIn(0f, 2f)).apply()
        _changeBus.tryEmit(KEY_GRID_CORNER_RATIO)
    }

    // ===== 周视图两栏(issue#8) — 默认关 =====
    // 开启后周视图课程列表拆左右两栏, 省纵向滚动; 分栏标准二选一:
    //   days    = 按天对半分(前半周左/后半周右, 天数固定)
    //   balance = 按当天课程数动态平衡(逐天放进课少的栏, 两栏高度接近; 天位置不固定)

    fun isWeekTwoColumn(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_WEEK_TWO_COLUMN, false)

    fun setWeekTwoColumn(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_WEEK_TWO_COLUMN, v).apply()
        _changeBus.tryEmit(KEY_WEEK_TWO_COLUMN)
    }

    fun getWeekTwoColumnMode(ctx: Context): String =
        sp(ctx).getString(KEY_WEEK_TWO_COLUMN_MODE, "days") ?: "days"

    fun setWeekTwoColumnMode(ctx: Context, v: String) {
        sp(ctx).edit().putString(KEY_WEEK_TWO_COLUMN_MODE, if (v == "balance") "balance" else "days").apply()
        _changeBus.tryEmit(KEY_WEEK_TWO_COLUMN_MODE)
    }

    fun isWeekHideEmptyDays(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_WEEK_HIDE_EMPTY_DAYS, false)

    fun setWeekHideEmptyDays(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_WEEK_HIDE_EMPTY_DAYS, v).apply()
        _changeBus.tryEmit(KEY_WEEK_HIDE_EMPTY_DAYS)
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

    // ===== 启动检查更新开关 =====

    fun isUpdateCheckEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_UPDATE_CHECK_ENABLED, true)

    fun setUpdateCheckEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_UPDATE_CHECK_ENABLED, v).apply()
    }
}
