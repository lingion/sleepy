package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 非常规时间课程与常规课程在真实时间上重叠时的并排车道布局回归测试。
 *
 * 场景复现：课表 8 节（VII 15:40-16:25 / VIII 16:25-17:10），
 * 常规课 VII-VIII 节，另有一节非常规时间课 16:00-16:50 ——
 * 两者真实时间重叠，应各自占据独立车道并排显示，而不是叠压遮挡。
 */
class IrregularLaneLayoutTest {

    private val base = TimeTableUtils.DEFAULT_TIME_JSON

    @Test
    fun `重叠的非常规课与常规课分到不同车道`() {
        // 常规课 VII-VIII：行区间 [6.0, 8.0)；非常规课 16:00-16:50：约 [6.44, 7.55)
        val lanes = ConflictLayoutEngine.assignLanes(
            listOf(
                1L to (6.0f to 2.0f),
                2L to (6.44f to 1.11f),
            )
        )
        assertEquals(2, lanes.size)
        assertEquals(0, lanes[1L])
        assertEquals(1, lanes[2L])
    }

    @Test
    fun `时间不重叠的课程共用同一车道`() {
        val lanes = ConflictLayoutEngine.assignLanes(
            listOf(
                1L to (0.0f to 1.0f),
                2L to (1.0f to 1.0f),
            )
        )
        assertEquals(0, lanes[1L])
        assertEquals(0, lanes[2L])
    }

    @Test
    fun `三课重叠时第三个可回收已释放车道`() {
        // A: [0,2) B: [1,3) C: [2.5,4)：A、B 各占一车道，C 与 B 重叠但与 A 不重叠 → 回到车道 0
        val lanes = ConflictLayoutEngine.assignLanes(
            listOf(
                1L to (0.0f to 2.0f),
                2L to (1.0f to 2.0f),
                3L to (2.5f to 1.5f),
            )
        )
        assertEquals(0, lanes[1L])
        assertEquals(1, lanes[2L])
        assertEquals(0, lanes[3L])
    }

    @Test
    fun `用户场景：非常规课与常规课聚为一簇`() {
        val regular = CourseEntity(
            id = 1L,
            groupId = "g1",
            tableId = 1L,
            courseName = "短视频策划与制作",
            startNode = 7,
            step = 2,
            day = 5,
            startWeek = 1,
            endWeek = 16,
            color = "",
        )
        val irregular = CourseEntity(
            id = 2L,
            groupId = "g2",
            tableId = 1L,
            courseName = "非常规课",
            startNode = 7,
            step = 2,
            day = 5,
            startWeek = 1,
            endWeek = 16,
            color = "",
            ownTime = true,
            isIrregularTime = true,
            startTime = "16:00",
            endTime = "16:50",
        )
        val clusters = ConflictLayoutEngine.findClusters(listOf(regular, irregular), timeJson = base)
        assertEquals(1, clusters.size)
        assertEquals(setOf(1L, 2L), clusters[0].courses.map { it.id }.toSet())
    }
}
