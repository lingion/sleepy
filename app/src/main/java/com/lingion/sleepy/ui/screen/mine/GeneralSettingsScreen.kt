package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.DisplayModeOption
import com.lingion.sleepy.ui.component.SectionHeader
import com.lingion.sleepy.ui.component.SettingsFlatCard
import com.lingion.sleepy.ui.component.SettingsCard
import com.lingion.sleepy.ui.component.SettingToggleRow
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 通用设置页(决策 D1 L1 ⑤): 课程显示 / 小组件 / 语言 三组。
 * 课程显示与小组件自 AppearanceScreen 迁入(2026-08-24, 用户指定), 语言卡沿用原折叠列表卡片样式。
 * 显示项变更后即时刷新小组件(refreshWidgets 管线随迁移一并保留)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit, onOpenHoliday: () -> Unit = {}) {
    val colors = SleepyTheme.colors
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppPrefs.getLanguage(context)) }

    val languages = listOf(
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "es" to "Español"
    )

    // 课程显示 / 小组件设置项状态
    var expandedSections by remember { mutableStateOf(emptySet<String>()) }
    fun toggleSection(key: String) {
        expandedSections = if (key in expandedSections) expandedSections - key else expandedSections + key
    }
    var displayMode by remember { mutableStateOf(AppPrefs.getDisplayMode(context)) }
    var gridSubInfo by remember { mutableStateOf(AppPrefs.getGridSubInfo(context)) }
    var gridScale by remember { mutableStateOf(AppPrefs.getGridScale(context)) }
    var weekScale by remember { mutableStateOf(AppPrefs.getWeekScale(context)) }
    var gridCorner by remember { mutableStateOf(AppPrefs.getGridCornerRatio(context)) }
    var weekTwoColumn by remember { mutableStateOf(AppPrefs.isWeekTwoColumn(context)) }
    var weekTwoColumnMode by remember { mutableStateOf(AppPrefs.getWeekTwoColumnMode(context)) }
    var weekHideEmptyDays by remember { mutableStateOf(AppPrefs.isWeekHideEmptyDays(context)) }
    var conflictStyle by remember { mutableStateOf(AppPrefs.getConflictStyle(context)) }
    var conflictTopInset by remember { mutableStateOf(AppPrefs.getConflictTopInset(context)) }
    var conflictFoldSize by remember { mutableStateOf(AppPrefs.getConflictFoldSize(context)) }
    var showDate by remember { mutableStateOf(AppPrefs.isShowDate(context)) }
    var startView by remember { mutableStateOf(AppPrefs.getStartView(context)) }
    var visibleDays by remember { mutableStateOf(AppPrefs.getVisibleDays(context)) }
    var vertPunct by remember { mutableStateOf(AppPrefs.isVertPunctReplace(context)) }
    var widgetColorless by remember { mutableStateOf(AppPrefs.isWidgetColorless(context)) }
    var courseColorless by remember { mutableStateOf(AppPrefs.isCourseColorless(context)) }
    var widgetSeparator by remember { mutableStateOf(AppPrefs.isWidgetSeparator(context)) }

    // 显示项变更后立即刷小组件(管线自 AppearanceScreen 迁移保留)
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        widgetScope.launch { com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged(context) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_general)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 分组① 课程显示 ──
            item {
                SectionHeader(title = stringResource(R.string.appearance_section_display))
            }

            // 课程时间显示: 节次 / 时间 — 二选一, 标题行右侧 tab 切换(用户 2026-09-03 指令)
            item {
                SettingsFlatCard(
                    title = stringResource(R.string.settings_display_mode),
                    options = listOf(
                        stringResource(R.string.settings_display_node),
                        stringResource(R.string.settings_display_time)
                    ),
                    selectedKey = if (displayMode == "node") 0 else 1,
                    onSelect = { i ->
                        val v = if (i == 0) "node" else "time"
                        displayMode = v; AppPrefs.setDisplayMode(context, v); refreshWidgets()
                    }
                )
            }

            // 网格卡片副信息: 教室 / 教师 / 无 — 三选一, 标题行右侧 tab 切换(周视图网格卡课程名下方那行；节次信息左栏已有)
            item {
                SettingsFlatCard(
                    title = stringResource(R.string.settings_grid_sub_info),
                    options = listOf(
                        stringResource(R.string.settings_grid_sub_room),
                        stringResource(R.string.settings_grid_sub_teacher),
                        stringResource(R.string.settings_grid_sub_none)
                    ),
                    selectedKey = when (gridSubInfo) {
                        "room" -> 0
                        "teacher" -> 1
                        else -> 2
                    },
                    onSelect = { i ->
                        val v = listOf("room", "teacher", "none")[i]
                        gridSubInfo = v; AppPrefs.setGridSubInfo(context, v); refreshWidgets()
                    }
                )
            }

            // 主页显示(issue#8): 网格/周视图各一个缩放 70%~130% + 圆角 0%~200%(5% 吸附) + 周视图两栏开关
            item {
                SettingsCard(title = stringResource(R.string.settings_pill), expanded = "gridScale" in expandedSections, onToggle = { toggleSection("gridScale") }) {
                    Text(text = stringResource(R.string.settings_pill_scale), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Text(text = stringResource(R.string.settings_pill_scale_sub), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${(gridScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.widthIn(min = 52.dp)
                        )
                        Slider(
                            value = gridScale,
                            onValueChange = {
                                gridScale = (it * 20).roundToInt() / 20f
                            },
                            onValueChangeFinished = {
                                AppPrefs.setGridScale(context, gridScale)
                            },
                            valueRange = 0.7f..1.3f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.primary,
                                activeTrackColor = colors.primary,
                                inactiveTrackColor = colors.surfaceVariant
                            )
                        )
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    Text(text = stringResource(R.string.settings_pill_week_scale), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Text(text = stringResource(R.string.settings_pill_week_scale_sub), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${(weekScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.widthIn(min = 52.dp)
                        )
                        Slider(
                            value = weekScale,
                            onValueChange = {
                                weekScale = (it * 20).roundToInt() / 20f
                            },
                            onValueChangeFinished = {
                                AppPrefs.setWeekScale(context, weekScale)
                            },
                            valueRange = 0.7f..1.3f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.primary,
                                activeTrackColor = colors.primary,
                                inactiveTrackColor = colors.surfaceVariant
                            )
                        )
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    Text(text = stringResource(R.string.settings_pill_corner), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Text(text = stringResource(R.string.settings_pill_corner_sub), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${(gridCorner * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.widthIn(min = 52.dp)
                        )
                        Slider(
                            value = gridCorner,
                            onValueChange = {
                                gridCorner = (it * 20).roundToInt() / 20f
                            },
                            onValueChangeFinished = {
                                AppPrefs.setGridCornerRatio(context, gridCorner)
                            },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.primary,
                                activeTrackColor = colors.primary,
                                inactiveTrackColor = colors.surfaceVariant
                            )
                        )
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    SettingToggleRow(
                        label = stringResource(R.string.settings_week_two_column),
                        subtitle = stringResource(R.string.settings_week_two_column_sub),
                        checked = weekTwoColumn,
                        onCheckedChange = { weekTwoColumn = it; AppPrefs.setWeekTwoColumn(context, it) }
                    )
                    // 分栏标准 — 两栏开启时才需要选
                    if (weekTwoColumn) {
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                        DisplayModeOption(
                            label = stringResource(R.string.settings_week_two_column_days),
                            subtitle = stringResource(R.string.settings_week_two_column_days_sub),
                            selected = weekTwoColumnMode == "days",
                            onClick = { weekTwoColumnMode = "days"; AppPrefs.setWeekTwoColumnMode(context, "days") }
                        )
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                        DisplayModeOption(
                            label = stringResource(R.string.settings_week_two_column_balance),
                            subtitle = stringResource(R.string.settings_week_two_column_balance_sub),
                            selected = weekTwoColumnMode == "balance",
                            onClick = { weekTwoColumnMode = "balance"; AppPrefs.setWeekTwoColumnMode(context, "balance") }
                        )
                    }
                    // 隐藏无课日 — 与两栏无关, 单栏/两栏都生效
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    SettingToggleRow(
                        label = stringResource(R.string.settings_week_hide_empty),
                        subtitle = stringResource(R.string.settings_week_hide_empty_sub),
                        checked = weekHideEmptyDays,
                        onCheckedChange = { weekHideEmptyDays = it; AppPrefs.setWeekHideEmptyDays(context, it) }
                    )
                    // 表头日期 — 网格视图列头 + 小组件列头共用的开关
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    SettingToggleRow(
                        label = stringResource(R.string.settings_show_date),
                        subtitle = stringResource(R.string.settings_show_date_sub),
                        checked = showDate,
                        onCheckedChange = { showDate = it; AppPrefs.setShowDate(context, it); refreshWidgets() }
                    )
                }
            }

            // 冲突课程样式: 叠层 / 折角 / 竖轨（仅 App 内网格视图, 不涉及小组件, 无需 refreshWidgets）
            item {
                SettingsCard(title = stringResource(R.string.settings_conflict_style), expanded = "conflictStyle" in expandedSections, onToggle = { toggleSection("conflictStyle") }) {
                    DisplayModeOption(
                        label = stringResource(R.string.settings_conflict_stack),
                        subtitle = stringResource(R.string.settings_conflict_stack_sub),
                        selected = conflictStyle == "stack",
                        onClick = { conflictStyle = "stack"; AppPrefs.setConflictStyle(context, "stack") }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    DisplayModeOption(
                        label = stringResource(R.string.settings_conflict_fold),
                        subtitle = stringResource(R.string.settings_conflict_fold_sub),
                        selected = conflictStyle == "fold",
                        onClick = { conflictStyle = "fold"; AppPrefs.setConflictStyle(context, "fold") }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    DisplayModeOption(
                        label = stringResource(R.string.settings_conflict_rail),
                        subtitle = stringResource(R.string.settings_conflict_rail_sub),
                        selected = conflictStyle == "rail",
                        onClick = { conflictStyle = "rail"; AppPrefs.setConflictStyle(context, "rail") }
                    )
                    // 折角幅度拖杆(v7.10.16o): 仅折角样式下显示 —— 其他样式没有折角符号
                    if (conflictStyle == "fold") {
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                        Text(text = stringResource(R.string.settings_conflict_fold_size), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                        Text(text = stringResource(R.string.settings_conflict_fold_size_sub), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "${conflictFoldSize.roundToInt()}dp",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primary,
                                modifier = Modifier.widthIn(min = 52.dp)
                            )
                            Slider(
                                value = conflictFoldSize,
                                onValueChange = { conflictFoldSize = it.roundToInt().toFloat() },
                                onValueChangeFinished = {
                                    AppPrefs.setConflictFoldSize(context, conflictFoldSize)
                                },
                                valueRange = AppPrefs.CONFLICT_FOLD_SIZE_RANGE.start..AppPrefs.CONFLICT_FOLD_SIZE_RANGE.endInclusive,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.primary,
                                    activeTrackColor = colors.primary,
                                    inactiveTrackColor = colors.surfaceVariant
                                )
                            )
                        }
                    }
                    // 顶卡收窄量滑杆(v6): 仅叠层偏移/侧边竖轨下显示(用户 2026-09-03 指令) —
                    // STACK=右/下偏移量, RAIL=右缘让宽, 同一设置值; FOLD 没有顶卡收窄(它有专属折角幅度杆)
                    if (conflictStyle == "stack" || conflictStyle == "rail") {
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                        Text(text = stringResource(R.string.settings_conflict_top_inset), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                        Text(text = stringResource(R.string.settings_conflict_top_inset_sub), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "${conflictTopInset.roundToInt()}dp",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primary,
                                modifier = Modifier.widthIn(min = 52.dp)
                            )
                            Slider(
                                value = conflictTopInset,
                                onValueChange = { conflictTopInset = it.roundToInt().toFloat() },
                                onValueChangeFinished = {
                                    AppPrefs.setConflictTopInset(context, conflictTopInset)
                                },
                                valueRange = AppPrefs.CONFLICT_TOP_INSET_RANGE.start..AppPrefs.CONFLICT_TOP_INSET_RANGE.endInclusive,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.primary,
                                    activeTrackColor = colors.primary,
                                    inactiveTrackColor = colors.surfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // 显示星期: 周一~周日多选
            item {
                SettingsCard(title = stringResource(R.string.settings_visible_days), expanded = "visibleDays" in expandedSections, onToggle = { toggleSection("visibleDays") }) {
                    Text(text = stringResource(R.string.settings_visible_days_sub), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    (1..7).forEach { day ->
                        val checked = day in visibleDays
                        Row(
                            modifier = Modifier.fillMaxWidth().noRippleClickable {
                                val n = if (checked) visibleDays - day else visibleDays + day
                                if (n.isNotEmpty()) { visibleDays = n; AppPrefs.setVisibleDays(context, n); refreshWidgets() }
                            }.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = DateUtils.localizedDay(day, context), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                            Switch(checked = checked, onCheckedChange = { on ->
                                val n = if (on) visibleDays + day else visibleDays - day
                                if (n.isNotEmpty()) { visibleDays = n; AppPrefs.setVisibleDays(context, n); refreshWidgets() }
                            }, colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary))
                        }
                        if (day != 7) HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    }
                }
            }

            // 启动默认页: full / cards — 二选一, 标题行右侧 tab 切换(仅 App 内偏好, 不涉及小组件, 无需 refreshWidgets)
            item {
                SettingsFlatCard(
                    title = stringResource(R.string.settings_start_view),
                    subtitle = stringResource(R.string.settings_start_view_sub),
                    options = listOf(
                        stringResource(R.string.settings_start_view_full),
                        stringResource(R.string.settings_start_view_cards)
                    ),
                    selectedKey = if (startView == "full") 0 else 1,
                    onSelect = { i ->
                        val v = if (i == 0) "full" else "cards"
                        startView = v; AppPrefs.setStartView(context, v)
                    }
                )
            }

            // 课程胶囊统一底色: 单开关, 平铺直露不折叠(App 侧独立, 不刷新小组件)
            item {
                SettingsFlatCard(title = stringResource(R.string.settings_course_colorless)) {
                    SettingToggleRow(
                        label = stringResource(R.string.settings_course_colorless),
                        subtitle = stringResource(R.string.settings_course_colorless_sub),
                        checked = courseColorless,
                        onCheckedChange = { courseColorless = it; AppPrefs.setCourseColorless(context, it) }
                    )
                }
            }

            // 节假日课程灰显: 点击进入二级页
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SleepyTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .noRippleClickable(onOpenHoliday)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_holiday_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_holiday_entry_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── 分隔线 ──
            item { HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline)) }

            // ── 分组② 小组件 ──
            item {
                SectionHeader(title = stringResource(R.string.appearance_section_widget))
            }

            item {
                SettingsCard(title = stringResource(R.string.settings_widget), expanded = "widget" in expandedSections, onToggle = { toggleSection("widget") }) {
                    SettingToggleRow(
                        label = stringResource(R.string.settings_widget_colorless),
                        subtitle = stringResource(R.string.settings_widget_colorless_sub),
                        checked = widgetColorless,
                        onCheckedChange = {
                            widgetColorless = it
                            AppPrefs.setWidgetColorless(context, it)
                            refreshWidgets()
                        }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    SettingToggleRow(
                        label = stringResource(R.string.settings_widget_separator),
                        subtitle = stringResource(R.string.settings_widget_separator_sub),
                        checked = widgetSeparator,
                        onCheckedChange = {
                            widgetSeparator = it
                            AppPrefs.setWidgetSeparator(context, it)
                            refreshWidgets()
                        }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    SettingToggleRow(
                        label = stringResource(R.string.settings_vert_punct),
                        subtitle = stringResource(R.string.settings_vert_punct_sub),
                        checked = vertPunct,
                        onCheckedChange = {
                            vertPunct = it
                            AppPrefs.setVertPunctReplace(context, it)
                            refreshWidgets()
                        }
                    )
                }
            }

            // ── 分隔线 ──
            item { HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline)) }

            // ── 分组③ 语言 ──
            item {
                SectionHeader(title = stringResource(R.string.settings_language))
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).background(colors.surfaceContainer).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    languages.forEach { (code, label) ->
                        val selected = language == code
                        Row(
                            modifier = Modifier.fillMaxWidth().noRippleClickable {
                                language = code
                                AppPrefs.setLanguage(context, code)
                                (context as? android.app.Activity)?.recreate()
                            }.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.primary else colors.onSurface)
                            if (selected) Icon(Icons.Outlined.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        }
                        if (code != languages.last().first) HorizontalDivider(color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline))
                    }
                }
            }
        }
    }
}
