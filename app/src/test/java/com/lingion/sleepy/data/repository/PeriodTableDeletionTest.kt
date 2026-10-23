package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.entity.PeriodTableEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager

class PeriodTableDeletionTest {
    private val period = PeriodTableEntity(
        id = 7, name = "Shared", nodesPerDay = 2,
        timeJson = """[{"node":0,"start":"07:00","end":"07:30","edgeClass":"Before"},{"node":1,"start":"08:10","end":"09:00"},{"node":2,"start":"09:20","end":"10:10"},{"node":3,"start":"19:00","end":"20:00","edgeClass":"After"}]""",
        smartConfigJson = """{"periodMinutes":50,"startTime":"08:10","totalPeriods":2}""",
    )
    private fun table(id: Long, binding: Long?) = TimeTableEntity(
        id = id, name = "Table $id", startDate = "2026-09-01", maxWeek = 18,
        nodesPerDay = 1, timeJson = "old local times", smartConfigJson = "old local config",
        periodTableId = binding, preBindSnapshotJson = """{"n":1,"t":"before binding","s":"old"}""",
        isDefault = id == 1L, reminderEnabled = id != 2L, color = "#123456", createdAt = id,
    )

    private class SqlStore(
        val source: PeriodTableEntity,
        val originals: List<TimeTableEntity>,
        val failAt: String? = null,
    ) : PeriodTableDeletionStore, AutoCloseable {
        private val db = DriverManager.getConnection("jdbc:sqlite::memory:")
        var captures = 0
        var undoTables = emptyList<TimeTableEntity>()
        var undoPeriod: PeriodTableEntity? = null
        val writes = mutableListOf<TimeTableEntity>()
        init {
            db.createStatement().use {
                it.execute("CREATE TABLE periods(id INTEGER PRIMARY KEY)")
                it.execute("INSERT INTO periods VALUES (${source.id})")
                it.execute("CREATE TABLE tables(id INTEGER PRIMARY KEY, period INTEGER, nodes INTEGER, time TEXT, smart TEXT, snapshot TEXT)")
            }
            originals.forEach { write(it) }
        }
        private fun write(table: TimeTableEntity) {
            db.prepareStatement("INSERT OR REPLACE INTO tables VALUES(?,?,?,?,?,?)").use {
                it.setLong(1, table.id)
                it.setObject(2, table.periodTableId)
                it.setInt(3, table.nodesPerDay)
                it.setString(4, table.timeJson)
                it.setString(5, table.smartConfigJson)
                it.setString(6, table.preBindSnapshotJson)
                it.executeUpdate()
            }
        }
        fun tables(): List<TimeTableEntity> = db.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM tables ORDER BY id").use { rows ->
                buildList {
                    while (rows.next()) {
                        val binding = rows.getLong("period").let { if (rows.wasNull()) null else it }
                        add(originals.first { it.id == rows.getLong("id") }.copy(
                            periodTableId = binding, nodesPerDay = rows.getInt("nodes"),
                            timeJson = rows.getString("time"), smartConfigJson = rows.getString("smart"),
                            preBindSnapshotJson = rows.getString("snapshot"),
                        ))
                    }
                }
            }
        }
        override suspend fun atomic(block: suspend () -> Boolean): Boolean {
            db.autoCommit = false
            return try { block().also { db.commit() } } catch (e: Throwable) {
                db.rollback()
                throw e
            } finally { db.autoCommit = true }
        }
        override suspend fun getPeriod(id: Long): PeriodTableEntity? = db.createStatement().use {
            it.executeQuery("SELECT id FROM periods WHERE id=$id").use { rows -> if (rows.next()) source else null }
        }
        override suspend fun getBoundTables(id: Long) = tables().filter { it.periodTableId == id }
        override suspend fun captureUndo() {
            captures++
            undoTables = tables()
            undoPeriod = getPeriod(source.id)
        }
        override suspend fun updateTable(table: TimeTableEntity) {
            check(captures == 1)
            writes.add(table)
            write(table)
            if (failAt == "table${table.id}") error("Injected update failure")
        }
        override suspend fun deletePeriod(id: Long) {
            check(captures == 1)
            db.createStatement().use { it.execute("DELETE FROM periods WHERE id=$id") }
            if (failAt == "delete") error("Injected deletion failure")
        }
        override fun close() = db.close()
    }

    @Test fun deletionPreservesFullEffectiveScheduleAndMetadataForEveryBoundTable() = runBlocking {
        val originals = listOf(table(1, 7), table(2, 7), table(3, null), table(4, 8))
        // Both automatic and manual schedules must retain their exact saved configuration.
        for (source in listOf(period, period.copy(smartConfigJson = ""))) {
            SqlStore(source, originals).use { store ->
                assertTrue(deletePeriodTableAtomically(store, 7))
                assertNull(store.getPeriod(7))
                val expected = originals.take(2).map {
                    it.hydratedWith(source).copy(periodTableId = null, preBindSnapshotJson = "")
                }
                assertEquals(expected, store.writes)
                assertEquals(expected + originals.drop(2), store.tables())
                assertEquals(1, store.captures)
                assertEquals(originals, store.undoTables)
                assertEquals(source, store.undoPeriod)
                // A later bind/unbind must restore the newly embedded times, not the stale snapshot.
                val rebound = TimeTableEntity.snapshotForBind(store.tables().first(), 8)
                assertEquals(expected.first(), TimeTableEntity.restoredForUnbind(rebound))
            }
        }
    }

    @Test fun failuresRollBackAllTimetablesAndKeepTheSharedSchedule() = runBlocking {
        val originals = listOf(table(1, 7), table(2, 7), table(3, null))
        for (stage in listOf("table1", "table2", "delete")) {
            SqlStore(period, originals, stage).use { store ->
                try {
                    deletePeriodTableAtomically(store, 7)
                    fail("Expected failure at $stage")
                } catch (_: IllegalStateException) { }
                assertEquals(period, store.getPeriod(7))
                assertEquals(originals, store.tables())
            }
        }
    }

    @Test fun unboundDeletionWorksAndRepeatedDeletionDoesNotReplaceUndo() = runBlocking {
        val originals = listOf(table(1, null))
        SqlStore(period, originals).use { store ->
            assertTrue(deletePeriodTableAtomically(store, 7))
            assertFalse(deletePeriodTableAtomically(store, 7))
            assertEquals(1, store.captures)
            assertEquals(originals, store.tables())
            assertTrue(store.writes.isEmpty())
        }
    }

    @Test fun abandoningUnsavedScheduleStillProtectsExistingBindings() = runBlocking {
        val originals = listOf(table(1, 7))
        SqlStore(period, originals).use { store ->
            assertFalse(deletePeriodTableAtomically(store, 7, onlyIfUnbound = true))
            assertEquals(0, store.captures)
            assertEquals(originals, store.tables())
            assertEquals(period, store.getPeriod(7))
        }
        SqlStore(period, emptyList()).use { store ->
            assertTrue(deletePeriodTableAtomically(store, 7, onlyIfUnbound = true))
            assertNull(store.getPeriod(7))
        }
    }
}
