package com.lingion.sleepy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** HolidayManager 纯决策核 — 课程提醒"节假日跳过"判定。 */
class HolidaySkipDecisionTest {

    private val d = LocalDate.of(2026, 10, 1)

    @Test
    fun `在法定节假日且无调休 → 跳过`() {
        assertTrue(HolidayManager.decideSkipPublicHoliday(d, setOf(d), dateHasTransfer = false))
    }

    @Test
    fun `法定节假日但命中调休映射 → 不跳过 (当天要上课)`() {
        assertFalse(HolidayManager.decideSkipPublicHoliday(d, setOf(d), dateHasTransfer = true))
    }

    @Test
    fun `不在节假日集合 → 不跳过`() {
        assertFalse(HolidayManager.decideSkipPublicHoliday(d, setOf(LocalDate.of(2026, 5, 1)), dateHasTransfer = false))
    }

    @Test
    fun `空集合 网络失败态 → 不跳过 (宁误报不丢课)`() {
        assertFalse(HolidayManager.decideSkipPublicHoliday(d, emptySet(), dateHasTransfer = false))
    }
}
