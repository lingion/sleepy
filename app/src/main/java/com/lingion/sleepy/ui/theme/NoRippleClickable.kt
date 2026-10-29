package com.lingion.sleepy.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.lingion.sleepy.ui.component.SleepyPressScale

/**
 * 无涟漪点击 — 全 app 自定义可点面(Box/Row/Text 上的 clickable)唯一入口。
 * 视觉策略: 自定义表面(星期块/展开卡片/选周圆点/课程卡/设置行等)不渲染涟漪,
 * 涟漪反馈只保留给 M3 标准组件(Button/Switch/IconButton/DropdownMenuItem)。
 * 按压反馈 (M3 Expressive motion pass): interactionSource 采 pressed →
 * animateFloatAsState 0.98 微缩 (fastEffectsSpec, 阻尼 1.0 不回弹), graphicsLayer 应用。
 * 刻意只走 interactionSource 不走 pointerInput — 手势观察者不参与派发,
 * 不与拖拽母容器(课程卡长按拖/sort)竞争 (IntRangeSlider 冻结先例)。
 * noRippleCombinedClickable 不加缩放: 长按拖拽起点在 combined 上, 拖起期间
 * 持续 pressed 会显示常驻 0.98, 干扰 lift 视觉; 保持原状。
 */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) SleepyPressScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>(),
        label = "noRipplePressScale",
    )
    this.graphicsLayer {
        scaleX = pressScale
        scaleY = pressScale
    }.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

fun Modifier.noRippleCombinedClickable(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = composed {
    combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/** 带 enabled 门控的变体(禁用时不响应点击)。供撤回/取消撤回胶囊等禁用态使用。 */
fun Modifier.noRippleClickable(
    enabled: Boolean,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) SleepyPressScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>(),
        label = "noRipplePressScale",
    )
    this.graphicsLayer {
        scaleX = pressScale
        scaleY = pressScale
    }.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}
