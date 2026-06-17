package com.lingion.sleepy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
    surface = Color(0xFFFFFBFE),
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
    onPrimary = Color(0xFF2D165C),
    primaryContainer = Color(0xFF564092),
    onPrimaryContainer = Color(0xFFF2E8FF),

    secondary = Color(0xFFD8CEE8),
    onSecondary = Color(0xFF2C2638),
    secondaryContainer = Color(0xFF524B61),
    onSecondaryContainer = Color(0xFFF0E7FF),

    tertiary = Color(0xFFF4C3D2),
    onTertiary = Color(0xFF472230),
    tertiaryContainer = Color(0xFF6E4452),
    onTertiaryContainer = Color(0xFFFFEAF1),

    background = Color(0xFF141218),
    onBackground = Color(0xFFF4EEF4),
    surface = Color(0xFF161419),
    onSurface = Color(0xFFF4EEF4),
    surfaceVariant = Color(0xFF4F4A55),
    onSurfaceVariant = Color(0xFFE4DCE8),
    surfaceContainerLowest = Color(0xFF100E13),
    surfaceContainerLow = Color(0xFF1D1A22),
    surfaceContainer = Color(0xFF25212B),
    surfaceContainerHigh = Color(0xFF302C36),
    surfaceContainerHighest = Color(0xFF3B3641),

    outline = Color(0xFFA9A2AE),
    outlineVariant = Color(0xFF5C5661),
    scrim = Color(0xFF000000),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * Material You 课程主题色（卡片背景）
 * 来源: switchable.html .card.primary/.secondary/.tertiary/.surface/.english/.military/...
 */
data class CoursePalette(
    val primary: Color,        // 高数 / 主课
    val secondary: Color,      // 英语 / 外语
    val tertiary: Color,       // 史纲 / 思政
    val surface: Color,        // 体育 / 通用
    val english: Color,
    val military: Color,
    val physics: Color,
    val history: Color,
    val psychology: Color,
    val practice: Color
)

val LightCoursePalette = CoursePalette(
    primary = Color(0xFFEADDFF),       // primary-container
    secondary = Color(0xFFE8DEF8),     // secondary-container
    tertiary = Color(0xFFFFD8E4),      // tertiary-container
    surface = Color(0xFFF3EDF7),       // surface-container
    english = Color(0xFFD8F2FF),
    military = Color(0xFFE7F3DC),
    physics = Color(0xFFFFE7C7),
    history = Color(0xFFF7D9D9),
    psychology = Color(0xFFE6DDFB),
    practice = Color(0xFFD7F0E8)
)

val DarkCoursePalette = CoursePalette(
    primary = Color(0xFF4F378B),
    secondary = Color(0xFF4A4458),
    tertiary = Color(0xFF633B48),
    surface = Color(0xFF211F26),
    english = Color(0xFF1E3A4D),
    military = Color(0xFF2E3F26),
    physics = Color(0xFF4D3A1E),
    history = Color(0xFF4D2828),
    psychology = Color(0xFF352B4D),
    practice = Color(0xFF1E3D32)
)

val LocalWakeUpColors = staticCompositionLocalOf { LightScheme }
val LocalCoursePalette = staticCompositionLocalOf { LightCoursePalette }

/**
 * Material You 字体系统 — 完整 M3 type scale，对应 switchable.html 字号
 * (11/12/13/15/22, line-height: 13/16/18/22/28)
 */
val SleepyTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Medium
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
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
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
)

/**
 * Material You 形状系统 — 对应 switchable.html 的圆角 (16/18/20/24)
 * - card: 16dp
 * - panel: 18-20dp
 * - bottom sheet: 24-28dp
 * - segment button: 12dp
 * - pill: full
 */
val SleepyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * 扩展字号 — switchable.html 的额外尺寸 (9px/10px/13px/15px)
 */
object SleepyTextStyle {
    val micro = TextStyle(fontSize = 9.sp, lineHeight = 11.sp)
    val smallMeta = TextStyle(fontSize = 10.sp, lineHeight = 14.sp)
    val dayLabel = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val sectionHead = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
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
}

@Composable
fun SleepyThemeProvider(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkScheme else LightScheme
    val palette = if (darkTheme) DarkCoursePalette else LightCoursePalette

    CompositionLocalProvider(
        LocalWakeUpColors provides colors,
        LocalCoursePalette provides palette
    ) {
        content()
    }
}