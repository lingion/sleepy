package com.lingion.sleepy.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.ThemePresets
import com.lingion.sleepy.ui.theme.LightCoursePalette
import com.lingion.sleepy.ui.theme.DarkCoursePalette
import com.lingion.sleepy.ui.theme.WakeUpColorScheme
import com.lingion.sleepy.util.DateUtils
import java.time.LocalDate

/**
 * 小组件数据模型 + 配色派生 — 生产 RemoteViews 渲染链路
 * (WidgetBitmapRenderers / WeekGridWidgetProvider / WidgetRenderActivity) 共用。
 *
 * Glance composable 层(WidgetContent/WeekListContent/TwoDayContent/WeekGridContent)
 * 已删除(决策 D5-11): 5 个生产入口全走 RemoteViews + Canvas bitmap,
 * Glance 层生产不可达, 其旧关键词配色路径(CourseColorRules)一并移除。
 * 文件名保留 WidgetContent.kt 以减小 diff; 如需可后续重命名为 WidgetModels.kt。
 */

/**
 * 小组件渲染数据 — 让渲染端单纯绘制，不读 DB。
 * Receiver.loadDataSync 在后台线程拉数据，组装成这个 model 喂给 renderer。
 */
data class WidgetData(
    /** 今日日期 */
    val date: LocalDate,
    /** 今日课程（已按当前周次过滤 + 排序） */
    val courses: List<CourseEntity>,
    /** timeJson（用于查开始/结束时间） */
    val timeJson: String,
    /** 是否有课表 */
    val hasTable: Boolean,
    /** 跟 app 主题保持一致：true=深色小组件 */
    val isDark: Boolean = false,
    /** 跟 app 主题色（ThemePresets key） */
    val themeKey: String = ThemePresets.KEY_DEFAULT
) {
    val dayName: String get() = DateUtils.localizedDay(date.dayOfWeek.value, com.lingion.sleepy.SleepyApp.get())
    val dateLabel: String get() = "${date.monthValue}/${date.dayOfMonth}"
}

/**
 * 4 元组：背景 / 主题强调色 / 正文色 / 次要色
 * 跟 app M3 scheme 派生方式相同：surface / primary / onSurface / onSurfaceVariant
 * 额外携带课程调色板（从主题 container 色派生，跟随主题切换）
 */
data class WidgetScheme(
    val bg: Color = Color(0xFFFDFCFF),
    val surface: Color = Color(0xFFFFFBFE),
    val primary: Color = Color(0xFF6750A4),
    val primaryContainer: Color = Color(0xFFEADDFF),
    val onPrimaryContainer: Color = Color(0xFF1C1B1F),
    val onSurface: Color = Color(0xFF1C1B1F),
    val onSurfaceVariant: Color = Color(0xFF79747E),
    val surfaceContainer: Color = Color(0xFFF3EDF7),
    val surfaceVariant: Color = Color(0xFFE7E0EC),
    val isDark: Boolean = false,
    // 课程色 — 从主题 container 色派生
    val coursePrimary: Color = Color(0xFFEADDFF),
    val courseSecondary: Color = Color(0xFFE8DEF8),
    val courseTertiary: Color = Color(0xFFFFD8E4),
    val courseEnglish: Color = Color(0xFFD8F2FF),
    val courseMilitary: Color = Color(0xFFE7F3DC),
    val coursePhysics: Color = Color(0xFFFFE7C7),
    val courseHistory: Color = Color(0xFFF7D9D9),
    val coursePsychology: Color = Color(0xFFE6DDFB),
    val coursePractice: Color = Color(0xFFD7F0E8)
)

/**
 * 按 themeKey + isDark 派生小组件配色。
 *
 * ★ themeKey == "system" 时走 Material You 动态取色(dynamicLightColorScheme / dynamicDarkColorScheme),
 *   与 [com.lingion.sleepy.ui.theme.SleepyThemeProvider] 的处理对齐 — 之前 widget 把 "system"
 *   当未知 key → ThemePresets.byKey 返回 Default(紫色) → 小组件永远紫色, 不跟随系统壁纸取色。
 *
 * 课程色使用 app 的 LightCoursePalette / DarkCoursePalette（全局统一，不随主题变）。
 */
internal fun resolveSchemePublic(context: Context, themeKey: String, isDark: Boolean): WidgetScheme {
    val palette = if (isDark) DarkCoursePalette else LightCoursePalette
    // "跟随系统" 主题 → Material You 动态取色 (API 31+), 低版本降级 Default
    val s = if (themeKey == ThemePresets.KEY_SYSTEM && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val dyn = if (isDark) androidx.compose.material3.dynamicDarkColorScheme(context)
                  else androidx.compose.material3.dynamicLightColorScheme(context)
        WakeUpColorScheme(
            primary = dyn.primary, onPrimary = dyn.onPrimary,
            primaryContainer = dyn.primaryContainer, onPrimaryContainer = dyn.onPrimaryContainer,
            secondary = dyn.secondary, onSecondary = dyn.onSecondary,
            secondaryContainer = dyn.secondaryContainer, onSecondaryContainer = dyn.onSecondaryContainer,
            tertiary = dyn.tertiary, onTertiary = dyn.onTertiary,
            tertiaryContainer = dyn.tertiaryContainer, onTertiaryContainer = dyn.onTertiaryContainer,
            background = dyn.background, onBackground = dyn.onBackground,
            surface = dyn.surface, onSurface = dyn.onSurface,
            surfaceVariant = dyn.surfaceVariant, onSurfaceVariant = dyn.onSurfaceVariant,
            surfaceContainerLowest = dyn.surfaceContainerLowest, surfaceContainerLow = dyn.surfaceContainerLow,
            surfaceContainer = dyn.surfaceContainer, surfaceContainerHigh = dyn.surfaceContainerHigh,
            surfaceContainerHighest = dyn.surfaceContainerHighest,
            outline = dyn.outline, outlineVariant = dyn.outlineVariant, scrim = dyn.scrim,
            error = dyn.error, onError = dyn.onError, errorContainer = dyn.errorContainer, onErrorContainer = dyn.onErrorContainer
        )
    } else {
        val preset = ThemePresets.byKey(themeKey)
        if (isDark) preset.dark else preset.light
    }
    return WidgetScheme(
        bg = s.surface,
        surface = s.surface,
        primary = s.primary,
        primaryContainer = s.primaryContainer,
        onPrimaryContainer = s.onPrimaryContainer,
        onSurface = s.onSurface,
        onSurfaceVariant = s.onSurfaceVariant,
        surfaceContainer = s.surfaceContainer,
        surfaceVariant = s.surfaceVariant,
        isDark = isDark,
        coursePrimary = palette.primary,
        courseSecondary = palette.secondary,
        courseTertiary = palette.tertiary,
        courseEnglish = palette.english,
        courseMilitary = palette.military,
        coursePhysics = palette.physics,
        courseHistory = palette.history,
        coursePsychology = palette.psychology,
        coursePractice = palette.practice
    )
}

// ═══════════════════════════════════════════════════════
// Multi-day widget data
// ═══════════════════════════════════════════════════════

/** 单天数据 */
data class DayData(
    val date: LocalDate,
    val dayOfWeek: Int,
    val courses: List<CourseEntity>,
    val timeJson: String
) {
    val dayLabel: String get() = DateUtils.shortDate(date)
    val dayName: String get() = DateUtils.localizedDay(dayOfWeek, com.lingion.sleepy.SleepyApp.get())
    val isToday: Boolean get() = date == LocalDate.now()
    val isTomorrow: Boolean get() = date == LocalDate.now().plusDays(1)
}

/** 周视图数据 */
data class WeekData(
    val days: List<DayData>,
    val hasTable: Boolean,
    val isDark: Boolean = false,
    val themeKey: String = ThemePresets.KEY_DEFAULT,
    val displayMode: String = "node",
    val showDate: Boolean = false,
    val visibleDays: Set<Int> = (1..7).toSet()
)

/** 两天视图数据 */
data class TwoDayData(
    val days: List<DayData>,
    val hasTable: Boolean,
    val isDark: Boolean = false,
    val themeKey: String = ThemePresets.KEY_DEFAULT
)
