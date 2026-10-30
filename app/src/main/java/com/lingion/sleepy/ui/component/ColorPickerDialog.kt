package com.lingion.sleepy.ui.component

import androidx.core.graphics.toColorInt
import androidx.compose.foundation.Canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.lingion.sleepy.R
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * HSV 取色器公共组件 — 课程色与自定义主题编辑器共用。
 *
 * 2026-09-11 从 AddCourseScreen(原 private ColorPickerDialog/SVPanel/HueSlider)
 * 抽出:自定义主题编辑器需要同一取色交互,公共化避免复制粘贴算法(签名保持
 * initialHex/onConfirm/onDismiss 不变,AddCourseScreen 行为零改动)。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // 解析初始 HSV
    val initialHSV = remember {
        val hsv = FloatArray(3)
        val rgb = runCatching { initialHex.toColorInt() }
            .getOrDefault(0xFF6750A4.toInt())
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv
    }

    var hue by remember { mutableStateOf(initialHSV[0]) }
    // 幽灵基线 = 打开弹窗时刻的色相 (initialHSV 已 remember, 弹窗重建即重算)。
    val hueBaseline = initialHSV[0]
    var saturation by remember { mutableStateOf(initialHSV[1]) }
    var value by remember { mutableStateOf(initialHSV[2]) }

    val currentColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    val currentHex = String.format("#%08X", android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.course_color), color = colors.onSurface)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SV 面板 — 大方块，横向拖=饱和度，纵向拖=明度
                SVPanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSVChange = { s, v ->
                        saturation = s
                        value = v
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(SleepyTheme.shapes.large)
                )

                // 色相滑条
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                    initialHue = hueBaseline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(SleepyTheme.shapes.large)
                )

                // 预览 + Hex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                    )
                    Text(
                        text = currentHex,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )
                }

                // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
                DialogActionButtons(
                    confirmText = stringResource(R.string.ok),
                    onConfirm = { onConfirm(currentHex) },
                    dismissText = stringResource(R.string.cancel),
                    onDismiss = onDismiss
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

/** 饱和度-明度面板：横=饱和度(0→1)，纵=明度(1→0)，背景色=当前色相 */
@Composable
fun SVPanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onSVChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                        val y = (change.position.y / size.height).coerceIn(0f, 1f)
                        onSVChange(x, 1f - y)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                        val y = (offset.y / size.height).coerceIn(0f, 1f)
                        onSVChange(x, 1f - y)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 底层：纯色相
            drawRect(pureHue)
            // 白色横向渐变（左→右 = 白→透明）
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.Transparent)
                )
            )
            // 黑色纵向渐变（上→下 = 透明→黑）
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black)
                )
            )
            // 指示器
            val cx = saturation * size.width
            val cy = (1f - value) * size.height
            drawCircle(Color.White, radius = 10f, center = androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(
                Color.Black.copy(alpha = SleepyTheme.Alpha.hairline),
                radius = 10f,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}

/** 色相滑条：360°彩虹水平条。
 *  onHueChange 逐帧回调(拖动跟手), onHueCommit 松手/点按各回调一次 —
 *  供「拖动只动本地值, 松手才落库重算」的调用方(外观页课程配色种子)节流;
 *  默认空实现, ColorPickerDialog 等既有调用零改动。
 *
 *  initialHue 幽灵 (需求 2026-10-29 用户定义原话语义, spec §3.1 色相变体):
 *  拖离基线 (调用点传打开弹窗/进编辑区时刻的色相, null = 不接) → 基线刻度出现
 *  一条细双描条 (白芯+深描 — 单头共享件 primary 色在彩虹底衬上辨不清, 此处
 *  专用几何; 判据仍复用 singleSliderGhostVisible, epsilon = 色相 1 度步进取半档);
 *  拖回基线 → 消失。纯装饰: 零 pointerInput 零语义, testTag 仅供 Robolectric
 *  管线定位。真机目验待办: 双描配方 (白 2dp 芯 + 深色 1dp 描边) 在任意色相底衬
 *  的可辨性需最终在设备上过目 (spec §3.1 挂账)。 */
@Composable
fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onHueCommit: (Float) -> Unit = {},
    initialHue: Float? = null,
) {
    // pointerInput(Unit) 只在首组成形, 闭包捕获的 hue 会过期; dragged 在 onDrag 逐帧
    // 更新, onDragEnd 松手提交 = 最后一帧视觉值(同 coroutine, 零竞态; cancel 复位不提交)。
    // 触碰未越过 touch slop 时 detectDragGestures 不触发, 点按走下方 tap 通道。
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                var dragged = -1f
                detectDragGestures(
                    onDragEnd = { if (dragged >= 0f) onHueCommit(dragged) },
                    onDragCancel = { dragged = -1f },
                    onDrag = { change, _ ->
                        change.consume()
                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragged = x * 360f
                        onHueChange(dragged)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                        val target = x * 360f
                        onHueChange(target)
                        onHueCommit(target)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hueColors = listOf(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(60f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(120f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(180f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(240f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(300f, 1f, 1f))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf(360f, 1f, 1f)))
            )
            drawRect(brush = androidx.compose.ui.graphics.Brush.horizontalGradient(colors = hueColors))
            // 指示器
            val cx = (hue / 360f) * size.width
            val cy = size.height / 2f
            drawCircle(Color.White, radius = 10f, center = androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
                radius = 8f,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
        }
        if (singleSliderGhostVisible(hue, initialHue, 0.5f)) {
            // 双描细条: 白芯 (2dp) + 深色描边 (1dp), 任意色相底衬可辨;
            // 几何 = 基线 hue 的满宽线性 x (无 thumb 内缩 — 与拖动换算同式)。
            val baseline = initialHue!!.coerceIn(0f, 360f)
            Canvas(modifier = Modifier.fillMaxSize().testTag("hue-ghost")) {
                val x = (baseline / 360f) * size.width
                val coreW = 2.dp.toPx()
                val strokeW = 1.dp.toPx()
                drawRect(
                    color = Color.Black.copy(alpha = 0.85f),
                    topLeft = androidx.compose.ui.geometry.Offset(x - coreW / 2f - strokeW, 0f),
                    size = androidx.compose.ui.geometry.Size(coreW + 2f * strokeW, size.height),
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = androidx.compose.ui.geometry.Offset(x - coreW / 2f, 0f),
                    size = androidx.compose.ui.geometry.Size(coreW, size.height),
                )
            }
        }
    }
}
