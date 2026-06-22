package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow

/**
 * 共享节次编辑器 —— 用于"编辑当前课表"和"导入前确认"两处。
 *
 * 行为：
 * - 每行 [第N节] [开始] [结束] [❌删除]
 * - 行数 > 1 时才显示 ❌ (至少留 1 节)
 * - 删除时**重新编号**为 1..N (友好)
 * - 底部 [➕ 添加一节] (node = max + 1, 时间留空)
 *
 * rows 完全由调用方持有 (state hoisting)，本 composable 无内部状态。
 */
@Composable
fun TimeSlotEditor(
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