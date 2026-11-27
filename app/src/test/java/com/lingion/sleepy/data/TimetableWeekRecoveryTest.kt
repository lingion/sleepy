package com.lingion.sleepy.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TimetableWeekRecoveryTest {
    private lateinit var context: Application

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        resetDatabaseInstance()
        context.deleteDatabase("sleepy.db")
        UndoManager.clear()
    }

    @After fun cleanup() {
        resetDatabaseInstance()
        context.deleteDatabase("sleepy.db")
        UndoManager.clear()
    }

    private fun resetDatabaseInstance() {
        AppDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }.let { field ->
            (field.get(null) as? AppDatabase)?.close()
            field.set(null, null)
        }
    }

    private fun table(id: Long, weeks: Int) = TimeTableEntity(
        id = id, name = "Table $id", startDate = "2026-09-07", maxWeek = weeks,
        isDefault = id == 2L,
    )

    private fun course(id: Long, tableId: Long, endWeek: Int) = CourseEntity(
        id = id, tableId = tableId, groupId = "group$id", courseName = "Course $id", day = 1,
        startNode = 1, step = 1, startWeek = 1, endWeek = endWeek, color = "#6750A4",
    )

    @Test fun openingExistingDatabaseRepairsOnlyInvalidWeekCountsAndPreservesCourses() = runBlocking {
        val tables = listOf(table(1, 0), table(2, -3), table(3, 20), table(4, 0))
        val courses = listOf(course(1, 2, 16), course(2, 2, 7), course(3, 3, 25), course(4, 4, -1))
        val seed = Room.databaseBuilder(context, AppDatabase::class.java, "sleepy.db").build()
        try {
            seed.timeTableDao().insertAll(tables)
            seed.courseDao().insertAll(courses)
        } finally {
            seed.close()
        }

        val repaired = AppDatabase.get(context)
        assertEquals(tables.map { old -> old.copy(maxWeek = when (old.id) { 2L -> 16; 3L -> 20; else -> 1 }) },
            repaired.timeTableDao().getAll().sortedBy { it.id })
        assertEquals(courses, repaired.courseDao().getAll().sortedBy { it.id })
        assertEquals(2L, repaired.timeTableDao().getDefault()!!.id)
        assertFalse("Automatic recovery must not consume an undo entry", UndoManager.hasSnapshot)

        resetDatabaseInstance()
        assertEquals(listOf(1, 16, 20, 1), AppDatabase.get(context).timeTableDao().getAll().sortedBy { it.id }.map { it.maxWeek })
    }

    @Test fun repositoryRejectsInvalidCreateAndMetadataWritesBeforeUndoOrMutation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val original = table(1, 20)
            val period = PeriodTableEntity(id = 7, name = "Shared")
            db.timeTableDao().insert(original)
            db.periodTableDao().insert(period)
            val repo = ScheduleRepository(db)
            for (weeks in listOf(0, -3)) {
                val bad = original.copy(maxWeek = weeks)
                val operations: List<Pair<String, suspend () -> Unit>> = listOf(
                    "insert" to { repo.insertTable(bad.copy(id = 0)); Unit },
                    "update" to { repo.updateTable(bad) },
                    "metadata" to { repo.updateTableMetadata(bad) },
                    "metadata with period" to { repo.updateTableMetadataWithPeriodTable(bad, period) },
                    "metadata and binding" to { repo.updateTableMetadataAndBind(bad, period.id) },
                    "metadata and remapping" to { repo.updateTableRemappingCourses(bad) },
                    "import" to { repo.importNewTable(bad, emptyList(), period); Unit },
                )
                for ((name, operation) in operations) {
                    val failure = runCatching { operation() }.exceptionOrNull()
                    assertTrue("$name must reject weeks=$weeks with a validation error; got $failure", failure is IllegalArgumentException)
                    assertEquals("$name must preserve the existing table", listOf(original), db.timeTableDao().getAll())
                    assertEquals(listOf(period), db.periodTableDao().getAll())
                    assertFalse("$name must validate before capturing undo", UndoManager.hasSnapshot)
                }
            }
        } finally {
            db.close()
        }
    }
}
