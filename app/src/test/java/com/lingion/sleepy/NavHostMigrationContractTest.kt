package com.lingion.sleepy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 锁定 Navigation Compose 迁移必须保住的不变量。
 *
 * 与 BackRestoreSaveableContractTest 互补:
 * - 那个锁 Saveable 形态/栈结构
 * - 这个锁导航语义与系统集成(弃表/返回/手势/清单/枚举清退)
 *
 * 测试面全部用字符串扫描 + 平衡块提取,不实例化 NavHost(那要走 Robolectric,
 * 单测里没意义——契约要锁的是源码契约,不是运行时行为)。
 */
class NavHostMigrationContractTest {

    private val navHostSrc by lazy {
        File("src/main/java/com/lingion/sleepy/ui/nav/SleepyNavHost.kt").readText()
    }
    private val navigatorSrc by lazy {
        File("src/main/java/com/lingion/sleepy/ui/nav/SleepyNavigator.kt").readText()
    }
    private val routesSrc by lazy {
        File("src/main/java/com/lingion/sleepy/ui/nav/SleepyRoutes.kt").readText()
    }
    private val mainSrc by lazy {
        File("src/main/java/com/lingion/sleepy/MainActivity.kt").readText()
    }
    private val manifestSrc by lazy {
        File("src/main/AndroidManifest.xml").readText()
    }

    private fun balancedBlock(src: String, anchor: String, maxChars: Int = 2000): String {
        val start = src.indexOf(anchor)
        assertTrue("找不到锚点 $anchor", start >= 0)
        val open = src.indexOf('{', start)
        assertTrue("锚点后无开括号: $anchor", open >= 0)
        var depth = 0
        var i = open
        while (i < src.length && i - start < maxChars) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(start, i + 1)
                }
            }
            i++
        }
        error("未闭合块: $anchor")
    }

    // ─── EditTable 弃表语义 ───

    @Test
    fun editTable_route_backHandler_drops_pending_table_via_discardNewTable() {
        val block = balancedBlock(navHostSrc, "composable(Routes.EDIT_TABLE")
        // 兜底系统返回:enabled = pending != null(本地状态,不是路由参数)
        assertTrue(
            "EDIT_TABLE 必须有 BackHandler(enabled = pending != null)",
            Regex("""BackHandler\(\s*enabled\s*=\s*pending\s*!=\s*null\s*\)""").containsMatchIn(block)
        )
        assertTrue(
            "EDIT_TABLE 弃表回调必须调 mainVm.discardNewTable",
            block.contains("mainVm.discardNewTable")
        )
        assertTrue(
            "EDIT_TABLE 弃表后清 pending = null(防止重复触发)",
            block.contains("pending = null")
        )
    }

    @Test
    fun editTable_pending_is_local_rememberSaveable_not_route_arg() {
        val block = balancedBlock(navHostSrc, "composable(Routes.EDIT_TABLE")
        // pending 必须用本地 rememberSaveable,而非直接拿路由参数
        assertTrue(
            "pending 必须本地化: rememberSaveable + mutableStateOf<Long?>(routePendingNew)",
            Regex(
                """var\s+pending\s+by\s+rememberSaveable\s*\{\s*mutableStateOf<Long\?>\s*\(\s*routePendingNew\s*\)"""
            ).containsMatchIn(block)
        )
        // 兜底注释必须解释为什么不读路由参数(防历史 bug 复发)
        assertTrue(
            "EDIT_TABLE 必须有『读路由参数会触发历史 bug』类注释",
            block.contains("路由参数") && block.contains("bug")
        )
    }

    // ─── PeriodTableEdit 弃表语义 ───

    @Test
    fun periodTableEdit_route_backHandler_drops_pending_period_via_discardNewPeriodTable() {
        val block = balancedBlock(navHostSrc, "composable(Routes.PERIOD_EDIT")
        assertTrue(
            "PERIOD_EDIT 必须有 BackHandler(enabled = unsavedNew)",
            Regex("""BackHandler\(\s*enabled\s*=\s*unsavedNew\s*\)""").containsMatchIn(block)
        )
        assertTrue(
            "PERIOD_EDIT 弃表回调必须调 mainVm.discardNewPeriodTable",
            block.contains("mainVm.discardNewPeriodTable")
        )
        assertTrue(
            "PERIOD_EDIT 弃表后清 unsavedNew = false",
            block.contains("unsavedNew = false")
        )
    }

    // ─── 弃表兜底优先级:屏内 BackHandler > 导航回调 ───

    @Test
    fun editTable_local_backHandler_registers_after_onBack_navigation_chain() {
        // 导航回调链(主屏 onBack + 各 Tab onBack)先注册,per-route BackHandler 后注册;
        // BackHandler "最后注册者优先" 语义正是需要的:用户在 EditTable 时,本地兜底应赢。
        // 锁契约:EDIT_TABLE 块的 BackHandler 出现位置晚于 `onBack=` / `popBackStack(`。
        val block = balancedBlock(navHostSrc, "composable(Routes.EDIT_TABLE")
        val onBackIdx = block.indexOf("onBack")
        val backHandlerIdx = block.indexOf("BackHandler(enabled = pending != null)")
        assertTrue("EDIT_TABLE 块内应出现 onBack 链", onBackIdx > 0)
        assertTrue("EDIT_TABLE 块内应出现本地 BackHandler", backHandlerIdx > 0)
        assertTrue(
            "本地 BackHandler 必须注册在 onBack 链之后(BackHandler 后注册者优先)",
            backHandlerIdx > onBackIdx
        )
    }

    // ─── Navigator 语义 ───

    @Test
    fun navigator_push_does_not_use_popUpTo_so_layers_stack() {
        // General→Holiday 必须 push(不替换),否则 HolidayScreen 入参拿不到 General 表 id。
        assertTrue(
            "SleepyNavigator.push 不得含 popUpTo",
            !Regex("""fun\s+push\([^)]*\)\s*\{[\s\S]{0,400}?popUpTo""").containsMatchIn(navigatorSrc)
        )
        assertTrue("Navigator 必须定义 push 函数", navigatorSrc.contains("fun push("))
    }

    @Test
    fun navigator_popToMain_clears_back_stack_to_main() {
        // popToMain 才是唯一允许清栈回主屏的地方:任意栈→回 main
        assertTrue(
            "popToMain 必须 popBackStack(Routes.MAIN, inclusive = false) 把栈清回主屏",
            Regex(
                """fun\s+popToMain[\s\S]{0,400}?popBackStack\(\s*Routes\.MAIN\s*,\s*inclusive\s*=\s*false\s*\)"""
            ).containsMatchIn(navigatorSrc)
        )
    }

    @Test
    fun navigator_open_methods_only_use_routes_constants_no_string_literals() {
        // 14 个 openXxx 入口全部走 Routes 常量,不允许裸字符串。
        // 多数是表达式体 `fun openX() = push(Routes.X)`,少数是块体。
        val openFns = Regex("""fun\s+(open\w+)\(""").findAll(navigatorSrc).map { it.groupValues[1] }.toList()
        assertEquals("Navigator 必须有 14 个 openXxx 入口(与原 OverlayScreen 一一对应)", 14, openFns.size)
        openFns.forEach { fn ->
            // 多行签名(如 openEditTable 三参数)必须取到函数体尾部,不止签名首行。
            // 取从 `fun fn(` 开始到下一个 `fun ` 或 400 字符,以先到者为准。
            val fnStart = navigatorSrc.indexOf("fun $fn(")
            val nextFn = navigatorSrc.indexOf("\n    fun ", fnStart + 1)
            val windowEnd = if (nextFn > 0) nextFn else (fnStart + 400).coerceAtMost(navigatorSrc.length)
            val window = navigatorSrc.substring(fnStart, windowEnd)
            assertTrue(
                "$fn 必须引用 Routes.X 常量(不许裸字符串路由名), 实际片段: ${window.take(120)}",
                Regex("""Routes\.\w+""").containsMatchIn(window)
            )
        }
    }

    // ─── 路由参数约定 ───

    @Test
    fun no_id_sentinel_is_NO_ID_constant_only_no_adhoc_minus_one() {
        // -1L 只许作为 NO_ID 出现,不允许在 nav/ 内裸写 -1L 当哨兵
        val navPkgSrc = (navHostSrc + "\n" + navigatorSrc + "\n" + routesSrc)
            .lines()
        val adhocLines = navPkgSrc.filterIndexed { _, line ->
            line.contains("-1L") && !line.contains("NO_ID")
        }
        assertEquals(
            "ui/nav/ 包内 -1L 必须只能出现在 NO_ID 常量定义行,禁止裸用做哨兵(违规行 = $adhocLines)",
            0,
            adhocLines.size
        )
    }

    // ─── Manifest 集成 ───

    @Test
    fun manifest_enables_predictive_back_app_wide() {
        assertTrue(
            "AndroidManifest <application> 必须含 enableOnBackInvokedCallback=\"true\"(预测性返回)",
            Regex(
                """<application[\s\S]{0,500}?android:enableOnBackInvokedCallback\s*=\s*[\"']true[\"']"""
            ).containsMatchIn(manifestSrc)
        )
    }

    // ─── 旧栈清退 ───

    @Test
    fun old_in_house_overlay_stack_is_fully_retired_from_MainActivity() {
        // 这些标识符/字段从 in-house 14 屏栈遗留下来;迁移后 MainActivity 里不应再有。
        listOf(
            "OverlayScreen",
            "pushOverlay",
            "popOverlay",
            "overlayStack",
            "rememberOverlayStack", // 即便起别名也禁
        ).forEach { token ->
            assertFalse(
                "MainActivity 不应再引用 $token(已迁移到 Navigation Compose)",
                mainSrc.contains(token)
            )
        }
    }

    @Test
    fun appRoot_delegates_to_SleepyNavHost_no_inline_overlay_branches() {
        // AppRoot 内不应再有 14 个 if 分支;整个 overlay 树应在 NavHost 里。
        // 锁:AppRoot 闭包体内必须出现 SleepyNavHost(...) 调用,且不再有
        // `topOverlay()` / `when (overlayStack.lastOrNull())` 这类模式。
        //
        // 用行级提取(到列 0 的 `}` 为止),避开 balancedBlock 对参数默认值 `= {}`
        // 里嵌套花括号的误抓。
        val appRootBlock = extractTopLevelFunBody(mainSrc, "private fun AppRoot")
        assertTrue(
            "AppRoot 必须调用 SleepyNavHost(...)",
            appRootBlock.contains("SleepyNavHost(")
        )
        listOf(
            "topOverlay(",
            "overlayStack",
            "OverlayScreen",
        ).forEach { dead ->
            assertFalse("AppRoot 内不应再有 $dead 痕迹", appRootBlock.contains(dead))
        }
    }

    private fun extractTopLevelFunBody(src: String, signaturePrefix: String): String {
        val lines = src.lines()
        val startIdx = lines.indexOfFirst { it.startsWith(signaturePrefix) }
        assertTrue("找不到函数签名 $signaturePrefix", startIdx >= 0)
        // 从签名行开始,累积直到遇到列 0 的 `}`
        val sb = StringBuilder()
        for (i in startIdx until lines.size) {
            sb.appendLine(lines[i])
            if (lines[i] == "}") return sb.toString()
        }
        error("函数 $signaturePrefix 未闭合")
    }

    // ─── 入口一致性 ───

    @Test
    fun every_routes_constant_has_a_composable_destination() {
        // 与 BackRestoreSaveableContractTest 里的同一不变量,这里从 ui/nav 包视角再锁一次。
        val routesConsts = Regex("""const\s+val\s+(\w+)\s*=\s*[\"']""").findAll(routesSrc)
            .map { it.groupValues[1] }
            .filter { it != "ARG_TABLE_ID" && it != "ARG_PERIOD_TABLE_ID" &&
                     it != "ARG_PENDING_NEW" && it != "ARG_PREV_DEFAULT" &&
                     it != "ARG_PENDING_NEW_PERIOD" && it != "ARG_EDITING" &&
                     it != "ARG_COURSE_ID" && it != "ARG_HOLIDAY_ID" }
            .toList()
        assertTrue("Routes 至少应有 14 个路由常量", routesConsts.size >= 14)
        routesConsts.forEach { c ->
            assertTrue(
                "Routes.$c 必须有对应的 composable(Routes.$c 注册",
                navHostSrc.contains("composable(Routes.$c")
            )
        }
    }

    // ─── issue#45 ③ 自适应导航: 官方有→官方, 官方无→保留自研 Dock ───
    @Test
    fun mainRoute_uses_official_nav_where_official_exists_and_keeps_self_dock() {
        val route = balancedBlock(navHostSrc, "private fun MainRoute(", maxChars = 8000)

        // ③ 横屏/平板: 必须按 WindowWidthSizeClass 分支, 非 Compact 用官方 NavigationRail
        assertTrue(
            "MainRoute 必须按 WindowWidthSizeClass.Compact 分支(横屏适配)",
            Regex("""WindowWidthSizeClass\.Compact""").containsMatchIn(route)
        )
        assertTrue(
            "非 Compact 必须用官方 NavigationRail(官方有此形态)",
            Regex("""NavigationRail\s*\{""").containsMatchIn(route)
        )
        assertTrue(
            "NavigationRail 必须用官方 NavigationRailItem",
            Regex("""NavigationRailItem\(""").containsMatchIn(route)
        )

        // 贴底: 官方有 NavigationBar → 用官方, 不得再用自研 PillNavigationBar 贴底形态
        assertTrue(
            "Compact 贴底必须用官方 NavigationBar",
            Regex("""NavigationBar\s*\{""").containsMatchIn(route)
        )
        assertTrue(
            "Compact 贴底必须用官方 NavigationBarItem",
            Regex("""NavigationBarItem\(""").containsMatchIn(route)
        )
        assertFalse(
            "贴底形态已改官方 NavigationBar, 不得残留 PillNavigationBar(dock = false)",
            Regex("""dock\s*=\s*false""").containsMatchIn(route)
        )

        // 悬浮 Dock: 官方无此形态 → 必须保留自研 PillNavigationBar(dock = true)
        assertTrue(
            "官方无悬浮药丸 Dock, 必须保留自研 PillNavigationBar(dock = true)",
            Regex("""PillNavigationBar\([\s\S]*?dock\s*=\s*true""").containsMatchIn(route)
        )
    }
}
