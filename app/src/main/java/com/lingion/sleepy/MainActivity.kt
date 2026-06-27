package com.lingion.sleepy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.PillNavItem
import com.lingion.sleepy.ui.component.PillNavigationBar
import com.lingion.sleepy.ui.screen.edit.AddCourseScreen
import com.lingion.sleepy.ui.screen.manage.ManagementPage
import com.lingion.sleepy.ui.screen.mine.AllTablesScreen
import com.lingion.sleepy.ui.screen.mine.MineScreen
import com.lingion.sleepy.ui.screen.mine.MoreSettingsScreen
import com.lingion.sleepy.ui.screen.mine.EditTableScreen
import com.lingion.sleepy.ui.screen.mine.ThemeColorScreen
import com.lingion.sleepy.ui.screen.mine.ExportScreen
import com.lingion.sleepy.ui.screen.mine.ReminderScreen
import com.lingion.sleepy.ui.screen.mine.AboutScreen
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

        /**
         * 构造一个带 courseId 路由的 Intent，Glance 小组件用。
         * 启动后 MainActivity.onNewIntent 会拉课程并切到 AddCourseScreen 编辑模式。
         */
        fun intentForCourse(context: Context, courseId: Long): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_COURSE_ID, courseId)
            }
        }
    }

    /**
     * Activity 级别的编辑目标——小组件 deep link 用。
     * onNewIntent 读 EXTRA_COURSE_ID → 查 DB → setValue → 触发 Compose 重组 → 进 AddCourseScreen。
     */
    private val editingCourseFromIntent = MutableStateFlow<CourseEntity?>(null)
    val editingCourseFlow: StateFlow<CourseEntity?> = editingCourseFromIntent.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleDeepLinkIntent(intent)

        setContent {
            val dark = remember { mutableStateOf(AppPrefs.isDarkMode(this@MainActivity)) }
            val themeKey by AppPrefs.themeKeyFlow(this@MainActivity)
                .collectAsState(initial = AppPrefs.getThemeKey(this@MainActivity))
            val deepLinkCourse by editingCourseFlow.collectAsState()
            SleepyThemeProvider(darkTheme = dark.value, themeKey = themeKey) {
                AppRoot(
                    darkMode = dark.value,
                    onToggleDark = {
                        val v = !dark.value
                        AppPrefs.setDarkMode(this@MainActivity, v)
                        dark.value = v
                    },
                    deepLinkCourse = deepLinkCourse,
                    onDeepLinkConsumed = { editingCourseFromIntent.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    /**
     * 解析启动 / 新 Intent 里的 EXTRA_COURSE_ID，IO 线程查 DB 后 setValue。
     */
    private fun handleDeepLinkIntent(intent: Intent?) {
        val courseId = intent?.getLongExtra(EXTRA_COURSE_ID, -1L) ?: -1L
        if (courseId <= 0) return
        // 避免重复触发：仅当目标 courseId 不同时才查
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
    AddCourse,
    AllTables,
    EditTable,
    ThemeColor,
    MoreSettings,
    Export,
    Reminder,
    About
}

@Composable
private fun AppRoot(
    darkMode: Boolean = false,
    onToggleDark: () -> Unit = {},
    deepLinkCourse: CourseEntity? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(Tab.Schedule) }
    var editingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var overlayScreen by remember { mutableStateOf<OverlayScreen?>(null) }
    var editTableId by remember { mutableStateOf<Long?>(null) }
    // 新建未保存课表的临时 id：进入 EditTable 时若为非空，按返回会丢弃这张表（删除 + 回滚选中状态）
    var pendingNewTableId by remember { mutableStateOf<Long?>(null) }
    // 记录新建课表前的默认表 id，作为 discard 时的回退目标
    var previousDefaultTableId by remember { mutableStateOf<Long?>(null) }
    val mainScope = rememberCoroutineScope()
    val mainVm: ScheduleViewModel = viewModel()

    // 小组件 deep link 触发：拉到了课程 → 切到编辑模式
    androidx.compose.runtime.LaunchedEffect(deepLinkCourse?.id) {
        if (deepLinkCourse != null) {
            editingCourse = deepLinkCourse
            onDeepLinkConsumed()
        }
    }

    BackHandler(enabled = overlayScreen != null || editingCourse != null) {
        // pendingNewTableId 不为空时，Back 也走 discard 路径
        if (pendingNewTableId != null) {
            val discardId = pendingNewTableId!!
            val fallback = previousDefaultTableId
            pendingNewTableId = null
            previousDefaultTableId = null
            mainVm.discardNewTable(discardId, fallback)
            overlayScreen = null
            editTableId = null
        } else {
            overlayScreen = null
            editingCourse = null
            editTableId = null
        }
    }

    // ----- AddCourse -----
    if (overlayScreen == OverlayScreen.AddCourse || editingCourse != null) {
        AddCourseScreen(
            onBack = {
                overlayScreen = null
                editingCourse = null
            },
            onSaved = {
                overlayScreen = null
                editingCourse = null
                currentTab = Tab.Schedule
            },
            editingCourse = editingCourse
        )
        return
    }

    // ----- AllTables -----
    if (overlayScreen == OverlayScreen.AllTables) {
        AllTablesScreen(
            onBack = { overlayScreen = null },
            onOpenEditTable = { tableId ->
                editTableId = tableId
                pendingNewTableId = null  // 从 AllTables 进入的都不是新建
                overlayScreen = OverlayScreen.EditTable
            }
        )
        return
    }

    // ----- EditTable (unified) -----
    if (overlayScreen == OverlayScreen.EditTable) {
        EditTableScreen(
            tableId = editTableId,
            pendingNewTableId = pendingNewTableId,
            onBack = {
                overlayScreen = null
                editTableId = null
                pendingNewTableId = null
                previousDefaultTableId = null
            },
            onDiscardPending = {
                // 丢弃新建未保存的表：删除并回滚到之前选中的表
                val discardId = pendingNewTableId
                val fallback = previousDefaultTableId
                pendingNewTableId = null
                previousDefaultTableId = null
                if (discardId != null) {
                    mainVm.discardNewTable(discardId, fallback)
                }
                overlayScreen = null
                editTableId = null
            },
            onSaved = {
                // 保存成功：把当前选中切到新表，再清 pending 标记
                val newId = pendingNewTableId
                pendingNewTableId = null
                previousDefaultTableId = null
                if (newId != null) {
                    mainVm.selectTable(newId)
                }
                overlayScreen = null
                editTableId = null
                currentTab = Tab.Schedule
            },
            onDeleted = {
                pendingNewTableId = null
                previousDefaultTableId = null
                overlayScreen = null
                editTableId = null
                currentTab = Tab.Mine
            }
        )
        return
    }

    // ----- ThemeColor -----
    if (overlayScreen == OverlayScreen.ThemeColor) {
        ThemeColorScreen(
            onBack = { overlayScreen = null }
        )
        return
    }

    // ----- MoreSettings -----
    if (overlayScreen == OverlayScreen.MoreSettings) {
        MoreSettingsScreen(
            onBack = { overlayScreen = null }
        )
        return
    }

    // ----- Export -----
    if (overlayScreen == OverlayScreen.Export) {
        ExportScreen(
            onBack = { overlayScreen = null }
        )
        return
    }

    // ----- Reminder -----
    if (overlayScreen == OverlayScreen.Reminder) {
        ReminderScreen(
            onBack = { overlayScreen = null }
        )
        return
    }

    // ----- About -----
    if (overlayScreen == OverlayScreen.About) {
        AboutScreen(
            onBack = { overlayScreen = null }
        )
        return
    }

    androidx.compose.material3.Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SleepyTheme.colors.background,
        bottomBar = {
            PillNavigationBar {
                Tab.entries.forEach { tab ->
                    PillNavItem(
                        icon = tab.icon,
                        label = stringResource(tab.labelRes),
                        selected = currentTab == tab,
                        onClick = { currentTab = tab }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                Tab.Schedule -> ScheduleScreen(
                    onGoImport = { currentTab = Tab.Manage },
                    onManualAdd = { overlayScreen = OverlayScreen.AddCourse },
                    onEditCourse = { course -> editingCourse = course }
                )
                Tab.Today -> TodayScreen()
                Tab.Manage -> {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    ManagementPage(
                        onJwImportRequested = {
                            val intent = android.content.Intent(ctx, com.lingion.sleepy.ui.screen.imports.JwImportActivity::class.java)
                            ctx.startActivity(intent)
                        },
                        onCreateNewTableRequested = {
                            mainScope.launch {
                                val previousId = mainVm.state.value.currentTable?.id
                                // commitSelection=false: 创建临时表但不切换选中状态，
                                // 避免管理页"当前课表"卡片在编辑页打开前瞬间跳到"默认X"。
                                val newId = mainVm.createEmptyTable(commitSelection = false)
                                previousDefaultTableId = previousId
                                pendingNewTableId = newId
                                editTableId = newId
                                overlayScreen = OverlayScreen.EditTable
                            }
                        },
                        onManualAdd = { overlayScreen = OverlayScreen.AddCourse },
                        onEditCurrentTable = {
                            editTableId = null
                            pendingNewTableId = null  // 编辑已有表
                            overlayScreen = OverlayScreen.EditTable
                        },
                        onImported = {
                            // refresh handled by ViewModel; just close any open overlays
                        },
                        onOpenEditTable = { tableId ->
                            editTableId = tableId
                            overlayScreen = OverlayScreen.EditTable
                        }
                    )
                }
                Tab.Mine -> MineScreen(
                    darkMode = darkMode,
                    onToggleDark = onToggleDark,
                    onOpenAllTables = { overlayScreen = OverlayScreen.AllTables },
                    onOpenThemeColor = { overlayScreen = OverlayScreen.ThemeColor },
                    onOpenMoreSettings = { overlayScreen = OverlayScreen.MoreSettings },
                    onOpenExport = { overlayScreen = OverlayScreen.Export },
                    onOpenReminder = { overlayScreen = OverlayScreen.Reminder },
                    onOpenAbout = { overlayScreen = OverlayScreen.About }
                )
            }
        }
    }
}
