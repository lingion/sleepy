package com.lingion.sleepy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/** v7 -> v8 独立时间节次表迁移契约(v6→v7 已被 issue#39 import_drafts 占用)。 */
class PeriodTableMigrationTest {

    private fun openInMemory(): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:")

    private fun createV6Schema(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE time_tables (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    startDate TEXT NOT NULL,
                    maxWeek INTEGER NOT NULL,
                    nodesPerDay INTEGER NOT NULL,
                    timeJson TEXT NOT NULL,
                    color TEXT NOT NULL,
                    isDefault INTEGER NOT NULL,
                    smartConfigJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.execute(
                """
                INSERT INTO time_tables
                  (id, name, startDate, maxWeek, nodesPerDay, timeJson, color,
                   isDefault, smartConfigJson, createdAt)
                VALUES
                  (7, '春季课表', '2026-02-23', 20, 12, '{"1":["08:00","08:45"]}', '#111111', 1, '{"mode":"smart"}', 1000),
                  (12, '考试周课表', '2026-02-23', 4, 8, '{"1":["09:00","09:40"]}', '#222222', 0, '', 2000)
                """.trimIndent()
            )
        }
    }

    /** v8 末态 = v6 schema + periodTableId 列 (7→8 迁移的产物), 供 8→9 迁移测试打底 */
    private fun createV8Schema(conn: Connection) {
        createV6Schema(conn)
        MIGRATION_7_8_SCHEMA_STATEMENTS.forEach { sql ->
            conn.createStatement().use { statement -> statement.execute(sql) }
        }
    }

    @Test
    fun migration_creates_period_table_schema_and_backfills_each_timetable() {
        val conn = openInMemory()
        try {
            createV6Schema(conn)
            MIGRATION_7_8_SCHEMA_STATEMENTS.forEach { sql ->
                conn.createStatement().use { statement -> statement.execute(sql) }
            }
            conn.createStatement().use { st ->
                conn.createStatement().use { query ->
                    query.executeQuery(
                        "SELECT id, name, nodesPerDay, timeJson, smartConfigJson, createdAt FROM time_tables ORDER BY id"
                    ).use { rows ->
                        while (rows.next()) {
                            val id = rows.getLong("id")
                            st.executeUpdate(
                                "INSERT INTO period_tables (id, name, nodesPerDay, timeJson, smartConfigJson, createdAt, updatedAt) VALUES " +
                                    "($id, '${rows.getString("name")}', ${rows.getInt("nodesPerDay")}, '${rows.getString("timeJson")}', '${rows.getString("smartConfigJson")}', ${rows.getLong("createdAt")}, ${rows.getLong("createdAt")})"
                            )
                            st.executeUpdate("UPDATE time_tables SET periodTableId = $id WHERE id = $id")
                        }
                    }
                }
            }
            conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM period_tables").use { assertTrue(it.next()); assertEquals(2, it.getInt(1)) }
                st.executeQuery("SELECT id, periodTableId FROM time_tables ORDER BY id").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(7L, rows.getLong("id"))
                    assertEquals(7L, rows.getLong("periodTableId"))
                    assertTrue(rows.next())
                    assertEquals(12L, rows.getLong("id"))
                    assertEquals(12L, rows.getLong("periodTableId"))
                }
                st.executeQuery("SELECT name, nodesPerDay, timeJson, smartConfigJson, createdAt, updatedAt FROM period_tables WHERE id = 7").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("春季课表", rows.getString("name"))
                    assertEquals(12, rows.getInt("nodesPerDay"))
                    assertEquals("{\"1\":[\"08:00\",\"08:45\"]}", rows.getString("timeJson"))
                    assertEquals("{\"mode\":\"smart\"}", rows.getString("smartConfigJson"))
                    assertEquals(1000L, rows.getLong("createdAt"))
                    assertEquals(1000L, rows.getLong("updatedAt"))
                }
            }
            val columns = mutableSetOf<String>()
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(time_tables)").use { rows ->
                    while (rows.next()) columns += rows.getString("name")
                }
            }
            assertTrue("periodTableId column must be added", "periodTableId" in columns)
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(period_tables)").use { rows ->
                    assertTrue(rows.next())
                    assertNotNull(rows.getString("name"))
                }
            }
        } finally {
            conn.close()
        }
    }

    @Test
    fun migration_8_9_adds_pre_bind_snapshot_column_with_empty_default() {
        val conn = openInMemory()
        try {
            createV8Schema(conn)
            MIGRATION_8_9_STATEMENTS.forEach { sql ->
                conn.createStatement().use { statement -> statement.execute(sql) }
            }
            val columns = mutableSetOf<String>()
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(time_tables)").use { rows ->
                    while (rows.next()) columns += rows.getString("name")
                }
            }
            assertTrue("preBindSnapshotJson column must be added", "preBindSnapshotJson" in columns)
            // 旧行升级后快照列 = 空串(无快照), NOT NULL DEFAULT '' 保证不炸非空约束
            conn.createStatement().use { st ->
                st.executeQuery("SELECT preBindSnapshotJson FROM time_tables ORDER BY id").use { rows ->
                    assertTrue(rows.next()); assertEquals("", rows.getString(1))
                    assertTrue(rows.next()); assertEquals("", rows.getString(1))
                }
            }
        } finally {
            conn.close()
        }
    }

    @Test
    fun migration_chain_reaches_database_version_nine() {
        assertEquals(10, ALL_MIGRATIONS.last().endVersion)
        assertTrue(ALL_MIGRATIONS.any { it.startVersion == 7 && it.endVersion == 8 })
        assertTrue(ALL_MIGRATIONS.any { it.startVersion == 8 && it.endVersion == 9 })
        assertTrue(ALL_MIGRATIONS.any { it.startVersion == 9 && it.endVersion == 10 })
    }
}
