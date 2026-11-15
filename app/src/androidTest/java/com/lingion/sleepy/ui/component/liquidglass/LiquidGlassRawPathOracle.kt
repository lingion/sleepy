package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Independent CPU oracle. It consumes raw buildPath commands, never shader descriptors.
 * Power-basis polynomials and derivative critical points isolate all ray roots, rather than
 * using the production endpoint-angle gates, De Casteljau evaluator, or 16-step solver.
 */
internal class LiquidGlassRawPathOracle(
    operations: List<LiquidGlassSuperellipse.PathOp>,
    density: Float,
    centerPx: Offset,
) {
    data class V(val x: Double, val y: Double) {
        operator fun plus(other: V) = V(x + other.x, y + other.y)
        operator fun minus(other: V) = V(x - other.x, y - other.y)
        operator fun times(scale: Double) = V(x * scale, y * scale)
        fun dot(other: V) = x * other.x + y * other.y
        fun cross(other: V) = x * other.y - y * other.x
        fun length() = sqrt(dot(this))
    }

    data class Segment(val start: V, val c1: V, val c2: V, val end: V, val line: Boolean) {
        private val a = if (line) V(0.0, 0.0) else end - c2 * 3.0 + c1 * 3.0 - start
        private val b = if (line) V(0.0, 0.0) else c2 * 3.0 - c1 * 6.0 + start * 3.0
        private val c = if (line) end - start else (c1 - start) * 3.0
        fun at(t: Double) = ((a * t + b) * t + c) * t + start
        fun tangent(t: Double) = (a * (3.0 * t) + b * 2.0) * t + c
        fun rayPolynomial(center: V, radial: V) = doubleArrayOf(
            a.cross(radial), b.cross(radial), c.cross(radial), (start - center).cross(radial),
        )
    }

    data class Geometry(val signedInset: Double, val normals: List<V>, val reach: Double)
    val center = V(centerPx.x.toDouble() / density, centerPx.y.toDouble() / density)
    val segments: List<Segment>

    init {
        val result = mutableListOf<Segment>()
        var previous: V? = null
        fun point(x: Float, y: Float) = V(x.toDouble() / density, y.toDouble() / density)
        operations.forEach { op ->
            when (op) {
                is LiquidGlassSuperellipse.PathOp.Move -> previous = point(op.x, op.y)
                is LiquidGlassSuperellipse.PathOp.Cubic -> {
                    val start = checkNotNull(previous)
                    val end = point(op.x, op.y)
                    result += Segment(start, point(op.c1x, op.c1y), point(op.c2x, op.c2y), end, false)
                    previous = end
                }
                is LiquidGlassSuperellipse.PathOp.Line -> {
                    val start = checkNotNull(previous)
                    val end = point(op.x, op.y)
                    result += Segment(start, start, end, end, true)
                    previous = end
                }
                LiquidGlassSuperellipse.PathOp.Close -> Unit
            }
        }
        segments = result
    }

    fun geometry(point: V): Geometry {
        val fromCenter = point - center
        val length = fromCenter.length()
        val radial = if (length > 1e-3) fromCenter * (1.0 / length) else V(1.0, 0.0)
        data class Hit(val reach: Double, val normal: V)
        val hits = mutableListOf<Hit>()
        segments.forEach { segment ->
            roots(segment.rayPolynomial(center, radial)).forEach hit@ { t ->
                val tangent = segment.tangent(t)
                val magnitude = tangent.length()
                if (magnitude <= 1e-6) return@hit
                val normal = V(tangent.y / magnitude, -tangent.x / magnitude)
                val reach = (segment.at(t) - center).dot(radial)
                if (reach >= 0.0 && normal.dot(radial) > 1e-5) hits += Hit(reach, normal)
            }
        }
        val reach = checkNotNull(hits.maxOfOrNull { it.reach }) { "Raw CPU path has no positive ray hit at $point" }
        // At an actual joint either one-sided CPU tangent is valid. Never average them.
        val equivalent = hits.filter { abs(it.reach - reach) < 5e-5 }.map { it.normal }
        return Geometry((reach - length) * equivalent.first().dot(radial), equivalent, reach)
    }

    private fun roots(c: DoubleArray): List<Double> {
        fun value(t: Double) = ((c[0] * t + c[1]) * t + c[2]) * t + c[3]
        val breaks = mutableListOf(0.0, 1.0)
        val a = 3.0 * c[0]
        val b = 2.0 * c[1]
        val d = c[2]
        if (abs(a) < 1e-14) {
            if (abs(b) > 1e-14) breaks += -d / b
        } else {
            val discriminant = b * b - 4.0 * a * d
            if (discriminant >= 0.0) {
                val root = sqrt(discriminant)
                breaks += (-b - root) / (2.0 * a)
                breaks += (-b + root) / (2.0 * a)
            }
        }
        val partitions = breaks.filter { it in 0.0..1.0 }.distinct().sorted()
        val roots = partitions.filter { abs(value(it)) < 1e-10 }.toMutableList()
        partitions.zipWithNext().forEach { (start, end) ->
            var lo = start
            var hi = end
            var flo = value(lo)
            val fhi = value(hi)
            if ((flo < 0.0) != (fhi < 0.0)) {
                repeat(60) {
                    val middle = (lo + hi) * .5
                    val at = value(middle)
                    if ((at < 0.0) == (flo < 0.0)) { lo = middle; flo = at } else hi = middle
                }
                roots += (lo + hi) * .5
            }
        }
        return roots.distinct()
    }

    companion object {
        fun union(first: Geometry, second: Geometry): Geometry {
            val selected = if (first.signedInset >= second.signedInset) first else second
            return selected.copy(signedInset = max(first.signedInset, second.signedInset))
        }
    }
}
