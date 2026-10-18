package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.SectionHeader
import com.lingion.sleepy.ui.component.SettingToggleRow
import com.lingion.sleepy.ui.component.SettingsCard
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 小组件设置页(设计 §3): 原通用页「小组件」板块整体平移至此,与「通用」并列。
 *
 * 内容 = 外观开关组(去色/分隔线/最近忙日) + 「添加或管理桌面小组件」条目。
 * 偏好存储键零迁移 — 与 GeneralSettingsScreen 时代完全一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit = {},
    onOpenWidgetManagement: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    var expanded by rememberSaveable { mutableStateOf(true) }
    var widgetColorless by remember { mutableStateOf(AppPrefs.isWidgetColorless(context)) }
    var widgetSeparator by remember { mutableStateOf(AppPrefs.isWidgetSeparator(context)) }
    var nearestBusyDay by remember { mutableStateOf(AppPrefs.isNearestBusyDay(context)) }

    // 显示项变更后立即刷小组件(管线随板块自 GeneralSettingsScreen 平移)
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        widgetScope.launch { WidgetUpdater.notifyDataChanged(context) }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_widget)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
            ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(title = stringResource(R.string.appearance_section_widget))
            }
            item {
                SettingsCard(
                    title = stringResource(R.string.settings_widget),
                    expanded = expanded,
                    onToggle = { expanded = !expanded }
                ) {
                    SettingToggleRow(
                        label = stringResource(R.string.settings_widget_colorless),
                        subtitle = stringResource(R.string.settings_widget_colorless_sub),
                        checked = widgetColorless,
                        onCheckedChange = {
                            widgetColorless = it
                            AppPrefs.setWidgetColorless(context, it)
                            refreshWidgets()
                        }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    SettingToggleRow(
                        label = stringResource(R.string.settings_widget_separator),
                        subtitle = stringResource(R.string.settings_widget_separator_sub),
                        checked = widgetSeparator,
                        onCheckedChange = {
                            widgetSeparator = it
                            AppPrefs.setWidgetSeparator(context, it)
                            refreshWidgets()
                        }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    SettingToggleRow(
                        label = stringResource(R.string.settings_nearest_busy_day),
                        subtitle = stringResource(R.string.settings_nearest_busy_day_sub),
                        checked = nearestBusyDay,
                        onCheckedChange = {
                            nearestBusyDay = it
                            AppPrefs.setNearestBusyDay(context, it)
                            refreshWidgets()
                        }
                    )
                }
            }

            // 添加或管理桌面小组件: 跳「添加或管理」页(右上 ＋ 进画廊)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .noRippleClickable(onOpenWidgetManagement)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.widget_manage_entry),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
