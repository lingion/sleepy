package com.lingion.sleepy.ui.screen.mine

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.ui.component.HueSlider
import com.lingion.sleepy.ui.component.SectionHeader
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.theme.CustomSchemeDeriver
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.ui.theme.ThemePreset
import com.lingion.sleepy.ui.theme.ThemePresets
import com.lingion.sleepy.data.CustomThemeStore
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.CourseColorUtil
import com.lingion.sleepy.util.CourseColorWriteGlue
import com.lingion.sleepy.util.CourseHueStore
import com.lingion.sleepy.util.GoldenAngleColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 外观页(决策 D2 合并页): 仅主题色彩组。课程显示/小组件组已迁至 GeneralSettingsScreen(2026-08-24)。
 * 保留 refreshWidgets() 管线, 主题变更后即时刷新小组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    themeMode: String = AppPrefs.THEME_MODE_SYSTEM,
    onThemeModeChange: (String) -> Unit = {},
    onOpenPeriodHeaderSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val currentKey by AppPrefs.themeKeyFlow(context).collectAsState(initial = AppPrefs.getThemeKey(context))
    val selectedMode = themeMode
    // 自定义主题编辑器 overlay(创建时 editingTheme=null;微调时载入草稿)
    var showEditor by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf<com.lingion.sleepy.data.CustomTheme?>(null) }
    // 编辑器保存/删除后刷新网格列表
    var customListVersion by remember { mutableIntStateOf(0) }

    // 选主题/模式后立即刷小组件: 之前只写 SP 不刷 widget → 小组件不跟主题变
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        widgetScope.launch { com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged(context) }
    }

    // 预览胶囊数据源: 当前默认课表前 5 个不同组(gid→课名, spec §5); 空课表时区体用占位名
    var previewCourseGroups by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching {
            val app = SleepyApp.get()
            val tid = app.repository.getDefaultTable()?.id
            val courses = if (tid != null) app.repository.getCourses(tid) else app.repository.getAllCourses()
            courses.asSequence().sortedBy { it.id }
                .filter { it.groupId.isNotBlank() }
                .distinctBy { it.groupId }
                .take(5)
                .map { it.groupId to it.courseName }
                .toList()
        }.getOrNull()?.let { previewCourseGroups = it }
    }

    // ── 课程配色三档状态 (spec §5) ──
    // mode/seed/theme/themeMode 四键 CourseHueStore.start 均已订阅自动重灌快照,
    // UI 订阅同一快照 → 试算预览/渲染叶子同帧一致。
    val courseColorMode by AppPrefs.courseColorModeFlow(context).collectAsState(initial = AppPrefs.getCourseColorMode(context))
    val courseColorSeed by AppPrefs.courseColorSeedHueFlow(context).collectAsState(initial = AppPrefs.getCourseColorSeedHue(context))
    var courseColorless by remember { mutableStateOf(AppPrefs.isCourseColorless(context)) }

    /** 全表重算挂后台 — recalcAll 内部自检 CLASSIC→0 写(零变化红线), 调用点无需判档。 */
    fun recalcAllCourses() {
        widgetScope.launch {
            val app = SleepyApp.get()
            val gids = app.repository.getAllCourses().sortedBy { it.id }.map { it.groupId }
            CourseColorWriteGlue.recalcAll(context, app.database.courseColorDao(), gids)
        }
    }

    // spec §2.3 时机2「FOLLOW_THEME 下换主题 → 重排课表色」(用户批准的 §2.5 目标行为):
    // theme_key 覆盖预设点选/自定义点选/编辑器保存/删除回落全部主题应用路径;
    // distinctUntilChanged 防初帧误触。CUSTOM 档 anchor=种子(与主题无关),不随主题重算;
    // CLASSIC 档 recalcAll 内部自检零写。深浅切换(§6 测试3: 只动 S/L)同样无需重算。
    LaunchedEffect(currentKey) {
        if (courseColorMode == AppPrefs.COURSE_COLOR_MODE_FOLLOW_THEME) recalcAllCourses()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mine_appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.dbgTag("settings_back", role = "button")) {
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
            // ── Section① 主题色彩 ──
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

            // 2 列网格 5 套预设 + 新建卡 + 自定义主题卡(混排,奇数补空位)
            item {
                val customThemes = remember(customListVersion) { CustomThemeStore.getAll(context) }
                // 网格单元序列:5 预设卡 → 各自定义卡 → 加号永远最后(2026-09-11 用户定稿:
                // 自定义卡与预设卡同列紧密堆积,加号只排在整个网格末尾)
                val cells: List<ThemeGridCell> = buildList {
                    ThemePresets.all.forEach { add(ThemeGridCell.Preset(it)) }
                    customThemes.forEach { add(ThemeGridCell.Custom(it)) }
                    add(ThemeGridCell.NewTheme)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cells.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { cell ->
                                Box(Modifier.weight(1f)) {
                                    when (cell) {
                                        is ThemeGridCell.Preset -> PresetThemeCard(
                                            preset = cell.preset,
                                            selected = currentKey == cell.preset.key,
                                            onClick = { AppPrefs.setThemeKey(context, cell.preset.key); refreshWidgets() }
                                        )
                                        is ThemeGridCell.NewTheme -> NewThemeCard(
                                            onClick = { showEditor = true; editingTheme = null }
                                        )
                                        is ThemeGridCell.Custom -> CustomThemeCard(
                                            theme = cell.theme,
                                            selected = currentKey == ThemePresets.CUSTOM_KEY_PREFIX + cell.theme.id,
                                            onClick = {
                                                AppPrefs.setThemeKey(context, ThemePresets.CUSTOM_KEY_PREFIX + cell.theme.id)
                                                refreshWidgets()
                                            },
                                            onEdit = { showEditor = true; editingTheme = cell.theme }
                                        )
                                    }
                                }
                            }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── Section② 外观深浅 ──
            item {
                SectionHeader(title = stringResource(R.string.theme_appearance))
            }

            item {
                val modes = listOf(
                    AppPrefs.THEME_MODE_SYSTEM to stringResource(R.string.theme_mode_system),
                    AppPrefs.THEME_MODE_LIGHT to stringResource(R.string.theme_mode_light),
                    AppPrefs.THEME_MODE_DARK to stringResource(R.string.theme_mode_dark)
                )
                // 轨道式分段(2026-10-20 用户裁定): 与课表页/假期页同款 SegmentedSwitcher,
                // 替换旧手搓 if(sel) 选中盒 Row — thumb 弹簧滑动+逐字擦色, 全 app 一致。
                SegmentedSwitcher(
                    options = modes,
                    selected = selectedMode,
                    onSelect = { mode ->
                        if (mode != selectedMode) {
                            AppPrefs.setThemeMode(context, mode); onThemeModeChange(mode); refreshWidgets()
                            // 深浅切换在 FOLLOW_THEME 下可能移动 anchor(系统动态取色深浅 primary
                            // 色相不同/预设深浅 primary 不同)→ 按 spec §2.3 时机2 重算保表与
                            // 新课补色同 anchor。CUSTOM 档 anchor=种子(与深浅无关),不重算。
                            if (courseColorMode == AppPrefs.COURSE_COLOR_MODE_FOLLOW_THEME) recalcAllCourses()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    dbgTagPrefix = "settings_mode_segment",
                )
            }

            // ── Section②·5 课程配色 (spec §5: 三档 + 色相滑条 + 试算预览 + 重排按钮) ──
            item {
                SectionHeader(title = stringResource(R.string.appearance_section_course_color))
            }
            item {
                CourseColorSection(
                    mode = courseColorMode,
                    seed = courseColorSeed,
                    colorless = courseColorless,
                    onColorlessSelect = {
                        courseColorless = true
                        AppPrefs.setCourseColorless(context, true)
                    },
                    onModeChange = { newMode ->
                        courseColorless = false
                        AppPrefs.setCourseColorless(context, false)
                        if (newMode != courseColorMode) {
                            // 进 CUSTOM 且种子未初始化 → 播种为当前主题 anchor(滑条起点即所见)
                            if (newMode == AppPrefs.COURSE_COLOR_MODE_CUSTOM &&
                                courseColorSeed == AppPrefs.COURSE_COLOR_SEED_UNSET
                            ) {
                                // hueOf(Color) 与 FOLLOW_THEME anchor 同一解析函数 → 滑条起点=所见主题色
                                AppPrefs.setCourseColorSeedHue(context, CourseColorUtil.hueOf(colors.primary))
                            }
                            AppPrefs.setCourseColorMode(context, newMode)
                            recalcAllCourses() // spec §2.3 时机1: 切档重排
                            refreshWidgets()
                        }
                    },
                    onSeedCommit = { hue ->
                        AppPrefs.setCourseColorSeedHue(context, hue)
                        recalcAllCourses() // spec §2.3 时机3: 种子变化重排
                        refreshWidgets()
                    },
                    onRecalc = {
                        widgetScope.launch {
                            val app = SleepyApp.get()
                            val gids = app.repository.getAllCourses().sortedBy { it.id }.map { it.groupId }
                            // spec §2.3 时机6: 手动重排 — Toast 报真实重算组数(0 写=表已一致), 回主线程弹
                            val n = CourseColorWriteGlue.recalcAll(context, app.database.courseColorDao(), gids)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.course_color_recalc_done, n),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        refreshWidgets()
                    },
                    previewGids = previewCourseGroups,
                )
            }

            // ── Section③ 课表显示：冲突样式及参数仍属于本组 ──
            item {
                SectionHeader(title = stringResource(R.string.appearance_section_schedule_display))
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SleepyTheme.shapes.large,
                    color = colors.surfaceContainer,
                ) {
                    ScheduleDisplayContent(modifier = Modifier.padding(16.dp))
                }
            }

            // ── Section④ 节次表头 ──
            item {
                SectionHeader(title = stringResource(R.string.appearance_period_header))
            }
            item {
                AppearanceNavigationRow(
                    title = stringResource(R.string.appearance_period_header),
                    tag = "settings_period_header",
                    onClick = onOpenPeriodHeaderSettings,
                )
            }
        }
    }

    // 编辑器 overlay — 全屏覆盖在外观页之上(仿其他 overlay 页的层叠样式)
    if (showEditor) {
        CustomThemeEditorScreen(
            editing = editingTheme,
            nextThemeNumber = remember(customListVersion) { CustomThemeStore.getAll(context).size + 1 },
            onBack = { showEditor = false; editingTheme = null },
            onSaved = { saved ->
                CustomThemeStore.save(context, saved)
                AppPrefs.setThemeKey(context, ThemePresets.CUSTOM_KEY_PREFIX + saved.id)
                customListVersion++
                // 保存即应用(2026-09-21 用户反馈): 无条件把 theme_key 落到本次保存的主题 —
                // themeKeyFlow 只听 sleepy_prefs 的 theme_key, 只写 custom_themes 文件
                // 不会触发 SleepyThemeProvider 重组, app 配色纹丝不动。新建主题此前更是
                // "保存了但从没被应用过"。
                refreshWidgets()
                showEditor = false
                editingTheme = null
            },
            onDeleted = { id ->
                CustomThemeStore.delete(context, id)
                customListVersion++
                // 删除的正是当前应用主题 → 写回 default(与 unknown-key 回落语义一致)
                if (currentKey == ThemePresets.CUSTOM_KEY_PREFIX + id) {
                    AppPrefs.setThemeKey(context, ThemePresets.KEY_DEFAULT)
                    refreshWidgets()
                }
                showEditor = false
                editingTheme = null
            }
        )
    }
}

/** 网格单元 — 预设卡 / 新建卡 / 自定义卡 混排 */
private sealed interface ThemeGridCell {
    data class Preset(val preset: ThemePreset) : ThemeGridCell
    data object NewTheme : ThemeGridCell
    data class Custom(val theme: com.lingion.sleepy.data.CustomTheme) : ThemeGridCell
}

// ── 以下复制自 ThemeColorScreen ──

@Composable
private fun SystemThemeCard(selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // 2026-08-25 用户指令: 全 app 纯色块禁描线 — 选中态只用色块层级+对勾表达
    val bgColor = if (selected) colors.primaryContainer else colors.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).noRippleClickable(onClick)
            .dbgTag("settings_theme", text = "system", role = "button"),
        color = bgColor, shape = SleepyTheme.shapes.large
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(SleepyTheme.shapes.large).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
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
    val colors = MaterialTheme.colorScheme
    val scheme = if (colors.background.red < 0.5f) preset.light else preset.dark
    // 2026-08-25 用户指令: 全 app 纯色块禁描线 — 选中态只用色块层级+对勾表达
    val bgColor = if (selected) colors.primaryContainer else colors.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).noRippleClickable(onClick)
            .dbgTag("settings_theme", text = stringResource(preset.nameRes), role = "button"),
        color = bgColor, shape = SleepyTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSwatch(scheme.primary)
                ColorSwatch(scheme.secondary)
                ColorSwatch(scheme.tertiary)
            }
            Spacer(Modifier.height(12.dp))
            // ✓槽位恒定 20dp 占位,图标仅在选中时渲染进槽位 —— 真机字体缩放(fontScale<1)
            // 下 titleSmall 行高会缩到 20dp 以下,条件渲染=选中那刻凭空多一个决定行高的孩子
            // =选中卡比未选中卡高(2026-09-22 用户实测)。禁改回 raw `if (selected) Icon(...)`。
            // 不用 alpha(0f) 隐藏:读屏会照常播报"已选中",占位 Box 才是布局+a11y 双正确。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(preset.nameRes), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = colors.onSurface, modifier = Modifier.weight(1f))
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = colors.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(Modifier.size(28.dp).clip(SleepyTheme.shapes.small).background(color))
}

/** 「新建主题」入口 — 裸虚线圆圈+加号,无卡片无背景无文字(2026-09-11 用户定稿) */
@Composable
private fun NewThemeCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .noRippleClickable(onClick)
            .dbgTag("settings_theme_new", role = "button"),
        contentAlignment = Alignment.Center
    ) {
        DashedCircleWithPlus(size = 40.dp, strokeColor = colors.onSurface, tint = colors.onSurface)
    }
}

/** 虚线圆圈 + 中心加号 — Canvas PathEffect.dashPathEffect 画圆环,onSurface 描边自动适配深浅 */
@Composable
private fun DashedCircleWithPlus(size: androidx.compose.ui.unit.Dp, strokeColor: Color, tint: Color) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val radius = (this.size.minDimension - stroke) / 2f
            drawCircle(
                color = strokeColor,
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        intervals = floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                    )
                )
            )
        }
        Icon(
            androidx.compose.material.icons.Icons.Outlined.Add,
            contentDescription = stringResource(R.string.theme_new),
            tint = tint,
            modifier = Modifier.size(size / 2)
        )
    }
}

/** 自定义主题卡 — 与 PresetThemeCard 完全同构(三色板+名称+选中对勾,大小一模一样)。
 *  edit 图标在色板行右端: 色块包裹(可点性可见) + 与第一行 3 色块同行对齐
 *  (2026-09-13 用户定稿: 裸图标贴主题名旁 = 不知道点哪, 24dp IconButton 嵌名称行
 *  还把卡片撑得比预设卡高) */
@Composable
private fun CustomThemeCard(
    theme: com.lingion.sleepy.data.CustomTheme,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // 卡片色板预览按当前深浅模式派生(与 PresetThemeCard 的探针逻辑一致)
    val isDark = colors.background.red < 0.5f
    val scheme = remember(theme, isDark) { CustomSchemeDeriver.derive(theme, isDark) }
    val bgColor = if (selected) colors.primaryContainer else colors.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).noRippleClickable(onClick)
            .dbgTag("settings_theme", text = theme.name, role = "button"),
        color = bgColor, shape = SleepyTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSwatch(scheme.primary)
                ColorSwatch(scheme.secondary)
                ColorSwatch(scheme.tertiary)
                Spacer(Modifier.weight(1f))
                // edit 色块: surfaceContainerHighest 与卡片底色拉开层级; 28dp 与色板同高, 首行结构与 Preset 卡完全一致不撑高
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(SleepyTheme.shapes.small)
                        .background(colors.surfaceContainerHighest)
                        .noRippleClickable(onEdit)
                        .dbgTag("settings_theme_edit", text = theme.name, role = "button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.theme_custom_edit),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    theme.name.ifBlank { stringResource(R.string.theme_new) },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 与预设卡相同:固定 20dp 槽位,避免选中态改变标题行高度。
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            stringResource(R.string.selected),
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 课程配色区：统一底色与三种配色模式四选一；自定义模式提供色相滑条。
 *
 * 预览试算走纯函数(planHuesForGroups / stableHue), 不依赖 DB 写成功 —— 与渲染兜底
 * 「表中查无 → 实时按 2.2 公式算, 与表将含值一致」同源, 预览即所得。
 * anchor 取当前 Compose primary(所见即所算, 动态取色主题也正确)。
 */
@Composable
private fun CourseColorSection(
    mode: Int,
    seed: Float,
    colorless: Boolean,
    onColorlessSelect: () -> Unit,
    onModeChange: (Int) -> Unit,
    onSeedCommit: (Float) -> Unit,
    onRecalc: () -> Unit,
    previewGids: List<Pair<String, String>>,
) {
    val colors = MaterialTheme.colorScheme
    // 与主页/课表格/小组件同一 isDark 判定（isPaletteDark(palette)）— 保证 S/L 档逐位同源
    val isDark = CourseColorUtil.isPaletteDark(SleepyTheme.palette)
    val placeholder = stringResource(R.string.format_help_example)

    // 滑条: 拖拽只动本地值, 松手才提交(onValueChangeFinished) → 一次拖动只重算一次
    var sliderHue by remember { mutableFloatStateOf(seed.coerceAtLeast(0f)) }
    LaunchedEffect(seed) { if (seed >= 0f) sliderHue = seed }

    val anchor = CourseColorUtil.resolveAnchor(mode, colors.primary.toArgb(), seed)
    // 课表有课取真实前 5 组课名; 空课表用占位串(预览仍可看色相编排)
    val items: List<Pair<String, String>> =
        previewGids.ifEmpty { (1..5).map { "preview-$it" to placeholder } }
    // 试算层: 只对「无持久记录」的组生效(占位串/导入未落库的新课)。
    val planned: Map<String, Float> = remember(anchor, items) {
        if (anchor == null) items.associate { (gid, _) -> gid to CourseColorUtil.stableHue(gid) }
        else CourseColorUtil.planHuesForGroups(anchor, emptyList(), items.map { it.first })
    }
    // 持久快照 = 主页胶囊取色的同一事实源（CourseHueStore）: 有记录的组直读,
    // 所见即主页所得。此前预览从零试算前 5 组, 与全表重算落库的持久 hue
    // 在增量避让滑位下可对同一门课给出不同色相 — 即用户所见「预览比主页淡」色差根因。
    val persisted by CourseHueStore.hueMapFlow.collectAsState()
    val hues = remember(planned, persisted) {
        planned.mapValues { (gid, hue) -> persisted[gid] ?: hue }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SleepyTheme.shapes.large,
        color = colors.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SegmentedSwitcher(
                options = listOf(
                    null to stringResource(R.string.course_color_uniform),
                    AppPrefs.COURSE_COLOR_MODE_CLASSIC to stringResource(R.string.course_color_classic),
                    AppPrefs.COURSE_COLOR_MODE_FOLLOW_THEME to stringResource(R.string.course_color_follow_theme),
                    AppPrefs.COURSE_COLOR_MODE_CUSTOM to stringResource(R.string.course_color_custom_seed),
                ),
                selected = if (colorless) null else mode,
                onSelect = { selected ->
                    if (selected == null) onColorlessSelect() else onModeChange(selected)
                },
                modifier = Modifier.fillMaxWidth(),
                containerColor = colors.surfaceContainerHighest,
                dbgTagPrefix = "course_color_segment",
            )
            if (!colorless && mode == AppPrefs.COURSE_COLOR_MODE_CUSTOM) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.course_color_seed_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${sliderHue.roundToInt()}°",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                    )
                }
                // 彩虹色相条 = ColorPickerDialog 同款共享 HueSlider(全 app 色相取色同源),
                // 替换裸 material3.Slider。拖动只动本地值, 松手/点按经 onHueCommit 落库重算。
                HueSlider(
                    hue = sliderHue,
                    onHueChange = { sliderHue = it.roundToInt().toFloat() },
                    onHueCommit = { onSeedCommit(it.roundToInt().toFloat()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(36.dp)
                        .clip(SleepyTheme.shapes.large)
                        .dbgTag("settings_color_seed", role = "slider", clickable = false),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.course_color_preview),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { (gid, name) ->
                    val hue = hues[gid] ?: return@forEach
                    val (s, l) = GoldenAngleColor.hsl(hue, isDark)
                    val bg = if (colorless) colors.secondaryContainer else CourseColorUtil.hslToColor(hue, s, l)
                    Box(
                        modifier = Modifier.clip(SleepyTheme.shapes.small)
                            .background(bg)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            name,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = CourseColorUtil.textColorOn(bg, isDark, colors.onSurface),
                        )
                    }
                }
            }
            if (!colorless) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onRecalc,
                    modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.regularHeight)
                        .dbgTag("settings_recalc_button", role = "button"),
                    shape = SleepyTheme.Buttons.shape,
                ) {
                    Text(stringResource(R.string.course_color_recalc))
                }
            }
        }
    }
}
