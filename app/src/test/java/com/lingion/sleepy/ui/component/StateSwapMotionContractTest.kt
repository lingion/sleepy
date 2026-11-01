package com.lingion.sleepy.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 选中态色彩/徽标切换统一契约 (MD3E motion 全量补齐 Task 7b, 2026-10-29):
 *
 *   1. DisplayModeOption (SettingsCards.kt): 选中文字色与徽标 Check 一律
 *      animateColorAsState + motionScheme.fastEffectsSpec (色彩是视觉属性,
 *      Expressive 家族对色变走 effects 档时长) — 禁 if(selected) 瞬时硬切。
 *   2. Check 徽标显隐 = AnimatedVisibility(sleepyPopupEnter/Exit) popup mini
 *      家族 (T1 工厂) — 与 T6 徽标/幽灵同族, 存在性终态由管线测试锁。
 *   3. CombinedRailItem (SleepyNavHost.kt): 容器色与图标色两处 selected 三元
 *      换 animateColorAsState fastEffects — 跑道胶囊选中的进/出色缓动。
 *
 * 载体形态: 7b 全部是纯 UI 状态色站点, 按 T3-T7a 定型先例源码结构锁定;
 * DisplayModeOption 徽标显隐另有 Robolectric 管线测试
 * DisplayModeOptionCheckPipelineTest (与 T7a 管线测试同构)。
 */
class StateSwapMotionContractTest {

    private fun loadSource(relPath: String): String =
        sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/"),
            File("src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            File(root, relPath).takeIf { it.isFile }?.readText()
        } ?: error("Unable to load $relPath")

    private val settingsCards: String by lazy { loadSource("ui/component/SettingsCards.kt") }
    private val navHost: String by lazy { loadSource("ui/nav/SleepyNavHost.kt") }

    // ---- 契约 1: DisplayModeOption 色彩切换走 animateColorAsState ----

    @Test
    fun display_mode_option_colors_animate_with_fast_effects() {
        val body = settingsCards.substringAfter("fun DisplayModeOption(")
            .substringBefore("@Composable")
        assertTrue(
            "DisplayModeOption 必须 animateColorAsState 选中色 (fastEffects 档)",
            Regex("""animateColorAsState\([\s\S]{0,200}?fastEffectsSpec""").containsMatchIn(body)
        )
        assertFalse(
            "选中文字色禁 if(selected) 瞬时硬切残留",
            Regex("""color = if \(selected\)""").containsMatchIn(body)
        )
    }

    // ---- 契约 2: Check 徽标显隐走 popup 家族 AnimatedVisibility ----

    @Test
    fun display_mode_option_check_mounts_via_popup_family() {
        val body = settingsCards.substringAfter("fun DisplayModeOption(")
            .substringBefore("@Composable")
        assertTrue(
            "Check 徽标必须 AnimatedVisibility + sleepyPopupEnter (popup mini 家族)",
            Regex("""AnimatedVisibility\([\s\S]{0,160}?sleepyPopupEnter\(\)""").containsMatchIn(body)
        )
        assertTrue(
            "退场成对 sleepyPopupExit",
            Regex("""exit = sleepyPopupExit\(\)""").containsMatchIn(body)
        )
        assertFalse(
            "徽标禁 if(selected) Icon 直挂回潮 (砍掉弹入)",
            Regex("""if \(selected\) Icon\(""").containsMatchIn(body)
        )
    }

    // ---- 契约 3: CombinedRailItem 两处 selected 色彩三元转正 ----

    @Test
    fun combined_rail_item_colors_animate_with_fast_effects() {
        val body = navHost.substringAfter("private fun CombinedRailItem(")
            .substringBefore("\n@Composable", navHost)
        // 容器色 + 图标色两处都吃 animateColorAsState fastEffects
        val swaps = Regex("""animateColorAsState\([\s\S]{0,200}?fastEffectsSpec""").findAll(body).count()
        assertTrue("CombinedRailItem 容器色/图标色两处都须 animateColorAsState (实得 $swaps)", swaps >= 2)
        assertFalse(
            "禁残留 val xxx = if (selected) colors.primaryContainer 硬切 (targetValue 内三元是合法消费)",
            Regex("""val \w+ = if \(selected\) colors\.primaryContainer""").containsMatchIn(body)
        )
        assertFalse(
            "禁残留 val xxx = if (selected) colors.onPrimaryContainer 硬切",
            Regex("""val \w+ = if \(selected\) colors\.onPrimaryContainer""").containsMatchIn(body)
        )
    }

    // ---- 反向护栏: 7b 不得动 7a/既有锁定的结构锚 ----

    @Test
    fun rail_structure_anchors_survive() {
        // TabletMasterDetailContractTest 锁定的锚: 红点条件与尺寸不许被顺手改掉
        val body = navHost.substringAfter("private fun CombinedRailItem(")
            .substringBefore("\n@Composable", navHost)
        assertTrue("红点条件 if (tabShowUpdateDot) 必须原样", Regex("""if\s*\(\s*tabShowUpdateDot\s*\)""").containsMatchIn(body))
        assertTrue("红点 .size(7.dp) 必须原样", body.contains(".size(7.dp)"))
    }
}
