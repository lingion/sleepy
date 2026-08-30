package com.lingion.sleepy.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 完整 Material You (M3) 调色板 — 基于 switchable.html 的色变量
 *
 * Surface container 层级体系 (从低到高):
 *   surface-dim → surface → surface-bright →
 *   surface-container-lowest → surface-container-low → surface-container →
 *   surface-container-high → surface-container-highest
 *
 * Container 角色:
 *   primary-container / secondary-container / tertiary-container / error-container
 */
data class WakeUpColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,

    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,

    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,

    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,

    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,

    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color
)

val LightScheme = WakeUpColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),

    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),

    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),

    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),

    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

val DarkScheme = WakeUpColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),

    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),

    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),

    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),

    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

/**
 * 课程色明暗探针调色板（决策 D5-死代码清理）
 * 原有 10 个命名色（secondary/tertiary/surface/english/… 等 9 个）全库零读取，已删；
 * 仅保留 primary —— CourseColorUtil.isPaletteDark 读它的亮度判定明暗模式。
 * 课程实际配色走 CourseColorUtil 黄金角 HSL（groupId 撒色），不走本调色板。
 */
data class CoursePalette(
    /** 明暗探针用（亮=0xFFEADDFF / 暗=0xFF4F378B），勿用于课程底色 */
    val primary: Color
)

val LightCoursePalette = CoursePalette(
    primary = Color(0xFFEADDFF)       // primary-container
)

val DarkCoursePalette = CoursePalette(
    primary = Color(0xFF4F378B)
)

val LocalWakeUpColors = staticCompositionLocalOf { LightScheme }
val LocalCoursePalette = staticCompositionLocalOf { LightCoursePalette }

/**
 * Material You 字体系统 — 1:1 官方 M3 baseline type scale
 * (m3.material.io/styles/typography/type-scale-tokens;
 *  同源: material-3-skill references/typography-and-shape.md Baseline Type Scale)
 * 全 15 档 size/lineHeight/weight/tracking 逐项对官方值,不再沿用 switchable.html 旧值。
 */
val SleepyTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
)

/**
 * Material You 形状系统 — 1:1 官方 M3 shape tokens
 * (material-web tokens/_md-sys-shape: none=0, xs=4, sm=8, md=12, lg=16, xl=28)
 * Compose Shapes 无 20dp/28dp 档位名,large=16 覆盖 FAB/导航;
 * extraLarge 用官方 28(dialog/bottom sheet),此前 24 是旧 switchable 值。
 */
val SleepyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 扩展字号 — switchable.html 的额外尺寸 (9px/10px/13px/15px)
 *
 * 改成函数返回 TextStyle 是为了让调用方 .copy() 时不污染共享 val：
 * 直接 `SleepyTextStyle.micro` 是 `val`，copy 出来的还是引用同一个对象；
 * 函数返回则每次新建，copy 永远是独立的实例。
 */
object SleepyTextStyle {
    fun micro() = TextStyle(fontSize = 9.sp, lineHeight = 11.sp)
    fun smallMeta() = TextStyle(fontSize = 10.sp, lineHeight = 14.sp)
    fun dayLabel() = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    fun sectionHead() = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
}

/** 全局访问入口 */
object SleepyTheme {
    val colors: WakeUpColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalWakeUpColors.current

    val palette: CoursePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalCoursePalette.current

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = SleepyShapes

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = SleepyTypography

    /** 语义 alpha 档位 — 替代散落的野值,全 app 只许这几个 */
    object Alpha {
        /** 高内容强调(时间轴文字/主容器上副文字) */
        const val highContent = 0.8f

        /** 微弱边界(divider/卡片描边) */
        const val hairline = 0.3f

        /** 着色背景(选中态底色/色块背景) */
        const val tinted = 0.12f

        /** 未选中态强调减弱(按钮/文字提示) */
        const val inactive = 0.6f
    }

    /** 统一输入框形状 — 全 app 输入框唯一档位(此前 large/medium 混用) */
    val fieldShape: CornerBasedShape
        get() = SleepyShapes.medium

    /** 统一按钮档位 — 此前 40~54dp 六种高度混布, 收敛为两档:
     *  常规动作 48dp / 页面主 CTA 56dp。形状统一 large。 */
    object Buttons {
        val regularHeight = 48.dp
        val ctaHeight = 56.dp
        val shape: CornerBasedShape
            get() = SleepyShapes.large
    }

    /** 统一输入框配色 — 全 app 输入框唯一入口, 各屏禁止手搓 TextFieldDefaults.colors
     *  filled 色块风格 (2026-08-25 用户指令: 全 app 统一色块, 禁描线):
     *    组件换 TextField (原 OutlinedTextField), 底色 surfaceContainerHighest 色块,
     *    指示线透明化 → 无任何描线。
     *  disabled 系列与正常态同色: 点击穿透式字段(TimePickerField/下拉)用 enabled=false
     *  挡键盘, 但视觉上必须和普通字段一模一样, 不能显灰。 */
    @Composable
    fun fieldColors(): TextFieldColors {
        val c = colors
        return TextFieldDefaults.colors(
            focusedTextColor = c.onSurface,
            unfocusedTextColor = c.onSurface,
            disabledTextColor = c.onSurface,
            focusedLabelColor = c.primary,
            unfocusedLabelColor = c.onSurfaceVariant,
            disabledLabelColor = c.onSurfaceVariant,
            focusedPlaceholderColor = c.onSurfaceVariant,
            unfocusedPlaceholderColor = c.onSurfaceVariant,
            disabledPlaceholderColor = c.onSurfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = c.primary,
            focusedContainerColor = c.surfaceContainerHighest,
            unfocusedContainerColor = c.surfaceContainerHighest,
            disabledContainerColor = c.surfaceContainerHighest,
            disabledTrailingIconColor = c.onSurfaceVariant,
            disabledLeadingIconColor = c.onSurfaceVariant
        )
    }
}

@Composable
fun SleepyThemeProvider(
    darkTheme: Boolean = false,
    themeKey: String = ThemePresets.KEY_DEFAULT,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // "跟随系统" 走 Material You 动态取色（API 31+）；低版本降级到默认。
    // 其他 5 套用预设的 light/dark scheme。
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val preset = if (themeKey == ThemePresets.KEY_SYSTEM && dynamicAvailable) {
        null  // 标记走 dynamic 分支
    } else {
        ThemePresets.byKey(themeKey)
    }

    // 合并两个分支（preset vs dynamic）到同一个 content() 调用位置，
    //   防止 Compose 因 if/else 树结构变化而丢失 AppRoot 的 remember 状态。
    //   之前 preset==null 走 early return → content() 在不同树位置 → 切换时状态丢失。
    val (wakeColors, palette, m3Scheme) = if (preset == null) {
        // dynamic 取色 — API 31+ Material You (preset==null 仅在 dynamicAvailable(S/31)+ 时成立,
        // lint 需要显式版本守卫才能识别 dynamicDarkColorScheme/dynamicLightColorScheme 的 API 31 要求)
        val m3Dynamic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            // 理论不可达: preset==null 已含 S 守卫; 防御性回退默认 scheme
            if (darkTheme) darkColorScheme() else lightColorScheme()
        }
        // 用 dynamic scheme 的值构造 WakeUpColorScheme（课程色退回默认）
        val wc = WakeUpColorScheme(
            primary = m3Dynamic.primary,
            onPrimary = m3Dynamic.onPrimary,
            primaryContainer = m3Dynamic.primaryContainer,
            onPrimaryContainer = m3Dynamic.onPrimaryContainer,
            secondary = m3Dynamic.secondary,
            onSecondary = m3Dynamic.onSecondary,
            secondaryContainer = m3Dynamic.secondaryContainer,
            onSecondaryContainer = m3Dynamic.onSecondaryContainer,
            tertiary = m3Dynamic.tertiary,
            onTertiary = m3Dynamic.onTertiary,
            tertiaryContainer = m3Dynamic.tertiaryContainer,
            onTertiaryContainer = m3Dynamic.onTertiaryContainer,
            background = m3Dynamic.background,
            onBackground = m3Dynamic.onBackground,
            surface = m3Dynamic.surface,
            onSurface = m3Dynamic.onSurface,
            surfaceVariant = m3Dynamic.surfaceVariant,
            onSurfaceVariant = m3Dynamic.onSurfaceVariant,
            surfaceContainerLowest = m3Dynamic.surfaceContainerLowest,
            surfaceContainerLow = m3Dynamic.surfaceContainerLow,
            surfaceContainer = m3Dynamic.surfaceContainer,
            surfaceContainerHigh = m3Dynamic.surfaceContainerHigh,
            surfaceContainerHighest = m3Dynamic.surfaceContainerHighest,
            outline = m3Dynamic.outline,
            outlineVariant = m3Dynamic.outlineVariant,
            scrim = m3Dynamic.scrim,
            error = m3Dynamic.error,
            onError = m3Dynamic.onError,
            errorContainer = m3Dynamic.errorContainer,
            onErrorContainer = m3Dynamic.onErrorContainer
        )
        Triple(wc, if (darkTheme) DarkCoursePalette else LightCoursePalette, m3Dynamic)
    } else {
        val wc = if (darkTheme) preset.dark else preset.light
        val m3 = if (darkTheme) {
            darkColorScheme(
                primary = wc.primary, onPrimary = wc.onPrimary, primaryContainer = wc.primaryContainer, onPrimaryContainer = wc.onPrimaryContainer,
                secondary = wc.secondary, onSecondary = wc.onSecondary, secondaryContainer = wc.secondaryContainer, onSecondaryContainer = wc.onSecondaryContainer,
                tertiary = wc.tertiary, onTertiary = wc.onTertiary, tertiaryContainer = wc.tertiaryContainer, onTertiaryContainer = wc.onTertiaryContainer,
                background = wc.background, onBackground = wc.onBackground, surface = wc.surface, onSurface = wc.onSurface,
                surfaceVariant = wc.surfaceVariant, onSurfaceVariant = wc.onSurfaceVariant,
                surfaceContainerLowest = wc.surfaceContainerLowest, surfaceContainerLow = wc.surfaceContainerLow,
                surfaceContainer = wc.surfaceContainer, surfaceContainerHigh = wc.surfaceContainerHigh, surfaceContainerHighest = wc.surfaceContainerHighest,
                outline = wc.outline, outlineVariant = wc.outlineVariant, scrim = wc.scrim,
                error = wc.error, onError = wc.onError, errorContainer = wc.errorContainer, onErrorContainer = wc.onErrorContainer
            )
        } else {
            lightColorScheme(
                primary = wc.primary, onPrimary = wc.onPrimary, primaryContainer = wc.primaryContainer, onPrimaryContainer = wc.onPrimaryContainer,
                secondary = wc.secondary, onSecondary = wc.onSecondary, secondaryContainer = wc.secondaryContainer, onSecondaryContainer = wc.onSecondaryContainer,
                tertiary = wc.tertiary, onTertiary = wc.onTertiary, tertiaryContainer = wc.tertiaryContainer, onTertiaryContainer = wc.onTertiaryContainer,
                background = wc.background, onBackground = wc.onBackground, surface = wc.surface, onSurface = wc.onSurface,
                surfaceVariant = wc.surfaceVariant, onSurfaceVariant = wc.onSurfaceVariant,
                surfaceContainerLowest = wc.surfaceContainerLowest, surfaceContainerLow = wc.surfaceContainerLow,
                surfaceContainer = wc.surfaceContainer, surfaceContainerHigh = wc.surfaceContainerHigh, surfaceContainerHighest = wc.surfaceContainerHighest,
                outline = wc.outline, outlineVariant = wc.outlineVariant, scrim = wc.scrim,
                error = wc.error, onError = wc.onError, errorContainer = wc.errorContainer, onErrorContainer = wc.onErrorContainer
            )
        }
        Triple(wc, if (darkTheme) DarkCoursePalette else LightCoursePalette, m3)
    }

    CompositionLocalProvider(
        LocalWakeUpColors provides wakeColors,
        LocalCoursePalette provides palette
    ) {
        MaterialTheme(colorScheme = m3Scheme) {
            content()
        }
    }
}