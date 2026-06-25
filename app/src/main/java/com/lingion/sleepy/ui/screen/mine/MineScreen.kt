package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.BuildConfig
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.launch

@Composable
fun MineScreen(
    viewModel: ScheduleViewModel = viewModel(),
    darkMode: Boolean = false,
    onToggleDark: () -> Unit = {},
    onOpenAllTables: () -> Unit = {},
    onOpenThemeColor: () -> Unit = {},
    onOpenMoreSettings: () -> Unit = {},
    onOpenExport: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showAbout by remember { mutableStateOf(false) }
    var reminderOn by remember { mutableStateOf(AppPrefs.isReminderEnabled(context)) }

    val showSnack: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = stringResource(R.string.tab_mine),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.mine_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // 数据统计卡
            item {
                StatsCard(
                    tableCount = state.tables.size,
                    courseCount = state.courses.size,
                    week = state.currentWeek
                )
            }

            // 设置项
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceContainer)
                ) {
                    SettingsItem(
                        icon = Icons.Outlined.Edit,
                        label = stringResource(R.string.all_tables),
                        onClick = onOpenAllTables
                    )
                    Divider()
                    SettingsItem(
                        icon = Icons.Outlined.Share,
                        label = stringResource(R.string.mine_export),
                        onClick = onOpenExport
                    )
                    Divider()
                    SettingsItem(
                        icon = Icons.Outlined.Notifications,
                        label = stringResource(R.string.mine_reminder),
                        trailing = {
                            Switch(
                                checked = reminderOn,
                                onCheckedChange = { on ->
                                    reminderOn = on
                                    AppPrefs.setReminderEnabled(context, on)
                                    if (on) {
                                        SleepyApp.get().notificationScheduler.scheduleDailyReminder()
                                        showSnack(context.getString(R.string.mine_reminder_on))
                                    } else {
                                        SleepyApp.get().notificationScheduler.cancelDailyReminder()
                                        showSnack(context.getString(R.string.mine_reminder_off))
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onPrimary,
                                    checkedTrackColor = colors.primary
                                )
                            )
                        }
                    )
                    Divider()
                    SettingsItem(
                        icon = Icons.Outlined.DarkMode,
                        label = stringResource(R.string.mine_dark_mode),
                        trailing = {
                            Switch(
                                checked = darkMode,
                                onCheckedChange = { onToggleDark() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onPrimary,
                                    checkedTrackColor = colors.primary
                                )
                            )
                        }
                    )
                    Divider()
                    SettingsItem(
                        icon = Icons.Outlined.Palette,
                        label = stringResource(R.string.mine_theme_color),
                        onClick = onOpenThemeColor
                    )
                    Divider()
                    SettingsItem(
                        icon = Icons.Outlined.Refresh,
                        label = stringResource(R.string.mine_refresh_widgets),
                        onClick = {
                            scope.launch {
                                com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged(context)
                                showSnack(context.getString(R.string.mine_refresh_widgets_done))
                            }
                        }
                    )
                    Divider()
                    SettingsItem(
                        icon = Icons.Outlined.Tune,
                        label = stringResource(R.string.mine_more_settings),
                        isLast = true,
                        onClick = onOpenMoreSettings
                    )
                }
            }

        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.mine_about_title)) },
            text = {
                Text(
                    text = stringResource(R.string.mine_about_body, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun StatsCard(tableCount: Int, courseCount: Int, week: Int) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(value = tableCount.toString(), label = stringResource(R.string.mine_stat_tables))
        Divider(vertical = true)
        StatItem(value = courseCount.toString(), label = stringResource(R.string.mine_stat_courses))
        Divider(vertical = true)
        StatItem(value = week.toString(), label = stringResource(R.string.mine_stat_week))
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    val colors = SleepyTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {},
    isLast: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun Divider(vertical: Boolean = false) {
    val colors = SleepyTheme.colors
    if (vertical) {
        androidx.compose.material3.VerticalDivider(
            modifier = Modifier
                .height(36.dp)
                .width(1.dp),
            color = colors.outline.copy(alpha = 0.18f)
        )
    } else {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            color = colors.outline.copy(alpha = 0.18f)
        )
    }
}
