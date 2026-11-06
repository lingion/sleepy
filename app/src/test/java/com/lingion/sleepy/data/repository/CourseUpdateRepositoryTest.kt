package com.lingion.sleepy.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.diff.diffImportedCourses
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SleepyApp::class)
class CourseUpdateRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository
    private val table = TimeTableEntity(id = 1, name = "Local", startDate = "2026-09-07", createdAt = 0)
    private val other = table.copy(id = 2, name = "Selected", isDefault = true)
    private val old = CourseEntity(id = 42, tableId = 1, groupId = "math", courseName = "Math",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF00FF00", alias = "My math")

    @Before fun setup() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SleepyApp>()
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        ReminderRescheduler.resetForTest()
        db.timeTableDao().insert(table)
        db.timeTableDao().insert(other)
        db.courseDao().insert(old)
        UndoManager.clear()
    }

    @After fun cleanup() { ReminderRescheduler.resetForTest(); db.close(); UndoManager.clear() }

    @Test fun updateKeepsGroupsSettingsSelectionAndUndoRedo() = runBlocking {
        val fresh = old.copy(id = 0, groupId = "", alias = "", room = "New room")
        val diff = diffImportedCourses(listOf(old), listOf(fresh, fresh.copy(day = 3)))
        repo.updateImportedCourses(1, diff.merged)
        val updated = repo.getCourses(1)
        assertEquals(2, updated.size)
        assertEquals(setOf("math"), updated.map { it.groupId }.toSet())
        assertEquals(setOf("My math"), updated.map { it.alias }.toSet())
        assertEquals(42L, updated.single { it.day == 1 }.id)
        assertEquals(table, repo.getTable(1))
        assertEquals(other, repo.getDefaultTable())
        assertTrue(repo.restoreLastSnapshot())
        assertEquals(listOf(old), repo.getCourses(1))
        assertEquals(other, repo.getDefaultTable())
        assertTrue(repo.redoLastUndo())
        assertEquals(updated.toSet(), repo.getCourses(1).toSet())
    }

    @Test fun foreignCourseIdCannotBeMovedIntoTheUpdatedTable() = runBlocking {
        val foreign = old.copy(id = 99, tableId = 2)
        db.courseDao().insert(foreign)
        try {
            repo.updateImportedCourses(1, listOf(foreign))
            fail("Foreign IDs must be rejected before any write")
        } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(old), repo.getCourses(1))
        assertEquals(listOf(foreign), repo.getCourses(2))
        assertFalse(repo.canUndo)
    }
}
