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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.ui.component.PillNavItem
import com.lingion.sleepy.ui.component.PillNavigationBar
import com.lingion.sleepy.ui.screen.edit.AddCourseScreen
import com.lingion.sleepy.ui.screen.imports.ImportScreen
import com.lingion.sleepy.ui.screen.manage.ManagementPage
import com.lingion.sleepy.ui.screen.mine.AllTablesScreen
import com.lingion.sleepy.ui.screen.mine.MineScreen
import com.lingion.sleepy.ui.screen.mine.MoreSettingsScreen
import com.lingion.sleepy.ui.screen.mine.EditTableScreen
import com.lingion.sleepy.ui.screen.mine.ThemeColorScreen
import com.lingion.sleepy.ui.screen.mine.ExportScreen
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(perm), 1001)
            }
        }

        try {
            (application as SleepyApp).notificationScheduler.scheduleDailyReminder()
        } catch (e: Throwable) {
            android.util.Log.e("Sleepy", "scheduleDailyReminder failed", e)
        }

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
    Import,
    AllTables,
    EditTable,
    ThemeColor,
    MoreSettings,
    Export
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

    // 小组件 deep link 触发：拉到了课程 → 切到编辑模式
    androidx.compose.runtime.LaunchedEffect(deepLinkCourse?.id) {
        if (deepLinkCourse != null) {
            editingCourse = deepLinkCourse
            onDeepLinkConsumed()
        }
    }

    BackHandler(enabled = overlayScreen != null || editingCourse != null) {
        overlayScreen = null
        editingCourse = null
        editTableId = null
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

    // ----- Import -----
    if (overlayScreen == OverlayScreen.Import) {
        val context = androidx.compose.ui.platform.LocalContext.current
        ImportScreen(
            onImported = {
                overlayScreen = null
                currentTab = Tab.Manage
            },
            onBack = { overlayScreen = null },
            onManualAdd = { overlayScreen = OverlayScreen.AddCourse },
            onOpenEditTable = { tableId ->
                editTableId = tableId
                overlayScreen = OverlayScreen.EditTable
            },
            onJwImportRequested = {
                val intent = android.content.Intent(context, com.lingion.sleepy.ui.screen.imports.JwImportActivity::class.java)
                context.startActivity(intent)
            }
        )
        return
    }

    // ----- AllTables -----
    if (overlayScreen == OverlayScreen.AllTables) {
        AllTablesScreen(
            onBack = { overlayScreen = null },
            onOpenEditTable = { tableId ->
                editTableId = tableId
                overlayScreen = OverlayScreen.EditTable
            }
        )
        return
    }

    // ----- EditTable (unified) -----
    if (overlayScreen == OverlayScreen.EditTable) {
        EditTableScreen(
            tableId = editTableId,
            onBack = { overlayScreen = null; editTableId = null },
            onSaved = {
                overlayScreen = null
                editTableId = null
                currentTab = Tab.Schedule
            },
            onDeleted = {
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
                Tab.Manage -> ManagementPage(
                    onOpenImport = { overlayScreen = OverlayScreen.Import },
                    onManualAdd = { overlayScreen = OverlayScreen.AddCourse },
                    onEditCurrentTable = {
                        editTableId = null
                        overlayScreen = OverlayScreen.EditTable
                    },
                    onCreateNewTable = {
                        overlayScreen = OverlayScreen.EditTable
                    }
                )
                Tab.Mine -> MineScreen(
                    darkMode = darkMode,
                    onToggleDark = onToggleDark,
                    onOpenAllTables = { overlayScreen = OverlayScreen.AllTables },
                    onOpenThemeColor = { overlayScreen = OverlayScreen.ThemeColor },
                    onOpenMoreSettings = { overlayScreen = OverlayScreen.MoreSettings },
                    onOpenExport = { overlayScreen = OverlayScreen.Export }
                )
            }
        }
    }
}
