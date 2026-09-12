package com.lingion.sleepy

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.lingion.sleepy.ui.screen.edit.AddCourseScreen
import com.lingion.sleepy.ui.component.NavDockSpec
import com.lingion.sleepy.ui.component.PillNavigationBar
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
    // 初始值取当前配置, 避免冷启时闪一次。
    // ponytail: MutableStateOf<Int> + onConfigurationChanged is the standard
    // pattern for theme changes under configChanges="uiMode"; no per-account lock needed.
    private val uiNightModeState = mutableStateOf(
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    )

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        uiNightModeState.value = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        com.lingion.sleepy.util.UpdateManager.cleanOldApk(this)
        enableEdgeToEdge()
        // 高刷新率(流畅优先): 按开关把窗口钉到屏幕最高刷率, 不表态会被省电逻辑限 60Hz
        com.lingion.sleepy.util.HighRefreshRate.apply(this, com.lingion.sleepy.util.AppPrefs.isHighRefresh(this))
        handleDeepLinkIntent(intent)
        // 启动时检查更新: 用户可在「关于」最底 Toggle 关闭
        com.lingion.sleepy.util.UpdateNotifier.maybeCheckOnStart(this, lifecycleScope)
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
            SleepyThemeProvider(darkTheme = dark, themeKey = themeKey) {
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

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    Schedule(R.string.tab_schedule, Icons.Outlined.CalendarMonth),
    Today(R.string.tab_today, Icons.Outlined.Today),
    Manage(R.string.tab_manage, Icons.Outlined.Settings),
    Mine(R.string.tab_mine, Icons.Outlined.Person)
}

private enum class OverlayScreen {
    AddCourse, AllTables, EditTable, Theme, General, Holiday, Export, Reminder, About, License, WidgetManagement, WidgetEdit
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
    var currentTab by remember { mutableStateOf(Tab.Schedule) }
    var editingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    // v7.10.8 返回键分层修复: overlayScreen 从单变量改成导航栈 —
    // 旧实现一个 BackHandler 把整摞 overlay 一次清空(通用设置→假期设置 按一次返回
    // 直接退两级); 栈化后每层只弹自己(通用→假期 返回 只回通用)。
    // 栈顶 = 当前显示页。pushOverlay 进页, popOverlay 退页。
    // 语言切换触发 Activity.recreate() 后仍需保留栈(旧注释决策 D2 同理),
    // editingCourse(CourseEntity)无法 Bundle 化: 编辑课程会话中不保存栈,
    //   旋转/进程恢复后退回主 Tab(丢弃编辑但安全), 避免恢复成"新增课程"空表单造成重复加课。
    val overlayScreenState = rememberSaveable(
        stateSaver = Saver<List<OverlayScreen>, List<OverlayScreen>>(
            save = { stack -> if (editingCourse == null) stack else emptyList() },
            restore = { it }
        )
    ) { mutableStateOf<List<OverlayScreen>>(emptyList()) }
    var overlayStack by overlayScreenState
    fun topOverlay(): OverlayScreen? = overlayStack.lastOrNull()
    fun pushOverlay(s: OverlayScreen) { overlayStack = overlayStack + s }
    fun popOverlay() { overlayStack = overlayStack.dropLast(1) }
    fun popToRoot() { overlayStack = emptyList() }
    fun hasOverlay(): Boolean = overlayStack.isNotEmpty()
    // overlayScreen 的伴生导航参数必须同步持久化, 否则旋转恢复后 overlay 存活但参数归 null:
    //   EditTable 的 tableId=null 语义为"编辑当前课表", 会静默改错表; pendingNewTableId 丢失
    //   会让新建空表遗留在 DB 且误显示删除按钮。三者均可 Bundle 化(Long?), 一并 rememberSaveable。
    var editTableId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingNewTableId by rememberSaveable { mutableStateOf<Long?>(null) }
    var previousDefaultTableId by rememberSaveable { mutableStateOf<Long?>(null) }
    var widgetEditId by rememberSaveable { mutableStateOf<Int?>(null) }
    var autoImportTriggered by remember { mutableStateOf(false) }
    // 底栏形态(贴底/悬浮 Dock): AppRoot 持真值 — 设置页改, 底栏即时切
    val context = LocalContext.current
    // 课表视图模式(周视图/网格) — 会话级状态, 与 currentTab 同级持有:
    // overlay(加课/编辑课程)与 tab 切换都会整页移除 ScheduleScreen, 状态必须提升到这层才存活。
    // 初始化只读启动默认(KEY_START_VIEW), 手动切换仅写这里不回写 AppPrefs —
    // 启动默认与会话内切换分离(AppPrefs.kt KEY_START_VIEW 注释的既有设计)。
    var scheduleViewMode by remember {
        mutableStateOf(
            if (AppPrefs.getStartView(context) == "cards") ViewMode.Cards else ViewMode.Full
        )
    }
    var navDock by remember { mutableStateOf(AppPrefs.isNavDock(context)) }
    val mainScope = rememberCoroutineScope()
    val mainVm: ScheduleViewModel = viewModel()
    // 返回恢复精确页面状态: 条件组合(if (topOverlay()==X) {...; return})使被覆盖页整体
    // 离开组合树, remember/rememberSaveable 状态销毁 — 通用设置二级页往返滚动归零/折叠卡
    // 全收起、tab 往返丢滚动位置都是这个根因。每个 overlay/tab 分支内容包进独立 key 的
    // SaveableStateProvider, 页面被覆盖时状态存进 holder(rememberSaveable 作用域), 返回时
    // 原样恢复(滚动位置/折叠展开/输入)。key 用稳定字符串, 禁用组合位置 key(会互相踩)。
    val saveableStateHolder: SaveableStateHolder = rememberSaveableStateHolder()

    androidx.compose.runtime.LaunchedEffect(deepLinkCourse?.id) {
        if (deepLinkCourse != null) { editingCourse = deepLinkCourse; onDeepLinkConsumed() }
    }
    androidx.compose.runtime.LaunchedEffect(pendingImportText) {
        if (!autoImportTriggered && pendingImportText != null) { autoImportTriggered = true; currentTab = Tab.Manage }
    }

    // 返回键: 只处理"有 overlay 在栈上"或"编辑课程"两种拦截; 主页面留给双击退出
    // (下方 exitBackHandler — enabled 互斥, 栈空时才接管)。
    BackHandler(enabled = hasOverlay() || editingCourse != null) {
        if (pendingNewTableId != null) {
            val discardId = pendingNewTableId!!; val fallback = previousDefaultTableId
            pendingNewTableId = null; previousDefaultTableId = null
            mainVm.discardNewTable(discardId, fallback)
            popToRoot(); editTableId = null
        } else { editingCourse = null; editTableId = null; popOverlay() }
    }

    // v7.10.8 主页面双击返回退出 — 第一次按 Toast 提示, 2 秒内再按才真退。
    // enabled 条件与上面互斥: 栈空且无编辑会话时才接管。
    // v7.10.9: 课表页 = 首页 — 其他 Tab(今日/管理/我的)按返回先回课表页,
    // 只有课表页本身才触发双击退出(用户 2026-09-02)。
    val ctxForExit = LocalContext.current
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler(enabled = !hasOverlay() && editingCourse == null && currentTab != Tab.Schedule) {
        currentTab = Tab.Schedule
    }
    BackHandler(enabled = !hasOverlay() && editingCourse == null && currentTab == Tab.Schedule) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackAt < 2000L) {
            (ctxForExit as? android.app.Activity)?.finish()
        } else {
            lastBackAt = now
            android.widget.Toast.makeText(
                ctxForExit, R.string.exit_press_back_again, Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 例外(§1.3): AddCourse(编辑/新增课程会话)不纳入 SaveableStateProvider —
    // 编辑课程会话在旋转/进程恢复时安全丢弃是有意设计(overlayStack saver 同款例外):
    // CourseEntity 无法 Bundle 化, 若恢复进空表单, 用户会误当成新课程重复添加。
    // 故此分支保持裸组合, 表单状态随覆盖销毁。
    if (topOverlay() == OverlayScreen.AddCourse || editingCourse != null) {
        AddCourseScreen(onBack = { popOverlay(); editingCourse = null }, onSaved = { popOverlay(); editingCourse = null; currentTab = Tab.Schedule }, editingCourse = editingCourse)
        return
    }
    if (topOverlay() == OverlayScreen.AllTables) {
        saveableStateHolder.SaveableStateProvider("AllTables") {
            AllTablesScreen(onBack = { popOverlay() }, onCreateNewTable = {
                mainScope.launch {
                    val previousId = mainVm.state.value.currentTable?.id
                    val newId = mainVm.createEmptyTable(commitSelection = false)
                    previousDefaultTableId = previousId; pendingNewTableId = newId; editTableId = newId; pushOverlay(OverlayScreen.EditTable)
                }
            }, onOpenEditTable = { tableId -> editTableId = tableId; pendingNewTableId = null; pushOverlay(OverlayScreen.EditTable) })
        }
        return
    }
    if (topOverlay() == OverlayScreen.EditTable) {
        saveableStateHolder.SaveableStateProvider("EditTable") {
            EditTableScreen(tableId = editTableId, pendingNewTableId = pendingNewTableId, onBack = { popOverlay(); editTableId = null; pendingNewTableId = null; previousDefaultTableId = null }, onDiscardPending = {
                val discardId = pendingNewTableId; val fallback = previousDefaultTableId; pendingNewTableId = null; previousDefaultTableId = null
                if (discardId != null) mainVm.discardNewTable(discardId, fallback)
                popOverlay(); editTableId = null
            }, onSaved = { popOverlay(); editTableId = null; pendingNewTableId = null; previousDefaultTableId = null }, onDeleted = { popOverlay(); editTableId = null; currentTab = Tab.Schedule })
        }
        return
    }
    if (topOverlay() == OverlayScreen.Theme) {
        saveableStateHolder.SaveableStateProvider("Theme") {
            AppearanceScreen(onBack = { popOverlay() }, themeMode = themeMode, onThemeModeChange = onThemeModeChange)
        }
        return
    }
    if (topOverlay() == OverlayScreen.General) {
        saveableStateHolder.SaveableStateProvider("General") {
            GeneralSettingsScreen(
                onBack = { popOverlay() },
                onOpenHoliday = { pushOverlay(OverlayScreen.Holiday) },
                onOpenWidgetManagement = { pushOverlay(OverlayScreen.WidgetManagement) },
                navDock = navDock,
                onNavDockChange = { navDock = it }
            )
        }
        return
    }
    if (topOverlay() == OverlayScreen.Holiday) {
        saveableStateHolder.SaveableStateProvider("Holiday") {
            HolidaySettingsScreen(onBack = { popOverlay() })
        }
        return
    }
    if (topOverlay() == OverlayScreen.Export) {
        saveableStateHolder.SaveableStateProvider("Export") {
            ExportScreen(onBack = { popOverlay() })
        }
        return
    }
    if (topOverlay() == OverlayScreen.Reminder) {
        saveableStateHolder.SaveableStateProvider("Reminder") {
            ReminderScreen(onBack = { popOverlay() })
        }
        return
    }
    if (topOverlay() == OverlayScreen.About) {
        saveableStateHolder.SaveableStateProvider("About") {
            AboutScreen(onBack = { popOverlay() }, onOpenLicense = { pushOverlay(OverlayScreen.License) })
        }
        return
    }
    if (topOverlay() == OverlayScreen.License) {
        saveableStateHolder.SaveableStateProvider("License") {
            LicenseScreen(onBack = { popOverlay() })
        }
        return
    }
    // widgetEditId 持久化(Int): 旋转/进程恢复后仍能定位具体 widget —
    // Int 可 Bundle 化, 与上面 editTableId/pendingNewTableId 同款处理。
    if (topOverlay() == OverlayScreen.WidgetManagement) {
        saveableStateHolder.SaveableStateProvider("WidgetManagement") {
            WidgetManagementScreen(
                onBack = { popOverlay() },
                onSelect = { widgetId -> widgetEditId = widgetId; pushOverlay(OverlayScreen.WidgetEdit) }
            )
        }
        return
    }
    if (topOverlay() == OverlayScreen.WidgetEdit) {
        saveableStateHolder.SaveableStateProvider("WidgetEdit") {
            WidgetEditScreen(
                widgetId = widgetEditId ?: -1,
                onBack = { popOverlay(); widgetEditId = null }
            )
        }
        return
    }

    // 底栏双形态(用户 2026-09-04 定版):
    // 贴底 = Scaffold bottomBar 占位(原样, 内容止于栏上沿);
    // Dock = iOS/Mac 语义悬浮药丸 — 内容 fillMaxSize 通到屏幕底, Dock 悬浮于内容
    // 上一层(FAB 式 overlay), 各页滚动容器经 LocalNavExtraBottomPadding 拿 Dock 总高
    // 加滚动余量, 保证最后一项能滚到 Dock 上方完全可见。
    val navItems = Tab.entries.map { com.lingion.sleepy.ui.component.PillNavItemSpec(it.icon, stringResource(it.labelRes)) }

    // Dock 滚动余量: 理论估算兜底(首帧前), overlay 实测高(dockOverlayPx)到位后覆盖 —
    // 猜值必小于真值(手势条 inset 因机型而异), 实测保证「最后一项能滚到 Dock 上方」
    var dockExtraDp by remember { mutableStateOf(NavDockSpec.capsuleHeight + NavDockSpec.bottomFloat) }

    if (!navDock) {
        androidx.compose.material3.Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = SleepyTheme.colors.background,
            bottomBar = {
                PillNavigationBar(
                    items = navItems,
                    selectedIndex = currentTab.ordinal,
                    onSelect = { currentTab = Tab.entries[it] },
                    dock = false
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                MainTabs(
                    currentTab = currentTab,
                    setCurrentTab = { currentTab = it },
                    pushOverlay = ::pushOverlay,
                    editingCourse = { editingCourse = it },
                    viewMode = scheduleViewMode,
                    onViewModeChange = { scheduleViewMode = it },
                    onCreateNewTable = {
                        mainScope.launch {
                            val previousId = mainVm.state.value.currentTable?.id
                            val newId = mainVm.createEmptyTable(commitSelection = false)
                            previousDefaultTableId = previousId; pendingNewTableId = newId; editTableId = newId; pushOverlay(OverlayScreen.EditTable)
                        }
                    },
                    holder = saveableStateHolder
                )
            }
        }
    } else {
        // Dock 模式: 无 bottomBar 占位 — 内容通底; Dock 悬浮层 Align.BottomCenter 叠加
        // 顶部: 裸 Box 没有 Scaffold 的 contentWindowInsets, 必须显式补 statusBars inset
        // (此前丢失 → 课表顶栏顶进摄像头挖孔区); 底部不加 — 内容延伸到最底是 Dock 语义
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SleepyTheme.colors.background)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.lingion.sleepy.ui.component.LocalNavExtraBottomPadding provides dockExtraDp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainTabs(
                        currentTab = currentTab,
                        setCurrentTab = { currentTab = it },
                        pushOverlay = ::pushOverlay,
                        editingCourse = { editingCourse = it },
                        viewMode = scheduleViewMode,
                        onViewModeChange = { scheduleViewMode = it },
                        onCreateNewTable = {
                            mainScope.launch {
                                val previousId = mainVm.state.value.currentTable?.id
                                val newId = mainVm.createEmptyTable(commitSelection = false)
                                previousDefaultTableId = previousId; pendingNewTableId = newId; editTableId = newId; pushOverlay(OverlayScreen.EditTable)
                            }
                        },
                        holder = saveableStateHolder
                    )
                }
            }
            var dockOverlayPx by remember { mutableStateOf(0) }
            val densityForDock = LocalDensity.current
            if (dockOverlayPx > 0) {
                dockExtraDp = with(densityForDock) { dockOverlayPx.toDp() }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { c -> dockOverlayPx = c.size.height }
            ) {
                PillNavigationBar(
                    items = navItems,
                    selectedIndex = currentTab.ordinal,
                    onSelect = { currentTab = Tab.entries[it] },
                    dock = true
                )
            }
        }
    }
}

@Composable
private fun MainTabs(
    currentTab: Tab,
    setCurrentTab: (Tab) -> Unit,
    pushOverlay: (OverlayScreen) -> Unit,
    editingCourse: (CourseEntity?) -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onCreateNewTable: () -> Unit,
    holder: SaveableStateHolder
) {
    // tab 往返滚动位置保真: when 条件组合同样整页移除被切走的 tab, 各 tab 内容包
    // SaveableStateProvider(currentTab.name) — key 稳定(tab 枚举名), 返回时恢复。
    // 注意: scheduleViewMode 会话态仍由 AppRoot 持有(§1.4 契约), 此处只管组合作用域。
    when (currentTab) {
        Tab.Schedule -> holder.SaveableStateProvider(currentTab.name) {
            ScheduleScreen(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                onGoImport = { MainActivity.autoShowImportOnceState.value = true; setCurrentTab(Tab.Manage) },
                onManualAdd = { pushOverlay(OverlayScreen.AddCourse) },
                onCreateTable = onCreateNewTable,
                onEditCourse = { course -> editingCourse(course) })
        }
        Tab.Today -> holder.SaveableStateProvider(currentTab.name) {
            TodayScreen(onEditCourse = { course -> editingCourse(course) })
        }
        Tab.Manage -> holder.SaveableStateProvider(currentTab.name) {
            val ctx = LocalContext.current
            // 空态导入引导: autoShowImportOnce 置位过 → 本次进管理页自动弹 ImportSheet, 随即消费清零。
            // pendingImportText != null 是另一路 (外部 app 分享课表文本进来) 的既有自动弹层, 语义不同并存。
            val autoOnce = MainActivity.autoShowImportOnceState.value
            if (autoOnce) MainActivity.autoShowImportOnceState.value = false
            ManagementPage(autoShowImportSheet = autoOnce || MainActivity.pendingImportText != null, onJwImportRequested = { ctx.startActivity(Intent(ctx, com.lingion.sleepy.ui.screen.imports.JwImportActivity::class.java)) }, onCreateNewTableRequested = onCreateNewTable, onManualAdd = { pushOverlay(OverlayScreen.AddCourse) }, onEditCurrentTable = { pushOverlay(OverlayScreen.EditTable) }, onExportRequested = { pushOverlay(OverlayScreen.Export) },
                // v7.10.16w 用户 2026-09-10: 导入完成留在管理页 — 此前硬跳课表页(周/网格),
                // 打断"复制副本→追加导入→继续操作"的管理动线。当前课表摘要卡就地刷新可见。
                onImported = { /* 留在管理页, 摘要卡就地刷新 */ })
        }
        Tab.Mine -> holder.SaveableStateProvider(currentTab.name) {
            MineScreen(
                onOpenAllTables = { pushOverlay(OverlayScreen.AllTables) },
                onOpenAppearance = { pushOverlay(OverlayScreen.Theme) },
                onOpenGeneral = { pushOverlay(OverlayScreen.General) },
                onOpenExport = { pushOverlay(OverlayScreen.Export) },
                onOpenReminder = { pushOverlay(OverlayScreen.Reminder) },
                onOpenAbout = { pushOverlay(OverlayScreen.About) })
        }
    }
}
