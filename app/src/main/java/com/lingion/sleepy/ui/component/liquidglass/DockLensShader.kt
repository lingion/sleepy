package com.lingion.sleepy.ui.component.liquidglass

import android.content.Context
import android.os.Build
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Keep API 33 types out of the dock's composition and captured drawing lambdas. */
internal interface DockLensShader {
    fun renderEffect(uniforms: Map<String, FloatArray>): RenderEffect
}

internal fun createDockLensShader(context: Context): DockLensShader? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { Api33DockLensShader.create(LiquidGlassShader.shaderSource(context)) }.getOrNull()
    } else null

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class Api33DockLensShader private constructor(
    private val shader: android.graphics.RuntimeShader,
) : DockLensShader {
    @DoNotInline
    override fun renderEffect(uniforms: Map<String, FloatArray>): RenderEffect {
        uniforms.forEach { (name, values) -> shader.setFloatUniform(name, values) }
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "uTexture").asComposeRenderEffect()
    }

    companion object {
        @DoNotInline
        fun create(source: String): DockLensShader = Api33DockLensShader(android.graphics.RuntimeShader(source))
    }
}
