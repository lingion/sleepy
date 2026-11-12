package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

/** The shared white circular thumb; glass keeps its existing continuous rise/fall. */
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
    val restingDiameter = GlassSliderThumbMetrics.diameter(range)
    if (!glass) {
        Box(modifier.size(restingDiameter.dp)
            .shadow(2.dp, CircleShape, clip = false)
            .background(Color.White.copy(alpha = if (enabled) 1f else .38f), CircleShape))
        return
    }

    var pressed by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val shape = CircleShape
    val motion = motionOverride ?: rememberGlassControlMotion(0f, enabled && (pressedOverride ?: pressed), enabled = enabled,
        spec = GlassControlMotionSpec.NativeSlider)
    val lift = motion.lift
    val diameter = restingDiameter * (1f + (GlassSliderThumbMetrics.LIFT_SCALE - 1f) * motion.geometryLift)
    val dark = LocalDarkTheme.current
    val lensStyle = LiquidGlassStyle.controlLens(scheme, dark).scale(lift.toDouble())

    // The ordinary track is a sibling, so the optical sampling graph cannot contain itself.
    val samplingHost = if (LocalGlassOverlay.current) LocalGlassOverlaySamplingHost.current ?: host else host
    val scene: Backdrop = if (LocalGlassOverlay.current) {
        samplingHost.overlayBackdrop
    } else {
        samplingHost.backdropAt(maxOf(1f, LocalGlassDepth.current))
    }
    val lensSource: Backdrop = if (trackBackdrop != null) {
        rememberCombinedBackdrop(scene, trackBackdrop)
    } else {
        scene
    }

    Box(
        modifier
            .size(diameter.dp)
            .shadow(2.dp, shape, clip = false)
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
