package com.lingion.sleepy.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.NavDockSpec
import com.lingion.sleepy.ui.component.PillNavigationBar
import com.lingion.sleepy.ui.component.PillNavItemSpec
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
import com.lingion.sleepy.util.UpdateNotifier
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
    nav: NavHostController,
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
) {
    val session = navigator.session
    val updateNoticeVisible by UpdateNotifier.noticeVisible.collectAsState()

    // 共享轴过渡:必须先在 composable 上下文求值,再把结果作为对象传入 NavHost
    // 的 transition lambda(那个 lambda 不是 composable 上下文,无法就地读
    // MaterialTheme.motionScheme)。
    val enter = sleepySharedAxisEnter()
    val exit = sleepySharedAxisExit()
    val popEnter = sleepySharedAxisPopEnter()
    val popExit = sleepySharedAxisPopExit()

    // 恢复守卫:进程被回收后栈里若有 add_course?editing=true 但会话态 editingCourse
    // 是 null(故意不持久化的产物,基线 §1.3),把这条路由弹掉,回到主 Tab。
    // 状态: deepLinkCourse 不消费(由 onDeepLinkConsumed 显式清),session 也不存。
    val currentRoute = nav.currentBackStackEntry?.destination?.route
    val editingRouteCourseId = nav.currentBackStackEntry?.arguments?.getLong("courseId") ?: Routes.NO_ID
    val editingRouteFlag = nav.currentBackStackEntry?.arguments?.getBoolean("editing") ?: false
    LaunchedEffect(currentRoute, editingRouteFlag, editingRouteCourseId, deepLinkCourse?.id) {
        if (currentRoute == Routes.ADD_COURSE &&
            editingRouteFlag &&
            session.editingCourse == null &&
            deepLinkCourse == null
        ) {
            nav.popBackStack()
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

    NavHost(
        navController = nav,
        startDestination = Routes.MAIN,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { popEnter },
        popExitTransition = { popExit },
    ) {
        // ----------------------------------------------------------------
        composable(Routes.MAIN) {
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
                updateNoticeVisible = updateNoticeVisible,
            )
        }

        // ----------------------------------------------------------------
        composable(Routes.ADD_COURSE, arguments = addCourseArgs) { entry ->
            // 基线 §1.3 例外: 编辑课程会话**不纳入**任何保存作用域。
            // editingCourse 是纯内存态(NavSession), 进程恢复后必为 null, 所以这条
            // 路由被系统恢复出来时上方 LaunchedEffect 会把它弹掉 → 回到主 Tab。
            // 手动新增(editing=false)走空表单, 属正常可恢复路径。
            // 若哪天给这里补上 SaveableStateProvider, 恢复出的空表单会被用户当成
            // 正在编辑的课提交 → 重复加课。
            val courseId = entry.longArg("courseId")
            val editing = entry.boolArg("editing")
            val editingCourse = remember(editing, courseId, session.editingCourse?.id) {
                if (editing) session.editingCourse else null
            }
            AddCourseScreen(
                onBack = {
                    session.clearEditCourse()
                    if (!nav.popBackStack()) {
                        // 栈只有 MAIN,直接清课程态即可
                    }
                },
                onSaved = {
                    session.clearEditCourse()
                    if (!nav.popBackStack()) { /* 同上 */ }
                    setCurrentTab(Tab.Schedule)
                },
                editingCourse = editingCourse,
            )
        }

        // ----------------------------------------------------------------
        composable(Routes.ALL_TABLES) {
            AllTablesScreen(
                onBack = { nav.popBackStack() },
                onCreateNewTable = onCreateNewTable,
                onOpenEditTable = { tableId ->
                    navigator.openEditTable(tableId = tableId)
                },
            )
        }

        // ----------------------------------------------------------------
        composable(Routes.EDIT_TABLE, arguments = editTableArgs) { entry ->
            val routeTableId = entry.longArg("tableId")
            val routePendingNew = entry.longArg("pendingNew")
            val routePrevDefault = entry.longArg("prevDefault")

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
                nav.popBackStack()
            }

            EditTableScreen(
                tableId = routeTableId,
                pendingNewTableId = pending,
                onBack = { nav.popBackStack() },
                onDiscardPending = {
                    val discardId = pending; val fallback = prevDefault
                    pending = null; prevDefault = null
                    if (discardId != null) mainVm.discardNewTable(discardId, fallback)
                    nav.popBackStack()
                },
                onSaved = {
                    pending = null; prevDefault = null
                    nav.popBackStack()
                },
                onDeleted = {
                    pending = null; prevDefault = null
                    nav.popBackStack()
                    setCurrentTab(Tab.Schedule)
                },
            )
        }

        // ----------------------------------------------------------------
        composable(Routes.APPEARANCE) {
            AppearanceScreen(
                onBack = { nav.popBackStack() },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }

        // ----------------------------------------------------------------
        composable(Routes.GENERAL) {
            GeneralSettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenHoliday = { navigator.openHoliday() },
                onOpenWidgetManagement = { navigator.openWidgetManagement() },
                navDock = navDock,
                onNavDockChange = onNavDockChange,
            )
        }

        composable(Routes.HOLIDAY) {
            HolidaySettingsScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.EXPORT) {
            ExportScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.REMINDER) {
            ReminderScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { nav.popBackStack() },
                onOpenLicense = { navigator.openLicense() },
                updateNoticeVisible = updateNoticeVisible,
            )
        }

        composable(Routes.LICENSE) {
            LicenseScreen(onBack = { nav.popBackStack() })
        }

        // ----------------------------------------------------------------
        composable(Routes.WIDGET_MANAGEMENT) {
            WidgetManagementScreen(
                onBack = { nav.popBackStack() },
                onSelect = { widgetId -> navigator.openWidgetEdit(widgetId) },
            )
        }

        composable(Routes.WIDGET_EDIT, arguments = widgetEditArgs) { entry ->
            WidgetEditScreen(
                widgetId = entry.intArg("widgetId"),
                onBack = { nav.popBackStack() },
            )
        }

        // ----------------------------------------------------------------
        composable(Routes.PERIOD_TABLES) {
            PeriodTablesScreen(
                onBack = { nav.popBackStack() },
                onOpenEdit = { periodId -> navigator.openPeriodEdit(periodId, isNew = false) },
                onCreateNew = { newId -> navigator.createPeriodTableAndEdit(newId) },
            )
        }

        composable(Routes.PERIOD_EDIT, arguments = periodEditArgs) { entry ->
            val periodId = entry.longArg("periodId") ?: NavSession.NO_ID
            val routeIsNew = entry.boolArg("isNew")
            // 镜像 PeriodTableEditScreen 的 unsavedNew 模式:用户保存/弃表后清掉,
            // 系统的返回兜底据此决定是否弃残留行。
            var unsavedNew by rememberSaveable { mutableStateOf(routeIsNew) }

            BackHandler(enabled = unsavedNew) {
                unsavedNew = false
                mainVm.discardNewPeriodTable(periodId)
                nav.popBackStack()
            }

            PeriodTableEditScreen(
                periodTableId = periodId,
                isNewUnsaved = unsavedNew,
                onBack = {
                    unsavedNew = false
                    nav.popBackStack()
                },
            )
        }
    }
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
    mainScope: CoroutineScope,
    navDock: Boolean,
    onNavDockChange: (Boolean) -> Unit,
    scheduleViewMode: ViewMode,
    onScheduleViewModeChange: (ViewMode) -> Unit,
    onCreateNewTable: () -> Unit,
    updateNoticeVisible: Boolean,
) {
    val navItems = Tab.entries.map { PillNavItemSpec(it.icon, stringResource(it.labelRes), badge = updateNoticeVisible && it == Tab.Mine) }
    val holder: SaveableStateHolder = rememberSaveableStateHolder()
    val nav = navigator.navController

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
    // 官方 NavigationBar/NavigationRail 有 → 贴底用官方 NavigationBar。
    // 官方没有悬浮药丸 Dock → Compact 且 navDock=true 时保留自研 PillNavigationBar(dock=true)。
    val activity = ctxForExit as? android.app.Activity
    val sizeClass = activity?.let { calculateWindowSizeClass(it) }
    val isCompact = sizeClass == null || sizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    if (!isCompact) {
        // ③ 中/大屏: 官方 NavigationRail + 主内容 Row
        Row(modifier = Modifier.fillMaxSize().background(SleepyTheme.colors.background)) {
            NavigationRail {
                Tab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = currentTab == tab,
                        onClick = { setCurrentTab(tab) },
                        icon = { NavigationTabIcon(tab, showUpdateDot = updateNoticeVisible && tab == Tab.Mine) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
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
                .background(SleepyTheme.colors.background)
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
                )
            }
        }
    } else {
        // Compact + 贴底: 官方有 NavigationBar → 用官方(替自研贴底形态)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = SleepyTheme.colors.background,
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
    val colors = SleepyTheme.colors
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
