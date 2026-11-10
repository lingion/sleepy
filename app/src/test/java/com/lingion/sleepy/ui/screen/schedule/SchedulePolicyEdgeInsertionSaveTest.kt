package com.lingion.sleepy.ui.screen.schedule

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.util.TimeTableUtils
import com.lingion.sleepy.util.TimeTableUtils.EdgeClass
import com.lingion.sleepy.util.TimeTableUtils.TimeSlotRow
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
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
@Config(sdk = [35], application = SleepyApp::class)
class SchedulePolicyEdgeInsertionSaveTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository
    private lateinit var model: ScheduleViewModel

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<SleepyApp>()
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        ReminderRescheduler.resetForTest()
        UndoManager.clear()
        compose.runOnIdle { model = ScheduleViewModel() }
    }

    @After fun cleanup() {
        val job = model.viewModelScope.coroutineContext[Job]!!
        compose.runOnIdle { model.viewModelScope.cancel() }
        // Cancellation requests are asynchronous while Room is querying. Wait
        // for the observers to finish before closing their connection pool.
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            job.isCompleted
        }
        ReminderRescheduler.resetForTest()
        UndoManager.clear()
        db.close()
    }

    @Test fun detachCopyMapsFromEffectiveSharedScheduleRatherThanCompatibilitySnapshot() =
        verifySave(SchedulePolicy.DETACH_COPY)

    @Test fun createNewMapsFromEffectiveSharedScheduleRatherThanCompatibilitySnapshot() =
        verifySave(SchedulePolicy.CREATE_NEW)

    @Test fun unboundSavePreservesEdgeIdentityWhenItsClockAndStandardCountChangeTogether() =
        verifySave(null)

    private fun verifySave(policy: SchedulePolicy?) {
        val compatibilityJson = json(TimeSlotRow(1, "06:00", "06:45"),
            TimeSlotRow(2, "07:00", "07:45"), TimeSlotRow(3, "08:00", "08:45"))
        val effectiveJson = json(TimeSlotRow(1, "08:00", "08:45"),
            TimeSlotRow(2, "10:00", "10:45"), TimeSlotRow(3, "22:00", "22:30", EdgeClass.After))
        val period = PeriodTableEntity(id = 7, name = "Shared", timeJson = effectiveJson, nodesPerDay = 2)
        val builtin = TimeTableEntity(id = 1, name = "First", startDate = "2026-09-07",
            isDefault = true, timeJson = compatibilityJson, nodesPerDay = 3)
        val table = if (policy == null) builtin.copy(timeJson = effectiveJson, nodesPerDay = 2)
            else TimeTableEntity.snapshotForBind(builtin, period.id)
        val other = TimeTableEntity.snapshotForBind(builtin.copy(id = 2, name = "Other", isDefault = false), period.id)
        val edge = course(10, 3, irregular = true)
        val standard = course(11, 2, irregular = false)
        val otherEdge = edge.copy(id = 12, tableId = 2)
        val originals = listOf(edge, standard, otherEdge)
        runBlocking {
            db.periodTableDao().insert(period)
            db.timeTableDao().insertAll(listOf(table, other))
            db.courseDao().insertAll(originals)
        }

        val appended = TimeTableUtils.appendEmptyRow(TimeTableUtils.parseTimeSlotRows(effectiveJson))
        val partial = TimeTableUtils.updateManualRowTime(appended, appended.last().node, start = "09:00")
        val completed = TimeTableUtils.updateManualRowTime(partial, appended.last().node, end = "09:45")
        // Editing the edge clock in the same draft proves identity, not clock overlap,
        // keeps the edge attached after the new standard card is inserted.
        val rows = completed.map { if (it.edgeClass == EdgeClass.After) it.copy(start = "21:30", end = "22:00") else it }
        assertEquals(TimeTableUtils.PeriodValidationResult.Valid, TimeTableUtils.validatePeriodRows(rows))
        val newJson = TimeTableUtils.buildTimeJsonFromRows(rows)
        val edited = table.copy(name = "Edited", timeJson = newJson)

        if (policy == null) {
            runBlocking { repo.updateTableRemappingCourses(edited.copy(nodesPerDay = 3)) }
        } else {
            var finished = false
            compose.runOnIdle {
                model.startEditSession(table.id, period).apply {
                    updateDraft(period.copy(timeJson = newJson, nodesPerDay = 3))
                    selectPolicy(policy)
                }
                model.executePolicyAndSave(table.id, edited, newJson, "", 3) { finished = true }
            }
            compose.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                finished
            }
        }

        runBlocking {
            val saved = repo.getTable(table.id)!!
            val savedEffective = saved.hydratedWith(repo.effectivePeriodTable(table.id))
            assertEquals("Edited", saved.name)
            assertEquals(newJson, savedEffective.timeJson)
            assertEquals(3, savedEffective.nodesPerDay)
            assertEquals(edge.copy(startNode = 4), repo.getCourse(edge.id))
            // The ordinary course keeps the existing absolute-clock remapping policy.
            assertEquals(standard.copy(startNode = 3), repo.getCourse(standard.id))
            assertEquals("21:30" to "22:00", TimeTableUtils.courseTimeParts(4, 1, savedEffective.timeJson))
            assertEquals("10:00" to "10:45", TimeTableUtils.courseTimeParts(3, 1, savedEffective.timeJson))
            assertEquals(period, repo.getPeriodTable(period.id))
            assertEquals(other, repo.getTable(other.id))
            assertEquals(otherEdge, repo.getCourse(otherEdge.id))
            if (policy == SchedulePolicy.CREATE_NEW) {
                assertNotNull(saved.periodTableId)
                assertNotEquals(period.id, saved.periodTableId)
                assertEquals(table.preBindSnapshotJson, saved.preBindSnapshotJson)
            } else {
                assertNull(saved.periodTableId)
                assertEquals("", saved.preBindSnapshotJson)
            }
            assertTrue(repo.restoreLastSnapshot())
            assertEquals(listOf(table, other).toSet(), repo.getAllTables().toSet())
            assertEquals(listOf(period), repo.getAllPeriodTables())
            assertEquals(originals.toSet(), repo.getAllCourses().toSet())
        }
    }

    private fun json(vararg rows: TimeSlotRow) = TimeTableUtils.buildTimeJsonFromRows(rows.toList())

    private fun course(id: Long, node: Int, irregular: Boolean) = CourseEntity(
        id = id, tableId = 1, groupId = "course-$id", courseName = "Course $id", day = 1,
        startNode = node, step = 1, startWeek = 1, endWeek = 16,
        color = "#FF6750A4", isIrregularNode = irregular,
    )
}
