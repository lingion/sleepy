package com.lingion.sleepy.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the registered v9-to-v10 migration with Room's runtime schema validation. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun migrationFromNineToTenPreservesCourseRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-9-to-10-${System.nanoTime()}.db"
        val schema = context.assets.open("com.lingion.sleepy.data.AppDatabase/10.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }

        try {
            context.openOrCreateDatabase(name, 0, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    if (table == "calendar_import_records") continue
                    db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                    }
                }
                db.execSQL(
                    "INSERT INTO time_tables (id, name, startDate, maxWeek, nodesPerDay, timeJson, color, isDefault, smartConfigJson, createdAt, preBindSnapshotJson) " +
                        "VALUES (1, 'Old term', '2026-01-01', 20, 12, '{}', '#FF6750A4', 1, '', 1, '')"
                )
                db.execSQL(
                    "INSERT INTO courses (id, groupId, tableId, courseName, teacher, room, note, alias, day, startNode, step, startWeek, endWeek, type, color, colorMode, ownTime, isIrregularNode, isIrregularTime, startTime, endTime, credit, level) " +
                        "VALUES (7, 'g', 1, 'Old course', '', '', '', '', 1, 1, 2, 1, 16, 0, '#FF6750A4', 0, 0, 0, 0, '', '', 0, 0)"
                )
                db.version = 9
            }

            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
            try {
                migrated.openHelper.writableDatabase.query("SELECT courseName FROM courses WHERE id = 7").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Old course", it.getString(0))
                }
                migrated.openHelper.writableDatabase.query("SELECT COUNT(*) FROM calendar_import_records").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
