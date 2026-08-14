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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.lingion.sleepy.ui.screen.mine.AppearanceScreen
import com.lingion.sleepy.ui.screen.mine.MineScreen
import com.lingion.sleepy.ui.screen.mine.EditTableScreen
import com.lingion.sleepy.ui.screen.mine.GeneralSettingsScreen
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
    }

    private val editingCourseFromIntent = MutableStateFlow<CourseEntity?>(null)
    val editingCourseFlow: StateFlow<CourseEntity?> = editingCourseFromIntent.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        com.lingion.sleepy.util.UpdateManager.cleanOldApk(this)
        enableEdgeToEdge()
        handleDeepLinkIntent(intent)
        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            var themeMode by remember { mutableStateOf(AppPrefs.getThemeMode(this@MainActivity)) }
            var dark by remember { mutableStateOf(AppPrefs.isDarkMode(this@MainActivity, systemDark)) }
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
                        // ★ 手动切主题时联动刷新 widget(双路:广播+Glance直更)
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
    AddCourse, AllTables, EditTable, Theme, General, Export, Reminder, About
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
    // ★ 语言切换触发 Activity.recreate() 后, 用 rememberSaveable 保留 overlayScreen 导航状态,
    //   否则用户切语言后会丢失设置页上下文、退回主 Tab(决策 D2 重排修复)。
    var overlayScreen by rememberSaveable { mutableStateOf<OverlayScreen?>(null) }
    var editTableId by remember { mutableStateOf<Long?>(null) }
    var pendingNewTableId by remember { mutableStateOf<Long?>(null) }
    var previousDefaultTableId by remember { mutableStateOf<Long?>(null) }
    var autoImportTriggered by remember { mutableStateOf(false) }
    val mainScope = rememberCoroutineScope()
    val mainVm: ScheduleViewModel = viewModel()

    androidx.compose.runtime.LaunchedEffect(deepLinkCourse?.id) {
        if (deepLinkCourse != null) { editingCourse = deepLinkCourse; onDeepLinkConsumed() }
    }
    androidx.compose.runtime.LaunchedEffect(pendingImportText) {
        if (!autoImportTriggered && pendingImportText != null) { autoImportTriggered = true; currentTab = Tab.Manage }
    }

    BackHandler(enabled = overlayScreen != null || editingCourse != null) {
        if (pendingNewTableId != null) {
            val discardId = pendingNewTableId!!; val fallback = previousDefaultTableId
            pendingNewTableId = null; previousDefaultTableId = null
            mainVm.discardNewTable(discardId, fallback)
            overlayScreen = null; editTableId = null
        } else { overlayScreen = null; editingCourse = null; editTableId = null }
    }

    if (overlayScreen == OverlayScreen.AddCourse || editingCourse != null) {
        AddCourseScreen(onBack = { overlayScreen = null; editingCourse = null }, onSaved = { overlayScreen = null; editingCourse = null; currentTab = Tab.Schedule }, editingCourse = editingCourse)
        return
    }
    if (overlayScreen == OverlayScreen.AllTables) {
        AllTablesScreen(onBack = { overlayScreen = null }, onCreateNewTable = {
            mainScope.launch {
                val previousId = mainVm.state.value.currentTable?.id
                val newId = mainVm.createEmptyTable(commitSelection = false)
                previousDefaultTableId = previousId; pendingNewTableId = newId; editTableId = newId; overlayScreen = OverlayScreen.EditTable
            }
        }, onOpenEditTable = { tableId -> editTableId = tableId; pendingNewTableId = null; overlayScreen = OverlayScreen.EditTable })
        return
    }
    if (overlayScreen == OverlayScreen.EditTable) {
        EditTableScreen(tableId = editTableId, pendingNewTableId = pendingNewTableId, onBack = { overlayScreen = null; editTableId = null; pendingNewTableId = null; previousDefaultTableId = null }, onDiscardPending = {
            val discardId = pendingNewTableId; val fallback = previousDefaultTableId; pendingNewTableId = null; previousDefaultTableId = null
            if (discardId != null) mainVm.discardNewTable(discardId, fallback)
            overlayScreen = null; editTableId = null
        }, onSaved = { overlayScreen = null; editTableId = null; pendingNewTableId = null; previousDefaultTableId = null }, onDeleted = { overlayScreen = null; editTableId = null; currentTab = Tab.Schedule })
        return
    }
    if (overlayScreen == OverlayScreen.Theme) {
        AppearanceScreen(onBack = { overlayScreen = null }, themeMode = themeMode, onThemeModeChange = onThemeModeChange)
        return
    }
    if (overlayScreen == OverlayScreen.General) {
        GeneralSettingsScreen(onBack = { overlayScreen = null })
        return
    }
    if (overlayScreen == OverlayScreen.Export) {
        ExportScreen(onBack = { overlayScreen = null })
        return
    }
    if (overlayScreen == OverlayScreen.Reminder) {
        ReminderScreen(onBack = { overlayScreen = null })
        return
    }
    if (overlayScreen == OverlayScreen.About) {
        AboutScreen(onBack = { overlayScreen = null })
        return
    }

    androidx.compose.material3.Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SleepyTheme.colors.background,
        bottomBar = {
            PillNavigationBar {
                Tab.entries.forEach { tab ->
                    PillNavItem(icon = tab.icon, label = stringResource(tab.labelRes), selected = currentTab == tab, onClick = { currentTab = tab })
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                Tab.Schedule -> ScheduleScreen(onGoImport = { currentTab = Tab.Manage }, onManualAdd = { overlayScreen = OverlayScreen.AddCourse }, onEditCourse = { course -> editingCourse = course })
                Tab.Today -> TodayScreen()
                Tab.Manage -> {
                    val ctx = LocalContext.current
                    ManagementPage(autoShowImportSheet = pendingImportText != null, onJwImportRequested = { ctx.startActivity(Intent(ctx, com.lingion.sleepy.ui.screen.imports.JwImportActivity::class.java)) }, onCreateNewTableRequested = {
                        mainScope.launch {
                            val previousId = mainVm.state.value.currentTable?.id
                            val newId = mainVm.createEmptyTable(commitSelection = false)
                            previousDefaultTableId = previousId; pendingNewTableId = newId; editTableId = newId; overlayScreen = OverlayScreen.EditTable
                        }
                    }, onManualAdd = { overlayScreen = OverlayScreen.AddCourse }, onEditCurrentTable = { editTableId = null; pendingNewTableId = null; overlayScreen = OverlayScreen.EditTable }, onImported = {}, onOpenEditTable = { tableId -> editTableId = tableId; overlayScreen = OverlayScreen.EditTable })
                }
                Tab.Mine -> MineScreen(
                    onOpenAllTables = { overlayScreen = OverlayScreen.AllTables },
                    onOpenAppearance = { overlayScreen = OverlayScreen.Theme },
                    onOpenGeneral = { overlayScreen = OverlayScreen.General },
                    onOpenExport = { overlayScreen = OverlayScreen.Export },
                    onOpenReminder = { overlayScreen = OverlayScreen.Reminder },
                    onOpenAbout = { overlayScreen = OverlayScreen.About })
            }
        }
    }
}
