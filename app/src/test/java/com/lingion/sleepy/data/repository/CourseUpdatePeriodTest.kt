package com.lingion.sleepy.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SleepyApp::class)
class CourseUpdatePeriodTest {
    @Test fun addedLateClassExtendsEffectiveSlotsAndUndoRestoresThem() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SleepyApp>()
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        ReminderRescheduler.resetForTest()
        UndoManager.clear()
        try {
            val original = TimeTableUtils.buildTimeJsonFromRows(listOf(
                TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
                TimeTableUtils.TimeSlotRow(2, "09:00", "09:45")
            ))
            val period = PeriodTableEntity(id = 7, name = "Shared", timeJson = original, nodesPerDay = 2)
            db.periodTableDao().insert(period)
            val table = TimeTableEntity(id = 1, name = "Table", startDate = "2026-09-07",
                periodTableId = 7, timeJson = original, nodesPerDay = 2)
            db.timeTableDao().insert(table)
            val late = CourseEntity(tableId = 1, groupId = "", courseName = "Late", day = 1,
                startNode = 3, step = 1, startWeek = 1, endWeek = 16, color = "#FF6750A4")
            repo.updateImportedCourses(1, listOf(late))
            val effective = repo.getTable(1)!!.hydratedWith(repo.effectivePeriodTable(1))
            assertTrue(effective.nodesPerDay >= 3)
            val rows = TimeTableUtils.parseTimeSlotRows(effective.timeJson)
            assertTrue(rows.any { it.node == 3 && it.start.isNotBlank() && it.end.isNotBlank() })
            assertEquals("08:00", rows.single { it.node == 1 }.start)
            assertTrue(repo.restoreLastSnapshot())
            assertEquals(period, repo.effectivePeriodTable(1))
            assertEquals(table, repo.getTable(1))
            assertTrue(repo.getCourses(1).isEmpty())
        } finally {
            ReminderRescheduler.resetForTest()
            UndoManager.clear()
            db.close()
        }
    }
}
