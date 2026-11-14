package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.withTransformCompensation
import com.kyant.backdrop.drawBackdrop
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

/** Single and range thumbs share the same horizontal silhouette and continuous rise/fall. */
@Composable
internal fun GlassSliderThumb(
    trackBackdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    range: Boolean = false,
    enabled: Boolean = true,
    pressedOverride: Boolean? = null,
    motionOverride: GlassControlMotion? = null,
) {
    val host = LocalGlassHost.current
    val glass = LocalUiStyle.current == UiStyle.LiquidGlass && host != null
    val restingWidth = GlassSliderThumbMetrics.width(range)
    val restingHeight = GlassSliderThumbMetrics.height(range)
    val shape = RoundedCornerShape(50)
    if (!glass) {
        Box(modifier.size(restingWidth.dp, restingHeight.dp)
            .shadow(2.dp, shape, clip = false)
            .background(Color.White.copy(alpha = if (enabled) 1f else .38f), shape))
        return
    }

    var pressed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val motion = motionOverride ?: rememberGlassControlMotion(0f, enabled && (pressedOverride ?: pressed), enabled = enabled,
        spec = GlassControlMotionSpec.NativeSlider)
    val lift = motion.lift
    val scale = 1f + (GlassSliderThumbMetrics.LIFT_SCALE - 1f) * motion.geometryLift
    // Single-Slider carrier transforms were measured separately from isotropic lift.
    // RangeSlider keeps its existing geometry until it has independent native evidence.
    val dark = LocalDarkTheme.current
    val lensStyle = (if (range) LiquidGlassStyle.rangeSliderLens(dark)
        else LiquidGlassStyle.sliderLens(dark)).scale(lift.toDouble())

    // The ordinary track is a sibling, so the optical sampling graph cannot contain itself.
    val scene = checkNotNull(rememberGlassSamplingBackdrop())
    val trackSource = remember(trackBackdrop, range) {
        if (range) trackBackdrop else trackBackdrop?.withTransformCompensation()
    }
    val lensSource: Backdrop = if (trackSource != null) {
        rememberCombinedBackdrop(scene, trackSource)
    } else {
        scene
    }

    Box(
        modifier
            .size((restingWidth * scale).dp, (restingHeight * scale).dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressed = true
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        pressed = false
                    }
                }
            }
    ) {
        // Native carrier bounds grow isotropically, then its presentation layer
        // transforms both axes. Resizing the capsule would redraw circular ends
        // instead of transforming them into ellipses. Keep the hit/semantic owner
        // at its isotropic size; only this visual child (including shadow) deforms.
        Box(
            Modifier.matchParentSize()
                .then(if (range) Modifier else Modifier.graphicsLayer {
                    scaleX = 1f + motion.stretch
                    scaleY = 1f + motion.stretchY
                })
                .testTag("glass-slider-thumb-carrier")
                .shadow(2.dp, shape, clip = false)
                .then(
                    if (lift > 0f) Modifier.drawBackdrop(
                        backdrop = lensSource,
                        shape = { shape },
                        effects = { liquidGlass(context = context, style = lensStyle) },
                        highlight = null,
                        shadow = null,
                    ) else Modifier
                )
                // The scene is painted first; the solid thumb fades above it.
                .background(Color.White.copy(alpha = 1f - lift.coerceIn(0f, 1f)), shape)
        )
    }
}
