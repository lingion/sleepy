package com.lingion.sleepy.ui.component.liquidglass

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
 * 实心轨道，按住时滑块升起为采样轨道与宿主背景的镜片。
 *
 * 几何真源: sleepy/docs/liquid-glass-ios27/controls/controls-toggle.md §1
 * (sarunw iOS17↔26 对比图 @3x 像素尺实测; 2026-11-06 重测修订, ±0.5pt):
 * - 轨道 63×28dp 全圆角胶囊 (iOS 17 是 51×29)
 * - 滑块 37×24dp 横椭圆, 四周均 2pt 留白 (iOS 17 是 27 正圆; 轨道加宽正是为椭圆让位)
 * - 行程 = 63 − 37 − 2×2 = 22dp, 两端余量与上下留白对称
 * 热区按 HIG 通用规则 ≥44dp 高。三态模型 (§0): rest 标准填充 → press/drag
 * 隆起为活玻璃镜片 → release 沿 Dock 弹簧收回并保留速度。
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
        androidx.compose.material3.Switch(checked, onCheckedChange, modifier, thumbContent, enabled, colors, interactionSource)
        return
    }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    var dragging by remember { mutableStateOf(false) }
    var gestureEnd by remember { mutableIntStateOf(0) }
    var tapGeneration by remember { mutableIntStateOf(0) }
    var position by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val update by rememberUpdatedState(onCheckedChange)
    val currentChecked by rememberUpdatedState(checked)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // 行程 = 轨道宽 − 滑块宽 − 两端各 2dp 留白 = 63 − 37 − 4 = 22dp (2026-11-06 对
    // sarunw-toggles-ios26.png 重测: 白块四周均 2pt, 旧 26 行程让白块贴轨道边缘 0 留白,
    // 用户实机看到 "小白色块的 margin 不太对")。拖动映射与位移共用同一真值。
    val travel = with(LocalDensity.current) { 22.dp.toPx() }
    LaunchedEffect(checked, dragging, gestureEnd) {
        if (!dragging) position = if (checked) 1f else 0f
    }
    val motion = rememberGlassControlMotion(position, pressed || dragging, dragging, tapGeneration, enabled)
    val lift = motion.lift
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

    val on = motion.position.value.coerceIn(0f, 1f)
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val trackColor = if (enabled) lerp(colors.uncheckedTrackColor, colors.checkedTrackColor, on)
        else lerp(colors.disabledUncheckedTrackColor, colors.disabledCheckedTrackColor, on)
    val thumbColor = if (enabled) lerp(colors.uncheckedThumbColor, colors.checkedThumbColor, on)
        else lerp(colors.disabledUncheckedThumbColor, colors.disabledCheckedThumbColor, on)
    val stretch = motion.stretch
    val thumbWidth = glassLensWidth((37f + 16f * lift) * (1f + .2f * lift), stretch)
    val thumbHeight = glassLensHeight((24f + 6f * lift) * (1f + .2f * lift), stretch,
        minimum = 24f + 6f * lift)
    val thumbShape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val track = rememberLayerBackdrop()
    val samplingHost = if (LocalGlassOverlay.current) LocalGlassOverlaySamplingHost.current ?: host else host
    val scene = if (LocalGlassOverlay.current) samplingHost.overlayBackdrop else samplingHost.backdropAt(LocalGlassDepth.current)
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
                .background(thumbColor.copy(alpha = thumbColor.alpha * (1f - lift)), thumbShape)
                .then(if (lift > 0f) Modifier.drawBackdrop(
                        backdrop = trackBackdrop,
                        shape = { thumbShape },
                        effects = { liquidGlass(context = context, style = lensStyle) },
                        highlight = null,
                        shadow = { LiquidGlassStyle.controlLensShadow(dark, lift) },
                    )
                else Modifier),
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
