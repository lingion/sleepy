// Modified for SleepDown on 2026-09-08; upstream 2.0.0, Apache-2.0 (see README.md).
package com.kyant.backdrop.internal

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.BackdropRenderOptions

@Immutable
internal class ShapeProvider(
    val shapeBlock: () -> Shape,
    val options: BackdropRenderOptions = BackdropRenderOptions.Default
) {

    // Cache ownership stays with the node when equivalent modifier elements are recreated.
    override fun equals(other: Any?): Boolean =
        other is ShapeProvider && shapeBlock == other.shapeBlock && options == other.options

    override fun hashCode(): Int = 31 * shapeBlock.hashCode() + options.hashCode()

    private var _shape: Shape? = null
    private var _bounds: Rect? = null
    private var _outline: State<Outline>? = null
    private var _size: Size = Size.Unspecified
    private var _layoutDirection: LayoutDirection? = null
    private var _density: Float? = null

    val innerShape
        get() = shapeBlock()

    /** Read dynamic geometry inside the layer's observation scope, including at fixed host size. */
    fun snapshot(size: Size, layoutDirection: LayoutDirection, density: Density): Shape =
        OutlineSnapshot(shape.createOutline(size, layoutDirection, density))

    val shape = object : Shape {

        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val shape = shapeBlock()
            val bounds = options.bounds()
            if (_shape != shape || _bounds != bounds || bounds != null) {
                _shape = shape
                _bounds = bounds
                _outline = null
            }
            if (_outline == null || _size != size || _layoutDirection != layoutDirection || _density != density.density) {
                _size = size
                _layoutDirection = layoutDirection
                _density = density.density
                // A stable Shape may read animation state itself. Observe that state even when
                // host size and Shape identity stay fixed; static outlines remain cached.
                _outline = derivedStateOf {
                    if (bounds == null) {
                        shape.createOutline(size, layoutDirection, density)
                    } else {
                        Outline.Generic(Path().apply {
                            addPath(Path().apply {
                                addOutline(shape.createOutline(bounds.size, layoutDirection, density))
                            }, bounds.topLeft)
                        })
                    }
                }
            }

            return _outline!!.value
        }
    }
}

private data class OutlineSnapshot(val outline: Outline) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = outline
}
