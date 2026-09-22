package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import kotlinx.coroutines.launch

@Composable
fun MineScreen(
    viewModel: ScheduleViewModel = viewModel(),
    onOpenAllTables: () -> Unit = {},
    onOpenCourseList: () -> Unit = {},
    onOpenPeriodTables: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenGeneral: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenReminder: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    updateNoticeVisible: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val showSnack: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        // Dock 悬浮底栏: 滚动尾部多留 Dock 总高(FAB 语义, 同今日页)
        val navExtra = com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + navExtra
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: "我的" + 副标题
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
                    // 与 CourseListScreen 同口径: 同名课程一组, 空名按 groupId 区分
                    courseCount = state.courses
                        .distinctBy { it.courseName.ifBlank { "#${it.groupId}" } }
                        .size,
                    week = state.currentWeek,
                    onOpenTables = onOpenAllTables,
                    onOpenCourses = onOpenCourseList
                )
            }

            // 设置项 (5 个导航项扁平列表)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                ) {
                    SettingsItem(icon = Icons.Outlined.Edit, label = stringResource(R.string.all_tables), onClick = onOpenAllTables)
                    Divider()
                    // issue#40: 时间节次表入口 — 与课表管理并列(设计 §4.1)
                    // 2026-09-16 用户: 「作息表/管理各课表共用的作息」副标题删除(冗余)
                    SettingsItem(icon = Icons.Outlined.Schedule, label = stringResource(R.string.mine_period_tables), onClick = onOpenPeriodTables)
                    Divider()
                    SettingsItem(icon = Icons.Outlined.Share, label = stringResource(R.string.mine_export), onClick = onOpenExport)
                    Divider()
                    SettingsItem(icon = Icons.Outlined.Notifications, label = stringResource(R.string.reminder_title), onClick = onOpenReminder)
                    Divider()
                    SettingsItem(icon = Icons.Outlined.Palette, label = stringResource(R.string.mine_appearance), onClick = onOpenAppearance)
                    Divider()
                    SettingsItem(icon = Icons.Outlined.Tune, label = stringResource(R.string.mine_general), onClick = onOpenGeneral)
                    Divider()
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        label = stringResource(R.string.about_title),
                        onClick = onOpenAbout,
                        highlighted = updateNoticeVisible
                    )
                }
            }

            // 动作区: 刷新所有小组件 (FilledTonalButton, 与上方导航项物理隔离)
            item {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged(context)
                            showSnack(context.getString(R.string.mine_refresh_widgets_done))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight),
                    shape = SleepyTheme.Buttons.shape
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.mine_refresh_widgets))
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun StatsCard(
    tableCount: Int,
    courseCount: Int,
    week: Int,
    onOpenTables: () -> Unit,
    onOpenCourses: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer).padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 2026-09-21 用户令: 统计格可点 — 表数格→所有课表, 课程格→课程清单页, 周数格静态
        StatItem(value = tableCount.toString(), label = stringResource(R.string.mine_stat_tables), onClick = onOpenTables)
        Divider(vertical = true)
        StatItem(value = courseCount.toString(), label = stringResource(R.string.mine_stat_courses), onClick = onOpenCourses)
        Divider(vertical = true)
        StatItem(value = week.toString(), label = stringResource(R.string.mine_stat_week))
    }
}

@Composable
private fun StatItem(value: String, label: String, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier
            .clip(SleepyTheme.shapes.medium)
            .noRippleClickable(onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
        else Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = colors.primary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

@Composable
// isLast / trailing 死参数已删（函数体从未读取 isLast; trailing 无任何调用方传值）
private fun SettingsItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {},
    subtitle: String? = null,
    highlighted: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) colors.primary.copy(alpha = 0.10f) else colors.surfaceContainer)
            .noRippleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(SleepyTheme.shapes.medium).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Divider(vertical: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    if (vertical) androidx.compose.material3.VerticalDivider(Modifier.height(36.dp).width(1.dp), color = colors.outline.copy(alpha = SleepyTheme.Alpha.hairline))
    else androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 72.dp), color = colors.outline.copy(alpha = SleepyTheme.Alpha.hairline))
}
