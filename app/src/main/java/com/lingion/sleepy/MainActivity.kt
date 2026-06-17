package com.lingion.sleepy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lingion.sleepy.ui.component.PillNavItem
import com.lingion.sleepy.ui.component.PillNavigationBar
import com.lingion.sleepy.ui.screen.imports.ImportScreen
import com.lingion.sleepy.ui.screen.mine.MineScreen
import com.lingion.sleepy.ui.screen.schedule.ScheduleScreen
import com.lingion.sleepy.ui.screen.edit.AddCourseScreen
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

        // Wrap in try/catch as defensive measure: any failure in notification scheduling
        // must not crash the app before setContent runs.
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
    Import(R.string.import_title, Icons.Outlined.UploadFile),
    Mine(R.string.tab_mine, Icons.Outlined.Person)
}

@Composable
private fun AppRoot(
    darkMode: Boolean = false,
    onToggleDark: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(Tab.Schedule) }
    var showAddCourse by remember { mutableStateOf(false) }

    if (showAddCourse) {
        AddCourseScreen(
            onBack = { showAddCourse = false },
            onSaved = {
                showAddCourse = false
                currentTab = Tab.Schedule
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
                    onGoImport = { currentTab = Tab.Import },
                    onManualAdd = { showAddCourse = true }
                )
                Tab.Today -> TodayScreen()
                Tab.Import -> ImportScreen(
                    onImported = { currentTab = Tab.Schedule },
                    onManualAdd = { showAddCourse = true }
                )
                Tab.Mine -> MineScreen(
                    darkMode = darkMode,
                    onToggleDark = onToggleDark
                )
            }
        }
    }
}