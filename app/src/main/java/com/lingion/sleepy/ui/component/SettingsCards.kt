package com.lingion.sleepy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyTheme

/**
 * 设置页公共卡片组件 — 自 AppearanceScreen 抽出(外观/通用两页共用):
 * SectionHeader 分组标题 / SettingsCard 折叠卡 / DisplayModeOption 单选项 / SettingToggleRow 开关行。
 * 样式与原 MoreSettingsScreen 折叠列表一致, 忠实迁移未改动。
 */

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    val colors = SleepyTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onBackground)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsCard(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).clickable(onClick = onToggle).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface, modifier = Modifier.weight(1f))
            Icon(imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (expanded) { Spacer(modifier = Modifier.height(4.dp)); content() }
    }
}

@Composable
fun DisplayModeOption(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.primary else colors.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Outlined.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingToggleRow(label: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = SleepyTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary))
    }
}
