package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.lingion.sleepy.ui.component.SettingsCard
import com.lingion.sleepy.ui.component.SettingToggleRow
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通用设置页(决策 D1 L1 ⑤): 课程显示 / 小组件 / 语言 三组。
 * 课程显示与小组件自 AppearanceScreen 迁入(2026-08-24, 用户指定), 语言卡沿用原折叠列表卡片样式。
 * 显示项变更后即时刷新小组件(refreshWidgets 管线随迁移一并保留)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
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
    var showDate by remember { mutableStateOf(AppPrefs.isShowDate(context)) }
    var visibleDays by remember { mutableStateOf(AppPrefs.getVisibleDays(context)) }
    var vertPunct by remember { mutableStateOf(AppPrefs.isVertPunctReplace(context)) }
    var widgetColorless by remember { mutableStateOf(AppPrefs.isWidgetColorless(context)) }
    var courseColorless by remember { mutableStateOf(AppPrefs.isCourseColorless(context)) }
    var widgetSeparator by remember { mutableStateOf(AppPrefs.isWidgetSeparator(context)) }

    // ★ 显示项变更后立即刷小组件(管线自 AppearanceScreen 迁移保留)
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

            // 课程时间显示: 节次 / 时间
            item {
                SettingsCard(title = stringResource(R.string.settings_display_mode), expanded = "displayMode" in expandedSections, onToggle = { toggleSection("displayMode") }) {
                    DisplayModeOption(
                        label = stringResource(R.string.settings_display_node),
                        subtitle = stringResource(R.string.settings_display_node_sub),
                        selected = displayMode == "node",
                        onClick = { displayMode = "node"; AppPrefs.setDisplayMode(context, "node"); refreshWidgets() }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                    DisplayModeOption(
                        label = stringResource(R.string.settings_display_time),
                        subtitle = stringResource(R.string.settings_display_time_sub),
                        selected = displayMode == "time",
                        onClick = { displayMode = "time"; AppPrefs.setDisplayMode(context, "time"); refreshWidgets() }
                    )
                }
            }

            // 网格卡片副信息: 教室 / 教师 / 无（周视图网格卡课程名下方那行；节次信息左栏已有）
            item {
                SettingsCard(title = stringResource(R.string.settings_grid_sub_info), expanded = "gridSubInfo" in expandedSections, onToggle = { toggleSection("gridSubInfo") }) {
                    DisplayModeOption(
                        label = stringResource(R.string.settings_grid_sub_room),
                        subtitle = stringResource(R.string.settings_grid_sub_room_sub),
                        selected = gridSubInfo == "room",
                        onClick = { gridSubInfo = "room"; AppPrefs.setGridSubInfo(context, "room"); refreshWidgets() }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                    DisplayModeOption(
                        label = stringResource(R.string.settings_grid_sub_teacher),
                        subtitle = stringResource(R.string.settings_grid_sub_teacher_sub),
                        selected = gridSubInfo == "teacher",
                        onClick = { gridSubInfo = "teacher"; AppPrefs.setGridSubInfo(context, "teacher"); refreshWidgets() }
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                    DisplayModeOption(
                        label = stringResource(R.string.settings_grid_sub_none),
                        subtitle = stringResource(R.string.settings_grid_sub_none_sub),
                        selected = gridSubInfo == "none",
                        onClick = { gridSubInfo = "none"; AppPrefs.setGridSubInfo(context, "none"); refreshWidgets() }
                    )
                }
            }

            // 显示星期: 周一~周日多选
            item {
                SettingsCard(title = stringResource(R.string.settings_visible_days), expanded = "visibleDays" in expandedSections, onToggle = { toggleSection("visibleDays") }) {
                    Text(text = stringResource(R.string.settings_visible_days_sub), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    (1..7).forEach { day ->
                        val checked = day in visibleDays
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
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
                        if (day != 7) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }

            // 课表显示日期: 开关
            item {
                SettingsCard(title = stringResource(R.string.settings_show_date), expanded = "showDate" in expandedSections, onToggle = { toggleSection("showDate") }) {
                    SettingToggleRow(
                        label = stringResource(R.string.settings_show_date),
                        subtitle = stringResource(R.string.settings_show_date_sub),
                        checked = showDate,
                        onCheckedChange = { showDate = it; AppPrefs.setShowDate(context, it); refreshWidgets() }
                    )
                }
            }

            // 课程胶囊统一底色: 开关(App 侧独立, 不刷新小组件)
            item {
                SettingsCard(title = stringResource(R.string.settings_course_colorless), expanded = "courseColorless" in expandedSections, onToggle = { toggleSection("courseColorless") }) {
                    SettingToggleRow(
                        label = stringResource(R.string.settings_course_colorless),
                        subtitle = stringResource(R.string.settings_course_colorless_sub),
                        checked = courseColorless,
                        onCheckedChange = { courseColorless = it; AppPrefs.setCourseColorless(context, it) }
                    )
                }
            }

            // ── 分隔线 ──
            item { HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f)) }

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
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
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
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
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
            item { HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f)) }

            // ── 分组③ 语言 ──
            item {
                SectionHeader(title = stringResource(R.string.settings_language))
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    languages.forEach { (code, label) ->
                        val selected = language == code
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
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
                        if (code != languages.last().first) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}
