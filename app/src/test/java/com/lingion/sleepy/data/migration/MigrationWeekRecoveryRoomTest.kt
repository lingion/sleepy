package com.lingion.sleepy.data.migration

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.ImportDraftEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigrationWeekRecoveryRoomTest {
    private fun course(id: Long, tableId: Long, endWeek: Int) = CourseEntity(
        id = id, tableId = tableId, groupId = "group$id", courseName = "Course $id", day = 1,
        startNode = 1, step = 1, startWeek = 1, endWeek = endWeek, color = "#6750A4",
    )

    private fun snapshot() = MigrationDatabaseCodec.run {
        MigrationDatabaseCodec.DatabaseSnapshot(
            periodTables = listOf(PeriodTableEntity(id = 9, name = "Imported periods", createdAt = 1, updatedAt = 1).toRow()),
            timeTables = listOf(0, -3, 20, 0, 0).mapIndexed { index, weeks ->
                TimeTableEntity(id = index + 1L, name = "Imported ${index + 1}", startDate = "2026-09-07",
                    maxWeek = weeks, periodTableId = 9, isDefault = index == 1, reminderOverride = true,
                    createdAt = index + 1L).toRow()
            },
            courses = listOf(course(10, 2, 18), course(11, 2, 7), course(12, 3, 25),
                course(13, 4, -1), course(14, 5, 3)).map { it.toRow() },
            importDrafts = listOf(ImportDraftEntity(id = "draft", payloadJson = "{}", createdAt = 1, updatedAt = 1).toRow()),
        )
    }

    @Test fun overwriteRecoversInvalidDurationsBeforeDatabaseReopensAndKeepsOriginalIds() =
        verifyImport(merge = false, localTables = true)

    @Test fun mergeRecoversImportedDurationsAndKeepsLocalTablesDefaultAndReminderSelection() =
        verifyImport(merge = true, localTables = true)

    @Test fun mergeIntoEmptyDatabaseRecoversDurationsAndKeepsImportedDefaultAndReminders() =
        verifyImport(merge = true, localTables = false)

    private fun verifyImport(merge: Boolean, localTables: Boolean) = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // This connection stays open throughout import and assertions: the app's
        // onOpen repair cannot conceal a missing repair in either import path.
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        try {
            val localPeriod = PeriodTableEntity(id = 9, name = "Local periods", createdAt = 1, updatedAt = 1)
            val localTable = TimeTableEntity(id = 2, name = "Local table", startDate = "2026-09-07",
                maxWeek = 30, periodTableId = 9, isDefault = true, reminderOverride = true, createdAt = 1)
            val localCourse = course(10, 2, 30)
            if (localTables) {
                db.periodTableDao().insert(localPeriod)
                db.timeTableDao().insert(localTable)
                db.courseDao().insert(localCourse)
            }
            val backup = snapshot()
            if (merge) MigrationDatabaseCodec.applyMerge(backup, db.courseDao(), db.timeTableDao(), db.periodTableDao(), db.importDraftDao())
            else MigrationDatabaseCodec.applyOverwrite(backup, db.courseDao(), db.timeTableDao(), db.periodTableDao(), db.importDraftDao())

            val tables = db.timeTableDao().getAll()
            val importedTables = tables.filter { it.name.startsWith("Imported") }.associateBy { it.name }
            val importedPeriod = db.periodTableDao().getAll().single { it.name == "Imported periods" }
            val expectedWeeks = listOf(1, 18, 20, 1, 3)
            MigrationDatabaseCodec.run {
                assertEquals(backup.periodTables.single().toEntity().copy(id = if (merge) importedPeriod.id else 9), importedPeriod)
                backup.timeTables.forEachIndexed { index, row ->
                    val actual = importedTables.getValue(row.name)
                    val expected = row.toEntity().copy(
                        id = if (merge) actual.id else row.id,
                        maxWeek = expectedWeeks[index], periodTableId = importedPeriod.id,
                        isDefault = if (merge && localTables) false else row.isDefault,
                        reminderEnabled = if (merge && localTables) false else row.reminderEnabled,
                        reminderOverride = if (merge && localTables) null else row.reminderOverride,
                    )
                    assertEquals("Imported ${row.name} must be usable immediately", expected, actual)
                }
                val importedCourses = db.courseDao().getAll().filter { !merge || !localTables || it.id != localCourse.id }
                assertEquals(backup.courses.size, importedCourses.size)
                backup.courses.forEach { row ->
                    val actual = importedCourses.single { it.courseName == row.courseName }
                    val expectedTable = importedTables.getValue("Imported ${row.tableId}")
                    assertEquals(row.toEntity().copy(id = if (merge) actual.id else row.id, tableId = expectedTable.id), actual)
                }
                assertEquals(listOf(backup.importDrafts.single().toEntity()), db.importDraftDao().getAll())
            }
            assertEquals(if (merge && localTables) localTable.id else importedTables.getValue("Imported 2").id,
                db.timeTableDao().getDefault()!!.id)
            if (merge && localTables) {
                assertEquals(localTable, db.timeTableDao().getById(localTable.id))
                assertEquals(localCourse, db.courseDao().getById(localCourse.id))
                assertEquals(localPeriod, db.periodTableDao().getById(localPeriod.id))
            } else {
                assertEquals(backup.timeTables.size, tables.size)
                assertEquals(backup.periodTables.size, db.periodTableDao().getAll().size)
            }
            assertEquals("Source snapshot stays unchanged", listOf(0, -3, 20, 0, 0), backup.timeTables.map { it.maxWeek })
        } finally {
            db.close()
        }
    }
}
