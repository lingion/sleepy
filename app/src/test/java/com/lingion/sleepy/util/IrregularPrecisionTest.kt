package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    /** 占位行权重可见下限 — 与 TimeTableUtils.PLACEHOLDER_MIN_WEIGHT 同值契约。 */
    private companion object {
        const val PLACEHOLDER_MIN_WEIGHT = 0.36f
    }

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

    /** 占位行 = 16:45-16:50 只有 5 分钟, 行高按分钟加权但带可见下限:
     *  5min/45min ≈ 0.111 裸比例 → 被下限 0.36 顶起(渲染层扣 gap/padding 后须剩可见内容);
     *  仍 < 1(不整行拉满)。实现落点: RenderSlotPlan.slotWeights。 */
    @Test
    fun placeholderRowHeight_weightedByMinutes_notFullRow() {
        val irr = c(1, 8, 1, true, "16:40", "16:50")
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(irr), tj)
        val ph = plan.slots.filter { it.isPlaceholder }
        assertEquals(1, ph.size)
        val idx = plan.slots.indexOf(ph[0])
        val weight: Float? = plan.let { p -> p.slotWeights?.getOrNull(idx) }
        assertEquals(
            "占位行权重 = max(5/45, 0.36) = 0.36 — 可见下限, 且 < 1 不整行",
            maxOf(5f / 45f, TimeTableUtils.PLACEHOLDER_MIN_WEIGHT), weight!!, 0.001f
        )
    }

    /** 用户报障 2026-09-10: 5 分钟占位行加权后 6.2dp − gapH − padding ≤ 0,
     *  时间文字挤没了, 时间轴上只剩一条 ~2dp 色条 = 肉眼隐形。
     *  权重必须带可见下限(够渲染一行 micro 时间文字 ≈ 0.36 行), 行高与 y 同源仍自洽。 */
    @Test
    fun placeholderRowWeight_hasMinVisibleFloor_notInvisible() {
        val irr = c(1, 8, 1, true, "16:40", "16:50")
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(irr), tj)
        val ph = plan.slots.filter { it.isPlaceholder }
        assertEquals(1, ph.size)
        val idx = plan.slots.indexOf(ph[0])
        val weight: Float? = plan.let { p -> p.slotWeights?.getOrNull(idx) }
        // 5min/45min = 0.111 → 被下限顶起; 20min/45min = 0.444 → 下限不咬
        assertEquals(PLACEHOLDER_MIN_WEIGHT, weight!!, 0.001f)
    }

    /** 占位行下限不破坏时间轴自洽: y 前缀和与行高同源(同一 ws 数组), 权重和只增不减; 占位行 ∈ [0.36, 1)。 */
    @Test
    fun placeholderFloor_keepsAxisConsistent_sumMatches() {
        val irr = c(1, 8, 1, true, "16:40", "16:50")
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(irr), tj)
        val ws = plan.slotWeights!!
        // 12 标准行(权重恒 1) + 1 占位行(权重 = max(5/45, 0.36) = 0.36) = 12.36
        assertEquals(12f + TimeTableUtils.PLACEHOLDER_MIN_WEIGHT, ws.sum(), 0.001f)
        // 占位行权重仍 < 1(不整行拉满) 且 ≥ 下限
        plan.slots.forEachIndexed { i, s ->
            if (s.isPlaceholder) {
                assertTrue(ws[i] >= PLACEHOLDER_MIN_WEIGHT)
                assertTrue(ws[i] < 1f)
            }
        }
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

    // ===============================================================
    // 用户反馈 2026-09-16: 占位节次文字放不下 → 灰置 + 点击展开/折叠
    // ===============================================================

    /** 检测阈值: 时间文字(11dp, micro 一行) + 上下 padding(8dp) = 内容区至少 19dp。
     *  行渲染高 = rowH * weight − gapH(4dp); 52dp 标准行下 0.36 权重 = 14.7dp < 19dp
     *  → 放不下 = needsExpansion; 0.6 权重 = 27.2dp ≥ 19dp → 放得下。 */
    @Test
    fun placeholderTextFits_5minRow_belowThreshold_needsExpansion() {
        // 5min 占位: 权重 = max(0.111, 0.36) = 0.36 → 52*0.36 - 4 = 14.7dp 内容区
        val fits = TimeTableUtils.placeholderTextFits(
            rowWeight = 0.36f, rowHeightDp = 52f, gapDp = 4f, requiredTextHeightDp = 19f
        )
        assertEquals("0.36 权重行放不下 19dp 文字 → 需灰置+可展开", false, fits)
    }

    @Test
    fun placeholderTextFits_longGapRow_aboveThreshold_showsText() {
        // 午休长空隙占位: 50min/45min > 1 但 clamp < 1; 用 0.8 权重 → 52*0.8 - 4 = 37.6dp
        val fits = TimeTableUtils.placeholderTextFits(
            rowWeight = 0.8f, rowHeightDp = 52f, gapDp = 4f, requiredTextHeightDp = 19f
        )
        assertEquals("0.8 权重行放得下时间文字 → 正常显示不灰置", true, fits)
    }

    /** 阈值必须随行高缩放联动: 用户捏合把行高压到 36dp 时, 同一 0.36 权重行
     *  内容区 = 36*0.36 - 4 = 8.96dp 更放不下; 放大行高到 96dp 时 0.36 权重
     *  = 30.6dp 反而放得下 — 检测是几何函数不是常量。 */
    @Test
    fun placeholderTextFits_scalesWithRowHeight() {
        val small = TimeTableUtils.placeholderTextFits(
            rowWeight = 0.36f, rowHeightDp = 36f, gapDp = 4f, requiredTextHeightDp = 19f
        )
        val large = TimeTableUtils.placeholderTextFits(
            rowWeight = 0.36f, rowHeightDp = 96f, gapDp = 4f, requiredTextHeightDp = 19f
        )
        assertEquals("小行高放不下", false, small)
        assertEquals("大行高放得下", true, large)
    }

    /** 展开高度 = 正好显示完文字: requiredTextHeightDp + gapDp, 换算回权重 = (19+4)/52 ≈ 0.442。
     *  必须大于原权重(展开只增不减)且小于 1 整行(不占满)。 */
    @Test
    fun placeholderExpandedWeight_exactTextHeight_neverFullRow() {
        val w = TimeTableUtils.placeholderExpandedWeight(
            rowHeightDp = 52f, gapDp = 4f, requiredTextHeightDp = 19f
        )
        assertEquals((19f + 4f) / 52f, w, 0.001f)
        assertTrue("展开权重必须 < 1 不占满整行", w < 1f)
    }
}
