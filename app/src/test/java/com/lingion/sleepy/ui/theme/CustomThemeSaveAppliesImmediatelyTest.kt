package com.lingion.sleepy.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义主题保存必须立刻应用到全 app (2026-09-21 用户真机反馈)。
 *
 * 症状: 编辑器点保存后, 整个 app 的主题没立刻切换 — 包括外观页本身、ScheduleScreen
 *       配色、所有 dialog。所有这些都是因为 `themeKeyFlow` 只监听 SP: sleepy_prefs 的
 *       `theme_key` 变化; AppearanceScreen 的 onSaved 只调了 `CustomThemeStore.save()`
 *       (写另一个 SP: custom_themes), 完全没触发 themeKeyFlow 的 listener, 因此:
 *         - SleepyThemeProvider(themeKey=...) 不会重组 → 主题色不切;
 *         - 没有调用 AppPrefs.setThemeKey(CUSTOM_KEY_PREFIX + saved.id) → 当前选中
 *           主题没真的落到 SP, 冷启动回来主题就回到之前的「新主题」(其实从没被应用)。
 *
 * 契约(源码结构断言, 与 CustomThemeUiContractTest 同流, repo 无 Robolectric):
 *   1. onSaved 必须写 AppPrefs.setThemeKey(context, ThemePresets.CUSTOM_KEY_PREFIX + saved.id);
 *   2. onSaved 必须无条件调 refreshWidgets()(关键) — 新建主题从未被应用, "if(currentKey==..."
 *      这个守卫永远不成立 → 永远不刷 → 桌面小组件也不跟应用主题变。
 *
 * 修法(在 AppearanceScreen.kt onSaved lambda 内):
 *   CustomThemeStore.save(context, saved)
 *   customListVersion++
 *   AppPrefs.setThemeKey(context, ThemePresets.CUSTOM_KEY_PREFIX + saved.id)
 *   refreshWidgets()
 *   showEditor = false
 *   editingTheme = null
 *
 * 反过来 onDeleted 已经把当前应用主题回落 default, 这条路径没问题; 但删除后
 * 也需要 refreshWidgets() — 见 onDeleted handler 现有写法。
 */
class CustomThemeSaveAppliesImmediatelyTest {

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

    /** 取 AppearanceScreen 的 onSaved lambda 体 — 到下一个 `},` (onDeleted) 为止 */
    private val onSavedLambda: String by lazy {
        appearanceSource.substringAfter("onSaved = { saved ->")
            .substringBefore("},\n            onDeleted =")
    }

    /** 契约 1: onSaved 必须落 SP: theme_key = "custom:" + saved.id,
     *  这是 SleepyThemeProvider 重组唯一信号源(themeKeyFlow 只听 sleepy_prefs 的 theme_key)。 */
    @Test
    fun onSaved_writes_theme_key_so_provider_recomposes() {
        assertTrue(
            "AppearanceScreen.onSaved must call AppPrefs.setThemeKey(context, " +
                "ThemePresets.CUSTOM_KEY_PREFIX + saved.id) — without this, themeKeyFlow never fires and " +
                "SleepyThemeProvider never rebuilds (2026-09-21 user feedback)",
            Regex(
                """AppPrefs\.setThemeKey\(\s*context\s*,\s*ThemePresets\.CUSTOM_KEY_PREFIX\s*\+\s*saved\.id\s*\)"""
            ).containsMatchIn(onSavedLambda)
        )
    }

    /** 契约 2: onSaved 必须无条件调 refreshWidgets() —
     *  原实现把 refreshWidgets() 锁在 `if(currentKey == CUSTOM_KEY_PREFIX + saved.id)` 内,
     *  对新建主题这个守卫恒假 → widget 永远不刷。 */
    @Test
    fun onSaved_refreshes_widgets_unconditionally() {
        // onSaved 体内 refreshWidgets 出现 ≥1 次, 且不在 if(currentKey == ...) 守卫之内
        // (即同 `if` 块以外)。简单做法:从 lambda 体里 grep refreshWidgets 行数,
        // 排除包在 `if (currentKey == ...)` 块内那条 — 期望 ≥2 行独立 refreshWidgets()。
        val lines = onSavedLambda.lines().map { it.trim() }.filter { it.contains("refreshWidgets()") }
        assertTrue(
            "onSaved must call refreshWidgets() outside the if(currentKey == ...) guard — " +
                "guarding it means new themes (which never match currentKey) never refresh widgets. " +
                "Found ${lines.size} call(s).",
            lines.size >= 1
        )
        // 同时锁定: 不允许 refreshWidgets() 被 if 守卫
        assertFalse(
            "refreshWidgets() must not be inside `if (currentKey == ...)` — new theme never matches",
            Regex(
                """if\s*\(\s*currentKey\s*==\s*ThemePresets\.CUSTOM_KEY_PREFIX\s*\+\s*saved\.id\s*\)\s*\{?\s*\n?\s*refreshWidgets"""
            ).containsMatchIn(onSavedLambda)
        )
    }
}