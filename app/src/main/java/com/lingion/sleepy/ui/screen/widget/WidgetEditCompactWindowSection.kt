package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 2026-09-15 用户定稿: 三天窗口档位从管理页外卡挪进「· 小」变体的二级编辑页,
 * 每个实例各管各的。只有 compact 脸会轮换三天, 故仅
 * WeekListSmall / WeekViewSmall 两个 receiver 显示本节; 未知族隐藏
 * (档位对其它变体无效果, 显示出来只会误导)。
 */
object WidgetEditCompactWindowSection : WidgetEditSection {
    override val titleRes: Int = R.string.widget_edit_compact_title

    /** 纯函数门控 — 单测锁死, 防止族名漂移。 */
    fun appliesTo(receiverSimpleName: String?): Boolean =
        receiverSimpleName == "WeekListSmallWidgetReceiver" ||
            receiverSimpleName == "WeekViewSmallWidgetReceiver"

    @Composable
    override fun Content(scope: WidgetEditScope) {
        if (!appliesTo(scope.receiverSimpleName)) return

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
                Column {
                    OptionRow(
                        label = stringResource(R.string.widget_edit_compact_today_first),
                        selected = scope.compactTodayFirst,
                        onSelect = { scope.onCompactTodayFirstChange(true) }
                    )
                    OptionRow(
                        label = stringResource(R.string.widget_edit_compact_today_second),
                        selected = !scope.compactTodayFirst,
                        onSelect = { scope.onCompactTodayFirstChange(false) }
                    )
                }
            }
        }
    }

    @Composable
    private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
