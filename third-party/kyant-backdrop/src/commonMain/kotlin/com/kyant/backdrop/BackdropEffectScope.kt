// Modified for SleepDown on 2026-09-08; upstream 2.0.0, Apache-2.0 (see README.md).
package com.kyant.backdrop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

sealed interface BackdropEffectScope : Density, RuntimeShaderCache {

    val size: Size
    val effectOffset: Offset

    /** Full outer source size in sampling pixels, before the retained raster's padding. */
    val captureSize: Size get() = size

    /** Optional fixed allocation; source translation still uses [padding]. */
    val captureAllocationPadding: Float? get() = null

    val layoutDirection: LayoutDirection

    val shape: Shape

    var padding: Float

    /** Optional GPU preprocessing pass, rasterized before [renderEffect] samples it. */
    var inputRenderEffect: RenderEffect?

    var renderEffect: RenderEffect?
}

internal abstract class BackdropEffectScopeImpl : BackdropEffectScope, RuntimeShaderCache {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var effectOffset: Offset = Offset.Zero
    override var captureSize: Size = Size.Unspecified
    override var captureAllocationPadding: Float? = null
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var inputRenderEffect: RenderEffect? = null
    override var renderEffect: RenderEffect? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl(shareEffects = true)

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaderCache.obtainRuntimeShader(key, string)
    }

    fun update(scope: DrawScope, effectSize: Size = scope.size, offset: Offset = Offset.Zero, densityScale: Float = 1f,
        allocationPadding: Float? = null): Boolean {
        val newDensity = scope.density * densityScale
        val newFontScale = scope.fontScale
        val newSize = effectSize
        val newCaptureSize = scope.size * densityScale
        val newLayoutDirection = scope.layoutDirection

        val changed = offset != effectOffset || newDensity != density ||
                newFontScale != fontScale ||
                newSize != size ||
                newCaptureSize != captureSize ||
                allocationPadding != captureAllocationPadding ||
                newLayoutDirection != layoutDirection

        if (changed) {
            density = newDensity
            fontScale = newFontScale
            size = newSize
            effectOffset = offset
            captureSize = newCaptureSize
            captureAllocationPadding = allocationPadding
            layoutDirection = newLayoutDirection
        }

        return changed
    }

    fun apply(effects: BackdropEffectScope.() -> Unit) {
        padding = 0f
        inputRenderEffect = null
        renderEffect = null
        effects()
    }

    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        captureSize = Size.Unspecified
        captureAllocationPadding = null
        padding = 0f
        inputRenderEffect = null
        renderEffect = null
        runtimeShaderCache.clear()
    }
}
