package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 契约测试：液态玻璃底栏在低版本 Android 设备与主题切换下的稳健性与保真度锁。
 *
 * 锁定目标:
 * 1. 主题实时响应: 禁止在 LiquidGlassNavigationBar 中直接使用 isSystemInDarkTheme()，
 *    必须通过 LocalDarkTheme / ColorScheme 派生 dark，防止会话内切深浅色时底栏卡死在旧主题。
 * 2. 低版本底座可见性: Dock 胶囊底座 (Layer 1) 必须无条件具有 .background() 保护，
 *    杜绝在 API < 33 (尤其是 API < 31) 设备上底座完全消失/透明仅剩滑块悬浮的故障。
 * 3. 老设备边缘放大拉伸: 在无硬件 AGSL 支持时，必须承载 DestinationTransform 几何变换，
 *    复刻 FLClash 在透镜边缘将图标向外扯大的非线性光学拉伸。
 * 4. 样式降级: LiquidGlassEffect 在不支持 RuntimeShader 时必须调用 LiquidGlassStyle.standard(style)
 *    将 hazeBlur 折算为 blur。
 */
class LiquidGlassDockFallbackContractTest {

    private fun loadSource(relPath: String): String =
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/"),
            File("src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            File(root, relPath).takeIf { it.isFile }?.readText()
        } ?: error("Unable to load $relPath")

    private val navBarSrc by lazy { loadSource("ui/component/liquidglass/LiquidGlassNavigationBar.kt") }
    private val effectSrc by lazy { loadSource("ui/component/liquidglass/LiquidGlassEffect.kt") }
    private val themeSrc by lazy { loadSource("ui/theme/Theme.kt") }

    @Test
    fun nav_bar_does_not_use_bare_isSystemInDarkTheme() {
        assertFalse(
            "LiquidGlassNavigationBar must NOT read isSystemInDarkTheme() directly — " +
                "Activity handles uiMode (configChanges=uiMode) so isSystemInDarkTheme() freezes.",
            Regex("""val\s+dark\s*=\s*isSystemInDarkTheme\(\)""").containsMatchIn(navBarSrc)
        )
        assertTrue(
            "LiquidGlassNavigationBar must resolve dark via LocalDarkTheme and ColorScheme luminance",
            navBarSrc.contains("LocalDarkTheme.current") && navBarSrc.contains("luminance()")
        )
        assertTrue(
            "LiquidGlassNavigationBar must derive dark via derivedStateOf so theme flips " +
                "invalidate only the shader/style consumers instead of freezing at init value",
            Regex("""val\s+dark\s+by\s+remember\([^)]*\)\s*\{[^{}]*derivedStateOf""").containsMatchIn(navBarSrc)
        )
    }

    @Test
    fun provider_supplies_LocalDarkTheme() {
        assertTrue(
            "Theme.kt must declare LocalDarkTheme as a subscribable compositionLocalOf — " +
                "staticCompositionLocalOf never invalidates readers, so the dock would ignore theme changes.",
            themeSrc.contains("val LocalDarkTheme = compositionLocalOf")
        )
        assertTrue(
            "SleepyThemeProvider must provide LocalDarkTheme",
            themeSrc.contains("LocalDarkTheme provides darkTheme")
        )
    }

    @Test
    fun layer1_dock_capsule_has_unconditional_background() {
        assertTrue(
            "Layer 1 must apply .background(colorScheme.surfaceContainer...) to prevent disappearing on older devices",
            navBarSrc.contains(".background(") && navBarSrc.contains("surfaceContainer")
        )
        assertTrue(
            "Layer 1 must apply .border(...) for capsule rim definition",
            navBarSrc.contains(".border(")
        )
    }

    @Test
    fun destination_transform_implements_edge_stretch_fallback() {
        assertTrue(
            "Must define DestinationTransform for per-item geometric transforms",
            navBarSrc.contains("data class DestinationTransform")
        )
        assertTrue(
            "computeInsideTransform must calculate non-linear edge stretch when shader is unavailable",
            navBarSrc.contains("fun computeInsideTransform") &&
                navBarSrc.contains("edgeStretch") &&
                navBarSrc.contains("centerMag")
        )
    }

    @Test
    fun effect_fallback_applies_standard_style_for_blur() {
        assertTrue(
            "LiquidGlassEffect must convert hazeBlur to blur via LiquidGlassStyle.standard on non-shader devices",
            effectSrc.contains("LiquidGlassStyle.standard(style)")
        )
    }
}
