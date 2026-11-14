package com.lingion.sleepy.data

import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.migration.MigrationDatabaseCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JwSchoolSourceBackupTest {
    @Test
    fun `database backup codec preserves JW school source`() {
        val source = """{"schemaVersion":1,"sourceType":"jw"}"""
        val table = TimeTableEntity(
            name = "备份课表",
            startDate = "2026-09-07",
            jwSchoolSourceJson = source,
            jwSchoolSourceVerified = true,
        )
        val row = MigrationDatabaseCodec.run { table.toRow() }
        val encoded = MigrationDatabaseCodec.encode(
            MigrationDatabaseCodec.DatabaseSnapshot(timeTables = listOf(row))
        )
        val restoredRow = MigrationDatabaseCodec.decode(encoded).timeTables.single()

        assertEquals(source, restoredRow.jwSchoolSourceJson)
        val restoredEntity = MigrationDatabaseCodec.run { restoredRow.toEntity() }
        assertEquals(source, restoredEntity.jwSchoolSourceJson)
        assertFalse(restoredEntity.jwSchoolSourceVerified)

        val forgedBackup = encoded.replace(
            "\"reminderEnabled\":true",
            "\"jwSchoolSourceVerified\":true,\"reminderEnabled\":true",
        )
        assertFalse(forgedBackup == encoded)
        val forgedEntity = MigrationDatabaseCodec.run {
            MigrationDatabaseCodec.decode(forgedBackup).timeTables.single().toEntity()
        }
        assertFalse(forgedEntity.jwSchoolSourceVerified)
        assertFalse(encoded.contains("jwSchoolSourceVerified"))
    }
}
