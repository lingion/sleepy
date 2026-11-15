package com.lingion.sleepy.ui.screen.mine

import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.nav.EditorCreation
import com.lingion.sleepy.ui.nav.EditorCreationState
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w360dp-h640dp-port-xhdpi", application = SleepyApp::class)
class PeriodTableCopyNavigationTest {
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
        ReminderRescheduler.resetForTest()
        UndoManager.clear()
        runBlocking {
            db.timeTableDao().insert(TimeTableEntity(
                id = 1, name = "Original timetable", startDate = "2026-09-07", isDefault = true,
            ))
        }
        AppPrefs.setCurrentTableId(app, 1L)
        compose.runOnIdle { model = ScheduleViewModel() }
        await { model.state.value.currentTable?.id == 1L }
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

    @Test fun copyingUnsavedPeriodDeletesItsDraftAndReturnsAfterCleanup() {
        val creation = newPeriodCreation()
        val sourceId = (creation.state.value as EditorCreationState.Ready).id
        var returned = 0
        var sourceAbsentInPublishedListAtReturn = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    PeriodTableEditScreen(
                        periodTableId = sourceId,
                        isNewUnsaved = true,
                        onBack = { error("Copying must use its own completion callback") },
                        onSaved = { error("Copying must not mark the unsaved source as saved") },
                        onCopied = {
                            creation.discardAndClose {
                                sourceAbsentInPublishedListAtReturn = model.allPeriodTables.value.none { it.id == sourceId }
                                model.releaseEditorCreation("copy-draft")
                                returned++
                            }
                        },
                        viewModel = model,
                    )
                }
            }
        }
        copyTo("Kept copy")
        await { returned == 1 }
        val rows = periods()
        assertEquals(listOf("Kept copy"), rows.map { it.name })
        assertTrue(rows.single().id != sourceId)
        assertNull(runBlocking { db.periodTableDao().getById(sourceId) })
        assertTrue(sourceAbsentInPublishedListAtReturn)
        assertEquals(1, returned)
    }

    @Test fun copyingExistingPeriodKeepsItsSourceAndUsesTheDefaultBackCallback() {
        runBlocking { db.periodTableDao().insert(PeriodTableEntity(id = 10, name = "Existing schedule")) }
        var returned = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    PeriodTableEditScreen(
                        periodTableId = 10,
                        onBack = { returned++ },
                        onSaved = { error("Copying an existing period is not saving its source") },
                        viewModel = model,
                    )
                }
            }
        }
        await { model.allPeriodTables.value.any { it.id == 10L } }
        copyTo("Kept copy")
        await { returned == 1 }
        assertEquals(setOf("Existing schedule", "Kept copy"), periods().map { it.name }.toSet())
        assertNotNull(runBlocking { db.periodTableDao().getById(10) })
        assertEquals(1, returned)
    }

    @Test fun savingNewPeriodUsesItsSaveCallbackAndKeepsTheOriginalRecord() {
        val creation = newPeriodCreation()
        val sourceId = (creation.state.value as EditorCreationState.Ready).id
        var savedCallbacks = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    PeriodTableEditScreen(
                        periodTableId = sourceId,
                        isNewUnsaved = true,
                        onBack = { error("Saving must use its own completion callback") },
                        onSaved = { savedCallbacks++; creation.completeSave() },
                        onCopied = { error("Saving must not run copy cleanup") },
                        onSaveStarted = { creation.tryBeginSave() },
                        onSaveJob = { creation.trackSave(it) },
                        viewModel = model,
                    )
                }
            }
        }
        compose.onNodeWithText(app.getString(R.string.period_table_name_label)).performTextReplacement("Saved schedule")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(app.getString(R.string.edit_table_save)))
        compose.onNodeWithText(app.getString(R.string.edit_table_save)).performClick()
        // Preview data comes from Room. Wait for its enabled action, and click the
        // button instead of relying on the dialog's merged text ancestry.
        val confirmText = hasText(app.getString(R.string.period_table_preview_confirm))
        val confirmButton = hasClickAction() and isEnabled() and hasAnyAncestor(isDialog()) and
            (confirmText or hasAnyDescendant(confirmText))
        await { compose.onAllNodes(confirmButton, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        compose.onNode(confirmButton, useUnmergedTree = true).performClick()
        await { savedCallbacks == 1 }
        assertEquals(EditorCreationState.Saved(sourceId), creation.state.value)
        assertEquals(sourceId, periods().single().id)
        assertEquals("Saved schedule", periods().single().name)
    }

    private fun copyTo(name: String) {
        compose.onNodeWithContentDescription(app.getString(R.string.period_table_copy)).performClick()
        await { compose.onAllNodesWithTag("period_edit_copy_field").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("period_edit_copy_field").performTextReplacement(name)
        compose.onNodeWithTag("period_edit_copy_confirm").performClick()
    }

    private fun newPeriodCreation(): EditorCreation {
        lateinit var creation: EditorCreation
        compose.runOnIdle {
            creation = model.newPeriodEditorCreation("copy-draft", restoredId = null)
            creation.start()
        }
        await { creation.state.value is EditorCreationState.Ready }
        return creation
    }

    private fun periods(): List<PeriodTableEntity> = runBlocking { db.periodTableDao().getAll() }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
