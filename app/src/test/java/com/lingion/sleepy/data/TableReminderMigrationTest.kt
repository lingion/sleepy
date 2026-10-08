package com.lingion.sleepy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/** v10 → v11 migration contract: per-table reminderEnabled flag, default ON. */
class TableReminderMigrationTest {
    @Test
    fun migration_adds_reminder_enabled_column_defaulting_to_on() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE time_tables (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                st.execute("INSERT INTO time_tables VALUES (1, 'foreground')")
                st.execute("INSERT INTO time_tables VALUES (2, 'background')")
                MIGRATION_10_11_STATEMENTS.forEach(st::execute)
            }

            val columns = mutableMapOf<String, Pair<String, Int>>()
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(time_tables)").use { rs ->
                    while (rs.next()) columns[rs.getString("name")] = rs.getString("type") to rs.getInt("notnull")
                }
            }
            val column = columns["reminderEnabled"]
            assertTrue("reminderEnabled column must be added", column != null)
            assertEquals("INTEGER", column!!.first)
            assertEquals("column must be NOT NULL", 1, column.second)

            // 旧行升级后默认开启提醒(升级行为与升级前一致), 且可正常关闭
            conn.createStatement().use { st ->
                st.executeQuery("SELECT reminderEnabled FROM time_tables ORDER BY id").use { rs ->
                    assertTrue(rs.next()); assertEquals(1, rs.getInt(1))
                    assertTrue(rs.next()); assertEquals(1, rs.getInt(1))
                }
                st.execute("UPDATE time_tables SET reminderEnabled = 0 WHERE id = 2")
                st.executeQuery("SELECT reminderEnabled FROM time_tables WHERE id = 2").use { rs ->
                    assertTrue(rs.next()); assertEquals(0, rs.getInt(1))
                }
            }
        }
    }
}
