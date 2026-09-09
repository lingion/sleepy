package com.lingion.sleepy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * issue#26 课程别名迁移 v5 → v6:
 *   - 用 sqlite-jdbc 直接执行 [MIGRATION_5_6_STATEMENTS](与 Room Migration 共用同一份 SQL, 单一事实来源)
 *   - 不依赖 Robolectric / Instrumentation(本仓库无)
 *
 * 覆盖三件事:
 *   1) 列被加上、类型 TEXT、NOT NULL、默认 ''
 *   2) 旧库已有行 alias 全部 = '' (回退原名, 行为不变)
 *   3) [ALL_MIGRATIONS] 链是从 3 起步严格连续(防漏登中间版本 / 错登跨版本)
 */
class CourseAliasMigrationTest {

    /** 手工复刻 v5 schema — 与 [AppDatabase] 当前实体 + MIGRATION_3_4/MIGRATION_4_5 等价 */
    private fun createV5Schema(conn: Connection) {
        conn.createStatement().use { st ->
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
            // 模拟旧 v3 库在迁移前已存在的真实数据
            st.execute(
                """
                INSERT INTO courses
                  (groupId, tableId, courseName, teacher, room, day, startNode, step,
                   startWeek, endWeek, type, color, colorMode, ownTime,
                   isIrregularNode, isIrregularTime, credit, level)
                VALUES
                  ('g1', 1, '高等数学', '王老师', '教3-101', 1, 1, 2,
                   1, 16, 0, '#FF6750A4', 0, 0, 0, 0, 4.0, 0),
                  ('g2', 1, '大学英语', '李老师', '教2-205', 2, 3, 2,
                   1, 16, 0, '#FF03DAC5', 0, 0, 0, 0, 2.0, 0)
                """.trimIndent()
            )
        }
    }

    private fun openInMemory(): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:")

    @Test
    fun alias_column_added_with_default_empty_string_and_not_null() {
        val conn = openInMemory()
        try {
            createV5Schema(conn)
            // 执行 MIGRATION_5_6_STATEMENTS (与 Room 同源)
            conn.createStatement().use { st ->
                MIGRATION_5_6_STATEMENTS.forEach { st.execute(it) }
            }
            // 1) 列存在 + 类型
            val cols = mutableListOf<Map<String, String>>()
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(courses)").use { rs ->
                    while (rs.next()) {
                        cols += mapOf(
                            "name" to rs.getString("name"),
                            "type" to rs.getString("type"),
                            "notnull" to rs.getInt("notnull").toString(),
                            "dflt_value" to (rs.getString("dflt_value") ?: "NULL")
                        )
                    }
                }
            }
            val alias = cols.firstOrNull { it["name"] == "alias" }
            assertNotNull("alias column must exist after migration", alias)
            assertEquals("TEXT", alias!!["type"])
            assertEquals("1", alias["notnull"])
            assertEquals("''", alias["dflt_value"])
        } finally {
            conn.close()
        }
    }

    @Test
    fun pre_existing_rows_get_empty_alias_after_migration() {
        val conn = openInMemory()
        try {
            createV5Schema(conn)
            conn.createStatement().use { st ->
                MIGRATION_5_6_STATEMENTS.forEach { st.execute(it) }
            }
            conn.createStatement().use { st ->
                st.executeQuery("SELECT courseName, alias FROM courses ORDER BY id").use { rs ->
                    val rows = mutableListOf<Pair<String, String>>()
                    while (rs.next()) {
                        rows += rs.getString("courseName") to rs.getString("alias")
                    }
                    assertEquals(
                        listOf("高等数学" to "", "大学英语" to ""),
                        rows
                    )
                }
            }
        } finally {
            conn.close()
        }
    }

    @Test
    fun migration_chain_3_to_6_is_strictly_consecutive() {
        // 防漏登 / 错登: 全链须从 3 起步, 每个 Migration startVersion == 上一个 endVersion,
        // 且跨度为 1。Room 在运行时若链断会抛 IllegalStateException 清库失败,
        // 这里在单测阶段直接挡住。
        val chain = ALL_MIGRATIONS
        assertEquals("first migration must start at version 3", 3, chain.first().startVersion)
        chain.forEachIndexed { idx, mig ->
            assertEquals(
                "migration #$idx endVersion must equal startVersion + 1",
                mig.startVersion + 1,
                mig.endVersion
            )
            if (idx > 0) {
                assertEquals(
                    "migration #$idx startVersion must equal previous endVersion (no gap)",
                    chain[idx - 1].endVersion,
                    mig.startVersion
                )
            }
        }
        assertTrue(
            "chain must reach version 6 (AppDatabase current)",
            chain.last().endVersion >= 6
        )
        assertTrue(
            "MIGRATION_5_6 must be registered in ALL_MIGRATIONS",
            chain.any { it.startVersion == 5 && it.endVersion == 6 }
        )
    }
}
