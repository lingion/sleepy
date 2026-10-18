package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「刷新全部小组件按钮搬家」契约(2026-10-18 用户定版)。
 *
 * ① 按钮退出「我的」页底部 —— MineScreen 零刷新 token;
 * ② 落位小组件设置页最底部(「添加或管理桌面小组件」条目之下,顺序锁);
 * ③ 条件显示: 仅当桌面已有已放置实例(items 非空)才出现,数据源 =
 *    WidgetManagementViewModel.state(与「添加或管理」列表单一真源);
 * ④ 页尾留底部悬浮栏余量(LocalNavExtraBottomPadding,同「我的」页约定)。
 *
 * 仓库无 Robolectric — 与其他契约测试同风格,源文件 token 扫描。
 */
class WidgetRefreshRelocationContractTest {

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private val mineScreen: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/mine/MineScreen.kt").readText()
    }
    private val widgetSettings: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/mine/WidgetSettingsScreen.kt").readText()
    }

    // ---- ① 我的页零刷新残留 ----

    @Test
    fun `mine screen no longer hosts refresh widgets button`() {
        listOf("FilledTonalButton", "mine_refresh_widgets", "Icons.Outlined.Refresh", "notifyDataChanged")
            .forEach { token ->
                assertFalse("MineScreen 必须已移除刷新按钮,不应再含 $token", mineScreen.contains(token))
            }
    }

    // ---- ② 设置页承载 + 条件显示 ----

    @Test
    fun `widget settings screen hosts conditional refresh button`() {
        listOf(
            "FilledTonalButton", "mine_refresh_widgets", "Icons.Outlined.Refresh",
            "WidgetUpdater.notifyDataChanged",
            "WidgetManagementViewModel", "vm.state.collectAsState()"
        ).forEach { token ->
            assertTrue("WidgetSettingsScreen 必须含 $token", widgetSettings.contains(token))
        }
        // 条件闸: 已放置实例非空才渲染(空桌面 = 按钮不出现)
        assertTrue(
            "按钮必须包在 items.isNotEmpty() 条件里",
            Regex("""if\s*\(\s*items\.isNotEmpty\s*\(\s*\)\s*\)\s*\{""").containsMatchIn(widgetSettings)
        )
        // 桌面增删后回页要重扫,否则按钮态滞留
        assertTrue(
            "页面需 ON_RESUME 重扫已放置实例",
            widgetSettings.contains("LifecycleEventObserver") && widgetSettings.contains("ON_RESUME")
        )
    }

    // ---- ③ 顺序锁: 按钮永远紧跟「添加或管理桌面小组件」条目之后 ----

    @Test
    fun `refresh button sits below manage entry with nothing in between`() {
        val e = widgetSettings.indexOf("stringResource(R.string.widget_manage_entry)")
        // 匹配带括号的调用点,排除 import 行(首个出现必须是布局代码里的按钮)
        val b = widgetSettings.indexOf("FilledTonalButton(")
        assertTrue("管理条目与刷新按钮必须都在场", e >= 0 && b >= 0 && e < b)
        // 条目与按钮之间禁止插入其他内容块(SectionHeader/SettingsCard/开关行)
        val between = widgetSettings.substring(e, b)
        listOf("SectionHeader(", "SettingsCard(", "SettingToggleRow(").forEach { token ->
            assertFalse("「添加或管理」与刷新按钮之间不得出现 $token(按钮必须紧随条目)", between.contains(token))
        }
    }

    // ---- ④ 页尾余量对齐底部悬浮栏 ----

    @Test
    fun `widget settings bottom padding respects nav dock overlay`() {
        assertTrue(
            "LazyColumn 底部留量必须含 LocalNavExtraBottomPadding(Dock 悬浮不压按钮)",
            widgetSettings.contains("LocalNavExtraBottomPadding")
        )
    }
}
