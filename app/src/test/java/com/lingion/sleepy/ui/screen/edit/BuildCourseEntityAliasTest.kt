package com.lingion.sleepy.ui.screen.edit

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * issue#26 保存路径: buildCourseEntity 的 alias 参数 → CourseEntity.alias。
 * buildCourseEntity/MeetingBlockDraft 本次改 internal (先例: internal groupSlotsForEdit 同包直测)。
 */
class BuildCourseEntityAliasTest {

    private fun block(): MeetingBlockDraft = MeetingBlockDraft(
        id = 1,
        days = androidx.compose.runtime.mutableStateListOf(1),
        startNode = 1,
        step = 2,
        startTime = "08:00",
        endTime = "09:40"
    )

    @Test
    fun alias_trims_and_lands_on_entity() {
        val e = buildCourseEntity(
            tableId = 1L,
            groupId = "g1",
            courseName = "高等数学",
            block = block(),
            day = 1,
            alias = "  高数  "
        )
        assertEquals("高数", e.alias)
        assertEquals("高等数学", e.courseName)
    }

    @Test
    fun blank_alias_normalizes_to_empty_string() {
        val e = buildCourseEntity(
            tableId = 1L,
            groupId = "g1",
            courseName = "大学英语",
            block = block(),
            day = 2,
            alias = "   "
        )
        assertEquals("", e.alias)
    }

    @Test
    fun default_alias_keeps_original_name() {
        // 不传 alias(默认 "")→ 行为与旧版本完全一致
        val e = buildCourseEntity(
            tableId = 1L,
            groupId = "g1",
            courseName = "数据结构",
            block = block(),
            day = 3
        )
        assertEquals("", e.alias)
        assertEquals("数据结构", e.courseName)
    }

    @Test
    fun other_fields_untouched_by_alias_param() {
        val e = buildCourseEntity(
            tableId = 7L,
            groupId = "g9",
            courseName = "高等数学",
            block = block(),
            day = 1,
            alias = "高数"
        )
        assertEquals(7L, e.tableId)
        assertEquals("g9", e.groupId)
        assertEquals(1, e.day)
        assertEquals(1, e.startNode)
        assertEquals(2, e.step)
    }
}
