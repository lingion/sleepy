package com.lingion.sleepy.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题色预设 — 5 套静态 + 1 套"跟随系统"（由 [com.lingion.sleepy.ui.theme.SleepyThemeProvider] 特殊处理）
 *
 * 颜色取自 Material 3 官方 tonal palette 推荐值。
 * 每套主题包含 light/dark 一对 [WakeUpColorScheme]，
 * 课程色 [CoursePalette] 5 套共用同一份（保持课程视觉一致）。
 */
data class ThemePreset(
    val key: String,
    val displayName: String,
    val light: WakeUpColorScheme,
    val dark: WakeUpColorScheme
)

object ThemePresets {

    const val KEY_DEFAULT = "default"
    const val KEY_SPRING = "spring"
    const val KEY_OCEAN = "ocean"
    const val KEY_PEACH = "peach"
    const val KEY_SLATE = "slate"
    const val KEY_SYSTEM = "system"

    /** 默认淡紫 — 保留 v1.0.6 行为 */
    val Default = ThemePreset(
        key = KEY_DEFAULT,
        displayName = "默认淡紫",
        light = LightScheme,
        dark = DarkScheme
    )

    /** 春绿 — 抹茶/植物 */
    val SpringLight = WakeUpColorScheme(
        primary = Color(0xFF386A20),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB7F397),
        onPrimaryContainer = Color(0xFF002200),

        secondary = Color(0xFF55624C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD9E7CC),
        onSecondaryContainer = Color(0xFF131F0F),

        tertiary = Color(0xFF386666),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFBCEBEB),
        onTertiaryContainer = Color(0xFF002020),

        background = Color(0xFFFCFDF6),
        onBackground = Color(0xFF1A1C18),
        surface = Color(0xFFFCFDF6),
        onSurface = Color(0xFF1A1C18),
        surfaceVariant = Color(0xFFDFE4D7),
        onSurfaceVariant = Color(0xFF43483E),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6F7F0),
        surfaceContainer = Color(0xFFF0F1EA),
        surfaceContainerHigh = Color(0xFFEAEBE5),
        surfaceContainerHighest = Color(0xFFE4E5DF),

        outline = Color(0xFF74796D),
        outlineVariant = Color(0xFFC4C8BC),
        scrim = Color(0xFF000000),

        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
    val SpringDark = WakeUpColorScheme(
        primary = Color(0xFF9CD67D),
        onPrimary = Color(0xFF0A3900),
        primaryContainer = Color(0xFF1E5109),
        onPrimaryContainer = Color(0xFFB7F397),

        secondary = Color(0xFFBDCBB1),
        onSecondary = Color(0xFF273420),
        secondaryContainer = Color(0xFF3D4B35),
        onSecondaryContainer = Color(0xFFD9E7CC),

        tertiary = Color(0xFFA0CFCF),
        onTertiary = Color(0xFF003737),
        tertiaryContainer = Color(0xFF1E4E4E),
        onTertiaryContainer = Color(0xFFBCEBEB),

        background = Color(0xFF1A1C18),
        onBackground = Color(0xFFE3E3DC),
        surface = Color(0xFF1A1C18),
        onSurface = Color(0xFFE3E3DC),
        surfaceVariant = Color(0xFF43483E),
        onSurfaceVariant = Color(0xFFC4C8BC),
        surfaceContainerLowest = Color(0xFF0D0F0C),
        surfaceContainerLow = Color(0xFF22241F),
        surfaceContainer = Color(0xFF262924),
        surfaceContainerHigh = Color(0xFF31332E),
        surfaceContainerHighest = Color(0xFF3C3E39),

        outline = Color(0xFF8E9387),
        outlineVariant = Color(0xFF43483E),
        scrim = Color(0xFF000000),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
    val Spring = ThemePreset(
        key = KEY_SPRING,
        displayName = "春绿",
        light = SpringLight,
        dark = SpringDark
    )

    /** 海蓝 — 沉静冷调 */
    val OceanLight = WakeUpColorScheme(
        primary = Color(0xFF0061A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1E4FF),
        onPrimaryContainer = Color(0xFF001D36),

        secondary = Color(0xFF535F70),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD7E3F7),
        onSecondaryContainer = Color(0xFF101C2B),

        tertiary = Color(0xFF6B5778),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF2DAFF),
        onTertiaryContainer = Color(0xFF251431),

        background = Color(0xFFFDFCFF),
        onBackground = Color(0xFF1A1C1E),
        surface = Color(0xFFFDFCFF),
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFDFE2EB),
        onSurfaceVariant = Color(0xFF43474E),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F8FA),
        surfaceContainer = Color(0xFFEEF0F4),
        surfaceContainerHigh = Color(0xFFE8E9EE),
        surfaceContainerHighest = Color(0xFFE2E3E8),

        outline = Color(0xFF73777F),
        outlineVariant = Color(0xFFC3C7CF),
        scrim = Color(0xFF000000),

        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
    val OceanDark = WakeUpColorScheme(
        primary = Color(0xFF9ECAFF),
        onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF00497D),
        onPrimaryContainer = Color(0xFFD1E4FF),

        secondary = Color(0xFFBBC7DB),
        onSecondary = Color(0xFF253140),
        secondaryContainer = Color(0xFF3B4858),
        onSecondaryContainer = Color(0xFFD7E3F7),

        tertiary = Color(0xFFD6BEE4),
        onTertiary = Color(0xFF3B2948),
        tertiaryContainer = Color(0xFF523F5F),
        onTertiaryContainer = Color(0xFFF2DAFF),

        background = Color(0xFF1A1C1E),
        onBackground = Color(0xFFE3E2E6),
        surface = Color(0xFF1A1C1E),
        onSurface = Color(0xFFE3E2E6),
        surfaceVariant = Color(0xFF43474E),
        onSurfaceVariant = Color(0xFFC3C7CF),
        surfaceContainerLowest = Color(0xFF0D0F12),
        surfaceContainerLow = Color(0xFF222426),
        surfaceContainer = Color(0xFF26282C),
        surfaceContainerHigh = Color(0xFF313338),
        surfaceContainerHighest = Color(0xFF3C3E43),

        outline = Color(0xFF8D9199),
        outlineVariant = Color(0xFF43474E),
        scrim = Color(0xFF000000),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
    val Ocean = ThemePreset(
        key = KEY_OCEAN,
        displayName = "海蓝",
        light = OceanLight,
        dark = OceanDark
    )

    /** 蜜桃粉 — 暖橙 */
    val PeachLight = WakeUpColorScheme(
        primary = Color(0xFF9D4400),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBC8),
        onPrimaryContainer = Color(0xFF341000),

        secondary = Color(0xFF765848),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDBC8),
        onSecondaryContainer = Color(0xFF2B160A),

        tertiary = Color(0xFF636032),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE9E4AA),
        onTertiaryContainer = Color(0xFF1E1C00),

        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF201A17),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF201A17),
        surfaceVariant = Color(0xFFF4DED4),
        onSurfaceVariant = Color(0xFF52443D),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF4ED),
        surfaceContainer = Color(0xFFFCEEE5),
        surfaceContainerHigh = Color(0xFFF6E8DE),
        surfaceContainerHighest = Color(0xFFF0E2D7),

        outline = Color(0xFF85746C),
        outlineVariant = Color(0xFFD7C2B8),
        scrim = Color(0xFF000000),

        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
    val PeachDark = WakeUpColorScheme(
        primary = Color(0xFFFFB689),
        onPrimary = Color(0xFF552100),
        primaryContainer = Color(0xFF783200),
        onPrimaryContainer = Color(0xFFFFDBC8),

        secondary = Color(0xFFE6C0AB),
        onSecondary = Color(0xFF442B1D),
        secondaryContainer = Color(0xFF5D4132),
        onSecondaryContainer = Color(0xFFFFDBC8),

        tertiary = Color(0xFFCCC890),
        onTertiary = Color(0xFF333208),
        tertiaryContainer = Color(0xFF4A481D),
        onTertiaryContainer = Color(0xFFE9E4AA),

        background = Color(0xFF201A17),
        onBackground = Color(0xFFECE0DA),
        surface = Color(0xFF201A17),
        onSurface = Color(0xFFECE0DA),
        surfaceVariant = Color(0xFF52443D),
        onSurfaceVariant = Color(0xFFD7C2B8),
        surfaceContainerLowest = Color(0xFF1A120E),
        surfaceContainerLow = Color(0xFF28221E),
        surfaceContainer = Color(0xFF2D2622),
        surfaceContainerHigh = Color(0xFF38312D),
        surfaceContainerHighest = Color(0xFF433C37),

        outline = Color(0xFFA08D84),
        outlineVariant = Color(0xFF52443D),
        scrim = Color(0xFF000000),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
    val Peach = ThemePreset(
        key = KEY_PEACH,
        displayName = "蜜桃粉",
        light = PeachLight,
        dark = PeachDark
    )

    /** 石板灰 — 中性冷淡 */
    val SlateLight = WakeUpColorScheme(
        primary = Color(0xFF3F4945),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC3D2CD),
        onPrimaryContainer = Color(0xFF00201C),

        secondary = Color(0xFF4F635E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD2E7E0),
        onSecondaryContainer = Color(0xFF0B1D1A),

        tertiary = Color(0xFF3D6373),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC0E8FA),
        onTertiaryContainer = Color(0xFF001F29),

        background = Color(0xFFFAFDFB),
        onBackground = Color(0xFF191C1B),
        surface = Color(0xFFFAFDFB),
        onSurface = Color(0xFF191C1B),
        surfaceVariant = Color(0xFFDBE5E1),
        onSurfaceVariant = Color(0xFF3F4945),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F6F4),
        surfaceContainer = Color(0xFFEEF1EE),
        surfaceContainerHigh = Color(0xFFE8EBE9),
        surfaceContainerHighest = Color(0xFFE2E5E3),

        outline = Color(0xFF6F7975),
        outlineVariant = Color(0xFFBEC9C4),
        scrim = Color(0xFF000000),

        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
    val SlateDark = WakeUpColorScheme(
        primary = Color(0xFFA7C2BD),
        onPrimary = Color(0xFF0E2E29),
        primaryContainer = Color(0xFF25403C),
        onPrimaryContainer = Color(0xFFC3D2CD),

        secondary = Color(0xFFB6CBC4),
        onSecondary = Color(0xFF213530),
        secondaryContainer = Color(0xFF384B47),
        onSecondaryContainer = Color(0xFFD2E7E0),

        tertiary = Color(0xFFA4CCDE),
        onTertiary = Color(0xFF073542),
        tertiaryContainer = Color(0xFF234B5A),
        onTertiaryContainer = Color(0xFFC0E8FA),

        background = Color(0xFF191C1B),
        onBackground = Color(0xFFE0E3E0),
        surface = Color(0xFF191C1B),
        onSurface = Color(0xFFE0E3E0),
        surfaceVariant = Color(0xFF3F4945),
        onSurfaceVariant = Color(0xFFBEC9C4),
        surfaceContainerLowest = Color(0xFF0B0F0E),
        surfaceContainerLow = Color(0xFF212523),
        surfaceContainer = Color(0xFF252927),
        surfaceContainerHigh = Color(0xFF303432),
        surfaceContainerHighest = Color(0xFF3B3F3D),

        outline = Color(0xFF89938F),
        outlineVariant = Color(0xFF3F4945),
        scrim = Color(0xFF000000),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
    val Slate = ThemePreset(
        key = KEY_SLATE,
        displayName = "石板灰",
        light = SlateLight,
        dark = SlateDark
    )

    val all: List<ThemePreset> = listOf(Default, Spring, Ocean, Peach, Slate)

    fun byKey(key: String?): ThemePreset {
        if (key == null) return Default
        return all.firstOrNull { it.key == key } ?: Default
    }
}
