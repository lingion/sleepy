package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.AndroidPath
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 液态玻璃的几何运算。逐段对应 FLClash 的 `lib/ui/liquid_glass.dart` 中同名 Dart 实现,
 * 不引入该文件之外的任何算法:
 *
 *  - `liquidGlassProfile`       -> `uProfile[16]`(64 个转折上的轮廓距离)
 *  - `liquidGlassCornerCurves`  -> `uCurves[12]`(每个角一条超椭圆八分弧的参数)
 *  - `_superellipseOctant` / `_octantFit` / `_octantFits` / `_octantGap`
 *  - `LiquidGlassRim._turns` / `_uniforms` -> `uShineTurns` / `uShineLit` / `uShineEnds` / `uShineShares`
 *  - `LiquidGlassGoo._uniforms` -> `uPills` / `uGoo` / `uPillCurves` / `uPillCenters`
 *  - `_optics`                  -> 饱和度 / 亮度色彩矩阵
 */

/** `uProfile` 的转折数, 与 `shaders/liquid_glass.frag` 的 `uProfile[16]` 一致。 */
internal const val LIQUID_GLASS_PROFILE_TURNS = 64

/** `uShineEnds` / `uShineShares` 的槽位数, 与着色器一致。 */
internal const val LIQUID_GLASS_SHINE_SLOTS = 12

/** `uPills` 的行数, 与着色器一致。 */
internal const val LIQUID_GLASS_GOO_PILLS = 6

/** 角的四个半径, 顺序为左上、右上、右下、左下。 */
internal data class LiquidGlassCornerRadii(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
) {
    /** Flutter `BorderRadius` 的四个横向半径。 */
    fun of(corners: List<CornerRadius>): LiquidGlassCornerRadii = LiquidGlassCornerRadii(
        topLeft = corners[0].x,
        topRight = corners[1].x,
        bottomRight = corners[2].x,
        bottomLeft = corners[3].x,
    )
}

/**
 * Dart 的 `Offset` 是双精度, 这里用同样的双精度保存中间量, 只在写入 uniform 时降到单精度。
 */
private class Vec2(val x: Double, val y: Double) {
    val distance: Double get() = sqrt(x * x + y * y)

    /** Flutter `Offset.direction` = `atan2(dy, dx)`。 */
    val direction: Double get() = atan2(y, x)
}

/**
 * 一个超椭圆八分弧的参数, 对应 Dart 的 `_Octant`。
 *
 * @param n 超椭圆的指数
 * @param joint 八分弧与直边相接处的点
 * @param center 八分弧的圆心
 * @param radius 八分弧的半径
 */
internal data class LiquidGlassOctant(
    val n: Double,
    val joint: Offset,
    val center: Offset,
    val radius: Double,
)

/** Dart 的 `_noOctant`。 */
private val NO_OCTANT = LiquidGlassOctant(
    n = 0.0,
    joint = Offset.Zero,
    center = Offset.Zero,
    radius = 0.0,
)

/** Dart 的 `_octantGap` = `1 - sqrt1_2`。 */
private const val OCTANT_GAP = 1.0 - 0.70710678118654752440

/**
 * Dart 的 `_octantFits`: 每个八分弧的边长与半径之比 -> (n, 1 / (1 - xJ / a))。
 */
private val OCTANT_FITS = listOf(
    Triple(2.0, 2.00000000, 1.13276676),
    Triple(2.1, 2.18349805, 1.20311921),
    Triple(2.2, 2.33888662, 1.28698796),
    Triple(2.3, 2.48660575, 1.36351941),
    Triple(2.4, 2.62226596, 1.44717976),
    Triple(2.5, 2.75148990, 1.53385819),
    Triple(3.0, 3.36298265, 1.98288283),
    Triple(3.5, 4.08649929, 2.23811846),
    Triple(4.0, 4.85481134, 2.47563463),
    Triple(4.5, 5.62945551, 2.72948597),
    Triple(5.0, 6.43023796, 2.98020421),
)

/**
 * Dart 的 `_octantFit`: 按八分弧的边长与半径之比拟合 (n, k)。
 */
private fun octantFit(ratio: Double): Pair<Double, Double> {
    val (lastRatio, lastN, lastK) = OCTANT_FITS.last()
    val past = ratio - lastRatio
    if (past > 0) {
        return (lastN + 1.559599389 * past) to (1.0 - 1.0 / (lastK + 0.522807185 * past))
    }
    var index = 0
    while (index < OCTANT_FITS.size - 2 && ratio > OCTANT_FITS[index + 1].first) {
        index++
    }
    val (r0, n0, k0) = OCTANT_FITS[index]
    val (r1, n1, k1) = OCTANT_FITS[index + 1]
    val t = max(0.0, (ratio - r0) / (r1 - r0))
    // Dart 的 `ui.lerpDouble` 是 `a * (1 - t) + b * t`, 不是 `a + (b - a) * t`:
    // 见 dart:ui 的 lerp.dart, 其 docstring 写的 `a + (b - a) * t` 已过期。
    return (n0 * (1.0 - t) + n1 * t) to (1.0 - 1.0 / (k0 * (1.0 - t) + k1 * t))
}

/**
 * Dart 的 `_superellipseOctant`: 计算 `impeller/geometry/round_superellipse_param.cc` 中的
 * 八分弧, 着色器的 `cornerInset` 依此计算内缩。
 *
 * @param a 半边长
 * @param radius 圆角半径
 */
internal fun superellipseOctant(a: Double, radius: Double): LiquidGlassOctant {
    if (radius <= 1e-3) return NO_OCTANT
    val (n, jointAt) = octantFit(a * 2 / radius)
    val joint = Vec2(
        x = jointAt * a,
        y = (1.0 - jointAt.pow(n)).pow(1.0 / n) * a,
    )
    val tangent = (joint.x / joint.y).pow(n - 1)
    val diagonal = (joint.x - tangent * joint.y) / (1.0 - tangent)
    val meet = a - OCTANT_GAP * radius
    val arc = (meet - diagonal) * sqrt(2.0)
    val chordX = meet - joint.x
    val chordY = meet - joint.y
    val chordDistance = sqrt(chordX * chordX + chordY * chordY)
    val reach = sqrt(max(0.0, arc * arc - (chordX * chordX + chordY * chordY) / 4.0))
    val centerX = (joint.x + meet) / 2 + chordY / chordDistance * reach
    val centerY = (joint.y + meet) / 2 - chordX / chordDistance * reach
    return LiquidGlassOctant(
        n = n,
        joint = Offset(joint.x.toFloat(), joint.y.toFloat()),
        center = Offset(centerX.toFloat(), centerY.toFloat()),
        radius = arc,
    )
}

/**
 * Dart 的 `_cornerCurve`: 一个角的十二条参数 —— 沿边八分弧的 (n, 接点, 半径),
 * 沿侧八分弧的 (n, 接点, 半径), 再是两条弧的圆心。
 */
private fun cornerCurve(halfWidth: Double, halfHeight: Double, radius: Double): FloatArray {
    val along = superellipseOctant(halfWidth, radius)
    val across = superellipseOctant(halfHeight, radius)
    return floatArrayOf(
        along.n.toFloat(),
        along.joint.x,
        along.joint.y,
        along.radius.toFloat(),
        across.n.toFloat(),
        across.joint.x,
        across.joint.y,
        across.radius.toFloat(),
        along.center.x,
        along.center.y,
        across.center.x,
        across.center.y,
    )
}

/**
 * Dart 的 `liquidGlassCornerCurves`: 四个角各十二条参数, 合计 48 个 float,
 * 即着色器的 `uCurves[12]`。
 */
internal fun liquidGlassCornerCurves(size: Size, radii: LiquidGlassCornerRadii): FloatArray {
    val halfWidth = size.width / 2.0
    val halfHeight = size.height / 2.0
    return floatArrayOf(
        *cornerCurve(halfWidth, halfHeight, radii.topLeft.toDouble()),
        *cornerCurve(halfWidth, halfHeight, radii.topRight.toDouble()),
        *cornerCurve(halfWidth, halfHeight, radii.bottomRight.toDouble()),
        *cornerCurve(halfWidth, halfHeight, radii.bottomLeft.toDouble()),
    )
}

/**
 * Dart 的 `liquidGlassProfile`: 沿轮廓向中心投射 64 条射线, 得到轮廓到中心的距离,
 * 即着色器的 `uProfile[16]`。
 *
 * Dart 的 `Path.computeMetrics()` 逐条轮廓返回一个 metric, 这里是等价的:
 * `android.graphics.PathMeasure` 同样按轮廓度量, 用 `nextContour()` 逐条推进。
 * 两者同源于 Skia 的 contour measure, 弧长参数化一致, 因此每个轮廓上按
 * `length * index / samples` 取点与 Dart 的 `metric.getTangentForOffset(...)!.position` 等价。
 *
 * @param outline 轮廓路径
 * @param center 轮廓的包围盒中心 (Flutter 里是 `rect.size.center(Offset.zero)`)
 */
internal fun liquidGlassProfile(outline: Path, center: Offset): FloatArray {
    val step = 2.0 * PI / LIQUID_GLASS_PROFILE_TURNS
    val profile = FloatArray(LIQUID_GLASS_PROFILE_TURNS)
    val centerX = center.x.toDouble()
    val centerY = center.y.toDouble()

    fun cross(from: Vec2, to: Vec2) {
        val start = from.direction
        var span = to.direction - start
        if (span > PI) {
            span -= 2.0 * PI
        } else if (span <= -PI) {
            span += 2.0 * PI
        }
        val edgeX = to.x - from.x
        val edgeY = to.y - from.y
        val first = ceil(min(start, start + span) / step).toInt()
        val last = floor(max(start, start + span) / step).toInt()
        for (turn in first..last) {
            val rayX = cos(turn * step)
            val rayY = sin(turn * step)
            val facing = rayX * edgeY - rayY * edgeX
            // Dart 的 `%` 恒为非负, Kotlin 的 `%` 随被除数符号, 故取 floorMod。
            val slot = Math.floorMod(turn, LIQUID_GLASS_PROFILE_TURNS)
            profile[slot] = if (abs(facing) < 1e-9) {
                max(from.distance, to.distance).toFloat()
            } else {
                ((from.x * edgeY - from.y * edgeX) / facing).toFloat()
            }
        }
    }

    val measure = android.graphics.PathMeasure(outline.toAndroidPath(), false)
    do {
        val length = measure.length.toDouble()
        val samples = max(LIQUID_GLASS_PROFILE_TURNS * 4, ceil(length).toInt())
        val position = FloatArray(2)
        val tangent = FloatArray(2)

        fun pointAt(index: Int): Vec2 {
            val distance = (length * index / samples).toFloat()
            measure.getPosTan(distance, position, tangent)
            return Vec2(position[0].toDouble() - centerX, position[1].toDouble() - centerY)
        }

        var previous = pointAt(0)
        for (index in 1..samples) {
            val next = pointAt(index)
            cross(previous, next)
            previous = next
        }
    } while (measure.nextContour())
    return profile
}

/**
 * Compose 的 `Path.asAndroidPath()` 在源码里是 `DeprecationLevel.HIDDEN`, Kotlin 侧不可见;
 * `AndroidPath.internalPath` 是公开的等价物。
 */
internal fun Path.toAndroidPath(): android.graphics.Path {
    val android = this as? AndroidPath
    checkNotNull(android) { "轮廓路径必须是 AndroidPath 才能度量" }
    return android.internalPath
}

/** Dart 的 `_shineAround`: 沿圆角周边的高光角度表。 */
private val SHINE_AROUND = listOf(
    Triple(1.53, 0.0, 0.68),
    Triple(1.0, 0.0, 0.48),
    Triple(0.3, 0.3, 0.38),
    Triple(0.0, 1.0, 0.17),
    Triple(0.0, 1.5, 0.07),
    Triple(0.0, 2.0, 0.03),
)

/** Dart 的 `_shineBands`: 由深及宽的高光环带。 */
private val SHINE_BANDS = listOf(
    Triple(0.0, 1.0 / 3, 1.0),
    Triple(1.0 / 3, 1.0 / 3, 0.7),
    Triple(2.0 / 3, 1.0 / 3, 0.47),
    Triple(1.0, 2.0 / 3, 0.18),
    Triple(5.0 / 3, 2.0 / 3, 0.12),
    Triple(7.0 / 3, 2.0 / 3, 0.085),
    Triple(3.0, 2.0 / 3, 0.065),
    Triple(11.0 / 3, 2.0 / 3, 0.045),
    Triple(13.0 / 3, 2.0 / 3, 0.035),
)

/** Dart 的 `LiquidGlassRim._roundOf`: 圆角半径不超过短边的一半。 */
internal fun liquidGlassRoundOf(size: Size, radius: Float): Float =
    min(max(0.0f, radius), size.minDimension / 2f)

/**
 * Dart 的 `LiquidGlassRim._turns`: 从 +x 方向起, 绕到 +y 方向为止的高光转折表,
 * 每项是 (转折角, 亮度)。
 */
internal fun liquidGlassShineTurns(size: Size, round: Float): List<Pair<Double, Double>> {
    val halfWidth = size.width / 2.0
    val halfHeight = size.height / 2.0
    val turns = mutableListOf(0.0 to 1.0)
    for ((along, down, lit) in SHINE_AROUND) {
        val turn = atan2(
            halfWidth - min(along * round, halfWidth),
            halfHeight - min(down * round, halfHeight),
        )
        turns.add(max(turns.last().first, turn) to lit)
    }
    turns.add(PI / 2 to 0.0)
    return turns
}

/**
 * Dart 的 `LiquidGlassRim._uniforms`: 打包成 `uShineTurns` / `uShineLit` /
 * `uShineEnds` / `uShineShares` 四组, 合计 40 个 float。
 *
 * @param shine 高光强度
 * @param radius 圆角半径
 */
internal fun liquidGlassShineUniforms(size: Size, shine: Float, radius: Float): FloatArray {
    val round = liquidGlassRoundOf(size, radius)
    val turns = liquidGlassShineTurns(size, round)
    val ends = SHINE_BANDS.map { (depth, width, _) -> depth + width }
    val values = ArrayList<Float>(turns.size * 2 + LIQUID_GLASS_SHINE_SLOTS * 2)
    turns.forEach { (turn, _) -> values.add(turn.toFloat()) }
    turns.forEach { (_, lit) -> values.add((shine * lit).toFloat()) }
    for (index in 0 until LIQUID_GLASS_SHINE_SLOTS) {
        values.add(ends[min(index, ends.size - 1)].toFloat())
    }
    for (index in 0 until LIQUID_GLASS_SHINE_SLOTS) {
        values.add(if (index < SHINE_BANDS.size) SHINE_BANDS[index].third.toFloat() else 0f)
    }
    return values.toFloatArray()
}

/**
 * Dart 的 `LiquidGlassGoo._uniforms`: 打包成 `uPills` / `uGoo` / `uPillCurves` /
 * `uPillCenters` 四组, 合计 68 个 float。
 *
 * @param pills 每行一颗药丸, 按 Flutter `GooSurface` 的布局
 * @param blends 相邻药丸之间的融合度
 * @param firstRadius 首行是圆角矩形时的圆角半径
 */
internal fun liquidGlassGooUniforms(
    pills: List<Rect>,
    blends: List<Double>,
    firstRadius: Double?,
): FloatArray {
    val octants = (0 until LIQUID_GLASS_GOO_PILLS).map { index ->
        if (index < pills.size) {
            superellipseOctant(pills[index].maxDimension / 2.0, pills[index].minDimension / 2.0)
        } else {
            NO_OCTANT
        }
    }
    val values = ArrayList<Float>(LIQUID_GLASS_GOO_PILLS * 10 + 8)
    for (index in 0 until LIQUID_GLASS_GOO_PILLS) {
        if (index < pills.size) {
            values.add(pills[index].center.x)
            values.add(pills[index].center.y)
            values.add(pills[index].width / 2f)
            values.add(pills[index].height / 2f)
        } else {
            repeat(4) { values.add(0f) }
        }
    }
    values.add(pills.size.toFloat())
    for (index in 1 until LIQUID_GLASS_GOO_PILLS) {
        values.add(if (index < pills.size) blends[index - 1].toFloat() else 0f)
    }
    values.add((firstRadius ?: 0.0).toFloat())
    values.add(0f)
    octants.forEach { octant ->
        values.add(octant.n.toFloat())
        values.add(octant.joint.x)
        values.add(octant.joint.y)
        values.add(octant.radius.toFloat())
    }
    octants.forEach { octant ->
        values.add(octant.center.x)
        values.add(octant.center.y)
    }
    return values.toFloatArray()
}

/**
 * Dart 的 `_optics`: 饱和度与亮度合成出的 4x5 色彩矩阵。
 * 仅用于着色器不可用时的降级绘制 (Flutter 侧 `_frostFrom`), 着色器内部自带饱和度与亮度。
 */
internal fun liquidGlassOpticsMatrix(saturation: Double, brightness: Double): FloatArray {
    val red = 0.2126 * brightness
    val green = 0.7152 * brightness
    val blue = 0.0722 * brightness
    val rest = 1.0 - saturation
    val kept = saturation * brightness
    return floatArrayOf(
        (red * rest + kept).toFloat(), green.rest(rest), blue.rest(rest), 0f, 0f,
        red.rest(rest), (green * rest + kept).toFloat(), blue.rest(rest), 0f, 0f,
        red.rest(rest), green.rest(rest), (blue * rest + kept).toFloat(), 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun Double.rest(rest: Double): Float = (this * rest).toFloat()
