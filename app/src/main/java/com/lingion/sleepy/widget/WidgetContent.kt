package com.lingion.sleepy.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.theme.ThemePresets
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
    val themeKey: String = ThemePresets.KEY_DEFAULT,
    /** 学期状态（v1.0.37）: 学期外时 Today 渲染状态文案不渲染课程 */
    val semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE
) {
    val dayName: String get() = DateUtils.localizedDay(date.dayOfWeek.value, com.lingion.sleepy.SleepyApp.get())
    val dateLabel: String get() = "${date.monthValue}/${date.dayOfMonth}"
}

/**
 * 4 元组：背景 / 主题强调色 / 正文色 / 次要色
 * 跟 app M3 scheme 派生方式相同：surface / primary / onSurface / onSurfaceVariant
 *
 * 死代码清理: 原 coursePrimary…coursePractice 9 个课程色字段赋值后从未被渲染使用
 * (课程底色实际走 CourseColorUtil 黄金角 HSL), 已随 CoursePalette 死属性一并删除。
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
    val isDark: Boolean = false
)

/**
 * 按 themeKey + isDark 派生小组件配色。
 *
 * themeKey == "system" 时走 Material You 动态取色(dynamicLightColorScheme / dynamicDarkColorScheme),
 *   与 [com.lingion.sleepy.ui.theme.SleepyThemeProvider] 的处理对齐 — 之前 widget 把 "system"
 *   当未知 key → ThemePresets.byKey 返回 Default(紫色) → 小组件永远紫色, 不跟随系统壁纸取色。
 */
internal fun resolveSchemePublic(context: Context, themeKey: String, isDark: Boolean): WidgetScheme {
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
        isDark = isDark
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
    // displayMode 死字段已删（renderer 各自直读 AppPrefs.getDisplayMode, 传入字段从未被消费）
    val showDate: Boolean = false,
    val visibleDays: Set<Int> = (1..7).toSet(),
    /** 学期状态（v1.0.37）: 学期外时列头加状态行 */
    val semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE
)

/** 两天视图数据 */
data class TwoDayData(
    val days: List<DayData>,
    val hasTable: Boolean,
    val isDark: Boolean = false,
    val themeKey: String = ThemePresets.KEY_DEFAULT,
    /** 学期状态（v1.0.37）: 学期外时渲染状态文案不渲染课程 */
    val semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE
)
