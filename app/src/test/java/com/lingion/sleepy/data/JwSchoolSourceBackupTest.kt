package com.lingion.sleepy.data

import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.data.migration.MigrationDatabaseCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertTrue(restoredRow.jwSchoolSourceVerified)
        val restoredEntity = MigrationDatabaseCodec.run { restoredRow.toEntity() }
        assertEquals(source, restoredEntity.jwSchoolSourceJson)
        assertTrue(restoredEntity.jwSchoolSourceVerified)

        val legacyEncoded = encoded.replace(",\"jwSchoolSourceVerified\":true", "")
        assertFalse(MigrationDatabaseCodec.decode(legacyEncoded).timeTables.single().jwSchoolSourceVerified)
    }
}
