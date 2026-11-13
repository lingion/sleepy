package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.shapes.Capsule

/** Keep Kyant's actual Continuous Capsule clip and optical commands tied to the same outline. */
internal class DockPlatterShape private constructor(
    private val capsule: Capsule,
) : Shape, LiquidGlassExactContourShape {
    constructor() : this(Capsule())

    private data class Key(val size: Size, val direction: LayoutDirection, val density: Float)
    private class Entry(val key: Key, val outline: Outline) {
        // CPU clips, backgrounds and shadows need only the actual outline.
        // Parse its optical commands only when a shader requests the descriptor.
        val exact: LiquidGlassExactContour? by lazy {
            (outline as? Outline.Generic)?.path?.let { LiquidGlassExactContour.fromPath(it) }
        }
        val opticalProfile: LiquidGlassCachedOutlineProfile? by lazy {
            (outline as? Outline.Generic)?.path?.let { source ->
                val pathDp = Path().apply {
                    addPath(source)
                    transform(Matrix().apply { scale(1f / key.density, 1f / key.density) })
                }
                LiquidGlassCachedOutlineProfile(pathDp) { exact }
            }
        }
    }
    private val entries = mutableListOf<Entry>()

    @Synchronized
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val key = Key(size, layoutDirection, density.density)
        entries.firstOrNull { it.key == key }?.let { return it.outline }
        val outline = capsule.createOutline(size, layoutDirection, density)
        if (entries.size == 4) entries.removeAt(0)
        entries += Entry(key, outline)
        return outline
    }

    @Synchronized
    override fun exactContoursFor(outline: Outline): List<LiquidGlassExactContour>? =
        entries.firstOrNull { it.outline === outline }?.exact?.let(::listOf)

    @Synchronized
    fun opticalProfile(
        sizePx: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): LiquidGlassCachedOutlineProfile? {
        val pixelsPerDp = density.density
        if (!pixelsPerDp.isFinite() || pixelsPerDp <= 0f || !sizePx.width.isFinite() || !sizePx.height.isFinite() ||
            sizePx.width <= 0f || sizePx.height <= 0f) return null
        val outline = createOutline(sizePx, layoutDirection, density)
        return entries.firstOrNull { it.outline === outline }?.opticalProfile
    }

    /** The returned path belongs to this shape's retained entry and must not be mutated. */
    fun opticalPath(sizePx: Size, layoutDirection: LayoutDirection, density: Density): Path? =
        opticalProfile(sizePx, layoutDirection, density)?.path
}
