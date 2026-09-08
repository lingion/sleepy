package com.lingion.sleepy.data

import com.lingion.sleepy.data.entity.CourseEntity
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
    }

    @Test
    fun copy_不丢_flags() {
        val c = course().copy(
            ownTime = true,
            isIrregularNode = true,
            isIrregularTime = true,
            startNode = 0
        )
        assertTrue(c.isIrregularNode)
        assertTrue(c.isIrregularTime)
        assertTrue(c.ownTime)
        assertEquals(0, c.startNode)
    }

    @Test
    fun ownTime_与_isIrregularTime_同值契约() {
        // MIGRATION_4_5: UPDATE courses SET isIrregularTime=1 WHERE ownTime=1
        // 保存路径契约: ownTime = isIrregularTime 永远同值(§5)
        val migrated = course(ownTime = true, isIrregularTime = true)
        assertEquals(migrated.ownTime, migrated.isIrregularTime)
    }
}
