package com.lingion.sleepy.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Two signed-distance lobes share one sampled surface; controls are separate siblings. */
internal data class GlassDropletGeometry(
    val body: Rect,
    val bodyRadius: Float,
    val drop: Rect,
    val dropRadius: Float,
    val joinRadius: Float,
    val dropTaper: Float = 0f
)

private fun dropletOutline(geometry: GlassDropletGeometry, drop: Rect = geometry.drop): Path {
    val radius = geometry.dropRadius.coerceIn(0f, minOf(drop.width, drop.height) / 2f)
    val path = Path()
    fun appendPoint(x: Float, y: Float, first: Boolean = false) {
        val normalizedY = ((y - drop.top) / drop.height.coerceAtLeast(1f)) * 2f - 1f
        val widthScale = 1f + geometry.dropTaper * normalizedY
        val warpedX = drop.center.x + (x - drop.center.x) * widthScale
        if (first) path.moveTo(warpedX, y) else path.lineTo(warpedX, y)
    }
    for (corner in 0..3) {
        val centerX = if (corner == 0 || corner == 3) drop.right - radius else drop.left + radius
        val centerY = if (corner < 2) drop.bottom - radius else drop.top + radius
        for (step in 0..16) {
            val angle = (corner + step / 16f) * PI.toFloat() / 2f
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            appendPoint(x, y, first = corner == 0 && step == 0)
        }
    }
    path.close()
    return path
}

/** Content and sampled glass use the same tapered outline so rows stay inside the shell. */
internal class GlassDropletContentShape(
    private val geometry: () -> GlassDropletGeometry
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val current = geometry()
        return Outline.Generic(dropletOutline(current, Rect(Offset.Zero, current.drop.size)))
    }
}

/** Interpolate premultiplied coverage so an opaque accent does not turn muddy on its way to glass. */
internal fun premultipliedGlassTint(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    val a = start.convert(ColorSpaces.Srgb)
    val b = end.convert(ColorSpaces.Srgb)
    val alpha = lerp(a.alpha, b.alpha, t)
    if (alpha <= 0f) return Color.Transparent
    return Color(
        red = lerp(a.red * a.alpha, b.red * b.alpha, t) / alpha,
        green = lerp(a.green * a.alpha, b.green * b.alpha, t) / alpha,
        blue = lerp(a.blue * a.alpha, b.blue * b.alpha, t) / alpha,
        alpha = alpha
    )
}

// A polynomial smooth minimum gives the joining neck a continuous normal. The same distance
// controls coverage, refraction and edge light, so there is no pasted-on bridge between surfaces.
private const val DropletGlassShader = """
uniform shader content;
uniform float2 offset;
uniform float4 body;
uniform float4 drop;
uniform float2 radii;
uniform float joinRadius;
uniform float edgeWidth;
uniform float lensHeight;
uniform float lensAmount;
uniform float shadowAlpha;
uniform float shadowRadius;
layout(color) uniform half4 tint;
layout(color) uniform half4 dropTint;
uniform float dropTaper;

float roundedDistance(float2 p, float4 rect, float radius) {
    float2 halfSize = rect.zw * 0.5;
    float2 q = abs(p - rect.xy - halfSize) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float smoothJoin(float a, float b, float amount) {
    float k = max(amount, 0.001);
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}
float distanceToGlass(float2 p) {
    float a = body.z > 0.0 ? roundedDistance(p, body, radii.x) : 1e6;
    float normalizedY = clamp((p.y - drop.y) / max(drop.w, 1.0), 0.0, 1.0) * 2.0 - 1.0;
    float widthScale = 1.0 + dropTaper * normalizedY;
    float centerX = drop.x + drop.z * 0.5;
    float2 warped = float2((p.x - centerX) / widthScale + centerX, p.y);
    float b = drop.z > 0.0 ? roundedDistance(warped, drop, radii.y) : 1e6;
    return smoothJoin(a, b, joinRadius);
}
half4 main(float2 coord) {
    float2 p = coord + offset;
    float sd = distanceToGlass(p);
    float coverage = 1.0 - smoothstep(-edgeWidth, edgeWidth, sd);
    float shadow = shadowAlpha * (1.0 - smoothstep(0.0, shadowRadius, max(sd, 0.0))) * (1.0 - coverage);
    if (coverage < 0.001) return half4(0.0, 0.0, 0.0, shadow);
    float2 refracted = coord;
    float shine = 0.0;
    if (sd > -lensHeight) {
        float epsilon = max(edgeWidth, 0.5);
        float2 grad = float2(
            distanceToGlass(p + float2(epsilon, 0.0)) - distanceToGlass(p - float2(epsilon, 0.0)),
            distanceToGlass(p + float2(0.0, epsilon)) - distanceToGlass(p - float2(0.0, epsilon))
        );
        grad /= max(length(grad), 0.0001);
        float edge = clamp(1.0 + min(sd, 0.0) / max(lensHeight, 0.001), 0.0, 1.0);
        float bend = (1.0 - sqrt(max(1.0 - edge * edge, 0.0))) * lensAmount;
        refracted -= grad * bend;
        shine = pow(edge, 12.0) * (0.025 + 0.13 * max(dot(grad, float2(-0.6, -0.8)), 0.0));
    }
    half4 sampleColor = content.eval(refracted);
    // The Dock is in front of the returning drop. Its translucent surface must not allow the
    // blue lobe to bleed through inside the capsule, even before the two outlines fully merge.
    float colorBlend = drop.z <= 0.0 ? 0.0 : body.z > 0.0 ? smoothstep(0.0, max(joinRadius * 0.5, edgeWidth),
        roundedDistance(p, body, radii.x)) : 1.0;
    half4 localTint = mix(half4(tint.rgb * tint.a, tint.a),
        half4(dropTint.rgb * dropTint.a, dropTint.a), colorBlend);
    half4 color = localTint + sampleColor * (1.0 - localTint.a);
    color.rgb = mix(color.rgb, half3(color.a), shine);
    return color * coverage + half4(0.0, 0.0, 0.0, shadow);
}
"""

/** The caller bounds this to the Dock/menu envelope, never a full-screen animated effect. */
@Composable
internal fun Modifier.sleepDownDropletGlass(
    backdrop: Backdrop,
    geometry: () -> GlassDropletGeometry,
    surfaceColor: Color,
    blurRadius: Dp,
    debugLabel: String = "HomeDockDroplet",
    dropColor: () -> Color = { surfaceColor },
    motionBlurPx: () -> Float = { 0f },
    baseBlurRadiusPx: (() -> Float)? = null,
    lensHeight: Dp = 12.dp,
    lensAmount: Dp = 24.dp,
    lensHeightPx: (() -> Float)? = null,
    lensAmountPx: (() -> Float)? = null,
    shadowAlpha: Float = 0f
): Modifier {
    val currentGeometry = rememberUpdatedState(geometry)
    val shaderSupported = isRuntimeShaderSupported() &&
        com.lingion.sleepy.ui.glass.performance.AppMaterialPreferences.policy.denseMaterials
    val currentDropColor = rememberUpdatedState(dropColor)
    val currentMotionBlur = rememberUpdatedState(motionBlurPx)
    val currentBaseBlur = rememberUpdatedState(baseBlurRadiusPx)
    val currentLensHeight = rememberUpdatedState(lensHeightPx)
    val currentLensAmount = rememberUpdatedState(lensAmountPx)
    val fallbackShape: () -> Shape = {
        val value = currentGeometry.value()
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val body = Path().apply { addRoundRect(RoundRect(value.body, CornerRadius(value.bodyRadius))) }
                val drop = dropletOutline(value)
                return Outline.Generic(Path.combine(PathOperation.Union, body, drop))
            }
        }
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = debugLabel,
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.Pill
    )
    val material = GlassMaterialSpec.pill().copy(
        blur = blurRadius, lensHeight = lensHeight, lensAmount = lensAmount,
        surfaceAlpha = surfaceColor.alpha
    )
    return sleepDownGlassSurface(
        backdrop = backdrop, descriptor = descriptor, material = material, fallbackColor = surfaceColor,
        shape = { if (shaderSupported) RectangleShape else fallbackShape() },
        effectFrame = GlassEffectFrame(blur = blurRadius),
        effectsOverride = {
            vibrancy()
            blur((currentBaseBlur.value?.invoke() ?: blurRadius.toPx()) + currentMotionBlur.value())
            if (shaderSupported) {
                // Keep the sampling origin and RenderTarget stable while the blur changes.
                // The fixed control envelope already includes the union's neck and shadow.
                padding = maxOf(padding, 16.dp.toPx())
                val value = currentGeometry.value()
                runtimeShaderEffect("SleepDownDockDropletV1", DropletGlassShader, "content") {
                    setFloatUniform("offset", -padding - effectOffset.x, -padding - effectOffset.y)
                    setFloatUniform("body", value.body.left, value.body.top, value.body.width, value.body.height)
                    setFloatUniform("drop", value.drop.left, value.drop.top, value.drop.width, value.drop.height)
                    setFloatUniform("radii", value.bodyRadius, value.dropRadius)
                    setFloatUniform("joinRadius", value.joinRadius)
                    setFloatUniform("dropTaper", value.dropTaper)
                    setFloatUniform("edgeWidth", 0.5.dp.toPx())
                    setFloatUniform("lensHeight", currentLensHeight.value?.invoke() ?: lensHeight.toPx())
                    setFloatUniform("lensAmount", currentLensAmount.value?.invoke() ?: lensAmount.toPx())
                    setFloatUniform("shadowAlpha", shadowAlpha)
                    setFloatUniform("shadowRadius", 8.dp.toPx())
                    setColorUniform("tint", surfaceColor)
                    val color = currentDropColor.value()
                    setColorUniform("dropTint", color)
                }
            }
        },
        onDrawSurface = if (shaderSupported) null else ({
            val value = currentGeometry.value()
            val bodyMask = Path().apply {
                if (value.body.width > 0f) addRoundRect(RoundRect(value.body, CornerRadius(value.bodyRadius)))
            }
            clipPath(bodyMask) { drawRect(surfaceColor) }
            clipPath(bodyMask, ClipOp.Difference) {
                drawPath(dropletOutline(value), currentDropColor.value())
            }
        })
    )
}
