package com.lingion.sleepy.ui.component

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.AppClock
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import java.io.File
import java.time.LocalDate

/** Real MainActivity routes on an isolated in-memory repository; never seeds the installed DB. */
class GlassAppNavigationRenderTest {
    @get:Rule(order = 0) val testName = TestName()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var app: SleepyApp
    private var repositoryDelegate: Any? = null
    private var savedPrefs: Map<String, *> = emptyMap<String, Any>()
    private val changedKeys = listOf(AppPrefs.KEY_UI_STYLE, AppPrefs.KEY_THEME_MODE, AppPrefs.KEY_NAV_DOCK, AppPrefs.KEY_UPDATE_CHECK_ENABLED)

    @get:Rule(order = 1) val memory = object : ExternalResource() {
        override fun before() {
            app = context.applicationContext as SleepyApp
            val delegate = SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }
            repositoryDelegate = delegate.get(app)
            db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
            val repository = ScheduleRepository(db)
            val today = LocalDate.now(AppClock)
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(1)
            runBlocking {
                val periodId = repository.insertPeriodTable(com.lingion.sleepy.data.entity.PeriodTableEntity(name = "视觉测试作息"))
                val id = repository.insertTable(TimeTableEntity(name = "玻璃视觉测试", startDate = monday.toString(), maxWeek = 20, isDefault = true, periodTableId = periodId))
                repository.insertCourses(listOf(
                    course(1, id, "高等数学", today.dayOfWeek.value, 1, "#B4D9F2"),
                    course(2, id, "大学物理", today.dayOfWeek.value, 4, "#BCE0BD"),
                    course(3, id, "程序设计", (today.dayOfWeek.value % 7) + 1, 7, "#F3C4CC"),
                ))
            }
            delegate.set(app, lazyOf(repository))
            val prefs = context.getSharedPreferences("sleepy_prefs", Context.MODE_PRIVATE)
            savedPrefs = prefs.all.filterKeys { it in changedKeys }
            AppPrefs.setUpdateCheckEnabled(context, false)
            AppPrefs.setUiStyle(context, UiStyle.LiquidGlass)
            AppPrefs.setThemeMode(context, if (testName.methodName.contains("Dark")) "dark" else "light")
            AppPrefs.setNavDock(context, false)
        }
        override fun after() {
            SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, repositoryDelegate)
            val edit = context.getSharedPreferences("sleepy_prefs", Context.MODE_PRIVATE).edit()
            changedKeys.forEach { key ->
                when (val old = savedPrefs[key]) {
                    null -> edit.remove(key)
                    is String -> edit.putString(key, old)
                    is Boolean -> edit.putBoolean(key, old)
                }
            }
            edit.commit()
            db.close()
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturesLightRealRoutesAndPopupStates() = walk("light")
    @Test fun capturesDarkRealRoutesAndPopupStates() = walk("dark")
    @Test fun capturesLightWideNavigation() = wide("light")
    @Test fun capturesDarkWideNavigation() = wide("dark")

    private fun wide(mode: String) {
        compose.mainClock.autoAdvance = true
        waitText("课表管理")
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(20_000) { compose.activity.resources.configuration.screenWidthDp > 600 }
        capture("$mode-wide-schedule-today")
        tap("我的")
        capture("$mode-wide-mine")
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun walk(mode: String) {
        compose.mainClock.autoAdvance = true
        waitText("课表管理")
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("schedule_add_course").fetchSemanticsNodes().isNotEmpty() }
        capture("$mode-home")
        compose.onNodeWithTag("schedule_prev_week").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(180)
        capture("$mode-home-pressed")
        compose.onNodeWithTag("schedule_prev_week").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("schedule_week_label").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithTag("schedule_week_label").performTouchInput { click() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("schedule_week#1").fetchSemanticsNodes().isNotEmpty() }
        capture("$mode-week-menu")
        back()
        compose.onNodeWithTag("schedule_add_course").performTouchInput { click() }
        waitText("手动创建课程")
        capture("$mode-add-course")
        back()
        tap("今日"); capture("$mode-today")
        tap("课表管理"); capture("$mode-manage")
        tap("备份与导出"); waitText("导出课表/作息表"); capture("$mode-export"); back()
        tap("添加课表"); capture("$mode-import")
        tap("教务直连"); capture("$mode-school-select"); back(); back()
        tap("我的"); capture("$mode-mine")
        for ((entry, name) in listOf(
            "所有课表" to "tables", "作息表" to "periods", "提醒" to "reminders",
            "外观与主题" to "appearance", "通用" to "general", "小组件" to "widgets", "关于" to "about",
        )) {
            tap(entry)
            capture("$mode-$name")
            if (name == "periods") {
                compose.onAllNodesWithContentDescription("编辑课表").onFirst().performTouchInput { click() }
                waitText("作息表"); capture("$mode-period-edit")
                compose.onNodeWithContentDescription("分享作息表").performTouchInput { click() }
                capture("$mode-period-share"); back()
                tap("自动模式"); capture("$mode-period-auto")
                back()
            }
            if (name == "general") {
                compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("节假日调休设置"))
                tap("节假日调休设置"); capture("$mode-holiday"); back()
            }
            if (name == "about") {
                compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("开源声明"))
                tap("开源声明"); capture("$mode-licenses"); back()
            }
            if (name == "appearance") {
                compose.onNodeWithText("界面风格").performScrollTo()
                capture("$mode-style-selector")
            }
            if (name == "widgets") {
                val node = compose.onAllNodesWithText("添加或管理桌面小组件").fetchSemanticsNodes()
                if (node.isNotEmpty()) {
                    tap("添加或管理桌面小组件"); capture("$mode-widget-management")
                    compose.onNodeWithContentDescription("添加小组件").performTouchInput { click() }
                    capture("$mode-widget-gallery"); back(); back()
                }
            }
            back()
        }
        tap("课表")
        // Dock owns a continuous physical frame loop. Freeze the test clock for a deterministic
        // resting frame instead of asking Espresso to exhaust an intentionally live frame loop.
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { AppPrefs.setNavDock(context, true) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("app_navigation_dock").fetchSemanticsNodes().isNotEmpty()
        }
        capture("$mode-dock")
    }

    private fun waitText(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun tap(text: String) {
        waitText(text)
        compose.onAllNodesWithText(text).onLast().performTouchInput { click() }
        compose.waitForIdle()
    }
    private fun back() {
        val popup = compose.onAllNodes(isPopup() or isDialog()).fetchSemanticsNodes().isNotEmpty()
        val pageBack = compose.onAllNodesWithContentDescription("返回")
        if (!popup && pageBack.fetchSemanticsNodes().isNotEmpty()) {
            // Exercise the actual page return button, not the system key's separate IME path.
            pageBack.onLast().performTouchInput { click() }
        } else {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val committed = java.util.concurrent.CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = com.lingion.sleepy.console.ActivityTracker.currentActivity() ?: compose.activity
            val decor = activity.window.decorView
            decor.postOnAnimation {
                decor.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                decor.invalidate()
            }
        }
        assertTrue("actual window frame must commit before screenshot", committed.await(8, java.util.concurrent.TimeUnit.SECONDS))
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { android.graphics.BitmapFactory.decodeStream(it) }
        }
        assertTrue("screenshot must have actual pixels", image != null && image.width > 0 && image.height > 0)
        val dir = File(context.cacheDir, "glass-route-matrix").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
        println("GLASS_ROUTE_CAPTURE=$name")
    }
    private fun course(id: Long, tableId: Long, name: String, day: Int, node: Int, color: String) = CourseEntity(
        id = id, groupId = "visual-$id", tableId = tableId, courseName = name, day = day,
        startNode = node, step = 2, startWeek = 1, endWeek = 20, color = color, room = "A203",
    )
}
