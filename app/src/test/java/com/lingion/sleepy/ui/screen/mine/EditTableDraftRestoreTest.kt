package com.lingion.sleepy.ui.screen.mine

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.SmartPeriodConfig
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.TimeTableUtils
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
@Config(sdk = [35], qualifiers = "en-w360dp-h640dp-port-xhdpi", application = SleepyApp::class)
class EditTableDraftRestoreTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: SleepyApp
    private lateinit var db: AppDatabase
    private lateinit var model: ScheduleViewModel

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        ReminderRescheduler.resetForTest()
        runBlocking { db.timeTableDao().insert(TimeTableEntity(id = 1, name = "Original", startDate = "2026-09-07", isDefault = true)) }
        compose.runOnIdle { model = ScheduleViewModel() }
    }

    @After fun cleanup() {
        compose.runOnIdle { model.viewModelScope.cancel() }
        ReminderRescheduler.resetForTest()
        db.close()
    }

    @Test fun holidayRoundTripKeepsUnsubmittedNameDateAndWeekCount() {
        val editing = mutableStateOf(true)
        var holidayTable: Long? = null
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                val holder = rememberSaveableStateHolder()
                if (editing.value) holder.SaveableStateProvider("table-editor") {
                    EditTableScreen(tableId = 1, onBack = {}, onSaved = {}, onDeleted = {},
                        onOpenHoliday = { holidayTable = it; editing.value = false }, viewModel = model)
                } else {
                    Button(onClick = { editing.value = true }) { Text("Return to editor") }
                }
            }
        }
        compose.waitUntil(10_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            model.state.value.tables.any { it.id == 1L }
        }
        fun field(id: Int) = compose.onNodeWithText(app.getString(id))
        field(R.string.edit_table_name).performTextReplacement("Unsubmitted name")
        field(R.string.edit_table_start_date).performTextReplacement("2026-09-14")
        field(R.string.edit_table_max_week).performTextReplacement("23")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(app.getString(R.string.settings_holiday_title_rules)))
        compose.onNodeWithText(app.getString(R.string.settings_holiday_title_rules)).performClick()
        assertEquals(1L, holidayTable)
        compose.onNodeWithText("Return to editor").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(app.getString(R.string.edit_table_name)))
        field(R.string.edit_table_name).assertTextContains("Unsubmitted name")
        field(R.string.edit_table_start_date).assertTextContains("2026-09-14")
        field(R.string.edit_table_max_week).assertTextContains("23")
        assertEquals("Original", runBlocking { db.timeTableDao().getAll().first().name })
    }

    @Test fun saversKeepIncompleteRowsAndAutomaticConfigurationExactly() {
        val scope = SaverScope { true }
        val rows = mutableStateListOf(
            TimeTableUtils.TimeSlotRow(1, "08:", "", TimeTableUtils.EdgeClass.Before),
            TimeTableUtils.TimeSlotRow(2, "09:00", "09:45"),
        )
        val encoded = with(TimeSlotRowsSaver) { scope.save(rows) }!!
        assertEquals(rows.toList(), TimeSlotRowsSaver.restore(encoded)!!.toList())
        val config = SmartPeriodConfig(startTime = "09:20", periodMinutes = 40, totalPeriods = 7)
        val saved = with(SmartPeriodConfigSaver) { scope.save(config) }!!
        assertEquals(config, SmartPeriodConfigSaver.restore(saved))
    }

    @Test fun restoredSharedScheduleStillRequiresConfirmationBeforeAnyWrite() {
        val original = """[{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"09:00","end":"09:45"}]"""
        runBlocking {
            db.periodTableDao().insert(PeriodTableEntity(id = 10, name = "Shared", nodesPerDay = 2, timeJson = original))
            db.timeTableDao().update(db.timeTableDao().getById(1)!!.copy(periodTableId = 10))
            db.timeTableDao().insert(TimeTableEntity(id = 2, name = "Other", startDate = "2026-09-07", periodTableId = 10))
        }
        val editing = mutableStateOf(true)
        var saved = false
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                val holder = rememberSaveableStateHolder()
                if (editing.value) holder.SaveableStateProvider("shared-editor") {
                    EditTableScreen(tableId = 1, onBack = {}, onSaved = { saved = true }, onDeleted = {},
                        onOpenHoliday = { editing.value = false }, viewModel = model)
                } else {
                    Button(onClick = {
                        // Reproduce the lost in-memory policy state after process restoration.
                        model.removeEditState(1)
                        editing.value = true
                    }) { Text("Restore editor") }
                }
            }
        }
        compose.waitUntil(10_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            model.state.value.effectivePeriodTable?.id == 10L
        }
        fun scrollTo(text: String) = compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        scrollTo(app.getString(R.string.edit_table_time_slots))
        compose.onNodeWithText(app.getString(R.string.edit_table_time_slots)).performClick()
        scrollTo("09:00")
        compose.onAllNodesWithContentDescription(app.getString(R.string.delete_period))[1].performClick()
        scrollTo(app.getString(R.string.settings_holiday_title_rules))
        compose.onNodeWithText(app.getString(R.string.settings_holiday_title_rules)).performClick()
        compose.onNodeWithText("Restore editor").performClick()
        scrollTo(app.getString(R.string.edit_table_save))
        compose.onNodeWithText(app.getString(R.string.edit_table_save)).performClick()
        compose.onNodeWithText(app.getString(R.string.schedule_conflict_dialog_title)).assertIsDisplayed()
        assertFalse(saved)
        assertEquals(original, runBlocking { db.periodTableDao().getById(10)!!.timeJson })
        assertEquals(10L, runBlocking { db.timeTableDao().getById(2)!!.periodTableId })
        compose.onNodeWithText(app.getString(R.string.schedule_conflict_option_detach_title)).performClick()
        scrollTo(app.getString(R.string.edit_table_save))
        compose.onNodeWithText(app.getString(R.string.edit_table_save)).performClick()
        compose.waitUntil(10_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            saved
        }
        assertNull(runBlocking { db.timeTableDao().getById(1)!!.periodTableId })
        assertEquals(original, runBlocking { db.periodTableDao().getById(10)!!.timeJson })
    }
}
