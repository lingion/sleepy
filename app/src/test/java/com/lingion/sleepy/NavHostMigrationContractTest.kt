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
    private val themesSrc by lazy {
        File("src/main/res/values/themes.xml").readText()
    }
    private val themesNightSrc by lazy {
        File("src/main/res/values-night/themes.xml").readText()
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

    // ─── B 方案: pop 用 scaleOut, popEnter=None, forward 对称 slide 1/8 ───

    @Test
    fun popExit_uses_scaleOut_with_target_0_92_and_center_transformOrigin() {
        // issue#45 第二轮 (2026-09-20): 用户选 B 方案 — pop 时退出页向屏幕中心缩放 + 淡出,
        // 目标页原地不动。这是 Navigation Compose 2.8+ 官方推荐 in-app 返回动画
        // (developer.android.com/develop/ui/compose/system/predictive-back-setup 示例)。
        val body = balancedBlock(routesSrc, "fun sleepySharedAxisPopExit")
        assertTrue(
            "popExit 必须调 scaleOut(0.92f, TransformOrigin(0.5, 0.5)) — 0.92 改 0.85/0.95 都是偏离官方示例",
            Regex(
                """scaleOut\(\s*targetScale\s*=\s*0\.92f\s*,\s*animationSpec\s*=\s*popExitSpring\s*,\s*transformOrigin\s*=\s*TransformOrigin\(\s*0\.5f\s*,\s*0\.5f\s*\)"""
            ).containsMatchIn(body)
        )
        // spring 必须不回弹 + 不软,避免 M3 expressive spatial 0.8/380 过冲抖动
        // popExitSpring 定义在文件顶层(不在函数块内),所以全文件扫描
        assertTrue(
            "popExit 的 spring 必须 DampingRatioNoBouncy + StiffnessMediumLow(不回弹不拖沓)",
            routesSrc.contains("dampingRatio = Spring.DampingRatioNoBouncy") &&
                routesSrc.contains("stiffness = Spring.StiffnessMediumLow")
        )
        assertTrue(
            "popExit 必须引用顶层 popExitSpring(禁止函数内另起 spring 参数)",
            body.contains("animationSpec = popExitSpring")
        )
    }

    @Test
    fun popEnter_returns_None_so_target_page_stays_in_place() {
        // 官方推荐示例 popEnter = EnterTransition.None: 返回时目标页不重画,
        // 退出页独立缩放淡出。sleepy 之前 popEnter 是 slideIn+slideOut+1/6(返回的页面从左侧滑入),
        // 视觉上"两层都动", 与缩放叠加会出现用户吐槽的"卡很久"。
        val body = balancedBlock(routesSrc, "fun sleepySharedAxisPopEnter")
        assertTrue(
            "popEnter 必须返回 EnterTransition.None(目标页原地不动画)",
            Regex("""fun\s+sleepySharedAxisPopEnter\(\)[\s\S]{0,200}?EnterTransition\.None""").containsMatchIn(routesSrc)
        )
        // 旧 slide 痕迹不能再出现 (滑动位移在 popEnter 路径上)
        assertFalse(
            "popEnter 路径内不允许出现 slideInHorizontally (B 方案已禁返回滑入)",
            body.contains("slideInHorizontally")
        )
    }

    @Test
    fun forward_enter_exit_use_mirrored_slide_one_eighth_screen_with_220ms_tween() {
        // 推进 (forward): enter 来自右侧 1/8, exit 退到左侧 1/8, 对称;
        // tween(220, FastOutSlowInEasing) — 1/4 + 1/8 不对称是 d7fc 那轮的二次翻车点。
        val enterBody = balancedBlock(routesSrc, "fun sleepySharedAxisEnter")
        val exitBody = balancedBlock(routesSrc, "fun sleepySharedAxisExit")
        assertTrue(
            "enter 必须用 slideInHorizontally + initialOffsetX = { it / 8 } (对称推入, 非 1/4 也非 1/6)",
            Regex("""slideInHorizontally\(\s*initialOffsetX\s*=\s*\{\s*it\s*/\s*ForwardOffsetFraction\s*\}""")
                .containsMatchIn(enterBody)
        )
        assertTrue(
            "exit 必须用 slideOutHorizontally + targetOffsetX = { -it / 8 } (对称推出)",
            Regex("""slideOutHorizontally\(\s*targetOffsetX\s*=\s*\{\s*-it\s*/\s*ForwardOffsetFraction\s*\}""")
                .containsMatchIn(exitBody)
        )
        assertTrue(
            "enter/exit 必须 tween(220, FastOutSlowInEasing) — 1/4 屏 + spring 是用户实锤翻车组合",
            enterBody.contains("tween(durationMillis = ForwardDuration, easing = FastOutSlowInEasing)")
        )
        assertTrue(
            "fade 速度要锁: enter fadeIn 110ms, exit fadeOut 220ms (避免两块页面同进同出叠影)",
            enterBody.contains("fadeIn(tween(durationMillis = ForwardFadeIn") &&
                exitBody.contains("fadeOut(tween(durationMillis = ForwardDuration")
        )
    }

    @Test
    fun transition_constants_are_top_level_not_buried_in_function_body() {
        // 锁参数常量化 — 修改位移 / 时长要改一处而非四处。文件名/常量名锁定:
        // 任何调整必须改 ForwardDuration/ForwardFadeIn/ForwardOffsetFraction 顶层常量。
        listOf(
            "private const val ForwardDuration",
            "private const val ForwardFadeIn",
            "private const val ForwardOffsetFraction",
            "private val popExitSpring",
        ).forEach { ident ->
            assertTrue(
                "SleepyRoutes.kt 顶层必须暴露过渡参数 $ident(集中调参点, 否则动画散在 4 个函数里)",
                Regex("""$ident\b""").containsMatchIn(routesSrc)
            )
        }
    }

    @Test
    fun manifest_predictive_back_stays_on_when_popExit_is_scaleOut() {
        // 双重锁定: B 方案依赖 enableOnBackInvokedCallback=true 让系统手势驱动 popExit 缩放;
        // 这与 NavHostMigrationContractTest::manifest_enables_predictive_back_app_wide 重复,
        // 单独再锁一次防止任一处 toggle 时互相独立决策 — 两处都开才是 B 方案成立条件。
        assertTrue(
            "B 方案要求 predictive-back=true(系统手势驱动 popExit 缩放, scaleOut 才生效)",
            Regex(
                """<application[\s\S]{0,500}?android:enableOnBackInvokedCallback\s*=\s*[\"']true[\"']"""
            ).containsMatchIn(manifestSrc)
        )
        // 反向 — popExit 必须用 scaleOut (如果有人把 predictive-back 关了但忘改 popExit,
        // 没有系统手势驱动, scaleOut 就只是个静态过渡, 会失去预览语义)
        val popExitBody = balancedBlock(routesSrc, "fun sleepySharedAxisPopExit")
        assertTrue(
            "B 方案成立需要 scaleOut 出现; 切回 slide 必须同步关 predictive-back 并删本测试",
            popExitBody.contains("scaleOut(")
        )
    }

    // ─── Splash / windowBackground 主题契约 (NIA 模式) ───

    @Test
    fun splash_theme_uses_AndroidXSplashScreen_parent_with_postSplashScreenTheme_switch() {
        // 2026-09-20 用户反馈 "返回预览出现白底+居中 logo": 根因是 Theme.Sleepy.Splash
        // 把 logo layer-list 当 windowBackground 一直挂着, 系统预览 (EMUI predictive-back
        // 预演 / 最近任务快照 / Activity 切换缩略图) 全部按底衬缩略。NIA 模式: parent =
        // androidx Theme.SplashScreen, postSplashScreenTheme 切到运行时主题 — 首帧后窗口
        // 背景归零, 系统任何预览拿不到 logo。
        assertTrue(
            "Theme.Sleepy.Splash 必须 parent=Theme.SplashScreen (androidx core-splashscreen); " +
                "切回 Theme.Material.* 会把 logo 底衬带回运行时窗口, 用户返回预览就又见 logo",
            Regex(
                """<style\s+name="NightAdjusted\.Theme\.Sleepy\.Splash"[^>]*parent="Theme\.SplashScreen""""
            ).containsMatchIn(themesSrc)
        )
        assertTrue(
            "Theme.Sleepy.Splash 必须 postSplashScreenTheme 切到运行时主题; " +
                "没有 postSplashScreenTheme 就一直停在启动主题, installSplashScreen 不切",
            Regex(
                """<style\s+name="Theme\.Sleepy\.Splash"[\s\S]{0,500}?postSplashScreenTheme"""
            ).containsMatchIn(themesSrc)
        )
        assertTrue(
            "postSplashScreenTheme 必须指向运行时 Theme.Sleepy (无 logo 底衬的纯色主题)",
            Regex("""postSplashScreenTheme"[^/>]*>@style/Theme\.Sleepy""").containsMatchIn(themesSrc)
        )
    }

    @Test
    fun runtime_theme_has_no_logo_windowBackground_drawable() {
        // Theme.Sleepy 不得挂任何 @drawable/splash_background 当 windowBackground —
        // 那就是用户反馈的"白底+居中 logo"残留的源头。运行时主题背景归零或纯色都行,
        // 关键是不能再引用 splash_background drawable。XML 是 <style ...>...</style>,
        // 没有 {}, 不能用 balancedBlock — 用 regex 摘出 <style name="Theme.Sleepy" ...> 块。
        val runtimeStyle = Regex(
            """<style\s+name="Theme\.Sleepy"\s*/>"""
        ).findAll(themesSrc).map { it.value }.toList()
            .ifEmpty {
                Regex(
                    """<style\s+name="Theme\.Sleepy"[\s\S]*?</style>"""
                ).findAll(themesSrc).map { it.value }.toList()
            }
        assertTrue(
            "values/themes.xml 必须有 <style name=\"Theme.Sleepy\" ...> 块",
            runtimeStyle.isNotEmpty()
        )
        runtimeStyle.forEach { block ->
            assertFalse(
                "运行时主题 Theme.Sleepy 不得 windowBackground=@drawable/splash_background " +
                    "(logo layer-list 是用户返回预览看到 logo 的根因; NIA 模式运行时主题纯色或默认)",
                block.contains("windowBackground") && block.contains("@drawable/splash_background")
            )
        }
        // 双保险: 整个 values/themes.xml + values-night/themes.xml 中, @drawable/splash_background
        // 不得出现在任何 <style> 里 (唯一合理位置 = 没有位置, splash 现在走 androidx 纯色属性)。
        (themesSrc + themesNightSrc).let { allThemes ->
            Regex("""<style[\s\S]*?</style>""").findAll(allThemes).forEach { m ->
                assertFalse(
                    "任何 <style> 都不得引用 @drawable/splash_background (logo layer-list 已删除, " +
                        "若重新引用说明底衬又回来了)",
                    m.value.contains("@drawable/splash_background")
                )
            }
        }
    }

    @Test
    fun no_oneShotPreDraw_windowBackground_downgrade_in_MainActivity() {
        // NIA 模式迁移后, 运行时主题已无 logo 底衬, PreDraw 降级 hack (da3a6335) 不再
        // 需要 — 删了反而清爽。本测试防回滚: 若有人未来又把 logo 底衬挂回 Theme.Sleepy
        // 或把 Theme.Sleepy.Splash 切回 Theme.Sleepy, 看不见 logo 残留 → 重新引入 hack。
        // 正确做法是检查 themes.xml, 不是在 MainActivity 里打补丁。
        assertFalse(
            "MainActivity 不得 OneShotPreDraw 降级 windowBackground — NIA 模式已修根因 (主题切换), " +
                "重新引入此 hack 表明底衬又出现 logo, 应回到 themes.xml 修",
            mainSrc.contains("OneShotPreDrawListener")
        )
        assertFalse(
            "MainActivity 不得 import ColorDrawable — 同上, hack 已删",
            mainSrc.contains("import android.graphics.drawable.ColorDrawable")
        )
    }

    @Test
    fun splash_background_drawable_is_deleted() {
        // drawable/splash_background.xml (logo layer-list) 是窗口底衬翻车源头, NIA 模式
        // 切到 androidx Theme.SplashScreen 后已无任何主题引用, 必须物理删除 — 留着会被未来
        // 新主题不小心引用上, 重蹈覆辙。
        val splash = File("src/main/res/drawable/splash_background.xml")
        assertFalse(
            "drawable/splash_background.xml 必须删除 (NIA 模式已切 androidx Theme.SplashScreen," +
                "旧 logo layer-list 留着会被未来主题意外引用, 重新引入返回预览 logo)",
            splash.exists()
        )
    }

    @Test
    fun night_adjusted_theme_split_shares_attributes_in_middle_layer() {
        // night-adjusted 拆分模式: 两侧 (values/ + values-night/) 只重声明需要切换的
        // 父主题与状态栏语义, 共用属性挂在中间层, 不被 night 侧覆盖重置。
        // 反模式: 在 values-night/themes.xml 直接重声明 Theme.Sleepy 完整样式 →
        // 会清掉 values/ 侧所有挂的属性。
        assertTrue(
            "values/themes.xml 必须有 NightAdjusted.Theme.Sleepy 中间层 " +
                "(共享 statusBarColor/navigationBarColor/启动底衬色)",
            themesSrc.contains("NightAdjusted.Theme.Sleepy")
        )
        assertTrue(
            "values/themes.xml 必须有 NightAdjusted.Theme.Sleepy.Splash 中间层",
            themesSrc.contains("NightAdjusted.Theme.Sleepy.Splash")
        )
        assertTrue(
            "values-night/themes.xml 必须重声明 NightAdjusted.Theme.Sleepy (换深色父主题)",
            themesNightSrc.contains("NightAdjusted.Theme.Sleepy")
        )
        // 反向 — night 侧不得有完整 Theme.Sleepy 样式覆盖, 那会清掉中间层挂的属性
        assertFalse(
            "values-night/themes.xml 不得完整重声明 Theme.Sleepy (会清掉 values/ 侧中间层属性)",
            Regex("""<style\s+name="Theme\.Sleepy"[^>]*parent=.*>\s*[\s\S]*?windowSplashScreenBackground""")
                .containsMatchIn(themesNightSrc)
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
}
