package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.graphics.asComposePath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/** Descriptors belong to the actual returned Outline, after Compose has resolved its corners. */
interface LiquidGlassExactContourShape {
    fun exactContoursFor(outline: Outline): List<LiquidGlassExactContour>?
}

const val LIQUID_GLASS_EXACT_FLOATS = 144

/**
 * Actual Float path commands, not a reconstruction from a nominal corner radius. Format 1 keeps
 * buildPath's 16 cubics and final closing line unchanged. Format 2 additionally preserves an actual
 * single closed Path with at most 17 mixed cubic/line segments, each monotone around its center.
 */
class LiquidGlassExactContour private constructor(
    val operations: List<LiquidGlassSuperellipse.PathOp>,
    val centerPx: Offset,
    private val pixels: FloatArray,
) {
    fun pack(density: Float): FloatArray? {
        if (!density.isFinite() || density <= 0f) return null
        return pixels.copyOf().also { packed ->
            for (slot in 0 until 36) {
                val base = slot * 4
                if (slot == 1) continue
                packed[base] /= density
                packed[base + 1] /= density
                if (slot == 0 || slot % 2 == 0) {
                    packed[base + 2] /= density
                    packed[base + 3] /= density
                }
            }
        }.takeIf { it.all(Float::isFinite) }
    }

    companion object {
        /** Read the actual path, retaining conics as unsupported verbs rather than approximating them. */
        fun fromPath(path: Path): LiquidGlassExactContour? {
            val operations = mutableListOf<LiquidGlassSuperellipse.PathOp>()
            val iterator = PathIterator(path, PathIterator.ConicEvaluation.AsConic)
            val points = FloatArray(8)
            var move: Offset? = null
            var previous: Offset? = null
            var closed = false
            while (iterator.hasNext()) {
                when (iterator.next(points)) {
                    PathSegment.Type.Move -> {
                        if (move != null || closed) return null
                        move = Offset(points[0], points[1])
                        previous = move
                        operations += LiquidGlassSuperellipse.PathOp.Move(points[0], points[1])
                    }
                    PathSegment.Type.Line -> {
                        if (previous == null || closed || previous != Offset(points[0], points[1])) return null
                        previous = Offset(points[2], points[3])
                        operations += LiquidGlassSuperellipse.PathOp.Line(points[2], points[3])
                    }
                    PathSegment.Type.Cubic -> {
                        if (previous == null || closed || previous != Offset(points[0], points[1])) return null
                        previous = Offset(points[6], points[7])
                        operations += LiquidGlassSuperellipse.PathOp.Cubic(
                            points[2], points[3], points[4], points[5], points[6], points[7])
                    }
                    PathSegment.Type.Close -> {
                        val start = move ?: return null
                        if (previous == null || closed) return null
                        // Close has a real straight edge when the final endpoint differs from Move.
                        if (previous != start) operations += LiquidGlassSuperellipse.PathOp.Line(start.x, start.y)
                        operations += LiquidGlassSuperellipse.PathOp.Close
                        closed = true
                    }
                    PathSegment.Type.Done -> break
                    else -> return null
                }
                if (operations.size > 19) return null
            }
            if (!closed) return null
            return fromMixedOperations(operations, path.getBounds().center)
        }

        private fun fromMixedOperations(
            operations: List<LiquidGlassSuperellipse.PathOp>,
            center: Offset,
        ): LiquidGlassExactContour? {
            val move = operations.firstOrNull() as? LiquidGlassSuperellipse.PathOp.Move ?: return null
            val segments = operations.drop(1).dropLast(1)
            if (operations.lastOrNull() != LiquidGlassSuperellipse.PathOp.Close || segments.size !in 1..17 ||
                !center.x.isFinite() || !center.y.isFinite()) return null
            val packed = FloatArray(LIQUID_GLASS_EXACT_FLOATS)
            packed[0] = move.x; packed[1] = move.y
            packed[2] = center.x; packed[3] = center.y
            packed[4] = segments.size.toFloat()
            packed[5] = -1f // v1's single closing-line index is unused by mixed-segment v2.
            packed[6] = 2f
            var lineMask = 0
            var previous = Offset(move.x, move.y)
            var totalSpan = 0.0
            segments.forEachIndexed { index, operation ->
                val end: Offset
                val controls: List<Offset>
                when (operation) {
                    is LiquidGlassSuperellipse.PathOp.Cubic -> {
                        end = Offset(operation.x, operation.y)
                        controls = listOf(previous, Offset(operation.c1x, operation.c1y),
                            Offset(operation.c2x, operation.c2y), end)
                        if (!monotoneRay(controls, center)) return null
                    }
                    is LiquidGlassSuperellipse.PathOp.Line -> {
                        end = Offset(operation.x, operation.y)
                        controls = listOf(previous, previous, end, end)
                        lineMask = lineMask or (1 shl index)
                        if (previous != end) {
                            val x = previous.x.toDouble() - center.x
                            val y = previous.y.toDouble() - center.y
                            val cross = x * (end.y.toDouble() - previous.y) - y * (end.x.toDouble() - previous.x)
                            if (!cross.isFinite() || cross <= 0.0) return null
                        }
                    }
                    else -> return null
                }
                val startAngle = atan2(previous.y - center.y, previous.x - center.x)
                var span = atan2(end.y - center.y, end.x - center.x) - startAngle
                if (span > PI.toFloat()) span -= (2.0 * PI).toFloat()
                if (span < -PI.toFloat()) span += (2.0 * PI).toFloat()
                if (!span.isFinite() || span < -1e-6f || span > PI.toFloat()) return null
                totalSpan += span
                val base = 8 + index * 8
                packed[base] = controls[1].x; packed[base + 1] = controls[1].y
                packed[base + 2] = controls[2].x; packed[base + 3] = controls[2].y
                packed[base + 4] = end.x; packed[base + 5] = end.y
                packed[base + 6] = startAngle; packed[base + 7] = startAngle + span
                previous = end
            }
            if (previous != Offset(move.x, move.y) || abs(totalSpan - 2.0 * PI) > 1e-4) return null
            packed[7] = lineMask.toFloat() // All 17 mask bits are exactly representable by Float.
            if (packed.any { !it.isFinite() }) return null
            return LiquidGlassExactContour(operations.toList(), center, packed)
        }

        fun from(operations: List<LiquidGlassSuperellipse.PathOp>, center: Offset): LiquidGlassExactContour? {
            val move = operations.firstOrNull() as? LiquidGlassSuperellipse.PathOp.Move ?: return null
            if (operations.size != 19 || operations.last() != LiquidGlassSuperellipse.PathOp.Close ||
                operations.subList(1, 17).any { it !is LiquidGlassSuperellipse.PathOp.Cubic } ||
                operations[17] !is LiquidGlassSuperellipse.PathOp.Line ||
                !center.x.isFinite() || !center.y.isFinite()) return null
            val closing = operations[17] as LiquidGlassSuperellipse.PathOp.Line
            if (closing.x != move.x || closing.y != move.y) return null
            val packed = FloatArray(LIQUID_GLASS_EXACT_FLOATS)
            packed[0] = move.x
            packed[1] = move.y
            packed[2] = center.x
            packed[3] = center.y
            packed[4] = 17f
            packed[5] = 16f // final segment is an actual Line, not an invented cubic
            packed[6] = 1f // descriptor format
            var previous = Offset(move.x, move.y)
            for (index in 0 until 17) {
                val op = operations[index + 1]
                val end: Offset
                val controls: List<Offset>
                when (op) {
                    is LiquidGlassSuperellipse.PathOp.Cubic -> {
                        end = Offset(op.x, op.y)
                        controls = listOf(previous, Offset(op.c1x, op.c1y), Offset(op.c2x, op.c2y), end)
                        if (!monotoneRay(controls, center)) return null
                    }
                    is LiquidGlassSuperellipse.PathOp.Line -> {
                        end = Offset(op.x, op.y)
                        controls = listOf(previous, previous, end, end)
                    }
                    else -> return null
                }
                val startAngle = atan2(previous.y - center.y, previous.x - center.x)
                var span = atan2(end.y - center.y, end.x - center.x) - startAngle
                if (span > PI.toFloat()) span -= (2.0 * PI).toFloat()
                if (span < -PI.toFloat()) span += (2.0 * PI).toFloat()
                // A tiny reverse closing correction is kept. A reversed cubic is unsupported.
                if (index < 16 && (span < -1e-6f || span > PI.toFloat())) return null
                val base = 8 + index * 8
                packed[base] = controls[1].x
                packed[base + 1] = controls[1].y
                packed[base + 2] = controls[2].x
                packed[base + 3] = controls[2].y
                packed[base + 4] = end.x
                packed[base + 5] = end.y
                packed[base + 6] = startAngle
                packed[base + 7] = startAngle + span
                previous = end
            }
            if (packed.any { !it.isFinite() }) return null
            return LiquidGlassExactContour(operations.toList(), center, packed)
        }

        /** Sufficient Bernstein bound on cross(B-center,B') over the entire cubic interval. */
        private fun monotoneRay(points: List<Offset>, center: Offset): Boolean {
            val choose3 = doubleArrayOf(1.0, 3.0, 3.0, 1.0)
            val choose2 = doubleArrayOf(1.0, 2.0, 1.0)
            val choose5 = doubleArrayOf(1.0, 5.0, 10.0, 10.0, 5.0, 1.0)
            val coefficients = DoubleArray(6)
            var scale = 1.0
            for (i in 0..3) {
                val x = points[i].x.toDouble() - center.x
                val y = points[i].y.toDouble() - center.y
                scale = max(scale, x * x + y * y)
                for (j in 0..2) {
                    val dx = 3.0 * (points[j + 1].x.toDouble() - points[j].x)
                    val dy = 3.0 * (points[j + 1].y.toDouble() - points[j].y)
                    coefficients[i + j] += (x * dy - y * dx) * choose3[i] * choose2[j] / choose5[i + j]
                }
            }
            // Only absorb Float construction roundoff, not a meaningful angular reversal.
            return coefficients.all { it.isFinite() && it >= -scale * 1e-7 } &&
                coefficients.any { abs(it) > scale * 1e-10 }
        }
    }
}

/** One raw command list creates both the CPU path and its optional optical descriptor. */
class LiquidGlassContourOutline(operations: List<LiquidGlassSuperellipse.PathOp>) {
    val operations: List<LiquidGlassSuperellipse.PathOp> = operations.toList()
    val path: Path = android.graphics.Path().also { this.operations.toAndroidPath(it) }.asComposePath()
    val outline: Outline.Generic = Outline.Generic(path)
    val exact: LiquidGlassExactContour? = LiquidGlassExactContour.from(this.operations, path.getBounds().center)
}

/** Small per-shape cache; both layout and effect request the identical resolved geometry. */
class LiquidGlassContourCache {
    private data class Key(val bounds: LiquidGlassSuperellipse.Rect2, val radii: LiquidGlassSuperellipse.Radii)
    private val entries = mutableListOf<Pair<Key, LiquidGlassContourOutline>>()

    @Synchronized
    fun obtain(bounds: LiquidGlassSuperellipse.Rect2, radii: LiquidGlassSuperellipse.Radii): LiquidGlassContourOutline {
        val key = Key(bounds, radii)
        entries.firstOrNull { it.first == key }?.let { return it.second }
        val result = LiquidGlassContourOutline(LiquidGlassSuperellipse.buildPath(bounds, radii))
        if (entries.size == 4) entries.removeAt(0)
        entries += key to result
        return result
    }

    @Synchronized
    fun exactContoursFor(outline: Outline): List<LiquidGlassExactContour>? =
        entries.firstOrNull { it.second.outline === outline }?.second?.exact?.let(::listOf)
}
