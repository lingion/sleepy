package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
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

// TimeSlotEditorManualOnly 死包装已删（全库零调用; 导入场景直接用 TimeSlotEditor(mode=Manual)）

enum class Mode { Manual, Auto }

@Composable
private fun ModeTabSwitch(current: Mode, onChange: (Mode) -> Unit) {
    // 2026-08-25 用户指令: 全 app 统一色块禁描线 — M3 SegmentedButton 是描边风格,
    // 换项目统一的 SegmentedSwitcher (主页周视图/网格同款)
    SegmentedSwitcher(
        options = Mode.entries.map { it to stringResource(if (it == Mode.Manual) R.string.mode_manual else R.string.mode_auto) },
        selected = current,
        onSelect = onChange,
        modifier = Modifier.fillMaxWidth()
    )
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
                .background(colors.surfaceContainerLow, SleepyTheme.shapes.large)
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