package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow

/**
 * 节次编辑器 v1.0.16+
 *
 * 支持两种模式，顶部 Tab 切换：
 *  - [Mode.Manual] 手动模式：原 TimeSlotEditor，逐节编辑 start/end
 *  - [Mode.Auto]   自动模式：智慧节次，三个字段 + break 分组卡片
 *
 * 调用方持有 rows（手动模式），config（自动模式），切换模式时通过
 * [onRowsChange]/[onConfigChange] 通知。应用自动模式后通过 [onApplyAuto]
 * 把生成的 rows 回填给手动模式。
 */
@Composable
fun TimeSlotEditor(
    rows: List<TimeSlotRow>,
    onRowsChange: (List<TimeSlotRow>) -> Unit,
    smartConfig: SmartPeriodConfig = SmartPeriodConfig(),
    onSmartConfigChange: (SmartPeriodConfig) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(Mode.Manual) }

    // Bug 2 fix: 自动模式下，smartConfig 一旦变化就立刻 derive 出 rows 同步给上层，
    // 否则保存时 timeJson 用的还是旧的手动 rows，导致"保存的不是自动模式数据"。
    LaunchedEffect(mode, smartConfig) {
        if (mode == Mode.Auto) {
            onRowsChange(smartConfig.derive())
        }
    }

    Column(modifier = modifier) {
        // ===== Tab 切换 =====
        ModeTabSwitch(
            current = mode,
            onChange = { mode = it }
        )
        Spacer(Modifier.height(8.dp))

        when (mode) {
            Mode.Manual -> ManualTimeSlotEditor(
                rows = rows,
                onRowsChange = onRowsChange
            )
            Mode.Auto -> SmartPeriodEditor(
                config = smartConfig,
                onConfigChange = onSmartConfigChange
            )
        }
    }
}

/**
 * 给"导入前确认"等场景用的简化包装：
 * 只用手动模式（无 Tab 切换），保持向后兼容。
 */
@Composable
fun TimeSlotEditorManualOnly(
    rows: List<TimeSlotRow>,
    onRowsChange: (List<TimeSlotRow>) -> Unit,
    modifier: Modifier = Modifier
) {
    ManualTimeSlotEditor(rows = rows, onRowsChange = onRowsChange, modifier = modifier)
}

enum class Mode { Manual, Auto }

@Composable
private fun ModeTabSwitch(current: Mode, onChange: (Mode) -> Unit) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Mode.values().forEach { m ->
            val selected = m == current
            val bg = if (selected) colors.primary else Color.Transparent
            val fg = if (selected) colors.onPrimary else colors.onSurfaceVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(bg, RoundedCornerShape(10.dp))
                    .clickable { onChange(m) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (m) {
                        Mode.Manual -> "手动模式"
                        Mode.Auto -> "自动模式"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = fg
                )
            }
        }
    }
}

@Composable
private fun ManualTimeSlotEditor(
    rows: List<TimeSlotRow>,
    onRowsChange: (List<TimeSlotRow>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SleepyTheme.colors

    Column(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.n_periods, rows.size),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            TextButton(
                onClick = { onRowsChange(TimeTableUtils.appendEmptyRow(rows)) }
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.add_period))
            }
        }

        // Rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainerLow, RoundedCornerShape(16.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rows.forEach { row ->
                TimeSlotRowItem(
                    row = row,
                    canDelete = rows.size > 1,
                    onStartChange = { newStart ->
                        onRowsChange(rows.map { if (it.node == row.node) it.copy(start = newStart) else it })
                    },
                    onEndChange = { newEnd ->
                        onRowsChange(rows.map { if (it.node == row.node) it.copy(end = newEnd) else it })
                    },
                    onDelete = {
                        onRowsChange(TimeTableUtils.removeAndRenumber(rows, row.node))
                    }
                )
            }
        }
    }
}

@Composable
private fun TimeSlotRowItem(
    row: TimeSlotRow,
    canDelete: Boolean,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.course_node_format, row.node),
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface
        )
        TimePickerField(
            value = row.start,
            onValueChange = onStartChange,
            label = stringResource(R.string.start_label),
            modifier = Modifier.weight(1f)
        )
        TimePickerField(
            value = row.end,
            onValueChange = onEndChange,
            label = stringResource(R.string.end_label),
            modifier = Modifier.weight(1f)
        )
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.RemoveCircleOutline,
                    contentDescription = stringResource(R.string.delete_period),
                    tint = colors.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(Modifier.width(32.dp))
        }
    }
}