package com.lingion.sleepy.ui.nav

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
class EditorCreationRoomTest {
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
            db.timeTableDao().insert(
                TimeTableEntity(id = 1, name = "Original", startDate = "2026-09-07", isDefault = true)
            )
        }
        AppPrefs.setCurrentTableId(app, 1L)
        compose.runOnIdle { model = ScheduleViewModel() }
        await { model.state.value.currentTable?.id == 1L }
    }

    @After fun cleanup() {
        val modelJob = model.viewModelScope.coroutineContext[Job]!!
        compose.runOnIdle { modelJob.cancel() }
        // Room query cancellation can finish on IO and dispatch back to Main. Pump
        // Main until every child stops before joining or closing its database.
        await { modelJob.isCompleted }
        runBlocking { modelJob.cancelAndJoin() }
        ReminderRescheduler.resetForTest()
        UndoManager.clear()
        AppPrefs.clearCurrentTableId(app)
        db.close()
    }

    @Test fun tableReadyAlreadyExistsInRoomAndEditorStateWithoutChangingSelection() {
        lateinit var creation: EditorCreation
        compose.runOnIdle {
            creation = model.newTableEditorCreation("new-table", restoredId = null, fallbackId = 1L)
            repeat(3) { creation.start() }
        }
        val id = readyId(creation)
        val rows = tables()
        assertEquals(2, rows.size)
        assertNotNull(rows.find { it.id == id })
        assertFalse(rows.single { it.id == id }.isDefault)
        assertEquals(1L, rows.single { it.isDefault }.id)
        assertTrue(model.state.value.tables.any { it.id == id })
        assertEquals(1L, model.state.value.selectedTableId)
        assertEquals(1L, AppPrefs.getCurrentTableId(app))
        compose.runOnIdle {
            assertSame(creation, model.newTableEditorCreation("new-table", restoredId = id, fallbackId = 1L))
        }
    }

    @Test fun earlyTableBackDeletesRoomRowAndPublishedListBeforeCloseCallback() {
        var closed = false
        var roomIdsAtClose = emptyList<Long>()
        var visibleIdsAtClose = emptyList<Long>()
        var selectedAtClose: Long? = null
        compose.runOnIdle {
            val creation = model.newTableEditorCreation("discard-table", restoredId = null, fallbackId = 1L)
            creation.start()
            creation.discardAndClose {
                roomIdsAtClose = tables().map { it.id }
                visibleIdsAtClose = model.state.value.tables.map { it.id }
                selectedAtClose = model.state.value.selectedTableId
                closed = true
            }
        }
        await { closed }
        assertEquals(listOf(1L), roomIdsAtClose)
        assertEquals(listOf(1L), visibleIdsAtClose)
        assertEquals(1L, selectedAtClose)
        assertEquals(1L, tables().single { it.isDefault }.id)
    }

    @Test fun firstTableDraftIsReadyWithARealDefaultRow() {
        runBlocking { db.timeTableDao().deleteAll() }
        await { model.state.value.tables.isEmpty() && model.state.value.selectedTableId == null }
        lateinit var creation: EditorCreation
        compose.runOnIdle {
            creation = model.newTableEditorCreation("first-table", restoredId = null, fallbackId = null)
            creation.start()
        }
        val id = readyId(creation)
        assertEquals(id, tables().single().id)
        assertTrue(tables().single().isDefault)
        assertEquals(id, model.state.value.currentTable?.id)
    }

    @Test fun periodReadyAndDiscardUseTheRealRowAndWaitForListCleanup() {
        lateinit var creation: EditorCreation
        compose.runOnIdle {
            creation = model.newPeriodEditorCreation("new-period", restoredId = null)
            creation.start()
        }
        val id = readyId(creation)
        val row = runBlocking { db.periodTableDao().getById(id) }
        assertNotNull(row)
        assertEquals(app.getString(R.string.period_table_new), row!!.name)
        assertEquals(TimeTableUtils.DEFAULT_TIME_JSON, row.timeJson)
        assertTrue(model.allPeriodTables.value.any { it.id == id })
        assertEquals(1L, model.state.value.selectedTableId)

        var closed = false
        var deletedInRoomAtClose = false
        var deletedInListAtClose = false
        compose.runOnIdle {
            creation.discardAndClose {
                deletedInRoomAtClose = runBlocking { db.periodTableDao().getById(id) == null }
                deletedInListAtClose = model.allPeriodTables.value.none { it.id == id }
                closed = true
            }
        }
        await { closed }
        assertTrue(deletedInRoomAtClose)
        assertTrue(deletedInListAtClose)
        assertEquals(listOf(1L), tables().map { it.id })
    }

    @Test fun restoredTableIdReusesThePersistedDraftWithoutAnotherInsert() {
        runBlocking {
            db.timeTableDao().insert(
                TimeTableEntity(id = 17, name = "Restored draft", startDate = "2026-09-14")
            )
        }
        await { model.state.value.tables.any { it.id == 17L } }
        lateinit var creation: EditorCreation
        compose.runOnIdle {
            creation = model.newTableEditorCreation("restored-table", restoredId = 17L, fallbackId = 1L)
            repeat(3) { creation.start() }
        }
        assertEquals(17L, readyId(creation))
        assertEquals(setOf(1L, 17L), tables().map { it.id }.toSet())
        assertEquals("Restored draft", tables().single { it.id == 17L }.name)
        assertEquals(1L, model.state.value.selectedTableId)
    }

    private fun tables(): List<TimeTableEntity> = runBlocking { db.timeTableDao().getAll() }

    private fun readyId(creation: EditorCreation): Long {
        await { creation.state.value !is EditorCreationState.Initializing }
        val state = creation.state.value
        if (state is EditorCreationState.Failed) throw AssertionError("Editor creation failed", state.cause)
        return (state as EditorCreationState.Ready).id
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
