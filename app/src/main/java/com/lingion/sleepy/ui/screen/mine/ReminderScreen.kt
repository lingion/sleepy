package com.lingion.sleepy.ui.screen.mine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(onBack: () -> Unit) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current

    var masterEnabled by remember { mutableStateOf(AppPrefs.isReminderEnabled(context)) }
    var dailyEnabled by remember { mutableStateOf(AppPrefs.isDailyReminderEnabled(context)) }
    var dailyTime by remember { mutableStateOf(AppPrefs.getDailyReminderTime(context)) }
    var beforeClassEnabled by remember { mutableStateOf(AppPrefs.isBeforeClassEnabled(context)) }
    var beforeClassMinutes by remember { mutableStateOf(AppPrefs.getBeforeClassMinutes(context)) }
    var showTimePicker by remember { mutableStateOf(false) }
    var minutesInput by remember { mutableStateOf(beforeClassMinutes.toString()) }

    // Permission launcher — NOT one-shot, can be re-triggered by clicking toggle again
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            masterEnabled = true
            AppPrefs.setReminderEnabled(context, true)
            SleepyApp.get().notificationScheduler.scheduleAll()
        } else {
            // Permission denied → revert to off
            masterEnabled = false
            AppPrefs.setReminderEnabled(context, false)
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-Android 13: permission auto-granted at install
            masterEnabled = true
            AppPrefs.setReminderEnabled(context, true)
            SleepyApp.get().notificationScheduler.scheduleAll()
        }
    }

    fun onMasterToggle(on: Boolean) {
        if (on) {
            // Check if already granted
            val alreadyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true

            if (alreadyGranted) {
                masterEnabled = true
                AppPrefs.setReminderEnabled(context, true)
                SleepyApp.get().notificationScheduler.scheduleAll()
            } else {
                requestNotificationPermission()
            }
        } else {
            masterEnabled = false
            dailyEnabled = false
            beforeClassEnabled = false
            AppPrefs.setReminderEnabled(context, false)
            AppPrefs.setDailyReminderEnabled(context, false)
            AppPrefs.setBeforeClassEnabled(context, false)
            SleepyApp.get().notificationScheduler.cancelAll()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminder_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master toggle card
            item {
                ReminderCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconBox(icon = Icons.Outlined.Notifications, color = colors.primary)
                            Spacer(modifier = Modifier.size(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.reminder_master_title),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.reminder_master_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = masterEnabled,
                            onCheckedChange = { onMasterToggle(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onPrimary,
                                checkedTrackColor = colors.primary
                            )
                        )
                    }
                }
            }

            // Sub-settings — only visible when master is on
            if (masterEnabled) {
                // Daily reminder
                item {
                    ReminderCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconBox(icon = Icons.Outlined.AccessTime, color = colors.primary)
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.reminder_daily_title),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.reminder_daily_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = dailyEnabled,
                                onCheckedChange = { on ->
                                    dailyEnabled = on
                                    AppPrefs.setDailyReminderEnabled(context, on)
                                    SleepyApp.get().notificationScheduler.scheduleAll()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onPrimary,
                                    checkedTrackColor = colors.primary
                                )
                            )
                        }

                        if (dailyEnabled) {
                            SubDivider()
                            // Time picker row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTimePicker = true }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.reminder_daily_time_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = dailyTime,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = colors.primary
                                )
                            }
                            SubDivider()
                            Text(
                                text = stringResource(R.string.reminder_daily_preview),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = 52.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                            )
                        }
                    }
                }

                // Before-class reminder
                item {
                    ReminderCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconBox(icon = Icons.Outlined.School, color = colors.primary)
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.reminder_before_class_title),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.reminder_before_class_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = beforeClassEnabled,
                                onCheckedChange = { on ->
                                    beforeClassEnabled = on
                                    AppPrefs.setBeforeClassEnabled(context, on)
                                    SleepyApp.get().notificationScheduler.scheduleAll()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onPrimary,
                                    checkedTrackColor = colors.primary
                                )
                            )
                        }

                        if (beforeClassEnabled) {
                            SubDivider()
                            // Free-input minutes field
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.reminder_before_minutes_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = minutesInput,
                                    onValueChange = { txt ->
                                        val digits = txt.filter { it.isDigit() }
                                        if (digits.isEmpty()) {
                                            minutesInput = ""
                                        } else {
                                            val v = digits.toIntOrNull() ?: 0
                                            if (v <= 999) minutesInput = digits
                                        }
                                        val v = minutesInput.toIntOrNull() ?: 1
                                        beforeClassMinutes = v.coerceIn(1, 999)
                                        AppPrefs.setBeforeClassMinutes(context, beforeClassMinutes)
                                        SleepyApp.get().notificationScheduler.scheduleAll()
                                    },
                                    modifier = Modifier.width(80.dp),
                                    shape = RoundedCornerShape(50),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.primary,
                                        unfocusedBorderColor = colors.outlineVariant,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface,
                                        focusedTextColor = colors.primary,
                                        unfocusedTextColor = colors.onSurface
                                    ),
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.reminder_before_minutes_unit),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            SubDivider()
                            Text(
                                text = stringResource(R.string.reminder_before_class_preview),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = 52.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        val parts = dailyTime.split(":")
        val timeState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 7,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.reminder_pick_time)) },
            text = {
                TimePicker(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = colors.surfaceContainer,
                        selectorColor = colors.primary,
                        timeSelectorSelectedContainerColor = colors.primaryContainer,
                        timeSelectorSelectedContentColor = colors.onPrimaryContainer
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = String.format("%02d", timeState.hour)
                    val m = String.format("%02d", timeState.minute)
                    dailyTime = "$h:$m"
                    AppPrefs.setDailyReminderTime(context, dailyTime)
                    SleepyApp.get().notificationScheduler.scheduleAll()
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun ReminderCard(content: @Composable () -> Unit) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun IconBox(icon: ImageVector, color: androidx.compose.ui.graphics.Color) {
    val colors = SleepyTheme.colors
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SubDivider() {
    val colors = SleepyTheme.colors
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = colors.outline.copy(alpha = 0.15f)
    )
}
