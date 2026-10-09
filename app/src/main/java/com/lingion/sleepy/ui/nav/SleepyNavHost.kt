package com.lingion.sleepy.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.scene.Scene
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.NavDockSpec
import com.lingion.sleepy.ui.component.PillNavigationBar
import com.lingion.sleepy.ui.component.PillNavItemSpec
import com.lingion.sleepy.ui.component.PillBarState
import com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding
import com.lingion.sleepy.ui.screen.edit.AddCourseScreen
import com.lingion.sleepy.ui.screen.mine.AllTablesScreen
import com.lingion.sleepy.ui.screen.mine.CourseListScreen
import com.lingion.sleepy.ui.screen.mine.AppearanceScreen
import com.lingion.sleepy.ui.screen.mine.PeriodHeaderSettingsScreen
import com.lingion.sleepy.ui.screen.mine.EditTableScreen
import com.lingion.sleepy.ui.screen.mine.GeneralSettingsScreen
import com.lingion.sleepy.ui.screen.mine.HolidaySettingsScreen
import com.lingion.sleepy.ui.screen.mine.ExportScreen
import com.lingion.sleepy.ui.screen.mine.ReminderScreen
import com.lingion.sleepy.ui.screen.mine.AboutScreen
import com.lingion.sleepy.util.UpdateNotifier
import com.lingion.sleepy.ui.screen.mine.LicenseScreen
import com.lingion.sleepy.ui.screen.mine.PeriodTablesScreen
import com.lingion.sleepy.ui.screen.mine.PeriodTableEditScreen
import com.lingion.sleepy.ui.screen.schedule.ScheduleScreen
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.screen.schedule.ViewMode
import com.lingion.sleepy.ui.screen.today.TodayScreen
import com.lingion.sleepy.ui.screen.today.CompactTodayPane
import com.lingion.sleepy.ui.screen.manage.ManagementPage
import com.lingion.sleepy.ui.screen.mine.MineScreen
import com.lingion.sleepy.ui.screen.widget.WidgetEditScreen
import com.lingion.sleepy.ui.screen.widget.WidgetManagementScreen
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.MainTabs
import com.lingion.sleepy.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler

/**
 * issue#45: 顶层 NavHost。
 *
 * AppRoot 持导航意图(哪个 tab、底栏形态、视图模式),NavHost 持栈;两者通过
 * SleepyNavigator 解耦 — 页面只调 nav.openXxx(),不碰 NavHostController。
 *
 * 弃表语义(EditTable/PeriodTableEdit)放**路由内** BackHandler:旋转/进程恢复
 * 跟着返回栈走,各页自管弃表,与旧"14 个 if 分支共享一个 BackHandler"模型说再见。
 * NavHost 自带 13 个 onBack 拦截覆盖(pop 弹栈),per-route BackHandler 覆写
 * 其上的 edit-discard 拦截 — BackHandler 的"最后注册者优先"语义正好支持。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SleepyNavHost(
    nav: NavBackStack<NavKey>,
    navigator: SleepyNavigator,
    currentTab: Tab,
    setCurrentTab: (Tab) -> Unit,
    navDock: Boolean,
    onNavDockChange: (Boolean) -> Unit,
    scheduleViewMode: ViewMode,
    onScheduleViewModeChange: (ViewMode) -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    deepLinkCourse: CourseEntity?,
    onDeepLinkConsumed: () -> Unit,
    mainVm: ScheduleViewModel,
    currentTableId: Long?,
    mainScope: CoroutineScope,
    onCreateNewTable: () -> Unit,
    pillBarState: PillBarState,
) {
    val session = navigator.session
    val updateNoticeVisible by UpdateNotifier.noticeVisible.collectAsState()

    // Keep typed stack and entry content under the official androidx.navigation3 NavDisplay.
    // editingCourse is intentionally not saveable. Drop a restored edit key if its
    // in-memory session is absent, rather than showing an empty editing form.
    val currentRoute = navigator.backStack.lastOrNull() as? SleepyRoute.AddCourse
    LaunchedEffect(currentRoute, deepLinkCourse?.id) {
        if (currentRoute?.editing == true && session.editingCourse == null && deepLinkCourse == null) {
            navigator.pop()
        }
    }

    // 深度链接: 外部深链(小组件/快捷方式)→ 编辑/查看课程。AppRoot 把课程给到
    // editingCourseFlow,这里消费:进 AddCourse + 把课程绑到 session。
    LaunchedEffect(deepLinkCourse?.id) {
        val course = deepLinkCourse ?: return@LaunchedEffect
        session.beginEditCourse(course)
        navigator.openAddCourse(courseId = course.id, editing = true)
        onDeepLinkConsumed()
    }

    val predictiveSpec: (AnimatedContentTransitionScope<Scene<NavKey>>, Int) -> ContentTransform =
        { _, _ -> sleepyPredictivePopTransform }

    NavDisplay(
        backStack = nav,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
        ),
        onBack = { navigator.pop() },
        transitionSpec = { sleepyForwardTransform },
        popTransitionSpec = { sleepyPopTransform },
        predictivePopTransitionSpec = predictiveSpec,
        entryProvider = entryProvider {
        entry<SleepyRoute.Main> {

            MainRoute(
                currentTab = currentTab,
                setCurrentTab = setCurrentTab,
                navigator = navigator,
                mainVm = mainVm,
                currentTableId = currentTableId,
                mainScope = mainScope,
                navDock = navDock,
                onNavDockChange = onNavDockChange,
                scheduleViewMode = scheduleViewMode,
                onScheduleViewModeChange = onScheduleViewModeChange,
                onCreateNewTable = onCreateNewTable,
                updateNoticeVisible = updateNoticeVisible,
                pillBarState = pillBarState,
            )
        }

        entry<SleepyRoute.AddCourse> { key ->
            // 基线 §1.3 例外: 编辑课程会话**不纳入**任何保存作用域。
            // editingCourse 是纯内存态(NavSession), 进程恢复后必为 null, 所以这条
            // 路由被系统恢复出来时上方 LaunchedEffect 会把它弹掉 → 回到主 Tab。
            // 手动新增(editing=false)走空表单, 属正常可恢复路径。
            val courseId = key.courseId
            val editing = key.editing
            val editingCourse = remember(editing, courseId, session.editingCourse?.id) {
                if (editing) session.editingCourse else null
            }
            AddCourseScreen(
                onBack = {
                    session.clearEditCourse()
                    if (!navigator.pop()) {
                        // 栈只有 MAIN,直接清课程态即可
                    }
                },
                onSaved = {
                    session.clearEditCourse()
                    if (!navigator.pop()) { /* 同上 */ }
                    setCurrentTab(Tab.Schedule)
                },
                editingCourse = editingCourse,
            )
        }

        entry<SleepyRoute.AllTables> {
            AllTablesScreen(
                onBack = { navigator.pop() },
                onCreateNewTable = onCreateNewTable,
                onOpenEditTable = { tableId -> navigator.openEditTable(tableId = tableId) },
            )
        }

        entry<SleepyRoute.CourseList> {
            CourseListScreen(onBack = { navigator.pop() })
        }

        entry<SleepyRoute.EditTable> { key ->
            val routeTableId = key.tableId.takeUnless { it == SleepyRoute.NO_ID }
            val routePendingNew = key.pendingNew.takeUnless { it == SleepyRoute.NO_ID }
            val routePrevDefault = key.prevDefault.takeUnless { it == SleepyRoute.NO_ID }

            // 本地 pending 态:用户保存/删除/弃表后清掉,系统返回只看这个。
            // 直接用路由参数会让"保存后按返回又触发弃表"这类历史 bug 复发。
            var pending by rememberSaveable { mutableStateOf<Long?>(routePendingNew) }
            var prevDefault by rememberSaveable { mutableStateOf<Long?>(routePrevDefault) }

            // 弃表兜底:系统返回手势 + 系统返回键都走这里。EditTableScreen 自带
            // 的"返回"按钮调 onBack(不清 pending),"保存"调 onSaved(也不清),由
            // 我们的 in-screen 返回按钮决定是否触发弃表;此处只兜系统返回。
            BackHandler(enabled = pending != null) {
                val discardId = pending; val fallback = prevDefault
                pending = null; prevDefault = null
                if (discardId != null) mainVm.discardNewTable(discardId, fallback)
                navigator.pop()
            }

            EditTableScreen(
                tableId = routeTableId,
                pendingNewTableId = pending,
                onBack = { navigator.pop() },
                onDiscardPending = {
                    val discardId = pending; val fallback = prevDefault
                    pending = null; prevDefault = null
                    if (discardId != null) mainVm.discardNewTable(discardId, fallback)
                    navigator.pop()
                },
                onSaved = {
                    pending = null; prevDefault = null
                    navigator.pop()
                },
                onDeleted = {
                    pending = null; prevDefault = null
                    navigator.pop()
                    setCurrentTab(Tab.Schedule)
                },
            )
        }

        entry<SleepyRoute.Appearance> {
            AppearanceScreen(
                onBack = { navigator.pop() },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onOpenPeriodHeaderSettings = { navigator.openPeriodHeaderSettings() },
            )
        }

        entry<SleepyRoute.PeriodHeaderSettings> {
            PeriodHeaderSettingsScreen(onBack = { navigator.pop() })
        }

        // ----------------------------------------------------------------
        entry<SleepyRoute.General> {
            GeneralSettingsScreen(
                onBack = { navigator.pop() },
                onOpenHoliday = { navigator.openHoliday() },
                onOpenWidgetManagement = { navigator.openWidgetManagement() },
                navDock = navDock,
                onNavDockChange = onNavDockChange,
            )
        }

        entry<SleepyRoute.Holiday> {
            HolidaySettingsScreen(
                onBack = { navigator.pop() },
                tableId = currentTableId,
                viewModel = mainVm
            )
        }

        entry<SleepyRoute.Export> {
            ExportScreen(onBack = { navigator.pop() })
        }

        entry<SleepyRoute.Reminder> {
            ReminderScreen(
                onBack = { navigator.pop() },
                onOpenHoliday = { navigator.openHoliday() },
            )
        }

        entry<SleepyRoute.About> {
            AboutScreen(
                onBack = { navigator.pop() },
                onOpenLicense = { navigator.openLicense() },
                updateNoticeVisible = updateNoticeVisible,
            )
        }

        entry<SleepyRoute.License> {
            LicenseScreen(onBack = { navigator.pop() })
        }

        // ----------------------------------------------------------------
        entry<SleepyRoute.WidgetManagement> {
            WidgetManagementScreen(
                onBack = { navigator.pop() },
                onSelect = { widgetId -> navigator.openWidgetEdit(widgetId) },
            )
        }

        entry<SleepyRoute.WidgetEdit> { key ->
            WidgetEditScreen(
                widgetId = key.widgetId,
                onBack = { navigator.pop() },
            )
        }

        // ----------------------------------------------------------------
        entry<SleepyRoute.PeriodTables> {
            PeriodTablesScreen(
                onBack = { navigator.pop() },
                onOpenEdit = { periodId -> navigator.openPeriodEdit(periodId, isNew = false) },
                onCreateNew = { newId -> navigator.createPeriodTableAndEdit(newId) },
            )
        }

        entry<SleepyRoute.PeriodEdit> { key ->
            val periodId = key.periodId
            val routeIsNew = key.isNew
            // 镜像 PeriodTableEditScreen 的 unsavedNew 模式:用户保存/弃表后清掉,
            // 系统的返回兜底据此决定是否弃残留行。
            var unsavedNew by rememberSaveable { mutableStateOf(routeIsNew) }

            BackHandler(enabled = unsavedNew) {
                unsavedNew = false
                mainVm.discardNewPeriodTable(periodId)
                navigator.pop()
            }

            PeriodTableEditScreen(
                periodTableId = periodId,
                isNewUnsaved = unsavedNew,
                onBack = {
                    unsavedNew = false
                    navigator.pop()
                },
            )
        }
        }
    )
}

/**
 * 主页签路由:含底栏(Scaffold 或悬浮 Dock),4 个 tab。
 *
 * 返回键处理:栈空(在 MAIN 上)才接管 — 切到课表页 + 双击退出。
 * 其它页面的返回由 NavHost 自动 pop 处理,这里不拦(拦了反而把栈 pop 错)。
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun MainRoute(
    currentTab: Tab,
    setCurrentTab: (Tab) -> Unit,
    navigator: SleepyNavigator,
    mainVm: ScheduleViewModel,
    currentTableId: Long?,
    mainScope: CoroutineScope,
    navDock: Boolean,
    onNavDockChange: (Boolean) -> Unit,
    scheduleViewMode: ViewMode,
    onScheduleViewModeChange: (ViewMode) -> Unit,
    onCreateNewTable: () -> Unit,
    updateNoticeVisible: Boolean,
    pillBarState: PillBarState,
) {
    val navItems = Tab.entries.map { PillNavItemSpec(it.icon, stringResource(it.labelRes), badge = updateNoticeVisible && it == Tab.Mine) }
    val holder: SaveableStateHolder = rememberSaveableStateHolder()
    // 双击退出只在课表页生效;其它 tab 第一次返回回课表页。
    val ctxForExit = LocalContext.current
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler(enabled = currentTab != Tab.Schedule) {
        setCurrentTab(Tab.Schedule)
    }
    BackHandler(enabled = currentTab == Tab.Schedule) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackAt < 2000L) {
            (ctxForExit as? android.app.Activity)?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(ctxForExit, R.string.exit_press_back_again, Toast.LENGTH_SHORT).show()
        }
    }

    // issue#45 ③横屏/平板: 自研 PillNavigationBar 无此形态,官方 NavigationRail 有 → 用官方。
    // 官方没有悬浮药丸 Dock → Compact 且 navDock=true 时保留自研 PillNavigationBar(dock=true)。
    val activity = ctxForExit as? android.app.Activity
    val sizeClass = activity?.let { calculateWindowSizeClass(it) }
    val isCompact = sizeClass == null || sizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    if (!isCompact) {
        // 平板宽屏 Master-Detail (PLAN:wide-merged):
        // 左 50% = ScheduleScreen 永久固定;右 50% = Today/Manage/Mine (跟用户切 rail 项);
        // Rail: 当前选中的 tab 跟 Schedule 组合成跑道胶囊(高亮),其它两个普通单图标。
        // 默认 currentTab = Tab.Schedule (Compact 分支默认),宽屏派生为 Tab.Today
        // 让右半默认显示今日、slot 1 胶囊默认高亮,两侧一致。
        // 红点显示条件 = 真实 updateNoticeVisible (云端有新 release + 用户未 dismiss)。
        val effectiveRightTab: Tab = if (currentTab == Tab.Schedule) Tab.Today else currentTab
        val onTabletEditCourse: (CourseEntity) -> Unit = { course ->
            navigator.session.beginEditCourse(course)
            navigator.openAddCourse(course.id, editing = true)
        }
        val onTabletGoImport: () -> Unit = {
            com.lingion.sleepy.MainActivity.autoShowImportOnceState.value = true
            setCurrentTab(Tab.Manage)
        }
        val onTabletManualAdd: () -> Unit = { navigator.openAddCourse() }
        val colors = MaterialTheme.colorScheme
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            NavigationRail {
                // 当前选中的 tab 跟 Schedule 组合成跑道胶囊(强高亮 primaryContainer);
                // 其它两个 tab 是普通 NavigationRailItem 单图标(标准 selected 态)。
                when (effectiveRightTab) {
                    Tab.Today -> {
                        CombinedRailItem(
                            selected = true,
                            scheduleIcon = Tab.Schedule.icon,
                            tabIcon = Tab.Today.icon,
                            tabShowUpdateDot = false,
                            onClick = { setCurrentTab(Tab.Today) },
                        )
                        PlainRailItem(
                            selected = false,
                            tab = Tab.Manage,
                            showUpdateDot = false,
                            onClick = { setCurrentTab(Tab.Manage) },
                        )
                        PlainRailItem(
                            selected = false,
                            tab = Tab.Mine,
                            showUpdateDot = updateNoticeVisible,
                            onClick = { setCurrentTab(Tab.Mine) },
                        )
                    }
                    Tab.Manage -> {
                        PlainRailItem(
                            selected = false,
                            tab = Tab.Today,
                            showUpdateDot = false,
                            onClick = { setCurrentTab(Tab.Today) },
                        )
                        CombinedRailItem(
                            selected = true,
                            scheduleIcon = Tab.Schedule.icon,
                            tabIcon = Tab.Manage.icon,
                            tabShowUpdateDot = false,
                            onClick = { setCurrentTab(Tab.Manage) },
                        )
                        PlainRailItem(
                            selected = false,
                            tab = Tab.Mine,
                            showUpdateDot = updateNoticeVisible,
                            onClick = { setCurrentTab(Tab.Mine) },
                        )
                    }
                    Tab.Mine -> {
                        PlainRailItem(
                            selected = false,
                            tab = Tab.Today,
                            showUpdateDot = false,
                            onClick = { setCurrentTab(Tab.Today) },
                        )
                        PlainRailItem(
                            selected = false,
                            tab = Tab.Manage,
                            showUpdateDot = false,
                            onClick = { setCurrentTab(Tab.Manage) },
                        )
                        CombinedRailItem(
                            selected = true,
                            scheduleIcon = Tab.Schedule.icon,
                            tabIcon = Tab.Mine.icon,
                            tabShowUpdateDot = updateNoticeVisible,
                            onClick = { setCurrentTab(Tab.Mine) },
                        )
                    }
                    else -> {
                        PlainRailItem(
                            selected = false,
                            tab = Tab.Today,
                            showUpdateDot = false,
                            onClick = { setCurrentTab(Tab.Today) },
                        )
                        PlainRailItem(
                            selected = false,
                            tab = Tab.Manage,
                            showUpdateDot = false,
                            onClick = { setCurrentTab(Tab.Manage) },
                        )
                        PlainRailItem(
                            selected = false,
                            tab = Tab.Mine,
                            showUpdateDot = updateNoticeVisible,
                            onClick = { setCurrentTab(Tab.Mine) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 左: 课表永久固定 (地球毁灭也不变)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerLow)
                ) {
                    ScheduleScreen(
                        viewMode = scheduleViewMode,
                        onViewModeChange = onScheduleViewModeChange,
                        onGoImport = onTabletGoImport,
                        onManualAdd = onTabletManualAdd,
                        onCreateTable = onCreateNewTable,
                        onEditCourse = onTabletEditCourse,
                        viewModel = mainVm,
                    )
                }
                // 暗色 gap — 12dp 沟槽 (背景 = background)
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .fillMaxSize()
                        .background(colors.background)
                )
                // 右: AnimatedContent 切换 Today/Manage/Mine
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerLow)
                ) {
                    RightHalfContent(
                        currentTab = effectiveRightTab,
                        mainVm = mainVm,
                        navigator = navigator,
                        ctx = ctxForExit,
                        onTabletEditCourse = onTabletEditCourse,
                        onTabletGoImport = onTabletGoImport,
                        onTabletManualAdd = onTabletManualAdd,
                        onCreateNewTable = onCreateNewTable,
                        onNavigateManageTab = { setCurrentTab(Tab.Manage) },
                    )
                }
            }
        }
    } else if (navDock) {
        // Compact + 悬浮 Dock: 官方无此形态 → 保留自研 PillNavigationBar(dock=true)
        var dockExtraDp by remember { mutableStateOf(NavDockSpec.capsuleHeight + NavDockSpec.bottomFloat) }
        var dockOverlayPx by remember { mutableStateOf(0) }
        val densityForDock = LocalDensity.current
        if (dockOverlayPx > 0) {
            dockExtraDp = with(densityForDock) { dockOverlayPx.toDp() }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalNavExtraBottomPadding provides dockExtraDp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainTabs(
                        currentTab = currentTab,
                        setCurrentTab = setCurrentTab,
                        navigator = navigator,
                        mainVm = mainVm,
                        mainScope = mainScope,
                        viewMode = scheduleViewMode,
                        onViewModeChange = onScheduleViewModeChange,
                        onCreateNewTable = onCreateNewTable,
                        holder = holder,
                        updateNoticeVisible = updateNoticeVisible,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { c -> dockOverlayPx = c.size.height }
            ) {
                PillNavigationBar(
                    items = navItems,
                    selectedIndex = currentTab.ordinal,
                    onSelect = { setCurrentTab(Tab.entries[it]) },
                    dock = true,
                    state = pillBarState,
                )
            }
        }
    } else {
        // Compact + 贴底: 官方有 NavigationBar → 用官方(替自研贴底形态)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { setCurrentTab(tab) },
                            icon = { NavigationTabIcon(tab, showUpdateDot = updateNoticeVisible && tab == Tab.Mine) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                MainTabs(
                    currentTab = currentTab,
                    setCurrentTab = setCurrentTab,
                    navigator = navigator,
                    mainVm = mainVm,
                    mainScope = mainScope,
                    viewMode = scheduleViewMode,
                    onViewModeChange = onScheduleViewModeChange,
                    onCreateNewTable = onCreateNewTable,
                    holder = holder,
                    updateNoticeVisible = updateNoticeVisible,
                )
            }
        }
    }
}

/**
 * 官方 NavigationBar / NavigationRail 的 tab 图标 — Mine 且有更新提醒时右上角画主题色小圆点。
 * 三种导航形态(贴底/Rail/悬浮 Dock)与自研 PillNavigationBar 共用同一 noticeVisible 状态。
 */
@Composable
private fun NavigationTabIcon(tab: Tab, showUpdateDot: Boolean) {
    val colors = MaterialTheme.colorScheme
    if (!showUpdateDot) {
        Icon(tab.icon, contentDescription = null)
        return
    }
    Box {
        Icon(tab.icon, contentDescription = null)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(7.dp)
                .background(colors.primary, androidx.compose.foundation.shape.CircleShape)
        )
    }
}

/**
 * Master-Detail 跑道胶囊导航项 — Schedule + 任意 tab 图标组合,无 label。
 * 56dp 宽 × 64dp 高;上下两半各塞图标,中线分隔;选中整组**强高亮**(primaryContainer)。
 * 自研容器,绕开 NavigationRailItem 默认 24dp 图标槽位。
 * `tabShowUpdateDot` = true 时在"从 tab"图标右上角画 7dp 红点(原 NavigationTabIcon 风格)。
 */
@Composable
private fun CombinedRailItem(
    selected: Boolean,
    scheduleIcon: androidx.compose.ui.graphics.vector.ImageVector,
    tabIcon: androidx.compose.ui.graphics.vector.ImageVector,
    tabShowUpdateDot: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (selected) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
    val iconColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .width(56.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(containerColor)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(scheduleIcon, contentDescription = "课表", tint = iconColor, modifier = Modifier.size(22.dp))
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 10.dp),
                thickness = 0.5.dp,
                color = colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.hairline),
            )
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tabIcon, contentDescription = "从 tab", tint = iconColor, modifier = Modifier.size(22.dp))
                if (tabShowUpdateDot) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .background(colors.primary, androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Master-Detail 普通 rail 项 — 单图标 + 标准 NavigationRailItem 风格。
 * 用于"当前选中胶囊"以外的另两个 tab。
 */
@Composable
private fun PlainRailItem(
    selected: Boolean,
    tab: Tab,
    showUpdateDot: Boolean,
    onClick: () -> Unit,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { NavigationTabIcon(tab, showUpdateDot = showUpdateDot) },
        label = { Text(stringResource(tab.labelRes)) },
    )
}

/**
 * 右半边内容 — AnimatedContent 包 3 个子页 (Today / Manage / Mine)。
 * 200ms 渐隐渐显。子页面跳转回调全部接到 navigator (避免空 lambda 让按钮"点不动")。
 */
@Composable
private fun RightHalfContent(
    currentTab: Tab,
    mainVm: ScheduleViewModel,
    navigator: SleepyNavigator,
    ctx: android.content.Context,
    onTabletEditCourse: (CourseEntity) -> Unit,
    onTabletGoImport: () -> Unit,
    onTabletManualAdd: () -> Unit,
    onCreateNewTable: () -> Unit,
    onNavigateManageTab: () -> Unit,
) {
    // 导入草稿来源 — 与 MainTabs (Compact) 同源
    val draftEntities by com.lingion.sleepy.SleepyApp.get().importDraftRepository
        .observeAll().collectAsState(initial = emptyList())
    val drafts: List<com.lingion.sleepy.ui.screen.imports.ImportDraft> =
        draftEntities.mapNotNull { entity ->
            val snapshot = com.lingion.sleepy.data.jw.JwImportDraftCodec.fromJson(entity.payloadJson)
                ?: return@mapNotNull null
            com.lingion.sleepy.ui.screen.imports.ImportDraft(
                id = entity.id,
                name = snapshot.tableName.ifBlank { snapshot.school.name },
                details = "${snapshot.courses.size} ${stringResource(com.lingion.sleepy.R.string.import_courses)}",
            )
        }
    val draftScope = androidx.compose.runtime.rememberCoroutineScope()
    AnimatedContent(
        targetState = currentTab,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
        },
        label = "right-half-tab-switch",
    ) { tab ->
        when (tab) {
            Tab.Today -> CompactTodayPane(
                onEditCourse = onTabletEditCourse,
                viewModel = mainVm,
            )
            Tab.Manage -> ManagementPage(
                autoShowImportSheet = com.lingion.sleepy.MainActivity.autoShowImportOnceState.value
                    || com.lingion.sleepy.MainActivity.pendingImportText != null,
                onJwImportRequested = {
                    ctx.startActivity(android.content.Intent(
                        ctx,
                        com.lingion.sleepy.ui.screen.imports.JwImportActivity::class.java
                    ))
                },
                onCreateNewTableRequested = onCreateNewTable,
                onCreateNewPeriodTableRequested = { newId -> navigator.createPeriodTableAndEdit(newId) },
                onManualAdd = onTabletManualAdd,
                onEditCurrentTable = { navigator.openEditTable() },
                onExportRequested = { navigator.openExport() },
                onOpenAllTables = { navigator.openAllTables() },
                drafts = drafts,
                onRestoreDraft = { id ->
                    ctx.startActivity(
                        android.content.Intent(
                            ctx,
                            com.lingion.sleepy.ui.screen.imports.JwImportActivity::class.java
                        ).putExtra(
                            com.lingion.sleepy.ui.screen.imports.JwImportActivity.EXTRA_DRAFT_ID,
                            id
                        )
                    )
                },
                onDeleteDraft = { id ->
                    draftScope.launch {
                        com.lingion.sleepy.SleepyApp.get().importDraftRepository.delete(id)
                    }
                },
                onImported = { /* 留在管理页, 摘要卡就地刷新 */ },
                viewModel = mainVm,
            )
            Tab.Mine -> MineScreen(
                viewModel = mainVm,
                onOpenAllTables = { navigator.openAllTables() },
                onOpenCourseList = { navigator.openCourseList() },
                onOpenPeriodTables = { navigator.openPeriodTables() },
                onOpenAppearance = { navigator.openAppearance() },
                onOpenGeneral = { navigator.openGeneral() },
                onOpenExport = { navigator.openExport() },
                onOpenReminder = { navigator.openReminder() },
                onOpenAbout = { navigator.openAbout() },
            )
            else -> CompactTodayPane(
                onEditCourse = onTabletEditCourse,
                viewModel = mainVm,
            )
        }
    }
}

