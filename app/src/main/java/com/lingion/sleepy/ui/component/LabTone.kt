package com.lingion.sleepy.ui.component

import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * FlClash `_Lens` 玻璃托盘色源 (navigation_dock.dart 1342-1344):
 * `Hct.fromInt(secondary) .. tone = dark ? 4.0 : 90.0`。HCT 的 tone 按定义
 * 就是 CIE L* (tone↔L* 恒等, material_color_utilities OkLCH 只是其参数化),
 * 项目无 material_color_utilities 依赖 → 直译 sRGB↔Lab(D65):
 * 目标 L* 上保持色相(h)方向、色度(C)在 sRGB 色域内二分最大化 —— 这是
 * HCT setTone 的 Lab 类比: HCT 同样在保持 hue/chroma 意图的前提下钳色域,
 * tone 4/90 的近中性结果下二者输出一致 (LabToneTest 用 M3 baseline token
 * secondary4=#1D192B / secondary90=#E6E0F9 与灰阶锚点锁死)。
 *
 * 纯 JVM 数学, 无 Android 依赖; 输入/输出 = ARGB Int (alpha 原样保留)。
 */
object LabTone {

    /** D65 白点。 */
    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883
    private const val DELTA = 6.0 / 29.0
    private const val DELTA3 = 216.0 / 24389.0 // δ³ — CIE 分段阈值
    private const val LEVER = 841.0 / 108.0 // 1/(3δ²)

    /** sRGB 8bit 通道 → 线性(IEC 61966-2-1)。 */
    private fun toLinear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** 线性 0..1(可越界) → 8bit, 越界钳位。 */
    private fun toSrgb(c: Double): Int {
        val v = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055
        return (v * 255.0).roundToInt().coerceIn(0, 255)
    }

    /** ARGB → CIE L* (0..100)。 */
    fun lStar(argb: Int): Float {
        val y = forwardY(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)
        val fy = if (y > DELTA3) cbrt(y) else (y * LEVER + 16.0 / 116.0)
        return (116.0 * fy - 16.0).toFloat()
    }

    private fun forwardY(r: Int, g: Int, b: Int): Double {
        val lin = toLinear(r) * 0.2126 + toLinear(g) * 0.7152 + toLinear(b) * 0.0722
        return lin / YN
    }

    /** ARGB → Lab a,b (D65)。 */
    private fun forwardAb(argb: Int): Pair<Double, Double> {
        val r = toLinear(argb shr 16 and 0xFF)
        val g = toLinear(argb shr 8 and 0xFF)
        val b = toLinear(argb and 0xFF)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / XN
        val y = (0.2126 * r + 0.7152 * g + 0.0722 * b) / YN
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / ZN
        val fx = if (x > DELTA3) cbrt(x) else x * LEVER + 16.0 / 116.0
        val fy = if (y > DELTA3) cbrt(y) else y * LEVER + 16.0 / 116.0
        val fz = if (z > DELTA3) cbrt(z) else z * LEVER + 16.0 / 116.0
        return Pair(500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    /** Lab(L, a, b) → 线性 RGB 三元组(可能越界); [inGamut] 判定共用。 */
    private fun rawLinear(lStar: Double, a: Double, b: Double): DoubleArray {
        val fy = (lStar + 16.0) / 116.0
        val fx = fy + a / 500.0
        val fz = fy - b / 200.0
        fun f3(f: Double): Double = if (f > 6.0 / 29.0) f * f * f else 3.0 * (6.0 / 29.0) * (6.0 / 29.0) * (f - 4.0 / 29.0)
        val x = XN * f3(fx)
        val y = YN * f3(fy)
        val z = ZN * f3(fz)
        return doubleArrayOf(
            3.2406 * x - 1.5372 * y - 0.4986 * z,
            -0.9689 * x + 1.8758 * y + 0.0415 * z,
            0.0557 * x - 0.2040 * y + 1.0570 * z,
        )
    }

    /**
     * 色域判定, 容差 eps 给 8bit 量化留边(sRGB 1 级 ≈ 1/255 线性附近量级,
     * 取 1/510 半级)。
     */
    private fun inGamut(lStar: Double, a: Double, b: Double): Boolean {
        val rgb = rawLinear(lStar, a, b)
        return rgb.all { it >= -1.0 / 510.0 && it <= 1.0 + 1.0 / 510.0 }
    }

    /**
     * 把 [argb] 的 L* 设为 [tone](HCT `..tone=` 的等价), 保持 hue 方向与
     * 源色度, 仅当目标 L* 上原色度出界时沿 a,b 等比缩进色域(Lab 类比
     * setTone 的色域钳位; 近中性 tone 4/90 下与 HCT 输出一致)。
     */
    fun tone(argb: Int, tone: Float): Int {
        val l = tone.coerceIn(0f, 100f).toDouble()
        val (a0, b0) = forwardAb(argb)
        var lo = 0.0
        var hi = 1.0
        // 二分找最大色度缩放 s∈[0,1] 使 (L*, s·a0, s·b0) 落在色域内
        repeat(24) {
            val mid = (lo + hi) / 2.0
            if (inGamut(l, a0 * mid, b0 * mid)) lo = mid else hi = mid
        }
        val rgb = rawLinear(l, a0 * lo, b0 * lo)
        val alpha = argb ushr 24 and 0xFF
        return (alpha shl 24) or
            (toSrgb(rgb[0]) shl 16) or (toSrgb(rgb[1]) shl 8) or toSrgb(rgb[2])
    }
}
