package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** FlClash switch.dart：实心轨道，按住时滑块升起为采样轨道的镜片。 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val host = LocalGlassHost.current
    if (LocalUiStyle.current != UiStyle.LiquidGlass || host == null) {
        androidx.compose.material3.Switch(checked, onCheckedChange, modifier, thumbContent, enabled, colors, interactionSource)
        return
    }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    var dragging by remember { mutableStateOf(false) }
    var gestureEnd by remember { mutableIntStateOf(0) }
    var tapGeneration by remember { mutableIntStateOf(0) }
    var tapLift by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val animation = remember { Animatable(position) }
    val update by rememberUpdatedState(onCheckedChange)
    val currentChecked by rememberUpdatedState(checked)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val travel = with(LocalDensity.current) { 20.dp.toPx() }
    LaunchedEffect(checked, dragging, gestureEnd) {
        if (!dragging) {
            animation.snapTo(position)
            animation.animateTo(if (checked) 1f else 0f, tween(300, easing = SwitchTravelEasing)) {
                position = value
            }
        }
    }
    LaunchedEffect(tapGeneration, enabled) {
        if (tapGeneration > 0 && enabled) {
            tapLift = true
            delay(200)
        }
        tapLift = false
    }
    val lift by animateFloatAsState(
        if (enabled && (pressed || dragging || tapLift)) 1f else 0f,
        tween(150), label = "switch-lift",
    )
    val input = if (onCheckedChange == null) Modifier else Modifier
        .toggleable(
            value = checked, enabled = enabled, role = Role.Switch,
            interactionSource = interactions, indication = null,
            onValueChange = {
                tapGeneration++
                gestureEnd++
                update?.invoke(it)
            },
        )
        .pointerInput(checked, enabled, rtl, travel) {
            if (!enabled) return@pointerInput
            try {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragCancel = { dragging = false; gestureEnd++ },
                    onDragEnd = {
                        val target = position >= .5f
                        dragging = false
                        gestureEnd++
                        if (target != currentChecked) update?.invoke(target)
                    },
                ) { change, amount ->
                    change.consume()
                    position = (position + amount * (if (rtl) -1f else 1f) / travel).coerceIn(0f, 1f)
                }
            } finally {
                dragging = false
                gestureEnd++
            }
        }

    val on = position.coerceIn(0f, 1f)
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val trackColor = if (enabled) lerp(colors.uncheckedTrackColor, colors.checkedTrackColor, on)
        else lerp(colors.disabledUncheckedTrackColor, colors.disabledCheckedTrackColor, on)
    val thumbColor = if (enabled) lerp(colors.uncheckedThumbColor, colors.checkedThumbColor, on)
        else lerp(colors.disabledUncheckedThumbColor, colors.disabledCheckedThumbColor, on)
    val stretch = sin(PI * on).toFloat()
    // iOS 26 定标: 静止近圆, 按住/拖动加宽成胶囊(宽 > 高 + 50% 圆角), 松手回弹。
    // 高度恒定 ~24(不随 lift 长高), 只让宽度随 lift 拉大 — 老代码同时把高度抬到 38 才
    // 让 CircleShape 渲染成方块气泡而非胶囊。用户 2026-11-04: 宽度变宽、圆形泡成胶囊。
    val thumbWidth = (24f + 13f * stretch) + 16f * lift
    val thumbHeight = 24f - 2f * stretch
    val thumbShape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val track = rememberLayerBackdrop()
    val samplingHost = LocalGlassOverlaySamplingHost.current ?: host
    val scene = if (LocalGlassOverlay.current) samplingHost.overlayBackdrop else host.backdropAt(LocalGlassDepth.current)
    val backdrop = rememberCombinedBackdrop(scene, track)
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val lensStyle = LiquidGlassStyle.lens(scheme, dark).scale(lift.toDouble())
    Box(modifier.then(input).size(60.dp, 48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(52.dp, 32.dp).layerBackdrop(track)
                .background(trackColor, CircleShape)
                .then(if (focused) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier),
        )
        Box(
            Modifier.offset(x = ((if (rtl) .5f - position else position - .5f) * 20f).dp)
                .requiredSize(thumbWidth.dp, thumbHeight.dp)
                .shadow((6f * lift).dp, thumbShape, clip = false)
                .then(if (lift > 0f) Modifier.drawPlainBackdrop(
                    backdrop = backdrop, shape = { thumbShape },
                    effects = { liquidGlass(context = context, style = lensStyle) },
                ) else Modifier)
                .background(thumbColor.copy(alpha = thumbColor.alpha * (1f - lift)), thumbShape),
            contentAlignment = Alignment.Center,
        ) {
            val iconColor = if (enabled) {
                if (checked) colors.checkedIconColor else colors.uncheckedIconColor
            } else {
                if (checked) colors.disabledCheckedIconColor else colors.disabledUncheckedIconColor
            }
            CompositionLocalProvider(LocalContentColor provides iconColor) {
                thumbContent?.invoke()
            }
        }
    }
}

private val SwitchTravelEasing = Easing { fraction ->
    val t = fraction - 1f
    1f + 2.70158f * t * t * t + 1.70158f * t * t
}
