package com.lingion.sleepy.data

import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.repository.resolveReminderTableScope
import com.lingion.sleepy.data.migration.MigrationDatabaseCodec
import com.lingion.sleepy.data.migration.MigrationDatabaseCodec.toRow
import com.lingion.sleepy.data.migration.MigrationDatabaseCodec.toEntity
import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager

class ReminderTableScopeTest {
    private val a = TimeTableEntity(id = 1, name = "A", startDate = "2026-09-07", isDefault = true)
    private val b = a.copy(id = 2, name = "B", isDefault = false)
    private fun enabled(tables: List<TimeTableEntity>, selected: Long?) =
        resolveReminderTableScope(tables, selected).filter { it.reminderEnabled }.map { it.id }

    @Test fun automaticScopeUsesSelectedThenDefaultThenFirst() {
        assertEquals(listOf(2L), enabled(listOf(a, b), 2))
        assertEquals(listOf(1L), enabled(listOf(b, a), 99))
        assertEquals(listOf(2L), enabled(listOf(b, a.copy(isDefault = false)), null))
        assertEquals(emptyList<Long>(), enabled(emptyList(), null))
    }

    @Test fun legacyDisabledTableAndAutomaticBackupRemainOff() {
        assertEquals(emptyList<Long>(), enabled(listOf(a.copy(reminderEnabled = false), b), 1))
        assertEquals(listOf(1L), enabled(listOf(a, b.copy(reminderEnabled = false)), 1))
    }

    @Test fun explicitEmptySelectionAndNewTablesRemainOff() {
        assertEquals(emptyList<Long>(), enabled(listOf(a.copy(reminderOverride = false), b), 2))
        assertEquals(listOf(1L), enabled(listOf(a.copy(reminderOverride = true), b), 2))
    }

    @Test fun backupRoundTripPreservesAutomaticAndManualStates() {
        val tables = listOf(a, b.copy(reminderOverride = false, reminderEnabled = false), a.copy(id = 3, reminderOverride = true))
        val snapshot = MigrationDatabaseCodec.DatabaseSnapshot(timeTables = tables.map { it.toRow() })
        val restored = MigrationDatabaseCodec.decode(MigrationDatabaseCodec.encode(snapshot)).timeTables.map { it.toEntity() }
        assertEquals(tables, restored)
        assertEquals(listOf(3L), enabled(restored, 1))
    }

    @Test fun version14MigrationPreservesExistingFlagsAndAddsUnsetOverride() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE time_tables(id INTEGER PRIMARY KEY, reminderEnabled INTEGER NOT NULL DEFAULT 1)")
                sql.execute("INSERT INTO time_tables VALUES(1,1),(2,0)")
                sql.execute(MIGRATION_13_14_SQL)
                sql.executeQuery("SELECT reminderEnabled,reminderOverride FROM time_tables ORDER BY id").use { rows ->
                    assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); assertNull(rows.getObject(2))
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); assertNull(rows.getObject(2))
                }
            }
        }
    }
}
