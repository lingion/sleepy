package com.lingion.sleepy.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room opening and DAO round trip, using the full exported v14 schema. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JwSourceMetadataRoomMigrationTest {
    @Test fun `Room migrates v14 and keeps provenance through DAO close and reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "source-metadata-${System.nanoTime()}.db"
        val root = System.getProperty("sleepy.test.root") ?: error("sleepy.test.root is required")
        val schema = JSONObject(File(root, "app/schemas/com.lingion.sleepy.data.AppDatabase/14.json").readText())
            .getJSONObject("database")
        val metadata = """{"schemaVersion":1,"config":{"semesterStartDate":null,"firstDayOfWeek":7},"adapter":{"adapterId":"UJS_02","name":"研究生Ａ"}}"""
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            context.openOrCreateDatabase(name, 0, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                    }
                }
                db.execSQL("INSERT INTO time_tables (id,name,startDate,maxWeek,nodesPerDay,timeJson,color,isDefault,smartConfigJson,createdAt,preBindSnapshotJson) " +
                    "VALUES (5,'Existing','2026-09-07',24,12,'[]','#FF6750A4',1,'',1,'')")
                db.version = 14
            }
            val migrated = open()
            try {
                val before = migrated.timeTableDao().getById(5)!!
                assertEquals("Existing", before.name)
                assertEquals(24, before.maxWeek)
                assertEquals("", before.importSourceJson)
                migrated.timeTableDao().update(before.copy(importSourceJson = metadata))
            } finally { migrated.close() }
            val reopened = open()
            try {
                assertEquals(metadata, reopened.timeTableDao().getById(5)!!.importSourceJson)
                assertEquals(1, reopened.timeTableDao().count())
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
