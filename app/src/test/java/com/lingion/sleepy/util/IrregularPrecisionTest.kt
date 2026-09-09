package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 用户反馈 2026-09-09 (第二轮, 精度):
 * 作息表相邻两节 16:00-16:45 / 17:05-…, 建一门 16:40-16:50 非常规课。
 *
 * 期望 (用户原话意图):
 *  - 16:40 落在 16:00-16:45 节内的 40/45 高度处开始渲染;
 *  - 16:50 终止在空隙(16:45,17:05) → 合成占位行, 课尾渲染到占位行内自身比例处;
 *  - 与 16:00-16:45 的常规课真实重叠 16:40-16:45 (5 分钟) — 这是**部分重叠**,
 *    属真实时间冲突, 冲突判定本身正确; 视觉上簇内须能看出 16:40 起点
 *    (顶卡不得遮死底卡起点) — 由 ConflictCard 几何测试锁, 这里锁时间轴映射。
 *
 * 本文件锁时间轴映射的**像素级正确性**:
 *  - renderSlots 里占位行只占它代表的真实分钟数 (行高按分钟加权),
 *    不再"5 分钟占满一整行 rowH"导致时间轴被拉歪。
 */
class IrregularPrecisionTest {

    private val tj = """[{"node":1,"start":"08:00","end":"08:45"},
        {"node":2,"start":"08:55","end":"09:40"},{"node":3,"start":"09:50","end":"10:35"},
        {"node":4,"start":"10:45","end":"11:30"},{"node":5,"start":"11:40","end":"12:25"},
        {"node":6,"start":"14:00","end":"14:45"},{"node":7,"start":"14:55","end":"15:40"},
        {"node":8,"start":"16:00","end":"16:45"},{"node":9,"start":"17:05","end":"17:50"},
        {"node":10,"start":"18:00","end":"18:45"},{"node":11,"start":"18:55","end":"19:40"},
        {"node":12,"start":"19:50","end":"20:35"}]"""

    private fun c(day: Int, sn: Int, step: Int, own: Boolean, st: String, en: String) = CourseEntity(
        groupId = "g", tableId = 1L, courseName = "x", day = day, startNode = sn, step = step,
        startWeek = 1, endWeek = 16, color = "", ownTime = own, startTime = st, endTime = en,
        isIrregularTime = own
    )

    /** 16:40-16:50 与常规 16:00-16:45 课真实重叠 5 分钟 → 成簇是正确的, 冲突不是假报。 */
    @Test
    fun realOverlap_1640to1650_vs_1600to1645_clusters() {
        val irr = c(1, 8, 1, true, "16:40", "16:50")
        val reg = c(1, 8, 1, false, "", "")
        val clusters = ConflictLayoutEngine.findClusters(listOf(reg, irr).map { it.normalizeNode(tj) }, tj)
        assertEquals("16:40-16:50 与 16:00-16:45 重叠 5 分钟, 应成簇 (真实冲突)", 1, clusters.size)
        assertEquals(2, clusters[0].courses.size)
    }

    /** 占位行 = 16:45-16:50 只有 5 分钟, 行高必须按分钟加权: 5min/45min ≈ 0.111 行, 不是整行。
     *  实现落点: RenderSlotPlan.slotWeights — 每行权重(该行分钟数 / 相邻标准行分钟数), 占位行 < 1。 */
    @Test
    fun placeholderRowHeight_weightedByMinutes_notFullRow() {
        val irr = c(1, 8, 1, true, "16:40", "16:50")
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(irr), tj)
        val ph = plan.slots.filter { it.isPlaceholder }
        assertEquals(1, ph.size)
        val idx = plan.slots.indexOf(ph[0])
        val weight: Float? = plan.let { p -> if (idx < p.slotWeights.size) p.slotWeights[idx] else null }
        assertEquals(
            "占位行 5 分钟应只占 5/45 ≈ 0.111 标准行高, 不是整行",
            5f / 45f, weight!!, 0.01f
        )
    }

    /** 16:40 在 renderSlots 加权坐标系里仍落在节 8 内 40/45 处 (时间→像素单调一致)。 */
    @Test
    fun fracPosition_1640_mapsTo_40of45_insideNode8() {
        val irr = c(1, 8, 1, true, "16:40", "16:50")
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(irr), tj)
        val frac = TimeTableUtils.timeToFractionalRows("16:40", "16:50", plan.slots)
        assertNotNull(frac)
        // 节 8 是 idx 7 (0-based), 16:40 = 7 + 40/45
        assertEquals(7f + 40f / 45f, frac!!.first, 0.01f)
    }

    /** 混合域假冲突回归锁: ownTime 但时间不可解析(数据脏)的课, 不得与时间域课因
     *  "节点数 vs 秒数"错位比较被并进前一簇 (上一轮修复遗留缺口, 用户实测复现)。 */
    @Test
    fun dirtyOwnTimeCourse_doesNotMergeIntoPreviousTimeDomainCluster() {
        val morning = c(1, 1, 1, true, "08:20", "09:10")   // 早上非常规课
        val blank = CourseEntity(                          // ownTime 但时间为空 = 数据脏
            groupId = "g2", tableId = 1L, courseName = "脏数据", day = 1,
            startNode = 3, step = 1, startWeek = 1, endWeek = 16, color = "",
            ownTime = true, startTime = "", endTime = "", isIrregularTime = true
        )
        val afternoon = c(1, 8, 1, true, "16:40", "16:50")
        val clusters = ConflictLayoutEngine.findClusters(
            listOf(morning, blank, afternoon).map { it.normalizeNode(tj) }, tj
        )
        // 早上课与脏数据课时间毫无关系, 不得并入同一簇
        clusters.forEach { cl ->
            val names = cl.courses.map { it.courseName }
            val bad = names.contains("x") && names.contains("脏数据") &&
                cl.courses.none { it.startTime == "16:40" }
            assertEquals("时间域课与脏 ownTime 课不得因混合域错位比较成簇: $names", false, bad)
        }
    }
}
