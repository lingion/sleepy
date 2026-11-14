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
                sql.executeQuery("SELECT id, name, jwSchoolSourceJson FROM time_tables").use { row ->
                    assertTrue(row.next())
                    assertEquals(7, row.getInt("id"))
                    assertEquals("手动课表", row.getString("name"))
                    assertEquals("", row.getString("jwSchoolSourceJson"))
                    assertFalse(row.next())
                }
            }
        }
        assertEquals(16, ALL_MIGRATIONS.last().endVersion)
    }
}
