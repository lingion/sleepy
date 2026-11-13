// Copyright 2013 The Flutter Authors. All rights reserved.
// Kotlin adaptation Copyright 2026 The Sleepy Authors.
// Derived from Flutter rounded-superellipse geometry. The original BSD-3-Clause
// notice is retained in assets/third_party/notices/Flutter-BSD-3-Clause.txt.
// Source revision: adc901062556672b4138e18a4dc62a4be8f4b3c2.

package com.lingion.sleepy.ui.component.liquidglass

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 圆角超椭圆（rounded superellipse）路径构建器。
 *
 * 本文件逐行移植 Flutter 引擎的 `impeller/geometry/round_superellipse_param.cc`
 * 与 `impeller/geometry/rounding_radii.cc`，即 FLClash 锁定修订
 * `adc901062556672b4138e18a4dc62a4be8f4b3c2` 所使用的引擎实现。
 *
 * 对应的 Dart 侧代码是 `dart:ui` 的 `RSuperellipse`（`packages/flutter/lib/ui/geometry.dart`
 * 与 `packages/flutter/lib/ui/painting.dart`），FLClash 通过
 * `Canvas.drawRSuperellipse` / `Path.pushClipRSuperellipse` 消费它。
 *
 * 精度约定：引擎的 `Scalar` 是 `float`，因此本移植逐点复刻 C++ 的
 * 重载决议结果——`<cmath>` 为 `float` 实参提供 `float` 重载，
 * `std::pow(float,float)`/`std::sqrt(float)`/`std::asin(float)`/
 * `std::atan2(float,float)`/`std::tan(float)`/`std::exp(float)`/
 * `std::cos(float)`/`std::sin(float)` 全部返回 `float`；只有
 * `std::pow(float,int)` 与 `std::sqrt(int)`（整数字面量）返回
 * `double`。`Point::Rotate` 使用单精度 `std::cosf`/`std::sinf`，
 * `OctantContains` 使用单精度 `powf`。本移植用 `kotlin.math` 的
 * `Float` 重载逐点对应，只有 `R = (a - d - g) * sqrt(2)` 一处因整型
 * 字面量保留 `Double` 中间量。
 *
 * 引擎会为溢出的半径输出 NaN 坐标，本移植同样**不做**任何净化处理。
 */
object LiquidGlassSuperellipse {

    // ---------------------------------------------------------------------
    // 常量表
    // ---------------------------------------------------------------------

    /**
     * `ComputeNAndXj` 的查表数据。
     *
     * 第 0 列为 `n`，第 1 列为 `k_xJ`，其定义是 `1 / (1 - xJ / a)`。
     */
    private val kPrecomputedVariables = floatArrayOf(
        // ratio=2.00
        2.00000000f, 1.13276676f,
        // ratio=2.10
        2.18349805f, 1.20311921f,
        // ratio=2.20
        2.33888662f, 1.28698796f,
        // ratio=2.30
        2.48660575f, 1.36351941f,
        // ratio=2.40
        2.62226596f, 1.44717976f,
        // ratio=2.50
        2.75148990f, 1.53385819f,
        // ratio=3.00
        3.36298265f, 1.98288283f,
        // ratio=3.50
        4.08649929f, 2.23811846f,
        // ratio=4.00
        4.85481134f, 2.47563463f,
        // ratio=4.50
        5.62945551f, 2.72948597f,
        // ratio=5.00
        6.43023796f, 2.98020421f,
    )

    private const val kMinRatio = 2.00f
    private const val kFirstStepInverse = 10f // = 1 / 0.10
    private const val kFirstMaxRatio = 2.50f
    private const val kFirstNumRecords = 6
    private const val kSecondStepInverse = 2f // = 1 / 0.50
    private const val kSecondMaxRatio = 5.00f
    private const val kThirdNSlope = 1.559599389f
    private const val kThirdKxjSlope = 0.522807185f
    private const val kNumRecords = 11

    /**
     * `SuperellipseBezierFactors` 的查表数据，第 0 列为起始切线因子，
     * 第 1 列为结束切线因子。
     */
    private val kBezierFactors = floatArrayOf(
        // n=2.0
        0.01339448f, 0.05994973f,
        // n=3.0
        0.13664115f, 0.13592082f,
        // n=4.0
        0.24545546f, 0.14099516f,
        // n=5.0
        0.32353151f, 0.12808021f,
        // n=6.0
        0.39093068f, 0.11726264f,
        // n=7.0
        0.44847800f, 0.10808278f,
        // n=8.0
        0.49817452f, 0.10026175f,
        // n=9.0
        0.54105583f, 0.09344429f,
        // n=10.0
        0.57812578f, 0.08748984f,
        // n=11.0
        0.61050961f, 0.08224722f,
        // n=12.0
        0.63903989f, 0.07759639f,
        // n=13.0
        0.66416338f, 0.07346530f,
        // n=14.0
        0.68675338f, 0.06974996f,
        // n=15.0
        0.70678034f, 0.06529512f,
    )

    private const val kBezierNumRecords = 14
    private const val kBezierStep = 1.00f
    private const val kBezierMinN = 2.00f
    private const val kBezierMaxN = kBezierMinN + (kBezierNumRecords - 1) * kBezierStep

    /**
     * 曲角中点到边界框最近边的距离系数，等于 `1 - cos(pi/4)`。
     */
    const val kGapFactor = 0.29289321881f

    /**
     * 引擎 `ScalarNearlyEqual` 的默认容差。
     */
    const val kEhCloseEnough = 1e-3f

    // ---------------------------------------------------------------------
    // 基础几何类型（对应引擎的 TPoint / TSize / TRect / Matrix）
    // ---------------------------------------------------------------------

    /** 对应引擎的 `impeller::Point`。 */
    data class Vec(val x: Float, val y: Float) {
        operator fun plus(other: Vec) = Vec(x + other.x, y + other.y)

        operator fun minus(other: Vec) = Vec(x - other.x, y - other.y)

        operator fun times(other: Vec) = Vec(x * other.x, y * other.y)

        operator fun times(scale: Float) = Vec(x * scale, y * scale)

        operator fun div(other: Vec) = Vec(x / other.x, y / other.y)

        /** 对应引擎的 `Point::operator/(const TSize&)`。 */
        operator fun div(other: Size2) = Vec(x / other.width, y / other.height)

        operator fun div(scale: Float) = Vec(x / scale, y / scale)

        /** 对应引擎的 `Point::Abs`。 */
        fun abs() = Vec(abs(x), abs(y))

        /**
         * 对应引擎的 `Point::GetDistanceSquared`：中间量用 `double`，结果收敛到 `float`。
         */
        fun distanceSquared(other: Vec): Float {
            val dx = (other.x - x).toDouble()
            val dy = (other.y - y).toDouble()
            return (dx * dx + dy * dy).toFloat()
        }

        /** 对应引擎的 `Point::GetLength`：`sqrt(GetDistanceSquared({}))` 为单精度。 */
        fun length(): Float = sqrt(distanceSquared(Vec(0f, 0f)))

        /** 对应引擎的 `Point::Normalize`：长度为 0 时返回 `{1, 0}`。 */
        fun normalize(): Vec {
            val length = length()
            if (length == 0f) return Vec(1f, 0f)
            return Vec(x / length, y / length)
        }

        fun cross(other: Vec): Float = (x * other.y) - (y * other.x)

        fun dot(other: Vec): Float = (x * other.x) + (y * other.y)

        /**
         * 对应引擎的 `Point::Rotate`：使用单精度的 `cosf`/`sinf`。
         */
        fun rotate(radians: Float): Vec {
            val cosA = cos(radians)
            val sinA = sin(radians)
            return Vec(x * cosA - y * sinA, x * sinA + y * cosA)
        }

        /**
         * 对应引擎的 `Point::AngleTo`：`atan2(Cross, Dot)` 为单精度。
         */
        fun angleTo(other: Vec): Float = atan2(cross(other), dot(other))
    }

    /** 对应引擎的 `impeller::Size`。 */
    data class Size2(val width: Float, val height: Float) {
        constructor(dimension: Float) : this(dimension, dimension)

        /** 对应引擎 `Size::width` / `Size::height` 的分量访问。 */
        val x: Float get() = width

        val y: Float get() = height

        operator fun times(scale: Float) = Size2(width * scale, height * scale)

        operator fun div(other: Size2) = Size2(width / other.width, height / other.height)

        operator fun div(scale: Float) = Size2(width / scale, height / scale)

        fun minDimension(): Float = min(width, height)

        fun abs() = Size2(abs(width), abs(height))

        /** 对应 `dart:ui` 的 `Size.inflate`：`max(0, dimension + delta)`。 */
        fun inflate(delta: Float): Size2 =
            Size2(max(0f, width + delta), max(0f, height + delta))

        /** 对应 `dart:ui` 的 `Size.deflate`：`inflate(-delta)`。 */
        fun deflate(delta: Float): Size2 = inflate(-delta)

        /** 对应引擎的 `Size::IsEmpty`：`!(width > 0 && height > 0)`。 */
        fun isEmpty(): Boolean = !(width > 0f && height > 0f)

        fun isFinite(): Boolean = width.isFinite() && height.isFinite()
    }

    /** 对应引擎的 `impeller::Rect`。 */
    data class Rect2(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        /** 对应引擎的 `Rect::IsEmpty`：`!(left < right && top < bottom)`。 */
        fun isEmpty(): Boolean = !(left < right && top < bottom)

        fun isFinite(): Boolean =
            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()

        fun width(): Float = right - left

        fun height(): Float = bottom - top

        fun size() = Size2(width(), height())

        fun center() = Vec(
            left * 0.5f + right * 0.5f,
            top * 0.5f + bottom * 0.5f,
        )

        fun leftTop() = Vec(left, top)

        fun rightTop() = Vec(right, top)

        fun leftBottom() = Vec(left, bottom)

        fun rightBottom() = Vec(right, bottom)

        /**
         * 对应引擎的 `Rect::GetPositive`：只对空矩形做规范化。
         */
        fun getPositive(): Rect2 =
            if (!isEmpty()) {
                this
            } else {
                Rect2(
                    min(left, right),
                    min(top, bottom),
                    max(left, right),
                    max(top, bottom),
                )
            }

        fun shift(dx: Float, dy: Float) = Rect2(left + dx, top + dy, right + dx, bottom + dy)

        /**
         * 对应引擎 `dart:ui` 的 `_RRectLike.inflate`：
         * 边移动 `delta`，八个角半径同时 `math.max(0, r + delta)`。
         */
        fun inflate(delta: Float): Rect2 = Rect2(
            left - delta, top - delta, right + delta, bottom + delta,
        )

        /** 对应 `dart:ui` 的 `_RRectLike.deflate`：`inflate(-delta)`。 */
        fun deflate(delta: Float): Rect2 = inflate(-delta)
    }

    /** 对应引擎的 `impeller::Matrix`（列主序 4x4）。 */
    internal class Mat4(val m: FloatArray) {
        operator fun times(other: Mat4): Mat4 {
            val a = m
            val o = other.m
            return Mat4(
                floatArrayOf(
                    a[0] * o[0] + a[4] * o[1] + a[8] * o[2] + a[12] * o[3],
                    a[1] * o[0] + a[5] * o[1] + a[9] * o[2] + a[13] * o[3],
                    a[2] * o[0] + a[6] * o[1] + a[10] * o[2] + a[14] * o[3],
                    a[3] * o[0] + a[7] * o[1] + a[11] * o[2] + a[15] * o[3],
                    a[0] * o[4] + a[4] * o[5] + a[8] * o[6] + a[12] * o[7],
                    a[1] * o[4] + a[5] * o[5] + a[9] * o[6] + a[13] * o[7],
                    a[2] * o[4] + a[6] * o[5] + a[10] * o[6] + a[14] * o[7],
                    a[3] * o[4] + a[7] * o[5] + a[11] * o[6] + a[15] * o[7],
                    a[0] * o[8] + a[4] * o[9] + a[8] * o[10] + a[12] * o[11],
                    a[1] * o[8] + a[5] * o[9] + a[9] * o[10] + a[13] * o[11],
                    a[2] * o[8] + a[6] * o[9] + a[10] * o[10] + a[14] * o[11],
                    a[3] * o[8] + a[7] * o[9] + a[11] * o[10] + a[15] * o[11],
                    a[0] * o[12] + a[4] * o[13] + a[8] * o[14] + a[12] * o[15],
                    a[1] * o[12] + a[5] * o[13] + a[9] * o[14] + a[13] * o[15],
                    a[2] * o[12] + a[6] * o[13] + a[10] * o[14] + a[14] * o[15],
                    a[3] * o[12] + a[7] * o[13] + a[11] * o[14] + a[15] * o[15],
                ),
            )
        }

        /** 对应引擎的 `Matrix::operator*(Point)`。 */
        operator fun times(v: Vec): Vec {
            var w = v.x * m[3] + v.y * m[7] + m[15]
            val result = Vec(v.x * m[0] + v.y * m[4] + m[12], v.x * m[1] + v.y * m[5] + m[13])
            // 引擎的行为：`w` 非零时取倒数，`w` 为零时保持原值（结果被清零）。
            if (w != 0f) {
                w = 1f / w
            }
            return result * w
        }

        companion object {
            fun makeTranslation(t: Vec) = Mat4(
                floatArrayOf(
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    t.x, t.y, 0f, 1f,
                ),
            )

            fun makeTranslateScale(s: Vec, t: Vec) = Mat4(
                floatArrayOf(
                    s.x, 0f, 0f, 0f,
                    0f, s.y, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    t.x, t.y, 0f, 1f,
                ),
            )

            /** 交换 x 与 y 的矩阵。 */
            val kFlip = Mat4(
                floatArrayOf(
                    0f, 1f, 0f, 0f,
                    1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f,
                ),
            )
        }
    }

    // ---------------------------------------------------------------------
    // 圆角半径（对应引擎的 impeller::RoundingRadii）
    // ---------------------------------------------------------------------

    /** 对应引擎的 `impeller::RoundingRadii`。 */
    data class Radii(
        val topLeft: Size2,
        val topRight: Size2,
        val bottomRight: Size2,
        val bottomLeft: Size2,
    ) {
        fun isFinite(): Boolean =
            topLeft.isFinite() && topRight.isFinite() &&
                bottomRight.isFinite() && bottomLeft.isFinite()

        fun areAllCornersEmpty(): Boolean =
            topLeft.isEmpty() && topRight.isEmpty() &&
                bottomRight.isEmpty() && bottomLeft.isEmpty()

        /** 对应引擎的 `RoundingRadii::AreAllCornersSame`。 */
        fun areAllCornersSame(tolerance: Float = kEhCloseEnough): Boolean =
            scalarNearlyEqual(topLeft.width, topRight.width, tolerance) &&
                scalarNearlyEqual(topLeft.width, bottomRight.width, tolerance) &&
                scalarNearlyEqual(topLeft.width, bottomLeft.width, tolerance) &&
                scalarNearlyEqual(topLeft.height, topRight.height, tolerance) &&
                scalarNearlyEqual(topLeft.height, bottomRight.height, tolerance) &&
                scalarNearlyEqual(topLeft.height, bottomLeft.height, tolerance)

        operator fun times(scale: Float) = Radii(
            topLeft * scale,
            topRight * scale,
            bottomRight * scale,
            bottomLeft * scale,
        )

        /**
         * 对应引擎 `dart:ui` 的 `_RRectLike.inflate`：
         * 八个角半径同时 `math.max(0, r + delta)`。
         */
        fun inflate(delta: Float): Radii = Radii(
            topLeft = topLeft.inflate(delta),
            topRight = topRight.inflate(delta),
            bottomRight = bottomRight.inflate(delta),
            bottomLeft = bottomLeft.inflate(delta),
        )

        /** 对应 `dart:ui` 的 `_RRectLike.deflate`：`inflate(-delta)`。 */
        fun deflate(delta: Float): Radii = inflate(-delta)

        companion object {
            fun makeRadius(radius: Float) = Radii(
                Size2(radius, radius),
                Size2(radius, radius),
                Size2(radius, radius),
                Size2(radius, radius),
            )

            fun makeRadii(radii: Size2) = Radii(radii, radii, radii, radii)

            /**
             * 对应引擎的 `RoundingRadii::Scaled`：在给定矩形内按比例缩小半径，
             * 保证相邻两角的半径之和不超过对应边长。
             */
            fun scaled(bounds: Rect2, radii: Radii): Radii {
                val positive = bounds.getPositive()
                if (positive.isEmpty() || radii.areAllCornersEmpty() || !radii.isFinite()) {
                    return Radii(Size2(0f, 0f), Size2(0f, 0f), Size2(0f, 0f), Size2(0f, 0f))
                }
                var working = Radii(
                    normalizeEmptyToZero(radii.topLeft),
                    normalizeEmptyToZero(radii.topRight),
                    normalizeEmptyToZero(radii.bottomRight),
                    normalizeEmptyToZero(radii.bottomLeft),
                )
                val size = positive.size()
                var scale = 1f
                scale = adjustScale(working.topLeft.width, working.topRight.width, size.width, scale)
                scale = adjustScale(working.bottomLeft.width, working.bottomRight.width, size.width, scale)
                scale = adjustScale(working.topLeft.height, working.bottomLeft.height, size.height, scale)
                scale = adjustScale(working.topRight.height, working.bottomRight.height, size.height, scale)
                if (scale < 1f) {
                    working = working * scale
                }
                return working
            }

            private fun normalizeEmptyToZero(radii: Size2): Size2 =
                if (radii.isEmpty()) Size2(0f, 0f) else radii

            private fun adjustScale(radius1: Float, radius2: Float, dimension: Float, scale: Float): Float {
                if (radius1 + radius2 > dimension) {
                    return min(scale, dimension / (radius1 + radius2))
                }
                return scale
            }
        }
    }

    internal fun scalarNearlyEqual(a: Float, b: Float, tolerance: Float = kEhCloseEnough): Boolean =
        abs(a - b) <= tolerance

    // ---------------------------------------------------------------------
    // 超椭圆参数（对应引擎的 RoundSuperellipseParam）
    // ---------------------------------------------------------------------

    /** 对应引擎的 `RoundSuperellipseParam::Octant`。 */
    data class Octant(
        val offset: Vec,
        val seA: Float,
        val seN: Float,
        val seMaxTheta: Float,
        val circleStart: Vec,
        val circleCenter: Vec,
        val circleMaxAngle: Float,
    )

    /** 对应引擎的 `RoundSuperellipseParam::Quadrant`。 */
    data class Quadrant(
        val offset: Vec,
        val signedScale: Vec,
        val top: Octant,
        val right: Octant,
    )

    /** 对应引擎的 `RoundSuperellipseParam`。 */
    data class SuperellipseParam(
        val topRight: Quadrant,
        val bottomRight: Quadrant,
        val bottomLeft: Quadrant,
        val topLeft: Quadrant,
        val allCornersSame: Boolean,
    )

    // ---------------------------------------------------------------------
    // 路径指令
    // ---------------------------------------------------------------------

    /** 路径的一条指令，与引擎的 `PathReceiver` 一一对应。 */
    sealed interface PathOp {
        data class Move(val x: Float, val y: Float) : PathOp

        data class Line(val x: Float, val y: Float) : PathOp

        data class Cubic(
            val c1x: Float,
            val c1y: Float,
            val c2x: Float,
            val c2y: Float,
            val x: Float,
            val y: Float,
        ) : PathOp

        data object Close : PathOp
    }

    // ---------------------------------------------------------------------
    // 入口
    // ---------------------------------------------------------------------

    /**
     * 对应引擎的 `RoundSuperellipse::MakeRectRadii` + `RoundSuperellipseParam::MakeBoundsRadii`，
     * 即真实渲染路径：先按矩形缩放半径，再用缩放后的半径构建参数。
     */
    fun buildPath(bounds: Rect2, radii: Radii): List<PathOp> {
        val positive = if (!bounds.isFinite()) Rect2(0f, 0f, 0f, 0f) else bounds.getPositive()
        val scaled = Radii.scaled(positive, radii)
        return dispatch(makeBoundsRadii(positive, scaled))
    }

    /**
     * 对应引擎的 `RoundSuperellipseParam::MakeBoundsRadii` + `Dispatch`，
     * 即 `RoundSuperellipse::Contains` 使用的未缩放半径路径。
     */
    fun buildUnscaledPath(bounds: Rect2, radii: Radii): List<PathOp> =
        dispatch(makeBoundsRadii(bounds.getPositive(), radii))

    /**
     * 对应引擎的 `RoundSuperellipseParam::Contains`。
     */
    fun contains(bounds: Rect2, radii: Radii, point: Vec): Boolean {
        val param = makeBoundsRadii(bounds.getPositive(), radii)
        return if (param.allCornersSame) {
            cornerContains(param.topRight, point, checkQuadrant = false)
        } else {
            cornerContains(param.topRight, point) &&
                cornerContains(param.bottomRight, point) &&
                cornerContains(param.bottomLeft, point) &&
                cornerContains(param.topLeft, point)
        }
    }

    // ---------------------------------------------------------------------
    // 参数构建
    // ---------------------------------------------------------------------

    /** 对应引擎的 `RoundSuperellipseParam::MakeBoundsRadii`。 */
    fun makeBoundsRadii(bounds: Rect2, radii: Radii): SuperellipseParam {
        if (radii.areAllCornersSame() && !radii.topLeft.isEmpty()) {
            return SuperellipseParam(
                topRight = computeQuadrant(bounds.center(), bounds.rightTop(), radii.topRight, Vec(-1f, 1f)),
                bottomRight = computeQuadrant(bounds.center(), bounds.rightTop(), radii.topRight, Vec(-1f, 1f)),
                bottomLeft = computeQuadrant(bounds.center(), bounds.rightTop(), radii.topRight, Vec(-1f, 1f)),
                topLeft = computeQuadrant(bounds.center(), bounds.rightTop(), radii.topRight, Vec(-1f, 1f)),
                allCornersSame = true,
            )
        }
        val topSplit = split(bounds.left, bounds.right, radii.topLeft.width, radii.topRight.width)
        val rightSplit = split(bounds.top, bounds.bottom, radii.topRight.height, radii.bottomRight.height)
        val bottomSplit = split(bounds.left, bounds.right, radii.bottomLeft.width, radii.bottomRight.width)
        val leftSplit = split(bounds.top, bounds.bottom, radii.topLeft.height, radii.bottomLeft.height)

        return SuperellipseParam(
            topRight = computeQuadrant(Vec(topSplit, rightSplit), bounds.rightTop(), radii.topRight, Vec(1f, -1f)),
            bottomRight = computeQuadrant(Vec(bottomSplit, rightSplit), bounds.rightBottom(), radii.bottomRight, Vec(1f, 1f)),
            bottomLeft = computeQuadrant(Vec(bottomSplit, leftSplit), bounds.leftBottom(), radii.bottomLeft, Vec(-1f, 1f)),
            topLeft = computeQuadrant(Vec(topSplit, leftSplit), bounds.leftTop(), radii.topLeft, Vec(-1f, -1f)),
            allCornersSame = false,
        )
    }

    /** 对应引擎的 `Split`。 */
    private fun split(left: Float, right: Float, ratioLeft: Float, ratioRight: Float): Float {
        if (ratioLeft == 0f && ratioRight == 0f) {
            return (left + right) / 2f
        }
        return (left * ratioRight + right * ratioLeft) / (ratioLeft + ratioRight)
    }

    /**
     * 对应引擎的 `ReplanceNaNWithDefault`。
     *
     * 引擎签名是 `ReplanceNaNWithDefault(Point in, Size default_size)`，
     * 而 `ComputeQuadrant` 传入的 `sign` 也是一个 `Size`，因此这里用
     * [Vec] 承载两个分量：`x` 对应 `Size::width`，`y` 对应 `Size::height`。
     */
    private fun replanceNaNWithDefault(input: Vec, defaultSize: Vec): Vec = Vec(
        if (input.x.isNaN()) defaultSize.x else input.x,
        if (input.y.isNaN()) defaultSize.y else input.y,
    )

    /** 对应引擎的 `Flip`。 */
    private fun flip(a: Vec) = Vec(a.y, a.x)

    /** 对应引擎的 `ComputeNAndXj`。 */
    private fun computeNAndXj(ratio: Float): FloatArray {
        if (ratio > kSecondMaxRatio) {
            val n = kThirdNSlope * (ratio - kSecondMaxRatio) +
                kPrecomputedVariables[(kNumRecords - 1) * 2]
            val kXj = kThirdKxjSlope * (ratio - kSecondMaxRatio) +
                kPrecomputedVariables[(kNumRecords - 1) * 2 + 1]
            return floatArrayOf(n, 1f - 1f / kXj)
        }
        val clamped = clamp(ratio, kMinRatio, kSecondMaxRatio)
        val steps = if (clamped < kFirstMaxRatio) {
            (clamped - kMinRatio) * kFirstStepInverse
        } else {
            (clamped - kFirstMaxRatio) * kSecondStepInverse + kFirstNumRecords - 1
        }
        val left = clamp(floor(steps).toInt().toFloat(), 0f, (kNumRecords - 2).toFloat()).toInt()
        val frac = steps - left

        val n = (1f - frac) * kPrecomputedVariables[left * 2] +
            frac * kPrecomputedVariables[(left + 1) * 2]
        val kXj = (1f - frac) * kPrecomputedVariables[left * 2 + 1] +
            frac * kPrecomputedVariables[(left + 1) * 2 + 1]
        return floatArrayOf(n, 1f - 1f / kXj)
    }

    /**
     * 对应 C++ 的 `std::clamp`：注意 NaN 输入会返回下界。
     */
    private fun clamp(value: Float, low: Float, high: Float): Float = when {
        value < low -> low
        value > high -> high
        else -> value
    }

    /**
     * 对应引擎的 `FindCircleCenter`。
     *
     * 引擎在 `-O2` 构建下把 `r*r - distanceAm*distanceAm` 缩缩约为一次
     * 融合乘-减（FMA），复现 `golden_labelled.txt`；`-ffp-contract=off`
     * 构建给出未缩缩约的结果，对应 `golden_labelled_nofma.txt`。
     *
     * 此处用 `java.lang.Math.fma(-d, d, r*r)` 对准引擎的默认 `-O2` 行为。
     * `Math.fma` 是 API 24+，minSdk 26 的应用安全调用。
     */
    private fun findCircleCenter(a: Vec, b: Vec, r: Float): Vec {
        val aToB = b - a
        val m = (a + b) * 0.5f
        val cToM = Vec(-aToB.y, aToB.x)
        val distanceAm = aToB.length() / 2f
        val distanceCm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            sqrt(Math.fma(-distanceAm, distanceAm, r * r))
        } else {
            sqrt(r * r - distanceAm * distanceAm)
        }
        return m - cToM.normalize() * distanceCm
    }

    /** 对应引擎的 `ComputeOctant`。 */
    private fun computeOctant(center: Vec, a: Float, radius: Float): Octant {
        if (radius <= 0f) {
            return Octant(
                offset = center,
                seA = a,
                seN = 0f,
                seMaxTheta = 0f,
                circleStart = Vec(a, a),
                circleCenter = Vec(0f, 0f),
                circleMaxAngle = 0f,
            )
        }
        val ratio = a * 2f / radius
        val g = kGapFactor * radius

        val precomputed = computeNAndXj(ratio)
        val n = precomputed[0]
        val xJ = precomputed[1] * a
        // 引擎此处整条表达式都是 `float`：`pow(float,float)` 返回 `float`，
        // `1 / n` 的整型字面量被提升为 `float` 后仍走 `pow(float,float)` 重载。
        val yJ = (1f - precomputed[1].pow(n)).pow(1f / n) * a
        val maxTheta = asin(precomputed[1].pow(n / 2f))
        val tanPhiJ = (xJ / yJ).pow(n - 1f)
        val d = (xJ - tanPhiJ * yJ) / (1f - tanPhiJ)
        // `sqrt(2)` 是整型字面量，引擎在这里提升到 `double` 后只收敛一次。
        val r = ((a - d - g) * sqrt(2.0)).toFloat()

        val pointM = Vec(a - g, a - g)
        val pointJ = Vec(xJ, yJ)
        val circleCenter = if (radius == 0f) pointM else findCircleCenter(pointJ, pointM, r)
        val circleMaxAngle = if (radius == 0f) {
            0f
        } else {
            (pointM - circleCenter).angleTo(pointJ - circleCenter)
        }

        return Octant(
            offset = center,
            seA = a,
            seN = n,
            seMaxTheta = maxTheta,
            circleStart = pointJ,
            circleCenter = circleCenter,
            circleMaxAngle = circleMaxAngle,
        )
    }

    /** 对应引擎的 `ComputeQuadrant`。 */
    private fun computeQuadrant(center: Vec, corner: Vec, inRadii: Size2, sign: Vec): Quadrant {
        val cornerVector = corner - center
        val radii = inRadii.abs()

        val normRadius = radii.minDimension()
        val forwardScale = if (normRadius == 0f) Size2(1f, 1f) else radii / normRadius
        val normHalfSize = cornerVector.abs() / forwardScale
        val signedScale = replanceNaNWithDefault(cornerVector / normHalfSize, sign)

        val c = normHalfSize.x - normHalfSize.y

        return Quadrant(
            offset = center,
            signedScale = signedScale,
            top = computeOctant(Vec(0f, -c), normHalfSize.x, normRadius),
            right = computeOctant(Vec(c, 0f), normHalfSize.y, normRadius),
        )
    }

    // ---------------------------------------------------------------------
    // 命中测试
    // ---------------------------------------------------------------------

    /** 对应引擎的 `OctantContains`。 */
    private fun octantContains(param: Octant, p: Vec): Boolean {
        if (p.x < 0f || p.y < 0f || p.y < p.x) {
            return true
        }
        if (p.x <= param.circleStart.x) {
            val pSe = p / param.seA
            return pSe.x.pow(param.seN) + pSe.y.pow(param.seN) <= 1f
        }
        val circleRadius = param.circleStart.distanceSquared(param.circleCenter)
        val pCircle = p - param.circleCenter
        return pCircle.distanceSquared(Vec(0f, 0f)) < circleRadius
    }

    /** 对应引擎的 `CornerContains`。 */
    private fun cornerContains(param: Quadrant, p: Vec, checkQuadrant: Boolean = true): Boolean {
        var normPoint = (p - param.offset) / param.signedScale
        if (checkQuadrant) {
            if (normPoint.x < 0f || normPoint.y < 0f) {
                return true
            }
        } else {
            normPoint = normPoint.abs()
        }
        if (param.top.seN < 2f || param.right.seN < 2f) {
            val xDelta = param.right.offset.x + param.right.seA - normPoint.x
            val yDelta = param.top.offset.y + param.top.seA - normPoint.y
            val xWithin = xDelta > 0f || (xDelta == 0f && param.signedScale.x < 0f)
            val yWithin = yDelta > 0f || (yDelta == 0f && param.signedScale.y < 0f)
            return xWithin && yWithin
        }
        return octantContains(param.top, normPoint - param.top.offset) &&
            octantContains(param.right, flip(normPoint - param.right.offset))
    }

    // ---------------------------------------------------------------------
    // 路径输出
    // ---------------------------------------------------------------------

    /** 对应引擎的 `RoundSuperellipseParam::Dispatch`。 */
    private fun dispatch(param: SuperellipseParam): List<PathOp> {
        val ops = mutableListOf<PathOp>()
        val start = param.topRight.offset +
            param.topRight.signedScale *
            (param.topRight.top.offset + Vec(0f, param.topRight.top.seA))
        ops += PathOp.Move(start.x, start.y)

        if (param.allCornersSame) {
            addQuadrant(ops, param.topRight, reverse = false, scaleSign = Vec(1f, 1f))
            addQuadrant(ops, param.topRight, reverse = true, scaleSign = Vec(1f, -1f))
            addQuadrant(ops, param.topRight, reverse = false, scaleSign = Vec(-1f, -1f))
            addQuadrant(ops, param.topRight, reverse = true, scaleSign = Vec(-1f, 1f))
        } else {
            addQuadrant(ops, param.topRight, reverse = false, scaleSign = Vec(1f, 1f))
            addQuadrant(ops, param.bottomRight, reverse = true, scaleSign = Vec(1f, 1f))
            addQuadrant(ops, param.bottomLeft, reverse = false, scaleSign = Vec(1f, 1f))
            addQuadrant(ops, param.topLeft, reverse = true, scaleSign = Vec(1f, 1f))
        }

        ops += PathOp.Line(start.x, start.y)
        ops += PathOp.Close
        return ops
    }

    /** 对应引擎的 `RoundSuperellipseBuilder::AddQuadrant`。 */
    private fun addQuadrant(
        ops: MutableList<PathOp>,
        param: Quadrant,
        reverse: Boolean,
        scaleSign: Vec,
    ) {
        val transform = Mat4.makeTranslateScale(param.signedScale * scaleSign, param.offset)
        if (param.top.seN < 2f || param.right.seN < 2f) {
            ops += PathOp.Line(
                (transform * (param.top.offset + Vec(param.top.seA, param.top.seA))).x,
                (transform * (param.top.offset + Vec(param.top.seA, param.top.seA))).y,
            )
            if (!reverse) {
                val p = transform * (param.right.offset + Vec(param.right.seA, 0f))
                ops += PathOp.Line(p.x, p.y)
            } else {
                val p = transform * (param.top.offset + Vec(0f, param.top.seA))
                ops += PathOp.Line(p.x, p.y)
            }
            return
        }
        if (!reverse) {
            addOctant(ops, param.top, reverse = false, flip = false, externalTransform = transform)
            addOctant(ops, param.right, reverse = true, flip = true, externalTransform = transform)
        } else {
            addOctant(ops, param.right, reverse = false, flip = true, externalTransform = transform)
            addOctant(ops, param.top, reverse = true, flip = false, externalTransform = transform)
        }
    }

    /** 对应引擎的 `RoundSuperellipseBuilder::AddOctant`。 */
    private fun addOctant(
        ops: MutableList<PathOp>,
        param: Octant,
        reverse: Boolean,
        flip: Boolean,
        externalTransform: Mat4,
    ) {
        var transform = externalTransform * Mat4.makeTranslation(param.offset)
        if (flip) {
            transform = transform * Mat4.kFlip
        }

        val circlePoints = circularArcPoints(param)
        val sePoints = superellipseArcPoints(param)

        if (!reverse) {
            ops += PathOp.Cubic(
                (transform * sePoints[1]).x, (transform * sePoints[1]).y,
                (transform * sePoints[2]).x, (transform * sePoints[2]).y,
                (transform * sePoints[3]).x, (transform * sePoints[3]).y,
            )
            ops += PathOp.Cubic(
                (transform * circlePoints[1]).x, (transform * circlePoints[1]).y,
                (transform * circlePoints[2]).x, (transform * circlePoints[2]).y,
                (transform * circlePoints[3]).x, (transform * circlePoints[3]).y,
            )
        } else {
            ops += PathOp.Cubic(
                (transform * circlePoints[2]).x, (transform * circlePoints[2]).y,
                (transform * circlePoints[1]).x, (transform * circlePoints[1]).y,
                (transform * circlePoints[0]).x, (transform * circlePoints[0]).y,
            )
            ops += PathOp.Cubic(
                (transform * sePoints[2]).x, (transform * sePoints[2]).y,
                (transform * sePoints[1]).x, (transform * sePoints[1]).y,
                (transform * sePoints[0]).x, (transform * sePoints[0]).y,
            )
        }
    }

    /** 对应引擎的 `SuperellipseArcPoints`。 */
    private fun superellipseArcPoints(param: Octant): Array<Vec> {
        val start = Vec(0f, param.seA)
        val end = param.circleStart
        val startTangent = Vec(1f, 0f)
        val circleStartVector = param.circleStart - param.circleCenter
        val endTangent = Vec(-circleStartVector.y, circleStartVector.x).normalize()

        val factors = superellipseBezierFactors(param.seN)

        return arrayOf(
            start,
            start + startTangent * factors[0] * param.seA,
            end + endTangent * factors[1] * param.seA,
            end,
        )
    }

    /** 对应引擎的 `CircularArcPoints`。 */
    private fun circularArcPoints(param: Octant): Array<Vec> {
        val startVector = param.circleStart - param.circleCenter
        val endVector = startVector.rotate(-param.circleMaxAngle)
        val circleEnd = param.circleCenter + endVector
        val startTangent = Vec(startVector.y, -startVector.x).normalize()
        val endTangent = Vec(-endVector.y, endVector.x).normalize()
        val bezierFactor = tan(param.circleMaxAngle / 4f) * 4f / 3f
        val radius = startVector.length()

        return arrayOf(
            param.circleStart,
            param.circleStart + startTangent * bezierFactor * radius,
            circleEnd + endTangent * bezierFactor * radius,
            circleEnd,
        )
    }

    /** 对应引擎的 `SuperellipseBezierFactors`。 */
    private fun superellipseBezierFactors(n: Float): FloatArray {
        if (n >= kBezierMaxN) {
            return floatArrayOf(
                1.07f - exp(1.307649835f) * n.pow(-0.8568516731f),
                -0.01f + exp(-0.9287690322f) * n.pow(-0.6120901398f),
            )
        }
        val steps = clamp((n - kBezierMinN) / kBezierStep, 0f, (kBezierNumRecords - 1).toFloat())
        val left = clamp(floor(steps).toInt().toFloat(), 0f, (kBezierNumRecords - 2).toFloat()).toInt()
        val frac = steps - left

        return floatArrayOf(
            (1f - frac) * kBezierFactors[left * 2] + frac * kBezierFactors[(left + 1) * 2],
            (1f - frac) * kBezierFactors[left * 2 + 1] + frac * kBezierFactors[(left + 1) * 2 + 1],
        )
    }
}

/**
 * 将 [LiquidGlassSuperellipse.PathOp] 序列回放到 Android `Compose` 路径上。
 *
 * 对应 FLClash 的 `Path` 回放（`moveTo`/`lineTo`/`cubicTo`/`close`），
 * 仅转发顶点，不做任何几何改动。声明为顶层扩展而非成员，方便来自另一
 * module 的调用方直导 `ops.toAndroidPath(path)`。
 */
fun List<LiquidGlassSuperellipse.PathOp>.toAndroidPath(path: android.graphics.Path) {
    for (op in this) {
        when (op) {
            is LiquidGlassSuperellipse.PathOp.Move -> path.moveTo(op.x, op.y)
            is LiquidGlassSuperellipse.PathOp.Line -> path.lineTo(op.x, op.y)
            is LiquidGlassSuperellipse.PathOp.Cubic -> path.cubicTo(op.c1x, op.c1y, op.c2x, op.c2y, op.x, op.y)
            is LiquidGlassSuperellipse.PathOp.Close -> path.close()
        }
    }
}
