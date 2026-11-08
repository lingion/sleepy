package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding
import com.lingion.sleepy.ui.component.SettingsLayout
import com.lingion.sleepy.ui.component.SettingsSection
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.component.SingleSliderGhostMark
import com.lingion.sleepy.ui.component.singleSliderGhostVisible
import com.lingion.sleepy.util.AppPrefs
import kotlin.math.roundToInt

enum class DisplayStyleSection { ALL, WEEK, GRID, CONFLICT }

/** Existing slider controls, grouped without changing baseline or commit semantics. */
@Composable
fun ScheduleDisplayContent(
    modifier: Modifier = Modifier,
    section: DisplayStyleSection = DisplayStyleSection.ALL,
) {
    val context = LocalContext.current
    // 幽灵基线 (需求 2026-10-29 用户定义原话语义, spec §1 裁决 2): 进页时刻一次性
    // 捕获, 现值态从基线初始化。基线绝不接 setter — 这六个滑杆松手即落 AppPrefs,
    // "最近一次落库"会被拖动自身追着改; 锚进页时刻才满足"拖回原刻度点才消失、
    // 退出重进基线重算"。
    val gridScaleBaseline = remember { AppPrefs.getGridScale(context) }
    val weekScaleBaseline = remember { AppPrefs.getWeekScale(context) }
    val cornerRatioBaseline = remember { AppPrefs.getGridCornerRatio(context) }
    var gridScale by remember { mutableStateOf(gridScaleBaseline) }
    var weekScale by remember { mutableStateOf(weekScaleBaseline) }
    var cornerRatio by remember { mutableStateOf(cornerRatioBaseline) }
    var conflictStyle by remember { mutableStateOf(AppPrefs.getConflictStyle(context)) }
    val conflictStackInsetBaseline = remember { AppPrefs.getConflictStackInset(context) }
    val conflictRailInsetBaseline = remember { AppPrefs.getConflictRailInset(context) }
    val conflictFoldSizeBaseline = remember { AppPrefs.getConflictFoldSize(context) }
    var conflictStackInset by remember { mutableStateOf(conflictStackInsetBaseline) }
    var conflictRailInset by remember { mutableStateOf(conflictRailInsetBaseline) }
    var conflictFoldSize by remember { mutableStateOf(conflictFoldSizeBaseline) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (section == DisplayStyleSection.ALL || section == DisplayStyleSection.GRID) {
            DisplaySlider(
                label = stringResource(R.string.settings_pill_scale), value = gridScale,
                valueText = "${(gridScale * 100).toInt()}%", range = 0.7f..1.3f,
                initial = gridScaleBaseline, epsilon = 0.025f,
                onValueChange = { gridScale = (it * 20).toInt() / 20f },
                onFinished = { AppPrefs.setGridScale(context, gridScale) },
            )
        }
        if (section == DisplayStyleSection.ALL || section == DisplayStyleSection.WEEK) {
            DisplaySlider(
                label = stringResource(R.string.settings_pill_week_scale), value = weekScale,
                valueText = "${(weekScale * 100).toInt()}%", range = 0.7f..1.3f,
                initial = weekScaleBaseline, epsilon = 0.025f,
                onValueChange = { weekScale = (it * 20).toInt() / 20f },
                onFinished = { AppPrefs.setWeekScale(context, weekScale) },
            )
        }
        if (section == DisplayStyleSection.ALL || section == DisplayStyleSection.GRID) {
            DisplaySlider(
                label = stringResource(R.string.settings_pill_corner), value = cornerRatio,
                valueText = "${(cornerRatio * 100).toInt()}%", range = 0f..2f,
                initial = cornerRatioBaseline, epsilon = 0.025f,
                onValueChange = { cornerRatio = (it * 20).toInt() / 20f },
                onFinished = { AppPrefs.setGridCornerRatio(context, cornerRatio) },
            )
        }
        if (section == DisplayStyleSection.ALL || section == DisplayStyleSection.CONFLICT) {
            if (section == DisplayStyleSection.ALL) {
                // 冲突课程样式 — 课表显示 Section 的下级分组: 子标题降级为 labelMedium/
                // onSurfaceVariant, 与上面三个滑杆区分开, 三档分段+参数滑杆都从属于它。
                Text(
                    text = stringResource(R.string.settings_conflict_style),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    "stack" to stringResource(R.string.settings_conflict_stack),
                    "fold" to stringResource(R.string.settings_conflict_fold),
                    "rail" to stringResource(R.string.settings_conflict_rail),
                )
                SegmentedSwitcher(
                    options = options.mapIndexed { index, (_, label) -> index to label },
                    selected = options.indexOfFirst { it.first == conflictStyle }.coerceAtLeast(0),
                    onSelect = { index ->
                        conflictStyle = options[index].first
                        AppPrefs.setConflictStyle(context, conflictStyle)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            when (conflictStyle) {
                "stack" -> DisplaySlider(
                    label = stringResource(R.string.settings_conflict_stack_inset), value = conflictStackInset,
                    valueText = "${conflictStackInset.toInt()}dp",
                    range = AppPrefs.CONFLICT_TOP_INSET_RANGE.start..AppPrefs.CONFLICT_TOP_INSET_RANGE.endInclusive,
                    initial = conflictStackInsetBaseline, epsilon = 0.5f,
                    onValueChange = { conflictStackInset = it.roundToInt().toFloat() },
                    onFinished = { AppPrefs.setConflictStackInset(context, conflictStackInset) },
                )
                "rail" -> DisplaySlider(
                    label = stringResource(R.string.settings_conflict_rail_inset), value = conflictRailInset,
                    valueText = "${conflictRailInset.toInt()}dp",
                    range = AppPrefs.CONFLICT_TOP_INSET_RANGE.start..AppPrefs.CONFLICT_TOP_INSET_RANGE.endInclusive,
                    initial = conflictRailInsetBaseline, epsilon = 0.5f,
                    onValueChange = { conflictRailInset = it.roundToInt().toFloat() },
                    onFinished = { AppPrefs.setConflictRailInset(context, conflictRailInset) },
                )
                "fold" -> DisplaySlider(
                    label = stringResource(R.string.settings_conflict_fold_size), value = conflictFoldSize,
                    valueText = "${conflictFoldSize.toInt()}dp",
                    range = AppPrefs.CONFLICT_FOLD_SIZE_RANGE.start..AppPrefs.CONFLICT_FOLD_SIZE_RANGE.endInclusive,
                    initial = conflictFoldSizeBaseline, epsilon = 0.5f,
                    onValueChange = { conflictFoldSize = it.roundToInt().toFloat() },
                    onFinished = { AppPrefs.setConflictFoldSize(context, conflictFoldSize) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDisplayScreen(
    onBack: () -> Unit,
    onOpenPeriodHeaderSettings: () -> Unit = {},
) {
    val navExtra = LocalNavExtraBottomPadding.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_section_schedule_display)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // A single scroll container keeps each slider baseline alive while other groups scroll off screen.
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + navExtra),
            verticalArrangement = Arrangement.spacedBy(SettingsLayout.cardGap),
        ) {
            SettingsSection(stringResource(R.string.settings_appearance_common), first = true) {
                CommonDisplaySettings()
            }
            SettingsSection(stringResource(R.string.settings_start_view_full)) {
                WeekDisplaySettings()
            }
            SettingsSection(stringResource(R.string.settings_start_view_cards)) {
                GridDisplaySettings()
            }
            AppearanceNavigationRow(
                title = stringResource(R.string.appearance_period_header),
                tag = "settings_period_header",
                onClick = onOpenPeriodHeaderSettings,
            )
            SettingsSection(stringResource(R.string.settings_conflict_style)) {
                DisplaySettingsGroup {
                    ScheduleDisplayContent(section = DisplayStyleSection.CONFLICT)
                }
            }
            SettingsSection(stringResource(R.string.settings_appearance_holidays)) {
                HolidayDisplaySettings()
            }
        }
    }
}

/**
 * 设置类单头滑杆 (六站点共用)。幽灵接线 (需求 2026-10-29 用户定义原话语义,
 * spec §3.1 单头变体): 拖离进页基线 initial → 基线刻度位置出现一条细条提醒
 * "上一次的结果"; 拖回基线 (半档容差 epsilon 内) → 消失; 退出重进基线重算。
 * 纯装饰: 零手势零语义, testTag 仅供 Robolectric 管线定位。落库节奏一克不动 —
 * 仍只在 onValueChangeFinished 走 onFinished (幽灵路径零 setter)。
 */
@Composable
private fun DisplaySlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    initial: Float,
    epsilon: Float,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SettingsLayout.controlGap)) {
            Text(label, modifier = Modifier.weight(1f), style = SettingsLayout.itemStyle, color = MaterialTheme.colorScheme.onSurface)
            Text(valueText, style = SettingsLayout.itemStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            Slider(value = value, onValueChange = onValueChange, onValueChangeFinished = onFinished, valueRange = range)
            if (singleSliderGhostVisible(value, initial, epsilon)) {
                val baselineFrac = (initial - range.start) / (range.endInclusive - range.start)
                SingleSliderGhostMark(
                    baselineFrac = baselineFrac,
                    modifier = Modifier.testTag("display-slider-ghost"),
                )
            }
        }
    }
}
