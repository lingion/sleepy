package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.util.AppPrefs
import kotlin.math.roundToInt

/** Reusable body used directly at the bottom of AppearanceScreen. */
@Composable
fun ScheduleDisplayContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var gridScale by remember { mutableStateOf(AppPrefs.getGridScale(context)) }
    var weekScale by remember { mutableStateOf(AppPrefs.getWeekScale(context)) }
    var cornerRatio by remember { mutableStateOf(AppPrefs.getGridCornerRatio(context)) }
    var conflictStyle by remember { mutableStateOf(AppPrefs.getConflictStyle(context)) }
    var conflictStackInset by remember { mutableStateOf(AppPrefs.getConflictStackInset(context)) }
    var conflictRailInset by remember { mutableStateOf(AppPrefs.getConflictRailInset(context)) }
    var conflictFoldSize by remember { mutableStateOf(AppPrefs.getConflictFoldSize(context)) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DisplaySlider(
            label = stringResource(R.string.settings_pill_scale), value = gridScale,
            valueText = "${(gridScale * 100).toInt()}%", range = 0.7f..1.3f,
            onValueChange = { gridScale = (it * 20).toInt() / 20f },
            onFinished = { AppPrefs.setGridScale(context, gridScale) },
        )
        DisplaySlider(
            label = stringResource(R.string.settings_pill_week_scale), value = weekScale,
            valueText = "${(weekScale * 100).toInt()}%", range = 0.7f..1.3f,
            onValueChange = { weekScale = (it * 20).toInt() / 20f },
            onFinished = { AppPrefs.setWeekScale(context, weekScale) },
        )
        DisplaySlider(
            label = stringResource(R.string.settings_pill_corner), value = cornerRatio,
            valueText = "${(cornerRatio * 100).toInt()}%", range = 0f..2f,
            onValueChange = { cornerRatio = (it * 20).toInt() / 20f },
            onFinished = { AppPrefs.setGridCornerRatio(context, cornerRatio) },
        )
        // 冲突课程样式 — 课表显示 Section 的下级分组: 子标题降级为 labelMedium/
        // onSurfaceVariant, 与上面三个滑杆区分开, 三档分段+参数滑杆都从属于它。
        Text(
            text = stringResource(R.string.settings_conflict_style),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
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
                onValueChange = { conflictStackInset = it.roundToInt().toFloat() },
                onFinished = { AppPrefs.setConflictStackInset(context, conflictStackInset) },
            )
            "rail" -> DisplaySlider(
                label = stringResource(R.string.settings_conflict_rail_inset), value = conflictRailInset,
                valueText = "${conflictRailInset.toInt()}dp",
                range = AppPrefs.CONFLICT_TOP_INSET_RANGE.start..AppPrefs.CONFLICT_TOP_INSET_RANGE.endInclusive,
                onValueChange = { conflictRailInset = it.roundToInt().toFloat() },
                onFinished = { AppPrefs.setConflictRailInset(context, conflictRailInset) },
            )
            "fold" -> DisplaySlider(
                label = stringResource(R.string.settings_conflict_fold_size), value = conflictFoldSize,
                valueText = "${conflictFoldSize.toInt()}dp",
                range = AppPrefs.CONFLICT_FOLD_SIZE_RANGE.start..AppPrefs.CONFLICT_FOLD_SIZE_RANGE.endInclusive,
                onValueChange = { conflictFoldSize = it.roundToInt().toFloat() },
                onFinished = { AppPrefs.setConflictFoldSize(context, conflictFoldSize) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDisplayScreen(onBack: () -> Unit) {
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
        Column(Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
            ScheduleDisplayContent()
        }
    }
}

@Composable
private fun DisplaySlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Column {
        Text("$label  $valueText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Slider(value = value, onValueChange = onValueChange, onValueChangeFinished = onFinished, valueRange = range)
    }
}
