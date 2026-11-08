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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * FlClash switch.dart：实心轨道，按住时滑块升起为采样轨道的镜片。
 *
 * 几何真源: sleepy/docs/liquid-glass-ios27/controls/controls-toggle.md §1
 * (sarunw iOS17↔26 对比图 @3x 像素尺实测; 2026-11-06 重测修订, ±0.5pt):
 * - 轨道 63×28dp 全圆角胶囊 (iOS 17 是 51×29)
 * - 滑块 37×24dp 横椭圆, 四周均 2pt 留白 (iOS 17 是 27 正圆; 轨道加宽正是为椭圆让位)
 * - 行程 = 63 − 37 − 2×2 = 22dp, 两端余量与上下留白对称
 * 热区按 HIG 通用规则 ≥44dp 高。三态模型 (§0): rest 标准填充 → press/drag
 * 隆起为活玻璃镜片 (下方 lift 路径) → release 沉回带过冲 (SwitchTravelEasing)。
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
    var tapLift by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val animation = remember { Animatable(position) }
    val update by rememberUpdatedState(onCheckedChange)
    val currentChecked by rememberUpdatedState(checked)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // 行程 = 轨道宽 − 滑块宽 − 两端各 2dp 留白 = 63 − 37 − 4 = 22dp (2026-11-06 对
    // sarunw-toggles-ios26.png 重测: 白块四周均 2pt, 旧 26 行程让白块贴轨道边缘 0 留白,
    // 用户实机看到 "小白色块的 margin 不太对")。拖动映射与位移共用同一真值。
    val travel = with(LocalDensity.current) { 22.dp.toPx() }
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
    // 静置即横椭圆 37×24 (2026-11-06 重测: 轨道 84px 高、白核 72px 高 → 上下各 2pt 留白;
    // 旧 26 高把留白吃成 1dp, 白块顶天立地); 拖动过中线橡皮筋展宽到 49,
    // 按住/拖动升镜片再 +16 (用户 2026-11-04: 宽度变宽、圆形泡成胶囊)。
    // 2026-11-06 用户: "浮起的玻璃块高度不够，没有超出上下边缘" —— iOS 参考帧实测
    // 按压镜片 129×65px vs 静置轨道 127×55px = 镜片高 1.18× 轨道, 上下缘各溢出;
    // 旧值 24×1.105=26.5dp 仍矮于 28dp 轨道 = 永远不越界。升镜高度 +6 (24→30,
    // 再 ×1.105 生长 = 33.2dp = 轨道 1.19×); 宽度 +16 仍远大于高度增量,
    // 全升时 53:30 = 1.77 胶囊比, 40x38 方形气泡旧 bug 不会借道回归。
    val thumbWidth = (37f + 12f * stretch) + 16f * lift
    val thumbHeight = (24f - 2f * stretch) + 6f * lift
    val thumbShape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val track = rememberLayerBackdrop()
    val trackBackdrop = track
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val baseLens = LiquidGlassStyle.lens(scheme, dark).copy(
        refraction = -8.5,
        bevel = 9.0,
        brightness = if (dark) 1.12 else 1.08,
        rim = if (dark) 0.48 else 0.72,
        rimWidth = 1.2,
        endShade = 0.06,
        dispersion = 1.8,
        hairline = 0.28,
        contrast = 0.0,
    )
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
            // 位移 = (position−0.5)×行程 22dp: 白块两端各留 2dp, 与上下留白对称
            // (2026-11-06 逐图核对 sarunw-toggles-ios26.png: 四周均 2pt margin)。
            Modifier.offset(x = ((if (rtl) .5f - position else position - .5f) * 22f).dp)
                .requiredSize(thumbWidth.dp, thumbHeight.dp)
                // 升镜时沿生长方向轻微外扩 (Dock lensGrowth×lift 语义, 比例随 lift)
                .graphicsLayer {
                    if (lift > 0f) {
                        scaleX = 1f + 0.2f * lift
                        scaleY = 1f + 0.2f * lift
                    }
                }
                .shadow((8f * lift).dp, thumbShape, clip = false)
                .background(thumbColor.copy(alpha = thumbColor.alpha * (1f - 0.78f * lift)), thumbShape)
                .then(if (lift > 0f) Modifier.drawPlainBackdrop(
                        backdrop = trackBackdrop,
                        shape = { thumbShape },
                        effects = { liquidGlass(context = context, style = lensStyle) },
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

private val SwitchTravelEasing = Easing { fraction ->
    val t = fraction - 1f
    1f + 2.70158f * t * t * t + 1.70158f * t * t
}
