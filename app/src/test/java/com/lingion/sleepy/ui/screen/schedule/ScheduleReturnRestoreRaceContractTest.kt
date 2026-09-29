package com.lingion.sleepy.ui.screen.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ScheduleScreen 返回主页后周次闪烁契约 (2026-09-14 报障):
 *
 * 症状: 从小组件点开 App → 返回 → 回到主页 (tab 切回 Schedule, ScheduleScreen
 * 重新进入组合树), 周视图/网格视图课表在多个周次之间反复跳变闪烁, 无需用户
 * 操作, 返回即触发。
 *
 * 根因: ScheduleScreen 因条件组合离开组合树再回来时, 两个状态源的存活策略
 * 不同 — pagerState (rememberPagerState 内部 rememberSaveable) 按离开时的
 * page 恢复, 而 syncingFromState (普通 remember) 恢复时归零 false。恢复帧上:
 *   - LaunchedEffect(pagerState.currentPage) 立即以恢复的 page 回调
 *     viewModel.changeWeek (守卫 !syncingFromState 刚初始化为 false, 不成立),
 *   - LaunchedEffect(state.selectedWeek) 同帧重启, page ≠ targetPage 就
 *     scrollToPage, 滚动期间 currentPage 每变一页都重启 effect 并再回调一次
 *     changeWeek — 每个中间页都写 VM, VM 写回又触发 scroll, 形成恢复期
 *     feedback loop。
 * 2026-09-13 修复 (syncingFromState=true 包 scrollToPage) 只挡"程序化滚动
 * 期间"的回调, 挡不住恢复帧回调与逐页重启。
 *
 * 修复契约 (结构锁定; 先例: SchedulePagerSyncRaceContractTest — 仓库无
 * Robolectric/Compose UI 测试, 声明式接线读源头文件等价于读编译产物):
 *   1. pagerState.currentPage 的回调解耦为恢复感知: 返回恢复期间
 *      (SaveableStateProvider 恢复帧) currentPage 变化不得回调
 *      viewModel.changeWeek — 锁 currentPage effect 回调体内不得直接出现
 *      changeWeek( 无守卫调用; 必须经恢复闸 (restoredSyncGate)。
 *   2. LaunchedEffect(state.selectedWeek) 的程序化滚动必须保持 2026-09-13
 *      的 syncingFromState 守卫 (回归保护, 不回退)。
 */
class ScheduleReturnRestoreRaceContractTest {

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

    /**
     * 契约 1: currentPage effect 不得无守卫回调 changeWeek。
     * 恢复帧上 syncingFromState=false 的守卫拦不住首次回调 (它就是恢复帧的
     * 第一个写), 也不拦"逐页重启再回调" — 必须存在独立的恢复闸, 使返回
     * 恢复期间 currentPage → changeWeek 的写路径被静默丢弃。
     */
    @Test
    fun pager_current_page_effect_must_use_restore_gate() {
        val body = screenSource.substringAfter("LaunchedEffect(pagerState.currentPage)")
            .substringBefore("HorizontalPager")
        // currentPage effect 只有在恢复校准完成且确实处于手势滚动时才可写 VM。
        assertTrue(
            "currentPage -> changeWeek must require pagerReady",
            Regex("""pagerReady""").containsMatchIn(body)
        )
        assertTrue(
            "currentPage -> changeWeek must require !syncingFromState",
            Regex("""!syncingFromState""").containsMatchIn(body)
        )
        assertTrue(
            "currentPage -> changeWeek must require pagerState.isScrollInProgress",
            Regex("""pagerState\.isScrollInProgress""").containsMatchIn(body)
        )
    }

    /** 契约 2 (回归保护): selectedWeek effect 的程序化滚动守卫不回退 */
    @Test
    fun selected_week_scroll_keeps_sync_guard() {
        val body = screenSource.substringAfter("LaunchedEffect(state.selectedWeek)")
            .substringBefore("LaunchedEffect(pagerState.currentPage)")
        assertTrue(
            "scrollToPage must be preceded by syncingFromState = true (2026-09-13 fix, no regression)",
            body.indexOf("syncingFromState = true") in 0 until body.indexOf("pagerState.scrollToPage")
        )
    }
}
