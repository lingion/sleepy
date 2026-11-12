package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.shadow.Shadow
import material.Hct

/**
 * 液态玻璃的外观参数, 逐字段对应 FLClash 的 `lib/ui/liquid_glass.dart` 中的
 * `LiquidGlassStyle`。长度单位是 dp —— 着色器会把 [bevel] 与 [refraction]
 * 收缩到放得下它们的面板尺寸, 使按钮随底栏等比弯曲。
 *
 * FLClash 的每个工厂都按 `colorScheme.brightness == Brightness.dark` 分支;
 * Compose 的 [ColorScheme] 没有亮度字段, 故此处显式传入 [dark]。
 */
data class LiquidGlassStyle(
    val tint: Color,
    /** tint 在比背景明显更暗 (或相反, 负值) 的内容之上加厚多少, 使压在玻璃上的内容仍可读。 */
    val contrast: Double = 0.0,
    val blur: Double = 2.5,
    /** 经 [hazeBlur] 而非 [blur] 看到背景的比例。 */
    val haze: Double = 0.0,
    val hazeBlur: Double = 0.0,
    /** 负值表示凸起的镜片, 其边缘向外折射。 */
    val refraction: Double = 60.0,
    val bevel: Double = 20.0,
    val saturation: Double = 1.5,
    val brightness: Double = 1.0,
    val rim: Double = 0.6,
    val rimWidth: Double = 1.3,
    val rimTail: Double = 0.0,
    /** 0 让 [rim] 在两端变暗 (如 iOS 的标签栏), 1 如其控件。 */
    val rimFocus: Double = 0.0,
    /** [rim] 中作为光而非向白色混合的比例。 */
    val rimAdded: Double = 0.0,
    val endShade: Double = 0.0,
    val dispersion: Double = 0.0,
    val rimBlur: Double = 0.0,
    val hairline: Double = 0.0,
    val hairlineSide: Double? = null,
) {
    /** Dart 的 `_hairlineSide`: 未单独指定时退回 [hairline]。 */
    val hairlineSideOrHairline: Double get() = hairlineSide ?: hairline

    /** 从无到有地渐入, 如玻璃靠弯曲而非淡入显形。 */
    fun scale(t: Double): LiquidGlassStyle {
        val amount = t.coerceIn(0.0, 1.0)
        return copy(
            tint = tint.copy(alpha = (tint.alpha * amount).toFloat()),
            contrast = contrast * amount,
            blur = blur * amount,
            haze = haze * amount,
            hazeBlur = hazeBlur,
            refraction = refraction * amount,
            bevel = bevel,
            saturation = 1.0 + (saturation - 1.0) * amount,
            brightness = 1.0 + (brightness - 1.0) * amount,
            rim = rim * amount,
            rimWidth = rimWidth,
            rimTail = rimTail,
            rimFocus = rimFocus,
            rimAdded = rimAdded,
            endShade = endShade * amount,
            dispersion = dispersion * amount,
            rimBlur = rimBlur * amount,
            hairline = hairline * amount,
            hairlineSide = hairlineSide?.times(amount),
        )
    }

    companion object {
        /**
         * FLClash 的 `ui.lerpDouble`: 端点相等时直接返回端点, 否则 `a * (1 - t) + b * t`。
         * dart:ui 的 lerp.dart 文档写的是 `a + (b - a) * t`, 但那是过期注释,
         * 实际实现是 `a * (1.0 - t) + b * t`。
         */
        private fun lerpDouble(a: Double, b: Double, t: Double): Double =
            if (a == b) a else a * (1.0 - t) + b * t

        /**
         * Flutter 的 `Color.lerp`: 逐通道线性插值 sRGB 分量, 每个通道夹到 0..1, 不做色彩空间变换。
         * Compose 的 `Color.lerp` 走 Oklab, 与 Flutter 语义不同, 故此处自行实现。
         *
         * Flutter 用的是私有的 `_lerpDouble`(无端点相等的提前返回), 故此处也不提前返回。
         */
        private fun lerpColor(a: Color, b: Color, t: Double): Color {
            fun channel(x: Float, y: Float): Float =
                (x * (1.0 - t) + y * t).coerceIn(0.0, 1.0).toFloat()
            return Color(
                channel(a.red, b.red),
                channel(a.green, b.green),
                channel(a.blue, b.blue),
                channel(a.alpha, b.alpha),
            )
        }

        fun lerp(a: LiquidGlassStyle, b: LiquidGlassStyle, t: Double): LiquidGlassStyle =
            LiquidGlassStyle(
                tint = lerpColor(a.tint, b.tint, t),
                contrast = lerpDouble(a.contrast, b.contrast, t),
                blur = lerpDouble(a.blur, b.blur, t),
                haze = lerpDouble(a.haze, b.haze, t),
                hazeBlur = lerpDouble(a.hazeBlur, b.hazeBlur, t),
                refraction = lerpDouble(a.refraction, b.refraction, t),
                bevel = lerpDouble(a.bevel, b.bevel, t),
                saturation = lerpDouble(a.saturation, b.saturation, t),
                brightness = lerpDouble(a.brightness, b.brightness, t),
                rim = lerpDouble(a.rim, b.rim, t),
                rimWidth = lerpDouble(a.rimWidth, b.rimWidth, t),
                rimTail = lerpDouble(a.rimTail, b.rimTail, t),
                rimFocus = lerpDouble(a.rimFocus, b.rimFocus, t),
                rimAdded = lerpDouble(a.rimAdded, b.rimAdded, t),
                endShade = lerpDouble(a.endShade, b.endShade, t),
                dispersion = lerpDouble(a.dispersion, b.dispersion, t),
                rimBlur = lerpDouble(a.rimBlur, b.rimBlur, t),
                hairline = lerpDouble(a.hairline, b.hairline, t),
                hairlineSide = lerpDouble(a.hairlineSideOrHairline, b.hairlineSideOrHairline, t),
            )

        fun regular(colorScheme: ColorScheme, dark: Boolean): LiquidGlassStyle =
            if (dark) {
                LiquidGlassStyle(
                    tint = colorScheme.surfaceContainerHighest.copy(alpha = 0.62f),
                    contrast = -0.4,
                    saturation = 2.84,
                    rim = 0.28,
                    rimWidth = 1.2,
                    rimTail = 0.17,
                    rimFocus = 1.0,
                    rimAdded = 0.38,
                    hairlineSide = 0.75,
                )
            } else {
                LiquidGlassStyle(
                    tint = Color(0x8AFDFDFD),
                    contrast = 0.4,
                    saturation = 2.08,
                    rim = 0.28,
                    rimWidth = 1.2,
                    rimTail = 0.17,
                    rimFocus = 1.0,
                    rimAdded = 0.38,
                    hairline = 0.25,
                    hairlineSide = 0.75,
                )
            }

        fun pill(colorScheme: ColorScheme, dark: Boolean): LiquidGlassStyle =
            if (dark) {
                LiquidGlassStyle(
                    tint = colorScheme.surfaceContainerHighest.copy(alpha = 0.48f),
                    contrast = -0.4,
                    blur = 0.0,
                    haze = 0.72,
                    hazeBlur = 15.6,
                    saturation = 1.925,
                    rim = 0.35,
                )
            } else {
                LiquidGlassStyle(
                    tint = Color(0x93F4F4F4),
                    contrast = 0.4,
                    blur = 0.0,
                    haze = 0.72,
                    hazeBlur = 15.6,
                    saturation = 2.1,
                    hairline = 0.35,
                )
            }

        /** 比 [regular] 更厚, 用于压在任意内容之上的行。它的 rim 来自 [LiquidGlassRim]。 */
        fun menu(colorScheme: ColorScheme, dark: Boolean): LiquidGlassStyle =
            if (dark) {
                LiquidGlassStyle(
                    tint = colorScheme.surfaceContainerHighest.copy(alpha = 0.63f),
                    contrast = -0.53,
                    blur = 12.0,
                    saturation = 1.75,
                    brightness = 1.8,
                    rim = 0.0,
                )
            } else {
                LiquidGlassStyle(
                    tint = colorScheme.surfaceBright.copy(alpha = 0.55f),
                    blur = 12.0,
                    saturation = 2.0,
                    brightness = 1.1,
                    rim = 0.0,
                )
            }

        fun alert(colorScheme: ColorScheme, dark: Boolean): LiquidGlassStyle =
            menu(colorScheme, dark).copy(blur = 16.0)

        /**
         * Separate from context menus: measured iOS 26.5 system sheets have a
         * neutral backing and retain far less background light in dark mode.
         * These are empirical shader coefficients, not native material values.
         */
        fun sheet(dark: Boolean): LiquidGlassStyle =
            if (dark) LiquidGlassStyle(
                tint = Color(0xFF313131).copy(alpha = .59f),
                contrast = -.09, blur = 12.0, brightness = .315,
                saturation = 2.50, rim = 0.0,
            ) else LiquidGlassStyle(
                tint = Color(0xFFF2F2F2).copy(alpha = .70f),
                contrast = .05, blur = 12.0, brightness = .88,
                saturation = 1.90, rim = 0.0,
            )

        fun tinted(colorScheme: ColorScheme, color: Color, dark: Boolean): LiquidGlassStyle =
            LiquidGlassStyle(
                tint = color.copy(alpha = 0.82f),
                saturation = 1.2,
                rim = if (dark) 0.14 else 0.2,
                rimWidth = 1.2,
                rimTail = 0.17,
                rimFocus = 1.0,
                rimAdded = 0.38,
                hairline = if (dark) 0.0 else 0.34,
                hairlineSide = if (dark) 0.73 else 0.5,
            )

        fun lens(colorScheme: ColorScheme, dark: Boolean): LiquidGlassStyle =
            LiquidGlassStyle(
                tint = Color.Transparent,
                blur = 0.0,
                refraction = -4.3,
                bevel = 5.5,
                saturation = 1.0,
                brightness = if (dark) 1.33 else 1.0,
                rim = if (dark) 0.3 else 0.6,
                rimWidth = 1.1,
                endShade = 0.045,
                dispersion = 6.5,
                rimBlur = 1.5,
                hairline = 0.42,
            )

        /** Floating controls share the Dock's edge dispersion. */
        fun controlLens(colorScheme: ColorScheme, dark: Boolean): LiquidGlassStyle =
            lens(colorScheme, dark)

        /** Glyph refraction retains the Dock's independent content dispersion. */
        fun controlLensContent(colorScheme: ColorScheme, dark: Boolean): LiquidGlassStyle =
            lensContent(colorScheme, dark)

        /** FLClash navigation_dock.dart `_lensShadows`, shared by the floating controls. */
        fun controlLensShadow(dark: Boolean, lift: Float): Shadow =
            Shadow(radius = 5.dp, offset = DpOffset(0.dp, 8.dp),
                color = Color.Black.copy(alpha = .055f * (if (dark) 3f else 1f) * lift.coerceIn(0f, 1f)))

        fun lensContent(colorScheme: ColorScheme, dark: Boolean): LiquidGlassStyle =
            LiquidGlassStyle(
                tint = Color.Transparent,
                blur = 0.0,
                refraction = 14.0,
                bevel = 11.2,
                saturation = 1.0,
                brightness = if (dark) 1.33 else 1.0,
                rim = 0.0,
                dispersion = 3.6,
                rimBlur = 2.5,
            )

        /**
         * Dart 的 `_standard`: 标准档位不分色, 整片雾感用一层模糊。
         * 只在质量档位为 standard 时取代 [LiquidGlassStyle] 本身。
         */
        fun standard(style: LiquidGlassStyle): LiquidGlassStyle {
            if (style.haze == 0.0 && style.dispersion == 0.0) return style
            return style.copy(
                blur = if (style.haze > 0.0) style.hazeBlur else style.blur,
                haze = 0.0,
                hazeBlur = 0.0,
                dispersion = 0.0,
            )
        }
    }
}

/**
 * iOS 27 的菜单/警告玻璃外圈。
 * FLClash 的 `LiquidGlassRim` (`lib/ui/liquid_glass.dart`),
 * 说明见 FLClash 资产的 `.agents/architecture.md:769`。
 */
data class LiquidGlassRim(
    val side: Double,
    val corner: Double,
    val flat: Double,
    /** 增补的光而非盖上的白: iOS 深浅两种面板都被提亮。 */
    val shine: Double,
) {
    fun scale(t: Double): LiquidGlassRim {
        val amount = t.coerceIn(0.0, 1.0)
        return copy(
            side = side * amount,
            corner = corner * amount,
            flat = flat * amount,
            shine = shine * amount,
        )
    }

    companion object {
        /** A sheet retains its directional optical highlight without a menu's dark hairline. */
        fun sheet(): LiquidGlassRim =
            LiquidGlassRim(side = 0.0, corner = 0.0, flat = 0.0, shine = 0.16)

        fun alert(dark: Boolean): LiquidGlassRim =
            if (dark) {
                LiquidGlassRim(side = 0.75, corner = 0.1, flat = 0.03, shine = 0.22)
            } else {
                LiquidGlassRim(side = 0.35, corner = 0.22, flat = 0.12, shine = 0.16)
            }

        fun menu(dark: Boolean): LiquidGlassRim =
            if (dark) {
                LiquidGlassRim(side = 0.27, corner = 0.1, flat = 0.02, shine = 0.22)
            } else {
                LiquidGlassRim(side = 0.25, corner = 0.15, flat = 0.09, shine = 0.16)
            }
    }
}

/**
 * 一次按压打在面板上的光, 用面板自身坐标表示: [strength] 在 [center],
 * 在 [reach] 内衰减到铺满面板的 [wash]。
 * FLClash 的 `LiquidGlassGlow`。
 */
data class LiquidGlassGlow(
    val center: Offset,
    val reach: Double,
    val strength: Double,
    val wash: Double = 0.0,
) {
    companion object {
        val None = LiquidGlassGlow(center = Offset.Zero, reach = 1.0, strength = 0.0)
    }
}

/**
 * FLClash 的 `Color.shiftTone`: HCT 色调加 [delta], 色相与彩度不变。
 * `com.google.android.material:material-color-utilities` 不存在于任何 Maven 仓库,
 * 故用 API 等价的 `studio.lunabee.compose:material-color-utilities`。
 */
internal fun Color.shiftTone(delta: Double): Color {
    val hct = Hct.fromInt(toArgb())
    val shifted = Hct.from(hct.hue, hct.chroma, (hct.tone + delta).coerceIn(0.0, 100.0))
    return Color(shifted.toInt())
}
