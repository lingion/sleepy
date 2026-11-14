package com.lingion.sleepy.data

import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager

class JwSourceMetadataMigrationTest {
    @Test fun `migration preserves previous rows and adds empty provenance`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE time_tables (id INTEGER PRIMARY KEY, name TEXT NOT NULL, timeJson TEXT NOT NULL)")
                sql.execute("INSERT INTO time_tables VALUES (5, 'Existing', '[]')")
                sql.execute(MIGRATION_14_15_SQL)
                sql.executeQuery("SELECT * FROM time_tables").use { row ->
                    assertTrue(row.next()); assertEquals(5, row.getInt("id"))
                    assertEquals("Existing", row.getString("name")); assertEquals("[]", row.getString("timeJson"))
                    assertEquals("", row.getString("importSourceJson")); assertFalse(row.next())
                }
                sql.execute("UPDATE time_tables SET importSourceJson = '{\"schemaVersion\":1}' WHERE id=5")
                sql.executeQuery("SELECT importSourceJson FROM time_tables").use { row ->
                    assertTrue(row.next()); assertEquals("{\"schemaVersion\":1}", row.getString(1))
                }
            }
        }
        assertEquals(APP_DATABASE_VERSION, ALL_MIGRATIONS.last().endVersion)
    }
}
