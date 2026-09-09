package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure-JVM tests for [TodayDateNavCore] — 每日课程小组件日期导航的状态核心。
 *
 * 状态语义(与 [[WidgetBindingCore]] 同模式, Android-free):
 * - 每个 appWidgetId 独立存 (selectedEpochDay, anchorEpochDay);
 * - anchor == 今天的 epochDay → 显示 selected (用户导航态);
 * - anchor 过期(跨天) → 显示今天 (R5: 到新的一天回到今天)。
 * 导航偏移钳制在 ±[TodayDateNavCore.MAX_ABS_OFFSET_DAYS] 天内。
 */
class TodayDateNavCoreTest {

    private val todayEpochDay: Long = LocalDate.of(2026, 9, 9).toEpochDay()

    // ---- 稳定契约: prefs 文件名 + key 形状 ----

    @Test
    fun `prefs name and key prefixes are stable contract`() {
        assertEquals("widget_today_nav", TodayDateNavCore.PREFS_NAME)
        assertEquals("today_nav_sel_", TodayDateNavCore.KEY_SEL_PREFIX)
        assertEquals("today_nav_anchor_", TodayDateNavCore.KEY_ANCHOR_PREFIX)
        assertEquals("today_nav_sel_42", TodayDateNavCore.keySel(42))
        assertEquals("today_nav_anchor_42", TodayDateNavCore.keyAnchor(42))
    }

    // ---- parseAll: 只认自家 key 形状 ----

    @Test
    fun `parseAll keeps only own widget entries ignoring foreign keys`() {
        val parsed = TodayDateNavCore.parseAll(
            mapOf(
                "today_nav_sel_1" to 100L,
                "today_nav_anchor_1" to 100L,
                "today_nav_sel_abc" to 7L,      // 非 int id — 忽略
                "app_widget_1" to 999L,          // 别家的 key(binding store) — 忽略
                "random" to 5L
            )
        )
        assertEquals(setOf(1), parsed.keys)
        assertEquals(100L, parsed[1]?.selectedEpochDay)
        assertEquals(100L, parsed[1]?.anchorEpochDay)
    }

    @Test
    fun `parseAll on empty map returns empty map`() {
        assertTrue(TodayDateNavCore.parseAll(emptyMap()).isEmpty())
    }

    // ---- resolveTarget: 无状态/导航态/跨天 ----

    @Test
    fun `resolveTarget returns today when nothing stored`() {
        val target = TodayDateNavCore.resolveTargetEpochDay(null, todayEpochDay)
        assertEquals(todayEpochDay, target)
    }

    @Test
    fun `resolveTarget returns selected date while anchor is current day`() {
        val entry = TodayDateNavCore.Entry(
            selectedEpochDay = todayEpochDay + 1,
            anchorEpochDay = todayEpochDay
        )
        assertEquals(todayEpochDay + 1, TodayDateNavCore.resolveTargetEpochDay(entry, todayEpochDay))
    }

    @Test
    fun `resolveTarget falls back to today after crossing midnight (stale anchor)`() {
        // 昨天(9/8)导航到 9/10; 今天(9/9)更新 → 回今天 (R5)
        val stale = TodayDateNavCore.Entry(
            selectedEpochDay = LocalDate.of(2026, 9, 10).toEpochDay(),
            anchorEpochDay = LocalDate.of(2026, 9, 8).toEpochDay()
        )
        assertEquals(todayEpochDay, TodayDateNavCore.resolveTargetEpochDay(stale, todayEpochDay))
    }

    @Test
    fun `resolveTarget is defensive against future anchor`() {
        val future = TodayDateNavCore.Entry(
            selectedEpochDay = todayEpochDay,
            anchorEpochDay = todayEpochDay + 5
        )
        assertEquals(todayEpochDay, TodayDateNavCore.resolveTargetEpochDay(future, todayEpochDay))
    }

    // ---- shift: 推进/回退, 锚点重置, 钳制 ----

    @Test
    fun `shift with no state starts from today`() {
        val e = TodayDateNavCore.shift(null, todayEpochDay, deltaDays = 1)
        assertEquals(todayEpochDay + 1, e.selectedEpochDay)
        assertEquals(todayEpochDay, e.anchorEpochDay)
    }

    @Test
    fun `shift chains across calls to accumulate offset`() {
        val e1 = TodayDateNavCore.shift(null, todayEpochDay, deltaDays = 1)
        val e2 = TodayDateNavCore.shift(e1, todayEpochDay, deltaDays = 1)
        assertEquals(todayEpochDay + 2, e2.selectedEpochDay)
    }

    @Test
    fun `shift backwards from today goes to previous day`() {
        val e = TodayDateNavCore.shift(null, todayEpochDay, deltaDays = -1)
        assertEquals(todayEpochDay - 1, e.selectedEpochDay)
    }

    @Test
    fun `shift rebases on today when previous entry is stale`() {
        // 昨天(9/8)导航到 9/8+3; 跨天后(9/9)再点后一天 → 基准=9/9 → 9/10
        val stale = TodayDateNavCore.Entry(
            selectedEpochDay = LocalDate.of(2026, 9, 8).toEpochDay() + 3,
            anchorEpochDay = LocalDate.of(2026, 9, 8).toEpochDay()
        )
        val e = TodayDateNavCore.shift(stale, todayEpochDay, deltaDays = 1)
        assertEquals(todayEpochDay + 1, e.selectedEpochDay)
    }

    @Test
    fun `shift clamps at max forward offset`() {
        var e: TodayDateNavCore.Entry? = null
        repeat(200) { e = TodayDateNavCore.shift(e, todayEpochDay, deltaDays = 1) }
        assertEquals(todayEpochDay + TodayDateNavCore.MAX_ABS_OFFSET_DAYS, e!!.selectedEpochDay)
        assertEquals(TodayDateNavCore.MAX_ABS_OFFSET_DAYS, e.selectedEpochDay - todayEpochDay)
    }

    @Test
    fun `shift clamps at max backward offset`() {
        var e: TodayDateNavCore.Entry? = null
        repeat(200) { e = TodayDateNavCore.shift(e, todayEpochDay, deltaDays = -1) }
        assertEquals(todayEpochDay - TodayDateNavCore.MAX_ABS_OFFSET_DAYS, e!!.selectedEpochDay)
    }

    @Test
    fun `shift clamped entry stays put when trying to exceed limit`() {
        val atLimit = TodayDateNavCore.Entry(
            selectedEpochDay = todayEpochDay + TodayDateNavCore.MAX_ABS_OFFSET_DAYS,
            anchorEpochDay = todayEpochDay
        )
        val e = TodayDateNavCore.shift(atLimit, todayEpochDay, deltaDays = 1)
        assertEquals(todayEpochDay + TodayDateNavCore.MAX_ABS_OFFSET_DAYS, e.selectedEpochDay)
    }

    // ---- 实例隔离 (R4): 各 appWidgetId 互不干扰 ----

    @Test
    fun `different widget ids keep independent selections`() {
        val state = mutableMapOf<String, Long>()
        TodayDateNavCore.write(state, 42, TodayDateNavCore.shift(null, todayEpochDay, 1))
        TodayDateNavCore.write(state, 43, TodayDateNavCore.shift(null, todayEpochDay, -1))
        // 42 号在 +1, 43 号在 -1, 互不污染
        assertEquals(todayEpochDay + 1, TodayDateNavCore.read(state, 42)!!.selectedEpochDay)
        assertEquals(todayEpochDay - 1, TodayDateNavCore.read(state, 43)!!.selectedEpochDay)
    }

    @Test
    fun `read returns null when absent`() {
        assertNull(TodayDateNavCore.read(mapOf(), 42))
    }

    @Test
    fun `delete removes only the target widget entry`() {
        val state = mutableMapOf<String, Long>()
        TodayDateNavCore.write(state, 42, TodayDateNavCore.Entry(todayEpochDay + 1, todayEpochDay))
        TodayDateNavCore.write(state, 43, TodayDateNavCore.Entry(todayEpochDay - 1, todayEpochDay))
        TodayDateNavCore.delete(state, 42)
        assertNull(TodayDateNavCore.read(state, 42))
        assertEquals(todayEpochDay - 1, TodayDateNavCore.read(state, 43)!!.selectedEpochDay)
    }

    // ---- write/delete 的 key 形状 (prefs 落盘契约) ----

    @Test
    fun `write uses both sel and anchor keys`() {
        val state = mutableMapOf<String, Long>()
        TodayDateNavCore.write(state, 42, TodayDateNavCore.Entry(todayEpochDay + 1, todayEpochDay))
        assertTrue(TodayDateNavCore.KEY_SEL_PREFIX + "42" in state)
        assertTrue(TodayDateNavCore.KEY_ANCHOR_PREFIX + "42" in state)
        assertFalse(TodayDateNavCore.KEY_SEL_PREFIX + "43" in state)
    }
}
