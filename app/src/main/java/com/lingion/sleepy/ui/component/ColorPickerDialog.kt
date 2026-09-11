package com.lingion.sleepy.ui.component

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
    val colors = SleepyTheme.colors

    // 解析初始 HSV
    val initialHSV = remember {
        val hsv = FloatArray(3)
        val rgb = runCatching { android.graphics.Color.parseColor(initialHex) }
            .getOrDefault(0xFF6750A4.toInt())
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv
    }

    var hue by remember { mutableStateOf(initialHSV[0]) }
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentHex) }) {
                Text(stringResource(R.string.ok), color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
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

/** 色相滑条：360°彩虹水平条 */
@Composable
fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                        onHueChange(x * 360f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                        onHueChange(x * 360f)
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
    }
}
