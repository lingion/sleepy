package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户反馈 2026-09-09(需求真源): 非常规时间课跨午间空隙吸附 bug。
 *
 * 场景: 用户建两门非常规课 10:30~12:30 与 14:00~16:00。作息表上午最后一节 11:40
 * 结束(节 4), 下午 14:00 开始(节 5), 11:40~14:00 之间没有任何节次。
 * 结果: 12:30 这个结束时间跨过午间空隙"吸附"到下午 14:00 的节次里渲染,
 * 和 14:00 的课"硬生生冲突了"。
 *
 * 本文件 = 复现测试(先红后修)。normalizeNode 的跨空隙反算(timeToNode 把 end
 * 向上取到下一个存在节点的 end)是 issue#23 时代的设计取舍, 用户已明确否决。
 */
class IrregularOverflowTest {

    private val base = TimeTableUtils.DEFAULT_TIME_JSON

    /** 用户场景课 1: 10:30~12:30, 存储坐标 (1,1) — 真实位置由时间决定 */
    private fun morningOverflow(id: Long, day: Int = 1) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = "上午溢出课",
        day = day, startNode = 1, step = 1,
        startWeek = 1, endWeek = 16, color = "",
        ownTime = true, startTime = "10:30", endTime = "12:30"
    )

    /** 用户场景课 2: 14:00~16:00 = 节 5-6 */
    private fun afternoon(id: Long, day: Int = 1) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = "下午课",
        day = day, startNode = 5, step = 2,
        startWeek = 1, endWeek = 16, color = "",
        ownTime = true, startTime = "14:00", endTime = "16:00"
    )

    /**
     * 加载链复现: normalizeNode 用 timeToNode 反算, 12:30 跨过 11:40~14:00 空隙
     * 向上吸附到节 5(14:00~14:45) → 反算出 (3, 3), 节点范围 3..5 侵入下午。
     * 用户意图: 反算节点范围不得跨过空隙(end 节点必须 <= 4)。
     */
    @Test
    fun normalizeNode_gapOverflow_notAbsorbedIntoAfternoon() {
        val normalized = morningOverflow(1).normalizeNode(base)
        assertTrue(
            "反算节点范围不得跨过午间空隙(end 节点必须 <= 4), 实际=${normalized.startNode}..${normalized.startNode + normalized.step - 1}",
            normalized.startNode + normalized.step - 1 <= 4
        )
        assertTrue("step 不得为非正", normalized.step >= 1)
    }

    /** 常规 ownTime 课(不跨空隙)反算行为不变 — 回归锚。 */
    @Test
    fun normalizeNode_continuousOverlap_unchanged() {
        val c = CourseEntity(
            id = 1, groupId = "g1", tableId = 1L, courseName = "课",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "10:00", endTime = "11:40"
        )
        val n = c.normalizeNode(base)
        assertEquals(3, n.startNode)
        assertEquals(2, n.step)
    }

    /** 端到端: 用户两门课经完整加载语义(反算→聚簇)绝不成簇 — bug 本体。 */
    @Test
    fun endToEnd_userScenario_noFalseConflict() {
        val loaded = listOf(morningOverflow(1), afternoon(2))
            .map { it.normalizeNode(base) }
        val clusters = ConflictLayoutEngine.findClusters(loaded)
        assertTrue(
            "用户场景: 10:30~12:30 与 14:00~16:00 真实时间不重叠, 绝不冲突",
            clusters.isEmpty()
        )
        assertFalse(loaded.isEmpty())
    }

    // ===============================================================
    // B: 渲染期占位节次(贪心拓宽), 不改 timeJson
    // ===============================================================

    /** 10:30~12:30 跨 11:40~14:00 空隙 → 合成 11:40~12:30 占位节次, 原节次不变。 */
    @Test
    fun placeholder_gapOverflow_synthSlotCreated() {
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(morningOverflow(1)), base)
        val ph = plan.slots.filter { it.isPlaceholder }
        assertEquals("应合成 1 个占位节次", 1, ph.size)
        assertEquals("11:40", ph[0].displayStart)
        assertEquals("12:30", ph[0].displayEnd)
        assertEquals("原 12 节一个不少", 13, plan.slots.size)
        assertEquals("节 4 结束不变", "11:40", plan.slots[3].displayEnd)
        assertEquals("节 5 开始不变(占位行插入后顺移)", "14:00", plan.slots[5].displayStart)
        assertEquals("占位节次排在节 4 之后", 4, plan.slots.indexOf(ph[0]))
    }

    /** B(贪心): 两门课跨同一空隙, 一门 12:30 止一门 13:30 止 → 占位拓宽到 13:30。 */
    @Test
    fun placeholder_greedy_takesLongestOverflow() {
        val a = CourseEntity(
            id = 1, groupId = "g1", tableId = 1L, courseName = "A",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "10:30", endTime = "12:30"
        )
        val b = CourseEntity(
            id = 2, groupId = "g2", tableId = 1L, courseName = "B",
            day = 2, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "11:00", endTime = "13:30"
        )
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(a, b), base)
        val ph = plan.slots.filter { it.isPlaceholder }
        assertEquals("两门课跨同一空隙 → 只合 1 个占位节次", 1, ph.size)
        assertEquals("11:40", ph[0].displayStart)
        assertEquals("贪心取最长溢出", "13:30", ph[0].displayEnd)
    }

    /** B: 不同空隙各自合成, 互不吞并。 */
    @Test
    fun placeholder_multipleGaps_separateSlots() {
        val noon = CourseEntity(
            id = 1, groupId = "g1", tableId = 1L, courseName = "午间",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "10:30", endTime = "12:30"
        )
        val evening = CourseEntity(
            id = 2, groupId = "g2", tableId = 1L, courseName = "傍晚",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "17:00", endTime = "18:30"
        )
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(noon, evening), base)
        val ph = plan.slots.filter { it.isPlaceholder }
        assertEquals("午休 + 傍晚两个空隙各合 1 个占位", 2, ph.size)
        assertTrue(ph.any { it.displayStart == "11:40" && it.displayEnd == "12:30" })
        assertTrue(ph.any { it.displayStart == "17:40" && it.displayEnd == "18:30" })
    }

    /** B: 无非常规课 / 非常规课不跨空隙 → 零占位, 槽位表与 timeSlotsFor 一致。 */
    @Test
    fun placeholder_noOverflow_zeroPlaceholder_identity() {
        val planRegular = TimeTableUtils.buildRenderSlotPlan(
            listOf(CourseEntity(
                id = 1, groupId = "g1", tableId = 1L, courseName = "常规课",
                day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = ""
            )),
            base
        )
        assertEquals(12, planRegular.slots.size)
        assertTrue(planRegular.slots.none { it.isPlaceholder })

        val inNode = CourseEntity(
            id = 9, groupId = "g9", tableId = 1L, courseName = "节内",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "08:10", endTime = "08:30"
        )
        val plan2 = TimeTableUtils.buildRenderSlotPlan(listOf(inNode), base)
        assertEquals(12, plan2.slots.size)
        assertTrue(plan2.slots.none { it.isPlaceholder })
    }

    /** B: 占位节次绝不写回 timeJson — buildRenderSlotPlan 是纯函数, 输入原样。 */
    @Test
    fun placeholder_neverTouchesTimeJson() {
        val before = base
        TimeTableUtils.buildRenderSlotPlan(listOf(morningOverflow(1), afternoon(2)), base)
        assertEquals("timeJson 必须原样(渲染期合成物)", before, base)
    }

    // ===============================================================
    // A: 比例渲染基于扩展后槽位表 — 12:30 落在占位节次末端, 不进 14:00 行
    // ===============================================================

    @Test
    fun fractional_afterPlanSchedule_realMinuteRatio() {
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(morningOverflow(1)), base)
        val frac = TimeTableUtils.timeToFractionalRows("10:30", "12:30", plan.slots)!!
        // 节 3 = 10:00~10:45, 10:30 = 行 2 + 30/45
        assertEquals(2f + 30f / 45f, frac.first, 0.0001f)
        // 12:30 = 占位节次(行 4, 11:40~12:30)末端 → 行坐标 5.0
        assertEquals(5f, frac.second, 0.0001f)
        assertTrue("绝不越过占位节次进 14:00 行", frac.second <= 5f)
    }

    // ===============================================================
    // C: 冲突判定按真实时间区间(分钟级)
    // ===============================================================

    /** C(核心): findClusters 带 timeJson 时按真实分钟重叠聚簇。 */
    @Test
    fun clusters_ownTimeRealTimeOverlap_required() {
        val clusters = ConflictLayoutEngine.findClusters(
            listOf(morningOverflow(1), afternoon(2)), timeJson = base
        )
        assertTrue("真实时间不重叠的课绝不成簇", clusters.isEmpty())
    }

    /** C: 真实时间确实重叠的两门 ownTime 课仍成簇(回归保护)。 */
    @Test
    fun clusters_trueTimeOverlap_stillClusters() {
        val a = CourseEntity(
            id = 1, groupId = "g1", tableId = 1L, courseName = "A",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "10:30", endTime = "12:30"
        )
        val b = CourseEntity(
            id = 2, groupId = "g2", tableId = 1L, courseName = "B",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "12:00", endTime = "13:00"
        )
        val clusters = ConflictLayoutEngine.findClusters(listOf(a, b), timeJson = base)
        assertEquals("12:00~13:00 与 10:30~12:30 真实重叠 → 成簇", 1, clusters.size)
        assertEquals(2, clusters[0].courses.size)
    }

    /** C: ownTime 课 vs 常规课 — 以常规课节次真实起止时间比较。 */
    @Test
    fun clusters_ownTimeVsRegular_usesRealNodeTimes() {
        val reg34 = CourseEntity(
            id = 2, groupId = "g2", tableId = 1L, courseName = "常规课",
            day = 1, startNode = 3, step = 2, startWeek = 1, endWeek = 16, color = ""
        )
        val reg56 = CourseEntity(
            id = 2, groupId = "g2", tableId = 1L, courseName = "常规课",
            day = 1, startNode = 5, step = 2, startWeek = 1, endWeek = 16, color = ""
        )
        val clusters1 = ConflictLayoutEngine.findClusters(
            listOf(morningOverflow(1), reg34), timeJson = base
        )
        assertEquals("10:30~12:30 ∩ 10:00~11:40 = 重叠 → 成簇", 1, clusters1.size)
        val clusters2 = ConflictLayoutEngine.findClusters(
            listOf(morningOverflow(1), reg56), timeJson = base
        )
        assertTrue(
            "10:30~12:30 与 14:00~15:40 节点反算假相交, 真实不重叠 → 绝不成簇",
            clusters2.isEmpty()
        )
    }

    /** C: 保存时冲突明细报告同一语义。 */
    @Test
    fun conflictReporter_minuteOverlap_semantics() {
        val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val r1 = ConflictDetailReporter.draftConflictDetails(
            listOf(morningOverflow(100)), listOf(afternoon(1)), dayNames, base
        )
        assertTrue("真实时间不重叠绝不能报冲突", r1.isEmpty())

        val r2 = ConflictDetailReporter.draftConflictDetails(
            listOf(morningOverflow(100)),
            listOf(CourseEntity(
                id = 1, groupId = "g1", tableId = 1L, courseName = "常规课",
                day = 1, startNode = 3, step = 2, startWeek = 1, endWeek = 16, color = ""
            )),
            dayNames, base
        )
        assertEquals("与节 3-4(10:00~11:40)真实交叠 → 报", 1, r2.size)
    }

    // ===============================================================
    // A: 簇内 ownTime 卡片比例定位 — 不再整格吸附
    // ===============================================================

    @Test
    fun clusterCard_ownTime_fractionalPlacement() {
        val a = CourseEntity(
            id = 1, groupId = "g1", tableId = 1L, courseName = "A",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "10:30", endTime = "12:30"
        )
        val b = CourseEntity(
            id = 2, groupId = "g2", tableId = 1L, courseName = "B",
            day = 1, startNode = 1, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "12:00", endTime = "13:00"
        )
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(a, b), base)
        val fracA = TimeTableUtils.timeToFractionalRows("10:30", "12:30", plan.slots)!!
        val fracB = TimeTableUtils.timeToFractionalRows("12:00", "13:00", plan.slots)!!
        assertTrue(
            "真实重叠在比例坐标上必须体现: B 起(${fracB.first}) < A 止(${fracA.second})",
            fracB.first < fracA.second
        )
        assertTrue("12:00 必须落在 11:40 之后的占位行, 行坐标应 > 3", fracB.first > 3f)
    }
}
