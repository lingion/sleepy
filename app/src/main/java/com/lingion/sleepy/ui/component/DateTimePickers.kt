package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePicker as MaterialDatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import com.lingion.sleepy.ui.component.liquidglass.GlassDatePickerDialog as DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.lingion.sleepy.ui.component.liquidglass.GlassIconButton as IconButton
import androidx.compose.material3.TextField
import com.lingion.sleepy.ui.component.liquidglass.GlassButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.component.liquidglass.LocalGlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.ui.theme.noRippleClickable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Material owns the calendar and its animation; the app owns the input focus lifetime. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepyDatePicker(
    state: DatePickerState,
    modifier: Modifier = Modifier,
    showModeToggle: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pickerState = remember(state, focusManager, keyboard) {
        object : DatePickerState by state {
            override var displayMode: DisplayMode
                get() = state.displayMode
                set(value) {
                    if (state.displayMode != value && value == DisplayMode.Picker) {
                        // Release the outgoing text field before Material starts expanding
                        // the calendar, rather than when AnimatedContent finally disposes it.
                        focusManager.clearFocus(force = true)
                        keyboard?.hide()
                    }
                    state.displayMode = value
                }
        }
    }
    val displayMode = state.displayMode
    val previousDisplayMode = remember(state) { mutableStateOf(displayMode) }
    SideEffect {
        // A caller can change its original state without going through the proxy.
        // Clean up in that composition's apply phase, before its expanding frame
        // is drawn; a launched coroutine may run after that frame has started.
        if (previousDisplayMode.value != displayMode && displayMode == DisplayMode.Picker) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
        previousDisplayMode.value = displayMode
    }
    MaterialDatePicker(
        state = pickerState,
        modifier = modifier,
        showModeToggle = showModeToggle,
        // Material's delayed auto-focus survives in the outgoing AnimatedContent.
        // Explicit input taps still focus normally; no late request may reopen IME
        // after the user has already returned to the calendar.
        focusRequester = null,
    )
}

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
    isError: Boolean = false,
    // 调试台语义身份: 传 tag → 输入框=<tag>、日历按钮=<tag>_pick、
    // 弹窗 确定/取消=<tag>_pick_confirm/_dismiss; null = 不打标, 既有调用点零影响
    dbgTag: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val nativeDialogActions = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
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
            modifier = Modifier.weight(1f)
                .then(dbgTag?.let { Modifier.dbgTag(it, text = label, role = "textbox") } ?: Modifier),
            shape = shape,
            isError = isError,
            colors = fieldColors
        )
        IconButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .padding(start = 4.dp)
                .then(dbgTag?.let { Modifier.dbgTag("${it}_pick", role = "button") } ?: Modifier)
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
                // Material Design 保留原色块；玻璃动作采用日期弹窗的统一默认样式。
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("Asia/Shanghai"))
                                .toLocalDate()
                            onValueChange(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        }
                        showPicker = false
                    },
                    modifier = dbgTag?.let { Modifier.dbgTag("${it}_pick_confirm", role = "button") } ?: Modifier,
                    shape = if (nativeDialogActions) null else SleepyTheme.shapes.medium
                ) { Text(stringResource(R.string.ok), maxLines = if (nativeDialogActions) Int.MAX_VALUE else 1) }
            },
            dismissButton = {
                Button(
                    onClick = { showPicker = false },
                    modifier = dbgTag?.let { Modifier.dbgTag("${it}_pick_dismiss", role = "button") } ?: Modifier,
                    shape = if (nativeDialogActions) null else SleepyTheme.shapes.medium,
                    colors = if (nativeDialogActions) null else ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) { Text(stringResource(R.string.cancel), maxLines = if (nativeDialogActions) Int.MAX_VALUE else 1) }
            }
        ) {
            SleepyDatePicker(state = datePickerState)
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
    shape: CornerBasedShape = SleepyTheme.fieldShape,
    // 调试台语义身份: 传 tag → 字段=<tag>、弹窗 确定/取消=<tag>_pick_confirm/_dismiss
    dbgTag: String? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
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
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .noRippleClickable { showPicker = true }
            .then(dbgTag?.let { Modifier.dbgTag(it, text = label, role = "textbox") } ?: Modifier)
    ) {
        if (compact) {
            val horizontalPadding = if (maxWidth < 68.dp) 2.dp else 4.dp
            val regularTime = MaterialTheme.typography.bodyMedium
            val regularTimeWidth = with(LocalDensity.current) {
                textMeasurer.measure(value, style = regularTime, softWrap = false, maxLines = 1).size.width.toDp()
            }
            val timeStyle = if (regularTimeWidth + horizontalPadding * 2 > maxWidth) MaterialTheme.typography.bodySmall else regularTime
            Column(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .background(colors.surfaceContainerHighest, shape)
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                Text(value, style = timeStyle, color = colors.onSurface, maxLines = 1)
            }
        } else TextField(
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
        val nativeActions = LocalUiStyle.current == UiStyle.LiquidGlass && LocalGlassHost.current != null
        val actions: @Composable () -> Unit = {
            DialogActionButtons(
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    val h = timePickerState.hour.toString().padStart(2, '0')
                    val m = timePickerState.minute.toString().padStart(2, '0')
                    onValueChange("$h:$m")
                    showPicker = false
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { showPicker = false },
                dbgTagPrefix = dbgTag?.let { "${it}_pick" }
            )
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.select_time), color = colors.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 默认 TimePicker 配色 — 与 ReminderScreen 时间弹窗一致, 不再单独覆写表盘色
                    TimePicker(state = timePickerState)
                    if (!nativeActions) {
                        Spacer(modifier = Modifier.height(12.dp))
                        actions()
                    }
                }
            },
            confirmButton = { if (nativeActions) actions() },
            dismissButton = {}
        )
    }
}
