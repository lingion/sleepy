package com.lingion.sleepy.ui.screen.mine

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.calendar.CalendarImportOptions
import com.lingion.sleepy.data.calendar.CalendarImportPreview
import com.lingion.sleepy.data.calendar.CalendarImportRange
import com.lingion.sleepy.data.calendar.CalendarImportResult
import com.lingion.sleepy.data.calendar.SystemCalendarManager
import com.lingion.sleepy.data.calendar.SystemCalendarInfo
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun CalendarImportDialog(
    table: TimeTableEntity,
    courses: List<CourseEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var calendars by remember { mutableStateOf<List<SystemCalendarInfo>>(emptyList()) }
    var selectedCalendarId by remember { mutableLongStateOf(AppPrefs.getCalendarTargetId(context)) }
    var range by remember {
        mutableStateOf(runCatching { CalendarImportRange.valueOf(AppPrefs.getCalendarImportRange(context)) }
            .getOrDefault(CalendarImportRange.NEXT_WEEK))
    }
    var transfersEnabled by remember { mutableStateOf(AppPrefs.isCalendarApplyTransfers(context)) }
    var reminderEnabled by remember { mutableStateOf(AppPrefs.getCalendarReminderMinutes(context) != null) }
    var reminderMinutes by remember { mutableStateOf((AppPrefs.getCalendarReminderMinutes(context) ?: 15).toString()) }
    var alarmEnabled by remember { mutableStateOf(AppPrefs.isCalendarFirstAlarmEnabled(context)) }
    var alarmMinutes by remember { mutableStateOf(AppPrefs.getCalendarFirstAlarmMinutes(context).toString()) }
    var calendarMenu by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<CalendarImportPreview?>(null) }
    var result by remember { mutableStateOf<CalendarImportResult?>(null) }
    var managedCount by remember { mutableIntStateOf(0) }
    var showFinalConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteResult by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    fun currentOptions(): CalendarImportOptions = CalendarImportOptions(
        calendarId = selectedCalendarId,
        range = range,
        applyHolidayTransfers = transfersEnabled,
        reminderMinutes = if (reminderEnabled) reminderMinutes.toIntOrNull()?.coerceIn(0, 999) ?: 15 else null,
        firstPeriodAlarmEnabled = alarmEnabled,
        firstPeriodAlarmMinutes = alarmMinutes.toIntOrNull()?.coerceIn(0, 999) ?: 60
    )

    suspend fun reloadCalendars() {
        calendars = SystemCalendarManager.writableCalendars(context)
        if (calendars.none { it.id == selectedCalendarId }) {
            selectedCalendarId = calendars.firstOrNull()?.id ?: -1L
            if (selectedCalendarId >= 0) AppPrefs.setCalendarTargetId(context, selectedCalendarId)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionDenied = grants.values.any { !it }
        scope.launch {
            if (!permissionDenied) reloadCalendars()
        }
    }

    LaunchedEffect(table.id) {
        if (SystemCalendarManager.hasCalendarPermissions(context)) reloadCalendars()
        managedCount = withContext(Dispatchers.IO) {
            AppDatabase.get(context).calendarImportRecordDao().forTable(table.id).size
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_import_title), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!SystemCalendarManager.hasCalendarPermissions(context)) {
                    Text(stringResource(R.string.calendar_permission_explanation), style = MaterialTheme.typography.bodyMedium)
                    if (permissionDenied) Text(stringResource(R.string.calendar_permission_denied), color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                    }) { Text(stringResource(R.string.calendar_permission_action)) }
                } else if (calendars.isEmpty()) {
                    Text(stringResource(R.string.calendar_no_writable_calendar), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.calendar_target_label), style = MaterialTheme.typography.labelLarge)
                    Column {
                        TextButton(onClick = { calendarMenu = true }) {
                            Text(calendars.firstOrNull { it.id == selectedCalendarId }?.let {
                                "${it.displayName} · ${it.accountName}"
                            } ?: stringResource(R.string.calendar_choose_target))
                        }
                        DropdownMenu(expanded = calendarMenu, onDismissRequest = { calendarMenu = false }) {
                            calendars.forEach { calendar ->
                                DropdownMenuItem(
                                    text = { Text("${calendar.displayName} · ${calendar.accountName}") },
                                    onClick = {
                                        selectedCalendarId = calendar.id
                                        AppPrefs.setCalendarTargetId(context, calendar.id)
                                        calendarMenu = false
                                        preview = null
                                    }
                                )
                            }
                        }
                    }

                    Text(stringResource(R.string.calendar_range_label), style = MaterialTheme.typography.labelLarge)
                    CalendarImportRange.entries.forEach { candidate ->
                        val label = when (candidate) {
                            CalendarImportRange.NEXT_WEEK -> stringResource(R.string.calendar_range_week)
                            CalendarImportRange.NEXT_MONTH -> stringResource(R.string.calendar_range_month)
                            CalendarImportRange.SEMESTER -> stringResource(R.string.calendar_range_semester)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = range == candidate, onClick = {
                                range = candidate
                                AppPrefs.setCalendarImportRange(context, candidate.name)
                                preview = null
                            })
                            Text(label)
                        }
                    }

                    val hasTransfers = remember(table.id) { AppPrefs.getHolidayTransfers(context, table.id).isNotEmpty() }
                    if (hasTransfers) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = transfersEnabled, onCheckedChange = {
                                transfersEnabled = it
                                AppPrefs.setCalendarApplyTransfers(context, it)
                                preview = null
                            })
                            Text(stringResource(R.string.calendar_apply_transfers))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = reminderEnabled, onCheckedChange = {
                            reminderEnabled = it
                            AppPrefs.setCalendarReminderMinutes(context, if (it) reminderMinutes.toIntOrNull() ?: 15 else null)
                            preview = null
                        })
                        Text(stringResource(R.string.calendar_normal_reminder))
                    }
                    if (reminderEnabled) {
                        OutlinedTextField(
                            value = reminderMinutes,
                            onValueChange = { value ->
                                reminderMinutes = value.filter(Char::isDigit).take(3)
                                AppPrefs.setCalendarReminderMinutes(context, reminderMinutes.toIntOrNull() ?: 15)
                                preview = null
                            },
                            label = { Text(stringResource(R.string.calendar_minutes_before)) },
                            suffix = { Text(stringResource(R.string.reminder_before_minutes_unit)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alarmEnabled, onCheckedChange = {
                            alarmEnabled = it
                            AppPrefs.setCalendarFirstAlarmEnabled(context, it)
                            preview = null
                        })
                        Text(stringResource(R.string.calendar_first_period_alarm))
                    }
                    if (alarmEnabled) {
                        Text(stringResource(R.string.calendar_first_period_scope),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = alarmMinutes,
                            onValueChange = { value ->
                                alarmMinutes = value.filter(Char::isDigit).take(3)
                                AppPrefs.setCalendarFirstAlarmMinutes(context, alarmMinutes.toIntOrNull() ?: 60)
                                preview = null
                            },
                            label = { Text(stringResource(R.string.calendar_alarm_minutes_before)) },
                            suffix = { Text(stringResource(R.string.reminder_before_minutes_unit)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Text(stringResource(R.string.calendar_alarm_disclaimer), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(Modifier.height(2.dp))
                    Button(enabled = !busy && selectedCalendarId >= 0, onClick = {
                        scope.launch {
                            busy = true
                            val current = currentOptions()
                            AppPrefs.setCalendarTargetId(context, current.calendarId)
                            preview = withContext(Dispatchers.Default) {
                                SystemCalendarManager.buildPreview(context, table, courses, current)
                            }
                            result = null
                            busy = false
                        }
                    }) { Text(stringResource(R.string.calendar_preview_action)) }

                    preview?.let { p ->
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(stringResource(R.string.calendar_preview_count, p.rows.size), fontWeight = FontWeight.SemiBold)
                                if (p.skippedInvalidTime > 0) Text(stringResource(R.string.calendar_invalid_skipped, p.skippedInvalidTime))
                                p.rows.take(8).forEach { row ->
                                    val labels = buildList {
                                        if (row.transferred) add(stringResource(R.string.calendar_transfer_mark))
                                        if (row.firstPeriodAlarm) add(stringResource(R.string.calendar_alarm_mark))
                                    }.joinToString(" · ").let { if (it.isBlank()) "" else " · $it" }
                                    Text("${row.date}  ${row.start}–${row.end}  ${row.courseName}$labels",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (p.rows.size > 8) Text(stringResource(R.string.calendar_more_rows, p.rows.size - 8),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.calendar_second_confirm_warning),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                Button(enabled = p.rows.isNotEmpty() && !busy, onClick = { showFinalConfirm = true }) {
                                    Text(stringResource(R.string.calendar_confirm_import))
                                }
                            }
                        }
                    }

                    result?.let { outcome ->
                        Text(stringResource(R.string.calendar_result_summary, outcome.inserted, outcome.updated,
                            outcome.unchanged, outcome.skippedEdited, outcome.skippedInvalidTime, outcome.alarmFallback, outcome.errors),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    deleteResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                    if (managedCount > 0) {
                        TextButton(onClick = { showDeleteConfirm = true }, enabled = !busy) {
                            Text(stringResource(R.string.calendar_delete_managed, managedCount), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (busy) CircularProgressIndicator()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (showFinalConfirm && preview != null) {
        AlertDialog(
            onDismissRequest = { showFinalConfirm = false },
            title = { Text(stringResource(R.string.calendar_final_confirm_title)) },
            text = { Text(stringResource(R.string.calendar_final_confirm_body, preview!!.rows.size)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    showFinalConfirm = false
                    scope.launch {
                        busy = true
                        result = SystemCalendarManager.import(context, table, courses, currentOptions(), UUID.randomUUID().toString())
                        managedCount = withContext(Dispatchers.IO) { AppDatabase.get(context).calendarImportRecordDao().forTable(table.id).size }
                        busy = false
                    }
                }) { Text(stringResource(R.string.calendar_final_import_action)) }
            },
            dismissButton = { TextButton(onClick = { showFinalConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.calendar_delete_confirm_title)) },
            text = { Text(stringResource(R.string.calendar_delete_confirm_body, managedCount)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        busy = true
                        val deleted = SystemCalendarManager.deleteManagedForTable(context, table.id)
                        deleteResult = context.getString(R.string.calendar_delete_result, deleted.deleted, deleted.skippedEdited, deleted.missing)
                        managedCount = withContext(Dispatchers.IO) { AppDatabase.get(context).calendarImportRecordDao().forTable(table.id).size }
                        busy = false
                    }
                }) { Text(stringResource(R.string.calendar_delete_action)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
