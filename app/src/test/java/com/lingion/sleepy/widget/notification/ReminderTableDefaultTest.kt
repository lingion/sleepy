package com.lingion.sleepy.widget.notification

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SleepyApp::class)
class ReminderTableDefaultTest {
    private lateinit var app: SleepyApp
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository

    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        UndoManager.clear()
        ReminderRescheduler.resetForTest()
        db.timeTableDao().insert(TimeTableEntity(id = 1, name = "Default", startDate = "2026-09-07", isDefault = true))
        db.timeTableDao().insert(TimeTableEntity(id = 2, name = "Selected", startDate = "2026-09-07"))
        AppPrefs.setCurrentTableId(app, 2)
    }

    @After fun cleanup() {
        UndoManager.clear()
        ReminderRescheduler.resetForTest()
        AppPrefs.clearCurrentTableId(app)
        db.close()
    }

    private suspend fun enabledIds() = AndroidBeforeClassDataSource(app).resolveReminderTables().map { it.id }.toSet()

    @Test fun defaultUsesCurrentSelectionEvenWhenEmptyAndDifferentFromDatabaseDefault() = runBlocking {
        assertEquals(setOf(2L), enabledIds())
        AppPrefs.setCurrentTableId(app, 1)
        assertEquals(setOf(1L), enabledIds())
    }

    @Test fun manualSelectionSurvivesSwitchingAndNewImports() = runBlocking {
        repo.setTableReminderEnabled(1, true)
        assertEquals(setOf(1L, 2L), enabledIds())
        repo.setTableReminderEnabled(2, false)
        AppPrefs.setCurrentTableId(app, 2)
        repo.importNewTable(TimeTableEntity(name = "Imported", startDate = "2026-09-07"), emptyList(), null)
        assertEquals(setOf(1L), enabledIds())
    }

    @Test fun turningOffCurrentTableDoesNotEnableAnyOtherTable() = runBlocking {
        repo.setTableReminderEnabled(2, false)
        assertEquals(emptySet<Long>(), enabledIds())
        AppPrefs.setCurrentTableId(app, 1)
        assertEquals(emptySet<Long>(), enabledIds())
    }

    @Test fun settingsAndDailyAgendasUseTheSameSelectionAsBeforeClassReminders() = runBlocking {
        assertEquals(setOf(2L), repo.observeReminderTables().first().filter { it.reminderEnabled }.map { it.id }.toSet())
        for (date in listOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5))) {
            assertEquals(listOf(2L), loadDailyAgenda(app, date).agenda.sourceTables.map { it.id })
        }
        repo.setTableReminderEnabled(1, true)
        assertEquals(setOf(1L, 2L), loadDailyAgenda(app, LocalDate.of(2026, 11, 4)).agenda.sourceTables.map { it.id }.toSet())
    }

    @Test fun missingSelectionFallsBackToDefaultAndPreviouslyDisabledTableStaysOff() = runBlocking {
        AppPrefs.setCurrentTableId(app, 99)
        assertEquals(setOf(1L), enabledIds())
        db.timeTableDao().update(db.timeTableDao().getById(1)!!.copy(reminderEnabled = false))
        assertEquals(emptySet<Long>(), enabledIds())
    }

    @Test fun repositoryRecreationPreservesManualSelectionAndMetadata() = runBlocking {
        repo.setTableReminderEnabled(1, true)
        repo.setTableReminderEnabled(2, false)
        val reloaded = ScheduleRepository(db).getReminderTables()
        assertEquals(setOf(1L), reloaded.filter { it.reminderEnabled }.map { it.id }.toSet())
        assertEquals("Selected", reloaded.first { it.id == 2L }.name)
        assertEquals("2026-09-07", reloaded.first { it.id == 2L }.startDate)
    }
    @Test fun undoRestoresAutomaticModeAndRedoRestoresManualSelection() = runBlocking {
        repo.setTableReminderEnabled(2, false)
        assertEquals(emptySet<Long>(), enabledIds())
        org.junit.Assert.assertTrue(repo.restoreLastSnapshot())
        assertEquals(setOf(2L), enabledIds())
        org.junit.Assert.assertTrue(repo.redoLastUndo())
        assertEquals(emptySet<Long>(), enabledIds())
        AppPrefs.setCurrentTableId(app, 1)
        assertEquals(emptySet<Long>(), enabledIds())
    }

}
