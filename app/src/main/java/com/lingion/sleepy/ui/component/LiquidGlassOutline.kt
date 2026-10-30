package com.lingion.sleepy.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FlClash `AppShape.full.getOuterPath(rect)` = RoundedSuperellipseBorder 轮廓
 * (navigation_dock.dart `_LensClip` 1477、`_RenderLensTint` 1935、`_paintShadows`
 * 1035) 的移植 — 与 shader `cornerInset` 同源的 ComputeOctant 参数
 * ([LiquidGlassSuperellipse]), 即引擎 `Path.addRSuperellipse` 画的同一形状:
 * 每边界从**边中点**起 = 超椭圆弧 xⁿ+yⁿ=aⁿ (0≤t≤jointAt) → 过渡圆 J→meet →
 * 邻 octant 圆 meet→J' → 其超椭圆弧 (jointAt'→1) 到邻边中点。八段超椭圆+八段圆,
 * 药丸两端无直线段 — 替代 RoundedCornerShape(50%) 圆近似(任务书 prefer)。
 *
 * 前提: 四角全等圆角 r=min(radius, shortestSide/2)(FlClash pane 钳位
 * liquid_glass.dart 966 同式; Dock 三 pane 皆胶囊, r=shortestSide/2)。
 *
 * 坐标 = [rect] 局部(左上原点), 与 Compose clipPath 直接兼容。
 */
object LiquidGlassOutline {

    /** 每段弧的线性采样数(24 段: 弦高 <0.02dp@31dp, 远超像素精度)。 */
    const val SEGMENTS = 24

    /** 右上象限半边链(中心系 q): (0,·) 边中点 → … → (·,0) 边中点。 */
    fun topRightChain(rect: Rect, radiusDp: Float): List<Offset> {
        val hw = rect.width / 2.0
        val hh = rect.height / 2.0
        val r = minOf(radiusDp, minOf(rect.width, rect.height) / 2f).toDouble()
        require(r > 1e-3) { "尖角退化请用 addRect(rect)" }
        val along = LiquidGlassSuperellipse.superellipseOctant(hw, r)
        val across = LiquidGlassSuperellipse.superellipseOctant(hh, r)
        val c = hw - hh // 八分帧错位(对角线过 meet 与中心)
        val gap = 1.0 - 0.7071067811865476 // OCTANT_GAP
        val meetA = hw - gap * r
        val meetB = hh - gap * r
        val out = ArrayList<Offset>(4 * SEGMENTS)
        // 1. along 超椭圆 (a=hw): t ∈ [0, jx/hw] → q=(X, Y−c)
        for (i in 0..SEGMENTS) superellipsePoint(hw, along.n, i / SEGMENTS * (along.jointX / hw))
            ?.let { (x, y) -> out.add(Offset(x.toFloat(), (y - c).toFloat())) }
        // 2. along 圆: frame J→(m,m) → q 换轴 (X, Y−c)
        arcPoints(along, from = along.jointX to along.jointY, to = meetA to meetA)
            .forEach { (x, y) -> out.add(Offset(x.toFloat(), (y - c).toFloat())) }
        // 3. across 圆: frame' (m',m')→J' → q=(X'+c, Y')  (首点=上段末点, 去重)
        arcPoints(across, from = meetB to meetB, to = across.jointX to across.jointY)
            .drop(1)
            .forEach { (x, y) -> out.add(Offset((x + c).toFloat(), y.toFloat())) }
        // 4. across 超椭圆 (a=hh): t ∈ [jx'/hh, 1] → q=(X'+c, Y')
        for (i in 0..SEGMENTS) {
            val t = across.jointX / hh + (1.0 - across.jointX / hh) * i / SEGMENTS
            superellipsePoint(hh, across.n, t)?.let { (x, y) -> out.add(Offset((x + c).toFloat(), y.toFloat())) }
        }
        return out
    }

    /** 完整胶囊/squircle 轮廓: 右上链 + 三镜像拼合(顺时针闭合)。 */
    fun path(rect: Rect, radiusDp: Float): Path {
        val path = Path()
        val hw = rect.width / 2f
        val hh = rect.height / 2f
        val r = minOf(radiusDp, minOf(rect.width, rect.height) / 2f)
        if (r <= 1e-3f) {
            path.addRect(rect)
            return path
        }
        val cx = rect.center.x
        val cy = rect.center.y
        val tr = topRightChain(rect, r)
        // 链端点即边界中点(帧数值: 首 (0,hh) 末 (hw,0))
        val br = tr.map { Offset(cx + it.x, cy - it.y) }   // (0,−hh)…(hw,0)
        val tl = tr.map { Offset(cx - it.x, cy + it.y) }   // (0,hh)…(−hw,0)
        val bl = tr.map { Offset(cx - it.x, cy - it.y) }   // (0,−hh)…(−hw,0)
        path.moveTo(cx + tr.first().x, cy + tr.first().y)  // 顶边中点
        tr.drop(1).forEach { path.lineTo(cx + it.x, cy + it.y) } // → 右边中点
        br.drop(1).reversed().forEach { path.lineTo(it.x, it.y) } // 右边中点→底边中点
        bl.drop(1).forEach { path.lineTo(it.x, it.y) }            // 底边中点→左边中点
        tl.drop(1).reversed().forEach { path.lineTo(it.x, it.y) } // 左边中点→顶边中点
        path.close()
        return path
    }

    /** 超椭圆弧一点(帧坐标): x=a·t, y=a(1−tⁿ)^{1/n}; t∉[0,1] 返回 null。 */
    private fun superellipsePoint(a: Double, n: Double, t: Double): Pair<Double, Double>? {
        if (n <= 0.0) return if (t <= 0.0) Pair(0.0, a) else Pair(a, 0.0)
        val tt = t.coerceIn(0.0, 1.0)
        val y = (1.0 - tt.pow(n)).coerceAtLeast(0.0).pow(1.0 / n)
        return Pair(a * tt, a * y)
    }

    /** 过渡圆从 from→to 的短弧点列(含两端, 帧坐标; 入参 (x,y) 对)。 */
    fun arcPoints(
        octant: LiquidGlassSuperellipse.Octant,
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
    ): List<Offset> {
        val cx = octant.centerX
        val cy = octant.centerY
        val arc = octant.radius
        if (arc <= 0.0) return listOf(from.toOffset(), to.toOffset())
        val a0 = atan2(from.second - cy, from.first - cx)
        val a1 = atan2(to.second - cy, to.first - cx)
        var d = a1 - a0
        while (d > PI) d -= 2.0 * PI
        while (d <= -PI) d += 2.0 * PI
        return (0..SEGMENTS).map { i ->
            val ang = a0 + d * i / SEGMENTS
            Offset((cx + arc * cos(ang)).toFloat(), (cy + arc * sin(ang)).toFloat())
        }
    }

    private fun Pair<Double, Double>.toOffset() = Offset(first.toFloat(), second.toFloat())

    /** 圆心距校验用(测试): 点到 (cx,cy) 距离。 */
    fun distTo(octant: LiquidGlassSuperellipse.Octant, x: Double, y: Double): Double {
        val dx = x - octant.centerX
        val dy = y - octant.centerY
        return sqrt(dx * dx + dy * dy)
    }
}
