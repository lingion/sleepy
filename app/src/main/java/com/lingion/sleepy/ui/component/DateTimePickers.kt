package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日期输入字段 — 手动输入 + 点击图标弹出原生日期选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = SleepyTheme.fieldShape,
    isError: Boolean = false
) {
    val colors = SleepyTheme.colors
    var showPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val fieldColors = SleepyTheme.fieldColors()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = shape,
            isError = isError,
            colors = fieldColors
        )
        IconButton(
            onClick = { showPicker = true },
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = stringResource(R.string.select_date),
                tint = colors.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("Asia/Shanghai"))
                            .toLocalDate()
                        onValueChange(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 时间输入字段 — 点击输入框直接弹时间选择器，无 clock 图标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = SleepyTheme.fieldShape
) {
    val colors = SleepyTheme.colors
    var showPicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = value.substringBefore(":").toIntOrNull() ?: 8,
        initialMinute = value.substringAfter(":").toIntOrNull() ?: 0,
        is24Hour = true
    )
    val fieldColors = SleepyTheme.fieldColors()

    // Box + clickable 包装：OutlinedTextField 内部设 enabled=false
    // 让 click 事件穿透到外层 Box 的 clickable。
    // Box 必须先 clip(shape) 再 clickable — 否则涟漪是方角、且能溢出字段圆角。
    Box(
        modifier = modifier
            .clip(shape)
            .noRippleClickable { showPicker = true }
    ) {
        TextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = fieldColors
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.select_time), color = colors.onSurface) },
            text = {
                // 默认 TimePicker 配色 — 与 ReminderScreen 时间弹窗一致, 不再单独覆写表盘色
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    onValueChange("$h:$m")
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
