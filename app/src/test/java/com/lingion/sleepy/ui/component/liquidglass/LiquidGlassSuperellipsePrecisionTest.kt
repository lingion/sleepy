// Copyright 2026 The Sleepy Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.lingion.sleepy.ui.component.liquidglass

import android.graphics.Path
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassSuperellipse.PathOp
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassSuperellipse.Radii
import com.lingion.sleepy.ui.component.liquidglass.LiquidGlassSuperellipse.Rect2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * 位精度回归测试。
 *
 * 把引擎 `adc901062556672b4138e18a4dc62a4be8f4b3c2` 的
 * `round_superellipse_param.cc` 关键路径的 Kotlin 移植结果，与引擎 `-O2`
 * 构建的已知比特黄金值逐位比对。
 *
 * 黄金值来自 `/tmp/oracle`（`arcprobe`/`octantmass2`），其中
 * `-O2` 复现 `golden_labelled.txt`，`-ffp-contract=off` 复现
 * `golden_labelled_nofma.txt`。引擎默认构建是 `-O2`，故此处
 * 校验对准 `golden_labelled.txt`。
 *
 * 已知不可逾越的差距（Cause B）：右圆 `circleMaxAngle` 在 JVM 双精度
 * `atan2` 上 1 ULP，引擎 `glibc atan2f` 1 ULP 更上。本测试在右圆处
 * 允许 ≤1 ULP，见 [atMostOneUlp]。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiquidGlassSuperellipsePrecisionTest {

    /** 两个 float 间以无符-格雷格码距离度量的最大 ULP。 */
    private fun ulpDistance(a: Float, b: Float): Long {
        val ia = java.lang.Float.floatToRawIntBits(a).toLong()
        val ib = java.lang.Float.floatToRawIntBits(b).toLong()
        val ga = if (ia < 0L) 0x80000000L - ia else ia
        val gb = if (ib < 0L) 0x80000000L - ib else ib
        return abs(ga - gb)
    }

    /** Cause B 允许的 ≤1 ULP 容忍。 */
    private fun atMostOneUlp(actual: Float, expected: Float) {
        val dist = ulpDistance(actual, expected)
        assertTrue(
            "expected=$expected bits=${java.lang.Float.floatToRawIntBits(expected)}, " +
                "actual=$actual bits=${java.lang.Float.floatToRawIntBits(actual)}, " +
                "ulp=$dist",
            dist <= 1L
        )
    }

    @Test
    fun rightOctantCircleMaxAngleFmaGolden() {
        // 来自 /tmp/oracle arcprobe —O2 (golden_labelled.txt)
        // CASE ... radius=25, a=50, right octant
        // Engine circleMaxAngle bits = 1059328229 == Float 0.64088273
        val bounds = Rect2(0f, 0f, 200f, 100f)
        val radii = Radii.makeRadius(25f)
        val param = LiquidGlassSuperellipse.makeBoundsRadii(bounds.getPositive(), radii)
        // all-corners-same=true 时 top_right 四个象限共用参数；
        // 右圆 = param.topRight.right。
        val rightAngle = param.topRight.right.circleMaxAngle
        atMostOneUlp(rightAngle, 0.64088273f)
    }

    @Test
    fun deflateShrinksAllEightRadii() {
        // 对应 FLClash 的 `shape.deflate(style.rimWidth / 2)`：
        // Dart _RRectLike.inflate 把 8 个角半径同时 max(0, r + delta)。
        val r = Radii.makeRadius(20f)
        val deflated = r.deflate(5f)
        assertEquals(15f, deflated.topLeft.width, 0.001f)
        assertEquals(15f, deflated.topLeft.height, 0.001f)
        assertEquals(15f, deflated.bottomRight.width, 0.001f)
        assertEquals(15f, deflated.bottomRight.height, 0.001f)
    }

    @Test
    fun pathOpsReplayToAndroidPath() {
        val ops: List<PathOp> = listOf(
            PathOp.Move(1f, 2f),
            PathOp.Line(3f, 4f),
            PathOp.Cubic(3f, 4f, 5f, 6f, 7f, 8f),
            PathOp.Close,
        )
        val path = Path()
        // toAndroidPath is a top-level internal extension in the same package.
        ops.toAndroidPath(path)
        // Bounds reflect the last cubic endpoint (7,8)。
        val b = android.graphics.RectF()
        path.computeBounds(b, true)
        assertEquals(1f, b.left, 0.001f)
        assertEquals(2f, b.top, 0.001f)
        assertEquals(7f, b.right, 0.001f)
        assertEquals(8f, b.bottom, 0.001f)
    }
}
