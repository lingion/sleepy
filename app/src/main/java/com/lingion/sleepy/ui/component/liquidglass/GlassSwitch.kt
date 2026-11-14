package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

/**
 * 实心轨道，按住时滑片渐变为采样轨道和宿主背景的玻璃镜片。
 * iOS 26.5 原生测量与 Android 对齐范围见 docs/native-glass-control-alignment.md。
 * 轨道 63×28，静止片 37×24、两端留白 2，行程 22；触摸目标保留 Android 48dp。
 */
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
    val glassEnabled = LocalUiStyle.current == UiStyle.LiquidGlass && host != null
    if (!glassEnabled) {
        androidx.compose.material3.Switch(checked, onCheckedChange,
            modifier, thumbContent, enabled, colors, interactionSource)
        return
    }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val update by rememberUpdatedState(onCheckedChange)
    val inputTimeNanos by rememberUpdatedState(LocalDockInputTimeNanos.current)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Geometry and drag use the same measured 22dp travel.
    val travel = with(LocalDensity.current) { 22.dp.toPx() }
    val driver = rememberGlassSwitchMotion(checked, enabled, interactions,
        parentOwnsInput = onCheckedChange == null, travelDistanceDp = if (rtl) -22f else 22f)
    val motion = driver.motion
    val lift = motion.lift
    val geometryLift = motion.geometryLift
    val input = if (onCheckedChange == null) Modifier else Modifier
        .glassGestureOwnership(enabled)
        .toggleable(
            value = checked, enabled = enabled, role = Role.Switch,
            interactionSource = interactions, indication = null,
            onValueChange = {
                val target = driver.requestedPosition < .5f
                if (driver.select(if (target) 1f else 0f, inputTimeNanos())) update?.invoke(target)
            },
        )
        .pointerInput(enabled, rtl, travel, driver) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val downReceipt = inputTimeNanos()
                val checkedAtStart = driver.requestedPosition >= .5f
                val start = if (checkedAtStart) 1f else 0f
                val epoch = driver.begin(pressed = true, dragging = true,
                    receivedNanos = downReceipt, sampleUptimeMillis = down.uptimeMillis)
                    ?: return@awaitEachGesture
                down.consume()
                var position = start
                var moved = false
                var released = false
                var cancelReceipt: Long? = null
                var cancelSample: Long? = null
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val receivedNanos = inputTimeNanos()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        cancelReceipt = receivedNanos
                        cancelSample = pointer?.uptimeMillis
                        if (pointer == null || !driver.isCurrent(epoch)) break
                        if (!pointer.pressed) {
                            if (pointer.isConsumed) break
                            pointer.consume()
                            val target = if (moved) position >= .5f else !checkedAtStart
                            released = driver.release(epoch, if (target) 1f else 0f, activate = true,
                                receivedNanos = receivedNanos, sampleUptimeMillis = pointer.uptimeMillis)
                            if (released && target != checkedAtStart) update?.invoke(target)
                            break
                        }
                        val dx = pointer.position.x - down.position.x
                        if (kotlin.math.abs(dx) > viewConfiguration.touchSlop) moved = true
                        if (moved) {
                            position = (start + dx * (if (rtl) -1f else 1f) / travel).coerceIn(0f, 1f)
                        }
                        driver.move(epoch, position, dragging = true,
                            receivedNanos = receivedNanos, sampleUptimeMillis = pointer.uptimeMillis)
                        pointer.consume()
                        // A pointerInput restart has no delivered CANCEL sample.
                        cancelReceipt = null
                        cancelSample = null
                    }
                } finally {
                    if (!released) driver.cancel(epoch, cancelReceipt ?: inputTimeNanos(), cancelSample)
                }
            }
        }

    val on = motion.position.value.coerceIn(0f, 1f)
    // The solid thumb fades on the same lift clock as its glass geometry.
    // The glass is always drawn behind this fill, so an almost-flat optical
    // layer cannot hide the returning solid thumb during the last release frames.
    val solidAlpha = 1f - lift
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    // Track fill follows the sliding position, including intermediate drag frames.
    val trackColor = if (enabled) lerp(colors.uncheckedTrackColor, colors.checkedTrackColor, on)
        else lerp(colors.disabledUncheckedTrackColor, colors.disabledCheckedTrackColor, on)
    val thumbColor = if (enabled) lerp(colors.uncheckedThumbColor, colors.checkedThumbColor, on)
        else lerp(colors.disabledUncheckedThumbColor, colors.disabledCheckedThumbColor, on)
    // iOS 26.5 presentation layers: 37×24 at rest, 58×38⅓ held,
    // briefly about 60×40 on rise. Keep geometric overshoot separate from alpha.
    val stretch = motion.stretch * .25f
    val thumbWidth = glassLensWidth(37f + 21f * geometryLift, stretch)
    val thumbHeight = glassLensHeight(24f + (43f / 3f) * geometryLift, stretch,
        minimum = 24f + 13f * geometryLift)
    val thumbShape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val track = rememberLayerBackdrop()
    val scene = checkNotNull(rememberGlassSamplingBackdrop())
    val trackBackdrop = rememberCombinedBackdrop(scene, track)
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val baseLens = LiquidGlassStyle.controlLens(scheme, dark)
    val lensStyle = baseLens.scale(lift.toDouble())
    // 热区 68×48: 轨道 63×28 居中; 高度保留 Material 无障碍契约 ≥48dp
    // （GlassSwitchTest.minimumTarget 断言, iOS 实测热区仅 51×31, 平台兼容取大者）。
    Box(modifier.then(input).size(68.dp, 48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(63.dp, 28.dp).layerBackdrop(track)
                .background(trackColor, CircleShape)
                .then(if (focused) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier),
        )
        Box(
            Modifier.offset {
                IntOffset(((if (rtl) .5f - motion.position.value else motion.position.value - .5f) * travel).roundToInt(), 0)
            }
                .requiredSize(thumbWidth.dp, thumbHeight.dp)
                .then(if (lift > 0f) Modifier.drawBackdrop(
                        backdrop = trackBackdrop,
                        shape = { thumbShape },
                        effects = { liquidGlass(context = context, style = lensStyle) },
                        highlight = null,
                        shadow = { LiquidGlassStyle.controlLensShadow(dark, lift) },
                    )
                else Modifier)
                .background(thumbColor.copy(alpha = thumbColor.alpha * solidAlpha), thumbShape),
            contentAlignment = Alignment.Center,
        ) {
            val iconColor = if (enabled) lerp(colors.uncheckedIconColor, colors.checkedIconColor, on)
                else lerp(colors.disabledUncheckedIconColor, colors.disabledCheckedIconColor, on)
            CompositionLocalProvider(LocalContentColor provides iconColor) {
                thumbContent?.invoke()
            }
        }
    }
}

private data class ParentSwitchPress(
    val interaction: PressInteraction.Press,
    val epoch: Long,
    val checked: Boolean,
)

private data class ParentSwitchSelection(val epoch: Long, val checked: Boolean)

/** A parent-owned switch observes input without adding a second click action. */
@Composable
private fun rememberGlassSwitchMotion(
    checked: Boolean,
    enabled: Boolean,
    interactions: MutableInteractionSource,
    parentOwnsInput: Boolean,
    travelDistanceDp: Float,
): TimedGlassControlMotion {
    if (!parentOwnsInput) {
        return rememberTimedGlassControlMotion(if (checked) 1f else 0f, enabled,
            spec = GlassControlMotionSpec.Native, travelDistanceDp = travelDistanceDp)
    }
    var press by remember(interactions) { mutableStateOf<ParentSwitchPress?>(null) }
    var selection by remember(interactions) { mutableStateOf<ParentSwitchSelection?>(null) }
    val currentChecked by rememberUpdatedState(checked)
    val currentEnabled by rememberUpdatedState(enabled)
    val inputTimeNanos by rememberUpdatedState(LocalDockInputTimeNanos.current)
    val driver = rememberTimedGlassControlMotion(
        if (selection?.checked ?: checked) 1f else 0f,
        // Only an input transaction already accepted by the parent may finish
        // its presentation while that parent temporarily disables new input.
        enabled || press != null || selection != null,
        spec = GlassControlMotionSpec.Native,
        travelDistanceDp = travelDistanceDp,
        animateExternalChanges = true,
    )
    LaunchedEffect(interactions, driver) {
        try {
            interactions.interactions.collect { interaction ->
                val received = inputTimeNanos()
                // PressInteraction carries no hardware sample timestamp. Use
                // its delivery clock instead of inventing an earlier sample.
                val sample = received / 1_000_000L
                when (interaction) {
                    is PressInteraction.Press -> if (currentEnabled) {
                        press?.let { driver.cancel(it.epoch, received) }
                        val epoch = driver.begin(pressed = true, dragging = false,
                            receivedNanos = received, sampleUptimeMillis = sample)
                        press = epoch?.let { ParentSwitchPress(interaction, it, currentChecked) }
                    }
                    is PressInteraction.Release -> {
                        val owned = press?.takeIf { it.interaction === interaction.press } ?: return@collect
                        press = null
                        if (currentChecked != owned.checked) {
                            driver.cancel(owned.epoch, received)
                        } else {
                            val target = !owned.checked
                            if (driver.release(owned.epoch, if (target) 1f else 0f, activate = true,
                                    receivedNanos = received, sampleUptimeMillis = sample)) {
                                // A database-backed row can retain its old checked value
                                // while pending disables it. Keep this accepted target
                                // until the parent finishes that controlled update.
                                selection = ParentSwitchSelection(owned.epoch, target)
                            }
                        }
                    }
                    is PressInteraction.Cancel -> {
                        val owned = press?.takeIf { it.interaction === interaction.press } ?: return@collect
                        press = null
                        driver.cancel(owned.epoch, received)
                    }
                }
            }
        } finally {
            press?.let { driver.cancel(it.epoch, inputTimeNanos()) }
            press = null
        }
    }
    LaunchedEffect(selection, checked, enabled) {
        val accepted = selection ?: return@LaunchedEffect
        if (!enabled) return@LaunchedEffect
        // Give the parent's click callback/recomposition one frame to expose
        // either its new controlled value or a synchronous pending disable.
        withFrameNanos { }
        if (selection != accepted || !currentEnabled) return@LaunchedEffect
        if (currentChecked != accepted.checked) {
            // A failed/rejected write rolls back without synthesizing a new tap.
            driver.synchronize(if (currentChecked) 1f else 0f, enabled = true,
                receivedNanos = inputTimeNanos(), animateSelection = false)
        }
        selection = null
    }
    return driver
}
