package com.lingion.sleepy.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义主题接入契约测试 — 源码级结构锁定(仿 ScheduleViewModeSessionContractTest,
 * 先例:JwQzAppWebViewContractTest / WidgetInfoXmlContractTest — 仓库无 Robolectric/
 * Compose UI 测试,声明式接线读源头文件等价于读编译产物)。
 *
 * 契约(设计定稿,防回归):
 *   1. widget resolveSchemePublic 必须含 "custom:" 分支 — App 与小组件同源派生,
 *      widget 缺分支 = 应用内是自定义主题色、桌面小组件永远是默认紫(用户可见不一致);
 *   2. widget custom 分支必须调 CustomSchemeDeriver(禁复制粘贴算法);
 *   3. App 端 SleepyThemeProvider 同样必须接 custom 分支 + 同一派生函数;
 *   4. AppearanceScreen 必须含「新建主题」卡与自定义主题卡;
 *   5. 自定义主题已删时 KEY_THEME 回落 default(双端语义一致);
 *   6. 删除按钮用色块(errorContainer),禁 BorderStroke(全 app 铁律)。
 */
class CustomThemeUiContractTest {

    private fun loadSource(vararg relPaths: String): String =
        sequenceOf(
            java.io.File("app/src/main/java/com/lingion/sleepy/"),
            java.io.File("src/main/java/com/lingion/sleepy/")
        ).firstOrNull { it.isDirectory }?.let { root ->
            relPaths.map { java.io.File(root, it) }.firstOrNull { it.isFile }?.readText()
        } ?: error("Unable to load sources: ${relPaths.joinToString()}")

    private val widgetSource: String by lazy { loadSource("widget/WidgetContent.kt") }
    private val themeSource: String by lazy { loadSource("ui/theme/Theme.kt") }
    private val appearanceSource: String by lazy { loadSource("ui/screen/mine/AppearanceScreen.kt") }
    private val editorSource: String by lazy { loadSource("ui/screen/mine/CustomThemeEditorScreen.kt") }

    /** 契约 1: resolveSchemePublic 必须含 custom 分支 */
    @Test
    fun resolveSchemePublic_has_custom_branch() {
        assertTrue(
            "resolveSchemePublic must branch on ThemePresets.CUSTOM_KEY_PREFIX (custom:<id> keys)",
            widgetSource.contains("ThemePresets.CUSTOM_KEY_PREFIX")
        )
    }

    /** 契约 2: widget custom 分支必须走同一派生函数,禁复制算法 */
    @Test
    fun widget_custom_branch_uses_shared_deriver() {
        assertTrue(
            "widget custom branch must call CustomSchemeDeriver.derive — duplicating the algorithm is banned",
            widgetSource.contains("CustomSchemeDeriver.derive")
        )
    }

    /** 契约 3: App 端 provider 接 custom 分支且与 widget 同函数 */
    @Test
    fun theme_provider_has_custom_branch_with_same_deriver() {
        assertTrue(
            "SleepyThemeProvider must branch on custom theme keys",
            themeSource.contains("ThemePresets.CUSTOM_KEY_PREFIX")
        )
        assertTrue(
            "SleepyThemeProvider must derive via CustomSchemeDeriver.derive (same function as widget)",
            themeSource.contains("CustomSchemeDeriver.derive")
        )
    }

    /** 契约 4: AppearanceScreen 必含新建卡与自定义卡 */
    @Test
    fun appearance_screen_has_new_theme_card_and_custom_theme_card() {
        assertTrue(
            "AppearanceScreen must render the 'new theme' card",
            appearanceSource.contains("NewThemeCard(")
        )
        assertTrue(
            "AppearanceScreen must render custom theme cards",
            appearanceSource.contains("CustomThemeCard(")
        )
        // 新建卡视觉契约:虚线圆圈(dashPathEffect)
        assertTrue(
            "NewThemeCard must draw a dashed circle (PathEffect.dashPathEffect)",
            appearanceSource.contains("dashPathEffect")
        )
    }

    /** 契约 4c: 单元序列必须 预设→自定义→加号 — 加号永远排在整个网格末尾,
     *  禁插在预设与自定义卡之间(2026-09-11 用户定稿:自定义卡与预设紧密堆积) */
    @Test
    fun theme_grid_orders_plus_sign_last() {
        val presetAdd = Regex("""ThemePresets\.all\.forEach \{ add\(ThemeGridCell\.Preset\(it\)\) \}\s*\n\s*customThemes\.forEach \{ add\(ThemeGridCell\.Custom\(it\)\) \}\s*\n\s*add\(ThemeGridCell\.NewTheme\)""")
        assertTrue(
            "Theme grid cells must be presets -> customs -> NewTheme (plus sign always last)",
            presetAdd.containsMatchIn(appearanceSource)
        )
    }

    /** 契约 4b: 自定义卡选中判定用 custom:<id> 完整键,禁裸 id 误命中预设 */
    @Test
    fun custom_card_selection_uses_prefixed_key() {
        assertTrue(
            "CustomThemeCard selection must compare against CUSTOM_KEY_PREFIX + id",
            Regex("""CUSTOM_KEY_PREFIX\s*\+\s*cell\.theme\.id""").containsMatchIn(appearanceSource)
        )
    }

    /** 契约 5: 删除应用中的自定义主题 → KEY_THEME 写回 default */
    @Test
    fun deleting_active_custom_theme_falls_back_to_default() {
        val deletedWritesDefault = Regex(
            """onDeleted[\s\S]*?setThemeKey[\s\S]*?KEY_DEFAULT"""
        ).containsMatchIn(appearanceSource)
        assertTrue(
            "Deleting the active custom theme must write KEY_DEFAULT back (same semantics as unknown-key fallback)",
            deletedWritesDefault
        )
    }

    /** 契约 6: 删除按钮 = errorContainer 色块,禁 BorderStroke */
    @Test
    fun editor_delete_button_is_color_block_no_border() {
        assertTrue(
            "Editor delete button must use errorContainer color block",
            editorSource.contains("errorContainer")
        )
        assertFalseIn(
            "BorderStroke is banned app-wide (solid color blocks only)",
            editorSource, "BorderStroke"
        )
    }

    /** 编辑器必须复用公共取色器(禁再私有化一份) */
    @Test
    fun editor_reuses_public_color_picker() {
        assertTrue(
            "Editor must import the shared ColorPickerDialog from ui/component",
            editorSource.contains("com.lingion.sleepy.ui.component.ColorPickerDialog")
        )
    }

    private fun assertFalseIn(message: String, source: String, needle: String) {
        org.junit.Assert.assertFalse(message, source.contains(needle))
    }
}
