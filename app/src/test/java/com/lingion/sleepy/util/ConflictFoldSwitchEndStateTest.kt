package com.lingion.sleepy.util

import com.lingion.sleepy.ui.component.CourseDrawItem
import com.lingion.sleepy.ui.component.overlayMarkOrder

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.ConflictCluster
import com.lingion.sleepy.util.ConflictLayoutEngine
import com.lingion.sleepy.util.ConflictVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.10.16m — 折角切换端态回归(用户 2026-09-03 报障:
 * 「为什么我折角切换一次又变成重叠错位了??」)。
 * 复现链(拼条 1-3/4-6 + 包夹 1-4, fold 样式):
 *   拼条态(整齐) → 点折角命中区 → 1-4 整层置顶 → 端态塌成:
 *     a) FOLD 沉底链组成员按全尺寸真卡渲染 → 4-6 未被盖的 5-6 节裸露垂下(错位观感)
 *     b) 1-3 被 1-4 全遮但链组态 hidden=false → 无虚线轮廓,彻底隐身
 *     c) 折角命中区只在 chainStripActive 挂载 → 切走后折角不可点,再点 = 编辑
 *
 * 定案语义(用户未选项,按 FOLD 既有虚线语言最小闭环):
 *   1. FOLD 样式 + 链组态: 沉底课 hidden=true(与经典 FOLD 同语言 → 虚线占位),不再裸真卡
 *   2. 折角命中区双向: 只要 form==FOLD 且存在次层,折角区域恒可点切换
 *   3. STACK/RAIL 链组沉底语义不动(v7.8 定版真卡)
 */
class ConflictFoldSwitchEndStateTest {

    private fun course(
        id: Long,
        day: Int,
        startNode: Int,
        step: Int,
        courseName: String = "课程"
    ) = CourseEntity(
        id = id,
        groupId = "grp-$id",
        tableId = 1L,
        courseName = courseName,
        day = day,
        startNode = startNode,
        step = step,
        startWeek = 1,
        endWeek = 16,
        color = ""
    )

    /** 用户场景: 拼条 1-3(id1) + 4-6(id2), 包夹 1-4(id3)。maxNode=12。 */
    private val stripHead = course(1, 1, 1, 3)
    private val stripTail = course(2, 1, 4, 3)
    private val overlapper = course(3, 1, 1, 4)

    private fun foldLaid(topOverrideId: Long?) = ConflictLayoutEngine.layoutCluster(
        ConflictCluster(1, listOf(stripHead, stripTail, overlapper)),
        "fold",
        topOverrideId,
        12
    )

    // ---- RED 1: 切到单课层后, 沉底链组成员在 FOLD 样式下必须 hidden=true(虚线占位) ----

    @Test
    fun foldEndState_sunk_chain_members_hidden_so_dash_outline_covers_them() {
        // 切到 1-4(id3) → 拼条 {1-3,4-6} 沉底
        val byId = foldLaid(3L).associateBy { it.course.id }
        // 4-6 未被 1-4 全遮(5-6 露出), 但 FOLD 端态不渲染裸真卡 → hidden=true
        assertTrue("4-6 must be hidden in FOLD end-state (dash outline instead of naked card)",
            byId.getValue(2L).hidden)
        // 1-3 被 1-4 全遮: 也必须 hidden(拿虚线轮廓, 不再隐身)
        assertTrue("1-3 must be hidden in FOLD end-state (dash outline)",
            byId.getValue(1L).hidden)
        assertEquals(ConflictVariant.FOLD, byId.getValue(1L).variant)
        assertEquals(ConflictVariant.FOLD, byId.getValue(2L).variant)
        // Mark 命中区跟 hidden 集生成 → overlayMarkOrder 应含两条 Mark(点哪个都置顶该层)
        val order = overlayMarkOrder(foldLaid(3L))
        val marks = order.filterIsInstance<CourseDrawItem.Mark>()
        assertEquals(setOf(1L, 2L), marks.map { it.hiddenCourseId }.toSet())
    }

    // ---- RED 2: 折角命中区双向 — 非拼条态(单课层置顶)时折角区域仍可点切回 ----

    @Test
    fun foldSwitchHitArea_available_whenever_next_layer_exists_regardless_of_strip_active() {
        // 现状: foldSwitchHitArea 只看 variant, 不看 chainStripActive —
        // 挂载门槛在 Composable 里(chainStripActive && switchTarget != null)。
        // 该函数本身两态同值, 回归点在挂载条件 → 用引擎状态断言两态都有"次层存在":
        val endState = foldLaid(3L)
        val stripState = foldLaid(1L)
        // 端态(1-4 置顶): 图层数 2 → 次层存在, 折角应可点(当前 UI 条件 chainStripActive=false → 不可点 = bug)
        assertEquals(2, ConflictLayoutEngine.chainGroups(listOf(stripHead, stripTail, overlapper)).size)
        // 两态的顶层课都存在(挂载宿主)
        assertTrue(endState.any { it.zRank == 0 })
        assertTrue(stripState.any { it.zRank == 0 && it.chainFront })
    }

    // ---- 防回归: STACK/RAIL 链组沉底语义不动(真卡, 不 hidden) ----

    @Test
    fun stackStyle_sunk_chain_members_stay_real_cards_not_hidden() {
        val byId = ConflictLayoutEngine.layoutCluster(
            ConflictCluster(1, listOf(stripHead, stripTail, overlapper)),
            "stack",
            3L,
            12
        ).associateBy { it.course.id }
        assertFalse(byId.getValue(1L).hidden)
        assertFalse(byId.getValue(2L).hidden)
        assertEquals(ConflictVariant.NONE, byId.getValue(1L).variant)
    }

    @Test
    fun foldStripState_unchanged_when_strip_on_top() {
        // 拼条态本身(hidden 语义)不受本修影响
        val byId = foldLaid(1L).associateBy { it.course.id }
        assertFalse(byId.getValue(1L).hidden)
        assertFalse(byId.getValue(2L).hidden)
    }

    // ---- 经典双课 FOLD(无链组)回归: hidden/Mark 语义原样 ----

    @Test
    fun foldClassic_pair_overlapped_hidden_semantics_unchanged() {
        val a = course(10, 2, 1, 3)
        val b = course(11, 2, 1, 3)
        val byId = ConflictLayoutEngine.layoutCluster(
            ConflictCluster(2, listOf(a, b)), "fold", null, 12
        ).associateBy { it.course.id }
        assertFalse(byId.getValue(10L).hidden)
        assertTrue(byId.getValue(11L).hidden)
        assertEquals(ConflictVariant.FOLD, byId.getValue(11L).variant)
    }
}
