package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
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
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.parser.SleepyNativeExporter
import com.lingion.sleepy.ui.screen.mine.ExportItem
import com.lingion.sleepy.ui.screen.mine.shareText
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.launch

/**
 * v1.0.56 T11: 作息表分享底部弹窗 — 两种格式:
 * - Sleepy 原生格式(纯 P 区块文本): 其他 Sleepy 粘贴导入 → T9 纯作息路径吃回
 * - JSON 格式: 节次时间数据通用形态(WakeUp timeList 语义), 同样可被吃回
 * 条目视觉复用 ExportScreen.ExportItem; 选完即走系统分享, 弹窗保持展开
 * (与 ShareScheduleSheet 行为一致, 由用户返回键/点外部关闭)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodTableShareSheet(
    periodTable: PeriodTableEntity,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.period_table_share_sheet_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            ExportItem(
                icon = Icons.Outlined.Star,
                title = stringResource(R.string.period_table_share_native_title),
                subtitle = stringResource(R.string.period_table_share_native_sub),
                onClick = {
                    scope.launch {
                        shareText(
                            ctx = ctx,
                            content = SleepyNativeExporter.exportPeriodTableShareText(periodTable),
                            subject = periodTable.name,
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
                icon = Icons.Outlined.Code,
                title = stringResource(R.string.period_table_share_json_title),
                subtitle = stringResource(R.string.period_table_share_json_sub),
                onClick = {
                    scope.launch {
                        shareText(
                            ctx = ctx,
                            content = SleepyNativeExporter.exportPeriodTableJson(periodTable),
                            subject = periodTable.name,
                            onResult = { }
                        )
                    }
                }
            )
        }
    }
}
