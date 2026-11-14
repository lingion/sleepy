package com.lingion.sleepy.ui.screen.mine

import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.lingion.sleepy.widget.notification.ReminderRescheduler

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w360dp-h640dp-port-xhdpi", application = SleepyApp::class)
class EditTableTargetIsolationTest {
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
        AppPrefs.clearCurrentTableId(app)
        AppPrefs.setCurrentTableId(app, 1L)
        ReminderRescheduler.resetForTest()
        UndoManager.clear()
        runBlocking {
            db.timeTableDao().insert(TimeTableEntity(
                id = 1, name = "Selected timetable", startDate = "2026-09-07", isDefault = true,
            ))
            db.timeTableDao().insert(TimeTableEntity(
                id = 2, name = "Edited timetable", startDate = "2026-09-07",
            ))
            db.courseDao().insert(course(1, "Selected course A"))
            db.courseDao().insert(course(1, "Selected course B"))
        }
        compose.runOnIdle { model = ScheduleViewModel() }
        await { model.state.value.selectedTableId == 1L && model.state.value.courses.size == 2 }
    }

    @After fun cleanup() {
        val modelJob = model.viewModelScope.coroutineContext[Job]!!
        compose.runOnIdle { modelJob.cancel() }
        await { modelJob.isCompleted }
        runBlocking { modelJob.cancelAndJoin() }
        ReminderRescheduler.resetForTest()
        UndoManager.clear()
        AppPrefs.clearCurrentTableId(app)
        db.close()
    }

    @Test fun deletingAnEmptyUnselectedTableDoesNotClaimTheSelectedTablesCourses() {
        showEditor()
        openDeleteConfirmation()

        compose.onNodeWithText(app.getString(R.string.edit_table_delete_msg, "Edited timetable"))
            .assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.edit_table_delete_msg_count, "Edited timetable", 2))
            .assertDoesNotExist()
        assertEquals(1L, model.state.value.selectedTableId)
        assertEquals(2, runBlocking { db.courseDao().getByTable(1).size })
    }

    @Test fun deletionCountAndDeletionBothUseTheExplicitTargetTable() {
        runBlocking { db.courseDao().insert(course(2, "Edited course")) }
        var deleted = false
        showEditor(onDeleted = { deleted = true })
        openDeleteConfirmation()

        compose.onNodeWithText(app.getString(R.string.edit_table_delete_msg_count, "Edited timetable", 1))
            .assertIsDisplayed()
        compose.onNode(hasText(app.getString(R.string.delete)) and hasAnyAncestor(isDialog()))
            .performClick()
        await { deleted && runBlocking { db.timeTableDao().getById(2) == null } }

        assertNotNull(runBlocking { db.timeTableDao().getById(1) })
        assertEquals(2, runBlocking { db.courseDao().getByTable(1).size })
        assertEquals(1L, model.state.value.selectedTableId)
    }

    @Test fun openingAndSavingAnotherBoundTableUsesItsOwnScheduleWithoutChangingSelection() {
        val (selectedPeriod, editedPeriod) = bindDistinctSchedules()
        var saved = false
        showEditor(onSaved = { saved = true })

        val editState = model.editStateFor(2, null)
        assertEquals(editedPeriod, editState.draftEffectiveSchedule.value)
        assertFalse(editState.hasScheduleChanged())
        compose.onNodeWithText(app.getString(R.string.edit_table_name)).performTextReplacement("Renamed target")
        scrollTo(app.getString(R.string.edit_table_save))
        compose.onNodeWithText(app.getString(R.string.edit_table_save)).performClick()
        await { saved }

        compose.onNodeWithText(app.getString(R.string.schedule_conflict_dialog_title)).assertDoesNotExist()
        assertEquals("Renamed target", runBlocking { db.timeTableDao().getById(2)!!.name })
        assertEquals(20L, runBlocking { db.timeTableDao().getById(2)!!.periodTableId })
        assertEquals(editedPeriod.timeJson, runBlocking { db.periodTableDao().getById(20)!!.timeJson })
        assertEquals(selectedPeriod, runBlocking { db.periodTableDao().getById(10) })
        assertEquals(1L, model.state.value.selectedTableId)
    }

    @Test fun cancellingAnEditedTargetScheduleRestoresTheTargetsBaseline() {
        val (selectedPeriod, editedPeriod) = bindDistinctSchedules()
        showEditor()
        scrollTo(app.getString(R.string.edit_table_time_slots))
        compose.onNodeWithText(app.getString(R.string.edit_table_time_slots)).performClick()
        scrollTo("14:00")
        compose.onAllNodesWithContentDescription(app.getString(R.string.delete_period))[1].performClick()
        scrollTo(app.getString(R.string.edit_table_save))
        compose.onNodeWithText(app.getString(R.string.edit_table_save)).performClick()
        compose.onNodeWithText(app.getString(R.string.schedule_conflict_dialog_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.schedule_conflict_option_sync_title, editedPeriod.name))
            .assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.cancel)).performClick()

        val editState = model.editStateFor(2, null)
        assertEquals(editedPeriod, editState.draftEffectiveSchedule.value)
        assertFalse(editState.hasScheduleChanged())
        assertEquals(editedPeriod, runBlocking { db.periodTableDao().getById(20) })
        assertEquals(selectedPeriod, runBlocking { db.periodTableDao().getById(10) })
        assertEquals(1L, model.state.value.selectedTableId)
    }

    @Test fun hiddenEditorsKeepSeparateCurrentSnapshotsWithoutAVisibleCollector() {
        val (selectedPeriod, editedPeriod) = bindDistinctSchedules()
        val selectedContent = compose.runOnIdle { model.observeTableEditorContent(1) }
        val editedContent = compose.runOnIdle { model.observeTableEditorContent(2) }
        await { selectedContent.value != null && editedContent.value != null }

        assertEquals(selectedPeriod, selectedContent.value!!.effectivePeriodTable)
        assertEquals(editedPeriod, editedContent.value!!.effectivePeriodTable)
        val changedPeriod = editedPeriod.copy(name = "Updated while editor is hidden")
        runBlocking {
            db.periodTableDao().update(changedPeriod)
            db.courseDao().insert(course(2, "Added while editor is hidden"))
        }
        await {
            editedContent.value?.effectivePeriodTable == changedPeriod &&
                editedContent.value?.courseCount == 1
        }

        val returnedContent = compose.runOnIdle { model.observeTableEditorContent(2) }
        assertSame(editedContent, returnedContent)
        assertEquals(changedPeriod, returnedContent.value!!.effectivePeriodTable)
        assertEquals(2L, returnedContent.value!!.tableId)
        assertEquals(selectedPeriod, selectedContent.value!!.effectivePeriodTable)
        assertEquals(2, selectedContent.value!!.courseCount)
        assertEquals(1L, model.state.value.selectedTableId)
    }

    @Test fun deletingATableStopsItsCachedObserverAndRestoringItsIdCreatesAFreshOne() {
        val (selectedPeriod, _) = bindDistinctSchedules()
        val selectedContent = compose.runOnIdle { model.observeTableEditorContent(1) }
        val editedContent = compose.runOnIdle { model.observeTableEditorContent(2) }
        await { selectedContent.value != null && editedContent.value != null }
        val originalTable = runBlocking { db.timeTableDao().getById(2)!! }

        runBlocking { db.timeTableDao().deleteById(2) }
        await { model.state.value.tables.none { it.id == 2L } }
        val stoppedSnapshot = compose.runOnIdle { editedContent.value }
        runBlocking {
            db.timeTableDao().insert(originalTable)
            db.courseDao().insert(course(2, "Restored target course"))
            db.periodTableDao().update(selectedPeriod.copy(name = "Updated surviving schedule"))
            db.courseDao().insert(course(1, "Added to surviving table"))
        }
        await { model.state.value.tables.any { it.id == 2L } }
        val restoredContent = compose.runOnIdle { model.observeTableEditorContent(2) }
        await {
            restoredContent.value?.courseCount == 1 &&
                selectedContent.value?.courseCount == 3 &&
                selectedContent.value?.effectivePeriodTable?.name == "Updated surviving schedule"
        }

        assertNotSame(editedContent, restoredContent)
        assertEquals(stoppedSnapshot, editedContent.value)
        assertEquals(2L, restoredContent.value!!.tableId)
        assertEquals(20L, restoredContent.value!!.effectivePeriodTable!!.id)
        assertSame(selectedContent, compose.runOnIdle { model.observeTableEditorContent(1) })
        assertEquals(1L, model.state.value.selectedTableId)
    }

    private fun showEditor(onSaved: () -> Unit = {}, onDeleted: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    EditTableScreen(tableId = 2, onBack = {}, onSaved = onSaved, onDeleted = onDeleted, viewModel = model)
                }
            }
        }
        await {
            compose.onAllNodesWithText(app.getString(R.string.edit_table_name)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openDeleteConfirmation() {
        scrollTo(app.getString(R.string.edit_table_delete))
        compose.onNodeWithText(app.getString(R.string.edit_table_delete)).performClick()
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    private fun bindDistinctSchedules(): Pair<PeriodTableEntity, PeriodTableEntity> {
        val selected = PeriodTableEntity(
            id = 10, name = "Selected morning schedule", nodesPerDay = 2,
            timeJson = """[{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"09:00","end":"09:45"}]""",
        )
        val edited = PeriodTableEntity(
            id = 20, name = "Edited afternoon schedule", nodesPerDay = 2,
            timeJson = """[{"node":1,"start":"13:00","end":"13:45"},{"node":2,"start":"14:00","end":"14:45"}]""",
        )
        runBlocking {
            db.periodTableDao().insert(selected)
            db.periodTableDao().insert(edited)
            db.timeTableDao().update(db.timeTableDao().getById(1)!!.copy(periodTableId = selected.id))
            db.timeTableDao().update(db.timeTableDao().getById(2)!!.copy(periodTableId = edited.id))
        }
        await { model.state.value.effectivePeriodTable?.id == selected.id }
        return selected to edited
    }

    private fun course(tableId: Long, name: String) = CourseEntity(
        tableId = tableId, groupId = name, courseName = name,
        day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 20, color = "#FF6750A4",
    )

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
