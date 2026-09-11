package com.lingion.sleepy.data.diff

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.CourseColorMode
import org.junit.Assert.assertEquals
import org.junit.Test

private fun course(
    id: Long = 0,
    gid: String = "g1",
    table: Long = 1,
    name: String = "x",
    teacher: String = "",
    room: String = "",
    day: Int = 1,
    startNode: Int = 1,
    step: Int = 1,
    startWeek: Int = 1,
    endWeek: Int = 16,
    type: Int = 0,
    colorMode: Int = CourseColorMode.GROUP,
    color: String = "#FF6750A4",
    note: String = "",
    startTime: String = "",
    endTime: String = "",
    ownTime: Boolean = false,
    credit: Float = 0f,
    level: Int = 0
) = CourseEntity(
    id = id, groupId = gid, tableId = table, courseName = name,
    teacher = teacher, room = room, note = note,
    day = day, startNode = startNode, step = step,
    startWeek = startWeek, endWeek = endWeek, type = type,
    color = color, colorMode = colorMode,
    ownTime = ownTime, startTime = startTime, endTime = endTime,
    credit = credit, level = level
)

/**
 * issue#22 行级 diff/patch 核心 — TDD 6 用例覆盖:
 *   - 草稿一致 → 空 diff
 *   - 改 1 行的 room → 该行 update,其他不动
 *   - 删 1 行 → 该行 id 进 toDelete,其他不动
 *   - 增 1 行 → 该行进 toInsert(原 groupId 保留)
 *   - 跨 groupId 不互相影响
 *   - 改字段不进 row key → 走 update 而非 delete+insert
 */
class RowKeyDifferTest {

    @Test fun `同名 3 个不同地点,server 3 行 与 draft 一致 → diff 空`() {
        val server = listOf(
            course(id = 1, room = "A"),
            course(id = 2, room = "B"),
            course(id = 3, room = "C")
        )
        val draft = server.map { it.copy() }
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(emptyList<CourseEntity>(), diff.toInsert)
        assertEquals(emptyList<CourseEntity>(), diff.toUpdate)
        assertEquals(emptyList<Long>(), diff.toDelete)
    }

    @Test fun `同名 3 行 server,草稿改 A 楼地点 → A 行 delete + A2 行 insert,其他 2 行不动`() {
        // RowKey 含 room → 改地点等于换新节次,旧行删 + 新行插
        val server = listOf(
            course(id = 1, room = "A"),
            course(id = 2, room = "B"),
            course(id = 3, room = "C")
        )
        val draft = listOf(
            course(id = 0, room = "A2"),  // 新地点 A2(新 RowKey)
            course(id = 2, room = "B"),   // 保持
            course(id = 3, room = "C")
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(1, diff.toInsert.size)
        assertEquals("A2", diff.toInsert[0].room)
        assertEquals(0L, diff.toInsert[0].id)
        assertEquals(emptyList<CourseEntity>(), diff.toUpdate)
        assertEquals(listOf(1L), diff.toDelete)
    }

    @Test fun `删 1 行 → 其他 2 行不动,删的行 id 进 toDelete`() {
        val server = listOf(
            course(id = 1, room = "A"),
            course(id = 2, room = "B"),
            course(id = 3, room = "C")
        )
        val draft = listOf(
            course(id = 1, room = "A"),
            course(id = 3, room = "C")
            // id=2 被删
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(emptyList<CourseEntity>(), diff.toInsert)
        assertEquals(emptyList<CourseEntity>(), diff.toUpdate)
        assertEquals(listOf(2L), diff.toDelete)
    }

    @Test fun `增 1 行 → 新行进 toInsert(带原 tableId 与 groupId)`() {
        val server = listOf(
            course(id = 1, room = "A"),
            course(id = 2, room = "B")
        )
        val draft = server + course(id = 0, room = "D")  // 新行 id=0 让 Room 自增
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(1, diff.toInsert.size)
        assertEquals(0L, diff.toInsert[0].id)
        assertEquals("D", diff.toInsert[0].room)
        assertEquals("g1", diff.toInsert[0].groupId)
        assertEquals(emptyList<CourseEntity>(), diff.toUpdate)
        assertEquals(emptyList<Long>(), diff.toDelete)
    }

    @Test fun `跨 groupId 不互相影响 — A 组加行不影响 B 组`() {
        val server = listOf(
            course(id = 1, gid = "g1", room = "A"),
            course(id = 2, gid = "g2", room = "B")
        )
        val draft = listOf(
            course(id = 1, gid = "g1", room = "A"),
            course(id = 2, gid = "g2", room = "B"),
            course(id = 0, gid = "g1", room = "A2")  // 新加到 g1
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(1, diff.toInsert.size)
        assertEquals("g1", diff.toInsert[0].groupId)
        assertEquals(emptyList<Long>(), diff.toDelete)
    }

    @Test fun `row key 比较不含 colorMode 和 note(改字段不视为新行)`() {
        val server = listOf(
            course(id = 1, room = "A", colorMode = 0, color = "#FF111111", note = "旧")
        )
        val draft = listOf(
            course(id = 1, room = "A", colorMode = 1, color = "", note = "新")
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(emptyList<CourseEntity>(), diff.toInsert)
        assertEquals(1, diff.toUpdate.size)
        assertEquals(1L, diff.toUpdate[0].id)
        assertEquals(1, diff.toUpdate[0].colorMode)
        assertEquals("", diff.toUpdate[0].color)
        assertEquals("新", diff.toUpdate[0].note)
    }

    /** 用户报障 2026-09-11: 别名无法保存 — fieldsDiffer 漏比 alias, 改别名 diff 判"相同"跳过 update */
    @Test fun `只改别名 → 该行走 update 保留 id, 不删不插`() {
        val server = listOf(
            course(id = 1, room = "A")
        )
        val draft = listOf(
            course(id = 1, room = "A").copy(alias = "高数(强化)")
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(emptyList<CourseEntity>(), diff.toInsert)
        assertEquals(emptyList<Long>(), diff.toDelete)
        assertEquals(1, diff.toUpdate.size)
        assertEquals(1L, diff.toUpdate[0].id)
        assertEquals("高数(强化)", diff.toUpdate[0].alias)
    }

    /** 别名清空也是变更 — 空回退原名(CourseDisplayUtil), 但存量行必须同步清掉 */
    @Test fun `别名清空 → 该行走 update 写回空串`() {
        val server = listOf(
            course(id = 1, room = "A").copy(alias = "高数(强化)")
        )
        val draft = listOf(
            course(id = 1, room = "A").copy(alias = "")
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(emptyList<Long>(), diff.toDelete)
        assertEquals(1, diff.toUpdate.size)
        assertEquals("", diff.toUpdate[0].alias)
    }

    /** 相同别名不动 — diff 空(别让修补把跳过路径打穿成全量 update) */
    @Test fun `别名相同 → diff 空`() {
        val server = listOf(
            course(id = 1, room = "A").copy(alias = "高数(强化)")
        )
        val draft = listOf(
            course(id = 1, room = "A").copy(alias = "高数(强化)")
        )
        val diff = RowKeyDiffer.diff(draft, server)
        assertEquals(emptyList<CourseEntity>(), diff.toInsert)
        assertEquals(emptyList<CourseEntity>(), diff.toUpdate)
        assertEquals(emptyList<Long>(), diff.toDelete)
    }
}
