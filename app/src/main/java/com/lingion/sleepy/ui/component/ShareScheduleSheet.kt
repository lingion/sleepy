package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.parser.ScheduleExporter
import com.lingion.sleepy.data.parser.SleepyNativeExporter
import com.lingion.sleepy.ui.screen.mine.ExportItem
import com.lingion.sleepy.ui.screen.mine.exportAndShare
import com.lingion.sleepy.ui.screen.mine.shareText
import com.lingion.sleepy.ui.screen.mine.stamp
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.launch

/**
 * 顶栏分享按钮的格式选择底部弹窗(v7.10.7) — 从下而上弹出,沿用导出页三种格式:
 * WakeUp JSON / WakeUp 分享文本 / ICS 日历。条目视觉与导出操作全部复用
 * ExportScreen 的现成实现,选中即走系统分享,弹窗保持展开(与导出页行为一致,
 * 由用户返回键/点外部关闭)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScheduleSheet(
    table: TimeTableEntity,
    courses: List<CourseEntity>,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = SleepyTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.share_sheet_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            ExportItem(
                icon = Icons.Outlined.Code,
                title = stringResource(R.string.export_json_title),
                subtitle = stringResource(R.string.export_json_subtitle),
                onClick = {
                    scope.launch {
                        exportAndShare(
                            ctx = ctx,
                            fileName = "sleepy_${table.name}_${stamp()}.json",
                            mime = "application/json",
                            content = ScheduleExporter.exportWakeUpJson(table, courses),
                            displayName = table.name,
                            onResult = { }
                        )
                    }
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline)
            )
            ExportItem(
                icon = Icons.Outlined.Share,
                title = stringResource(R.string.export_share_title),
                subtitle = stringResource(R.string.export_share_subtitle),
                onClick = {
                    scope.launch {
                        shareText(
                            ctx = ctx,
                            content = ScheduleExporter.exportWakeUpShareText(table, courses),
                            subject = table.name,
                            onResult = { }
                        )
                    }
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline)
            )
            ExportItem(
                icon = Icons.Outlined.CalendarMonth,
                title = stringResource(R.string.export_ics_title),
                subtitle = stringResource(R.string.export_ics_subtitle),
                onClick = {
                    scope.launch {
                        exportAndShare(
                            ctx = ctx,
                            fileName = "sleepy_${table.name}_${stamp()}.ics",
                            mime = "text/calendar",
                            content = ScheduleExporter.exportIcs(table, courses),
                            displayName = table.name,
                            onResult = { }
                        )
                    }
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline)
            )
            ExportItem(
                icon = Icons.Outlined.Star,
                title = stringResource(R.string.export_native_title),
                subtitle = stringResource(R.string.export_native_subtitle),
                onClick = {
                    scope.launch {
                        // 分享形态: marker 包裹 + 无 chk(IM 场景最小体积), 接收方粘贴导入
                        shareText(
                            ctx = ctx,
                            content = SleepyNativeExporter.exportShareText(
                                table.name, table.startDate, table.maxWeek, table.nodesPerDay,
                                table.timeJson, courses
                            ),
                            subject = table.name,
                            onResult = { }
                        )
                    }
                }
            )
        }
    }
}
