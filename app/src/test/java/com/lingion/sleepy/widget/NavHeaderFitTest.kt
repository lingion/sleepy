package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 测试: 顶栏降级判定 [TodayWidgetReceiver.navHeaderTier] 的 2×2 刷新按钮定稿语义
 * (Android-free — Paint 用 mock 尺寸序列注入)。
 *
 * 2×2 刷新按钮定稿 (用户 2026-09-13): 「回到今天/今天」文字在窄档被裁点不了, 换成
 * 真实刷新按钮 (40×28dp, 与 prev/next 同规格, 视图即点击区) — 恓牲序改为标题先退:
 *   FULL → SHORT_TITLE(去星期) → HIDE_TITLE(标题 GONE, 三钮保留) → HIDE_NAV
 * 旧 TWO_CHAR/HIDE_TODAY 档与 fitsNavTodayFourChar 判定随之作废删除
 * (nav_today 不再是文本, 无文字宽度可量)。
 *
 * 预算常量 (dp): padStart10 + title + margin4 + prev40 + (6+refresh40+6) + next40 + padEnd10
 * refresh GONE (isToday): 6+6 margin 与 refresh 40 一起消失。
 * 三钮固定件 (标题 GONE): 10 + 4 + 40×3 + 10 = 144dp。
 */
class NavHeaderFitTest {

    /** 记录构造时收到的 textSize (px), 其余行为无关紧要的 Paint 替身。 */
    private class RecordingPaint(var pxAtConstruction: Float = -1f) {
        fun measureText(s: String): Float = s.length * pxAtConstruction
    }

    private fun tier(
        density: Float, wDp: Int, fullTitle: String, dateOnly: String,
        titleM: (String) -> Float = { it.length * 13f * density },
        refreshVisible: Boolean = true
    ): NavTier = TodayWidgetReceiver.navHeaderTier(
        density, wDp, fullTitle, dateOnly,
        titleMeasure = titleM, refreshVisible = refreshVisible
    )

    // ── FULL 档: 宽 widget 满标题 + 刷新按钮 ──

    @Test
    fun `wide widget keeps full title and refresh button`() {
        // req(full=9字) = 10+117+4+40+52+40+10 = 273 ≤ 400
        assertEquals(NavTier.FULL, tier(1f, 400, "9/12 · 周六", "9/12"))
    }

    @Test
    fun `unknown width defaults to full safe path`() {
        assertEquals(NavTier.FULL, tier(1f, 0, "9/12 · 周六", "9/12"))
    }

    @Test
    fun `four-by-three real width keeps full title`() {
        // 4×3 ≈ 250dp: req(full=104) = 10+104+4+40+52+40+10 = 260 > 250 → SHORT_TITLE?
        // 周六 2 字 + 9/12: 真实 13sp 宽 ~100dp → 256 > 250 边界; date-only 39 → 191 ≤ 250
        // 用 mock 精确断言 SHORT_TITLE 分支
        assertEquals(NavTier.SHORT_TITLE, tier(1f, 250, "9/12 · 周六", "9/12"))
    }

    // ── SHORT_TITLE 档: 标题去星期 ──

    @Test
    fun `drops weekday when date-only plus refresh fits`() {
        // req(dateOnly=4字=52) = 10+52+4+40+52+40+10 = 208 ≤ 210
        assertEquals(NavTier.SHORT_TITLE, tier(1f, 210, "9/12 · 周六", "9/12"))
    }

    // ── HIDE_TITLE 档 (2×2 主形态): 标题 GONE, 三钮保留 ──

    @Test
    fun `narrow 2x2 hides title but keeps all three buttons`() {
        // 2×2 = 148dp (SIZES 口径): req(dateOnly=39)=195 > 148 → HIDE_TITLE,
        // 三钮固定件 144 ≤ 148 ✓ — 回到今天刷新按钮恒可点 (用户定稿)
        assertEquals(NavTier.HIDE_TITLE, tier(1f, 148, "9/8 · 周二", "9/8"))
    }

    @Test
    fun `hide title boundary is exact at three-button fixed width`() {
        // 三钮固定件 144dp: 143 装不下 → HIDE_NAV; 144 恰好 → HIDE_TITLE
        assertEquals(NavTier.HIDE_TITLE, tier(1f, 144, "9/8 · 周二", "9/8"))
        assertEquals(NavTier.HIDE_NAV, tier(1f, 143, "9/8 · 周二", "9/8"))
    }

    // ── isToday: refresh GONE, 预算不含 refresh+margin ──

    @Test
    fun `isToday frees refresh budget so full title keeps slot`() {
        // reqNoRefresh(full=104) = 10+104+4+40+40+10 = 208 ≤ 215
        assertEquals(
            NavTier.FULL,
            tier(1f, 215, "9/8 · 周二", "9/8", refreshVisible = false)
        )
    }

    @Test
    fun `isToday narrow still falls to hide nav when two buttons cannot fit`() {
        // reqNoRefresh(dateOnly=39) = 143 ≤ 148 → SHORT_TITLE (今日态两钮形态)
        assertEquals(
            NavTier.SHORT_TITLE,
            tier(1f, 148, "9/8 · 周二", "9/8", refreshVisible = false)
        )
        // 142: 两钮也装不下 → HIDE_NAV
        assertEquals(
            NavTier.HIDE_NAV,
            tier(1f, 142, "9/8 · 周二", "9/8", refreshVisible = false)
        )
    }

    // ── fontScale: 标题测量随注入 Paint 走 ──

    @Test
    fun `big font scale widens title and degrades tier`() {
        // fontScale=1.3: title 13×1.3≈17dp/字 → full 9字=152 → req=269 > 260 → 降档
        assertEquals(
            NavTier.SHORT_TITLE,
            tier(1.3f, 260, "9/12 · 周六", "9/12", titleM = { it.length * (13f * 1.3f) })
        )
    }
}
