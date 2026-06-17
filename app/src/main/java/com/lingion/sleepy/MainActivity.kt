package com.lingion.sleepy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.lingion.sleepy.ui.screen.mine.EditTableScreen
import com.lingion.sleepy.ui.screen.schedule.ScheduleScreen
import com.lingion.sleepy.ui.screen.today.TodayScreen
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs

class MainActivity : ComponentActivity() {

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

        setContent {
            val dark = remember { mutableStateOf(AppPrefs.isDarkMode(this@MainActivity)) }
            SleepyThemeProvider(darkTheme = dark.value) {
                AppRoot(
                    darkMode = dark.value,
                    onToggleDark = {
                        val v = !dark.value
                        AppPrefs.setDarkMode(this@MainActivity, v)
                        dark.value = v
                    }
                )
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
    EditTable
}

@Composable
private fun AppRoot(
    darkMode: Boolean = false,
    onToggleDark: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(Tab.Schedule) }
    var editingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var overlayScreen by remember { mutableStateOf<OverlayScreen?>(null) }
    var editTableId by remember { mutableStateOf<Long?>(null) }

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
                    onOpenAllTables = { overlayScreen = OverlayScreen.AllTables }
                )
            }
        }
    }
}
