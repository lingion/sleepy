package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * MD3 双头滑杆 (RangeSlider) 的整数区间封装 — 编辑"第2–4节""第1–16周"这类
 * 首尾都是整数的范围, 一头拖起点、一头拖终点, steps 把轨道离散成整数档位,
 * 物理上拖不出小数/交叉/越界。键盘方向键微调由 RangeSlider 内置。
 *
 * 调用方仍按各自的存储语义接 (start, end) 区间语言:
 * 节次卡写回时自行换算 step = end - start + 1, 周次直接写 startWeek/endWeek。
 * 数据实体 (CourseEntity startNode/step) 与所有消费方零改动。
 */
@Composable
fun IntRangeSlider(
    summary: String,
    minValue: Int,
    maxValue: Int,
    startValue: Int,
    endValue: Int,
    onValueChange: (start: Int, end: Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    // 只有一个可选档位 (maxStd=1 的作息表) — 滑杆两端点重合无意义, 退化成静态读数
    val hasTwoStops = maxValue >= minValue + 1
    val displayStart = startValue.coerceIn(minValue, maxValue)
    val displayEnd = endValue.coerceIn(displayStart, maxValue)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )
        }
        if (hasTwoStops) {
            RangeSlider(
                value = displayStart.toFloat()..displayEnd.toFloat(),
                onValueChange = { range ->
                    val snapped = snapRangeToInt(range, minValue, maxValue)
                    onValueChange(snapped.first, snapped.second)
                },
                valueRange = minValue.toFloat()..maxValue.toFloat(),
                // stops = maxValue-minValue+1, 内部档位 = stops-2 (两端点之外)
                steps = maxValue - minValue - 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = if (label != null) "$label $summary" else summary },
            )
        }
    }
}

/**
 * RangeSlider 回调值 → 整数 (start, end)。
 * 两端各自四舍五入并夹进 [minValue, maxValue]; start 不得越过 end
 * (RangeSlider 本身保证 start<=end, 此处的夹取是对导入脏数据的兜底)。
 */
internal fun snapRangeToInt(
    range: ClosedFloatingPointRange<Float>,
    minValue: Int,
    maxValue: Int,
): Pair<Int, Int> {
    val start = range.start.roundToInt().coerceIn(minValue, maxValue)
    val end = range.endInclusive.roundToInt().coerceIn(start, maxValue)
    return start to end
}
