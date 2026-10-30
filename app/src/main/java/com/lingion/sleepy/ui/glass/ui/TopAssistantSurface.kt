package com.lingion.sleepy.ui.glass.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.lingion.sleepy.ui.component.liquidglass.liquidButtonVisualTransform
import com.lingion.sleepy.ui.component.liquidglass.InteractiveHighlight
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.lingion.sleepy.ui.glass.GlassMorphAllocation
import com.lingion.sleepy.ui.glass.insetShapeFor
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Shared by the education island, home reminder and home conversation. */
@Stable
internal class TopAssistantMotion(initialValue: Float = 0f) {
    val drop = Animatable(initialValue)
    val spread = Animatable(initialValue)

    suspend fun animateTo(target: Float, overshoot: Boolean = true) = coroutineScope {
        fun spec(start: Float, vertical: Boolean) = if (overshoot) {
            topAssistantBezierSpec(start, target, vertical)
        } else tween<Float>(if (target > start) 520 else 420, easing = TopAssistantSettleEasing)
        launch { drop.animateTo(target, spec(drop.value, true)) }
        launch { spread.animateTo(target, spec(spread.value, false)) }
    }

    suspend fun snapTo(target: Float) {
        drop.snapTo(target)
        spread.snapTo(target)
    }
}

internal val TopAssistantOpenEasing = CubicBezierEasing(0.18f, 0.76f, 0.20f, 1f)
private val TopAssistantCloseEasing = CubicBezierEasing(0.34f, 0.02f, 0.30f, 1f)
private val TopAssistantReboundEasing = CubicBezierEasing(0.22f, 0f, 0.28f, 1f)
private val TopAssistantSettleEasing = CubicBezierEasing(0.20f, 0.72f, 0.26f, 1f)

/** Authored Bezier beats, not a platform/Compose spring simulation. */
internal fun topAssistantBezierSpec(start: Float, target: Float, vertical: Boolean): FiniteAnimationSpec<Float> {
    val opening = target > start
    val duration = if (opening) { if (vertical) 640 else 580 } else 460
    val travel = target - start
    return keyframes {
        durationMillis = duration
        start at 0 using if (opening) TopAssistantOpenEasing else TopAssistantCloseEasing
        (target + travel * if (vertical) 0.025f else 0.016f) at (duration * 0.72f).roundToInt() using TopAssistantReboundEasing
        (target - travel * 0.003f) at (duration * 0.91f).roundToInt() using TopAssistantSettleEasing
        target at duration
    }
}

@Composable
internal fun rememberTopAssistantMotion(initialValue: Float = 0f): TopAssistantMotion =
    remember { TopAssistantMotion(initialValue) }

/** Dissolve the last few pixels instead of exposing a hard seam against an OEM cutout. */
internal fun topAssistantDockAlpha(progress: Float, fadeEnd: Float = 0.155f): Float {
    val t = ((progress - 0.015f) / (fadeEnd - 0.015f).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun topAssistantMorphRect(source: Rect, target: Rect, drop: Float, spread: Float): Rect {
    val y = drop.coerceIn(-0.04f, 1.04f)
    val x = spread.coerceIn(-0.04f, 1.04f)
    val width = (source.width + (target.width - source.width) * x).coerceAtLeast(1f)
    val height = (source.height + (target.height - source.height) * y).coerceAtLeast(1f)
    val centerX = source.center.x + (target.center.x - source.center.x) * x
    val top = source.top + (target.top - source.top) * y
    return Rect(centerX - width / 2f, top, centerX + width / 2f, top + height)
}

/** Measure the *final* paragraph layout even when its revealing shell is only a few pixels wide. */
internal fun Modifier.fixedAssistantContentSize(width: Dp, height: Dp): Modifier = layout { measurable, constraints ->
    val finalWidth = width.roundToPx().coerceAtLeast(1)
    val finalHeight = height.roundToPx().coerceAtLeast(1)
    val child = measurable.measure(Constraints.fixed(finalWidth, finalHeight))
    layout(constraints.constrainWidth(finalWidth), constraints.constrainHeight(finalHeight)) {
        child.place(0, 0)
    }
}

/** Natural card height at its final width, independent of the revealing shell's constraints. */
internal fun Modifier.fixedAssistantContentWidth(width: Dp, maxHeight: Dp): Modifier = layout { measurable, constraints ->
    val finalWidth = width.roundToPx().coerceAtLeast(1)
    val child = measurable.measure(Constraints(
        minWidth = finalWidth, maxWidth = finalWidth, maxHeight = maxHeight.roundToPx().coerceAtLeast(1)
    ))
    layout(constraints.constrainWidth(finalWidth), constraints.constrainHeight(child.height)) { child.place(0, 0) }
}

internal fun Modifier.assistantMorphBounds(bounds: () -> Rect): Modifier = layout { measurable, _ ->
    val frame = bounds()
    val width = frame.width.roundToInt().coerceAtLeast(1)
    val height = frame.height.roundToInt().coerceAtLeast(1)
    val child = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { child.place(0, 0) }
}

@Composable
internal fun TopAssistantSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    shape: Shape,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    edgeEffectsEnabled: Boolean = true,
    refractionEnabled: Boolean = true,
    opaqueHeaderHeight: Dp = 0.dp,
    bottomShadeAlpha: Float = 0.04f,
    interactiveHighlight: InteractiveHighlight? = null,
    morphAllocation: GlassMorphAllocation? = null,
    shapeProvider: (() -> Shape)? = null,
    surfaceFrame: (() -> Pair<Dp, Float>)? = null,
    materialEnabled: Boolean = true,
    nativeMaterial: Boolean = false,
    nativeFollowLightIntensityScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier.then(if (interactiveHighlight != null)
            Modifier.liquidButtonVisualTransform(interactiveHighlight, pressExpansion = 1.5.dp) else Modifier
        ).then(if (glow) Modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val edge = Brush.linearGradient(
                listOf(Color(0xFF81BDFF), Color(0xFFCCB2FF), Color(0xFF8FF4DC)),
                start = Offset(0f, size.height * 0.35f), end = Offset(size.width, size.height)
            )
            onDrawBehind {
                drawOutline(outline, edge, alpha = 0.06f, style = Stroke(14.dp.toPx()))
                drawOutline(outline, edge, alpha = 0.12f, style = Stroke(6.dp.toPx()))
                drawOutline(outline, edge, alpha = 0.46f, style = Stroke(1.dp.toPx()))
            }
        } else Modifier)
    ) {
        if (materialEnabled) GlassSurface(
            nativeMaterial = nativeMaterial,
            nativeFollowLightIntensityScale = nativeFollowLightIntensityScale,
            nativeDark = true,
            backdrop = backdrop,
            config = config,
            modifier = Modifier.matchParentSize(),
            shape = shape,
            shapeProvider = shapeProvider,
            morphAllocation = morphAllocation,
            baseSurfaceColorOverride = Color.Black,
            tokens = GlassTokens.dialog(1f).copy(
                blur = 12.dp, surfaceAlpha = 0.12f, lensHeight = 24.dp, lensAmount = 42.dp,
                chromaticAberration = false, highlightAlpha = 0.08f, shadowAlpha = 0f
            ).let { tokens -> if (refractionEnabled) tokens else tokens.copy(lensHeight = 0.dp, lensAmount = 0.dp, depthEffect = false)
            }.let { tokens -> if (edgeEffectsEnabled) tokens else tokens.copy(
                lensHeight = 0.dp, lensAmount = 0.dp, borderAlpha = 0f, highlightAlpha = 0f,
                innerShadowAlpha = 0f, depthEffect = false
            ) },
            debugLabel = "TopAssistantSurface"
        ) {}
        Box(
            Modifier.fillMaxSize().then(if (morphAllocation == null) Modifier.clip(shape) else Modifier.graphicsLayer {
                this.shape = morphAllocation.envelope.insetShapeFor(morphAllocation.geometry())
                clip = true
            }).drawWithCache {
                val bounds = morphAllocation?.localBounds() ?: Rect(Offset.Zero, size)
                val frame = surfaceFrame?.invoke()
                val header = (frame?.first ?: opaqueHeaderHeight).toPx().coerceIn(0f, bounds.height)
                val bottomShade = (frame?.second ?: bottomShadeAlpha).coerceIn(0f, 1f)
                fun shade(alpha: Float) = Color.Black.copy(alpha = alpha + (1f - alpha) * bottomShade)
                val shade = Brush.verticalGradient(
                    0f to shade(0.98f),
                    0.30f to shade(0.88f),
                    0.58f to shade(0.66f),
                    0.82f to shade(0.18f),
                    1f to shade(0f),
                    startY = bounds.top + header,
                    endY = maxOf(bounds.top + header + 1f, bounds.bottom)
                )
                onDrawBehind { drawRect(shade, bounds.topLeft, bounds.size) }
            }.then(if (glow) Modifier.drawWithCache {
                val radius = size.width * 0.70f
                val cyan = Brush.radialGradient(
                    listOf(Color(0xFF79C6FF).copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height + radius * 0.38f), radius = radius
                )
                val green = Brush.radialGradient(
                    listOf(Color(0xFFB0F4BA).copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.90f, size.height + radius * 0.50f), radius = radius
                )
                onDrawBehind { drawRect(cyan); drawRect(green) }
            } else Modifier).then(interactiveHighlight?.foregroundModifier ?: Modifier),
            content = content
        )
    }
}
