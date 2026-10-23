package com.lingion.sleepy.ui.screen.manage

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable

@Composable
fun ManagementPage(
    onAddTable: () -> Unit,
    onManualAdd: () -> Unit,
    onEditCurrentTable: () -> Unit,
    onOpenPeriodTables: () -> Unit,
    onExportRequested: () -> Unit,
    onOpenAllTables: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val table = state.currentTable

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Dock 悬浮底栏: 滚动尾部多留 Dock 总高(FAB 语义, 同今日/我的页)
        // contentPadding(非 Modifier.padding): 内容能滚到屏幕边缘自然滑出, 禁列表整体内缩硬裁
        val navExtra = com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + navExtra
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .noRippleClickable(enabled = table != null, onClick = onEditCurrentTable)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.manage_current_table),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primary
                        )
                        Text(
                            text = table?.name ?: stringResource(R.string.manage_no_table),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Text(
                            text = if (table != null) {
                                stringResource(R.string.table_info, table.startDate, state.currentWeek, state.courses.size)
                            } else {
                                stringResource(R.string.manage_no_table_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onOpenAllTables,
                        modifier = Modifier.padding(top = 4.dp, end = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.manage_view_all_tables),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // 添加课表、添加课程、作息表管理、备份与导出。
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ManageCard(
                        icon = Icons.Outlined.FileUpload,
                        title = stringResource(R.string.manage_add_table),
                        subtitle = stringResource(R.string.manage_add_table_sub),
                        onClick = onAddTable
                    )
                    ManageCard(
                        icon = Icons.Outlined.Add,
                        title = stringResource(R.string.manage_manual_add),
                        subtitle = stringResource(R.string.manage_manual_add_sub),
                        onClick = onManualAdd
                    )
                    ManageCard(
                        icon = Icons.Outlined.Schedule,
                        title = stringResource(R.string.manage_period_tables),
                        subtitle = stringResource(R.string.manage_period_tables_sub),
                        onClick = onOpenPeriodTables
                    )
                    ManageCard(
                        icon = Icons.Outlined.Share,
                        title = stringResource(R.string.manage_export),
                        subtitle = stringResource(R.string.manage_export_sub),
                        onClick = onExportRequested
                    )
                }
            }
        }
    }
}

@Composable
private fun ManageCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .noRippleClickable(onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(SleepyTheme.shapes.medium)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
