package com.lingion.sleepy.ui.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

internal fun GlassEffectFrame.sampledBackdropOnly(sampleScale: Float): GlassEffectFrame {
    val scale = sampleScale.coerceIn(0.5f, 1f)
    return copy(
        blur = blur?.times(scale),
        lensHeight = lensHeight?.times(scale),
        lensAmount = lensAmount?.times(scale),
        highlight = null,
        shadowAlpha = null,
        innerShadow = null
    )
}

internal fun GlassEffectFrame.decorationOnly(): GlassEffectFrame = copy(
    blur = null,
    lensHeight = null,
    lensAmount = null,
    useVibrancy = false
)

/** Decoration and placement changes must not invalidate the observed blur/refraction inputs. */
internal fun GlassEffectFrame.materialEffectsOnly(): GlassEffectFrame = copy(
    highlight = null,
    shadowAlpha = null,
    innerShadow = null,
    layerScale = null
)

/** Keeps the layer origin stable while reducing its actual allocation and local SDF geometry. */
internal fun GlassGroupLayerPlan.sampled(sampleScale: Float): GlassGroupLayerPlan {
    val scale = sampleScale.coerceIn(0.5f, 1f)
    if (scale >= 0.999f) return this
    val scaledWidth = ceil(size.width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = ceil(size.height * scale).toInt().coerceAtLeast(1)
    val scaledMembers = localPlan.members.map { member ->
        val bounds = member.boundsInViewport
        member.copy(
            boundsInViewport = Rect(
                left = bounds.left * scale,
                top = bounds.top * scale,
                right = bounds.right * scale,
                bottom = bounds.bottom * scale
            ),
            cornerRadiusPx = member.cornerRadiusPx * scale
        )
    }
    return copy(
        size = IntSize(scaledWidth, scaledHeight),
        localPlan = localPlan.copy(
            viewport = Rect(0f, 0f, scaledWidth.toFloat(), scaledHeight.toFloat()),
            members = scaledMembers
        )
    )
}
