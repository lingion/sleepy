// Adapted from NexioSchedule by HaoZai000 (AGPL-3.0); see THIRD_PARTY_NOTICES.md.
// Uses Backdrop 2's node-owned shader cache and already-scaled effect coordinates.
package com.lingion.sleepy.ui.glass.ui

import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect

internal fun BackdropEffectScope.nexioProgressiveBlur(radius: Float, tint: Color, intensity: Float, fadeStart: Float) {
    padding = radius
    val bufferW = size.width + 2f * padding
    val bufferH = size.height + 2f * padding
    runtimeShaderEffect("NexioProgressiveRadial", PROGRESSIVE_BLUR_SHADER, "content") {
        setFloatUniform("contentOrigin", padding, padding)
        setFloatUniform("contentSize", size.width, size.height)
        setFloatUniform("bufferSize", bufferW, bufferH)
        setFloatUniform("maxRadius", radius)
        setFloatUniform("radiusFadeStart", fadeStart.coerceIn(0f, 0.95f))
        setColorUniform("tint", tint)
        setFloatUniform("tintIntensity", intensity.coerceIn(0f, 1f))
    }
    runtimeShaderEffect("NexioProgressiveDenoise", PROGRESSIVE_DENOISE_SHADER, "content") {
        setFloatUniform("contentOrigin", padding, padding)
        setFloatUniform("contentSize", size.width, size.height)
        setFloatUniform("bufferSize", bufferW, bufferH)
        setFloatUniform("maxRadius", radius)
        setFloatUniform("radiusFadeStart", fadeStart.coerceIn(0f, 0.95f))
    }
}

// 两端导数为 0 的 S 曲线，收尾比 smoothstep 更绵
private const val SOFTER_STEP = """
float softerstep(float a, float b, float x) {
    float s = clamp((x - a) / max(b - a, 0.0001), 0.0, 1.0);
    return s * s * s * (s * (s * 6.0 - 15.0) + 10.0);
}
"""

private const val PROGRESSIVE_BLUR_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;
uniform float radiusFadeStart;
layout(color) uniform half4 tint;
uniform float tintIntensity;

$SOFTER_STEP

float hash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 progressiveBlur(float2 coord, float radius) {
    if (radius < 0.5) {
        return content.eval(coord);
    }
    half4 sum = half4(0.0);
    float wsum = 0.0;
    // 高半径时采样点距大于笔画宽，固定螺旋会把文字打成星点；
    // 逐像素旋转核 + 径向扰动，把规则点阵打散成细噪。
    float spin = hash12(coord) * 6.2831853;
    for (int i = 0; i < 32; i++) {
        float fi = float(i);
        float r = radius * pow((fi + 0.5) / 32.0, 0.5);
        r *= 0.90 + 0.20 * hash12(coord + float2(fi, 1.7));
        float a = fi * 2.39996323 + spin;
        float2 o = float2(cos(a), sin(a)) * r;
        float w = exp(-r * r / max(0.85 * radius * radius, 0.001));
        float2 sc = clamp(coord + o, float2(0.0), max(bufferSize - 1.0, float2(0.0)));
        half4 c = content.eval(sc);
        // 近透明样本不参与平均，避免顶边空隙把 alpha 洗掉、露出清晰层
        if (c.a > 0.02) {
            sum += c * w;
            wsum += w;
        }
    }
    if (wsum < 0.0001) {
        return content.eval(coord);
    }
    return sum / wsum;
}

half4 main(float2 coord) {
    // 可见区域从 contentOrigin 起算，padding 边距不参与 Y 渐变
    float t = clamp((coord.y - contentOrigin.y) / max(contentSize.y, 1.0), 0.0, 1.0);
    float u = 1.0 - smoothstep(radiusFadeStart, 1.0, t);
    float radius = maxRadius * u;
    half4 color = progressiveBlur(coord, radius);
    // 仅末端收透明度，消掉与下方清晰内容的接缝
    float edge = softerstep(0.88, 1.0, t);
    color *= (1.0 - edge);
    if (tintIntensity > 0.0) {
        half4 tintPremul = half4(tint.rgb * tint.a, tint.a);
        float amount = tintIntensity * u;
        color = tintPremul * (amount * (1.0 - edge)) + color * (1.0 - tint.a * amount);
    }
    return color;
}
"""

/** 对上一阶段结果做小半径盒式平均，压掉抖动细噪；半径与模糊强度成正比。 */
private const val PROGRESSIVE_DENOISE_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;
uniform float radiusFadeStart;

$SOFTER_STEP

half4 main(float2 coord) {
    float t = clamp((coord.y - contentOrigin.y) / max(contentSize.y, 1.0), 0.0, 1.0);
    // 与渐进模糊同一套曲线，避免两阶段半径不一致
    float u = 1.0 - smoothstep(radiusFadeStart, 1.0, t);
    float r = maxRadius * u * 0.18;
    if (r < 0.4) {
        return content.eval(coord);
    }
    half4 sum = content.eval(coord);
    float wsum = 1.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5235987756;
        float2 o = float2(cos(a), sin(a)) * r;
        float2 sc = clamp(coord + o, float2(0.0), max(bufferSize - 1.0, float2(0.0)));
        half4 c = content.eval(sc);
        if (c.a > 0.02) {
            sum += c;
            wsum += 1.0;
        }
    }
    return sum / wsum;
}
"""
