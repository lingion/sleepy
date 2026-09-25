package com.lingion.sleepy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/** v9 → v10 migration contract for tracking only Sleepy-created calendar events. */
class CalendarImportMigrationTest {
    @Test
    fun migration_creates_calendar_mapping_without_changing_existing_rows() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE time_tables (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                st.execute("INSERT INTO time_tables VALUES (1, 'existing')")
                MIGRATION_9_10_STATEMENTS.forEach(st::execute)
            }

            val columns = mutableMapOf<String, Pair<String, Int>>()
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(calendar_import_records)").use { rs ->
                    while (rs.next()) columns[rs.getString("name")] = rs.getString("type") to rs.getInt("notnull")
                }
                st.executeQuery("SELECT name FROM time_tables WHERE id = 1").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("existing", rs.getString(1))
                }
            }
            listOf("tableId", "courseId", "eventDate", "calendarId", "eventId", "batchId", "fingerprint", "alarmRequested", "createdAt")
                .forEach { assertNotNull("missing calendar mapping column $it", columns[it]) }

            conn.createStatement().use { st ->
                st.execute("INSERT INTO calendar_import_records (tableId, courseId, eventDate, calendarId, eventId, batchId, fingerprint, alarmRequested, createdAt) VALUES (1, 2, '2026-09-23', 3, 4, 'b', 'f', 0, 1)")
                val duplicateRejected = runCatching {
                    st.execute("INSERT INTO calendar_import_records (tableId, courseId, eventDate, calendarId, eventId, batchId, fingerprint, alarmRequested, createdAt) VALUES (1, 2, '2026-09-23', 3, 5, 'b2', 'f2', 0, 2)")
                }.isFailure
                assertTrue("same table/course/date/calendar must be unique", duplicateRejected)
            }
        }
    }
}
