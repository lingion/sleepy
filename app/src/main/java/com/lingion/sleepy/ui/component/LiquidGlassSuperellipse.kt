package com.lingion.sleepy.ui.component

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * FlClash `liquid_glass.dart` 的 superellipse 角曲线生成器 1:1 移植
 * (v0.9.1, 源 1401-1486 行): [cornerCurves] == Dart `liquidGlassCornerCurves`,
 * 产出的 48 个 float 直接喂给 AGSL 的 `uniform float4 uCurves[12]`。
 *
 * 这条曲线是 Impeller `round_superellipse_param.cc` 的 `ComputeOctant`,
 * shader 的 `cornerInset` 按同一参数复算到曲线的距离 —— 移植必须逐式一致,
 * 差一个常数玻璃边缘就会和 clip 错位半 dp。
 *
 * 单位: 与调用方一致(FlClash 里全部是 dp), 不做 dp/px 换算。
 * 坐标: joint/center 在"以角为原点的八分圆框架"里, a=该轴半长。
 */
object LiquidGlassSuperellipse {

    /** Dart `_octantGap = 1 - math.sqrt1_2`; sqrt1_2 = 1/√2。 */
    private const val OCTANT_GAP = 1.0 - 0.7071067811865476

    /**
     * Dart `_octantFits`: 按 (octant 半轴 a)/(radius) = ratio 索引的拟合表,
     * 每项 (ratio, n, K), 其中 jointAt = 1 - 1/K。逐字照抄, 勿改精度。
     */
    private val OCTANT_FITS = arrayOf(
        doubleArrayOf(2.0, 2.00000000, 1.13276676),
        doubleArrayOf(2.1, 2.18349805, 1.20311921),
        doubleArrayOf(2.2, 2.33888662, 1.28698796),
        doubleArrayOf(2.3, 2.48660575, 1.36351941),
        doubleArrayOf(2.4, 2.62226596, 1.44717976),
        doubleArrayOf(2.5, 2.75148990, 1.53385819),
        doubleArrayOf(3.0, 3.36298265, 1.98288283),
        doubleArrayOf(3.5, 4.08649929, 2.23811846),
        doubleArrayOf(4.0, 4.85481134, 2.47563463),
        doubleArrayOf(4.5, 5.62945551, 2.72948597),
        doubleArrayOf(5.0, 6.43023796, 2.98020421),
    )

    /** 一个八分圆的 superellipse 参数(Dart `({double n, Offset joint, Offset center, double radius})`)。 */
    data class Octant(val n: Double, val jointX: Double, val jointY: Double, val centerX: Double, val centerY: Double, val radius: Double)

    /**
     * Dart `_octantFit(ratio)` → `(n, jointAt)`。ratio 越过表尾线性外推, 表内
     * 线性插值 n、插 K 后取 jointAt = 1-1/K。t 用 max(0,…) 兜底(表首以下钳 0)。
     */
    fun octantFit(ratio: Double): Pair<Double, Double> {
        val last = OCTANT_FITS.last()
        val lastRatio = last[0]; val lastN = last[1]; val lastK = last[2]
        val past = ratio - lastRatio
        if (past > 0.0) {
            return Pair(lastN + 1.559599389 * past, 1.0 - 1.0 / (lastK + 0.522807185 * past))
        }
        var index = 0
        while (index < OCTANT_FITS.size - 2 && ratio > OCTANT_FITS[index + 1][0]) {
            index++
        }
        val r0 = OCTANT_FITS[index][0]; val n0 = OCTANT_FITS[index][1]; val k0 = OCTANT_FITS[index][2]
        val r1 = OCTANT_FITS[index + 1][0]; val n1 = OCTANT_FITS[index + 1][1]; val k1 = OCTANT_FITS[index + 1][2]
        val t = maxOf(0.0, (ratio - r0) / (r1 - r0))
        val n = lerpDouble(n0, n1, t)
        val k = lerpDouble(k0, k1, t)
        return Pair(n, 1.0 - 1.0 / k)
    }

    /**
     * Dart `_superellipseOctant(a, radius)`。radius ≤ 1e-3 → 尖角(n=0, 全零)。
     * 逐式照抄: joint → tangent → diagonal → meet → arc → chord → reach → center。
     */
    fun superellipseOctant(a: Double, radius: Double): Octant {
        if (radius <= 1e-3) return Octant(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val (n, jointAt) = octantFit(a * 2.0 / radius)
        val jointX = jointAt * a
        val jointY = (1.0 - jointAt.pow(n)).pow(1.0 / n) * a
        val tangent = (jointX / jointY).pow(n - 1.0)
        val diagonal = (jointX - tangent * jointY) / (1.0 - tangent)
        val meet = a - OCTANT_GAP * radius
        val arc = (meet - diagonal) * SQRT2
        val chordX = meet - jointX
        val chordY = meet - jointY
        val chordDist = sqrt(chordX * chordX + chordY * chordY)
        val reach = sqrt(maxOf(0.0, arc * arc - (chordX * chordX + chordY * chordY) / 4.0))
        // center = (joint + (meet,meet))/2 + (chordY, -chordX)/|chord| * reach
        //   (Dart: Offset(chord.dy, -chord.dx) —— 法向, 与 (meet,meet) 对称无关)
        val nx = if (chordDist == 0.0) 0.0 else chordY / chordDist
        val ny = if (chordDist == 0.0) 0.0 else -chordX / chordDist
        val centerX = (jointX + meet) / 2.0 + nx * reach
        val centerY = (jointY + meet) / 2.0 + ny * reach
        return Octant(n, jointX, jointY, centerX, centerY, arc)
    }

    /**
     * Dart `_cornerCurve(halfWidth, halfHeight, radius)` → 12 floats:
     * along(n,jx,jy,r), across(n,jx,jy,r), along.center(x,y), across.center(x,y)。
     * 顺序与 shader `uCurves` 每角 3 向量一致。
     */
    fun cornerCurve(halfWidth: Double, halfHeight: Double, radius: Double): DoubleArray {
        val along = superellipseOctant(halfWidth, radius)
        val across = superellipseOctant(halfHeight, radius)
        return doubleArrayOf(
            along.n, along.jointX, along.jointY, along.radius,
            across.n, across.jointX, across.jointY, across.radius,
            along.centerX, along.centerY, across.centerX, across.centerY,
        )
    }

    /**
     * Dart `liquidGlassCornerCurves(size, corners)` → 48 floats, 四角各 12。
     * 角序 = uCorners: TL, TR, BR, BL, 每角传 `corners[轴].x`(Dart 只取 .x,
     * 即 BorderRadius 的圆角半径按几何 x 分量; Dock 全圆角四值相同)。
     */
    fun cornerCurves(width: Double, height: Double, tl: Double, tr: Double, br: Double, bl: Double): DoubleArray {
        val halfW = width / 2.0
        val halfH = height / 2.0
        val out = DoubleArray(48)
        val radii = doubleArrayOf(tl, tr, br, bl)
        for (i in 0 until 4) {
            cornerCurve(halfW, halfH, radii[i]).copyInto(out, i * 12)
        }
        return out
    }

    /** 全等圆角便捷式: Dock 三 pane 都用 BorderRadius.circular(shortestSide/2)。 */
    fun capsuleCurves(width: Double, height: Double): DoubleArray {
        val radius = minOf(width, height) / 2.0
        return cornerCurves(width, height, radius, radius, radius, radius)
    }

    private const val SQRT2 = 1.4142135623730951

    /** ui.lerpDouble(a,b,t) = a+(b-a)*t。 */
    private fun lerpDouble(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
