package com.lingion.sleepy.data

import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager

class CourseOccurrenceMigrationTest {
    @Test fun upgradePreservesExistingRowsAndDefaultsToRecurring() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE courses (id INTEGER PRIMARY KEY, courseName TEXT NOT NULL)")
                sql.execute("INSERT INTO courses VALUES (1, 'Math')")
                sql.execute(MIGRATION_12_13_SQL)
                sql.executeQuery("SELECT * FROM courses").use { row ->
                    assertTrue(row.next())
                    assertEquals("Math", row.getString("courseName"))
                    assertNull(row.getString("fixedDate"))
                }
                sql.execute("UPDATE courses SET fixedDate = '2026-10-11' WHERE id = 1")
                sql.executeQuery("SELECT fixedDate FROM courses").use { row ->
                    assertTrue(row.next())
                    assertEquals("2026-10-11", row.getString(1))
                }
            }
        }
        assertEquals(13, ALL_MIGRATIONS.last().endVersion)
    }
}
