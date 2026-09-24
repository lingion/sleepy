package com.lingion.sleepy

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import com.lingion.sleepy.ui.nav.NavSession
import com.lingion.sleepy.ui.nav.rememberSleepyNavigator
import com.lingion.sleepy.ui.nav.SleepyNavHost
import com.lingion.sleepy.ui.nav.SleepyRoute
import com.lingion.sleepy.ui.nav.SleepyNavigator
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.screen.schedule.ViewMode
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.jw.JwImportDraftCodec
import com.lingion.sleepy.ui.screen.imports.ImportDraft
import com.lingion.sleepy.ui.screen.imports.JwImportActivity
import com.lingion.sleepy.ui.screen.edit.AddCourseScreen
import com.lingion.sleepy.ui.component.NavDockSpec
import com.lingion.sleepy.ui.component.PillNavigationBar
import com.lingion.sleepy.ui.component.PillBarState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import com.lingion.sleepy.ui.component.PillNavItemSpec
import com.lingion.sleepy.ui.screen.manage.ManagementPage
import com.lingion.sleepy.ui.screen.widget.WidgetManagementScreen
import com.lingion.sleepy.ui.screen.widget.WidgetEditScreen
import com.lingion.sleepy.ui.screen.mine.AllTablesScreen
import com.lingion.sleepy.ui.screen.mine.AppearanceScreen
import com.lingion.sleepy.ui.screen.mine.MineScreen
import com.lingion.sleepy.ui.screen.mine.EditTableScreen
import com.lingion.sleepy.ui.screen.mine.GeneralSettingsScreen
import com.lingion.sleepy.ui.screen.mine.HolidaySettingsScreen
import com.lingion.sleepy.ui.screen.mine.ExportScreen
import com.lingion.sleepy.ui.screen.mine.ReminderScreen
import com.lingion.sleepy.ui.screen.mine.AboutScreen
import com.lingion.sleepy.ui.screen.mine.LicenseScreen
import com.lingion.sleepy.data.CustomThemeStore
import com.lingion.sleepy.ui.screen.schedule.ScheduleScreen
import com.lingion.sleepy.ui.screen.today.TodayScreen
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.lingion.sleepy.util.LocaleHelper.wrapDefault(newBase))
    }

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"
        fun intentForCourse(context: Context, courseId: Long): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_COURSE_ID, courseId)
            }
        }
        val pendingImportTextState: androidx.compose.runtime.MutableState<String?> =
            androidx.compose.runtime.mutableStateOf(null)
        @Volatile var incomingImportText: String? = null
        var pendingImportText: String?
            get() = pendingImportTextState.value
            set(v) { pendingImportTextState.value = v }
        // 无表空态 → "导入第一张课表" 引导: 切管理页时自动弹 ImportSheet 一次。
        // 会话级一次性 flag (组合态可读), 消费即清 — 避免下次进管理页误弹。
        val autoShowImportOnceState: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(false)
    }

    private val editingCourseFromIntent = MutableStateFlow<CourseEntity?>(null)
    val editingCourseFlow: StateFlow<CourseEntity?> = editingCourseFromIntent.asStateFlow()

    // systemDark 变化信号: configChanges="uiMode" 不重建 Activity, Compose 的
    // isSystemInDarkTheme() 不会自行 recomposition。覆盖 onConfigurationChanged,
    // 把最新 uiMode 推入此 State 触发重组 — dark 即随 systemDark 实时重算。
    // 初始值在 onCreate 赋(取当前配置, 避免冷启时闪一次) — 属性初始化器读
    // resources 会在构造函数阶段执行, 此时 attachBaseContext 未调, resources
    // 访问 NPE → 启动秒崩(v1.0.55 测试包翻车点)。
    private val uiNightModeState: androidx.compose.runtime.MutableState<Int> =
        androidx.compose.runtime.mutableStateOf(Configuration.UI_MODE_NIGHT_UNDEFINED)

    override fun onPostResume() {
        super.onPostResume()
        // Keep the splash logo out of system window snapshots after the first frame.
        androidx.core.view.OneShotPreDrawListener.add(window.decorView) {
            window.setBackgroundDrawable(
                ColorDrawable(getColor(com.lingion.sleepy.R.color.splash_background))
            )
            true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        uiNightModeState.value = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // uiNightModeState 初始值: attachBaseContext 已完成, resources 可安全访问
        uiNightModeState.value =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        com.lingion.sleepy.util.UpdateManager.cleanOldApk(this)
        enableEdgeToEdge()
        // 高刷新率(流畅优先): 按开关把窗口钉到屏幕最高刷率, 不表态会被省电逻辑限 60Hz
        com.lingion.sleepy.util.HighRefreshRate.apply(this, com.lingion.sleepy.util.AppPrefs.isHighRefresh(this))
        handleDeepLinkIntent(intent)
        // 启动时检查更新: 用户可在「关于」最底 Toggle 关闭
        com.lingion.sleepy.util.UpdateNotifier.loadDismissedVersion(this)
        com.lingion.sleepy.util.UpdateNotifier.maybeCheckOnStart(this, lifecycleScope)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("mock_update", false)) {
            com.lingion.sleepy.util.UpdateNotifier.showMockUpdate()
        }
        setContent {
            // uiNightModeState.value 变化(composition-observed) → systemDark 重算 →
            // dirty 指派给 remember(systemDark) 触发 dark 重算; 此前 isSystemInDarkTheme()
            // 在 configChanges="uiMode" 场景下不会 recomposition, dark 冻结在首帧值。
            val systemDark = (uiNightModeState.value == Configuration.UI_MODE_NIGHT_YES)
            var themeMode by remember { mutableStateOf(AppPrefs.getThemeMode(this@MainActivity)) }
            var dark by remember(systemDark) { mutableStateOf(AppPrefs.isDarkMode(this@MainActivity, systemDark)) }
            fun applyTheme() { dark = AppPrefs.isDarkMode(this@MainActivity, systemDark) }
            val deepLinkCourse by editingCourseFlow.collectAsState()
            val themeKey by AppPrefs.themeKeyFlow(this@MainActivity).collectAsState(initial = AppPrefs.getThemeKey(this@MainActivity))
            // The selected custom theme can be edited in place, so its key does not change.
            // Subscribe to the custom-theme document as a separate invalidation signal.
            val customThemesJson by CustomThemeStore.changes(this@MainActivity)
                .collectAsState(initial = "")
            SleepyThemeProvider(
                darkTheme = dark,
                themeKey = themeKey,
                customThemeVersion = customThemesJson
            ) {
                AppRoot(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        AppPrefs.setThemeMode(this@MainActivity, mode)
                        themeMode = mode
                        applyTheme()
                        // 手动切主题时联动刷新 widget(广播 APPWIDGET_UPDATE)
                        lifecycleScope.launch {
                            com.lingion.sleepy.widget.WidgetUpdater.notifyDataChanged(this@MainActivity)
                        }
                    },
                    deepLinkCourse = deepLinkCourse,
                    onDeepLinkConsumed = { editingCourseFromIntent.value = null },
                    pendingImportText = pendingImportText,
                    consumePendingImportText = { MainActivity.pendingImportText = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val importText = intent?.getStringExtra(
            com.lingion.sleepy.ui.screen.imports.ImportReceiverActivity.EXTRA_IMPORT_TEXT
        ) ?: com.lingion.sleepy.MainActivity.incomingImportText
        if (!importText.isNullOrBlank()) {
            com.lingion.sleepy.MainActivity.pendingImportText = importText
            com.lingion.sleepy.MainActivity.incomingImportText = null
            intent?.removeExtra(com.lingion.sleepy.ui.screen.imports.ImportReceiverActivity.EXTRA_IMPORT_TEXT)
        }
        val courseId = intent?.getLongExtra(EXTRA_COURSE_ID, -1L) ?: -1L
        if (courseId <= 0) return
        if (editingCourseFromIntent.value?.id == courseId) return
        lifecycleScope.launch {
            try {
                val course = (application as SleepyApp).repository.getCourse(courseId)
                editingCourseFromIntent.value = course
            } catch (e: Throwable) {
                android.util.Log.e("Sleepy", "deep link course lookup failed", e)
            }
        }
    }
}

internal enum class Tab(val labelRes: Int, val icon: ImageVector) {
    Schedule(R.string.tab_schedule, Icons.Outlined.CalendarMonth),
    Today(R.string.tab_today, Icons.Outlined.Today),
    Manage(R.string.tab_manage, Icons.Outlined.Settings),
    Mine(R.string.tab_mine, Icons.Outlined.Person)
}

@Composable
private fun AppRoot(
    themeMode: String = AppPrefs.THEME_MODE_SYSTEM,
    onThemeModeChange: (String) -> Unit = {},
    deepLinkCourse: CourseEntity? = null,
    onDeepLinkConsumed: () -> Unit = {},
    pendingImportText: String? = null,
    consumePendingImportText: () -> Unit = {}
) {
    // issue#45: 自研 Overlay 栈 → Navigation Compose。
    // AppRoot 只持「不属于任何路由的会话态」: 当前 tab / 底栏形态 / 课表视图模式。
    // 导航栈、页面状态保存、返回手势全部交给 NavHost(见 SleepyNavHost.kt)。
    var currentTab by rememberSaveable { mutableStateOf(Tab.Schedule) }
    val context = LocalContext.current
    // 课表视图模式(周视图/网格) — 会话级,与 currentTab 同级持有:
    // overlay 与 tab 切换都会整页移除 ScheduleScreen,状态必须提升到这层才存活。
    var scheduleViewMode by remember {
        mutableStateOf(
            if (AppPrefs.getStartView(context) == "cards") ViewMode.Cards else ViewMode.Full
        )
    }
    var navDock by remember { mutableStateOf(AppPrefs.isNavDock(context)) }
    val mainScope = rememberCoroutineScope()
    val mainVm: ScheduleViewModel = viewModel()
    // composition 内读 StateFlow.value 会被 lint(StateFlowValueCalledInComposition)拦:
    // 快照值不随 flow 更新重组。改订阅, holiday 设置页拿到的 tableId 恒为当前值。
    val mainState by mainVm.state.collectAsState()
    val navigator = rememberSleepyNavigator()
    val nav = navigator.backStack
    // 底栏 thumb 状态提升到 NavDisplay 之外: entry<Main> 在 push 子页时会被销毁,
    // pop 返回时高亮若随 entry 重建,首帧会闪现在课表 tab 再挪回目标 tab
    // (2026-09-21 用户报障)。放这层后 pop 重建首帧即正确。
    val pillBarState = remember { PillBarState() }

    // 外部导入文本 → 切管理页(与旧实现等价,语义不变)。
    var autoImportTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(pendingImportText) {
        if (!autoImportTriggered && pendingImportText != null) {
            autoImportTriggered = true
            currentTab = Tab.Manage
        }
    }

    SleepyNavHost(
        nav = nav,
        navigator = navigator,
        currentTab = currentTab,
        setCurrentTab = { currentTab = it },
        navDock = navDock,
        onNavDockChange = { navDock = it },
        scheduleViewMode = scheduleViewMode,
        onScheduleViewModeChange = { scheduleViewMode = it },
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        deepLinkCourse = deepLinkCourse,
        onDeepLinkConsumed = onDeepLinkConsumed,
        mainVm = mainVm,
        currentTableId = mainState.currentTable?.id,
        mainScope = mainScope,
        onCreateNewTable = {
            mainScope.launch {
                val previousId = mainVm.state.value.currentTable?.id ?: NavSession.NO_ID
                val newId = mainVm.createEmptyTable(commitSelection = false)
                navigator.openEditTable(tableId = newId, pendingNew = newId, prevDefault = previousId)
            }
        },
        pillBarState = pillBarState,
    )
}

@Composable
internal fun MainTabs(
    currentTab: Tab,
    setCurrentTab: (Tab) -> Unit,
    navigator: SleepyNavigator,
    mainVm: ScheduleViewModel,
    mainScope: CoroutineScope,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onCreateNewTable: () -> Unit,
    holder: SaveableStateHolder,
    updateNoticeVisible: Boolean = false
) {
    // tab 往返滚动位置保真: when 条件组合同样整页移除被切走的 tab, 各 tab 内容包
    // SaveableStateProvider(currentTab.name) — key 稳定(tab 枚举名), 返回时恢复。
    // 注意: scheduleViewMode 会话态仍由 AppRoot 持有(§1.4 契约), 此处只管组合作用域。
    val session = navigator.session
    val draftScope = rememberCoroutineScope()
    when (currentTab) {
        Tab.Schedule -> holder.SaveableStateProvider(currentTab.name) {
            ScheduleScreen(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                onGoImport = { MainActivity.autoShowImportOnceState.value = true; setCurrentTab(Tab.Manage) },
                onManualAdd = { navigator.openAddCourse() },
                onCreateTable = onCreateNewTable,
                onEditCourse = { course -> session.beginEditCourse(course); navigator.openAddCourse(course.id, editing = true) })
        }
        Tab.Today -> holder.SaveableStateProvider(currentTab.name) {
            TodayScreen(onEditCourse = { course -> session.beginEditCourse(course); navigator.openAddCourse(course.id, editing = true) })
        }
        Tab.Manage -> holder.SaveableStateProvider(currentTab.name) {
            val ctx = LocalContext.current
            val importCoursesLabel = stringResource(com.lingion.sleepy.R.string.import_courses)
            // 空态导入引导: autoShowImportOnce 置位过 → 本次进管理页自动弹 ImportSheet, 随即消费清零。
            // pendingImportText != null 是另一路 (外部 app 分享课表文本进来) 的既有自动弹层, 语义不同并存。
            val autoOnce = MainActivity.autoShowImportOnceState.value
            if (autoOnce) MainActivity.autoShowImportOnceState.value = false
            val draftEntities by SleepyApp.get().importDraftRepository.observeAll().collectAsState(initial = emptyList())
            val drafts = draftEntities.mapNotNull { entity ->
                val snapshot = JwImportDraftCodec.fromJson(entity.payloadJson) ?: return@mapNotNull null
                ImportDraft(
                    id = entity.id,
                    name = snapshot.tableName.ifBlank { snapshot.school.name },
                    details = "${snapshot.courses.size} $importCoursesLabel",
                )
            }
            ManagementPage(autoShowImportSheet = autoOnce || MainActivity.pendingImportText != null, onJwImportRequested = { ctx.startActivity(Intent(ctx, com.lingion.sleepy.ui.screen.imports.JwImportActivity::class.java)) }, onCreateNewTableRequested = onCreateNewTable,
                // v1.0.56 T7: 新建作息表卡 — ManagementPage 内部建表(自动唯一命名)后回调带新 id,
                // 与 PeriodTablesScreen 新建按钮同一套 pendingNew discard 残留语义
                onCreateNewPeriodTableRequested = { newId -> navigator.createPeriodTableAndEdit(newId) },
                onManualAdd = { navigator.openAddCourse() }, onEditCurrentTable = { navigator.openEditTable() }, onExportRequested = { navigator.openExport() },
                onOpenAllTables = { navigator.openAllTables() },
                drafts = drafts,
                onRestoreDraft = { id ->
                    ctx.startActivity(Intent(ctx, JwImportActivity::class.java).putExtra(JwImportActivity.EXTRA_DRAFT_ID, id))
                },
                onDeleteDraft = { id ->
                    draftScope.launch { SleepyApp.get().importDraftRepository.delete(id) }
                },
                // v7.10.16w 用户 2026-09-10: 导入完成留在管理页 — 此前硬跳课表页(周/网格),
                // 打断"复制副本→追加导入→继续操作"的管理动线。当前课表摘要卡就地刷新可见。
                onImported = { /* 留在管理页, 摘要卡就地刷新 */ })
        }
        Tab.Mine -> holder.SaveableStateProvider(currentTab.name) {
            MineScreen(
                onOpenAllTables = { navigator.openAllTables() },
                onOpenCourseList = { navigator.openCourseList() },
                onOpenPeriodTables = { navigator.openPeriodTables() },
                onOpenAppearance = { navigator.openAppearance() },
                onOpenGeneral = { navigator.openGeneral() },
                onOpenExport = { navigator.openExport() },
                onOpenReminder = { navigator.openReminder() },
                onOpenAbout = { navigator.openAbout() },
                updateNoticeVisible = updateNoticeVisible)
        }
    }
}
