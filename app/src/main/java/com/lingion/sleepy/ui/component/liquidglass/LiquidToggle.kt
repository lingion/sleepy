// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.lingion.sleepy.ui.component.liquidglass.DampedDragAnimation
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.lingion.sleepy.ui.glass.GlassBackdropDomain
import com.lingion.sleepy.ui.glass.GlassEffectFrame
import com.lingion.sleepy.ui.glass.GlassMaterialRole
import com.lingion.sleepy.ui.glass.GlassMaterialSpec
import com.lingion.sleepy.ui.glass.glassBackdropProducer
import com.lingion.sleepy.ui.glass.rememberGlassCombinedBackdrop
import com.lingion.sleepy.ui.glass.rememberGlassLayerBackdrop
import com.lingion.sleepy.ui.glass.rememberGlassSurfaceDescriptor
import com.lingion.sleepy.ui.glass.sleepDownGlassSurface
import com.lingion.sleepy.ui.glass.ui.*
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true
) {
    if (com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeSwitchRenderer.current?.invoke(
            com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSwitchRequest(
                selected(), onSelect, modifier, enabled, compact
            )) == true) return
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) Color(0xFF34C759) else Color(0xFF30D158)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val trackWidth = if (compact) 52.dp else 64.dp
    val trackHeight = if (compact) 24.dp else 28.dp
    val thumbWidth = if (compact) 32.dp else 40.dp
    val thumbHeight = if (compact) 20.dp else 24.dp
    val dragWidth = with(density) { (trackWidth - thumbWidth - 4.dp).toPx() }
    val animationScope = rememberCoroutineScope()
    // The drag object survives recomposition, but permissions and the selected widget can change.
    val currentOnSelect by rememberUpdatedState(onSelect)
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    var targetSelected by remember { mutableStateOf(selected()) }
    val dampedDragAnimation = remember(animationScope, compact, dragWidth, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = if (compact) 1.2f else 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    targetSelected = fraction == 1f
                    currentOnSelect(targetSelected)
                    didDrag = false
                } else {
                    targetSelected = !targetSelected
                    fraction = if (targetSelected) 1f else 0f
                    currentOnSelect(targetSelected)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                    else (fraction - delta).fastCoerceIn(0f, 1f)
            }
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { fraction ->
                dampedDragAnimation.updateValue(fraction)
            }
    }
    LaunchedEffect(selected, enabled) {
        snapshotFlow { selected() }
            .collectLatest { isSelected ->
                targetSelected = isSelected
                val target = if (isSelected) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    val trackBackdrop = rememberGlassLayerBackdrop(
        domain = GlassBackdropDomain.ChromeCombined,
        providerId = "liquid-toggle-track"
    )
    val material = remember {
        GlassMaterialSpec(
            role = GlassMaterialRole.Control,
            blur = 8.dp,
            lensHeight = 5.dp,
            lensAmount = 10.dp,
            surfaceAlpha = 1f,
            borderAlpha = 0f,
            highlightAlpha = 0.45f,
            shadowAlpha = 0.05f,
            innerShadowAlpha = 1f,
            chromaticAberration = false,
            depthEffect = false
        )
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "LiquidToggleThumb",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.Control,
        sceneKey = "liquid-toggle-thumb"
    )

    Box(
        modifier.size(trackWidth, trackHeight),
        contentAlignment = Alignment.TopStart
    ) {
        Box(
            Modifier
                .glassBackdropProducer(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    val fraction = dampedDragAnimation.value
                    drawRect(lerp(trackColor, accentColor, fraction))
                }
                .size(trackWidth, trackHeight)
        )

        Box(
            Modifier
                .offset {
                    val fraction = dampedDragAnimation.value
                    // Relative placement mirrors in RTL. Track and thumb share the same
                    // layout origin, including the very first unchecked frame.
                    IntOffset(
                        x = (2.dp.toPx() + dragWidth * fraction).roundToInt(),
                        y = ((trackHeight - thumbHeight) / 2).roundToPx()
                    )
                }
                .semantics {
                    role = Role.Switch
                }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .sleepDownGlassSurface(
                    backdrop = rememberGlassCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            if (scaleY > 0f) {
                                scale(scaleX, scaleY) { drawBackdrop() }
                            }
                        }
                    ),
                    descriptor = descriptor,
                    material = material,
                    shape = { Capsule() },
                    effectFrame = GlassEffectFrame(blur = null),
                    // An opaque resting thumb needs no sampled/offscreen glass. Draw its
                    // white fill as ordinary local content below; keep refraction on press.
                    renderEnabled = { dampedDragAnimation.pressProgress > 0f },
                    placementLayer = false,
                    effectsOverride = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                            chromaticAberration = false
                        )
                    },
                    highlightOverride = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress * 0.45f
                        )
                    },
                    shadowOverride = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadowOverride = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    additionalLayerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    }
                )
                .drawBehind {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 1f - dampedDragAnimation.pressProgress),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
                .size(thumbWidth, thumbHeight)
        )
    }
}
