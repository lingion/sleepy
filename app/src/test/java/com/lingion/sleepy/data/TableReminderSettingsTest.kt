package com.lingion.sleepy.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class TableReminderSettingsTest {
    private val root = File(requireNotNull(System.getProperty("sleepy.test.root")))
    private fun source(path: String) = File(root, "app/src/main/java/com/lingion/sleepy/$path").readText()
    private val updateSql get() = source("data/dao/TimeTableDao.kt").lineSequence()
        .first { it.contains("UPDATE time_tables SET reminderEnabled") }
        .substringAfter('"').substringBeforeLast('"')
        .replace(":enabled", "?").replace(":id", "?")

    private fun database(test: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { st ->
                st.execute("CREATE TABLE time_tables(id INTEGER PRIMARY KEY, name TEXT NOT NULL, startDate TEXT NOT NULL, periodTableId INTEGER, reminderEnabled INTEGER NOT NULL DEFAULT 1)")
                st.execute("INSERT INTO time_tables VALUES(1, 'A', '2026-09-01', 7, 1), (2, 'B', '2026-08-01', NULL, 0)")
            }
            test(db)
        }
    }
    private fun Connection.toggle(id: Long, enabled: Boolean): Int = prepareStatement(updateSql).use { st ->
        st.setInt(1, if (enabled) 1 else 0)
        st.setLong(2, id)
        st.executeUpdate()
    }
    private fun Connection.states(): List<Int> = createStatement().use { st ->
        st.executeQuery("SELECT reminderEnabled FROM time_tables ORDER BY id").use { rs ->
            buildList { while (rs.next()) add(rs.getInt(1)) }
        }
    }

    @Test fun togglesOnlyTargetTimetableAndPreservesMetadata() = database { db ->
        assertEquals(1, db.toggle(1, false))
        assertEquals(listOf(0, 0), db.states())
        assertEquals(1, db.toggle(2, true))
        assertEquals(listOf(0, 1), db.states())
        db.createStatement().use { st ->
            st.executeQuery("SELECT name, startDate, periodTableId FROM time_tables WHERE id = 1").use { rs ->
                assertTrue(rs.next())
                assertEquals("A", rs.getString(1))
                assertEquals("2026-09-01", rs.getString(2))
                assertEquals(7, rs.getInt(3))
            }
        }
    }

    @Test fun deletedTimetableIsNotRecreatedAndNewTimetableKeepsDefault() = database { db ->
        assertEquals(0, db.toggle(99, false))
        db.createStatement().use { it.execute("INSERT INTO time_tables(id,name,startDate) VALUES(3,'C','2026-09-01')") }
        assertEquals(listOf(1, 0, 1), db.states())
    }

    @Test fun togglePreservesConcurrentMetadataEdits() = database { db ->
        db.createStatement().use { it.execute("UPDATE time_tables SET name='Renamed',periodTableId=8 WHERE id=1") }
        db.toggle(1, false)
        db.createStatement().use { st ->
            st.executeQuery("SELECT name,periodTableId,reminderEnabled FROM time_tables WHERE id=1").use { rs ->
                assertTrue(rs.next())
                assertEquals("Renamed", rs.getString(1))
                assertEquals(8, rs.getInt(2))
                assertEquals(0, rs.getInt(3))
            }
        }
    }

    @Test fun repositorySchedulesAfterWriteAndDoesNotEnableGlobalReminders() {
        val method = source("data/repository/ScheduleRepository.kt")
            .substringAfter("suspend fun setTableReminderEnabled").substringBefore("suspend fun updateTable(")
        assertTrue(method.indexOf("tableDao.setReminderEnabled") < method.indexOf("onDataChanged()"))
        assertFalse(method.contains("setReminderEnabled(context"))
        assertFalse(method.contains("AppPrefs"))
    }

    @Test fun editorNoLongerOwnsReminderStateAndSettingsListIncludesAllTables() {
        val editor = source("ui/screen/mine/EditTableScreen.kt")
        assertFalse(editor.contains("reminderEnabled"))
        assertFalse(editor.contains("edit_table_reminder_enable"))
        val list = source("ui/screen/mine/TableReminderScreen.kt")
        assertTrue(list.contains("observeAllTables()"))
        assertTrue(list.contains("key = { it.id }"))
        assertTrue(list.contains("onCheckedChange = null"))
        assertTrue(source("ui/nav/SleepyNavHost.kt").contains("entry<SleepyRoute.TableReminder>"))
    }
}
