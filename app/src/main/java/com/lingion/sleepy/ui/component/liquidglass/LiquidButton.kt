// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.lingion.sleepy.ui.component.liquidglass.InteractiveHighlight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCapsuleButtonRenderer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCapsuleButtonRequest
import com.lingion.sleepy.ui.glass.GlassBackdropDomain
import com.lingion.sleepy.ui.glass.GlassEffectFrame
import com.lingion.sleepy.ui.glass.GlassMaterialRole
import com.lingion.sleepy.ui.glass.GlassMaterialSpec
import com.lingion.sleepy.ui.glass.rememberGlassSurfaceDescriptor
import com.lingion.sleepy.ui.glass.sleepDownGlassSurface
import com.lingion.sleepy.ui.glass.ui.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

class LiquidButtonPressSnapshot {
    @Volatile
    var progress: Float = 0f
        internal set
}

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    height: Dp = 48f.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16f.dp),
    blurRadius: Dp = 2f.dp,
    lensHeight: Dp = 12f.dp,
    lensAmount: Dp = 24f.dp,
    chromaticAberration: Boolean = false,
    shadowEnabled: Boolean = true,
    shadowStyle: Shadow = Shadow.Default,
    highlightEnabled: Boolean = true,
    clickTargetEnabled: Boolean = true,
    pressExpansion: Dp = 4f.dp,
    highlightRadiusMultiplier: Float = 1.5f,
    staticPressDimAlpha: Float = 0f,
    shape: Shape = Capsule(),
    clipToBounds: Boolean = false,
    surfaceEnabled: Boolean = true,
    pressSnapshot: LiquidButtonPressSnapshot? = null,
    sharedInteractiveHighlight: InteractiveHighlight? = null,
    interactionEnabledAt: (size: Size, offset: Offset) -> Boolean = { _, _ -> true },
    flatOpacity: Float? = null,
    enabled: Boolean = true,
    nativeDarkOverride: Boolean? = null,
    preserveMaterialWhenDisabled: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    // Compatibility entry for existing feature buttons. Surface-only hosts (menus, fields,
    // shared gesture shells) retain their own geometry and do not become native click targets.
    val nativeRenderer = LocalNativeCapsuleButtonRenderer.current
    if (surfaceEnabled && (clickTargetEnabled || !enabled) && nativeRenderer != null) {
        val accent = listOf(surfaceColor, tint).firstOrNull { color ->
            color.isSpecified && color.alpha > 0f &&
                maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue) > 0.12f
        } ?: Color.Unspecified
        val dark = nativeDarkOverride ?: if (!accent.isSpecified && surfaceColor.isSpecified && surfaceColor.alpha > 0f)
            surfaceColor.luminance() < 0.5f else MaterialTheme.colorScheme.background.luminance() < 0.5f
        if (nativeRenderer(NativeCapsuleButtonRequest(onClick, modifier, height, contentPadding, dark,
                enabled = enabled, primaryColor = accent, acceptsGesture = interactionEnabledAt,
                preserveMaterialWhenDisabled = preserveMaterialWhenDisabled), content)) {
            pressSnapshot?.progress = 0f
            return
        }
    }
    val animationScope = rememberCoroutineScope()
    val clickInteractionSource = remember { MutableInteractionSource() }
    val pressed by clickInteractionSource.collectIsPressedAsState()
    val latestInteractionEnabledAt = rememberUpdatedState(interactionEnabledAt)

    val interactiveHighlight = remember(
        animationScope,
        highlightRadiusMultiplier,
        sharedInteractiveHighlight
    ) {
        sharedInteractiveHighlight ?: InteractiveHighlight(
            animationScope = animationScope,
            radius = { size -> size.minDimension * highlightRadiusMultiplier },
            acceptsGesture = { size, offset ->
                latestInteractionEnabledAt.value(size, offset)
            }
        )
    }
    val material = remember(
        blurRadius,
        lensHeight,
        lensAmount,
        chromaticAberration,
        tint,
        surfaceColor,
        shadowEnabled,
        highlightEnabled
    ) {
        GlassMaterialSpec(
            role = GlassMaterialRole.Control,
            blur = blurRadius,
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            surfaceAlpha = when {
                surfaceColor.isSpecified -> surfaceColor.alpha
                tint.isSpecified -> 0.22f
                else -> 0f
            },
            borderAlpha = 0f,
            highlightAlpha = if (highlightEnabled) 1f else 0f,
            shadowAlpha = if (shadowEnabled) 1f else 0f,
            innerShadowAlpha = 0f,
            chromaticAberration = chromaticAberration,
            depthEffect = false
        )
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "LiquidButton",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.Control,
        sceneKey = "liquid-button"
    )

    val buttonShapeBlock: () -> Shape = remember(shape) { { shape } }
    val buttonEffects: BackdropEffectScope.() -> Unit = remember(
        blurRadius, lensHeight, lensAmount, chromaticAberration
    ) {
        {
            vibrancy()
            blur(blurRadius.toPx())
            lens(lensHeight.toPx(), lensAmount.toPx(), chromaticAberration = chromaticAberration)
        }
    }
    val buttonOnDrawSurface: DrawScope.() -> Unit = remember(tint, surfaceColor) {
        {
            if (tint.isSpecified) {
                drawRect(tint.copy(alpha = 0.18f), blendMode = BlendMode.Hue)
                drawRect(tint.copy(alpha = 0.22f))
            }
            if (surfaceColor.isSpecified) drawRect(surfaceColor)
        }
    }
    Row(
        modifier
            .sleepDownGlassSurface(
                backdrop = backdrop,
                descriptor = descriptor,
                material = material,
                shape = buttonShapeBlock,
                effectFrame = GlassEffectFrame(blur = null),
                effectsOverride = buttonEffects,
                highlightOverride = if (highlightEnabled) ({ Highlight.Default }) else null,
                shadowOverride = if (shadowEnabled) ({ shadowStyle }) else null,
                additionalLayerBlock = if (isInteractive) {
                    {
                        val width = size.width
                        val height = size.height

                        val progress = interactiveHighlight.pressProgress
                        pressSnapshot?.progress = progress
                        val expansionPx = pressExpansion.toPx()
                        val scale = lerp(1f, 1f + expansionPx / size.height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = expansionPx / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                    maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                    (width / height).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                    maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                    (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = buttonOnDrawSurface,
                fallbackColor = surfaceColor.takeIf { it.isSpecified } ?: tint.takeIf { it.isSpecified },
                fallbackOpacity = flatOpacity,
                clipToBounds = clipToBounds,
                renderEnabled = { surfaceEnabled }
            )
            .then(
                if (!isInteractive && staticPressDimAlpha > 0f) {
                    Modifier.drawWithContent {
                        drawContent()
                        if (pressed) {
                            drawRect(Color.Black.copy(alpha = staticPressDimAlpha.coerceIn(0f, 1f)))
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (clickTargetEnabled) {
                    Modifier.clickable(
                        interactionSource = clickInteractionSource,
                        indication = when {
                            isInteractive -> null
                            staticPressDimAlpha > 0f -> null
                            else -> LocalIndication.current
                        },
                        role = Role.Button,
                        enabled = enabled,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(
                            if (sharedInteractiveHighlight == null) {
                                interactiveHighlight.gestureModifier
                            } else {
                                Modifier
                            }
                        )
                } else {
                    Modifier
                }
            )
            .height(height)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Applies the exact same press expansion and pointer-following stretch as [LiquidButton] to a
 * sibling foreground layer. Editable fields can use this while remaining outside the backdrop
 * consumer, so their blinking caret does not invalidate the glass surface beneath them.
 */
fun Modifier.liquidButtonVisualTransform(
    interactiveHighlight: InteractiveHighlight,
    pressExpansion: Dp = 4f.dp,
    dragExpansion: Dp = pressExpansion
): Modifier = graphicsLayer {
    val progress = interactiveHighlight.pressProgress
    val safeHeight = size.height.coerceAtLeast(1f)
    val safeWidth = size.width.coerceAtLeast(1f)
    val expansionPx = pressExpansion.toPx()
    val scale = lerp(1f, 1f + expansionPx / safeHeight, progress)
    val maxOffset = size.minDimension.coerceAtLeast(1f)
    val offset = interactiveHighlight.offset
    translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
    translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
    val maxDragScale = dragExpansion.toPx() / safeHeight
    val offsetAngle = atan2(offset.y, offset.x)
    scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension.coerceAtLeast(1f)) *
        (safeWidth / safeHeight).fastCoerceAtMost(1f)
    scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension.coerceAtLeast(1f)) *
        (safeHeight / safeWidth).fastCoerceAtMost(1f)
}

@Composable
fun Modifier.liquidButtonInteraction(
    onClick: () -> Unit,
    isInteractive: Boolean = true,
    showHighlight: Boolean = true
): Modifier {
    if (!isInteractive) {
        return clickable(role = Role.Button, onClick = onClick)
    }
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    return graphicsLayer {
        val progress = interactiveHighlight.pressProgress
        val height = size.height.coerceAtLeast(1f)
        val width = size.width.coerceAtLeast(1f)
        val scale = lerp(1f, 1f + 4f.dp.toPx() / height, progress)
        val maxOffset = size.minDimension.coerceAtLeast(1f)
        val offset = interactiveHighlight.offset
        translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
        translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
        val maxDragScale = 4f.dp.toPx() / height
        val offsetAngle = atan2(offset.y, offset.x)
        scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension.coerceAtLeast(1f)) *
            (width / height).fastCoerceAtMost(1f)
        scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension.coerceAtLeast(1f)) *
            (height / width).fastCoerceAtMost(1f)
    }
        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
        .then(if (showHighlight) interactiveHighlight.modifier else Modifier)
        .then(interactiveHighlight.gestureModifier)
}
