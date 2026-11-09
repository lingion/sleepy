package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.drawPlainBackdrop
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** Separate scope for a single optical pane shared by multiple actions. */
internal val LocalGlassGrouped = androidx.compose.runtime.compositionLocalOf { false }

enum class GlassMaterialRole { Regular, Menu, Alert }

/** Popup windows paint locally while sampling the parent window's glass-free scene. */
internal val LocalGlassOverlay = androidx.compose.runtime.compositionLocalOf { false }

/** Adds optical material without owning clicks or introducing a second semantic node. */
@Composable
fun Modifier.glassControl(
    shape: Shape = CircleShape,
    tint: Color? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    grouped: Boolean = false,
    registerPane: Boolean = true,
    role: GlassMaterialRole = GlassMaterialRole.Regular,
    interactionSource: MutableInteractionSource? = null,
    motionClearance: Dp = Dp.Unspecified,
): Modifier {
    check(!LocalGlassSourceContent.current) { "An optical consumer cannot be recorded as an ordinary source" }
    val host = LocalGlassHost.current
    val consumerDepth = LocalGlassDepth.current
    val isOverlay = LocalGlassOverlay.current
    if (LocalUiStyle.current != UiStyle.LiquidGlass || host == null || LocalGlassGrouped.current) return this
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val material = if ((enabled || selected) && (tint != null || selected)) LiquidGlassStyle.tinted(colors, tint ?: colors.primary, dark)
        else when (role) {
            GlassMaterialRole.Regular -> LiquidGlassStyle.regular(colors, dark)
            GlassMaterialRole.Menu -> LiquidGlassStyle.menu(colors, dark)
            GlassMaterialRole.Alert -> LiquidGlassStyle.alert(colors, dark)
        }
    val rim = when (role) {
        GlassMaterialRole.Regular -> null
        GlassMaterialRole.Menu -> LiquidGlassRim.menu(dark)
        GlassMaterialRole.Alert -> LiquidGlassRim.alert(dark)
    }
    val samplingHost = if (isOverlay) LocalGlassOverlaySamplingHost.current ?: host else host
    val filterBand = samplingHost.filterBand(consumerDepth, isOverlay, material.blur.toFloat())
    val backdrop = remember(samplingHost, consumerDepth, isOverlay, filterBand) {
        filterBand?.backdrop(samplingHost) ?: if (isOverlay) samplingHost.overlayBackdrop else samplingHost.backdropAt(consumerDepth)
    }
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var pull by remember { mutableStateOf(Offset.Zero) }
    var down by remember { mutableStateOf(false) }
    var motion by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current.density
    val pressed = interactionSource?.collectIsPressedAsState()?.value == true
    val active = down || pressed
    val pressSpring = spring<Float>(dampingRatio = 0.593f, stiffness = 311f)
    androidx.compose.runtime.LaunchedEffect(enabled, active) {
        if (!enabled) {
            motion?.cancel()
            down = false
            pull = Offset.Zero
            lift.snapTo(0f)
        } else if (active) {
            motion?.cancel()
            motion = scope.launch { lift.animateTo(1f, pressSpring) }
        } else {
            val swelling = motion
            motion = scope.launch {
                swelling?.join()
                lift.animateTo(0f, pressSpring)
            }
        }
    }
    return this
        .graphicsLayer {
            val growth = min(0.25f, 16f * density / size.maxDimension.coerceAtLeast(1f))
            val swell = 1f + lift.value * growth
            scaleX = swell * (1f + abs(pull.x) / size.width.coerceAtLeast(1f) * 0.5f)
            scaleY = swell * (1f + abs(pull.y) / size.height.coerceAtLeast(1f) * 0.5f)
            translationX = pull.x
            translationY = pull.y
            if (motionClearance.value.isFinite()) {
                // Dense controls own a measured gutter. Keep every animated pixel inside it,
                // including spring overshoot and rubber-band translation.
                val clearance = motionClearance.value.coerceAtLeast(0f) * density
                scaleX = scaleX.coerceAtMost(1f + 2f * clearance / size.width.coerceAtLeast(1f))
                scaleY = scaleY.coerceAtMost(1f + 2f * clearance / size.height.coerceAtLeast(1f))
                val xRoom = (clearance - size.width * (scaleX - 1f) / 2f).coerceAtLeast(0f)
                val yRoom = (clearance - size.height * (scaleY - 1f) / 2f).coerceAtLeast(0f)
                translationX = pull.x.coerceIn(-xRoom, xRoom)
                translationY = pull.y.coerceIn(-yRoom, yRoom)
            }
        }
        .pointerInput(enabled, grouped) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointer = event.changes.firstOrNull() ?: continue
                    if (pointer.pressed && !down) {
                        down = true
                        origin = pointer.position
                        pull = Offset.Zero
                    } else if (pointer.pressed && down) {
                        val limit = min(size.width, size.height) * (7f / 32f)
                        fun rubber(v: Float): Float = if (limit > 0f) FlClashDockPhysics.rubberBand(v, limit) else 0f
                        val delta = pointer.position - origin
                        pull = Offset(rubber(delta.x), rubber(delta.y))
                    } else if (!pointer.pressed && down) {
                        down = false
                        pull = Offset.Zero
                    }
                }
            }
        }
        .drawPlainBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                liquidGlass(
                    context = context,
                    style = material,
                    sampledBlurPx = if (filterBand?.ready == true) material.blur.toFloat() * density else 0f,
                    outline = if (size.width > 0f && size.height > 0f && size.width.isFinite() && size.height.isFinite())
                        (shape.createOutline(size, layoutDirection, this) as? androidx.compose.ui.graphics.Outline.Generic)?.path?.let { pxPath ->
                            androidx.compose.ui.graphics.Path().apply {
                                addPath(pxPath)
                                transform(androidx.compose.ui.graphics.Matrix().apply { scale(1f / density, 1f / density) })
                            }
                        } else null,
                    rim = rim,
                    glow = LiquidGlassGlow(
                        center = origin / density,
                        reach = (28f + 3f * lift.value).toDouble(),
                        strength = (0.06f * lift.value).toDouble(),
                        wash = (0.7f * lift.value).toDouble(),
                    ),
                )
            },
            onDrawFront = {
                if (rim != null && size.width > 0f && size.height > 0f) {
                    // liquid_glass.dart LiquidGlassRim.paintLine: exterior black hairline.
                    val radius = when (shape) {
                        is FlClashGlassShape -> shape.radiusDp * density
                        else -> size.minDimension / 2f
                    }.coerceAtMost(size.minDimension / 2f)
                    val across = (radius / size.width).coerceAtMost(0.4f)
                    val gradient = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = rim.side.toFloat()),
                        across / 4f to Color.Black.copy(alpha = rim.corner.toFloat()),
                        across to Color.Black.copy(alpha = rim.flat.toFloat()),
                        (1f - across) to Color.Black.copy(alpha = rim.flat.toFloat()),
                        (1f - across / 4f) to Color.Black.copy(alpha = rim.corner.toFloat()),
                        1f to Color.Black.copy(alpha = rim.side.toFloat()),
                    )
                    when (val outline = shape.createOutline(size, layoutDirection, this)) {
                        is androidx.compose.ui.graphics.Outline.Generic -> drawPath(outline.path, gradient, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f * density))
                        is androidx.compose.ui.graphics.Outline.Rounded -> {
                            val path = androidx.compose.ui.graphics.Path().apply { addRoundRect(outline.roundRect) }
                            drawPath(path, gradient, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f * density))
                        }
                        else -> drawRect(gradient, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f * density))
                    }
                }
            },
            onDrawSurface = {
                // API33 shader includes the tint; lower APIs must remain legible without
                // pretending their blur/vibrancy path provides equivalent refraction.
                if (android.os.Build.VERSION.SDK_INT < 33) drawRect(material.tint)
            },
        )
}

/** Original backgrounds disappear only when an actual glass host is available. */
@Composable
fun controlFill(classic: Color): Color =
    if (LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null) Color.Transparent else classic
