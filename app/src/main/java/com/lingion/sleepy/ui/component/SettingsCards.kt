package com.lingion.sleepy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable

/**
 * [intentional custom] 官方 M3 无「分组标题+折叠卡+单选行+开关行」组合件;
 * 本文件 = Sleepy 设置页领域封装, 内部全部消费官方组件(Switch/Icon/AnimatedVisibility),
 * 颜色/形状/字体全部走 MaterialTheme 官方 token。
 *
 * 设置页公共卡片组件 — 自 AppearanceScreen 抽出(外观/通用两页共用):
 * SectionHeader 分组标题 / SettingsCard 折叠卡 / DisplayModeOption 单选项 / SettingToggleRow 开关行。
 * SettingsFlatCard: 不折叠的平铺卡 — 内容只是简单选择或单个开关的设置项专用(用户 2026-09-03 指令:
 * 双选/三选纯选择、单开关项禁折叠, 直接露出)。
 */

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SettingsLayout.descriptionGap)) {
        Text(title, style = SettingsLayout.headingStyle, color = colors.onBackground)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = SettingsLayout.descriptionStyle, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    // ui-ops 语义身份(屏前缀_控件语义), null = 不打标(既有调用点行为不变)
    dbg: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // MD3E (Task 7a): chevron 旋转走 defaultSpatial (收稳不弹, 与展开家族同拍)
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        label = "settings-arrow"
    )
    Column(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer).noRippleClickable(onToggle)
            .then(dbg?.let { Modifier.dbgTag(it, text = title, role = "button") } ?: Modifier)
            .padding(horizontal = SettingsLayout.cardInset),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = SettingsLayout.minRowHeight).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(SettingsLayout.controlGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = SettingsLayout.headingStyle, color = colors.onSurface, modifier = Modifier.weight(1f))
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
        // 展开动画 (MD3E Task 7a): 尺寸 fastSpatial + 淡入淡出走 motionScheme,
        // 工厂先行取值再进 enter/exit 槽 (T5 rowExpand 先例)
        val expandSpecs = sleepyExpandSpecs()
        AnimatedVisibility(
            visible = expanded,
            enter = expandSpecs.enter,
            exit = expandSpecs.exit,
        ) {
            Column(Modifier.padding(top = SettingsLayout.headerGap, bottom = SettingsLayout.cardInset)) {
                content()
            }
        }
    }
}

/**
 * 平铺设置卡：空间足够时标题与选项同行；窄屏或大字体时选项换行，极长选项改为单选行。
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
    // ui-ops: 非空 = 内嵌分段每段注册 "<dbg>#<index>"(带选项文本), null 不打标
    dbg: String? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large)
            .background(colors.surfaceContainer)
            .padding(horizontal = SettingsLayout.cardInset, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(SettingsLayout.headerGap),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val titleStyle = SettingsLayout.headingStyle
            val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            val labelSizes = options.map { measurer.measure(AnnotatedString(it), labelStyle).size }
            val tabWidth = with(density) {
                ((labelSizes.maxOfOrNull { it.width } ?: 0).toDp() + 32.dp) * options.size + 8.dp
            }
            val titleWidth = with(density) { measurer.measure(AnnotatedString(title), titleStyle).size.width.toDp() }
            val controlHeight = with(density) {
                // Compact visual track; the surrounding row still reserves 48dp.
                ((labelSizes.maxOfOrNull { it.height } ?: 0).toDp() + 16.dp).coerceAtLeast(36.dp)
            }
            val inline = options.isNotEmpty() && titleWidth + SettingsLayout.controlGap + tabWidth <= maxWidth
            val choices: @Composable (Modifier) -> Unit = { modifier ->
                if (tabWidth <= maxWidth) {
                    SegmentedSwitcher(
                        options = options.mapIndexed { i, label -> i to label },
                        selected = selectedKey,
                        onSelect = onSelect,
                        modifier = modifier.height(controlHeight),
                        containerColor = colors.surfaceContainerHighest,
                        dbgTagPrefix = dbg,
                    )
                } else {
                    // Long translations / large fonts must remain readable and selectable.
                    Column(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, label ->
                            DisplayModeOption(label, "", index == selectedKey, { onSelect(index) },
                                dbg = dbg?.let { "$it#$index" })
                        }
                    }
                }
            }
            if (inline) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = SettingsLayout.minRowHeight),
                    horizontalArrangement = Arrangement.spacedBy(SettingsLayout.controlGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = titleStyle, color = colors.onSurface, modifier = Modifier.weight(1f))
                    choices(Modifier.width(tabWidth))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsLayout.headerGap)) {
                    Text(title, style = titleStyle, color = colors.onSurface)
                    if (options.isNotEmpty()) choices(Modifier.fillMaxWidth())
                }
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(text = subtitle, style = SettingsLayout.descriptionStyle, color = colors.onSurfaceVariant)
        }
        content()
    }
}

@Composable
fun DisplayModeOption(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    // ui-ops 语义身份(屏前缀_控件语义), null = 不打标(既有调用点行为不变)
    dbg: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    // MD3E (Task 7b): 选中态色彩/徽标切换统一走 motionScheme — 文字色 fastEffects
    // 缓动, Check 徽标弹入弹出吃 popup mini 家族 (与 T6 徽标/幽灵同族)。
    val labelColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurface,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "displayModeOptionLabel",
    )
    Row(modifier = Modifier.fillMaxWidth().noRippleClickable(onClick)
        .then(dbg?.let { Modifier.dbgTag(it, text = label, role = "radio") } ?: Modifier)
        .heightIn(min = SettingsLayout.minRowHeight).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(SettingsLayout.controlGap), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SettingsLayout.descriptionGap)) {
            Text(text = label, style = SettingsLayout.itemStyle, color = labelColor)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, style = SettingsLayout.descriptionStyle, color = colors.onSurfaceVariant)
            }
        }
        AnimatedVisibility(
            visible = selected,
            enter = sleepyPopupEnter(),
            exit = sleepyPopupExit(),
            modifier = Modifier.testTag("display-mode-check"),
        ) {
            // 徽标只伴随 selected 态存在 (含退场插帧), tint 恒 primary — 显隐交 popup 家族。
            Icon(Icons.Outlined.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    // ui-ops 语义身份(屏前缀_控件语义), null = 不打标(既有调用点行为不变)
    dbg: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = SettingsLayout.minRowHeight).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(SettingsLayout.controlGap), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SettingsLayout.descriptionGap)) {
            Text(text = label, style = SettingsLayout.itemStyle, color = colors.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, style = SettingsLayout.descriptionStyle, color = colors.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            modifier = Modifier.then(dbg?.let { Modifier.dbgTag(it, text = label, role = "switch") } ?: Modifier))
    }
}
