package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ScheduleScreen HorizontalPager 双向同步 race 契约 (2026-09-13 闪烁修复):
 *
 * 症状: 两张课表间切换 (A → B) → 回到 ScheduleScreen 后, 周视图在多周之间
 * 反复跳变闪烁。Realme OS 复现, ColorOS 同源。
 *
 * 根因: selectTable → loadCourses 异步重置 selectedWeek(currentWeek) →
 * LaunchedEffect(state.selectedWeek) 驱动 pagerState.scrollToPage → scroll 期间
 * pagerState.currentPage 变化 → LaunchedEffect(pagerState.currentPage) 回调
 * viewModel.changeWeek → selectedWeek 再变 → 另一轮 scrollToPage → 反向同步
 * 打架, 形成 feedback loop。
 *
 * 修复: 在 scrollToPage 之前把 syncingFromState= true, scroll 完成后复位为 false,
 * 阻止 scroll 期间 currentPage 回调反向触发 changeWeek。
 *
 * 锁法 (源码结构断言, 与 ScheduleViewModeSessionContractTest 同流):
 *   1. LaunchedEffect(state.selectedWeek) 回调体内, scrollToPage 前必须
 *      设置 syncingFromState = true;
 *   2. scrollToPage 必须在 try/finally 中, finally 复位 syncingFromState = false;
 *   3. LaunchedEffect(pagerState.currentPage) 回调必须检查 !syncingFromState。
 */
class SchedulePagerSyncRaceContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/"),
            File("src/main/java/com/lingion/sleepy/"),
            File("/Users/lingion_k/sleepy/app/src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val screenSource: String by lazy {
        loadSource("ui/screen/schedule/ScheduleScreen.kt")
    }

    /** 契约 1: scrollToPage 前必须设置 syncingFromState = true */
    @Test
    fun sync_guard_sets_syncing_true_before_scroll() {
        val body = screenSource.substringAfter("LaunchedEffect(state.selectedWeek)")
            .substringBefore("LaunchedEffect(pagerState.currentPage)")
        assertTrue(
            "Before calling pagerState.scrollToPage, syncingFromState must be set to true",
            Regex("""syncingFromState\s*=\s*true""").containsMatchIn(body)
        )
        assertTrue(
            "syncingFromState = true must appear before scrollToPage call",
            body.indexOf("syncingFromState = true") < body.indexOf("pagerState.scrollToPage")
        )
    }

    /** 契约 2: scrollToPage 必须在 try/finally 中, finally 复位 syncingFromState = false */
    @Test
    fun sync_guard_resets_syncing_false_in_finally() {
        val body = screenSource.substringAfter("LaunchedEffect(state.selectedWeek)")
            .substringBefore("LaunchedEffect(pagerState.currentPage)")
        assertTrue(
            "scrollToPage must be wrapped in try/finally",
            Regex("""try\s*\{""").containsMatchIn(body) &&
            Regex("""finally\s*\{""").containsMatchIn(body)
        )
        assertTrue(
            "finally block must reset syncingFromState = false",
            Regex("""finally\s*\{[^}]*syncingFromState\s*=\s*false""").containsMatchIn(body)
        )
    }

    /** 契约 3: LaunchedEffect(pagerState.currentPage) 回调必须检查 !syncingFromState */
    @Test
    fun pager_current_page_effect_checks_sync_guard() {
        val body = screenSource.substringAfter("LaunchedEffect(pagerState.currentPage)")
            .substringBefore("HorizontalPager")
        assertTrue(
            "LaunchedEffect(pagerState.currentPage) must guard with !syncingFromState",
            Regex("""if\s*\(\s*!syncingFromState\s*\)""").containsMatchIn(body)
        )
    }
}
