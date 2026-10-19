package com.lingion.sleepy.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.ui.screen.today.TodayScreen
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose-path pixel golden for the 今日课程 (today agenda) screen.
 *
 * Determinism strategy:
 *  - today is pinned to 2026-09-30 (Wednesday, week 2 of a term starting
 *    2026-09-28) via TodayScreen's today parameter — Robolectric cannot roll
 *    System.currentTimeMillis backwards, so LocalDate.now() is injected.
 *  - A real in-memory Room DB is seeded through the production repository and
 *    injected into SleepyApp by replacing the lazy delegate, so
 *    ScheduleViewModel observes genuine DAO flows (no reflection into state).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi", application = SleepyApp::class)
class TodayScreenGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun seedDatabase() {
        val app = ApplicationProvider.getApplicationContext<SleepyApp>()
        val instanceField = SleepyApp::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, app)

        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ScheduleRepository(db)
        runBlocking {
            val id = repo.insertTable(
                TimeTableEntity(
                    name = "演示课表",
                    startDate = "2026-09-28",
                    maxWeek = 20,
                    isDefault = true,
                )
            )
            repo.insertCourses(
                listOf(
                    course(1, id, "大学物理", 3, 1, "A203"),
                    course(2, id, "体育", 3, 5, "体育馆"),
                    course(3, id, "高等数学", 3, 7, "A201"),
                )
            )
            id
        }
        val delegateField = SleepyApp::class.java.getDeclaredField("repository\$delegate")
        delegateField.isAccessible = true
        delegateField.set(app, lazyOf(repo))
    }

    @Test
    fun todayScreenMatchesGolden() {
        composeRule.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(modifier = Modifier.fillMaxSize()) {
                    TodayScreen(
                        onEditCourse = {},
                        today = LocalDate.of(2026, 9, 30),
                    )
                }
            }
        }
        // Room flow round-trip is async and posts to the Android main looper,
        // which Robolectric only drains on explicit idle — pump it each poll.
        val mainLooper = shadowOf(android.os.Looper.getMainLooper())
        composeRule.waitUntil(timeoutMillis = 10_000) {
            mainLooper.idle()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("大学物理").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        GoldenCompare.assertGolden(bitmap, "today_screen_light.png")
        bitmap.recycle()
    }

    private fun course(id: Long, tableId: Long, name: String, day: Int, node: Int, room: String) =
        CourseEntity(
            id = id,
            groupId = "g$id",
            tableId = tableId,
            courseName = name,
            day = day,
            startNode = node,
            step = 2,
            startWeek = 1,
            endWeek = 16,
            room = room,
            color = "#FF6750A4",
        )
}
