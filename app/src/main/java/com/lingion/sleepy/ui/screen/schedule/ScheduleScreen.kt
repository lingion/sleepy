package com.lingion.sleepy.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.IosShare
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.component.CardsGridView
import com.lingion.sleepy.ui.component.CourseDetailSheet
import com.lingion.sleepy.ui.component.FullWeekView
import com.lingion.sleepy.ui.component.SectionHead
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.component.ShareScheduleSheet
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayManager
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.WeekDisplayContext
import com.lingion.sleepy.util.WeekDisplayResolver
import com.lingion.sleepy.util.WeekDisplayStatus

// 非 private: MainActivity(AppRoot 会话层)需以本类型注入 viewMode —
// 会话内切视图/编辑课程 overlay 往返/切 tab 往返都不丢(启动默认仍由 AppRoot 初始化时读 AppPrefs)。
enum class ViewMode(val labelRes: Int) {
    Full(R.string.view_full),
    Cards(R.string.view_cards)
}

@Composable
fun ScheduleScreen(
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onGoImport: () -> Unit = {},
    onManualAdd: () -> Unit = {},
    onCreateTable: () -> Unit = {},
    onEditCourse: (CourseEntity) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }
    // v7.10.5 会话级置顶 override — 网格 onPickTop 与详情弹窗 radio 共用真相源。
    // radio 点击 → 这里瞬时换层(同帧) + AppPrefs 持久化(跨会话),两条通道一次写齐。
    var topOverrides by remember { mutableStateOf(mapOf<String, Long>()) }
    fun setTopOverride(key: String, courseId: Long?) {
        topOverrides = if (courseId == null) topOverrides - key else topOverrides + (key to courseId)
    }
    // v7.10.16r 轮换态(issue#10, 评审 #1): 簇键 → 轮换步数,与 topOverrides 同级持有 —
    // HorizontalPager 翻页/周切换不丢;纯会话级不落盘,离开课表页即重置。
    var rotationSteps by remember { mutableStateOf(mapOf<String, Int>()) }
    val displayMode = remember { AppPrefs.getDisplayMode(context) }
    val showDate = remember { AppPrefs.isShowDate(context) }
    val visibleDays = remember { AppPrefs.getVisibleDays(context) }
    // 双指行高缩放 (2026-09-16 用户令): 长期手势 — 初始=上次 tick 确认的持久值;
    // 捏合只改会话值, 顶栏 tick=落盘长期生效, 撤回=回到上次确认值。
    var rowHeightScale by remember(state.selectedTableId) { mutableFloatStateOf(AppPrefs.getGridRowScale(context)) }
    var savedRowScale by remember(state.selectedTableId) { mutableFloatStateOf(AppPrefs.getGridRowScale(context)) }
    val scaleUncommitted = kotlin.math.abs(rowHeightScale - savedRowScale) > 0.001f

    val hasTable = state.tables.isNotEmpty()
    val hasCourses = state.courses.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!hasTable) {
            // 真的没表：导入或建表 (不用加课 — 无表载体时加课无从谈起)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onGoImport = onGoImport,
                    onCreateTable = onCreateTable
                )
            }
        } else if (!hasCourses) {
            // 有表无课：直接打开加课弹窗（addEmptyCourse 内部若 selectedTableId 为空会自动建表）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                NoCourseState(
                    tableName = state.currentTable?.name ?: "",
                    onAddCourse = onManualAdd,
                    onImport = onGoImport
                )
            }
        } else {
            // v7.10.7 顶栏分享 → 底部弹窗(格式选择)
            var showShareSheet by remember { mutableStateOf(false) }
            // v7.10.14 顶栏 logo → 课表切换弹窗
            var showTableSwitcher by remember { mutableStateOf(false) }
            val undoScope = androidx.compose.runtime.rememberCoroutineScope()
            TopBar(
                currentWeek = state.selectedWeek,
                maxWeek = state.currentTable?.maxWeek ?: 20,
                startDate = state.currentTable?.startDate ?: "",
                displayContext = state.weekDisplayContext,
                onSwitchTable = { showTableSwitcher = true },
                onUndo = {
                    undoScope.launch {
                        if (!viewModel.undoLastChange()) {
                            android.widget.Toast.makeText(
                                context, R.string.schedule_undo_none, android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                scaleUncommitted = scaleUncommitted,
                onScaleCommit = {
                    AppPrefs.setGridRowScale(context, rowHeightScale)
                    savedRowScale = rowHeightScale
                },
                onScaleReset = { rowHeightScale = savedRowScale },
                onPrevWeek = { viewModel.changeWeek(state.selectedWeek - 1) },
                onNextWeek = { viewModel.changeWeek(state.selectedWeek + 1) },
                onJumpToActual = {
                    val start = state.currentTable?.startDate ?: return@TopBar
                    viewModel.changeWeek(DateUtils.currentWeek(start))
                },
                onSelectWeek = { week -> viewModel.changeWeek(week) },
                onAddCourse = onManualAdd,
                onShare = { showShareSheet = true }
            )

            if (showShareSheet) {
                state.currentTable?.let { table ->
                    ShareScheduleSheet(
                        table = table,
                        courses = state.courses,
                        onDismiss = { showShareSheet = false }
                    )
                }
            }

            if (showTableSwitcher) {
                TableSwitcherDialog(
                    tables = state.tables,
                    selectedTableId = state.selectedTableId,
                    onSelect = { id ->
                        viewModel.selectTable(id)
                        showTableSwitcher = false
                    },
                    onDismiss = { showTableSwitcher = false }
                )
            }

            // Segmented Switcher — 选中态由调用方注入(会话级存活), 切换经回调上抛
            SegmentedSwitcher(
                options = ViewMode.entries.map { it to stringResource(it.labelRes) },
                selected = viewMode,
                onSelect = onViewModeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 主体视图 — 左右滑动切换周次
            val pagerMaxWeek = state.currentTable?.maxWeek ?: 20
            val pagerState = rememberPagerState(
                initialPage = (state.selectedWeek - 1).coerceIn(0, (pagerMaxWeek - 1).coerceAtLeast(0)),
                pageCount = { pagerMaxWeek.coerceAtLeast(1) }
            )

            // 标记：是否正在由 ViewModel 驱动 Pager 滚动（防止双向同步打架）
            var syncingFromState by remember { mutableStateOf(false) }

            // Pager 滑动（用户手势）→ 更新 ViewModel
            // 2026-09-14: 回调必须经恢复闸 — 条件组合(overlay/tab 往返)使
            // ScheduleScreen 整页离开组合树再回来, pagerState 按离开时的 page
            // 恢复而 syncingFromState (普通 remember) 恢复帧归零 false,
            // LaunchedEffect(pagerState.currentPage) 立即以恢复的 page 回调
            // changeWeek → 与 selectedWeek effect 的 scrollToPage 双打 → 返回后
            // 多周之间反复跳变闪烁 (无需用户操作)。恢复帧两个条件都不成立:
            // !syncingFromState 刚初始化 (拦不住首回调), isScrollInProgress=false
            // (无手势) → 恢复帧写路径静默丢弃, 环断; 手势拖动时 isScrollInProgress
            // =true 放行, 行为不变。
            LaunchedEffect(pagerState.currentPage) {
                if (!syncingFromState && pagerState.isScrollInProgress) {
                    viewModel.changeWeek(pagerState.currentPage + 1)
                }
            }

            // ViewModel 变化（TopBar 箭头/下拉菜单点击 / 切表）→ 同步 Pager
            // ponytail: syncingFromState 阻止 scrollToPage 期间 currentPage 回调反向
            // 触发 changeWeek, 否侧切表 A→B 异步重置 selectedWeek 即导致 Pager 滚动↔
            // ViewModel 双打反向同步 → 多周之间闪烁反复跳变 (Realme OS 复现, ColorOS 同源)
            LaunchedEffect(state.selectedWeek) {
                val targetPage = (state.selectedWeek - 1).coerceIn(0, pagerMaxWeek - 1)
                if (pagerState.currentPage != targetPage) {
                    syncingFromState = true
                    try {
                        pagerState.scrollToPage(targetPage)
                    } finally {
                        syncingFromState = false
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                // page 是 0-based 周索引，独立于 state.currentWeek 过滤课程
                val weekCourses = state.courses.filter { it.inWeek(page + 1) }
                    .let { list ->
                        val tj = state.effectiveCurrentTable?.timeJson
                        if (tj == null) list else list.map { c -> c.normalizeNode(tj) }
                    }
                // issue#44 调休改写: 本页各天日期若命中调休映射, 该天在网格里改按映射目标星期渲染 —
                // 周日(补周四)列显示周四的课。渲染期替身, 不写库; 未映射日期原样。
                val renderCourses = run {
                    val start = state.currentTable?.startDate
                    val transfers = state.transfers
                    if (start.isNullOrBlank() || transfers.isEmpty()) weekCourses
                    else {
                        fun displayDayOf(date: java.time.LocalDate): Int =
                            com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.effectiveDayOfWeek(date, transfers)
                        val daySwap: Map<Int, Int> = (1..7).mapNotNull { d ->
                            val natural = try {
                                com.lingion.sleepy.util.DateUtils.dateOfWeek(start, page + 1, d).dayOfWeek.value
                            } catch (_: Exception) { null } ?: return@mapNotNull null
                            val display = displayDayOf(
                                com.lingion.sleepy.util.DateUtils.dateOfWeek(start, page + 1, d)
                            )
                            if (display != natural) natural to display else null
                        }.toMap()
                        if (daySwap.isEmpty()) weekCourses
                        else weekCourses.map { c ->
                            val mapped = daySwap[c.day]
                            if (mapped == null || mapped == c.day) c else c.copy(day = mapped)
                        }
                    }
                }
                // 计算本周哪些天是节假日/周末(灰显用); 传入表 ID 使命中调休映射的放假日不灰
                val greyDays by produceState<Set<Int>>(
                    emptySet(), page, state.currentTable?.startDate, state.transfers
                ) {
                    val start = state.currentTable?.startDate
                    if (start.isNullOrBlank()) {
                        value = emptySet()
                    } else {
                        val greySet = mutableSetOf<Int>()
                        for (day in 1..7) {
                            val date = DateUtils.dateOfWeek(start, page + 1, day)
                            if (HolidayManager.shouldGrey(context, date, state.effectiveCurrentTable?.id)) {
                                greySet.add(day)
                            }
                        }
                        value = greySet
                    }
                }
                when (viewMode) {
                    ViewMode.Full -> FullWeekView(
                        courses = renderCourses,
                        visibleDays = visibleDays,
                        displayMode = displayMode,
                        timeJson = state.effectiveCurrentTable?.timeJson ?: "",
                        startDate = state.currentTable?.startDate ?: "",
                        currentWeek = page + 1,
                        onCourseClick = { selectedCourse = it },
                        greyDays = greyDays
                    )
                    ViewMode.Cards -> CardsGridView(
                        courses = renderCourses,
                        allCourses = state.courses,
                        timeSlots = TimeTableUtils.timeSlotsFor(state.currentTable),
                        visibleDays = visibleDays,
                        showDate = showDate,
                        startDate = state.currentTable?.startDate ?: "",
                        currentWeek = page + 1,
                        onCourseClick = { selectedCourse = it },
                        greyDays = greyDays,
                        topOverrides = topOverrides,
                        onSetTopOverride = ::setTopOverride,
                        rotationSteps = rotationSteps,
                        onRotationStep = { key, step ->
                            rotationSteps = if (step <= 0) rotationSteps - key
                            else rotationSteps + (key to step)
                        },
                        // 用户反馈 2026-09-09: 非常规课跨节次空隙 → 渲染期合成占位节次,
                        // 比例定位与聚簇都基于扩展后的槽位表(真实分钟语义)
                        timeJson = state.effectiveCurrentTable?.timeJson,
                        rowHeightScale = rowHeightScale,
                        onRowHeightScaleChange = { rowHeightScale = it },
                        // v1.0.56 T3: 实验室开关 — 默认关=手势不挂(顶栏 tick 按钮也随 scaleUncommitted 恒 false 不亮)
                        pinchZoomEnabled = AppPrefs.isGridPinchZoom(context)
                    )
                }
            }
        }

        // 详情 Bottom Sheet
        // v7.10.16q: allCourses 必须与网格同周域(state.selectedWeek 过滤) —
        // 此前传全周课程, ICS 往返/换教师拆出的周次不相交同行(如周四 8-10 的
        // 周1-4 与 周6-13 两行)被当成同时存在 → 幽灵图层 → 误弹"选择默认置顶"。
        // 网格一直传的是 inWeek 过滤后的 weekCourses, 弹窗对齐同一语义。
        CourseDetailSheet(
            course = selectedCourse,
            timeString = selectedCourse?.let { it.nodeString(LocalContext.current) },
            allCourses = state.courses.filter { it.inWeek(state.selectedWeek) },
            // 用户报障 2026-09-10: 详情页聚簇与网格同一时间域 — ownTime 课
            // 按真实分钟判重叠, 节点占位值不再制造假冲突。
            timeJson = state.effectiveCurrentTable?.timeJson,
            onDismiss = { selectedCourse = null },
            onEdit = { course ->
                selectedCourse = null
                onEditCourse(course)
            },
            onDefaultTopChanged = { clusterKey, repId ->
                // 勾选瞬间: 会话级换层(网格同帧刷新) + 持久化(跨会话默认)。
                // v7.10.16r(评审 #4): 同簇轮换态一并清除 — 用户显式选默认置顶,
                // 临时轮换让位,否则该簇轮换步数仍遮蔽 radio 的新决定。
                rotationSteps = rotationSteps - clusterKey
                setTopOverride(clusterKey, repId)
                AppPrefs.putConflictDefaultTop(context, clusterKey, repId)
            }
        )
    }
}

/**
 * v7.10.14 顶栏 logo 点击弹出的课表切换弹窗 —
 * 列出全部课表, 当前行 primaryContainer 高亮 + 对勾, 点击即切换。
 */
@Composable
private fun TableSwitcherDialog(
    tables: List<TimeTableEntity>,
    selectedTableId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(R.string.schedule_switch_table)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tables.forEach { table ->
                    val isCurrent = table.id == selectedTableId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.small)
                            .background(if (isCurrent) colors.primaryContainer else colors.surfaceContainer)
                            .noRippleClickable { onSelect(table.id) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = table.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (isCurrent) colors.onPrimaryContainer else colors.onSurface,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun TopBar(
    currentWeek: Int,
    maxWeek: Int,
    startDate: String,
    displayContext: WeekDisplayContext?,
    onSwitchTable: () -> Unit,
    onUndo: () -> Unit,
    scaleUncommitted: Boolean,
    onScaleCommit: () -> Unit,
    onScaleReset: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToActual: () -> Unit,
    onSelectWeek: (Int) -> Unit,
    onAddCourse: () -> Unit,
    onShare: () -> Unit
) {
    // [intentional custom] 官方 TopAppBar 槽位只有导航/标题/动作, 无「居中翻周器+周选择
    // 菜单+撤回/确认并排」布局; 此工作栏 = Sleepy 课表领域形态, 保留薄层。
    val colors = MaterialTheme.colorScheme
    // 实时计算当前实际周（不依赖 state.currentWeek — 用户可能切到了别的周）
    val actualWeek = displayContext?.actualWeek ?: remember(startDate) {
        if (startDate.isBlank()) 1 else DateUtils.currentWeek(startDate)
    }
    var menuOpen by remember { mutableStateOf(false) }
    val isOnActual = currentWeek == actualWeek
    val semesterStatus = displayContext?.semesterStatus
        ?: DateUtils.semesterStatus(startDate, maxWeek)
    val displayStatus = displayContext?.let {
        WeekDisplayResolver.statusForSelectedWeek(it, currentWeek)
    } ?: WeekDisplayStatus.NORMAL

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // v7.10.12: 三件套改 Box 叠加实现屏幕正中 —
        // 旧 weight(1f)+Center 是在"扣除右侧按钮后的剩余空间"里居中, 视觉偏左;
        // Box 叠加让三件套对齐全宽正中, 加课/分享绝对定位右缘(用户 2026-09-02)。
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // v7.10.14: 最左 logo — 点击弹课表切换弹窗, 右缘操作区(加课/分享)对称位
            // v7.10.16: logo 右边第二个按钮 = 撤回 — 仅有可撤回快照时才显示(用户 2026-09-03)
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeekNavButton(
                    icon = Icons.Outlined.CalendarMonth,
                    contentDescriptionRes = R.string.schedule_switch_table,
                    onClick = onSwitchTable
                )
                val showScaleUndo = com.lingion.sleepy.data.undo.UndoManager.hasSnapshot || scaleUncommitted
                if (showScaleUndo) {
                    Spacer(modifier = Modifier.width(6.dp))
                    WeekNavButton(
                        icon = Icons.AutoMirrored.Outlined.Undo,
                        contentDescriptionRes = R.string.schedule_undo,
                        onClick = { if (scaleUncommitted) onScaleReset() else onUndo() }
                    )
                }
                // 2026-09-16 用户令: 捏合未确认时 tick 与撤回并排同尺寸; tick=落盘长期生效(两标同灭), 撤回=回到上次确认值
                if (scaleUncommitted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    WeekNavButton(
                        icon = Icons.Outlined.Check,
                        contentDescriptionRes = R.string.schedule_scale_keep,
                        onClick = onScaleCommit
                    )
                }
            }
            // 翻页三件套(箭头+胶囊+箭头) — 箭头紧贴胶囊
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
            WeekNavButton(icon = Icons.Outlined.ChevronLeft, onClick = onPrevWeek)

            Spacer(modifier = Modifier.width(8.dp))

            // 第 N 周 标签 — 点击行为根据是否在当前实际周而不同
            // 学期外: 标签带上周数(学期未开始 · 第 3 周), 翻周时数字跟着变, 用户才知道自己看到第几周
            Box {
                val statusRes = when (semesterStatus) {
                    DateUtils.SemesterStatus.BEFORE_START -> R.string.semester_not_started
                    DateUtils.SemesterStatus.AFTER_END -> R.string.semester_ended
                    else -> 0
                }
                Text(
                    text = when (displayStatus) {
                        WeekDisplayStatus.NEAREST_BUSY_DAY -> stringResource(R.string.schedule_nearest_busy_day)
                        WeekDisplayStatus.NORMAL -> if (statusRes == 0)
                            stringResource(R.string.schedule_current_week, currentWeek)
                        else "${stringResource(statusRes)} · ${stringResource(R.string.schedule_week_prefix, currentWeek)}"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isOnActual) colors.onPrimaryContainer else colors.primary,
                    modifier = Modifier
                        .clip(SleepyTheme.shapes.medium)
                        .background(if (isOnActual) colors.primaryContainer else colors.primaryContainer.copy(alpha = SleepyTheme.Alpha.inactive))
                        .noRippleClickable {
                            if (isOnActual) {
                                // 在当前实际周 → 弹下拉菜单
                                menuOpen = true
                            } else {
                                // 不在当前实际周 → 一键跳回
                                onJumpToActual()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                )

                // Material3 DropdownMenu — FlowRow 标签式选周
                @OptIn(ExperimentalLayoutApi::class)
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.width(280.dp),
                    // 菜单浮在 surfaceContainer 背景上, 用 Highest 拉开对比(默认 High 与背景几乎同色=隐形)
                    containerColor = colors.surfaceContainerHighest
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.schedule_jump_week),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            (1..maxWeek).forEach { w ->
                                val isCurrent = w == currentWeek
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCurrent) colors.primary
                                            else colors.surfaceContainerHigh
                                        )
                                        .noRippleClickable {
                                            onSelectWeek(w)
                                            menuOpen = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = w.toString(),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isCurrent) colors.onPrimary else colors.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            WeekNavButton(icon = Icons.Outlined.ChevronRight, onClick = onNextWeek)
        }

            // 右侧操作区: 加课 + 分享 — 与翻页箭头同款圆形底, Box 右缘绝对定位
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeekNavButton(
                    icon = Icons.Outlined.Add,
                    contentDescriptionRes = R.string.schedule_add_course,
                    onClick = onAddCourse
                )
                Spacer(modifier = Modifier.width(6.dp))
                WeekNavButton(
                    icon = Icons.Outlined.IosShare,
                    contentDescriptionRes = R.string.schedule_share_table,
                    onClick = onShare
                )
            }
        }
    }
}

@Composable
private fun WeekNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescriptionRes: Int? = null
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .noRippleClickable(onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescriptionRes?.let { stringResource(it) },
            tint = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun NoCourseState(
    tableName: String,
    onAddCourse: () -> Unit,
    onImport: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.extraLarge)
            .background(colors.surfaceContainer)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.schedule_empty_name, tableName),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
        Text(
            text = stringResource(R.string.schedule_empty_name_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Button(
            onClick = onAddCourse,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
            shape = SleepyTheme.Buttons.shape,
        ) {
            Text(stringResource(R.string.schedule_manual_first))
        }
        // [intentional custom] FilledTonalButton + ctaHeight: 官方变体自带动效/形状,
        // 仅保留 Sleepy 的 56dp CTA 高度档位(官方无此 token)。
        FilledTonalButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
            shape = SleepyTheme.Buttons.shape,
        ) {
            Text(stringResource(R.string.schedule_go_manage))
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onGoImport: () -> Unit = {},
    onCreateTable: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .padding(horizontal = 22.dp)
            .clip(SleepyTheme.shapes.extraLarge)
            .background(colors.surfaceContainer)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.schedule_empty),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface
        )
        Text(
            text = stringResource(R.string.schedule_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        // 主按钮 = 导入第一张课表 (用户反馈: "前往课表管理"引导性不足)
        Button(
            onClick = onGoImport,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
            shape = SleepyTheme.Buttons.shape,
        ) {
            Text(stringResource(R.string.schedule_empty_import))
        }
        // 副按钮 = 手动创建第一张课表 (建表流, 非加课 — 无表载体时"创建第一门课"无从谈起)
        // [intentional custom] FilledTonalButton + ctaHeight: 同上, 仅保留 56dp CTA 档位。
        FilledTonalButton(
            onClick = onCreateTable,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
            shape = SleepyTheme.Buttons.shape,
        ) {
            Text(stringResource(R.string.schedule_empty_create_table))
        }
    }
}
