package com.lingion.sleepy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/**
 * v11 → v12 migration 契约 (spec §6 测试 5/6):
 * course_colors 建表、复合主键去重语义、旧数据无损、REPLACE 幂等。
 * 与 Room 共用同一份 MIGRATION_11_12_STATEMENTS（单一事实来源）。
 */
class CourseColorMigrationTest {

    @Test
    fun migration_creates_course_colors_without_touching_existing_rows() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE time_tables (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                st.execute("INSERT INTO time_tables VALUES (1, 'existing')")
                MIGRATION_11_12_STATEMENTS.forEach(st::execute)
            }

            val columns = mutableMapOf<String, Pair<String, Int>>()
            conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(course_colors)").use { rs ->
                    while (rs.next()) columns[rs.getString("name")] = rs.getString("type") to rs.getInt("notnull")
                }
                st.executeQuery("SELECT name FROM time_tables WHERE id = 1").use { rs ->
                    assertTrue("旧数据必须无损", rs.next())
                    assertEquals("existing", rs.getString(1))
                }
            }
            assertEquals("TEXT" to 1, columns["tableKey"])
            assertEquals("TEXT" to 1, columns["groupId"])
            assertEquals("REAL" to 1, columns["hue"])
        }
    }

    @Test
    fun compositePrimaryKey_rejectsDifferentTableKeySameGroupId_andUpsertIsIdempotent() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                MIGRATION_11_12_STATEMENTS.forEach(st::execute)
            }
            conn.createStatement().use { st ->
                st.execute("INSERT INTO course_colors VALUES ('__global__', 'g1', 123.5)")
                // 同 (tableKey, groupId) 二次写 = REPLACE 语义 → 更新非新增
                st.execute("INSERT OR REPLACE INTO course_colors VALUES ('__global__', 'g1', 200.0)")
                st.executeQuery("SELECT COUNT(*), MAX(hue) FROM course_colors").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("upsert 幂等: 同键只余一行", 1, rs.getInt(1))
                    assertEquals(200.0, rs.getDouble(2), 0.001)
                }
                // 不同 tableKey 同 groupId 是不同行（主键含 tableKey 的几何证明）
                st.execute("INSERT INTO course_colors VALUES ('t2', 'g1', 5.0)")
                st.executeQuery("SELECT COUNT(*) FROM course_colors").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(2, rs.getInt(1))
                }
                // 旧式双列主键的误用防线: 同键裸 INSERT 必被拒
                val rejected = runCatching {
                    st.execute("INSERT INTO course_colors VALUES ('__global__', 'g1', 9.0)")
                }.isFailure
                assertTrue("复合主键必须拒绝同键重复", rejected)
            }
        }
    }

    @Test
    fun migrationChainReachesV12() {
        val last = ALL_MIGRATIONS.last()
        assertEquals("迁移链尾必须到 12", 12, last.endVersion)
        assertNotNull(ALL_MIGRATIONS.first { it.startVersion == 11 })
    }
}
