package com.lingion.sleepy.ui.glass

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/** Geometry in a fixed host. The material retains its original local pixel coordinate system. */
class GlassMorphAllocation(
    val envelope: GlassTransitionEnvelope,
    val geometry: () -> GlassTransitionGeometry,
    val paddingPx: Float
) {
    fun localBounds(): Rect {
        val current = geometry().pixelAligned()
        check(envelope.contains(current)) { "Morph escaped its fixed material allocation" }
        return envelope.toLocal(current.rectInRoot)
    }
}

fun Modifier.glassMorphHost(allocation: GlassMorphAllocation): Modifier =
    wrapContentSize(Alignment.TopStart, unbounded = true).offset {
        IntOffset(allocation.envelope.boundsInRoot.left.roundToInt(), allocation.envelope.boundsInRoot.top.roundToInt())
    }.layout { measurable, _ ->
        val bounds = allocation.envelope.boundsInRoot
        val width = bounds.width.roundToInt()
        val height = bounds.height.roundToInt()
        val child = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { child.place(0, 0) }
    }

/** Measures real content at exactly the legacy shell size; no nonuniform scaling. */
fun Modifier.glassMorphContent(allocation: GlassMorphAllocation): Modifier =
    wrapContentSize(Alignment.TopStart, unbounded = true).offset {
        val bounds = allocation.localBounds()
        IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
    }.layout { measurable, _ ->
        val bounds = allocation.localBounds()
        val width = bounds.width.roundToInt().coerceAtLeast(1)
        val height = bounds.height.roundToInt().coerceAtLeast(1)
        val child = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { child.place(0, 0) }
    }
