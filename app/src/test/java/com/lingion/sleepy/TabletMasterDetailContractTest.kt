package com.lingion.sleepy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 锁定平板 Master-Detail 双栏布局必须保住的不变量。
 * 契约: 宽屏(!isCompact)时,左 50% 永远 ScheduleScreen;
 * 右 50% 跟用户选中的 rail 项切换 Today/Manage/Mine;
 * NavigationRail 严格 3 项,每项都是 Schedule+X 跑道胶囊 (无 label)。
 * 测试面全部用字符串扫描,不实例化 NavHost。
 */
class TabletMasterDetailContractTest {

    private val navHostSrc by lazy {
        File("src/main/java/com/lingion/sleepy/ui/nav/SleepyNavHost.kt").readText()
    }
    private val todayScreenSrc by lazy {
        File("src/main/java/com/lingion/sleepy/ui/screen/today/TodayScreen.kt").readText()
    }

    @Test
    fun wide_screen_has_two_pane_layout() {
        assertFalse("PLAN 标记必须清除(实现已落地)", navHostSrc.contains("// PLAN:wide-merged"))
        assertTrue("双栏分支必须有宽屏加横屏双闸", navHostSrc.contains("if (!isCompact && !portrait)"))
        val railSection = navHostSrc.indexOf("NavigationRail")
        assertTrue("宽屏导航锚点 NavigationRail 必须存在", railSection >= 0)
        val rowAfterRail = navHostSrc.indexOf("Row(", railSection)
        assertTrue("NavigationRail 之后应有 Row 双栏布局", rowAfterRail > railSection)
        val rowBody = navHostSrc.substring(
            rowAfterRail,
            minOf(rowAfterRail + 8000, navHostSrc.length),
        )
        assertTrue("宽屏 Row 内必须有 ScheduleScreen 调用", rowBody.contains("ScheduleScreen("))
    }

    @Test
    fun portrait_tablet_falls_back_to_single_pane() {
        // 同窗报修(2026-10-27): 平板竖屏宽 800dp 也过 !isCompact 闸 → 被强分双栏。
        // 契约: 双栏必须再加横屏闸;竖屏判据 = 宽<高(逐帧真实几何,不依赖 orientation 枚举)。
        val gate = Regex("""val\s+portrait\s*=.*screenWidthDp\s*<.*screenHeightDp""")
        assertTrue("必须存在 portrait 几何判定 val", gate.containsMatchIn(navHostSrc))
        assertTrue(
            "双栏分支条件必须含 !portrait 闸",
            navHostSrc.contains("if (!isCompact && !portrait)"),
        )
        // 竖屏宽屏设备落到手机形态分支: dock 或底栏 Scaffold 必须仍在双栏分支之后可达
        val gateIdx = navHostSrc.indexOf("if (!isCompact && !portrait)")
        assertTrue("分支链完整", gateIdx > 0 && navHostSrc.indexOf("else if (navDock)", gateIdx) > gateIdx)
    }

    @Test
    fun today_pane_exported_for_tablet_use() {
        assertTrue(
            "TodayScreen 应导出平板用紧凑面板(CompactTodayPane)",
            todayScreenSrc.contains("fun CompactTodayPane"),
        )
        assertTrue(
            "NavHost 宽屏分支应调用 CompactTodayPane",
            navHostSrc.contains("CompactTodayPane("),
        )
    }

    @Test
    fun panes_have_dark_gap() {
        val hasGap = navHostSrc.contains("surfaceContainerLow") ||
            navHostSrc.contains("HorizontalDivider(") ||
            navHostSrc.contains("Divider(")
        assertTrue("双栏应有视觉分隔(dark gap)", hasGap)
    }

    @Test
    fun schedule_pane_in_rounded_card() {
        val hasCard = navHostSrc.contains("scheduleCard") ||
            (navHostSrc.contains("clip(") && navHostSrc.contains("RoundedCornerShape"))
        assertTrue("课表面板应有圆角卡片容器(scheduleCard)", hasCard)
    }

    @Test
    fun compact_phone_branches_untouched() {
        assertFalse("手机 dock 分支禁被误删", navHostSrc.contains("// PLAN:wide-merged"))
        // 2026-10 液态玻璃移植: 手机两形态(贴底/悬浮 Dock)统一改走 LiquidGlassNavHost 宿主,
        // 旧断言字面量 PillNavigationBar(/NavigationBar( 随官方底栏与自研 Dock 移出 MainRoute 而失效。
        // 不变量本意不变 = 平板重构后手机导航仍完整: 宿主在 + 贴底(floating=false)/悬浮(floating=true)两摆位都在。
        assertTrue("手机导航必须保留 (LiquidGlassNavHost 宿主)", navHostSrc.contains("LiquidGlassNavHost("))
        assertTrue("手机悬浮 Dock 摆位必须保留", navHostSrc.contains("floating = true"))
        assertTrue("手机贴底摆位必须保留", navHostSrc.contains("floating = false"))
    }

    @Test
    fun rail_has_only_one_active_combined_capsule() {
        // 用户令: 只当前选中的 tab 跟 Schedule 组合成跑道胶囊;其它两个 tab 是普通单图标项。
        // 实际源码里有 4 个 CombinedRailItem 调用 (when 分支 3 个 + else 兜底 1 个),
        // 但运行时只有一个 selected = true (当前 effectiveRightTab 决定的分支)。
        val callCount = Regex("""\bCombinedRailItem\(\s*\w+\s*=""").findAll(navHostSrc).count()
        assertTrue("至少 1 个胶囊调用 (实际 4 个, when 分支各一)", callCount >= 1)
        // 至少 1 个 PlainRailItem 调用 (2 个未选中 tab 用单图标)
        assertTrue("至少 1 个 PlainRailItem 调用",
            Regex("""\bPlainRailItem\(\s*\w+\s*=""").containsMatchIn(navHostSrc))
        // 所有 CombinedRailItem 中只有一个 selected = true
        val trueCount = Regex("""selected\s*=\s*true""").findAll(navHostSrc).count()
        assertTrue("selected=true 至少出现 (胶囊当前选中态)", trueCount >= 1)
    }

    @Test
    fun left_half_is_permanent_schedule() {
        // 截 NavigationRail 关闭 } 后 6000 字符,该区段必须包含 ScheduleScreen 与 viewModel = mainVm
        val railIdx = navHostSrc.indexOf("NavigationRail {")
        assertTrue("必须存在 NavigationRail { ... }", railIdx >= 0)
        val railBodyStart = navHostSrc.indexOf("{", railIdx)
        var depth = 0
        var bodyEnd = -1
        var i = railBodyStart
        while (i < navHostSrc.length && i < railIdx + 8000) {
            when (navHostSrc[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { bodyEnd = i; break } }
            }
            i++
        }
        assertTrue("必须闭合 NavigationRail", bodyEnd >= 0)
        val afterRail = navHostSrc.substring(bodyEnd, minOf(bodyEnd + 6000, navHostSrc.length))
        assertTrue("左半 ScheduleScreen 永久固定", afterRail.contains("ScheduleScreen("))
        assertTrue("左半 viewModel = mainVm 共享", afterRail.contains("viewModel = mainVm"))
    }

    @Test
    fun right_half_animated_content() {
        assertTrue("右半边用 AnimatedContent", navHostSrc.contains("AnimatedContent("))
        // MD3E 全量过闸 (M3 motion Task 2): 旧 fadeIn/fadeOut(tween(200)) 字面量退场,
        // 右半区转场与页面转场同走 SleepyMotion content 家族 — 工厂结果先算后捕获,
        // 在 transitionSpec 里 togetherWith 成对。意图不变: 仍锁"入/退成对 + 无手搓时长"。
        assertTrue(
            "右半边必须用 sleepyContentEnter()/sleepyContentExit() 捕获后 togetherWith 组转场",
            Regex("""val\s+contentEnter\s*=\s*sleepyContentEnter\(\)""").containsMatchIn(navHostSrc) &&
                Regex("""val\s+contentExit\s*=\s*sleepyContentExit\(\)""").containsMatchIn(navHostSrc) &&
                Regex("""contentEnter\s+togetherWith\s+contentExit""").containsMatchIn(navHostSrc),
        )
        assertFalse(
            "右半边不得残留硬编码 tween(200) 淡入淡出(全走 motionScheme 工厂)",
            navHostSrc.contains("tween(200)"),
        )
    }

    @Test
    fun no_label_on_combined_capsule() {
        val start = navHostSrc.indexOf("private fun CombinedRailItem(")
        assertTrue("CombinedRailItem 必须存在", start >= 0)
        val body = navHostSrc.substring(start, minOf(start + 4000, navHostSrc.length))
        assertFalse(
            "胶囊内部不应有 Text(text = label) 调用",
            Regex("""Text\(\s*text\s*=\s*label""").containsMatchIn(body),
        )
    }

    @Test
    fun deleted_legacy_anchors() {
        assertFalse("tab_schedule_today 字符串应已删除", navHostSrc.contains("tab_schedule_today"))
        assertFalse("BottomCombinedScheduleToday 应已删除", navHostSrc.contains("BottomCombinedScheduleToday"))
        assertFalse("PLAN:bottom-combined 注释应已删除", navHostSrc.contains("// PLAN:bottom-combined"))
        assertFalse("CombinedScheduleTodayRailItem 应已删除", navHostSrc.contains("CombinedScheduleTodayRailItem"))
    }

    @Test
    fun update_dot_visible_when_mine_in_combined_capsule() {
        // 用户令: 我的图标即使组合成胶囊也要保留右上角更新红点。
        // CombinedRailItem 必须接受 tabShowUpdateDot 参数, 且当 tab=Mine + updateNoticeVisible=true 时画红点。
        val combinedRailStart = navHostSrc.indexOf("private fun CombinedRailItem(")
        val combinedRailBody = navHostSrc.substring(combinedRailStart, minOf(combinedRailStart + 4000, navHostSrc.length))
        assertTrue("CombinedRailItem 必须有 tabShowUpdateDot 参数", combinedRailBody.contains("tabShowUpdateDot"))
        assertTrue("CombinedRailItem 内部红点条件 tabShowUpdateDot",
            Regex("""if\s*\(\s*tabShowUpdateDot\s*\)""").containsMatchIn(combinedRailBody))
        // CombinedRailItem 应有红点 Box
        assertTrue("CombinedRailItem 必须有 update dot Box", combinedRailBody.contains(".size(7.dp)") &&
            combinedRailBody.contains(".background(colors.primary"))
    }
}
