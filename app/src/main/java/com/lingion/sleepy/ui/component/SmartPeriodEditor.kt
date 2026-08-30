package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingion.sleepy.data.entity.BreakOption
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
/**
 * v1.0.16 智慧节次编辑器（自动模式）
 *
 * 类比编辑课程选"上课星期"：每个 break 模板下面一行卡片
 * （1.5, 2.5, ..., (N-1).5），点选表示"该位置使用这个 break"。
 *
 * - 同组内多选
 * - 跨组互斥（同一 transition 只能属于一个 break）
 * - 未选中的位置 = 0 分钟（连续上课）
 */
@Composable
fun SmartPeriodEditor(
    config: SmartPeriodConfig,
    onConfigChange: (SmartPeriodConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors
    val n = (config.totalPeriods - 1).coerceAtLeast(0)
    val assigns = config.effectiveAssignments()

    // 注意：不能在 Column.verticalScroll() 嵌套 LazyColumn 中，
    // 否则会被检测为"无限垂直约束"导致 IllegalStateException。
    // 外层 EditTableScreen 是 LazyColumn，这里只填内容、不滚。
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // ===== 输入区 =====
        Text(
            stringResource(R.string.edit_period_input),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        // 每节时长 + 总节数
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            NumberField(
                label = stringResource(R.string.edit_period_duration_label),
                unit = stringResource(R.string.unit_minutes),
                value = config.periodMinutes,
                onValueChange = { onConfigChange(config.copy(periodMinutes = it.coerceAtLeast(1))) },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = stringResource(R.string.edit_period_total_label),
                unit = stringResource(R.string.unit_periods),
                value = config.totalPeriods,
                onValueChange = { newN ->
                    onConfigChange(config.copy(totalPeriods = newN.coerceAtLeast(1)))
                },
                modifier = Modifier.weight(1f)
            )
        }

        // 第一节开始时间
        TimePickerField(
            value = config.startTime,
            onValueChange = { onConfigChange(config.copy(startTime = it)) },
            label = stringResource(R.string.edit_period_first_start),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        // 添加 break
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AddBreakChip(
                label = stringResource(R.string.short_break),
                color = colors.tertiary,
                onAdd = { onConfigChange(config.copy(breaks = config.breaks + BreakOption(10, false))) },
                modifier = Modifier.weight(1f)
            )
            AddBreakChip(
                label = stringResource(R.string.long_break),
                color = colors.primary,
                onAdd = { onConfigChange(config.copy(breaks = config.breaks + BreakOption(30, true))) },
                modifier = Modifier.weight(1f)
            )
        }

        // ===== Break 分组区 =====
        if (config.breaks.isNotEmpty()) {
            Text(
                stringResource(R.string.break_assign_hint),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )

            config.breaks.forEachIndexed { groupIdx, br ->
                BreakGroupSection(
                    breakOption = br,
                    groupIdx = groupIdx,
                    totalPeriods = config.totalPeriods,
                    assigns = assigns,
                    onMinuteChange = { newMin ->
                        onConfigChange(config.copy(
                            breaks = config.breaks.toMutableList().also { it[groupIdx] = it[groupIdx].copy(minutes = newMin) }
                        ))
                    },
                    onToggle = { posIdx ->
                        val newAssigns = assigns.toMutableList()
                        val currentlySelected = newAssigns[posIdx] == groupIdx
                        if (currentlySelected) {
                            newAssigns[posIdx] = null
                        } else {
                            newAssigns[posIdx] = groupIdx
                        }
                        onConfigChange(config.copy(transitionAssignments = newAssigns))
                    },
                    onDelete = {
                        // 删除组后索引重映射：被删组清空 + 大于被删索引的全部减 1。
                        // 之前只把 ==groupIdx 的置 null，breaks.removeAt 后所有 >groupIdx 的
                        // 索引指向错位元素，被 effectiveAssignments 判越界置 null——
                        // 删一个课间组，其后所有课间的分配静默清空。
                        val newAssigns = assigns.map { v ->
                            when {
                                v == groupIdx -> null
                                v != null && v > groupIdx -> v - 1
                                else -> v
                            }
                        }
                        onConfigChange(config.copy(
                            breaks = config.breaks.toMutableList().also { it.removeAt(groupIdx) },
                            transitionAssignments = newAssigns
                        ))
                    }
                )
            }
        }

        // ===== 预览 =====
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.preview),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        PreviewList(config = config, assigns = assigns)
    }
}

@Composable
private fun BreakGroupSection(
    breakOption: BreakOption,
    groupIdx: Int,
    totalPeriods: Int,
    assigns: List<Int?>,
    onMinuteChange: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val colors = SleepyTheme.colors
    val groupColor = if (breakOption.isLong) colors.primary else colors.tertiary
    val n = (totalPeriods - 1).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(colors.surfaceContainerLow, SleepyTheme.shapes.medium)
            .padding(12.dp)
    ) {
        // Header: 名称 + 分钟数 + 删除
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            // 颜色 chip
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(groupColor, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                breakOption.displayLabel(groupIdx),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "",
                unit = stringResource(R.string.unit_minutes),
                value = breakOption.minutes,
                onValueChange = onMinuteChange,
                modifier = Modifier.width(110.dp)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.delete),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 位置卡片网格
        if (n > 0) {
            val cardsPerRow = 8
            val rows = (0 until n).chunked(cardsPerRow)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { rowIndices ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowIndices.forEach { posIdx ->
                            val selected = assigns.getOrNull(posIdx) == groupIdx
                            PositionCard(
                                label = "${posIdx + 1}.5",
                                selected = selected,
                                groupColor = groupColor,
                                onClick = { onToggle(posIdx) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 填满空位
                        repeat(cardsPerRow - rowIndices.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Text(
                stringResource(R.string.break_min_two_periods),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PositionCard(
    label: String,
    selected: Boolean,
    groupColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors
    val bg = if (selected) groupColor else colors.surfaceContainerHigh
    val fg = if (selected) colors.onPrimary else colors.onSurfaceVariant
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .background(bg, SleepyTheme.shapes.small)
            .noRippleClickable(onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun PreviewList(
    config: SmartPeriodConfig,
    assigns: List<Int?>
) {
    val colors = SleepyTheme.colors
    val rows = config.derive()
    val transMins = config.effectiveTransitionMinutes()
    val shortBreakLabel = stringResource(R.string.short_break)
    val longBreakLabel = stringResource(R.string.long_break)
    val breakContinuous0 = stringResource(R.string.break_continuous_0)
    if (rows.isEmpty()) {
        Text(stringResource(R.string.empty_placeholder), color = colors.onSurfaceVariant)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, SleepyTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        rows.forEachIndexed { i, slot ->
            Text(
                stringResource(R.string.period_time_range, slot.node, slot.start, slot.end),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface
            )
            if (i < transMins.size) {
                val mins = transMins[i]
                val (text, color) = when {
                    mins == 0 -> breakContinuous0 to colors.onSurfaceVariant
                    else -> stringResource(R.string.break_continuous_n, mins, if (assigns[i] != null && assigns[i]!! in config.breaks.indices && config.breaks[assigns[i]!!].isLong) longBreakLabel else shortBreakLabel) to colors.onSurfaceVariant
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = if (mins > 0) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    unit: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    TextField(
        value = text,
        onValueChange = { newText ->
            if (newText.all { it.isDigit() } && newText.length <= 4) {
                text = newText
                val parsed = newText.toIntOrNull()
                if (parsed != null) onValueChange(parsed)
            }
        },
        label = if (label.isNotBlank()) {
            { Text(label) }
        } else null,
        suffix = if (unit.isNotBlank()) {
            { Text(unit) }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        shape = SleepyTheme.fieldShape,
        colors = SleepyTheme.fieldColors(),
        modifier = modifier
    )
}

@Composable
private fun AddBreakChip(
    label: String,
    color: Color,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = false,
        onClick = onAdd,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add_label, label))
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = color.copy(alpha = SleepyTheme.Alpha.tinted),
            labelColor = color
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = false,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            disabledSelectedBorderColor = Color.Transparent
        ),
        modifier = modifier
    )
}

// smartConfigToRows 死函数已删（全库零调用; 调用方直接用 SmartPeriodConfig.derive()）