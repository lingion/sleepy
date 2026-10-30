/*
 * Copyright 2025 Kyant
 * Copyright 2026 SleepDown contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lingion.sleepy.ui.glass

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported

/* Adapted from AndroidLiquidGlass 2.0.0 RoundedRectRefractionShaderString. */
private const val InsetRoundedRectSdf = """
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

private const val InsetRoundedRectRefractionShader = """
uniform shader content;

uniform float4 rect;
uniform float2 offset;
uniform float cornerRadius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$InsetRoundedRectSdf

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = rect.zw * 0.5;
    float2 centeredCoord = (coord + offset) - (rect.xy + halfSize);
    float sd = sdRoundedRect(centeredCoord, halfSize, cornerRadius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(cornerRadius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) +
            depthEffect * normalize(centeredCoord)
    );
    return content.eval(coord + d * grad);
}
"""

private const val InsetRoundedRectRefractionWithDispersionShader = """
uniform shader content;

uniform float4 rect;
uniform float2 offset;
uniform float cornerRadius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$InsetRoundedRectSdf

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = rect.zw * 0.5;
    float2 centeredCoord = (coord + offset) - (rect.xy + halfSize);
    float sd = sdRoundedRect(centeredCoord, halfSize, cornerRadius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(cornerRadius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) +
            depthEffect * normalize(centeredCoord)
    );
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration *
        ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    return color;
}
"""

/**
 * Backdrop 2.0-compatible rounded-rect refraction whose SDF may move inside a fixed effect layer.
 * The geometry is pixel-aligned exactly like the legacy animated layout before uniforms are set.
 */
fun BackdropEffectScope.insetRoundedRectLens(
    envelope: GlassTransitionEnvelope,
    geometry: () -> GlassTransitionGeometry,
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding > 0f) {
        padding = (padding - refractionHeight).coerceAtLeast(0f)
    }
    val current = geometry().pixelAligned()
    require(envelope.contains(current)) {
        "Inset lens geometry escaped its stable envelope."
    }
    val rect = envelope.toLocal(current.rectInRoot)
    val radius = current.cornerRadiusPx.coerceIn(0f, minOf(rect.width, rect.height) / 2f)
    runtimeShaderEffect(
        key = if (chromaticAberration) {
            "SleepDownInsetRefractionWithDispersionV1"
        } else {
            "SleepDownInsetRefractionV1"
        },
        shaderString = if (chromaticAberration) {
            InsetRoundedRectRefractionWithDispersionShader
        } else {
            InsetRoundedRectRefractionShader
        },
        uniformShaderName = "content"
    ) {
        setFloatUniform("rect", rect.left, rect.top, rect.width, rect.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("cornerRadius", radius)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (chromaticAberration) {
            setFloatUniform("chromaticAberration", 1f)
        }
    }
}

/** Reuse the glass lens on an already-composited morph, including its text; no new backdrop. */
@Composable
internal fun rememberGlassContentLens(): (Size, Float, Float, Float) -> RenderEffect? = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val lens = GlassContentLensApi33()
        return@remember { size: Size, radius: Float, height: Float, amount: Float ->
            lens.effect(size, radius, height, amount)
        }
    } else {
        { _: Size, _: Float, _: Float, _: Float -> null }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassContentLensApi33 {
    private val shader = RuntimeShader(InsetRoundedRectRefractionShader)

    fun effect(size: Size, radius: Float, height: Float, amount: Float): RenderEffect? {
        if (size.width <= 0f || size.height <= 0f || height <= 0f || amount <= 0.01f) return null
        shader.setFloatUniform("rect", 0f, 0f, size.width, size.height)
        shader.setFloatUniform("offset", 0f, 0f)
        shader.setFloatUniform("cornerRadius", radius.coerceIn(0f, minOf(size.width, size.height) / 2f))
        shader.setFloatUniform("refractionHeight", height)
        shader.setFloatUniform("refractionAmount", -amount)
        shader.setFloatUniform("depthEffect", 0f)
        return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
