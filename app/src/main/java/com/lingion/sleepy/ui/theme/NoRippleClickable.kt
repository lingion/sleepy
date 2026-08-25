package com.lingion.sleepy.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * 无涟漪点击 — 全 app 自定义可点面(Box/Row/Text 上的 clickable)唯一入口。
 * 视觉策略: 自定义表面(星期块/展开卡片/选周圆点/课程卡/设置行等)不渲染涟漪,
 * 涟漪反馈只保留给 M3 标准组件(Button/Switch/IconButton/DropdownMenuItem)。
 */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}
