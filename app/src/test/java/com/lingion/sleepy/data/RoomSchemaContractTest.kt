package com.lingion.sleepy.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Verifies that the checked-in Room schema matches the current database contract. */
class RoomSchemaContractTest {
    private val schema by lazy {
        val root = System.getProperty("sleepy.test.root") ?: error("sleepy.test.root is required")
        val file = File(root, "app/schemas/com.lingion.sleepy.data.AppDatabase/11.json")
        assertTrue("Room schema must be generated at $file", file.isFile)
        JSONObject(file.readText()).getJSONObject("database")
    }

    @Test
    fun exportedSchemaMatchesDatabaseVersionAndEntitySet() {
        assertEquals(11, schema.getInt("version"))
        val names = buildSet {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) add(entities.getJSONObject(i).getString("tableName"))
        }
        assertEquals(
            setOf("courses", "time_tables", "period_tables", "import_drafts", "calendar_import_records"),
            names
        )
    }

    @Test
    fun exportedSchemaContainsMigrationIntroducedColumnsAndIndexes() {
        val entities = schema.getJSONArray("entities")
        val byName = buildMap {
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                put(entity.getString("tableName"), entity)
            }
        }
        val timeTables = byName.getValue("time_tables")
        val timeColumns = buildSet {
            val fields = timeTables.getJSONArray("fields")
            for (i in 0 until fields.length()) add(fields.getJSONObject(i).getString("columnName"))
        }
        assertTrue("v8 period table binding column must remain in schema", "periodTableId" in timeColumns)
        assertTrue("v9 bind snapshot column must remain in schema", "preBindSnapshotJson" in timeColumns)
        assertTrue("v11 per-table reminder toggle column must remain in schema", "reminderEnabled" in timeColumns)

        val calendar = byName.getValue("calendar_import_records")
        val calendarColumns = buildSet {
            val fields = calendar.getJSONArray("fields")
            for (i in 0 until fields.length()) add(fields.getJSONObject(i).getString("columnName"))
        }
        assertTrue("v10 calendar mapping must include event id", "eventId" in calendarColumns)
        assertTrue("v10 calendar mapping must include fingerprint", "fingerprint" in calendarColumns)

        val indices = calendar.getJSONArray("indices")
        assertEquals(3, indices.length())
    }

    @Test
    fun migrationChainIsContinuousAndReachesExportedSchemaVersion() {
        assertEquals(11, schema.getInt("version"))
        assertEquals(3, ALL_MIGRATIONS.first().startVersion)
        ALL_MIGRATIONS.forEachIndexed { index, migration ->
            assertEquals(migration.startVersion + 1, migration.endVersion)
            if (index > 0) assertEquals(ALL_MIGRATIONS[index - 1].endVersion, migration.startVersion)
        }
        assertEquals(schema.getInt("version"), ALL_MIGRATIONS.last().endVersion)
    }
}
