package com.lingion.sleepy.data

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** issue#23 逐卡重构: 行级非常规标志 — 默认值 / copy 保真 / 迁移语义契约 */
class CourseEntityFlagsTest {

    private fun course(
        ownTime: Boolean = false,
        isIrregularNode: Boolean = false,
        isIrregularTime: Boolean = false,
        startNode: Int = 3,
        step: Int = 2
    ): CourseEntity = CourseEntity(
        groupId = "g1",
        tableId = 1L,
        courseName = "测试课程",
        day = 1,
        startNode = startNode,
        step = step,
        startWeek = 1,
        endWeek = 16,
        color = "#FF6750A4",
        ownTime = ownTime,
        isIrregularNode = isIrregularNode,
        isIrregularTime = isIrregularTime
    )

    @Test
    fun 默认构造_flags_为_false() {
        val c = course()
        assertFalse(c.isIrregularNode)
        assertFalse(c.isIrregularTime)
        assertFalse(c.ownTime)
        // issue#26: alias 默认值是空串 — 显示按 settings 走,空 = 原名, 行为不变
        assertEquals("", c.alias)
    }

    @Test
    fun copy_不丢_flags() {
        val c = course().copy(
            ownTime = true,
            isIrregularNode = true,
            isIrregularTime = true,
            startNode = 0,
            alias = "高数"
        )
        assertTrue(c.isIrregularNode)
        assertTrue(c.isIrregularTime)
        assertTrue(c.ownTime)
        assertEquals(0, c.startNode)
        assertEquals("高数", c.alias)
    }

    @Test
    fun ownTime_与_isIrregularTime_同值契约() {
        // MIGRATION_4_5: UPDATE courses SET isIrregularTime=1 WHERE ownTime=1
        // 保存路径契约: ownTime = isIrregularTime 永远同值(§5)
        val migrated = course(ownTime = true, isIrregularTime = true)
        assertEquals(migrated.ownTime, migrated.isIrregularTime)
    }

    @Test
    fun 非常规节次卡_normalizeNode_禁止时间重映射() {
        // issue#23: 边缘槽位卡的网格位置 = 槽位编号本身 (selectedEdgeNode), step 锁 1;
        // 即便覆盖时间恰好匹配标准节次窗口, 也不得被 timeToNode 重映射走
        val c = course(
            ownTime = true,
            isIrregularNode = true,
            isIrregularTime = true,
            startNode = 0,
            step = 1
        ).copy(startTime = "08:00", endTime = "08:45")
        val normalized = c.normalizeNode(TimeTableUtils.DEFAULT_TIME_JSON)
        assertEquals(0, normalized.startNode)
        assertEquals(1, normalized.step)
    }
}
