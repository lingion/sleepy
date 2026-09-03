package com.lingion.sleepy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable

/**
 * 设置页公共卡片组件 — 自 AppearanceScreen 抽出(外观/通用两页共用):
 * SectionHeader 分组标题 / SettingsCard 折叠卡 / DisplayModeOption 单选项 / SettingToggleRow 开关行。
 * SettingsFlatCard: 不折叠的平铺卡 — 内容只是简单选择或单个开关的设置项专用(用户 2026-09-03 指令:
 * 双选/三选纯选择、单开关项禁折叠, 直接露出)。
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
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "settings-arrow"
    )
    Column(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer).noRippleClickable(onToggle).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface, modifier = Modifier.weight(1f))
            // 箭头随展开旋转, 与内容动画同拍
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation)
            )
        }
        // 展开动画: 高度+淡入同拍, 替代此前 if(expanded) 瞬间弹出
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                content()
            }
        }
    }
}

/**
 * 平铺设置卡(不折叠) — 单行: 标题居左, tab 切换器居右(非必要不换行, 用户 2026-09-03 指令)。
 * 用于内容为纯选项选择或单个开关的设置项; 有滑杆/多段逻辑的仍用 SettingsCard 折叠。
 * options 为空 = 单开关卡, 由 content 自行露出开关。
 */
@Composable
fun SettingsFlatCard(
    title: String,
    subtitle: String? = null,
    options: List<String> = emptyList(),
    selectedKey: Int = 0,
    onSelect: (Int) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = SleepyTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (options.isNotEmpty()) {
                SettingsTabSwitcher(options = options, selectedKey = selectedKey, onSelect = onSelect)
            }
        }
        content()
    }
}

/**
 * 设置页 tab 切换器 — 视觉规范与课表页 SegmentedSwitcher(周视图/网格切换)一致(用户 2026-09-03 指令):
 * 同为 14dp 圆角容器 + secondaryContainer 滑块; 区别仅在布局: 段宽按内容收缩(非 weight 等分),
 * 以便放进设置卡标题行右侧。滑块 = 单个 thumb spring 平移, 不是逐项变色。
 * 实现: 各段 onSizeChanged 测宽(thumb 偏移 = 前段宽度和), thumb Box 在文字层之下。
 */
@Composable
fun SettingsTabSwitcher(options: List<String>, selectedKey: Int, onSelect: (Int) -> Unit) {
    val colors = SleepyTheme.colors
    val density = LocalDensity.current
    var itemWidths by remember { mutableStateOf(IntArray(options.size)) }
    val pxToDp: (Int) -> androidx.compose.ui.unit.Dp = { with(density) { it.toDp() } }
    val thumbOffset by animateDpAsState(
        targetValue = pxToDp(itemWidths.take(selectedKey).sum()),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "settings-tab-thumb"
    )
    val thumbWidth = pxToDp(itemWidths.getOrElse(selectedKey) { 0 })

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(thumbWidth)
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.secondaryContainer)
        )
        options.forEachIndexed { index, label ->
            val selected = index == selectedKey
            Box(
                modifier = Modifier
                    .onSizeChanged { size -> if (size.width != itemWidths[index]) itemWidths = itemWidths.copyOf().also { it[index] = size.width } }
                    .noRippleClickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DisplayModeOption(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    Row(modifier = Modifier.fillMaxWidth().noRippleClickable(onClick).padding(vertical = 10.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.primary else colors.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Outlined.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null) {
    val colors = SleepyTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary))
    }
}

@Composable
fun HolidayStyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    Box(
        modifier = Modifier
            .clip(SleepyTheme.shapes.medium)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
            .noRippleClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = if (selected) colors.onPrimaryContainer else colors.onSurface
        )
    }
}
