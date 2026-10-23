package com.lingion.sleepy.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.util.*
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SleepyApp::class)
class CourseOccurrenceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository
    private val table = TimeTableEntity(id = 1, name = "Table", startDate = "2026-09-07", maxWeek = 16, createdAt = 0)
    private val course = CourseEntity(id = 7, tableId = 1, groupId = "math", courseName = "Math", day = 1,
        startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF6750A4")
    private val source = LocalDate.parse("2026-10-05")

    @Before fun setup() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SleepyApp>()
        SleepyApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ScheduleRepository(db)
        SleepyApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible = true }.set(app, lazyOf(repo))
        ReminderRescheduler.resetForTest()
        AppPrefs.setHolidayTransfers(app, table.id, emptyList())
        db.timeTableDao().insert(table)
        db.courseDao().insert(course)
        UndoManager.clear()
    }

    @After fun cleanup() { ReminderRescheduler.resetForTest(); db.close(); UndoManager.clear() }

    @Test fun moveAndOneUndoRestoreTheExactOriginalRows() = runBlocking {
        val occurrence = CourseOccurrences.resolve(course, source, emptyList())!!
        val replacement = CourseOccurrences.atDate(course, source.plusDays(1), table, false)
        assertTrue(repo.saveOccurrence(occurrence, replacement, repo.getCourses(1), table, emptyList()))
        assertEquals(3, repo.getCourses(1).size)
        assertTrue(repo.getCoursesForDate(table, source).isEmpty())
        assertEquals(1, repo.getCoursesForDate(table, source.plusDays(1)).size)
        assertTrue(repo.restoreLastSnapshot())
        assertEquals(listOf(course), repo.getCourses(1))
    }

    @Test fun movingMondayToTuesdayAndBackMergesTheThreeBlocksAndUndoRestoresTheSplit() = runBlocking {
        val target = source.plusDays(1)
        assertTrue(repo.saveOccurrence(CourseOccurrences.resolve(course, source, emptyList())!!,
            CourseOccurrences.atDate(course, target, table, false), repo.getCourses(1), table, emptyList()))
        val split = repo.getCourses(1)
        assertEquals(3, split.size)
        val moved = repo.getCoursesForDate(table, target).single()
        assertTrue(repo.saveOccurrence(CourseOccurrences.resolve(moved, target, emptyList())!!,
            CourseOccurrences.atDate(moved, source, table, false), split, table, emptyList()))
        assertEquals(listOf(course), repo.getCourses(1))
        assertEquals(listOf(course), repo.getCoursesForDate(table, source))
        assertTrue(repo.getCoursesForDate(table, target).isEmpty())
        assertTrue(repo.restoreLastSnapshot())
        assertEquals(split, repo.getCourses(1))
        assertEquals(listOf(moved), repo.getCoursesForDate(table, target))
    }

    @Test fun editingAnAlreadySplitCourseAlsoMergesMatchingBlocksAndPreservesOtherCourses() = runBlocking {
        val split = listOf(course.copy(endWeek = 4), course.copy(id = 8, startWeek = 5, endWeek = 5),
            course.copy(id = 9, startWeek = 6))
        val other = course.copy(id = 10, groupId = "another")
        db.courseDao().replaceAll(table.id, split + other)
        assertTrue(repo.saveCourseDraft(split, course.groupId, repo.getCourses(1), table, table.timeJson, emptyList()))
        assertEquals(setOf(course, other), repo.getCourses(1).toSet())
        assertTrue(repo.restoreLastSnapshot())
        assertEquals((split + other).toSet(), repo.getCourses(1).toSet())
    }

    @Test fun deletingOnlyOccurrenceRemovesTheEntireCourseAndUndoRestoresIt() = runBlocking {
        val single = course.copy(startWeek = 5, endWeek = 5)
        db.courseDao().update(single)
        assertTrue(repo.saveOccurrence(CourseOccurrences.resolve(single, source, emptyList())!!, null,
            repo.getCourses(1), table, emptyList()))
        assertTrue(repo.getCourses(1).isEmpty())
        assertTrue(repo.restoreLastSnapshot())
        assertEquals(listOf(single), repo.getCourses(1))
    }

    @Test fun concurrentCourseChangeRejectsTheStaleConfirmedSave() = runBlocking {
        val snapshot = repo.getCourses(1)
        db.courseDao().update(course.copy(room = "new room"))
        assertFalse(repo.saveOccurrence(CourseOccurrences.resolve(course, source, emptyList())!!, null, snapshot, table, emptyList()))
        assertEquals("new room", repo.getCourses(1).single().room)
        assertFalse(repo.canUndo)
    }

    @Test fun movingTheSameOccurrenceTwiceDoesNotLeaveAnExtraClass() = runBlocking {
        val target = source.plusDays(1)
        val first = CourseOccurrences.atDate(course, target, table, false)
        assertTrue(repo.saveOccurrence(CourseOccurrences.resolve(course, source, emptyList())!!, first, repo.getCourses(1), table, emptyList()))
        val moved = repo.getCoursesForDate(table, target).single()
        val next = target.plusDays(1)
        assertTrue(repo.saveOccurrence(CourseOccurrences.resolve(moved, target, emptyList())!!,
            CourseOccurrences.atDate(moved, next, table, false), repo.getCourses(1), table, emptyList()))
        assertEquals(3, repo.getCourses(1).size)
        assertTrue(repo.getCoursesForDate(table, source).isEmpty())
        assertTrue(repo.getCoursesForDate(table, target).isEmpty())
        assertEquals(1, repo.getCoursesForDate(table, next).size)
    }

    @Test fun transferChangeInvalidatesThePendingSave() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SleepyApp>()
        AppPrefs.setHolidayTransfers(app, 1, listOf(HolidayTransferEntry(source, source.plusDays(6), "holiday")))
        assertFalse(repo.saveOccurrence(CourseOccurrences.resolve(course, source, emptyList())!!, null,
            repo.getCourses(1), table, emptyList()))
        assertEquals(listOf(course), repo.getCourses(1))
        assertFalse(repo.canUndo)
    }
}
