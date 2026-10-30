package com.lingion.sleepy.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class GlassTransitionGeometry(
    val rectInRoot: Rect,
    val cornerRadiusPx: Float
) {
    init {
        require(rectInRoot.width > 0f && rectInRoot.height > 0f) {
            "Glass transition geometry must have a positive size."
        }
        require(
            rectInRoot.left.isFinite() &&
                rectInRoot.top.isFinite() &&
                rectInRoot.right.isFinite() &&
                rectInRoot.bottom.isFinite() &&
                cornerRadiusPx.isFinite() &&
                cornerRadiusPx >= 0f
        ) {
            "Glass transition geometry requires finite coordinates and a non-negative radius."
        }
    }
}

/** Matches the legacy `offset(round) + layout(size.round)` pixel grid exactly. */
fun GlassTransitionGeometry.pixelAligned(): GlassTransitionGeometry {
    val left = rectInRoot.left.roundToInt().toFloat()
    val top = rectInRoot.top.roundToInt().toFloat()
    val width = rectInRoot.width.roundToInt().coerceAtLeast(1).toFloat()
    val height = rectInRoot.height.roundToInt().coerceAtLeast(1).toFloat()
    return copy(rectInRoot = Rect(left, top, left + width, top + height))
}

data class GlassTransitionEnvelope(val boundsInRoot: Rect) {
    init {
        require(
            boundsInRoot.width > 0f &&
                boundsInRoot.height > 0f &&
                boundsInRoot.left.isFinite() &&
                boundsInRoot.top.isFinite() &&
                boundsInRoot.right.isFinite() &&
                boundsInRoot.bottom.isFinite()
        ) {
            "A glass transition envelope must have a positive size."
        }
    }

    fun contains(geometry: GlassTransitionGeometry): Boolean =
        geometry.rectInRoot.left >= boundsInRoot.left &&
            geometry.rectInRoot.top >= boundsInRoot.top &&
            geometry.rectInRoot.right <= boundsInRoot.right &&
            geometry.rectInRoot.bottom <= boundsInRoot.bottom

    fun toLocal(rectInRoot: Rect): Rect = Rect(
        left = rectInRoot.left - boundsInRoot.left,
        top = rectInRoot.top - boundsInRoot.top,
        right = rectInRoot.right - boundsInRoot.left,
        bottom = rectInRoot.bottom - boundsInRoot.top
    )

    companion object {
        fun covering(
            geometries: Iterable<GlassTransitionGeometry>,
            effectPaddingPx: Float = 0f
        ): GlassTransitionEnvelope {
            require(effectPaddingPx.isFinite()) { "Effect padding must be finite." }
            val iterator = geometries.iterator()
            require(iterator.hasNext()) { "At least one transition geometry is required." }
            var union = iterator.next().rectInRoot
            while (iterator.hasNext()) {
                val next = iterator.next().rectInRoot
                union = Rect(
                    left = minOf(union.left, next.left),
                    top = minOf(union.top, next.top),
                    right = maxOf(union.right, next.right),
                    bottom = maxOf(union.bottom, next.bottom)
                )
            }
            val padding = effectPaddingPx.coerceAtLeast(0f)
            return GlassTransitionEnvelope(
                Rect(
                    left = union.left - padding,
                    top = union.top - padding,
                    right = union.right + padding,
                    bottom = union.bottom + padding
                )
            )
        }
    }
}

fun GlassTransitionEnvelope.areaRatioComparedTo(geometry: GlassTransitionGeometry): Float {
    val targetArea = geometry.rectInRoot.width * geometry.rectInRoot.height
    return (boundsInRoot.width * boundsInRoot.height) / targetArea.coerceAtLeast(1f)
}

fun GlassTransitionEnvelope.isAllocationEfficientFor(
    geometry: GlassTransitionGeometry,
    maximumAreaRatio: Float
): Boolean {
    require(maximumAreaRatio >= 1f && maximumAreaRatio.isFinite()) {
        "Maximum envelope area ratio must be finite and at least one."
    }
    return contains(geometry.pixelAligned()) &&
        areaRatioComparedTo(geometry) <= maximumAreaRatio
}

/**
 * Samples deterministic legacy geometry once and allocates one integer-aligned layer that covers
 * the complete opening and closing paths. A small caller-provided padding absorbs the gap between
 * samples; it does not alter the animated clip or the glass material.
 */
fun sampleGlassTransitionEnvelope(
    tracks: List<(Float) -> GlassTransitionGeometry>,
    steps: Int = 96,
    effectPaddingPx: Float = 0f
): GlassTransitionEnvelope {
    require(tracks.isNotEmpty()) { "At least one transition track is required." }
    require(steps > 0) { "Transition envelope sampling requires at least one step." }
    val samples = buildList(tracks.size * (steps + 1)) {
        tracks.forEach { geometryAt ->
            for (step in 0..steps) {
                add(geometryAt(step.toFloat() / steps.toFloat()).pixelAligned())
            }
        }
    }
    val sampled = GlassTransitionEnvelope.covering(samples, effectPaddingPx)
    val bounds = sampled.boundsInRoot
    return GlassTransitionEnvelope(
        Rect(
            left = floor(bounds.left),
            top = floor(bounds.top),
            right = ceil(bounds.right),
            bottom = ceil(bounds.bottom)
        )
    )
}

/**
 * Places the already-measured target subtree exactly where the legacy dynamic shell centered it.
 * Neither content nor corner radii are scaled.
 */
fun stableContentOffsetInEnvelope(
    envelope: GlassTransitionEnvelope,
    geometry: GlassTransitionGeometry,
    stableContentSize: IntSize
): IntOffset {
    require(stableContentSize.width > 0 && stableContentSize.height > 0) {
        "Stable transition content must have a positive size."
    }
    val aligned = geometry.pixelAligned()
    require(envelope.contains(aligned)) { "Animated geometry escaped its stable envelope." }
    val local = envelope.toLocal(aligned.rectInRoot)
    val shellWidth = aligned.rectInRoot.width.roundToInt().coerceAtLeast(1)
    val shellHeight = aligned.rectInRoot.height.roundToInt().coerceAtLeast(1)
    return IntOffset(
        x = local.left.roundToInt() + (shellWidth - stableContentSize.width) / 2,
        y = local.top.roundToInt() + (shellHeight - stableContentSize.height) / 2
    )
}

@Stable
class GlassTransitionLayerState internal constructor(
    val envelope: GlassTransitionEnvelope,
    initialGeometry: GlassTransitionGeometry
) {
    var geometry by mutableStateOf(initialGeometry)
        private set

    val localRect: Rect get() = envelope.toLocal(geometry.rectInRoot)
    val stableTargetWidthPx: Int get() = ceil(envelope.boundsInRoot.width).toInt()
    val stableTargetHeightPx: Int get() = ceil(envelope.boundsInRoot.height).toInt()

    init {
        require(envelope.contains(initialGeometry)) {
            "Initial glass geometry must fit inside its stable envelope."
        }
    }

    fun updateGeometry(next: GlassTransitionGeometry) {
        require(envelope.contains(next)) {
            "Animated glass geometry escaped its stable envelope."
        }
        geometry = next
    }
}

@Composable
fun rememberGlassTransitionLayerState(
    envelope: GlassTransitionEnvelope,
    initialGeometry: GlassTransitionGeometry
): GlassTransitionLayerState = remember(envelope) {
    GlassTransitionLayerState(envelope, initialGeometry)
}

private data class GlassEnvelopeSnapshotShape(
    val rect: Rect,
    val cornerRadiusPx: Float
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = cornerRadiusPx.coerceIn(
            minimumValue = 0f,
            maximumValue = minOf(rect.width, rect.height) / 2f
        )
        return Outline.Generic(
            androidx.compose.ui.graphics.Path().apply {
                addPath(
                    androidx.compose.ui.graphics.Path().apply {
                        addOutline(com.kyant.shapes.RoundedRectangle(with(density) { radius.toDp() })
                            .createOutline(rect.size, layoutDirection, density))
                    },
                    rect.topLeft
                )
            }
        )
    }
}

/**
 * Returns a value Shape whose equality follows the current inset geometry. Backdrop 2.0 caches
 * an outline while both modifier size and returned Shape stay equal; returning the same mutable
 * Shape object would therefore freeze a moving rect at its first fixed-envelope frame.
 */
internal fun GlassTransitionEnvelope.insetShapeFor(
    geometry: GlassTransitionGeometry
): Shape {
    val current = geometry.pixelAligned()
    require(contains(current)) {
        "Animated glass geometry escaped its stable envelope."
    }
    return GlassEnvelopeSnapshotShape(
        rect = toLocal(current.rectInRoot),
        cornerRadiusPx = current.cornerRadiusPx
    )
}

/**
 * Fixed-size host for the experimental transition renderer. [content] must position source and
 * destination content at real measured sizes; this host never scales either page to fill the
 * envelope.
 */
@Composable
fun GlassTransitionLayer(
    state: GlassTransitionLayerState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(GlassTransitionLayerState) -> Unit
) {
    val density = LocalDensity.current
    val bounds = state.envelope.boundsInRoot
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = bounds.left.roundToInt(),
                    y = bounds.top.roundToInt()
                )
            }
            .requiredSize(
                width = with(density) { state.stableTargetWidthPx.toDp() },
                height = with(density) { state.stableTargetHeightPx.toDp() }
            )
    ) {
        content(state)
    }
}


/**
 * Draw-time geometry variant used by production experiments. The host allocation stays fixed;
 * only its inset outline and child placement move. When [temporaryClipActive] is false no
 * GraphicsLayer is installed, so the stable Open endpoint remains live and un-clipped.
 */
@Composable
fun GlassTransitionLayer(
    envelope: GlassTransitionEnvelope,
    geometry: () -> GlassTransitionGeometry,
    temporaryClipActive: Boolean,
    motionAlpha: () -> Float = { 1f },
    modifier: Modifier = Modifier,
    motionTransform: Modifier = Modifier,
    content: @Composable BoxScope.(
        envelope: GlassTransitionEnvelope,
        geometry: () -> GlassTransitionGeometry
    ) -> Unit
) {
    val density = LocalDensity.current
    val geometryState = rememberUpdatedState(geometry)
    val alphaState = rememberUpdatedState(motionAlpha)
    val bounds = envelope.boundsInRoot
    val layerModifier = if (temporaryClipActive) {
        Modifier.graphicsLayer {
            // Reading the actual animation state here invalidates only placement/layer drawing,
            // not the fixed host measurement or the precomposed destination subtree.
            val current = geometryState.value.invoke().pixelAligned()
            require(envelope.contains(current)) {
                "Animated glass geometry escaped its stable envelope."
            }
            alpha = alphaState.value.invoke().coerceIn(0f, 1f)
            clip = true
            shape = envelope.insetShapeFor(current)
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = bounds.left.roundToInt(),
                    y = bounds.top.roundToInt()
                )
            }
            .requiredSize(
                width = with(density) { ceil(bounds.width).toInt().toDp() },
                height = with(density) { ceil(bounds.height).toInt().toDp() }
            )
            .then(motionTransform)
            .then(layerModifier)
    ) {
        content(envelope) { geometryState.value.invoke() }
    }
}
