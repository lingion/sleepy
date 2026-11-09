package com.lingion.sleepy.ui.screen.schedule

import com.lingion.sleepy.ui.theme.noRippleGlassClickable
import com.lingion.sleepy.ui.component.liquidglass.glassControl
import com.lingion.sleepy.ui.component.liquidglass.glassSurfaceBackground
import com.lingion.sleepy.ui.component.liquidglass.controlFill
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import com.lingion.sleepy.ui.component.liquidglass.liquidGlassControlsEnabled
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.IosShare
import kotlinx.coroutines.launch
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog as AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import com.lingion.sleepy.ui.component.liquidglass.GlassDropdownMenu as DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.lingion.sleepy.ui.component.liquidglass.GlassButton as Button
import com.lingion.sleepy.ui.component.liquidglass.GlassTonalButton as FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.lingion.sleepy.console.ui.dbgTag
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.component.CardsGridView
import com.lingion.sleepy.ui.component.CourseFloatState
import com.lingion.sleepy.ui.component.dismissFloatOnOutsidePress
import com.lingion.sleepy.ui.component.dismissQuickAddOnOutsidePress
import com.lingion.sleepy.ui.component.CourseDetailSheet
import com.lingion.sleepy.ui.component.FullWeekView
import com.lingion.sleepy.ui.component.SectionHead
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.component.sleepyPopupEnter
import com.lingion.sleepy.ui.component.sleepyPopupExit
import com.lingion.sleepy.ui.component.ShareScheduleSheet
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.HolidayManager
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.WeekDisplayContext

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
    onAddAt: (Long, Int, Int, Int, Int) -> Unit = { _, _, _, _, _ -> },
    onCreateTable: () -> Unit = {},
    onEditCourse: (CourseEntity) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedOccurrence by remember { mutableStateOf<com.lingion.sleepy.util.CourseOccurrence?>(null) }
    // 浮窗「调整本次」入口置 true → 面板直接展开编辑表单; 长按通道置 false 保留两级菜单。
    var occurrenceEditDirect by remember { mutableStateOf(false) }
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
    // 用户令 2026-09-23: 周视图/网格视图切换栏可隐藏 — 默认显示保留现有行为;
    // 只影响这根 Bar 的去留, 不改 viewMode 本身与启动默认页。
    var showViewSwitcher by remember { mutableStateOf(AppPrefs.isShowViewSwitcher(context)) }
    var holidayPrefVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        AppPrefs.changeBus.collect { key ->
            if (key == AppPrefs.KEY_SHOW_VIEW_SWITCHER) {
                showViewSwitcher = AppPrefs.isShowViewSwitcher(context)
            }
            if (key == AppPrefs.KEY_HOLIDAY_GREY_HOLIDAY ||
                key == AppPrefs.KEY_HOLIDAY_GREY_WEEKEND ||
                key == AppPrefs.KEY_HOLIDAY_IGNORE_WORKDAY ||
                key == AppPrefs.KEY_HOLIDAY_OVERRIDES
            ) {
                holidayPrefVersion++
            }
        }
    }
    // 双指行高缩放 (2026-09-16 用户令): 长期手势 — 初始=上次 tick 确认的持久值;
    // 捏合只改会话值, 顶栏 tick=落盘长期生效, 撤回=回到上次确认值。
    var rowHeightScale by remember(state.selectedTableId) { mutableFloatStateOf(AppPrefs.getGridRowScale(context)) }
    var savedRowScale by remember(state.selectedTableId) { mutableFloatStateOf(AppPrefs.getGridRowScale(context)) }
    val scaleUncommitted = kotlin.math.abs(rowHeightScale - savedRowScale) > 0.001f

    val quickAddState = remember(state.selectedTableId, state.selectedWeek) { com.lingion.sleepy.ui.component.QuickAddCourseState() }
    LaunchedEffect(viewMode) { quickAddState.dismiss() }
    // 浮起卡态与 quickAddState 同级上提 — HorizontalPager 翻页重组不丢浮动态;
    // 边缘翻页特性 (2026-10-27): remember 键不再含 selectedWeek — 拖拽中途自动翻周
    // 会引起 changeWeek → 重建浮态腰斩手势。切表仍重建; 拖拽外切周由下方 effect 收层,
    // LaunchedEffect(viewMode) 只管视图切换。
    val floatState = remember(state.selectedTableId) { com.lingion.sleepy.ui.component.CourseFloatState() }
    LaunchedEffect(viewMode) { floatState.dismiss() }
    LaunchedEffect(state.selectedWeek, floatState.dragging) {
        // 用户手动换周(非拖拽自动翻) → 浮层不属于当前周, 收层; 自动翻周已 bump week, 不触发。
        if (!floatState.dragging && floatState.active && floatState.week != state.selectedWeek) {
            floatState.dismiss()
        }
    }
    // 浮框/编辑弹窗的审批问题宿主(与 OccurrenceEditor 同一 OccurrenceSaveDialogs 形态)。
    val floatDialogs = remember(context) { com.lingion.sleepy.ui.screen.edit.OccurrenceSaveDialogs(context) }
    com.lingion.sleepy.ui.screen.edit.OccurrenceSaveDialogHost(floatDialogs)
    val floatScope = androidx.compose.runtime.rememberCoroutineScope()
    var floatBusy by remember { mutableStateOf(false) }
    // 浮层写路径共用一把屏内闸: 弹窗审批挂起期间禁止叠第二个移动/删除(与 OccurrenceEditor
    // 的 busy 同语义)。快照/预期表在协程内现读, STALE/NOT_FOUND 只提示不落库。
    // [onResult] 回给网格/浮层: true=SAVED 可收层, false=否决/过期(弹回或复原拉伸)。
    fun runFloatOccurrence(
        work: suspend (FloatOccurrenceHandler) -> FloatOccurrenceHandler.Outcome,
        onResult: (Boolean) -> Unit = {},
    ) {
        if (floatBusy) return
        val table = state.effectiveCurrentTable ?: return
        floatBusy = true
        floatScope.launch {
            try {
                val handler = FloatOccurrenceHandler(
                    context, com.lingion.sleepy.SleepyApp.get().repository, floatDialogs, table,
                )
                when (val outcome = work(handler)) {
                    FloatOccurrenceHandler.Outcome.STALE, FloatOccurrenceHandler.Outcome.NOT_FOUND -> {
                        android.widget.Toast.makeText(context, R.string.occurrence_stale, android.widget.Toast.LENGTH_SHORT).show()
                        onResult(false)
                    }
                    FloatOccurrenceHandler.Outcome.SAVED -> onResult(true)
                    FloatOccurrenceHandler.Outcome.CANCELED -> onResult(false)
                }
            } finally { floatBusy = false }
        }
    }
    val hasTable = state.tables.isNotEmpty()
    val hasCourses = state.courses.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxSize().dismissQuickAddOnOutsidePress(quickAddState)
            // D 契约: 屏根空档按下也要先走「是否调整持续节次」确认 — 被拖的一次课
            // 住在起拖页 (floatState.originWeek, 浮起时快照), 时间域取它而不是翻页后的
            // floatState.week; 无 pending 时 confirmPendingResizeBeforeDismiss 自会直接收层。
            .dismissFloatOnOutsidePress(floatState) {
                floatState.confirmPendingResizeBeforeDismiss { lifted, day, node, step, onResult ->
                    runFloatOccurrence(
                        {
                            it.move(
                                lifted, floatState.week, day, node, step, FloatEditIntent.SPAN_RESIZE,
                                sourceWeek = floatState.originWeek,
                            )
                        },
                        onResult,
                    )
                }
            }
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
                maxWeek = state.displayMaxWeek,
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
                onRedo = {
                    undoScope.launch {
                        if (!viewModel.redoLastUndo()) {
                            android.widget.Toast.makeText(
                                context, R.string.schedule_redo_none, android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                scaleUncommitted = scaleUncommitted,
                onScaleCommit = {
                    AppPrefs.setGridRowScale(context, rowHeightScale)
                    savedRowScale = rowHeightScale
                },
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

            // Segmented Switcher — 选中态由调用方注入(会话级存活), 切换经回调上抛。
            // 隐藏时只释放这根 Bar 的空间, Pager 与当前 viewMode 不变。
            if (showViewSwitcher) {
                SegmentedSwitcher(
                    options = ViewMode.entries.map { it to stringResource(it.labelRes) },
                    selected = viewMode,
                    onSelect = onViewModeChange,
                    dbgTagPrefix = "schedule_view_segment",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 主体视图 — 左右滑动切换周次
            val pagerMaxWeek = state.displayMaxWeek
            val pagerState = rememberPagerState(
                initialPage = (state.selectedWeek - 1).coerceIn(0, (pagerMaxWeek - 1).coerceAtLeast(0)),
                pageCount = { pagerMaxWeek.coerceAtLeast(1) }
            )

            // Pager 只显示 ViewModel 的周次。恢复的 saveable page 在校准完成前
            // 不得反向写回 ViewModel, 否则页面恢复与 Room 初始发射会形成反馈环。
            var syncingFromState by remember { mutableStateOf(false) }
            var pagerReady by remember { mutableStateOf(false) }

            // 边缘翻页落位: 每次驻留满换页, week 即逻辑真相 (网格 bump),
            // pager 静止后回写 ViewModel — VM 与 week 双通道一致, 拖拽期间不回写(防腰斩)。
            // changeWeek 半句 2026-10-28 真机补: 程序化 animateScrollToPage 被下方
            // currentPage effect 的手势守卫(isScrollInProgress)有意拦截(09-13 防闪烁环),
            // 漏写则顶栏 chip 停在起拖周 — 网格第 3 周、chip 第 2 周、左箭头跳回第 1 周。
            // chipPending 半句同日二补: 原守卫「active && !dragging」在合法落点路上
            // 从未打开 — 松手即 dismiss(), active 与 dragging 同帧落 false, effect 再也
            // 等不到「浮着但不拖」的瞬间, chip 仍停起拖周。翻过页置位, 这里在
            // 「非拖拽 且 (浮着 或 待回写)」时统一消费(弹回路/审批弹窗路同样命中)。
            LaunchedEffect(
                pagerState.settledPage,
                floatState.active,
                floatState.dragging,
                floatState.chipPending,
            ) {
                if (!floatState.dragging && (floatState.active || floatState.chipPending)) {
                    floatState.week = pagerState.settledPage + 1
                    viewModel.changeWeek(pagerState.settledPage + 1)
                    floatState.chipPending = false
                }
            }

            // 先用业务状态校准恢复的 pager, 再开放用户手势回写。
            // Pager 滑动（用户手势）→ 更新 ViewModel。
            // 恢复帧和程序化滚动都必须被 pagerReady/syncingFromState 双重拦截。
            LaunchedEffect(pagerState.currentPage) {
                if (pagerReady && !syncingFromState && pagerState.isScrollInProgress) {
                    viewModel.changeWeek(pagerState.currentPage + 1)
                }
            }

            // ViewModel 变化（TopBar 箭头/下拉菜单点击 / 切表）→ 同步 Pager。
            // MD3E (Task 6): 程序化跳页一律缓动 — scrollToPage → animateScrollToPage
            // (计划裁决无瞬时例外); syncingFromState 守卫与 try/finally 结构不变,
            // 由两份 race 契约测试锁新字面量 (lockstep 改写 2026-10-29)。
            LaunchedEffect(state.selectedWeek) {
                val targetPage = (state.selectedWeek - 1).coerceIn(0, pagerMaxWeek - 1)
                if (pagerState.currentPage != targetPage) {
                    syncingFromState = true
                    try {
                        pagerState.animateScrollToPage(targetPage)
                    } finally {
                        syncingFromState = false
                    }
                }
                pagerReady = true
            }

            ScheduleWeekPager(
                state = pagerState,
                draggingCourse = floatState.dragging,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (viewMode == ViewMode.Cards &&
                    !com.lingion.sleepy.ui.component.shouldComposeFloatDragPage(page + 1, pagerState.currentPage + 1, floatState)) {
                    return@ScheduleWeekPager
                }
                // 拖拽坐标、缩放和弹窗变化不改变课程投影，只有数据/周次/调休/作息变化才重算。
                val renderCourses = viewModel.presentationCache.weekCourses(state, page + 1)
                // 计算本周哪些天是节假日/周末(灰显用); 纯内存快速计算 + 缓存, 避免首帧异步跳变导致转场中途重组
                val greyDays = remember(
                    page, state.selectedTableId, state.currentTable?.startDate, state.transfers, holidayPrefVersion
                ) {
                    viewModel.presentationCache.weekGreyDays(context, state, page + 1)
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
                        onCourseLongClick = { projected ->
                            occurrenceEditDirect = false
                            val table = state.currentTable
                            val original = state.courses.firstOrNull { it.id == projected.id }
                            if (table != null && original != null) {
                                selectedOccurrence = com.lingion.sleepy.util.CourseOccurrences.resolve(
                                    original, DateUtils.dateOfWeek(table.startDate, page + 1, projected.day), state.transfers
                                )
                            }
                        },
                        greyDays = greyDays,
                        showDate = showDate
                    )
                    ViewMode.Cards -> CardsGridView(
                        courses = renderCourses,
                        allCourses = state.courses,
                        onQuickAdd = { day, node, step -> state.currentTable?.let { onAddAt(it.id, page + 1, day, node, step) } },
                        quickAddState = quickAddState,
                        quickAddEnabled = page == pagerState.currentPage && !pagerState.isScrollInProgress,
                        timeSlots = TimeTableUtils.timeSlotsFor(state.effectiveCurrentTable),
                        visibleDays = visibleDays,
                        showDate = showDate,
                        startDate = state.currentTable?.startDate ?: "",
                        currentWeek = page + 1,
                        onCourseClick = { selectedCourse = it },
                        onCourseLongClick = { projected ->
                            occurrenceEditDirect = false
                            val table = state.currentTable
                            val original = state.courses.firstOrNull { it.id == projected.id }
                            if (table != null && original != null) {
                                selectedOccurrence = com.lingion.sleepy.util.CourseOccurrences.resolve(
                                    original, DateUtils.dateOfWeek(table.startDate, page + 1, projected.day), state.transfers
                                )
                            }
                        },
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
                        pinchZoomEnabled = AppPrefs.isGridPinchZoom(context),
                        // 长按浮起换格: 移动/删除走 FloatOccurrenceHandler 单格落库管线
                        // (与 OccurrenceEditor.save 同口径); 落点周 = floatState.week(时间域真值 —
                        // 起拖快照, 边缘翻页时由网格 bump; page+1 是闭包旧页, 翻页后不可信)。
                        // resizeOnly=true = 把手拉伸后经网格空档确认 → 「调整持续节次」弹窗分型。
                        floatState = floatState,
                        onEdgeDrag = { dir ->
                            // 驻留满 → 平滑翻页(300ms, PagerState 默认 spring 时长不符 →
                            // tween 按用户裁决 300ms 与预测返回规格)。目标页与网格 bump 的 week 一致。
                            floatScope.launch {
                                pagerState.animateScrollToPage(
                                    (floatState.week - 1).coerceIn(0, pagerMaxWeek - 1),
                                    animationSpec = tween(
                                        durationMillis = com.lingion.sleepy.ui.component.FLOAT_PAGE_ANIM_MS.toInt(),
                                        easing = com.lingion.sleepy.ui.component.FLOAT_PAGE_EASING,
                                    ),
                                )
                            }
                        },
                        floatMaxWeek = pagerMaxWeek,
                        onOccurrenceMove = { projected, day, node, step, resizeOnly, onResult ->
                            runFloatOccurrence(
                                {
                                    // 跨周拖拽: 被拖的一次课在 originWeek 页上(浮起快照),
                                    // week = 松手页 — 从起拖周拆出、落到松手周的目标格。
                                    // 同周时两值相等, 行为不变。
                                    it.move(
                                        projected, floatState.week, day, node, step,
                                        if (resizeOnly) FloatEditIntent.SPAN_RESIZE else FloatEditIntent.MOVE,
                                        sourceWeek = floatState.originWeek,
                                    )
                                },
                                onResult,
                            )
                        },
                        // 浮框只在「仍浮起未落库」时可见 — 所操作的就是起拖那一格
                        // (originWeek), 不是翻页后的宿主页。
                        onOccurrenceDelete = { projected ->
                            runFloatOccurrence(work = { it.delete(projected, floatState.originWeek) })
                        },
                        // 浮窗「调整本次」→ 解析当次 occurrence 并直达编辑表单(不再二次点击)。
                        onOccurrenceEdit = { projected ->
                            occurrenceEditDirect = true
                            val table = state.currentTable
                            val original = state.courses.firstOrNull { it.id == projected.id }
                            if (table != null && original != null) {
                                selectedOccurrence = com.lingion.sleepy.util.CourseOccurrences.resolve(
                                    original, DateUtils.dateOfWeek(table.startDate, floatState.originWeek, projected.day), state.transfers
                                )
                            }
                        },
                    )
                }
            }
        }

        selectedOccurrence?.let { occurrence ->
            state.effectiveCurrentTable?.takeIf { it.id == occurrence.course.tableId }?.let { table ->
                androidx.compose.runtime.key(occurrence) {
                    com.lingion.sleepy.ui.screen.edit.OccurrenceEditor(
                        occurrence, table,
                        onDismiss = { selectedOccurrence = null },
                        startInEdit = occurrenceEditDirect
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
            allCourses = state.currentTable?.let { table ->
                (1..7).flatMap { day ->
                    com.lingion.sleepy.util.HolidayRangeOps.HolidayTransferOps.coursesForDate(
                        DateUtils.dateOfWeek(table.startDate, state.selectedWeek, day),
                        table.startDate, table.maxWeek, state.courses, state.transfers
                    ).map { it.copy(day = day) }
                }
            } ?: emptyList(),
            // 用户报障 2026-09-10: 详情页聚簇与网格同一时间域 — ownTime 课
            // 按真实分钟判重叠, 节点占位值不再制造假冲突。
            timeJson = state.effectiveCurrentTable?.timeJson,
            onDismiss = { selectedCourse = null },
            onEdit = { course ->
                selectedCourse = null
                onEditCourse(state.courses.firstOrNull { it.id == course.id } ?: course)
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
                            .dbgTag("schedule_table_item", text = table.name, role = "button")
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
internal fun TopBar(
    currentWeek: Int,
    maxWeek: Int,
    startDate: String,
    displayContext: WeekDisplayContext?,
    onSwitchTable: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    scaleUncommitted: Boolean,
    onScaleCommit: () -> Unit,
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
    ScheduleToolbarLayout(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurfaceBackground(colors.surface, androidx.compose.ui.graphics.RectangleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        start = {
            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeekNavButton(
                    icon = Icons.Outlined.CalendarMonth,
                    tag = "schedule_switch_table",
                    contentDescriptionRes = R.string.schedule_switch_table,
                    onClick = onSwitchTable
                )
                // 2026-09-21 用户令: 撤回/取消撤回合成一个体育场形状(两半圆+中间矩形)胶囊,
                // 一起出现一起消失, 中间一条淡淡竖线 — TopBar 空间紧张, 单胶囊比两个分立按钮省位。
                // 无 undo 也无 redo 时整个胶囊隐藏; scale tick 仍独立圆形按钮(语义不同, 不入胶囊)。
                // MD3E (Task 6): 显隐走 AnimatedVisibility popup 家族 — 一体显隐语义不变,
                // 出入场补上 popup 签名性格 (0.8 尺度弹入弹出)。
                val hasUndo = com.lingion.sleepy.data.undo.UndoManager.hasSnapshot
                val hasRedo = com.lingion.sleepy.data.undo.UndoManager.hasRedoSnapshot
                AnimatedVisibility(
                    visible = hasUndo || hasRedo,
                    enter = sleepyPopupEnter(),
                    exit = sleepyPopupExit(),
                ) {
                    Row {
                        Spacer(modifier = Modifier.width(8.dp))
                        UndoRedoCapsule(
                            showUndo = hasUndo,
                            showRedo = hasRedo,
                            scale = 1f,
                            onUndo = onUndo,
                            onRedo = onRedo
                        )
                    }
                }
                // 2026-09-16 用户令: 捏合未确认时 tick 单列,与数据撤回胶囊互不干涉;
                // tick=落盘长期生效, 撤回=回到上次确认值(语义不同, 不并入胶囊)。
                if (scaleUncommitted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    WeekNavButton(
                        icon = Icons.Outlined.Check,
                        tag = "schedule_scale_keep",
                        contentDescriptionRes = R.string.schedule_scale_keep,
                        onClick = onScaleCommit
                    )
                }
            }
        },
        center = {
            BoxWithConstraints {
                val labelMaxWidth = (maxWidth - 112.dp).coerceAtLeast(0.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    WeekNavButton(icon = Icons.Outlined.ChevronLeft, tag = "schedule_prev_week", onClick = onPrevWeek)

                    Spacer(modifier = Modifier.width(8.dp))

                    // 第 N 周 标签 — 点击行为根据是否在当前实际周而不同
                    // 学期外: 标签带上周数(学期未开始 · 第 3 周), 翻周时数字跟着变, 用户才知道自己看到第几周
                    Box(Modifier.widthIn(max = labelMaxWidth)) {
                        val statusRes = when (semesterStatus) {
                            DateUtils.SemesterStatus.BEFORE_START -> R.string.semester_not_started
                            DateUtils.SemesterStatus.AFTER_END -> R.string.semester_ended
                            else -> 0
                        }
                        val weekLabel = if (statusRes == 0)
                            stringResource(R.string.schedule_current_week, currentWeek)
                        else "${stringResource(statusRes)} · ${stringResource(R.string.schedule_week_prefix, currentWeek)}"
                        Text(
                            // 表头语义永远=第 N 周(学期外带状态前缀但仍保留周数)。
                            // 最近有课日只能影响自动选中的周, 不能改写表头文案或点击行为。
                            text = weekLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isOnActual) colors.onPrimaryContainer else colors.primary,
                            modifier = Modifier
                                .glassControl(shape = SleepyTheme.shapes.medium, motionClearance = 4.dp)
                                .clip(SleepyTheme.shapes.medium)
                                .background(controlFill(if (isOnActual) colors.primaryContainer else colors.primaryContainer.copy(alpha = SleepyTheme.Alpha.inactive)))
                                .noRippleGlassClickable {
                                    if (isOnActual) {
                                        // 在当前实际周 → 弹下拉菜单
                                        menuOpen = true
                                    } else {
                                        // 不在当前实际周 → 一键跳回
                                        onJumpToActual()
                                    }
                                }
                                .dbgTag("schedule_week_label", text = weekLabel, role = "button")
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
                                                .glassControl(shape = CircleShape, selected = isCurrent, motionClearance = 4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    controlFill(if (isCurrent) colors.primary
                                                    else colors.surfaceContainerHigh)
                                                )
                                                .noRippleGlassClickable {
                                                    onSelectWeek(w)
                                                    menuOpen = false
                                                }
                                                .dbgTag("schedule_week#$w", text = w.toString(), role = "button"),
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

                    WeekNavButton(icon = Icons.Outlined.ChevronRight, tag = "schedule_next_week", onClick = onNextWeek)
                }
            }
        },
        end = {
            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeekNavButton(
                    icon = Icons.Outlined.Add,
                    tag = "schedule_add_course",
                    contentDescriptionRes = R.string.schedule_add_course,
                    onClick = onAddCourse
                )
                Spacer(modifier = Modifier.width(8.dp))
                WeekNavButton(
                    icon = Icons.Outlined.IosShare,
                    tag = "schedule_share",
                    contentDescriptionRes = R.string.schedule_share_table,
                    onClick = onShare
                )
            }
        }
    )
}

@Composable
private fun WeekNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit,
    contentDescriptionRes: Int? = null
) {
    val colors = MaterialTheme.colorScheme
    com.lingion.sleepy.ui.component.liquidglass.GlassIconButton(
        onClick = onClick,
        modifier = Modifier.dbgTag(tag, role = "button"),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescriptionRes?.let { stringResource(it) },
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 2026-09-21 用户令: 撤回/取消撤回一体胶囊 — 体育场形状(左右半圆+中间矩形),
 * 撤回左半·取消撤回右半, 中间一条淡淡竖线分隔。两半同现同隐(由调用方保证:
 * hasUndo||hasRedo 才挂载, 各半按各自有无快照显示/禁用), 省 TopBar 空间。
 */
@Composable
private fun UndoRedoCapsule(
    showUndo: Boolean,
    showRedo: Boolean,
    scale: Float,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val optical = liquidGlassControlsEnabled()
    val undoClick = if (optical) Modifier.clickable(
        interactionSource = interaction, indication = null, enabled = showUndo, role = Role.Button, onClick = onUndo,
    ) else Modifier.noRippleGlassClickable(enabled = showUndo, onClick = onUndo)
    val redoClick = if (optical) Modifier.clickable(
        interactionSource = interaction, indication = null, enabled = showRedo, role = Role.Button, onClick = onRedo,
    ) else Modifier.noRippleGlassClickable(enabled = showRedo, onClick = onRedo)
    val halfSize = 32.dp * scale
    val dividerHeight = 14.dp * scale
    Row(
        modifier = Modifier
            .height(halfSize)
            .glassControl(grouped = true, motionClearance = 4.dp, interactionSource = interaction)
            .clip(CircleShape)
            .background(controlFill(colors.surfaceContainerHigh)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左半: 撤回 — 无可撤回时半透明禁用(占位保形, 双侧共存才像一体胶囊)
        Box(
            modifier = Modifier
                .then(
                    if (scale == 1f) Modifier.size(width = 32.dp, height = 32.dp)
                    else Modifier.size(width = halfSize, height = halfSize)
                )
                .then(undoClick)
                .dbgTag("schedule_undo", text = stringResource(R.string.schedule_undo), role = "button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Undo,
                contentDescription = stringResource(R.string.schedule_undo),
                tint = colors.onSurfaceVariant.copy(alpha = if (showUndo) 1f else 0.38f),
                modifier = Modifier.size(20.dp * scale)
            )
        }
        // 中缝: 淡淡竖线 — 与图标同色调降透明度, 视觉上"一体两半"
        Box(
            modifier = Modifier
                .size(width = 1.dp, height = dividerHeight)
                .background(colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive))
        )
        // 右半: 取消撤回 — 无可重做时半透明禁用
        Box(
            modifier = Modifier
                .then(
                    if (scale == 1f) Modifier.size(width = 32.dp, height = 32.dp)
                    else Modifier.size(width = halfSize, height = halfSize)
                )
                .then(redoClick)
                .dbgTag("schedule_redo", text = stringResource(R.string.schedule_redo), role = "button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Redo,
                contentDescription = stringResource(R.string.schedule_redo),
                tint = colors.onSurfaceVariant.copy(alpha = if (showRedo) 1f else 0.38f),
                modifier = Modifier.size(20.dp * scale)
            )
        }
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
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight).dbgTag("schedule_add", role = "button"),
            shape = SleepyTheme.Buttons.shape,
        ) {
            Text(stringResource(R.string.schedule_manual_first))
        }
        // [intentional custom] FilledTonalButton + ctaHeight: 官方变体自带动效/形状,
        // 仅保留 Sleepy 的 56dp CTA 高度档位(官方无此 token)。
        FilledTonalButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight).dbgTag("schedule_import", role = "button"),
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
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight).dbgTag("schedule_go_import", role = "button"),
            shape = SleepyTheme.Buttons.shape,
        ) {
            Text(stringResource(R.string.schedule_empty_import))
        }
        // 副按钮 = 手动创建第一张课表 (建表流, 非加课 — 无表载体时"创建第一门课"无从谈起)
        // [intentional custom] FilledTonalButton + ctaHeight: 同上, 仅保留 56dp CTA 档位。
        FilledTonalButton(
            onClick = onCreateTable,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight).dbgTag("schedule_create_table", role = "button"),
            shape = SleepyTheme.Buttons.shape,
        ) {
            Text(stringResource(R.string.schedule_empty_create_table))
        }
    }
}
