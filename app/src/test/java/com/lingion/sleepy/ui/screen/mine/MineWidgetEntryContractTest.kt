package com.lingion.sleepy.ui.screen.mine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「小组件板块独立」契约（docs/widget-add-gallery-design.md §4 不变量①）。
 *
 * 顺序锁(铁律③): 「我的」页设置项列表中「小组件」必须紧跟「通用」之后 ——
 * 断言的是**相邻顺序本身**,不是两项的存在性。后续任何人插新项把这对拆散即红。
 *
 * 板块平移锁: 通用页内小组件板块(3 开关 + 管理条目)必须整体消失,
 * 且原样出现在新独立页 WidgetSettingsScreen 中(偏好键零迁移)。
 *
 * 仓库无 Robolectric — 与其他契约测试同风格,源文件 token 扫描。
 */
class MineWidgetEntryContractTest {

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
    private val generalScreen: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/mine/GeneralSettingsScreen.kt").readText()
    }
    private val routes: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/nav/SleepyRoutes.kt").readText()
    }
    private val navigator: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/nav/SleepyNavigator.kt").readText()
    }
    private val navHost: String by lazy {
        findUpward("app/src/main/java/com/lingion/sleepy/ui/nav/SleepyNavHost.kt").readText()
    }

    // ---- A. 顺序锁: 「通用」项闭合 → Divider → 「小组件」项开启,中间零他项 ----

    @Test
    fun `widget entry is immediately adjacent after general entry in MineScreen`() {
        val g = mineScreen.indexOf("R.string.mine_general")
        val w = mineScreen.indexOf("R.string.mine_widget")
        assertTrue("MineScreen 必须同时引用 mine_general 与 mine_widget", g >= 0 && w >= 0)
        assertTrue("「小组件」必须排在「通用」之后", g < w)
        // 归一化空白后,二者的间隔必须恰好是: 通用项收尾「, onClick = onOpenGeneral)」+「Divider()」+ 小组件项开头发。
        // 任何新项插在中间(拆散相邻对)都会让这段字符串变长 → 红。
        val between = mineScreen.substring(g + "R.string.mine_general".length, w)
            .replace(Regex("\\s+"), " ")
        val expected = "), onClick = onOpenGeneral) Divider() SettingsItem(icon = Icons.Outlined.Widgets, label = stringResource("
        assertTrue(
            "「通用」与「小组件」必须相邻(中间仅一个 Divider)。实际间隔: 「$between」",
            between == expected
        )
    }

    @Test
    fun `mine screen declares onOpenWidgetSettings parameter`() {
        assertTrue(
            "MineScreen 形参必须有 onOpenWidgetSettings: () -> Unit = {}",
            Regex("""onOpenWidgetSettings:\s*\(\)\s*->\s*Unit\s*=\s*\{\}""").containsMatchIn(mineScreen)
        )
        assertTrue(
            "小组件 SettingsItem 必须路由到 onOpenWidgetSettings",
            Regex("""onClick\s*=\s*onOpenWidgetSettings""").containsMatchIn(mineScreen)
        )
    }

    // ---- B. 板块平移: 通用页零小组件板块,新页承载全部 ----

    @Test
    fun `general screen no longer hosts widget section`() {
        listOf(
            "settings_widget_colorless", "settings_widget_separator", "settings_nearest_busy_day",
            "widget_manage_entry", "onOpenWidgetManagement"
        ).forEach { token ->
            assertTrue(
                "通用页必须已删除小组件板块,不应再含 $token",
                !generalScreen.contains(token)
            )
        }
    }

    @Test
    fun `widget settings screen hosts the migrated section`() {
        val file = findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/mine/WidgetSettingsScreen.kt")
        val src = file.readText()
        // 三个开关 + 管理条目原样平移(偏好键零迁移)
        listOf(
            "settings_widget_colorless", "settings_widget_separator", "settings_nearest_busy_day",
            "widget_manage_entry", "onOpenWidgetManagement",
            "AppPrefs.setWidgetColorless", "AppPrefs.setWidgetSeparator", "AppPrefs.setNearestBusyDay"
        ).forEach { token ->
            assertTrue("WidgetSettingsScreen 必须含 $token", src.contains(token))
        }
    }

    // ---- C. 路由/导航接线齐全 ----

    @Test
    fun `routes navigator and navhost wire WidgetSettings entry`() {
        assertTrue(routes.contains("data object WidgetSettings : SleepyRoute"))
        assertTrue(navigator.contains("fun openWidgetSettings() = push(SleepyRoute.WidgetSettings)"))
        assertTrue(navHost.contains("entry<SleepyRoute.WidgetSettings>"))
        assertTrue(navHost.contains("onOpenWidgetSettings = { navigator.openWidgetSettings() }"))
    }
}
