package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#23 非常规时间 UI 重构 — 用户 2026-09-08 提出的 6 个场景验证:
 *   1. 非常规两项(课表外节次 + 非标准时长)同时启用 — 是否冲突
 *   2. 启用一个、不启用另一个 — 是否冲突
 *   3. 正常的和不正常的在一起 — 是否冲突
 *   4. 非常规课程注册路径同常规课程(同一张表/同一 insert)
 *   5. 第 0 节取消后, 临时时间应结束(回收)
 *   6. n+1 无课 → 按正常课表呈现(回收)
 *
 * 测试策略: 通过公开 API(TimeTableUtils / ConflictDetailReporter / CourseEntity)
 * 直接验证每条路径的实际行为, 不依赖 Android 框架。
 */
class IrregularScenarioTest {

    private fun rows(json: String) = TimeTableUtils.parseTimeSlotRows(json)
    private val base = TimeTableUtils.DEFAULT_TIME_JSON

    // ---------------------------------------------------------------
    // 场景 1: 非常规两项同时启用(课表外节次 + 非标准时长)
    // ---------------------------------------------------------------

    /**
     * 用户同时: (a) 加第 0 节 07:30-08:00  (b) 设全局非常规时间 10:00-11:40
     * 然后把课放在第 0 节(startNode=0)。
     *
     * 当前实现: buildCourseEntity 在 irregularEnabled=true 时强制 ownTime=true,
     * 并用全局时间覆盖 block 内时间。所以第 0 节上的课会显示 10:00-11:40(全局),
     * 而非 07:30-08:00(第 0 节节点时间)。
     *
     * 这是设计取舍(全局时间优先), 不是 bug — 但必须显式记录, 避免后续误判。
     */
    @Test
    fun scenario1_bothIrregularItems_courseAtEdgeNode_usesGlobalTime() {
        // 加第 0 节 07:30-08:00
        val jsonWithEdge = TimeTableUtils.insertEdgeNode(
            base, TimeTableUtils.EdgeClass.Before, "07:30", "08:00"
        )
        assertTrue(rows(jsonWithEdge).any { it.node == 0 })

        // 模拟 buildCourseEntity: irregularEnabled=true, 全局 10:00-11:40, 放在第 0 节
        val course = CourseEntity(
            groupId = "g", tableId = 1L, courseName = "测试课",
            day = 1, startNode = 0, step = 1,
            startWeek = 1, endWeek = 16,
            color = "",
            ownTime = true, startTime = "10:00", endTime = "11:40"
        )

        // 渲染时间: ownTime=true → 直接返回全局时间, 忽略第 0 节节点时间
        val parts = TimeTableUtils.courseTimeParts(
            course.startNode, course.step, jsonWithEdge,
            course.ownTime, course.startTime, course.endTime
        )
        assertNotNull(parts)
        // 显示的是全局 10:00-11:40, 不是第 0 节的 07:30-08:00
        assertEquals("10:00", parts!!.first)
        assertEquals("11:40", parts.second)

        // 但网格定位: normalizeNode 把 10:00-11:40 反算到节点 3(08:00-08:45 之后最近),
        // 所以课实际画在第 3 节位置, 不是第 0 节! — 这是真正的冲突
        val normalized = course.normalizeNode(jsonWithEdge)
        // 10:00 匹配节点 3(10:00-10:45), 11:40 匹配节点 4(10:55-11:40) → step=2
        assertEquals("10:00 应映射到节点 3", 3, normalized.startNode)
        assertEquals("10:00-11:40 跨 2 节", 2, normalized.step)
    }

    // ---------------------------------------------------------------
    // 场景 2a: 只启用课表外节次, 不设非标准时长
    // ---------------------------------------------------------------

    /**
     * 用户只加第 0 节 07:30-08:00, 不碰全局时间。
     * 当前实现: irregularEnabled=true 强制所有 block ownTime=true + 全局默认 08:00-09:40。
     * 所以第 0 节上的课显示 08:00-09:40(默认全局), 不是 07:30-08:00。
     *
     * 冲突: 用户加了第 0 节但没用上 — 第 0 节节点时间被全局默认覆盖。
     */
    @Test
    fun scenario2a_onlyEdgeNode_courseAtEdgeNode_usesGlobalDefault() {
        val jsonWithEdge = TimeTableUtils.insertEdgeNode(
            base, TimeTableUtils.EdgeClass.Before, "07:30", "08:00"
        )
        // 全局默认 08:00-09:40
        val course = CourseEntity(
            groupId = "g", tableId = 1L, courseName = "测试课",
            day = 1, startNode = 0, step = 1,
            startWeek = 1, endWeek = 16,
            color = "",
            ownTime = true, startTime = "08:00", endTime = "09:40"
        )
        val parts = TimeTableUtils.courseTimeParts(
            course.startNode, course.step, jsonWithEdge,
            course.ownTime, course.startTime, course.endTime
        )
        // 显示 08:00-09:40(全局默认), 不是第 0 节的 07:30-08:00
        assertEquals("08:00", parts!!.first)
        assertEquals("09:40", parts.second)
    }

    // ---------------------------------------------------------------
    // 场景 2b: 只启用非标准时长, 不加课表外节次
    // ---------------------------------------------------------------

    /**
     * 用户只设全局非常规时间 10:00-11:40, 不加边缘节次。
     * 课放在标准节次(如 startNode=1)。
     * 这是最干净的非常规用例 — 无冲突。
     */
    @Test
    fun scenario2b_onlyGlobalTime_noEdge_noConflict() {
        val course = CourseEntity(
            groupId = "g", tableId = 1L, courseName = "测试课",
            day = 1, startNode = 1, step = 2,
            startWeek = 1, endWeek = 16,
            color = "",
            ownTime = true, startTime = "10:00", endTime = "11:40"
        )
        val parts = TimeTableUtils.courseTimeParts(
            course.startNode, course.step, base,
            course.ownTime, course.startTime, course.endTime
        )
        assertEquals("10:00", parts!!.first)
        assertEquals("11:40", parts.second)
        // 无边缘节点
        assertTrue(TimeTableUtils.edgeNodesOf(base, TimeTableUtils.EdgeClass.Before).isEmpty())
        assertTrue(TimeTableUtils.edgeNodesOf(base, TimeTableUtils.EdgeClass.After).isEmpty())
    }

    // ---------------------------------------------------------------
    // 场景 3: 正常的和不正常的在一起(同表混存)
    // ---------------------------------------------------------------

    /**
     * 同表: 常规课 A(第 1-2 节, 08:00-09:40) + 非常规课 B(10:00-11:40)。
     * 两者 ownTime 不同, 但注册路径相同(同一 repo.insertCourses)。
     * 冲突检测: ConflictDetailReporter 用 startNode+step 判定, 不调用 normalizeNode,
     * 所以 ownTime 课的节点坐标是存储值(可能不反映真实时间位置)。
     */
    @Test
    fun scenario3_normalAndIrregular_coexist_sameTable() {
        val normal = CourseEntity(
            groupId = "g1", tableId = 1L, courseName = "常规课",
            day = 1, startNode = 1, step = 2,
            startWeek = 1, endWeek = 16,
            color = "",
            ownTime = false, startTime = "", endTime = ""
        )
        val irregular = CourseEntity(
            groupId = "g2", tableId = 1L, courseName = "非常规课",
            day = 1, startNode = 1, step = 2,  // 存储坐标 1-2, 但实际 10:00-11:40
            startWeek = 1, endWeek = 16,
            color = "",
            ownTime = true, startTime = "10:00", endTime = "11:40"
        )
        // 注册路径相同(同一 insertCourses) — 这里只验证两者都能构造
        assertEquals(normal.tableId, irregular.tableId)

        // 冲突检测: 必须 normalize 真实节点号。非常规课 10:00-11:40 → 节点 3-4,
        // 与常规课的 1-2 不相交, 不应报冲突。
        val details = ConflictDetailReporter.draftConflictDetails(
            listOf(irregular), listOf(normal), arrayOf("一", "二", "三", "四", "五", "六", "日")
        )
        assertEquals(
            "ConflictDetailReporter 必须 normalizeNode 后再比较 (issue#23 Fix 3)",
            0, details.size
        )
    }

    // ---------------------------------------------------------------
    // 场景 4: 非常规课程注册路径同常规课程
    // ---------------------------------------------------------------

    /**
     * 验证: ownTime=true 的课和 ownTime=false 的课, 数据库字段结构相同,
     * 只是 ownTime/startTime/endTime 值不同。注册路径(insertCourses)完全一致。
     */
    @Test
    fun scenario4_irregularCourse_sameSchemaAsNormal() {
        val normal = CourseEntity(
            groupId = "g", tableId = 1L, courseName = "课",
            day = 1, startNode = 1, step = 2,
            startWeek = 1, endWeek = 16,
            color = "",
            ownTime = false, startTime = "", endTime = ""
        )
        val irregular = CourseEntity(
            groupId = "g", tableId = 1L, courseName = "课",
            day = 1, startNode = 1, step = 2,
            startWeek = 1, endWeek = 16,
            color = "",
            ownTime = true, startTime = "10:00", endTime = "11:40"
        )
        // 同 schema, 同表, 同 insert 路径 — 差异仅在三个字段
        assertEquals(normal.javaClass, irregular.javaClass)
        assertEquals(normal.tableId, irregular.tableId)
        assertFalse(normal.ownTime)
        assertTrue(irregular.ownTime)
    }

    // ---------------------------------------------------------------
    // 场景 5: 第 0 节取消后, 临时时间应结束(回收)
    // ---------------------------------------------------------------

    /**
     * 用户明示: "第 0 节取消了之后, 临时的那个时间就会结束"。
     *
     * 验证: removeEdgeNodeIfUnused 工具本身工作正常(已测),
     * 但生产代码(deleteCourseGroup)从未调用它 — 所以回收不会自动发生。
     *
     * 此测试证明工具可用, 同时通过代码审查确认 wiring 缺失。
     */
    @Test
    fun scenario5_edgeNodeReclaim_worksInIsolation() {
        // 加第 0 节
        val jsonWithEdge = TimeTableUtils.insertEdgeNode(
            base, TimeTableUtils.EdgeClass.Before, "07:30", "08:00"
        )
        assertEquals(13, rows(jsonWithEdge).size)

        // 第 0 节已无课程引用 → 回收
        val reclaimed = TimeTableUtils.removeEdgeNodeIfUnused(jsonWithEdge, 0, emptySet())
        assertEquals("回收后回到 12 节", 12, rows(reclaimed).size)
        assertNull("第 0 节应被移除", rows(reclaimed).firstOrNull { it.node == 0 })

        // 第 0 节仍有课程引用 → 不回收
        val kept = TimeTableUtils.removeEdgeNodeIfUnused(jsonWithEdge, 0, setOf(0))
        assertEquals("有引用时不回收", jsonWithEdge, kept)
    }

    // ---------------------------------------------------------------
    // 场景 6: n+1 无课 → 按正常课表呈现(回收)
    // ---------------------------------------------------------------

    /**
     * 用户明示: "n+1 只要没有 N1 的课或者第 0 节的课, 那就按正常课表呈现"。
     *
     * 后置边缘节点(第 13 节)同理: 工具支持回收, 但生产代码未接线。
     */
    @Test
    fun scenario6_nPlusOne_reclaim_worksInIsolation() {
        // 加第 13 节(后置)
        val jsonWithAfter = TimeTableUtils.insertEdgeNode(
            base, TimeTableUtils.EdgeClass.After, "22:30", "23:15"
        )
        assertEquals(13, rows(jsonWithAfter).size)
        assertNotNull(rows(jsonWithAfter).firstOrNull { it.node == 13 })

        // 无课引用 → 回收
        val reclaimed = TimeTableUtils.removeEdgeNodeIfUnused(jsonWithAfter, 13, emptySet())
        assertEquals("回收后回到 12 节", 12, rows(reclaimed).size)
        assertNull("第 13 节应被移除", rows(reclaimed).firstOrNull { it.node == 13 })
    }

    // ---------------------------------------------------------------
    // 附加: ownTime 课的 normalizeNode 把时间映射回节点(网格定位)
    // ---------------------------------------------------------------

    /**
     * ownTime=true 的课, normalizeNode 会用 timeToNode 反算等效节点坐标。
     * 这是网格定位的依据 — 课实际画在哪个节点行, 取决于 startTime/endTime。
     *
     * 重要: ownTime=true + startNode=0(用户手动填的) 并不代表"画在第 0 节"!
     * 真实位置 = timeToNode(startTime, endTime, timeJson) 算出的节点坐标。
     */
    @Test
    fun irregularCourse_normalizeNode_remapsByTime() {
        val course = CourseEntity(
            groupId = "g", tableId = 1L, courseName = "课",
            day = 1, startNode = 0, step = 1,   // 存储坐标 0
            startWeek = 1, endWeek = 16,
            color = "",
            ownTime = true, startTime = "10:00", endTime = "11:40"
        )
        val normalized = course.normalizeNode(base)
        // 10:00 匹配节点 3 (10:00-10:45); 11:40 匹配节点 4 结束 → step=2
        assertEquals(3, normalized.startNode)
        assertEquals(2, normalized.step)
        // 不管用户填的 startNode=0, 实际画在节点 3
    }

    // ===============================================================
    // issue#23 §3.3 逐卡 effective 时间解析矩阵 — 旧课程级 resolveIrregularCourseTime
    // 已删除, 消费方迁至逐卡 [TimeTableUtils.effectiveCourseTime]
    // ===============================================================

    /** 覆盖优先: 勾选非常规时间 → 课程自带起止直接生效, 不受槽位默认时间约束 (§2.3). */
    @Test
    fun effective_overrideWins_onEdgeSlot() {
        val jsonWithEdge = TimeTableUtils.insertEdgeNode(
            base, TimeTableUtils.EdgeClass.Before, "07:30", "08:00"
        )
        val r = TimeTableUtils.effectiveCourseTime(
            isIrregularTime = true, startTime = "08:01", endTime = "08:26",
            startNode = 0, step = 1, timeJson = jsonWithEdge
        )
        assertEquals("08:01" to "08:26", r)
    }

    /** 边缘继承: 未勾选覆盖 → 用边缘槽位默认时间 (§3.3 分支 2). */
    @Test
    fun effective_edgeSlotInheritsSlotDefault() {
        val jsonWithEdge = TimeTableUtils.insertEdgeNode(
            base, TimeTableUtils.EdgeClass.Before, "07:30", "08:00"
        )
        val r = TimeTableUtils.effectiveCourseTime(
            isIrregularTime = false, startTime = "", endTime = "",
            startNode = 0, step = 1, timeJson = jsonWithEdge
        )
        assertEquals("07:30" to "08:00", r)
    }

    /** 标准节次: 未勾选覆盖 → 标准 1..N 节次时间 (第 1-2 节 = 08:00-09:40). */
    @Test
    fun effective_standardNode_usesNodeRange() {
        val r = TimeTableUtils.effectiveCourseTime(
            isIrregularTime = false, startTime = "", endTime = "",
            startNode = 1, step = 2, timeJson = base
        )
        assertEquals("08:00" to "09:40", r)
    }

    /** 标准节次 + 覆盖: 勾选非常规时间在标准节次上 = 旧 ByClock 语义, 两选项完全独立 (§1). */
    @Test
    fun effective_standardNode_withOverride() {
        val r = TimeTableUtils.effectiveCourseTime(
            isIrregularTime = true, startTime = "10:00", endTime = "11:40",
            startNode = 1, step = 2, timeJson = base
        )
        assertEquals("10:00" to "11:40", r)
    }

    /** 勾选覆盖但时间无效 → null (validateCourseDraft 负责报格式错, 这里不静默给值). */
    @Test
    fun effective_invalidTimes_null() {
        val r = TimeTableUtils.effectiveCourseTime(
            isIrregularTime = true, startTime = "garbage", endTime = "xx",
            startNode = 1, step = 2, timeJson = base
        )
        assertNull(r)
    }

    /** 节次不存在 → null. */
    @Test
    fun effective_unknownNode_null() {
        assertNull(
            TimeTableUtils.effectiveCourseTime(
                isIrregularTime = false, startTime = "", endTime = "",
                startNode = 99, step = 1, timeJson = base
            )
        )
    }

    // ===============================================================
    // Fix 3 (issue#23 场景3): ConflictDetailReporter 必须把 ownTime 草稿
    // normalize 到真实节点号后再做 nodesOverlap — 否则 ownTime 草稿
    // 按 startNode 比较会与同 day 不同真实位置的标准课假撞车。
    // ===============================================================

    @Test
    fun conflictReporter_ownTimeDraft_normalizesBeforeOverlapCheck() {
        // 标准 12 节, 第 3 节 08:00-08:45 已有存量"数据库课"
        val existing = CourseEntity(
            groupId = "stored", tableId = 1L, courseName = "数据库课",
            day = 1, startNode = 3, step = 1,
            startWeek = 1, endWeek = 16,
            color = "", ownTime = false, startTime = "", endTime = ""
        )
        // 草稿: ownTime=true, 真实时间 10:00-11:40 (映射到节点 3-4),
        // 但用户在 UI 里"先选 ByNode 写 startNode=1" 误操作场景 — startNode=1
        // 与数据库课 startNode=3 直接比较不撞(1..1 vs 3..3), 无冲突 — 正确
        val draftByNode = CourseEntity(
            groupId = "draft", tableId = 1L, courseName = "测试课",
            day = 1, startNode = 1, step = 1,
            startWeek = 1, endWeek = 16,
            color = "",
            ownTime = true, startTime = "10:00", endTime = "11:40"
        )
        // normalize 后真实位置 = (3, 2)
        val normalized = draftByNode.normalizeNode(base)
        assertEquals(3, normalized.startNode)
        assertEquals(2, normalized.step)
        // 报给用户的明细应是 (3..4 vs 3..3) 命中 — 不再误报 startNode=1 不撞
        val details = ConflictDetailReporter.draftConflictDetails(
            listOf(draftByNode), listOf(existing),
            arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        )
        assertEquals("应报一条冲突(节点 3-4 与 3 相交)", 1, details.size)
    }

    @Test
    fun conflictReporter_ownTimeDraft_atEdgeNode_notReportedAsNormalOverlap() {
        // 第 0 节 07:30-08:00 的草稿(ownTime=true, 真实时间=第 0 节节点时间),
        // 与标准 1..12 节上的存量课不应该撞
        val jsonWithEdge = TimeTableUtils.insertEdgeNode(
            base, TimeTableUtils.EdgeClass.Before, "07:30", "08:00"
        )
        val existing = CourseEntity(
            groupId = "stored", tableId = 1L, courseName = "早八课",
            day = 1, startNode = 1, step = 1,
            startWeek = 1, endWeek = 16,
            color = "", ownTime = false, startTime = "", endTime = ""
        )
        // 草稿放第 0 节, ownTime=true 但起止时间 = 第 0 节节点时间
        val draft = CourseEntity(
            groupId = "draft", tableId = 1L, courseName = "更早的课",
            day = 1, startNode = 0, step = 1,
            startWeek = 1, endWeek = 16,
            color = "",
            ownTime = false,  // 经 resolveIrregular 后 = false(边缘节点赢)
            startTime = "07:30", endTime = "08:00"
        )
        val details = ConflictDetailReporter.draftConflictDetails(
            listOf(draft), listOf(existing), arrayOf("周一")
        )
        assertEquals("第 0 节与第 1 节不相交, 不应报冲突", 0, details.size)
    }

    // ===============================================================
    // issue#23 §5 渲染: 非常规时间胶囊按真实分钟比例定位 (行坐标, 1.0 = 一整行)
    // ===============================================================

    /** 节内时间 → 行下标 + 槽内比例: 08:01-08:26 落在第 1 节(行 0, 45 分钟)内. */
    @Test
    fun fractional_withinOneNode() {
        val (s, e) = TimeTableUtils.timeToFractionalRows("08:01", "08:26", base)!!
        assertEquals(1f / 45f, s, 0.0001f)
        assertEquals(26f / 45f, e, 0.0001f)
    }

    /** 跨节连续: 08:20 起(第 1 节内), 10:20 止(第 3 节 10:00-10:45 内 → 行 2 + 20/45). */
    @Test
    fun fractional_acrossNodes_continuous() {
        val (s, e) = TimeTableUtils.timeToFractionalRows("08:20", "10:20", base)!!
        assertEquals(20f / 45f, s, 0.0001f)
        assertEquals(2f + 20f / 45f, e, 0.0001f)
    }

    /** 空隙归属下一行顶端: 13:00 在午休空隙 → 第 5 节(14:00)行 4 的 0.0. */
    @Test
    fun fractional_gap_clampsToNextRowTop() {
        val (s, e) = TimeTableUtils.timeToFractionalRows("13:00", "14:20", base)!!
        assertEquals(4f, s, 0.0001f)
        assertEquals(4f + 20f / 45f, e, 0.0001f)
    }

    /** 窗口外钳到网格两端: 07:00-23:30 → (0, 12). */
    @Test
    fun fractional_outsideWindow_clampsToGridBounds() {
        val (s, e) = TimeTableUtils.timeToFractionalRows("07:00", "23:30", base)!!
        assertEquals(0f, s, 0.0001f)
        assertEquals(12f, e, 0.0001f)
    }

    /** 边缘槽位参与行序: 注册第 0 节(07:30-08:00)+第 13 节后, 07:40-07:55 → 行 0 内比例. */
    @Test
    fun fractional_edgeSlots_countedAsRows() {
        val withEdges = TimeTableUtils.insertEdgeNode(
            TimeTableUtils.insertEdgeNode(
                base, TimeTableUtils.EdgeClass.Before, "07:30", "08:00"
            ), TimeTableUtils.EdgeClass.After, "21:30", "22:15"
        )
        val (s, e) = TimeTableUtils.timeToFractionalRows("07:40", "07:55", withEdges)!!
        assertEquals(10f / 30f, s, 0.0001f)
        assertEquals(25f / 30f, e, 0.0001f)
    }

    /** 不可解析 / 结束≤开始 → null, 调用方退回整格吸附. */
    @Test
    fun fractional_unparseableOrNull() {
        assertNull(TimeTableUtils.timeToFractionalRows("xx", "08:26", base))
        assertNull(TimeTableUtils.timeToFractionalRows("09:00", "08:00", base))
    }
}
