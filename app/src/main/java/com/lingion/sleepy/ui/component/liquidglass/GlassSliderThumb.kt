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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

/**
 * 滑块静置几何与淡色的单点真源 — GlassSliderThumb 按压生长、幽灵绘制、摆位公式
 * 全部引用此处; 2026-11-08 用户: "滑块长什么样，幽灵态就应该是什么样子"。
 */
object GlassSliderThumbMetrics {
    const val RESTING_WIDTH = 37f
    const val RESTING_HEIGHT = 24f
    const val LIFT_GROWTH_WIDTH = 16f
    const val LIFT_GROWTH_HEIGHT = 6f

    /** 静置淡色: primary 保色相向白拉 0.85 (滑块与幽灵共用同一配方)。 */
    fun restingColor(thumbColor: Color): Color = lerp(thumbColor, Color.White, 0.85f)
}

/**
 * Material slider thumb in the same horizontal capsule family as GlassSwitch.
 *
 * 2026-11-08 用户三裁 (对齐 iOS 参考帧):
 * 1. 静置 = 主题淡色 ("滑块静止过程是白色/淡色，按主题色里的淡色，不得跟轨道一个
 *    颜色"): M3 默认 thumbColor=primary 与激活轨道同色, 向白拉 0.85 保色相去饱和。
 * 2. 按压镜片 = 宿主场景前置层 + 轨道记录层合成 ("摁起来要把底下的轨道包含进透镜
 *    折射, 不得是里面没有东西的透明色块")。两条采样通路都是既有无环模式
 *    (glassControl 面板采 host 前置; 滑块采兄弟 track Box), 无祖先-后代采样。
 * 3. 幽灵态 = 滑块静置外形 (37×24dp 横胶囊 + 同款淡色), 由调用点文件实现。
 */
@Composable
internal fun GlassSliderThumb(
    trackBackdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    enabled: Boolean = true,
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
    val thumbColor = if (enabled) colors.thumbColor else colors.disabledThumbColor
    val restingColor = GlassSliderThumbMetrics.restingColor(thumbColor)
    val shape = RoundedCornerShape(50)
    val lift = if (enabled && pressed) 1f else 0f
    val width = GlassSliderThumbMetrics.RESTING_WIDTH + GlassSliderThumbMetrics.LIFT_GROWTH_WIDTH * lift
    val height = GlassSliderThumbMetrics.RESTING_HEIGHT + GlassSliderThumbMetrics.LIFT_GROWTH_HEIGHT * lift
    val dark = LocalDarkTheme.current
    val lensStyle = LiquidGlassStyle.lens(scheme, dark).copy(
        refraction = -8.5,
        bevel = 9.0,
        brightness = if (dark) 1.12 else 1.08,
        rim = if (dark) 0.48 else 0.72,
        rimWidth = 1.2,
        endShade = 0.06,
        dispersion = 1.8,
        hairline = 0.28,
        contrast = 0.0,
    ).scale(lift.toDouble())

    // 镜片采样源 (2026-11-08 用户: 摁起来必须把底下的轨道包进透镜折射, 不得是里面
    // 没有内容的透明色块): 宿主场景前置层 (滑杆后方页面内容) 与轨道 Box 记录层
    // (真轨道 16dp 色带 + 幽灵像素) 合成。backdropAt 取 maxOf(1f, depth) 与
    // 分段滑块旧契约同法 — 保证 thumb 正下方的轨道/幽灵像素已进入前置层。
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
            .graphicsLayer {
                scaleX = 1f + 0.2f * lift
                scaleY = 1f + 0.2f * lift
                clip = false
            }
            .shadow((8f * lift).dp, shape, clip = false)
            .background(
                restingColor.copy(alpha = if (lift > 0f) 0.28f else 1f),
                shape,
            )
            .then(
                if (lift > 0f) Modifier.drawPlainBackdrop(
                    backdrop = lensSource,
                    shape = { shape },
                    effects = { liquidGlass(context = context, style = lensStyle) },
                ) else Modifier
            )
    )
}
