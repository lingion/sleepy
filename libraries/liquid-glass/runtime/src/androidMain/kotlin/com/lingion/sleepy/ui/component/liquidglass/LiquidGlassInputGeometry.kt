package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.ceil

/** Coordinates of the actual retained input raster, before any haze-slot packing. */
internal data class LiquidGlassInputGeometry(
    val originPx: Offset,
    val sizePx: Size,
    val clampPx: Rect,
)

/**
 * DrawBackdropModifier records the complete outer node, with a positive canvas translation
 * of [paddingPx]. A bounded pane retains its signed [effectOriginPx] within that node;
 * neither the pane's size nor its offset describes the full raster or clips its source.
 * Fixed allocation reserves extra trailing pixels but does not change that translation.
 */
internal fun liquidGlassInputGeometry(
    effectOriginPx: Offset,
    captureSizePx: Size,
    paddingPx: Float,
    allocationPaddingPx: Float? = null,
): LiquidGlassInputGeometry {
    require(effectOriginPx.x.isFinite() && effectOriginPx.y.isFinite())
    require(captureSizePx.width.isFinite() && captureSizePx.height.isFinite() &&
        captureSizePx.width > 0f && captureSizePx.height > 0f)
    require(paddingPx.isFinite() && paddingPx >= 0f)
    val allocationPadding = allocationPaddingPx ?: paddingPx
    require(allocationPadding.isFinite() && allocationPadding >= paddingPx)
    // Match the backend's physical recordingSize arithmetic, including fractional geometry.
    val requestedWidth = ceil(captureSizePx.width + allocationPadding * 2f)
    val requestedHeight = ceil(captureSizePx.height + allocationPadding * 2f)
    require(requestedWidth.isFinite() && requestedHeight.isFinite()) { "Input raster size overflows" }
    val width = requestedWidth.toInt().coerceAtLeast(1).toFloat()
    val height = requestedHeight.toInt().coerceAtLeast(1).toFloat()
    val origin = effectOriginPx + Offset(paddingPx, paddingPx)
    require(origin.x.isFinite() && origin.y.isFinite()) { "Input raster origin overflows" }
    return LiquidGlassInputGeometry(
        originPx = origin,
        sizePx = Size(width, height),
        clampPx = Rect(0f, 0f, width, height),
    )
}
