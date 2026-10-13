package com.lingion.sleepy.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.imports.*
import com.lingion.sleepy.data.parser.ScheduleParser
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.data.undo.UndoSnapshot
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite/Room transactions; no DAO mocks and no production reminder/widget dispatch. */
@RunWith(AndroidJUnit4::class)
class ImportTransactionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ScheduleRepository
    private val committed = mutableListOf<List<Long>>()
    private val times = TimeTableUtils.DEFAULT_TIME_JSON
    private val base = TimeTableEntity(id = 5, name = "Base", startDate = "2026-09-07",
        maxWeek = 16, isDefault = true, createdAt = 10)
    private val priorUndo = UndoSnapshot(tables = emptyList(), courses = emptyList(), defaultTableId = null)
    private val priorRedo = UndoSnapshot(tables = listOf(base.copy(name = "Earlier")),
        courses = emptyList(), defaultTableId = 5)

    @Before fun setUp() {
        UndoManager.clear()
        UndoManager.restoring = false
        committed.clear()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = ScheduleRepository(db, onCommitted = { ids -> committed += ids.toList() })
    }

    @After fun tearDown() {
        db.close()
        UndoManager.clear()
        UndoManager.restoring = false
    }

    private fun row(name: String = "Math", id: Long = 0, day: Int = 1,
                    first: Int = 1, last: Int = 16) = CourseEntity(
        id = id, tableId = if (id == 0L) 0 else base.id, groupId = "group-$name",
        courseName = name, day = day, startNode = 1, step = 2, startWeek = first,
        endWeek = last, color = "#FFFF0000", alias = "Alias $name", note = "Manual note", credit = 2.5f)

    private fun source(vararg rows: CourseEntity) = ScheduleParser.ParseResult(
        tableName = "Imported", startDate = base.startDate, courses = rows.toList(),
        timeJson = times, nodesPerDay = 12, maxWeek = 16)

    private fun config(table: TimeTableEntity = base) = ImportConfiguration.forExisting(table).copy(
        dateInterpretationAcknowledged = true)

    private suspend fun seed(vararg rows: CourseEntity) {
        db.timeTableDao().insert(base)
        db.courseDao().insertAll(rows.toList())
    }

    private suspend fun plan(parsed: ScheduleParser.ParseResult = source(row("Physics", day = 2)),
                             configuration: ImportConfiguration = config()): ImportPlan =
        planImport(parsed, repository.loadImportSnapshot(), configuration).also {
            assertTrue("Unexpected invalid fixture: ${it.issues}", it.canSubmit)
        }

    private fun seedHistory() {
        UndoManager.reinsertForRedoSymmetry(priorUndo)
        UndoManager.recordRedo(priorRedo)
    }

    private fun assertHistoryUnchanged() {
        assertEquals(priorUndo, UndoManager.poll())
        assertEquals(priorRedo, UndoManager.pollRedo())
    }

    private fun installFailureTrigger() {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_import BEFORE INSERT ON courses
            WHEN NEW.courseName = 'FAIL'
            BEGIN SELECT RAISE(ABORT, 'forced course insert failure'); END
        """.trimIndent())
    }

    @Test fun insertFailureRollsBackPeriodTableBindingMetadataAndCourseDeletion() = runBlocking {
        seed(row(id = 11))
        val before = repository.loadImportSnapshot()
        seedHistory()
        val p = plan(source(row("FAIL")), config().copy(name = "Renamed",
            content = ImportContent.ImportOnly, scheduleMode = ScheduleMode.CreateNew,
            periodName = "Confirmed period"))
        installFailureTrigger()

        assertTrue(repository.applyImportPlan(p) is ImportApplyResult.Failed)

        assertEquals(before, repository.loadImportSnapshot())
        assertHistoryUnchanged()
        assertTrue(committed.isEmpty())
    }

    @Test fun insertFailureRollsBackNewTableAndDefaultSwitch() = runBlocking {
        seed(row(id = 11))
        val before = repository.loadImportSnapshot()
        seedHistory()
        val p = plan(source(row("FAIL")), config().copy(destination = ImportDestination.New,
            name = "Copy", content = ImportContent.Merge, isDefault = true,
            scheduleMode = ScheduleMode.CreateNew, periodName = "Copy periods"))
        installFailureTrigger()
        assertTrue(repository.applyImportPlan(p) is ImportApplyResult.Failed)
        assertEquals(before, repository.loadImportSnapshot())
        assertHistoryUnchanged()
        assertTrue(committed.isEmpty())
    }

    @Test fun snapshotReadsRawBoundColumnsAndStalePeriodChangePreservesHistory() = runBlocking {
        val period = PeriodTableEntity(id = 9, name = "Shared", timeJson = times,
            createdAt = 1, updatedAt = 1)
        db.periodTableDao().insert(period)
        val bound = TimeTableEntity.snapshotForBind(base.copy(timeJson = "[]"), period.id)
        db.timeTableDao().insert(bound)
        val snapshot = repository.loadImportSnapshot()
        assertEquals("[]", snapshot.tables.single().timeJson)
        val p = planImport(source(row()), snapshot, config(bound))
        assertTrue(p.canSubmit)
        db.periodTableDao().update(period.copy(name = "Concurrent edit"))
        val changed = repository.loadImportSnapshot()
        seedHistory()

        assertEquals(ImportApplyResult.StalePreview, repository.applyImportPlan(p))
        assertEquals(changed, repository.loadImportSnapshot())
        assertHistoryUnchanged()
        assertTrue(committed.isEmpty())
    }

    @Test fun concurrentCourseOrDefaultChangeMakesWholeSnapshotStale() = runBlocking {
        seed(row(id = 11))
        db.timeTableDao().insert(base.copy(id = 6, name = "Other", isDefault = false))
        val p = plan()
        db.courseDao().update(row(id = 11).copy(note = "Concurrent note"))
        seedHistory()
        assertEquals(ImportApplyResult.StalePreview, repository.applyImportPlan(p))
        assertHistoryUnchanged()
        val second = plan()
        db.timeTableDao().setDefault(6)
        assertEquals(ImportApplyResult.StalePreview, repository.applyImportPlan(second))
        assertTrue(committed.isEmpty())
    }

    @Test fun canonicalOrderingDoesNotMakePreviewStale() = runBlocking {
        seed(row(id = 11), row("Biology", id = 12, day = 3))
        db.timeTableDao().insert(base.copy(id = 6, name = "Other", isDefault = false))
        val snapshot = repository.loadImportSnapshot()
        val reordered = snapshot.copy(tables = snapshot.tables.reversed(), courses = snapshot.courses.reversed())
        val p = planImport(source(row("Physics", day = 2)), reordered, config())
        assertTrue(repository.applyImportPlan(p) is ImportApplyResult.Applied)
        assertEquals(3, db.courseDao().getByTable(base.id).size)
    }

    @Test fun overlappingWeeksSplitPreservingOriginalIdAndOneUndoRedo() = runBlocking {
        val old = row(id = 11)
        seed(old)
        val before = repository.loadImportSnapshot()
        val p = plan(source(row("Physics", first = 7, last = 8)),
            config().copy(overlaps = OverlapPolicy.ReplaceExisting))
        assertEquals(ImportApplyResult.Applied(base.id), repository.applyImportPlan(p))
        val after = repository.loadImportSnapshot()
        val residuals = after.courses.filter { it.courseName == old.courseName }.sortedBy { it.startWeek }
        assertEquals(listOf(1 to 6, 9 to 16), residuals.map { it.startWeek to it.endWeek })
        assertEquals(old.id, residuals.first().id)
        assertTrue(residuals.last().id != old.id)
        assertTrue(residuals.all { it.alias == old.alias && it.note == old.note && it.credit == old.credit })
        assertEquals(listOf(emptyList<Long>()), committed)
        assertTrue(repository.restoreLastSnapshot())
        assertEquals(before, repository.loadImportSnapshot())
        assertFalse(repository.canUndo)
        assertTrue(repository.canRedo)
        assertTrue(repository.redoLastUndo())
        assertEquals(after, repository.loadImportSnapshot())
        assertTrue(repository.canUndo)
        assertFalse(repository.canRedo)
    }

    @Test fun newMergeCopiesRowsWithFreshIdsAndLeavesBaseUnchanged() = runBlocking {
        val old = row(id = 11)
        seed(old)
        val p = plan(configuration = config().copy(destination = ImportDestination.New,
            name = "Confirmed copy", isDefault = false))
        val applied = repository.applyImportPlan(p) as ImportApplyResult.Applied
        assertTrue(applied.tableId != base.id)
        assertEquals(base, db.timeTableDao().getById(base.id))
        assertEquals(listOf(old), db.courseDao().getByTable(base.id))
        val copy = db.courseDao().getByTable(applied.tableId)
        assertEquals(2, copy.size)
        assertTrue(copy.all { it.id != 0L && it.id != old.id && it.tableId == applied.tableId })
        assertEquals(base.id, db.timeTableDao().getDefault()!!.id)
        assertEquals("Confirmed copy", db.timeTableDao().getById(applied.tableId)!!.name)
    }

    @Test fun sharedScheduleEditChangesOwnerWithoutOverwritingCompatibilityColumns() = runBlocking {
        val period = PeriodTableEntity(id = 9, name = "Shared", createdAt = 1, updatedAt = 1)
        db.periodTableDao().insert(period)
        val bound = TimeTableEntity.snapshotForBind(base, period.id)
        val other = TimeTableEntity.snapshotForBind(base.copy(id = 6, name = "Other", isDefault = false), period.id)
        db.timeTableDao().insertAll(listOf(bound, other))
        val p = plan(configuration = config(bound).copy(scheduleMode = ScheduleMode.Edit,
            sharedScope = SharedScope.Shared, timeJson = times.replace("08:00", "08:05"),
            periodName = "Edited shared"))
        assertTrue(repository.applyImportPlan(p) is ImportApplyResult.Applied)
        assertEquals(bound, db.timeTableDao().getById(bound.id))
        assertEquals(other, db.timeTableDao().getById(other.id))
        val saved = db.periodTableDao().getById(period.id)!!
        assertEquals("Edited shared", saved.name)
        assertEquals(p.finalPeriodTable!!.timeJson, saved.timeJson)
        assertEquals(saved.timeJson, bound.hydratedWith(repository.effectivePeriodTable(bound.id)).timeJson)
        assertEquals(saved.timeJson, other.hydratedWith(repository.effectivePeriodTable(other.id)).timeJson)
    }

    @Test fun thisTableScheduleCreatesIndependentOwnerAndPreservesOriginalSharedOwner() = runBlocking {
        val period = PeriodTableEntity(id = 9, name = "Shared", createdAt = 1, updatedAt = 1)
        db.periodTableDao().insert(period)
        val bound = TimeTableEntity.snapshotForBind(base, period.id)
        val other = bound.copy(id = 6, name = "Other", isDefault = false)
        db.timeTableDao().insertAll(listOf(bound, other))
        val p = plan(configuration = config(bound).copy(scheduleMode = ScheduleMode.CreateNew,
            periodName = "Exact confirmed period", timeJson = times.replace("08:00", "08:05")))
        assertTrue(repository.applyImportPlan(p) is ImportApplyResult.Applied)
        assertEquals(period, db.periodTableDao().getById(period.id))
        assertEquals(other, db.timeTableDao().getById(other.id))
        val saved = db.timeTableDao().getById(base.id)!!
        assertTrue(saved.periodTableId != period.id)
        assertEquals(bound.preBindSnapshotJson, saved.preBindSnapshotJson)
        assertEquals(bound.timeJson, saved.timeJson)
        assertEquals("Exact confirmed period", repository.effectivePeriodTable(base.id)!!.name)
    }

    @Test fun defaultSelectionIsIndependentAndFirstTableBecomesDefault() = runBlocking {
        val initial = config().copy(baseTableId = 0, destination = ImportDestination.New,
            content = ImportContent.ImportOnly, name = "First", isDefault = false)
        val first = repository.applyImportPlan(plan(configuration = initial)) as ImportApplyResult.Applied
        assertEquals(first.tableId, db.timeTableDao().getDefault()!!.id)
        val current = db.timeTableDao().getById(first.tableId)!!
        val next = plan(configuration = config(current).copy(destination = ImportDestination.New,
            name = "Second", isDefault = true))
        val second = repository.applyImportPlan(next) as ImportApplyResult.Applied
        assertEquals(second.tableId, db.timeTableDao().getDefault()!!.id)
        assertFalse(db.timeTableDao().getById(first.tableId)!!.isDefault)
    }

    @Test fun duplicateOnlyNoopPreservesUndoRedoAndDoesNotRefresh() = runBlocking {
        seed(row(id = 11))
        val before = repository.loadImportSnapshot()
        seedHistory()
        val p = plan(source(row()))
        assertEquals(1, p.skippedCount)
        assertEquals(ImportApplyResult.NoChanges, repository.applyImportPlan(p))
        assertEquals(before, repository.loadImportSnapshot())
        assertHistoryUnchanged()
        assertTrue(committed.isEmpty())
    }

    @Test fun invalidAndForgedPlansCannotWriteOrConsumeUndo() = runBlocking {
        seed(row(id = 11))
        val before = repository.loadImportSnapshot()
        seedHistory()
        val invalid = planImport(source(row()), before, config().copy(name = ""))
        assertEquals(ImportApplyResult.Invalid, repository.applyImportPlan(invalid))
        val valid = plan()
        assertEquals(ImportApplyResult.Invalid, repository.applyImportPlan(valid.copy(
            finalTable = valid.finalTable.copy(name = "Unconfirmed"))))
        assertEquals(ImportApplyResult.Invalid, repository.applyImportPlan(invalid.copy(issues = emptyList())))
        assertEquals(before, repository.loadImportSnapshot())
        assertHistoryUnchanged()
        assertTrue(committed.isEmpty())
    }

    @Test fun repeatedConcurrentSubmissionAppliesOnlyOnce() = runBlocking {
        seed(row(id = 11))
        val p = plan()
        val results = coroutineScope {
            val first = async { repository.applyImportPlan(p) }
            val second = async { repository.applyImportPlan(p) }
            listOf(first.await(), second.await())
        }
        assertEquals(1, results.count { it is ImportApplyResult.Applied })
        assertEquals(1, results.count { it == ImportApplyResult.StalePreview })
        assertEquals(2, db.courseDao().getByTable(base.id).size)
        assertEquals(1, committed.size)
    }

    @Test fun deletedAlarmIdsAreDeliveredOnlyAfterCommitAndRefreshFailureIsWarning() = runBlocking {
        seed(row(id = 11))
        var callbacks = 0
        repository = ScheduleRepository(db, onCommitted = { deleted ->
            callbacks++
            assertFalse(db.inTransaction())
            assertEquals(listOf(11L), deleted)
            assertNull(db.courseDao().getById(11))
            assertTrue(UndoManager.hasSnapshot)
            throw IllegalStateException("Refresh unavailable")
        })
        val result = repository.applyImportPlan(plan(configuration = config().copy(
            content = ImportContent.ImportOnly))) as ImportApplyResult.Applied
        assertEquals(1, callbacks)
        assertEquals(base.id, result.tableId)
        assertTrue(result.warnings.isNotEmpty())
        assertEquals("Physics", db.courseDao().getByTable(base.id).single().courseName)
        assertTrue(repository.canUndo)
    }

    @Test fun successfulImportConsumesItsDraftAndRejectsReplay() = runBlocking {
        seed(row(id = 11))
        db.importDraftDao().insert(com.lingion.sleepy.data.entity.ImportDraftEntity(
            id = "draft", payloadJson = "{}", createdAt = 1, updatedAt = 1))
        val p = plan()
        assertTrue(repository.applyImportPlan(p, "draft") is ImportApplyResult.Applied)
        assertNull(db.importDraftDao().getById("draft"))
        val refreshed = plan()
        assertEquals(ImportApplyResult.StalePreview, repository.applyImportPlan(refreshed, "draft"))
        assertEquals(2, db.courseDao().getByTable(base.id).size)
    }

    @Test fun failedImportKeepsItsDraftAlongWithOriginalCourses() = runBlocking {
        seed(row(id = 11))
        val draft = com.lingion.sleepy.data.entity.ImportDraftEntity(
            id = "draft", payloadJson = "{}", createdAt = 1, updatedAt = 1)
        db.importDraftDao().insert(draft)
        val p = plan(source(row("FAIL")))
        installFailureTrigger()
        assertTrue(repository.applyImportPlan(p, "draft") is ImportApplyResult.Failed)
        assertEquals(draft, db.importDraftDao().getById("draft"))
        assertEquals(listOf(row(id = 11)), db.courseDao().getByTable(base.id))
    }

    @Test fun cancellationIsRethrownAfterCommitAndDoesNotLoseUndo() = runBlocking {
        seed(row(id = 11))
        repository = ScheduleRepository(db, onCommitted = { throw CancellationException("cancel refresh") })
        val p = plan()
        try {
            repository.applyImportPlan(p)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(2, db.courseDao().getByTable(base.id).size)
            assertTrue(repository.canUndo)
        }
    }
}
