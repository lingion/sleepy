package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * 甲案 (设计文档 §4.2) 待执行提醒 Banner — 用户登记策略后编辑页常驻。
 *
 * 不变量 (§4.2):
 *  - 点「修改」= 重新弹三选项(不清空作息草稿)
 *  - 作息草稿再次变化 → 策略作废(updateDraft 内自动失效), Banner 随 pendingPolicy==NONE 消失
 */
@Composable
fun PendingPolicyBanner(
    policy: SchedulePolicy,
    onModify: () -> Unit
) {
    if (policy == SchedulePolicy.NONE) return
    val colors = MaterialTheme.colorScheme

    val policyText = when (policy) {
        SchedulePolicy.DETACH_COPY -> stringResource(R.string.schedule_conflict_option_detach_title)
        SchedulePolicy.CREATE_NEW -> stringResource(R.string.schedule_conflict_option_create_title)
        SchedulePolicy.SYNC -> stringResource(R.string.schedule_conflict_option_sync_sub_only)
        SchedulePolicy.NONE -> return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(colors.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = colors.onSecondaryContainer,
            modifier = Modifier.height(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.schedule_conflict_banner_format, policyText),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.schedule_conflict_banner_modify),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.primary,
            modifier = Modifier
                .clip(SleepyTheme.shapes.medium)
                .clickable(onClick = onModify)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}