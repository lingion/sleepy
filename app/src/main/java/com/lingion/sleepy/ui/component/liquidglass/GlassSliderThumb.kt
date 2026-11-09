package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/** Shared precision thumb and ghost dimensions in dp. */
object GlassSliderThumbMetrics {
    const val RESTING_WIDTH = 16f
    const val RESTING_HEIGHT = 24f
    const val LIFT_GROWTH_WIDTH = 12f
    const val LIFT_GROWTH_HEIGHT = 16f

    /** 滑块和幽灵保留调用方的颜色。 */
    fun restingColor(thumbColor: Color): Color = thumbColor
}

/** Small colored vertical oval; the held lens grows around the same center. */
@Composable
internal fun GlassSliderThumb(
    trackBackdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    enabled: Boolean = true,
    pressedOverride: Boolean? = null,
    colorOverride: Color? = null,
    motionOverride: GlassControlMotion? = null,
) {
    val host = LocalGlassHost.current
    val glass = LocalUiStyle.current == UiStyle.LiquidGlass && host != null
    if (!glass) {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        SliderDefaults.Thumb(
            interactionSource = interactionSource,
            modifier = modifier,
            colors = colors,
            enabled = enabled,
        )
        return
    }

    var pressed by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val thumbColor = colorOverride ?: if (enabled) colors.thumbColor else colors.disabledThumbColor
    val restingColor = GlassSliderThumbMetrics.restingColor(thumbColor)
    val shape = RoundedCornerShape(50)
    val motion = motionOverride ?: rememberGlassControlMotion(0f, enabled && (pressedOverride ?: pressed), enabled = enabled)
    val lift = motion.lift
    val width = glassLensWidth(GlassSliderThumbMetrics.RESTING_WIDTH + GlassSliderThumbMetrics.LIFT_GROWTH_WIDTH * lift, motion.stretch)
    val height = glassLensHeight(GlassSliderThumbMetrics.RESTING_HEIGHT + GlassSliderThumbMetrics.LIFT_GROWTH_HEIGHT * lift, motion.stretch)
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
            .size(width.dp, height.dp)
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
            .background(
                restingColor.copy(alpha = 1f - lift.coerceIn(0f, 1f)),
                shape,
            )
            .then(
                if (lift > 0f) Modifier.drawBackdrop(
                    backdrop = lensSource,
                    shape = { shape },
                    effects = { liquidGlass(context = context, style = lensStyle) },
                    highlight = null,
                    shadow = { LiquidGlassStyle.controlLensShadow(dark, lift) },
                ) else Modifier
            )
    )
}
