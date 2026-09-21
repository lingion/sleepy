package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 设计 §6: widget 编辑页「滚动方式」节 — 强制滚动(实验) 本实例一档 (per-widget)。
 *
 * 出厂默认关 = FIXED 固定窗口 (全厂商可用, 课多显示「+N」)。开启走旧滚动条带,
 * 依赖系统小组件服务, 部分厂商 ROM 可能空白/卡顿 → 开启前确认弹窗如实告知风险,
 * 取消即回弹 (不写入); 关闭无需确认。WeekGrid 族隐藏本节 (§4.4 网格无滚动形态,
 * 其 S 档复用 Today 管线, 开关经 pushTodayData 默认参数仍生效)。
 */
object WidgetEditScrollSection : WidgetEditSection {
    override val titleRes: Int = R.string.widget_edit_section_scroll

    @Composable
    override fun Content(scope: WidgetEditScope) {
        // 族注入 (评审 #15): WeekGrid / WeekGridSmall 隐藏; 未知(null)按显示处理
        if (scope.receiverSimpleName?.contains("WeekGrid") == true) return
        var showConfirm by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (scope.scrollEnabled) scope.onScrollEnabledChange(false)
                            else showConfirm = true
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.widget_scroll_dialog_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.widget_edit_scroll_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = scope.scrollEnabled,
                        onCheckedChange = { on ->
                            if (on) showConfirm = true
                            else scope.onScrollEnabledChange(false)
                        }
                    )
                }
            }
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text(stringResource(R.string.widget_scroll_dialog_title)) },
                text = {
                    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.widget_scroll_dialog_body))
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                        // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
                        com.lingion.sleepy.ui.component.DialogActionButtons(
                            confirmText = stringResource(R.string.widget_scroll_dialog_confirm),
                            onConfirm = {
                                showConfirm = false
                                scope.onScrollEnabledChange(true)
                            },
                            dismissText = stringResource(R.string.widget_scroll_dialog_cancel),
                            onDismiss = { showConfirm = false }
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {},
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
