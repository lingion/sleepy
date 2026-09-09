package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户报障 2026-09-10 (第三轮, 簇内几何):
 *
 *  - 作息: 节8 = 16:00-16:45, 节9 = 17:05-17:50 (中间 20 分钟空隙)。
 *  - B = 非常规时间课 16:40-16:50, 与常规节8 课真实重叠 5 分钟 → 合法成簇。
 *  - 报障 1: 簇内 B 卡顶端渲染到 16:45 线**之下** (应 = 节8 行内 40/45 处)。
 *  - 报障 2: 早上 8 点段的非常规课与 B 无时间交集, 却被判冲突。
 *
 * 根因 (交叉验证 workflow 实锤):
 *  R1: ConflictClusterCard.rowGeomOf 把 timeToFractionalRows 的 (startFrac, endFrac)
 *      按 (start, span) 契约消费 → cardHOf = rowH * endFrac ≈ 9 行 (真值 1.11 行),
 *      STACK 底卡 topInset 锚定使 B 顶边压过 16:45 线。
 *  R2: 簇内几何全用裸 rowH*(行差), 外层簇框却用分钟加权 yOfRows — 两坐标系分裂,
 *      凡簇含占位行/小数行成员, 高度与位移即失真。
 *  R3: CourseDetailSheet / TodayScreen 等非网格面不带 timeJson 聚簇 + ownTime 课
 *      落库保留表单默认节点 → 节点域区间相同 → 假冲突。
 *
 * 本文件在纯函数层锁修复后的契约。
 */
class ClusterOwnTimeGeometryTest {

    private val tj = """[{"node":1,"start":"08:00","end":"08:45"},
        {"node":2,"start":"08:55","end":"09:40"},{"node":3,"start":"09:50","end":"10:35"},
        {"node":4,"start":"10:45","end":"11:30"},{"node":5,"start":"11:40","end":"12:25"},
        {"node":6,"start":"14:00","end":"14:45"},{"node":7,"start":"14:55","end":"15:40"},
        {"node":8,"start":"16:00","end":"16:45"},{"node":9,"start":"17:05","end":"17:50"},
        {"node":10,"start":"18:00","end":"18:45"},{"node":11,"start":"18:55","end":"19:40"},
        {"node":12,"start":"19:50","end":"20:35"}]"""

    private fun c(
        id: Long, day: Int, sn: Int, step: Int, own: Boolean, st: String, en: String
    ) = CourseEntity(
        id = id, groupId = "g", tableId = 1L, courseName = if (own) "B" else "REG",
        day = day, startNode = sn, step = step, startWeek = 1, endWeek = 16, color = "",
        ownTime = own, startTime = st, endTime = en, isIrregularTime = own
    )

    // ========== R3: 非网格面的假冲突 (节点域 vs 时间域) ==========

    /** 落库原样(未经 normalizeNode)的 A/B — 节点区间相同, 时间域必须不成簇。 */
    @Test
    fun detailSheet_path_rawRows_noFalseCluster_forDisjointTimes() {
        // 模拟 buildCourseEntity 落库形态: ownTime 课 startNode/step 是表单默认值 (1/2),
        // 早晚两门课节点区间 [1..2] 完全相同, 但时间 08:20-09:05 vs 16:40-16:50 天各一方。
        val morning = c(1L, 1, 1, 2, true, "08:20", "09:05")
        val b = c(2L, 1, 1, 2, true, "16:40", "16:50")
        // 时间域 (findClusters 带 timeJson = 详情页修复后的调用形态): 不成簇
        val byTime = ConflictLayoutEngine.findClusters(listOf(morning, b), tj)
        assertTrue(
            "时间零交集的 ownTime 课对不得成簇 (详情页/今日页路径)",
            byTime.isEmpty()
        )
        // 对照: 节点域旧调用 (不带 timeJson) 会误报 — 证明该测试测的是真实差异通道
        val byNode = ConflictLayoutEngine.findClusters(listOf(morning, b))
        assertEquals("节点域对照: 同节点区间会成簇(旧路径误报本体)", 1, byNode.size)
    }

    /** B 与节8 常规课时间重叠 5 分钟 → 时间域必须成簇 (冲突不是假报)。 */
    @Test
    fun detailSheet_path_realOverlap_stillClusters() {
        val reg = c(1L, 1, 8, 1, false, "", "")
        val b = c(2L, 1, 8, 1, true, "16:40", "16:50")
        val clusters = ConflictLayoutEngine.findClusters(listOf(reg, b), tj)
        assertEquals("16:40-16:50 与 16:00-16:45 重叠 5 分钟 → 成簇", 1, clusters.size)
    }

    /** 簇键域一致: 详情页/今日页写偏好用的键必须与网格 (带 timeJson) 同域,
     *  否则 radio 静默失效 + pruneConflictDefaultTop 误删。 */
    @Test
    fun clusterKey_timeDomain_matchesGridDomain_forOwnTimePair() {
        val reg = c(1L, 1, 8, 1, false, "", "")
        val b = c(2L, 1, 8, 1, true, "16:40", "16:50")
        val clusterTime = ConflictLayoutEngine.findClusters(listOf(reg, b), tj).single()
        // prune 也走时间域后, 网格簇键必然出现在 liveKeys 里
        val pruned = ConflictLayoutEngine.pruneConflictDefaultTop(
            mapOf(ConflictLayoutEngine.conflictClusterKey(clusterTime) to 2L),
            listOf(reg, b)
        )
        assertEquals("网格簇键必须被 prune 判活 (键域一致)", 1, pruned.size)
    }

    // ========== R1/R2: 簇内几何契约 (span 语义 + 分钟加权) ==========

    /**
     * 几何真值函数簇内消费契约: rowGeomOf 对 ownTime 课存的必须是
     * (startFrac, spanFrac), 即第二分量 = endFrac - startFrac (B = 9.0 - 7.889 ≈ 1.11),
     * 不是 endFrac 本身 (9.0) — cardHOf = rowH * span, 高度 ≈ 1.11 行。
     */
    @Test
    fun ownTime_cluster_span_is_endMinusStart_notEnd() {
        val reg = c(1L, 1, 8, 1, false, "", "")
        val b = c(2L, 1, 8, 1, true, "16:40", "16:50")
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(reg, b), tj)
        val frac = TimeTableUtils.timeToFractionalRows("16:40", "16:50", plan.slots)
        assertNotNull(frac)
        val span = frac!!.second - frac.first
        assertEquals("B 的真实时长 ≈ 1.111 标准行 (45 分钟行内 40 分 + 占位 5 分钟)",
            1.111f, span, 0.01f)
        // 旧 bug 值: 直接把 endFrac 当 span → 9.0 行 ≈ 8 倍失真
        assertTrue("span 严禁等于 endFrac (旧 bug 本体)", span < 2f)
    }

    /**
     * 分钟加权坐标一致性: 簇内以加权坐标表达的成员几何必须与外层 yOfRows 同一真值 —
     * B 顶端 (加权 7.889) 必须严格在 16:45 线 (加权 8.0) 之上;占位行权重 5/45。
     */
    @Test
    fun weighted_axis_B_top_above_1645_line() {
        val reg = c(1L, 1, 8, 1, false, "", "")
        val b = c(2L, 1, 8, 1, true, "16:40", "16:50")
        val plan = TimeTableUtils.buildRenderSlotPlan(listOf(reg, b), tj)
        val ws = plan.slotWeights!!
        fun yOfRows(r: Float): Float {
            var acc = 0f
            val full = r.toInt().coerceAtMost(ws.size)
            for (i in 0 until full) acc += ws[i]
            if (full < ws.size && r > full) acc += ws[full] * (r - full)
            return acc
        }
        val frac = TimeTableUtils.timeToFractionalRows("16:40", "16:50", plan.slots)!!
        val idx8 = plan.slots.indexOfFirst { it.nodeStart == 8 && !it.isPlaceholder }
        val bTop = yOfRows(frac.first)
        val line1645 = yOfRows((idx8 + 1).toFloat())
        assertTrue(
            "B 顶端 ($bTop) 必须在 16:45 线 ($line1645) 之上",
            bTop < line1645
        )
        // 40/45 精度: 顶端应在节8 行内 40 分钟处 (权重 1 的行内 40/45)
        assertEquals("顶端 = 节8 行顶 + 40/45 行", 7f + 40f / 45f, frac.first, 0.001f)
    }

    /**
     * 几何纯函数群 (conflictCardRectFrac / conflictMarkRectFrac) 在加权坐标下:
     * STACK 底卡锚自身区间右下 — 自身区间底 = 加权 ownH, 顶端 = 加权 y。
     * B(7.889..9.0) 在 minStart=7 的簇内: 底卡 y+height 底缘必须 = 加权 (9.0-7.0) 行
     * = 节9 行底, 高度 ≈ 加权 1.111 行 - inset, 不再是 rowH*9。
     */
    @Test
    fun frac_rect_stack_bottom_card_bounded_to_weighted_own_interval() {
        val rowH = 58f
        val gapH = 4f
        val inset = 7f
        val minStartRow = 7f
        val startFrac = 7f + 40f / 45f
        val span = 9f - startFrac // ≈ 1.111
        val r = com.lingion.sleepy.ui.component.conflictCardRectFrac(
            startRowFrac = startFrac,
            ownRowsFrac = span,
            isTop = false,
            form = ConflictVariant.STACK,
            colW = androidx.compose.ui.unit.Dp(100f),
            rowH = androidx.compose.ui.unit.Dp(rowH),
            gapH = androidx.compose.ui.unit.Dp(gapH),
            minStartRow = minStartRow,
            topInset = androidx.compose.ui.unit.Dp(inset)
        )
        val ownH = rowH * span - gapH
        val h = ownH - inset
        assertEquals("底卡高度 = 加权自身时长 - inset", androidx.compose.ui.unit.Dp(h), r.height)
        assertEquals(
            "底卡下缘贴自身加权区间底 (节9 行底 = 加权 2.0 行处)",
            androidx.compose.ui.unit.Dp(rowH * (9f - minStartRow) - gapH),
            r.y + r.height
        )
        // 顶端不越过 16:45 线 (16:45 线 = 加权 8.0 - minStart 7.0 = 1.0 行 = rowH):
        // STACK 底卡 y = y + ownH - h = inset, 但视觉顶端 = r.y (簇内) — 旧 inset 锚定
        // 使顶端 < 自身起点, 用户看到的是"顶端低于 16:45 线"。修复后坐标以加权轴表达,
        // 底缘钉死自身区间底, 高度=真实时长 → 顶端自然回到起点上方误差 ≤ inset。
        assertTrue(
            "底卡下缘必须等于自身区间底 (锚定基准)",
            (r.y + r.height).value.toDouble()
                .isEqualTo(androidx.compose.ui.unit.Dp(rowH * (9f - minStartRow) - gapH).value.toDouble())
        )
    }

    private fun Double.isEqualTo(o: Double): Boolean = kotlin.math.abs(this - o) < 1e-6
}
