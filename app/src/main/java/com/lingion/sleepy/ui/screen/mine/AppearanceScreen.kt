package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.ThemePreset
import com.lingion.sleepy.ui.theme.ThemePresets
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 外观与显示页(决策 D2 合并页): 承接主题色彩 + 课程显示 + 小组件三组设置, 三组分隔线结构。
 * 由 ThemeSettingsScreen(主题色彩组) 与 MoreSettingsScreen(课程显示/小组件组) 合并而来,
 * 整页保留 refreshWidgets() 管线, 任何显示项变更后即时刷新小组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    themeMode: String = AppPrefs.THEME_MODE_SYSTEM,
    onThemeModeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = SleepyTheme.colors
    val currentKey by AppPrefs.themeKeyFlow(context).collectAsState(initial = AppPrefs.getThemeKey(context))
    var selectedMode by remember(themeMode) { mutableStateOf(themeMode) }
    // 折叠状态: 记录展开的卡片标题
    var expandedSections by remember { mutableStateOf(emptySet<String>()) }
    fun toggleSection(key: String) {
        expandedSections = if (key in expandedSections) expandedSections - key else expandedSections + key
    }

    // 课程显示 / 小组件设置项状态
    var displayMode by remember { mutableStateOf(AppPrefs.getDisplayMode(context)) }
    var showDate by remember { mutableStateOf(AppPrefs.isShowDate(context)) }
    var visibleDays by remember { mutableStateOf(AppPrefs.getVisibleDays(context)) }
    var vertPunct by remember { mutableStateOf(AppPrefs.isVertPunctReplace(context)) }
    var widgetColorless by remember { mutableStateOf(AppPrefs.isWidgetColorless(context)) }
    var widgetSeparator by remember { mutableStateOf(AppPrefs.isWidgetSeparator(context)) }

    // ★ 选主题/模式/显示项后立即刷小组件: 之前只写 SP 不刷 widget → 小组件不跟主题变
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        widgetScope.launch { com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged(context) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background, titleContentColor = colors.onBackground, navigationIconContentColor = colors.onBackground)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 分组① 主题色彩 ──
            item {
                SectionHeader(title = stringResource(R.string.appearance_section_theme))
            }

            item {
                SystemThemeCard(
                    selected = currentKey == ThemePresets.KEY_SYSTEM,
                    onClick = {
                        AppPrefs.setThemeKey(context, ThemePresets.KEY_SYSTEM)
                        refreshWidgets()
                    }
                )
            }

            // 2 列网格 5 套预设
            item {
                val presets = ThemePresets.all
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    presets.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { p ->
                                Box(Modifier.weight(1f)) {
                                    PresetThemeCard(
                                        preset = p,
                                        selected = currentKey == p.key,
                                        onClick = { AppPrefs.setThemeKey(context, p.key); refreshWidgets() }
                                    )
                                }
                            }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }

            // 外观模式: 浅色 / 深色 / 深浅色跟随系统 三态分段控件(标签与主题取色的 theme_system"跟随系统"区分)
            item {
                Text(stringResource(R.string.theme_appearance), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                val modes = listOf(
                    AppPrefs.THEME_MODE_SYSTEM to stringResource(R.string.theme_mode_system),
                    AppPrefs.THEME_MODE_LIGHT to stringResource(R.string.theme_mode_light),
                    AppPrefs.THEME_MODE_DARK to stringResource(R.string.theme_mode_dark)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainer).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    modes.forEach { (mode, label) ->
                        val sel = mode == selectedMode
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) colors.primary else colors.surfaceContainer).clickable {
                                if (mode != selectedMode) {
                                    selectedMode = mode; AppPrefs.setThemeMode(context, mode); onThemeModeChange(mode); refreshWidgets()
                                }
                            }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium), color = if (sel) colors.onPrimary else colors.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── 分隔线 ──
            item { HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f)) }

            // ── 分组② 课程显示 ──
            item {
                SectionHeader(
                    title = stringResource(R.string.appearance_section_display),
                    subtitle = stringResource(R.string.appearance_affects_both)
                )
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

            // ── 分隔线 ──
            item { HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f)) }

            // ── 分组③ 小组件 ──
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
        }
    }
}

// ── 分组标题(带副文案) ──

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    val colors = SleepyTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onBackground)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

// ── 折叠卡片(自 MoreSettingsScreen 迁移, 保持折叠列表样式) ──

@Composable
private fun SettingsCard(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
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
private fun DisplayModeOption(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
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
private fun SettingToggleRow(label: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = SleepyTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary))
    }
}

// ── 以下复制自 ThemeColorScreen ──

@Composable
private fun SystemThemeCard(selected: Boolean, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    val borderColor = if (selected) colors.primary else colors.outline.copy(alpha = 0.18f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        color = colors.surfaceContainer, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.theme_system), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.theme_system_desc), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = colors.primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PresetThemeCard(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val colors = SleepyTheme.colors
    val scheme = if (colors.background.red < 0.5f) preset.light else preset.dark
    val borderColor = if (selected) colors.primary else colors.outline.copy(alpha = 0.18f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        color = colors.surfaceContainer, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSwatch(scheme.primary)
                ColorSwatch(scheme.secondary)
                ColorSwatch(scheme.tertiary)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(preset.nameRes), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = colors.onSurface, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(color))
}
