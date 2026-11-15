package com.lingion.sleepy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

class JwSchoolSourceMigrationTest {
    @Test
    fun `migration preserves existing timetables with no JW source`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE time_tables (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                sql.execute("INSERT INTO time_tables VALUES (7, '手动课表')")
                sql.execute(MIGRATION_15_16_SQL)
                sql.execute("UPDATE time_tables SET jwSchoolSourceJson = '{\"schemaVersion\":1}' WHERE id = 7")
                sql.execute(MIGRATION_16_17_SQL)
                sql.executeQuery("SELECT id, name, jwSchoolSourceJson, jwSchoolSourceVerified FROM time_tables").use { row ->
                    assertTrue(row.next())
                    assertEquals(7, row.getInt("id"))
                    assertEquals("手动课表", row.getString("name"))
                    assertEquals("{\"schemaVersion\":1}", row.getString("jwSchoolSourceJson"))
                    assertEquals(0, row.getInt("jwSchoolSourceVerified"))
                    assertFalse(row.next())
                }
            }
        }
        assertEquals(APP_DATABASE_VERSION, ALL_MIGRATIONS.last().endVersion)
    }
}
