package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 课表屏微动效契约 (MD3E motion 全量补齐 Task 6, 2026-10-29):
 *   1. 周次程序化跳转全部缓动 — LaunchedEffect(state.selectedWeek) 里
 *      pagerState.scrollToPage → animateScrollToPage (计划裁决: 程序化跳转一律
 *      animate, 无「用户设置要求瞬时」的例外)。syncingFromState 竞态守卫由
 *      SchedulePagerSyncRaceContractTest / ScheduleReturnRestoreRaceContractTest
 *      锁字面量, 两处随改名同步改写 (lockstep)。
 *   2. 撤回/取消撤回胶囊 — 显隐从 if(hasUndo||hasRedo) 改 AnimatedVisibility
 *      (sleepyPopup 家族, T1 工厂): 出入场弹一下是 popup 家族签名性格;
 *      一体显隐语义 (同进同出) 不变, 由 MineNavRedoCourseListContractTest
 *      随改写的挂载正则锁。
 *   3. 底栏徽标圆点 ×2 (贴底 + Dock 两形态) — if(item.badge) 直挂改
 *      AnimatedVisibility popup 家族, 出现/消失有弹入弹出。
 *   4. 滑杆幽灵层 — 幽灵 Canvas 用 AnimatedVisibility(visible = ghostRange != null)
 *      包 popup 家族: 隐藏时节点必须真的从树里移除 (IntRangeSliderGhostPipelineTest
 *      的 testTag 存在性断言依赖此语义, 不许改成 alpha 常驻)。
 *
 * 载体形态: ScheduleScreen 走 ViewModel/Room 全家桶, UndoManager 是进程单例 —
 * 声明式接线按 T3/T4/T5 定型先例用源码结构锁定; 徽标圆点 (公开 PillNavigationBar
 * + PillNavItemSpec(badge) 可驱动) 的行为终态另有 Robolectric 管线测试。
 */
class ScheduleMotionContractTest {

    private fun loadSource(relPath: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            java.io.File(root, relPath).takeIf { it.isFile }?.readText()
        } ?: error("Unable to load $relPath")

    private val screen: String by lazy { loadSource("ui/screen/schedule/ScheduleScreen.kt") }
    private val pill: String by lazy { loadSource("ui/component/PillNavigationBar.kt") }
    private val slider: String by lazy { loadSource("ui/component/IntRangeSlider.kt") }

    // ---- 契约 1: 程序化周次跳动画化 ----

    @Test
    fun programmatic_week_jump_animates() {
        assertTrue("周次同步必须用 animateScrollToPage (程序化跳转全缓动)",
            screen.contains("pagerState.animateScrollToPage("))
        assertFalse("裸 scrollToPage 不得残留 (跳页不许瞬移)",
            screen.contains("pagerState.scrollToPage("))
        // 改名不许把竞态守卫结构带走: syncingFromState 仍包在 try/finally 里
        assertTrue("animateScrollToPage 前必须仍置 syncingFromState = true",
            screen.substringAfter("LaunchedEffect(state.selectedWeek)")
                .substringBefore("LaunchedEffect(pagerState.currentPage)")
                .let { body ->
                    Regex("""syncingFromState\s*=\s*true""").containsMatchIn(body) &&
                        body.indexOf("syncingFromState = true") <
                        body.indexOf("pagerState.animateScrollToPage")
                })
    }

    // ---- 契约 2: 撤回胶囊走 popup 家族 ----

    @Test
    fun undo_redo_capsule_mounts_via_popup_family() {
        assertTrue("胶囊显隐必须挂 AnimatedVisibility popup 家族 (T1 工厂, 不得手抄)",
            Regex("""AnimatedVisibility\(\s*visible = hasUndo \|\| hasRedo,\s*enter = sleepyPopupEnter\(\),\s*exit = sleepyPopupExit\(\)""")
                .containsMatchIn(screen))
        assertFalse("旧的 if 直挂显隐不得残留 (会砍掉退场动画)",
            Regex("""if\s*\(hasUndo\s*\|\|\s*hasRedo\)""").containsMatchIn(screen))
    }

    // ---- 契约 3: 徽标圆点两形态都走 popup 家族 ----

    @Test
    fun badge_dots_swing_via_popup_family_in_both_variants() {
        assertEquals("贴底 + Dock 两处徽标都必须改 AnimatedVisibility", 2,
            Regex("""visible = item\.badge""").findAll(pill).count())
        assertEquals("两形态各一组 popup 家族 enter/exit", 2,
            Regex("""enter = sleepyPopupEnter\(\)""").findAll(pill).count())
        assertEquals(2, Regex("""exit = sleepyPopupExit\(\)""").findAll(pill).count())
        assertFalse("if(item.badge) 直挂不得残留", Regex("""if \(item\.badge\)""").containsMatchIn(pill))
        assertTrue("徽标节点必须带管线测试锚点 testTag", pill.contains("\"nav-badge\""))
    }

    // ---- 契约 4: 幽灵层走 popup 家族且保留节点移除语义 ----

    @Test
    fun slider_ghost_swings_via_popup_family() {
        assertTrue("幽灵必须挂 AnimatedVisibility (visible = ghostRange != null)",
            Regex("""AnimatedVisibility\(\s*visible = ghostRange != null""").containsMatchIn(slider))
        assertTrue("幽灵入退场必须用 popup 家族",
            slider.contains("enter = sleepyPopupEnter()") && slider.contains("exit = sleepyPopupExit()"))
        assertTrue("幽灵 testTag 必须留在幽灵本体上 (管线存在性断言的锚)",
            slider.contains(".testTag(\"slider-ghost\")"))
        assertFalse("不得改成 alpha 常驻 (初始无节点断言会失去意义)",
            Regex("""alpha = .*ghostVisible""").containsMatchIn(slider))
    }
}
