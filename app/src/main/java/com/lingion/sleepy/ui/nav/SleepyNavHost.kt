package com.lingion.sleepy.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.res.stringResource
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
import com.lingion.sleepy.ui.screen.mine.AppearanceScreen
import com.lingion.sleepy.ui.screen.mine.EditTableScreen
import com.lingion.sleepy.ui.screen.mine.GeneralSettingsScreen
import com.lingion.sleepy.ui.screen.mine.HolidaySettingsScreen
import com.lingion.sleepy.ui.screen.mine.ExportScreen
import com.lingion.sleepy.ui.screen.mine.ReminderScreen
import com.lingion.sleepy.ui.screen.mine.AboutScreen
import com.lingion.sleepy.ui.screen.mine.LicenseScreen
import com.lingion.sleepy.ui.screen.mine.PeriodTablesScreen
import com.lingion.sleepy.ui.screen.mine.PeriodTableEditScreen
import com.lingion.sleepy.ui.screen.schedule.ScheduleScreen
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.screen.schedule.ViewMode
import com.lingion.sleepy.ui.screen.today.TodayScreen
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
    mainScope: CoroutineScope,
    onCreateNewTable: () -> Unit,
    pillBarState: PillBarState,
) {
    val session = navigator.session

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
                mainScope = mainScope,
                navDock = navDock,
                onNavDockChange = onNavDockChange,
                scheduleViewMode = scheduleViewMode,
                onScheduleViewModeChange = onScheduleViewModeChange,
                onCreateNewTable = onCreateNewTable,
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
            )
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
            HolidaySettingsScreen(onBack = { navigator.pop() })
        }

        entry<SleepyRoute.Export> {
            ExportScreen(onBack = { navigator.pop() })
        }

        entry<SleepyRoute.Reminder> {
            ReminderScreen(onBack = { navigator.pop() })
        }

        entry<SleepyRoute.About> {
            AboutScreen(
                onBack = { navigator.pop() },
                onOpenLicense = { navigator.openLicense() },
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
@Composable
private fun MainRoute(
    currentTab: Tab,
    setCurrentTab: (Tab) -> Unit,
    navigator: SleepyNavigator,
    mainVm: ScheduleViewModel,
    mainScope: CoroutineScope,
    navDock: Boolean,
    onNavDockChange: (Boolean) -> Unit,
    scheduleViewMode: ViewMode,
    onScheduleViewModeChange: (ViewMode) -> Unit,
    onCreateNewTable: () -> Unit,
    pillBarState: PillBarState,
) {
    val navItems = Tab.entries.map { PillNavItemSpec(it.icon, stringResource(it.labelRes)) }
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

    if (!navDock) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                PillNavigationBar(
                    items = navItems,
                    selectedIndex = currentTab.ordinal,
                    onSelect = { setCurrentTab(Tab.entries[it]) },
                    dock = false,
                    state = pillBarState,
                )
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
                )
            }
        }
    } else {
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
    }
}
