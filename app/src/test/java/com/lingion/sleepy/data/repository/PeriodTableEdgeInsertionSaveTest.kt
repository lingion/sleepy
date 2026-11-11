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
import com.lingion.sleepy.util.TimeTableUtils.EdgeClass
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SleepyApp::class)
class PeriodTableEdgeInsertionSaveTest {
    @Test fun standaloneSaveKeepsEveryBoundEdgeCourseAtItsOriginalClockAndUndoRestoresIt() =
        verifySave { repo, period, _ -> assertEquals(2, repo.savePeriodTable(period)) }

    @Test fun sharedSyncKeepsEdgeCoursesWhileStandardCoursesRetainTheirNodePolicy() =
        verifySave { repo, period, _ -> repo.updatePeriodTable(period) }

    @Test fun metadataAndSharedContentSavePreservesEdgeCourseIdentity() =
        verifySave { repo, period, table -> repo.updateTableMetadataWithPeriodTable(table, period) }

    @Test fun metadataAndBindingContentSavePreservesExistingBoundEdgeCourses() =
        verifySave { repo, period, table -> repo.updateTableMetadataAndBind(table, period.id, period) }

    @Test fun standaloneSaveKeepsEdgeIdentityWhenItsClockAndStandardCountChangeTogether() =
        verifySave(editEdgeClock = true) { repo, period, _ -> repo.savePeriodTable(period) }

    private fun verifySave(
        editEdgeClock: Boolean = false,
        save: suspend (ScheduleRepository, PeriodTableEntity, TimeTableEntity) -> Unit
    ) = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SleepyApp>()
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        ReminderRescheduler.resetForTest()
        UndoManager.clear()
        try {
            val oldJson = TimeTableUtils.insertEdgeNode(TimeTableUtils.buildTimeJsonFromRows(listOf(
                TimeSlotRow(1, "08:00", "08:45"), TimeSlotRow(2, "10:00", "10:45")
            )), EdgeClass.After, "22:00", "22:30")
            val period = PeriodTableEntity(id = 7, name = "Shared", timeJson = oldJson, nodesPerDay = 2)
            db.periodTableDao().insert(period)
            val table = TimeTableEntity(id = 1, name = "First", startDate = "2026-09-07",
                periodTableId = 7, timeJson = oldJson, nodesPerDay = 2)
            val second = table.copy(id = 2, name = "Second")
            val unbound = table.copy(id = 3, name = "Unbound", periodTableId = null)
            db.timeTableDao().insertAll(listOf(table, second, unbound))
            val edge = CourseEntity(id = 10, tableId = 1, groupId = "edge", courseName = "Late", day = 1,
                startNode = 3, step = 1, startWeek = 1, endWeek = 16, color = "#FF6750A4", isIrregularNode = true)
            val standard = edge.copy(id = 11, groupId = "standard", courseName = "Standard", startNode = 2, isIrregularNode = false)
            val secondEdge = edge.copy(id = 12, tableId = 2)
            val unrelated = edge.copy(id = 13, tableId = 3)
            val originals = listOf(edge, standard, secondEdge, unrelated)
            db.courseDao().insertAll(originals)

            // Exercise the same creation, append, picker completion and save chain as the UI.
            val appended = TimeTableUtils.appendEmptyRow(TimeTableUtils.parseTimeSlotRows(oldJson))
            val partial = TimeTableUtils.updateManualRowTime(appended, appended.last().node, start = "09:00")
            val completed = TimeTableUtils.updateManualRowTime(partial, appended.last().node, end = "09:45")
            assertEquals(TimeTableUtils.PeriodValidationResult.Valid, TimeTableUtils.validatePeriodRows(completed))
            val finalRows = if (editEdgeClock) completed.map {
                if (it.edgeClass == EdgeClass.After) it.copy(start = "21:30", end = "22:00") else it
            } else completed
            val updated = period.copy(timeJson = TimeTableUtils.buildTimeJsonFromRows(finalRows), nodesPerDay = 3)
            save(repo, updated, table)

            assertEquals(edge.copy(startNode = 4), repo.getCourse(edge.id))
            assertEquals(secondEdge.copy(startNode = 4), repo.getCourse(secondEdge.id))
            assertEquals(standard, repo.getCourse(standard.id))
            assertEquals(unrelated, repo.getCourse(unrelated.id))
            val expectedClock = if (editEdgeClock) "21:30" to "22:00" else "22:00" to "22:30"
            assertEquals(expectedClock, TimeTableUtils.courseTimeParts(4, 1, repo.getPeriodTable(7)!!.timeJson))
            assertEquals(table.timeJson, repo.getTable(1)!!.timeJson)
            assertTrue(repo.restoreLastSnapshot())
            assertEquals(period, repo.getPeriodTable(7))
            assertEquals(originals.toSet(), repo.getAllCourses().toSet())
        } finally {
            ReminderRescheduler.resetForTest()
            UndoManager.clear()
            db.close()
        }
    }
}
