package com.lingion.sleepy.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 自定义主题派生引擎纯 JVM 测试。
 *
 * 契约:
 *  1. 任意输入(黑/白/纯饱和/纯灰边界)不崩溃,scheme 全角色非 NaN;
 *  2. error 族 = 模板原值(语义色固定,不派生);
 *  3. 深浅两套 primaryContainer 明度关系:dark 的 primaryContainer 必须暗于
 *     light 的 primaryContainer(M3 container 语义 — light 是"浅底容器",
 *     dark 是"深底容器");
 *  4. 派生 primary 色相跟随种子色相(色相旋转模板法的核心承诺);
 *  5. on* 文字色按底色亮度自适应,保证可读(深底浅字/浅底深字)。
 */
class CustomSchemeDeriverTest {

    private val theme = CustomThemeFake()

    /** 测试用 fake — 真类型在 data 包,这里用同形状构造避免跨包依赖耦合测试 */
    private fun CustomThemeFake(
        primary: String = "#7C4DFF",
        secondary: String = "#546E7A",
        tertiary: String = "#EF6C00",
        surfaceHue: Double = 265.0,
        surfaceChroma: Double = 8.0
    ): com.lingion.sleepy.data.CustomTheme = com.lingion.sleepy.data.CustomTheme(
        id = "test", name = "test", primary = primary, secondary = secondary,
        tertiary = tertiary, surfaceHue = surfaceHue, surfaceChroma = surfaceChroma,
        createdAt = 0L
    )

    // ── 契约 1: 边界不崩溃,全角色非 NaN ──

    private fun assertSchemeHealthy(scheme: WakeUpColorScheme, label: String) {
        val fields = listOf(
            scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer,
            scheme.secondary, scheme.onSecondary, scheme.secondaryContainer, scheme.onSecondaryContainer,
            scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer,
            scheme.background, scheme.onBackground, scheme.surface, scheme.onSurface,
            scheme.surfaceVariant, scheme.onSurfaceVariant,
            scheme.surfaceContainerLowest, scheme.surfaceContainerLow, scheme.surfaceContainer,
            scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
            scheme.outline, scheme.outlineVariant, scheme.scrim,
            scheme.error, scheme.onError, scheme.errorContainer, scheme.onErrorContainer
        )
        fields.forEach { c ->
            assertFalse("$label: color must not be NaN/undefined (got $c)", c.red.isNaN() || c.green.isNaN() || c.blue.isNaN())
            assertTrue("$label: channel out of range (got $c)", c.red in 0f..1f && c.green in 0f..1f && c.blue in 0f..1f)
        }
    }

    @Test
    fun derive_never_crashes_on_extreme_seed_colors() {
        val extremeSeeds = listOf(
            "#000000", "#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#7C4DFF",
            "garbage", "", "#FFF", "#12"
        )
        extremeSeeds.forEach { seed ->
            val t = CustomThemeFake(primary = seed, secondary = seed, tertiary = seed)
            assertSchemeHealthy(CustomSchemeDeriver.derive(t, isDark = false), "light/$seed")
            assertSchemeHealthy(CustomSchemeDeriver.derive(t, isDark = true), "dark/$seed")
        }
    }

    @Test
    fun derive_never_crashes_on_extreme_surface_tendency() {
        val tendencies = listOf(
            0.0 to 0.0,     // 纯灰无彩
            360.0 to 100.0, // 超界色相 + 高饱和
            -40.0 to -5.0   // 负值
        )
        tendencies.forEach { (hue, chroma) ->
            val t = CustomThemeFake(surfaceHue = hue, surfaceChroma = chroma)
            assertSchemeHealthy(CustomSchemeDeriver.derive(t, isDark = false), "light/hue=$hue")
            assertSchemeHealthy(CustomSchemeDeriver.derive(t, isDark = true), "dark/hue=$hue")
        }
    }

    // ── 契约 2: error 族照抄模板 ──

    @Test
    fun error_family_copies_template_exactly() {
        val light = CustomSchemeDeriver.derive(theme, isDark = false)
        val dark = CustomSchemeDeriver.derive(theme, isDark = true)
        assertEquals(LightScheme.error, light.error)
        assertEquals(LightScheme.onError, light.onError)
        assertEquals(LightScheme.errorContainer, light.errorContainer)
        assertEquals(LightScheme.onErrorContainer, light.onErrorContainer)
        assertEquals(DarkScheme.error, dark.error)
        assertEquals(DarkScheme.onError, dark.onError)
        assertEquals(DarkScheme.errorContainer, dark.errorContainer)
        assertEquals(DarkScheme.onErrorContainer, dark.onErrorContainer)
    }

    @Test
    fun scrim_copies_template_exactly() {
        assertEquals(LightScheme.scrim, CustomSchemeDeriver.derive(theme, isDark = false).scrim)
        assertEquals(DarkScheme.scrim, CustomSchemeDeriver.derive(theme, isDark = true).scrim)
    }

    // ── 契约 3: 深浅两套 primaryContainer 明度关系 ──

    @Test
    fun dark_primaryContainer_is_darker_than_light() {
        val light = CustomSchemeDeriver.derive(theme, isDark = false)
        val dark = CustomSchemeDeriver.derive(theme, isDark = true)
        assertTrue(
            "dark primaryContainer (${dark.primaryContainer}) must be darker than light (${light.primaryContainer})",
            dark.primaryContainer.luminanceCompat() < light.primaryContainer.luminanceCompat()
        )
        assertTrue(
            "dark surface (${dark.surface}) must be darker than light (${light.surface})",
            dark.surface.luminanceCompat() < light.surface.luminanceCompat()
        )
    }

    // ── 契约 4: primary 色相跟随种子 ──

    @Test
    fun primary_hue_follows_seed_hue() {
        // 红种子 → light primary 应仍是红色系(色相 ≈ 0°)
        val redTheme = CustomThemeFake(primary = "#E53935")
        val redPrimary = CustomSchemeDeriver.derive(redTheme, isDark = false).primary
        val hue = hueOf(redPrimary)
        assertTrue("red seed should stay red-ish, got hue=$hue", hue < 30f || hue > 330f)

        // 绿种子 → 色相应接近 120°
        val greenPrimary = CustomSchemeDeriver.derive(CustomThemeFake(primary = "#43A047"), isDark = false).primary
        val gHue = hueOf(greenPrimary)
        assertTrue("green seed should stay green-ish, got hue=$gHue", gHue in 90f..150f)
    }

    // ── 契约 5: on 色按派生底色亮度自适应 ──

    @Test
    fun onPrimary_contrasts_with_derived_primary() {
        // 明度结构照抄模板(设计定稿):light primary 是深底 → 白字;
        // dark primary 是浅底 → 深字。这是可读性的实际保证。
        val light = CustomSchemeDeriver.derive(theme, isDark = false)
        assertTrue(
            "light primary (lum=${light.primary.luminanceCompat()}) is dark -> onPrimary must be light, got ${light.onPrimary}",
            light.primary.luminanceCompat() < 0.5f && light.onPrimary.luminanceCompat() > 0.5f
        )
        val dark = CustomSchemeDeriver.derive(theme, isDark = true)
        assertTrue(
            "dark primary (lum=${dark.primary.luminanceCompat()}) is light -> onPrimary must be dark, got ${dark.onPrimary}",
            dark.primary.luminanceCompat() > 0.5f && dark.onPrimary.luminanceCompat() < 0.5f
        )
    }

    @Test
    fun achromatic_seed_derives_template_luminance_structure() {
        // 设计契约:种子只贡献色相,明度/饱和度结构照抄模板 —
        // 黑/白种子(S=0)色相回退模板相,派生 primary 明度应与模板 primary 明度一致
        val forWhite = CustomSchemeDeriver.derive(CustomThemeFake(primary = "#FFFFFF"), isDark = false)
        val forBlack = CustomSchemeDeriver.derive(CustomThemeFake(primary = "#000000"), isDark = false)
        val tmplV = LightScheme.primary.luminanceCompat()
        assertTrue(
            "achromatic seeds must preserve template luminance (${forWhite.primary} vs $tmplV)",
            abs(forWhite.primary.luminanceCompat() - tmplV) < 0.2f &&
                abs(forBlack.primary.luminanceCompat() - tmplV) < 0.2f
        )
    }

    @Test
    fun surface_family_uses_surface_tendency_not_primary_seed() {
        // 表面族色相应取 surfaceHue,不取 primary 种子色相
        val t = CustomThemeFake(primary = "#E53935", surfaceHue = 120.0, surfaceChroma = 10.0)
        val surface = CustomSchemeDeriver.derive(t, isDark = false).surface
        // 中低 chroma 下色相检测可能偏移,给宽容差;关键是表面不该是红色系
        val h = hueOf(surface)
        assertFalse("surface must not inherit red primary hue, got hue=$h", h < 30f || h > 330f)
    }

    @Test
    fun both_modes_share_seed_theme_but_differ_in_brightness() {
        val light = CustomSchemeDeriver.derive(theme, isDark = false)
        val dark = CustomSchemeDeriver.derive(theme, isDark = true)
        assertFalse("light and dark schemes must differ", light == dark)
        assertTrue(dark.background.luminanceCompat() < 0.5f)
        assertTrue(light.background.luminanceCompat() > 0.5f)
    }

    // ── 工具: 纯 Kotlin 色相探针(与 CourseColorUtilTest 同思路) ──

    private fun hueOf(c: Color): Float {
        val r = c.red; val g = c.green; val b = c.blue
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val d = max - min
        if (d < 1e-6f) return 0f
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return if (h < 0) h + 360f else h
    }

    private fun Color.luminanceCompat(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
}
