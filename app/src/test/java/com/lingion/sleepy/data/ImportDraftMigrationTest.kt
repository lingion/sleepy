package com.lingion.sleepy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/** v6 → v7 import_drafts schema and data-preservation contract. */
class ImportDraftMigrationTest {

    private fun createV6Schema(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE time_tables (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    startDate TEXT NOT NULL,
                    maxWeek INTEGER NOT NULL DEFAULT 20,
                    nodesPerDay INTEGER NOT NULL DEFAULT 12,
                    timeJson TEXT NOT NULL,
                    color TEXT NOT NULL DEFAULT '#FF6750A4',
                    isDefault INTEGER NOT NULL DEFAULT 0,
                    smartConfigJson TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE courses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    groupId TEXT NOT NULL,
                    tableId INTEGER NOT NULL,
                    courseName TEXT NOT NULL,
                    teacher TEXT NOT NULL DEFAULT '',
                    room TEXT NOT NULL DEFAULT '',
                    note TEXT NOT NULL DEFAULT '',
                    alias TEXT NOT NULL DEFAULT '',
                    day INTEGER NOT NULL,
                    startNode INTEGER NOT NULL,
                    step INTEGER NOT NULL,
                    startWeek INTEGER NOT NULL,
                    endWeek INTEGER NOT NULL,
                    type INTEGER NOT NULL DEFAULT 0,
                    color TEXT NOT NULL,
                    colorMode INTEGER NOT NULL DEFAULT 0,
                    ownTime INTEGER NOT NULL DEFAULT 0,
                    isIrregularNode INTEGER NOT NULL DEFAULT 0,
                    isIrregularTime INTEGER NOT NULL DEFAULT 0,
                    startTime TEXT NOT NULL DEFAULT '',
                    endTime TEXT NOT NULL DEFAULT '',
                    credit REAL NOT NULL DEFAULT 0,
                    level INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            st.execute("INSERT INTO time_tables (name, startDate, timeJson, createdAt) VALUES ('旧课表', '2026-09-01', '{}', 11)")
            st.execute("INSERT INTO courses (groupId, tableId, courseName, day, startNode, step, startWeek, endWeek, color) VALUES ('g1', 1, '旧课程', 1, 1, 2, 1, 16, '#FF000000')")
        }
    }

    private fun openInMemory(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:")

    @Test
    fun migration_creates_draft_table_with_required_constraints() {
        openInMemory().use { conn ->
            createV6Schema(conn)
            conn.createStatement().use { st -> MIGRATION_6_7_STATEMENTS.forEach(st::execute) }

            val columns = mutableMapOf<String, Triple<String, Int, String?>>()
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(import_drafts)").use { rs ->
                    while (rs.next()) {
                        columns[rs.getString("name")] = Triple(
                            rs.getString("type"),
                            rs.getInt("notnull"),
                            rs.getString("dflt_value")
                        )
                    }
                }
            }
            assertEquals("TEXT", columns["id"]?.first)
            assertEquals(1, columns["id"]?.second)
            assertEquals("TEXT", columns["payloadJson"]?.first)
            assertEquals(1, columns["payloadJson"]?.second)
            assertEquals("INTEGER", columns["updatedAt"]?.first)
            assertNotNull("draft table must exist", columns["id"])
        }
    }

    @Test
    fun migration_preserves_existing_tables_and_rows() {
        openInMemory().use { conn ->
            createV6Schema(conn)
            conn.createStatement().use { st -> MIGRATION_6_7_STATEMENTS.forEach(st::execute) }
            conn.createStatement().use { st ->
                st.executeQuery("SELECT name FROM time_tables WHERE id = 1").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("旧课表", rs.getString("name"))
                }
                st.executeQuery("SELECT courseName FROM courses WHERE id = 1").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("旧课程", rs.getString("courseName"))
                }
            }
        }
    }

    @Test
    fun migration_chain_reaches_current_version_without_gaps() {
        val chain = ALL_MIGRATIONS
        assertEquals(3, chain.first().startVersion)
        chain.forEachIndexed { index, migration ->
            assertEquals(migration.startVersion + 1, migration.endVersion)
            if (index > 0) assertEquals(chain[index - 1].endVersion, migration.startVersion)
        }
        assertEquals(10, chain.last().endVersion)
    }
}
