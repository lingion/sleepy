package com.lingion.sleepy.ui.screen.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * issue#26 课程别名 — widget 编辑页「课程名显示」节。
 *
 * 全局一档: 所有小组件共享同一开关(渲染器无 widgetId, 渲染时读 AppPrefs —
 * 与 colorless/separator/vertPunct 同先例)。选中态遵循项目铁律:
 * primaryContainer 色块 + Check 图标, 无描边/无 OutlinedButton
 * (memory ui-blocks-no-border-rule)。
 */
object WidgetEditAliasSection : WidgetEditSection {
    override val titleRes: Int = R.string.widget_edit_section_alias

    @Composable
    override fun Content(scope: WidgetEditScope) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = SleepyTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                color = SleepyTheme.colors.surfaceContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    OptionRow(
                        label = stringResource(R.string.settings_name_original),
                        selected = !scope.useAlias,
                        onClick = { scope.onUseAliasChange(false) }
                    )
                    OptionRow(
                        label = stringResource(R.string.settings_name_alias),
                        selected = scope.useAlias,
                        onClick = { scope.onUseAliasChange(true) }
                    )
                }
            }
        }
    }

    /** 选中态 = primaryContainer 色块 + Check(与 WidgetEditScheduleSection.TableRow 同模式) */
    @Composable
    private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(
                    if (selected) SleepyTheme.colors.primaryContainer
                    else SleepyTheme.colors.surfaceContainer
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = SleepyTheme.colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.selected),
                    tint = SleepyTheme.colors.primary
                )
            }
        }
    }
}
