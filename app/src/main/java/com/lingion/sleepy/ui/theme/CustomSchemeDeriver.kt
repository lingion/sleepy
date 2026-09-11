package com.lingion.sleepy.ui.theme

import androidx.compose.ui.graphics.Color
import com.lingion.sleepy.data.CustomTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 自定义主题派生引擎 — 色相旋转模板法(M3 色彩角色关系的轻量实现,纯 JVM 可测)。
 *
 * 算法:以默认淡紫模板([LightScheme]/[DarkScheme],Theme.kt 顶部)为结构基底 —
 *   1. 对每个"有色相"的模板角色,读出其 HSV 的 S/V(饱和度/明度)结构,
 *      把色相 H 替换为该角色族的种子色相 → 派生色。即模板决定"这个角色该多深
 *      多灰",用户种子只决定"往哪个色相走" — 与 M3 tonal palette 从种子色
 *      生成整组色调的思想一致,但不引入 material-color-utilities 依赖。
 *   2. 色相族分配:primary 族 ← 用户 primary;secondary 族 ← 用户 secondary;
 *      tertiary 族 ← 用户 tertiary。各族内全部角色(primary/primaryContainer/
 *      onPrimaryContainer/…)同族同色相,深浅由模板的角色间明度差保证。
 *   3. 表面族(background/surface/surfaceVariant/surfaceContainer 系/onSurfaceVariant/
 *      outline/outlineVariant/onBackground/onSurface)不取种子饱和度 — 用
 *      [CustomTheme.surfaceHue] + [CustomTheme.surfaceChroma](低 chroma 中性色,
 *      推荐 4-12),明度 V 照抄模板角色。深浅两套各自成立。
 *   4. error 族/scrim 是语义色,固定照抄模板不派生。
 *   5. onPrimary/onSecondary/onTertiary 等文字色按底色亮度自适应
 *      (luminance < 0.5 → 白字,否则黑字;思路同 CourseColorUtil.textColorOn,
 *      但不依赖主题 onSurface — 派生语境里底色是用户的,自适应更稳)。
 *
 * 纯 Kotlin HSV 实现,禁用 android.graphics.Color.colorToHSV/HSVToColor —
 * JVM 单测里那是 stub(全返 0),会静默产出错误 scheme(仓库无 Robolectric,
 * 本引擎必须纯 JVM 可测)。
 */
object CustomSchemeDeriver {

    /** 表面 chroma 限幅上限 — 超过后表面不再"中性",视觉变彩色背景 */
    private const val SURFACE_CHROMA_MAX = 48.0

    fun derive(theme: CustomTheme, isDark: Boolean): WakeUpColorScheme {
        val template = if (isDark) DarkScheme else LightScheme
        val primaryHue = hueOfSeed(theme.primary, fallback = template.primary)
        val secondaryHue = hueOfSeed(theme.secondary, fallback = template.secondary)
        val tertiaryHue = hueOfSeed(theme.tertiary, fallback = template.tertiary)
        val surfaceHue = normalizeHue(theme.surfaceHue)
        val surfaceChroma = (theme.surfaceChroma.coerceIn(0.0, SURFACE_CHROMA_MAX)).toFloat() / 100f

        // 先派生三个角色族的"底色"(on* 文字色要按它们的实际亮度自适应)
        val derivedPrimary = deriveChromatic(template.primary, primaryHue)
        val derivedSecondary = deriveChromatic(template.secondary, secondaryHue)
        val derivedTertiary = deriveChromatic(template.tertiary, tertiaryHue)

        return WakeUpColorScheme(
            primary = derivedPrimary,
            onPrimary = adaptOn(derivedPrimary),
            primaryContainer = deriveChromatic(template.primaryContainer, primaryHue),
            onPrimaryContainer = deriveChromatic(template.onPrimaryContainer, primaryHue),

            secondary = derivedSecondary,
            onSecondary = adaptOn(derivedSecondary),
            secondaryContainer = deriveChromatic(template.secondaryContainer, secondaryHue),
            onSecondaryContainer = deriveChromatic(template.onSecondaryContainer, secondaryHue),

            tertiary = derivedTertiary,
            onTertiary = adaptOn(derivedTertiary),
            tertiaryContainer = deriveChromatic(template.tertiaryContainer, tertiaryHue),
            onTertiaryContainer = deriveChromatic(template.onTertiaryContainer, tertiaryHue),

            background = deriveSurface(template.background, surfaceHue, surfaceChroma),
            onBackground = deriveSurface(template.onBackground, surfaceHue, surfaceChroma),
            surface = deriveSurface(template.surface, surfaceHue, surfaceChroma),
            onSurface = deriveSurface(template.onSurface, surfaceHue, surfaceChroma),
            surfaceVariant = deriveSurface(template.surfaceVariant, surfaceHue, surfaceChroma),
            onSurfaceVariant = deriveSurface(template.onSurfaceVariant, surfaceHue, surfaceChroma),
            surfaceContainerLowest = deriveSurface(template.surfaceContainerLowest, surfaceHue, surfaceChroma),
            surfaceContainerLow = deriveSurface(template.surfaceContainerLow, surfaceHue, surfaceChroma),
            surfaceContainer = deriveSurface(template.surfaceContainer, surfaceHue, surfaceChroma),
            surfaceContainerHigh = deriveSurface(template.surfaceContainerHigh, surfaceHue, surfaceChroma),
            surfaceContainerHighest = deriveSurface(template.surfaceContainerHighest, surfaceHue, surfaceChroma),

            outline = deriveSurface(template.outline, surfaceHue, surfaceChroma),
            outlineVariant = deriveSurface(template.outlineVariant, surfaceHue, surfaceChroma),
            scrim = template.scrim,

            error = template.error,
            onError = template.onError,
            errorContainer = template.errorContainer,
            onErrorContainer = template.onErrorContainer
        )
    }

    // ── 角色派生 ──

    /**
     * 有色相角色派生:保留模板角色的 S/V 结构,替换色相。
     * 模板角色本身无色相(纯白/纯黑/纯灰,S≈0)→ 色相替换无视觉意义,照抄模板
     * (light onPrimary=白、dark onPrimary 模板=深紫有相 → 换种子相,S/V 照抄)。
     */
    private fun deriveChromatic(templateColor: Color, seedHue: Float): Color {
        val (h, s, v) = rgbToHsv(templateColor.red, templateColor.green, templateColor.blue)
        // S 极低(≈中性)的角色色相替换不产生可见变化 — 直接保留模板,避免浮点噪声
        if (s < 0.02f) return templateColor
        return hsvToColor(seedHue, s, v)
    }

    /** 表面族派生:模板明度 V + surfaceHue/surfaceChroma 低饱和中性 */
    private fun deriveSurface(templateColor: Color, surfaceHue: Float, surfaceChroma: Float): Color {
        val (_, _, v) = rgbToHsv(templateColor.red, templateColor.green, templateColor.blue)
        return hsvToColor(surfaceHue, surfaceChroma, v)
    }

    /** on* 文字色按派生底色亮度自适应:深底白字,浅底黑字(textColorOn 思路) */
    private fun adaptOn(derivedBase: Color): Color =
        if (derivedBase.luminance() < 0.5f) Color.White else Color.Black

    // ── 种子解析 ──

    /** 解析种子 hex 的色相;解析失败/无色相 → 模板同角色色相兜底 */
    private fun hueOfSeed(hex: String, fallback: Color): Float {
        val color = parseHex(hex) ?: return hueOrFallback(fallback)
        val hue = hueOf(color)
        return if (hue == null) hueOrFallback(fallback) else hue
    }

    private fun hueOrFallback(c: Color): Float {
        val (h, s, _) = rgbToHsv(c.red, c.green, c.blue)
        return if (s < 0.02f) 0f else h
    }

    /** "#RRGGBB" / "#AARRGGBB" 解析;失败返回 null(容错,不抛) */
    fun parseHex(hex: String): Color? {
        val s = hex.trim()
        if (s.length != 7 && s.length != 9) return null
        if (!s.startsWith("#")) return null
        val body = s.substring(1)
        return try {
            val value = body.toLong(16)
            val argb = if (body.length == 6) (0xFF000000L or value) else value
            Color(argb.toInt())
        } catch (_: NumberFormatException) {
            null
        }
    }

    // ── 纯 Kotlin HSV 转换(JVM 测试安全,禁 android.graphics.Color) ──

    /** RGB(0-1)→HSV;返回三元组 (h 0-360, s 0-1, v 0-1)。无色相(s≈0)时 h=0 */
    fun rgbToHsv(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val max = maxOf(r, g, b).coerceIn(0f, 1f)
        val min = minOf(r, g, b).coerceIn(0f, 1f)
        val d = max - min
        val s = if (max <= 0f) 0f else d / max
        if (d < 1e-6f) return Triple(0f, s, max)
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return Triple(if (h < 0) h + 360f else h, s.coerceIn(0f, 1f), max)
    }

    /** HSV → Compose Color(h 0-360, s/v 0-1;越界输入收敛后计算,不抛不 NaN) */
    fun hsvToColor(h: Float, s: Float, v: Float): Color {
        val hh = normalizeHue(h.toDouble()).toFloat()
        val ss = s.coerceIn(0f, 1f)
        val vv = v.coerceIn(0f, 1f)
        val c = vv * ss
        val x = c * (1f - abs((hh / 60f) % 2f - 1f))
        val m = vv - c
        val (r1, g1, b1) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            ((r1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((g1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        )
    }

    /** 色相归一化到 [0, 360):负值/超 360/NaN 均收敛 */
    fun normalizeHue(h: Double): Float {
        if (h.isNaN() || h.isInfinite()) return 0f
        val m = h % 360.0
        return (if (m < 0) m + 360.0 else m).toFloat()
    }

    /** Compose Color 的色相;无色相(s≈0)返回 null */
    fun hueOf(c: Color): Float? {
        val (h, s, _) = rgbToHsv(c.red, c.green, c.blue)
        return if (s < 1e-4f) null else h
    }

    private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
}
