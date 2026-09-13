package com.lingion.sleepy.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义主题卡视觉一致性契约 — 2026-09-13 用户真机反馈两点定稿:
 *
 * 症状 1: 自定义主题卡与原生 5 张预设卡高度不一样、间隙也不一样。
 * 根因: CustomThemeCard 名称行内嵌 IconButton(size 24dp) — Material3 IconButton
 * 是最小点击目标组件,24dp 容器把名称行撑得比 Preset 卡(20dp 对勾/文字行)高。
 * 契约: 自定义卡必须与任何预设卡大小一模一样 — 名称行禁再嵌 24dp+ 容器,
 * 高度差来源(IconButton)必须从名称行退场。
 *
 * 症状 2: 编辑图标贴在主题名旁边,用户不知道点哪、也不与第一行对齐。
 * 根因: edit 图标挂在第二行(名称行)。
 * 用户定稿: 编辑图标 ①要有色块包裹(否则不知道点哪) ②与第一行 3 个色块
 * 同行水平对齐,不在主题名旁边。
 *
 * 锁法(源码结构断言,与 CustomThemeUiContractTest 同流):
 *   1. CustomThemeCard 名称行(第二行)禁含 IconButton;
 *   2. edit IconButton 必须在色板行(第一行)内,经 Spacer(weight) 推到右缘;
 *   3. edit 图标必须有背景色块包裹(background 修饰符),禁裸图标。
 */
class CustomThemeCardVisualParityTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/"),
            java.io.File("/Users/lingion_k/sleepy/app/src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val appearanceSource: String by lazy {
        loadSource("ui/screen/mine/AppearanceScreen.kt")
    }

    /** 取 CustomThemeCard 函数体(到下一个 private fun 为止) */
    private val customCardBody: String by lazy {
        appearanceSource.substringAfter("private fun CustomThemeCard(")
            .substringBefore("/** 虚线圆圈")
    }

    /** 契约 1: 名称行禁再嵌 IconButton — 高度差来源退场,自定义卡与预设卡同高 */
    @Test
    fun custom_card_name_row_has_no_icon_button() {
        // 名称行 = spacer(12dp) 之后的 Row;IconButton 只允许出现在色板行
        val nameRow = customCardBody.substringAfter("Spacer(Modifier.height(12.dp))")
        assertFalse(
            "CustomThemeCard name row must not embed an IconButton — the 24dp container is the " +
                "height mismatch vs PresetThemeCard (2026-09-13 user feedback)",
            nameRow.contains("IconButton")
        )
    }

    /** 契约 2: edit 入口必须在色板行内,经 Spacer(weight) 推到右缘 — 与 3 色块同行。
     *  实现形态 = Box 色块 + noRippleClickable(全 app 可点色块统一模式, 同 WeekNavButton) */
    @Test
    fun edit_icon_lives_in_swatch_row_aligned_right() {
        // 色板行 = 第一个 Row(含三枚 ColorSwatch);edit 色块必须在其闭合之前,
        // 且前面有 Spacer(weight) 推右缘
        val swatchRow = customCardBody.substringAfter("Row(horizontalArrangement = Arrangement.spacedBy(8.dp))")
            .substringBefore("Spacer(Modifier.height(12.dp))")
        assertTrue(
            "CustomThemeCard edit entry must live inside the swatch row (first row, aligned with the 3 color blocks)",
            swatchRow.contains("Icons.Outlined.Edit")
        )
        assertTrue(
            "Edit entry must be pushed to the trailing edge by Spacer(weight(1f))",
            Regex("""Spacer\(Modifier\.weight\(1f\)\)""").containsMatchIn(swatchRow)
        )
    }

    /** 契约 3: edit 入口必须是背景色块包裹的可点 Box — 裸图标用户不知道可点 */
    @Test
    fun edit_icon_has_background_block_wrapper() {
        val swatchRow = customCardBody.substringAfter("Row(horizontalArrangement = Arrangement.spacedBy(8.dp))")
            .substringBefore("Spacer(Modifier.height(12.dp))")
        val entryBody = swatchRow.substringAfter("Box(")
            .substringBefore("Icons.Outlined.Edit")
        assertTrue(
            "Edit entry must be wrapped in a background color block (users must see where to tap)",
            Regex("""\.background\(""").containsMatchIn(entryBody)
        )
        // clip(圆角)同在 — 是"色块"不是矩形直角
        assertTrue(
            "Edit icon wrapper must clip to the theme shape (color block, not hard rectangle)",
            Regex("""\.clip\(""").containsMatchIn(entryBody)
        )
        // 可点性: 色块必须接 noRippleClickable(onEdit)
        assertTrue(
            "Edit color block must be clickable via noRippleClickable(onEdit)",
            swatchRow.contains("noRippleClickable(onEdit)")
        )
    }

    /** 契约 4: 色板/间距结构与 PresetThemeCard 完全同构 — 大小一模一样 */
    @Test
    fun custom_card_structure_matches_preset_card() {
        val presetBody = appearanceSource.substringAfter("private fun PresetThemeCard(")
            .substringBefore("private fun ColorSwatch")
        // 两者同用 16dp 内边距 + 8dp 色板间距 + 12dp 行间距
        for (needle in listOf("Modifier.padding(16.dp)", "Arrangement.spacedBy(8.dp)", "Spacer(Modifier.height(12.dp))")) {
            assertTrue(
                "PresetThemeCard must contain $needle (baseline sanity)",
                presetBody.contains(needle)
            )
            assertTrue(
                "CustomThemeCard must contain $needle (same as preset card — identical size)",
                customCardBody.contains(needle)
            )
        }
        // 名称行行高构成必须同构:除色板行外,行内最大子组件 = 20dp 对勾
        assertFalse(
            "CustomThemeCard must not contain any 24dp+ container outside the swatch row",
            customCardBody.substringAfter("Spacer(Modifier.height(12.dp))").contains("24.dp")
        )
    }
}
