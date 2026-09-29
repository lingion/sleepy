package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.screen.schedule.SchedulePolicy
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 甲案 (设计文档 §4.1) 三选项 BottomSheet — 绑定了共享作息表时改作息内容, 弹窗决定写入作用域。
 *
 * 不变量 (§2.2/§3.4):
 *  - 三个选项始终显示(影响数量仅提示, 不改变可选项)
 *  - 数据源 = 当前实际生效作息 + 用户本次编辑 (不回退导入快照)
 *  - 点选仅记录待执行策略, 不写库; 下次保存才执行
 *  - 弹窗外点按 / 「取消」= 仅撤销本次作息改动, 其他字段草稿保留
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleConflictBottomSheet(
    periodTableName: String,
    /** 绑定到 periodTableName 的全部课表数(含当前表本身)。<2 = 仅有当前表。 */
    boundTableCount: Int,
    onSelect: (SchedulePolicy) -> Unit,
    onCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val otherCount = (boundTableCount - 1).coerceAtLeast(0)
    val summaryTail = if (otherCount == 0) {
        stringResource(R.string.schedule_conflict_dialog_summary_only_this)
    } else {
        stringResource(R.string.schedule_conflict_dialog_summary_other_count, otherCount)
    }
    val syncSub = if (otherCount == 0) {
        stringResource(R.string.schedule_conflict_option_sync_sub_only)
    } else {
        stringResource(R.string.schedule_conflict_option_sync_sub_other, otherCount)
    }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.schedule_conflict_dialog_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.schedule_conflict_dialog_summary,
                    periodTableName,
                    summaryTail
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            ConflictOption(
                title = stringResource(R.string.schedule_conflict_option_detach_title),
                subtitle = stringResource(R.string.schedule_conflict_option_detach_sub),
                onClick = { onSelect(SchedulePolicy.DETACH_COPY) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ConflictOption(
                title = stringResource(R.string.schedule_conflict_option_create_title),
                subtitle = stringResource(R.string.schedule_conflict_option_create_sub),
                onClick = { onSelect(SchedulePolicy.CREATE_NEW) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ConflictOption(
                title = stringResource(
                    R.string.schedule_conflict_option_sync_title,
                    periodTableName
                ),
                subtitle = syncSub,
                onClick = { onSelect(SchedulePolicy.SYNC) }
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 「取消」= 撤销本次作息改动, 其他字段草稿保留
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SleepyTheme.shapes.large)
                    .background(colors.secondaryContainer)
                    .clickable(onClick = onCancel)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ConflictOption(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}