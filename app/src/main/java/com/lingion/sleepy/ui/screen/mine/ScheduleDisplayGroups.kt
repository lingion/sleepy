package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.DisplayModeOption
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.component.SettingToggleRow
import com.lingion.sleepy.ui.component.SettingsCard
import com.lingion.sleepy.ui.component.SettingsFlatCard
import com.lingion.sleepy.ui.component.TimePickerField
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.widget.WidgetUpdater
import kotlinx.coroutines.launch

/** A settings group only supplies the existing surface treatment, never another navigation layer. */
@Composable
internal fun DisplaySettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = SleepyTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun CommonDisplaySettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun refreshWidgets() { scope.launch { WidgetUpdater.notifyDataChanged(context) } }
    var startView by remember { mutableStateOf(AppPrefs.getStartView(context)) }
    var showViewSwitcher by remember { mutableStateOf(AppPrefs.isShowViewSwitcher(context)) }
    var visibleDays by remember { mutableStateOf(AppPrefs.getVisibleDays(context)) }
    var daysExpanded by rememberSaveable { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(AppPrefs.isShowDate(context)) }
    var displayMode by remember { mutableStateOf(AppPrefs.getDisplayMode(context)) }
    var nearestBusyDay by remember { mutableStateOf(AppPrefs.isNearestBusyDay(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsFlatCard(
            title = stringResource(R.string.settings_appearance_default_view),
            subtitle = stringResource(R.string.settings_appearance_default_view_sub),
            options = listOf(stringResource(R.string.settings_start_view_full), stringResource(R.string.settings_start_view_cards)),
            selectedKey = if (startView == "full") 0 else 1,
            dbg = "display_start_view",
            onSelect = { index ->
                startView = if (index == 0) "full" else "cards"
                AppPrefs.setStartView(context, startView)
            },
        )
        DisplaySettingsGroup {
            SettingToggleRow(
                label = stringResource(R.string.settings_show_view_switcher),
                checked = showViewSwitcher,
                onCheckedChange = { showViewSwitcher = it; AppPrefs.setShowViewSwitcher(context, it) },
            )
        }
        SettingsCard(
            title = stringResource(R.string.settings_visible_days),
            expanded = daysExpanded,
            onToggle = { daysExpanded = !daysExpanded },
        ) {
            Text(stringResource(R.string.settings_visible_days_sub), style = MaterialTheme.typography.bodySmall)
            (1..7).forEach { day ->
                SettingToggleRow(
                    label = DateUtils.localizedDay(day, context),
                    checked = day in visibleDays,
                    onCheckedChange = { selected ->
                        val next = if (selected) visibleDays + day else visibleDays - day
                        if (next.isNotEmpty()) {
                            visibleDays = next
                            AppPrefs.setVisibleDays(context, next)
                            refreshWidgets()
                        }
                    },
                )
            }
        }
        DisplaySettingsGroup {
            SettingToggleRow(
                label = stringResource(R.string.settings_appearance_header_date),
                subtitle = stringResource(R.string.settings_appearance_header_date_sub),
                checked = showDate,
                onCheckedChange = { showDate = it; AppPrefs.setShowDate(context, it); refreshWidgets() },
            )
        }
        SettingsFlatCard(
            title = stringResource(R.string.settings_display_mode),
            subtitle = stringResource(R.string.settings_appearance_time_scope),
            options = listOf(stringResource(R.string.settings_display_node), stringResource(R.string.settings_display_time)),
            selectedKey = if (displayMode == "node") 0 else 1,
            onSelect = { index ->
                displayMode = if (index == 0) "node" else "time"
                AppPrefs.setDisplayMode(context, displayMode)
                refreshWidgets()
            },
        )
        DisplaySettingsGroup {
            SettingToggleRow(
                label = stringResource(R.string.settings_nearest_busy_day),
                subtitle = stringResource(R.string.settings_nearest_busy_day_sub),
                checked = nearestBusyDay,
                onCheckedChange = { nearestBusyDay = it; AppPrefs.setNearestBusyDay(context, it); refreshWidgets() },
            )
        }
    }
}

@Composable
internal fun WeekDisplaySettings() {
    val context = LocalContext.current
    var twoColumn by remember { mutableStateOf(AppPrefs.isWeekTwoColumn(context)) }
    var twoColumnMode by remember { mutableStateOf(AppPrefs.getWeekTwoColumnMode(context)) }
    var hideEmpty by remember { mutableStateOf(AppPrefs.isWeekHideEmptyDays(context)) }
    var useAlias by remember { mutableStateOf(AppPrefs.isWeekUseAlias(context)) }
    DisplaySettingsGroup {
        ScheduleDisplayContent(section = DisplayStyleSection.WEEK)
        SettingToggleRow(
            label = stringResource(R.string.settings_week_two_column),
            checked = twoColumn,
            onCheckedChange = { twoColumn = it; AppPrefs.setWeekTwoColumn(context, it) },
        )
        if (twoColumn) {
            DisplayModeOption(
                label = stringResource(R.string.settings_week_two_column_days), subtitle = "",
                selected = twoColumnMode == "days",
                onClick = { twoColumnMode = "days"; AppPrefs.setWeekTwoColumnMode(context, "days") },
            )
            DisplayModeOption(
                label = stringResource(R.string.settings_week_two_column_balance), subtitle = "",
                selected = twoColumnMode == "balance",
                onClick = { twoColumnMode = "balance"; AppPrefs.setWeekTwoColumnMode(context, "balance") },
            )
        }
        SettingToggleRow(
            label = stringResource(R.string.settings_week_hide_empty), checked = hideEmpty,
            onCheckedChange = { hideEmpty = it; AppPrefs.setWeekHideEmptyDays(context, it) },
        )
        SettingToggleRow(
            label = stringResource(R.string.settings_week_alias), checked = useAlias,
            onCheckedChange = { useAlias = it; AppPrefs.setWeekUseAlias(context, it) },
        )
    }
}

@Composable
internal fun GridDisplaySettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun refreshWidgets() { scope.launch { WidgetUpdater.notifyDataChanged(context) } }
    var subInfo by remember { mutableStateOf(AppPrefs.getGridSubInfo(context)) }
    var useAlias by remember { mutableStateOf(AppPrefs.isGridUseAlias(context)) }
    var adaptiveHeight by remember { mutableStateOf(AppPrefs.isGridAdaptiveHeight(context)) }
    var pinchZoom by remember { mutableStateOf(AppPrefs.isGridPinchZoom(context)) }
    var autoHideEvening by remember { mutableStateOf(AppPrefs.isGridAutoHideEmptyEvening(context)) }
    var eveningStart by remember { mutableStateOf(AppPrefs.getGridEveningStart(context)) }
    var separators by remember { mutableStateOf(AppPrefs.isGridShowSeparators(context)) }
    var longBreakSpacing by remember { mutableStateOf(AppPrefs.isGridLongBreakSpacing(context)) }
    DisplaySettingsGroup {
        ScheduleDisplayContent(section = DisplayStyleSection.GRID)
        Text(stringResource(R.string.settings_grid_sub_info), style = MaterialTheme.typography.bodyLarge)
        SegmentedSwitcher(
            options = listOf(
                "room" to stringResource(R.string.settings_grid_sub_room),
                "teacher" to stringResource(R.string.settings_grid_sub_teacher),
                "none" to stringResource(R.string.settings_grid_sub_none),
            ),
            selected = subInfo,
            onSelect = { subInfo = it; AppPrefs.setGridSubInfo(context, it); refreshWidgets() },
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SettingToggleRow(
            label = stringResource(R.string.settings_grid_alias), checked = useAlias,
            onCheckedChange = { useAlias = it; AppPrefs.setGridUseAlias(context, it) },
        )
        HorizontalDivider()
        Text(stringResource(R.string.settings_appearance_advanced_experimental), style = MaterialTheme.typography.titleSmall)
        SettingToggleRow(
            label = stringResource(R.string.settings_grid_adaptive_height),
            subtitle = stringResource(R.string.settings_grid_adaptive_height_sub),
            checked = adaptiveHeight,
            onCheckedChange = { adaptiveHeight = it; AppPrefs.setGridAdaptiveHeight(context, it) },
        )
        SettingToggleRow(
            label = stringResource(R.string.settings_grid_pinch_zoom),
            subtitle = stringResource(R.string.settings_grid_pinch_zoom_sub),
            checked = pinchZoom,
            onCheckedChange = { pinchZoom = it; AppPrefs.setGridPinchZoom(context, it) },
        )
        SettingToggleRow(
            label = stringResource(R.string.settings_grid_auto_hide_evening),
            subtitle = stringResource(R.string.settings_grid_auto_hide_evening_sub),
            checked = autoHideEvening,
            onCheckedChange = { autoHideEvening = it; AppPrefs.setGridAutoHideEmptyEvening(context, it) },
        )
        if (autoHideEvening) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_grid_evening_start), modifier = Modifier.weight(1f))
                TimePickerField(
                    value = eveningStart,
                    onValueChange = { eveningStart = it; AppPrefs.setGridEveningStart(context, it) },
                    label = "", modifier = Modifier.width(150.dp),
                )
            }
        }
        SettingToggleRow(
            label = stringResource(R.string.settings_grid_show_separators),
            subtitle = stringResource(R.string.settings_appearance_separator_scope),
            checked = separators,
            onCheckedChange = { separators = it; AppPrefs.setGridShowSeparators(context, it); refreshWidgets() },
        )
        SettingToggleRow(
            label = stringResource(R.string.settings_grid_long_break_spacing),
            subtitle = stringResource(R.string.settings_appearance_break_scope),
            checked = longBreakSpacing,
            onCheckedChange = { longBreakSpacing = it; AppPrefs.setGridLongBreakSpacing(context, it); refreshWidgets() },
        )
    }
}

@Composable
internal fun HolidayDisplaySettings() {
    val context = LocalContext.current
    var holidayGrey by remember { mutableStateOf(AppPrefs.isHolidayGreyHoliday(context)) }
    var weekendGrey by remember { mutableStateOf(AppPrefs.isHolidayGreyWeekend(context)) }
    var ignoreWorkday by remember { mutableStateOf(AppPrefs.isHolidayIgnoreWorkday(context)) }
    var style by remember { mutableStateOf(AppPrefs.getHolidayStyle(context)) }
    DisplaySettingsGroup {
        SettingToggleRow(
            label = stringResource(R.string.settings_holiday_holiday),
            subtitle = stringResource(R.string.settings_holiday_holiday_sub), checked = holidayGrey,
            onCheckedChange = { holidayGrey = it; AppPrefs.setHolidayGreyHoliday(context, it) },
        )
        SettingToggleRow(
            label = stringResource(R.string.settings_holiday_weekend),
            subtitle = stringResource(R.string.settings_holiday_weekend_sub), checked = weekendGrey,
            onCheckedChange = { weekendGrey = it; AppPrefs.setHolidayGreyWeekend(context, it) },
        )
        if (weekendGrey) {
            SettingToggleRow(
                label = stringResource(R.string.settings_appearance_workday_visible),
                subtitle = stringResource(R.string.settings_holiday_workday_sub), checked = ignoreWorkday,
                onCheckedChange = { ignoreWorkday = it; AppPrefs.setHolidayIgnoreWorkday(context, it) },
            )
        }
        Text(stringResource(R.string.settings_holiday_style), style = MaterialTheme.typography.bodyLarge)
        SegmentedSwitcher(
            options = listOf(
                "grey" to stringResource(R.string.settings_holiday_style_grey),
                "strikethrough" to stringResource(R.string.settings_holiday_style_strikethrough),
            ),
            selected = style,
            onSelect = { style = it; AppPrefs.setHolidayStyle(context, it) },
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}
