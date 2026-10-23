package com.lingion.sleepy.ui.screen.edit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseColorMode
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi", application = SleepyApp::class)
class SingleOccurrenceCourseFormTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: SleepyApp
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository
    private lateinit var model: ScheduleViewModel
    private var saved = false
    private val slot = InitialCourseSlot(tableId = 1, week = 5, day = 2, node = 3, step = 3)

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        ReminderRescheduler.resetForTest()
        AppPrefs.setHolidayTransfers(app, 1, emptyList())
        runBlocking { db.timeTableDao().insert(TimeTableEntity(id = 1, name = "Table", startDate = "2026-09-07", isDefault = true)) }
        compose.runOnIdle { model = ScheduleViewModel() }
        UndoManager.clear()
    }

    @After fun cleanup() {
        compose.runOnIdle { model.viewModelScope.cancel() }
        ReminderRescheduler.resetForTest()
        db.close()
        UndoManager.clear()
    }

    private fun show(initialSlot: InitialCourseSlot? = slot) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                AddCourseScreen(onBack = {}, onSaved = { saved = true }, initialSlot = initialSlot, viewModel = model)
            }
        }
        waitFor { model.state.value.effectiveCurrentTable != null }
    }

    private fun waitFor(predicate: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.waitForIdle()
            predicate()
        }
    }

    private fun fill(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)
    }

    @Test fun nameAloneSavesOnlyTheSelectedDateAndPeriods() {
        show()
        compose.onNodeWithText(app.getString(R.string.occurrence_add_title)).assertIsDisplayed()
        compose.onNodeWithText("2026-10-06", substring = true).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.course_node_format, "3-5"), substring = true).assertIsDisplayed()
        compose.onNodeWithTag("single-occurrence-save").assertIsNotEnabled()
        fill("single-occurrence-name", "Math")
        compose.onNodeWithTag("single-occurrence-save").performClick()
        waitFor { saved }
        val row = runBlocking { repo.getCourses(1).single() }
        assertEquals("Math", row.courseName)
        assertEquals(5, row.startWeek)
        assertEquals(5, row.endWeek)
        assertEquals(2, row.day)
        assertEquals(3, row.startNode)
        assertEquals(3, row.step)
        assertEquals("", row.alias)
        assertEquals("", row.teacher)
        assertEquals("", row.room)
        assertEquals("", row.note)
        assertNull(row.fixedDate)
    }

    @Test fun morePreservesEveryFieldAndTheSelectedSlotWithoutSavingEarly() {
        show()
        fill("single-occurrence-name", "Math")
        fill("single-occurrence-alias", "M")
        fill("single-occurrence-teacher", "Teacher")
        fill("single-occurrence-room", "A101")
        fill("single-occurrence-note", "Bring a book")
        compose.onNodeWithTag("single-occurrence-color").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.action_confirm)).performClick()
        compose.onNodeWithTag("single-occurrence-more").performClick()
        compose.onNodeWithTag("single-occurrence-form").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.create_course)).assertIsDisplayed()
        assertTrue(runBlocking { repo.getCourses(1).isEmpty() })
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(app.getString(R.string.create_course_btn)))
        compose.onNodeWithText(app.getString(R.string.create_course_btn)).performClick()
        waitFor { saved }
        val row = runBlocking { repo.getCourses(1).single() }
        assertEquals("Math", row.courseName)
        assertEquals("M", row.alias)
        assertEquals("Teacher", row.teacher)
        assertEquals("A101", row.room)
        assertEquals("Bring a book", row.note)
        assertEquals(CourseColorMode.CUSTOM, row.colorMode)
        assertEquals("#FF6750A4", row.color)
        assertEquals(5, row.startWeek)
        assertEquals(5, row.endWeek)
        assertEquals(2, row.day)
        assertEquals(3, row.startNode)
        assertEquals(3, row.step)
    }

    @Test fun regularAddEntryStillOpensTheFullEditor() {
        show(initialSlot = null)
        compose.onNodeWithTag("single-occurrence-form").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.create_course)).assertIsDisplayed()
    }
}
