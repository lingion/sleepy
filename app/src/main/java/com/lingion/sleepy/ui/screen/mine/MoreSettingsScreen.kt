package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSettingsScreen(onBack: () -> Unit) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current

    var language by remember { mutableStateOf(AppPrefs.getLanguage(context)) }
    var displayMode by remember { mutableStateOf(AppPrefs.getDisplayMode(context)) }
    var showDate by remember { mutableStateOf(AppPrefs.isShowDate(context)) }
    var visibleDays by remember { mutableStateOf(AppPrefs.getVisibleDays(context)) }

    val languages = listOf(
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "es" to "Español"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            // 语言设置
            item {
                SettingsCard(title = stringResource(R.string.settings_language)) {
                    languages.forEach { (code, label) ->
                        val selected = language == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    language = code
                                    AppPrefs.setLanguage(context, code)
                                    // Recreate activity to apply locale
                                    (context as? android.app.Activity)?.recreate()
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) colors.primary else colors.onSurface
                            )
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (code != languages.last().first) {
                            Divider(color = colors.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }

            // 显示模式：节次 / 时间
            item {
                SettingsCard(title = stringResource(R.string.settings_display_mode)) {
                    DisplayModeOption(
                        label = stringResource(R.string.settings_display_node),
                        subtitle = stringResource(R.string.settings_display_node_sub),
                        selected = displayMode == "node",
                        onClick = {
                            displayMode = "node"
                            AppPrefs.setDisplayMode(context, "node")
                        }
                    )
                    Divider(color = colors.outlineVariant.copy(alpha = 0.3f))
                    DisplayModeOption(
                        label = stringResource(R.string.settings_display_time),
                        subtitle = stringResource(R.string.settings_display_time_sub),
                        selected = displayMode == "time",
                        onClick = {
                            displayMode = "time"
                            AppPrefs.setDisplayMode(context, "time")
                        }
                    )
                }
            }

            // 网格显示日期
            item {
                SettingsCard(title = stringResource(R.string.settings_grid_view)) {
                    SettingToggleRow(
                        label = stringResource(R.string.settings_show_date),
                        subtitle = stringResource(R.string.settings_show_date_sub),
                        checked = showDate,
                        onCheckedChange = {
                            showDate = it
                            AppPrefs.setShowDate(context, it)
                        }
                    )
                }
            }

            // 显示天数
            item {
                SettingsCard(title = stringResource(R.string.settings_visible_days)) {
                    Text(
                        text = stringResource(R.string.settings_visible_days_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    (1..7).forEach { day ->
                        val checked = day in visibleDays
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newDays = if (checked) {
                                        visibleDays - day
                                    } else {
                                        visibleDays + day
                                    }
                                    if (newDays.isNotEmpty()) {
                                        visibleDays = newDays
                                        AppPrefs.setVisibleDays(context, newDays)
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = DateUtils.localizedDay(day, context),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface
                            )
                            Switch(
                                checked = checked,
                                onCheckedChange = { on ->
                                    val newDays = if (on) visibleDays + day else visibleDays - day
                                    if (newDays.isNotEmpty()) {
                                        visibleDays = newDays
                                        AppPrefs.setVisibleDays(context, newDays)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onPrimary,
                                    checkedTrackColor = colors.primary
                                )
                            )
                        }
                        if (day != 7) {
                            Divider(color = colors.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun DisplayModeOption(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) colors.primary else colors.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary
            )
        )
    }
}
