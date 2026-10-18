package com.lingion.sleepy.util

import com.lingion.sleepy.data.dao.CourseColorDao
import com.lingion.sleepy.data.entity.CourseColorEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * step-5 重算层契约（spec docs/spec-course-color-harmony-2026-10-18.md §2.3 时机1/2/3/6）:
 *   - 全表重算 = 以新 anchor 重排所有 gid（用户已批准"换主题重排课表整体颜色"）;
 *   - CLASSIC（anchor=null）零写 — 渲染走 stableHue 老路径, 表保持不动（红线 spec §2.1）;
 *   - 重算结果仍满足安全弧禁区不变量 + 同批 ≥10° 间距（与 §2.2 同源纯函数保证）;
 *   - 脏输入（空白/重复 gid）在写库前被过滤去重。
 *
 * Context 系（模式/anchor 实时读）留给 UI 层手测 — 纯 JVM 先例 AppPrefsIsolationTest;
 * 本锁覆盖 recalcWithAnchor 的 anchor 注入半区（fake DAO 记录调用）。
 */
class CourseColorRecalcTest {

    private class FakeDao : CourseColorDao {
        val written = mutableListOf<CourseColorEntity>()
        override suspend fun upsertAll(rows: List<CourseColorEntity>) { written += rows }
        override suspend fun upsert(row: CourseColorEntity) { written += row }
        override suspend fun getHueMap(tableKey: String): Map<String, Float> =
            written.filter { it.tableKey == tableKey }.associate { it.groupId to it.hue }
        override suspend fun getHue(tableKey: String, groupId: String): Float? =
            written.firstOrNull { it.tableKey == tableKey && it.groupId == groupId }?.hue
        override suspend fun clear(tableKey: String) { written.removeAll { it.tableKey == tableKey } }
    }

    private fun zoneDist(hue: Float, anchor: Float): Float =
        ((hue - anchor) % 360f + 360f) % 360f

    private fun assertInAllowedArc(hue: Float, anchor: Float, ctx: String) {
        val dist = zoneDist(hue, anchor)
        assertTrue("$ctx hue=$hue anchor=$anchor dist=$dist 落入禁区",
            (dist >= 30f && dist < 150f) || (dist >= 210f && dist < 330f))
    }

    private fun ringDelta(a: Float, b: Float): Float {
        val d = ((a - b) % 360f + 360f) % 360f
        return if (d > 180f) 360f - d else d
    }

    @Test
    fun classicNullAnchor_writesNothing_redLine() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val plan = CourseColorWriteGlue.recalcWithAnchor(null, dao, listOf("g1", "g2"))
        assertTrue("CLASSIC 重算必须零写（表不动, 渲染零变化）", plan.isEmpty())
        assertTrue("CLASSIC 重算必须零写（DAO 零调用）", dao.written.isEmpty())
    }

    @Test
    fun recalc_coversEveryNonBlankDistinct_gid_only() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val gids = listOf("g1", "g2", "  ", "", "g1", "g3")
        val plan = CourseColorWriteGlue.recalcWithAnchor(280f, dao, gids)
        assertEquals("空白/重复 gid 须被过滤去重", setOf("g1", "g2", "g3"), plan.keys)
        assertEquals("落库行数 = 规划行数", plan.size, dao.written.size)
        assertTrue(dao.written.all { it.tableKey == CourseColorEntity.GLOBAL_TABLE_KEY })
        assertEquals(plan.entries.map { it.key to it.value }.toSet(),
            dao.written.map { it.groupId to it.hue }.toSet())
    }

    @Test
    fun recalc_resultSatisfiesArcAndSpacingInvariants() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val anchor = 120f
        // 严格间距只测弧容量内(≤20 门); >~24 门弧饱和 → 文档化退化=保弧弃距(§2.2 ③),
        // 30 门全 ≥10° 数学上不可满足(240° 弧 ÷ 10° = 24 槽上限)。
        val gids = (1..20).map { "recalc-g$it" }
        val plan = CourseColorWriteGlue.recalcWithAnchor(anchor, dao, gids)
        assertEquals(20, plan.size)
        for (h in plan.values) assertInAllowedArc(h, anchor, "重算安全弧不变量")
        val hues = plan.values.toList()
        for (i in hues.indices) for (j in i + 1 until hues.size) {
            assertTrue("容量内重算间距 g${i}/g${j}=${hues[i]} vs ${hues[j]}",
                ringDelta(hues[i], hues[j]) >= CourseColorUtil.MIN_SEP_DEG)
        }
    }

    @Test
    fun recalc_arcSaturation_degradesToSpacingCompromise_butArcHolds() = kotlinx.coroutines.runBlocking {
        val plan = CourseColorWriteGlue.recalcWithAnchor(
            120f, FakeDao(), (1..30).map { "over-g$it" }
        )
        assertEquals(30, plan.size)
        for (h in plan.values) assertInAllowedArc(h, 120f, "弧饱和仍不出禁区")
    }

    @Test
    fun recalc_differentAnchor_relaysOutAllGroups_approvedBehavior() = kotlinx.coroutines.runBlocking {
        val gids = (1..12).map { "g$it" }
        val purple = CourseColorWriteGlue.recalcWithAnchor(280f, FakeDao(), gids)
        val warm = CourseColorWriteGlue.recalcWithAnchor(40f, FakeDao(), gids)
        assertEquals("gid 覆盖一致", purple.keys, warm.keys)
        assertTrue("换 anchor 必须整体重排（用户批准的目标行为, spec §2.5）",
            purple.values.toSet() != warm.values.toSet())
        for (h in purple.values) assertInAllowedArc(h, 280f, "紫主题重算")
        for (h in warm.values) assertInAllowedArc(h, 40f, "暖主题重算")
    }

    @Test
    fun recalc_deterministic_sameAnchorSameGids() = kotlinx.coroutines.runBlocking {
        val gids = listOf("高数", "英语", "大物", "体育", "实验")
        val a = CourseColorWriteGlue.recalcWithAnchor(200f, FakeDao(), gids)
        val b = CourseColorWriteGlue.recalcWithAnchor(200f, FakeDao(), gids)
        assertEquals("同 (anchor, gids) 两次重算逐 bit 相等（手动重排按钮可依赖）", a, b)
    }

    @Test
    fun recalc_emptyGidList_writesNothing() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val plan = CourseColorWriteGlue.recalcWithAnchor(280f, dao, emptyList())
        assertTrue(plan.isEmpty())
        assertTrue(dao.written.isEmpty())
    }
}
