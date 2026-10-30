package com.lingion.sleepy.ui.glass.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices

/** Top light only, clipped by the real card outline at the call site. */
internal fun courseEdgeGlowMesh(bounds: Rect, spread: Float, color: Color, strength: Float): Vertices? {
    if (bounds.width <= 0f || bounds.height <= 0f || spread <= 0f || strength <= 0f) return null
    val topFeather = minOf(spread, bounds.width / 2f, bounds.height / 2f)
    // Without side light each horizontal row has a uniform color. Two vertices per
    // row preserve the original 16-step top falloff without building a full-card grid.
    val positions = ArrayList<Offset>(34)
    val colors = ArrayList<Color>(34)
    for (row in 0..16) {
        val t = row / 16f
        val y = bounds.top + topFeather * t
        val falloff = (1f - t) * (1f - t) * (1f + 2f * t)
        val rowColor = color.copy(alpha = (0.28f * strength * falloff).coerceIn(0f, 1f))
        positions += Offset(bounds.left, y)
        positions += Offset(bounds.right, y)
        colors += rowColor
        colors += rowColor
    }
    val indices = ArrayList<Int>(96)
    for (row in 0 until 16) {
        val a = row * 2
        val b = a + 1
        val c = a + 2
        val d = c + 1
        indices.addAll(listOf(a, b, c, b, d, c))
    }
    return Vertices(VertexMode.Triangles, positions, positions, colors, indices)
}
