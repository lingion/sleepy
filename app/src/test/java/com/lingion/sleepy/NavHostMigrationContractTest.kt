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
        File("src/main/java/com/lingion/sleepy/ui/nav/SleepyMaterialTransition.kt").readText()
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

    // ─── EditTable 弃表语义 (official navigation3 entry<T> 锚点) ───

    @Test
    fun editTable_route_backHandler_drops_pending_table_via_discardNewTable() {
        val block = balancedBlock(navHostSrc, "entry<SleepyRoute.EditTable>")
        // 兜底系统返回:enabled = pending != null(本地状态,不是路由参数)
        assertTrue(
            "EditTable 必须有 BackHandler(enabled = pending != null)",
            Regex("""BackHandler\(\s*enabled\s*=\s*pending\s*!=\s*null\s*\)""").containsMatchIn(block)
        )
        assertTrue(
            "EditTable 弃表回调必须调 mainVm.discardNewTable",
            block.contains("mainVm.discardNewTable")
        )
        assertTrue(
            "EditTable 弃表后清 pending = null(防止重复触发)",
            block.contains("pending = null")
        )
    }

    @Test
    fun editTable_pending_is_local_rememberSaveable_not_route_arg() {
        val block = balancedBlock(navHostSrc, "entry<SleepyRoute.EditTable>")
        // pending 必须用本地 rememberSaveable,而非直接拿路由参数
        assertTrue(
            "pending 必须本地化: rememberSaveable + mutableStateOf<Long?>(routePendingNew)",
            Regex(
                """var\s+pending\s+by\s+rememberSaveable\s*\{\s*mutableStateOf<Long\?>\s*\(\s*routePendingNew\s*\)"""
            ).containsMatchIn(block)
        )
        // 兜底注释必须解释为什么不读路由参数(防历史 bug 复发)
        assertTrue(
            "EditTable 必须有『读路由参数会触发历史 bug』类注释",
            block.contains("路由参数") && block.contains("bug")
        )
    }

    // ─── PeriodTableEdit 弃表语义 ───

    @Test
    fun periodTableEdit_route_backHandler_drops_pending_period_via_discardNewPeriodTable() {
        val block = balancedBlock(navHostSrc, "entry<SleepyRoute.PeriodEdit>")
        assertTrue(
            "PeriodEdit 必须有 BackHandler(enabled = unsavedNew)",
            Regex("""BackHandler\(\s*enabled\s*=\s*unsavedNew\s*\)""").containsMatchIn(block)
        )
        assertTrue(
            "PeriodEdit 弃表回调必须调 mainVm.discardNewPeriodTable",
            block.contains("mainVm.discardNewPeriodTable")
        )
        assertTrue(
            "PeriodEdit 弃表后清 unsavedNew = false",
            block.contains("unsavedNew = false")
        )
    }

    // ─── 弃表兜底优先级:屏内 BackHandler > 导航回调 ───

    @Test
    fun editTable_local_backHandler_registers_after_onBack_navigation_chain() {
        // 导航回调链(主屏 onBack + 各 Tab onBack)先注册,per-route BackHandler 后注册;
        // BackHandler "最后注册者优先" 语义正是需要的:用户在 EditTable 时,本地兜底应赢。
        // 锁契约:EditTable 块的 BackHandler 出现位置晚于 `onBack=` 链。
        val block = balancedBlock(navHostSrc, "entry<SleepyRoute.EditTable>")
        val onBackIdx = block.indexOf("onBack")
        val backHandlerIdx = block.indexOf("BackHandler(enabled = pending != null)")
        assertTrue("EditTable 块内应出现 onBack 链", onBackIdx > 0)
        assertTrue("EditTable 块内应出现本地 BackHandler", backHandlerIdx > 0)
        assertTrue(
            "本地 BackHandler 必须注册在 onBack 链之后(BackHandler 后注册者优先)",
            backHandlerIdx > onBackIdx
        )
    }

    // ─── Material 动效: pop 用 scaleOut + Center origin, popEnter=None, forward 对称 scale ───

    @Test
    fun popExit_uses_scaleOut_with_target_0_92_and_center_transformOrigin() {
        // issue#45 第三轮 (2026-09-21): miuix 弃库 → 官方 navigation3,
        // Material 动效同样用 scaleIn/Out + TransformOrigin.Center + 0.92,
        // 完全对齐 Material Motion in-app 返回手势官方示例。
        val body = balancedBlock(routesSrc, "fun sleepyPopExit")
        assertTrue(
            "popExit 必须调 scaleOut(0.92f, TransformOrigin.Center) — 0.92 改 0.85/0.95 都是偏离官方示例",
            Regex(
                """scaleOut\(\s*animationSpec\s*=\s*forwardTween\s*,\s*targetScale\s*=\s*0\.92f\s*,\s*transformOrigin\s*=\s*TransformOrigin\.Center"""
            ).containsMatchIn(body)
        )
        // predictiveCommitTween 是 Material 化后的统一 tween(200ms CubicBezier(0.2,0,0,1)),
        // 供 predictivePopTransitionSpec 手势释放(commit)时使用;popExit(程序化返回)用
        // forwardTween。锁:tween 配置必须与 InstallerX Classic 的物理参数对齐
        // (时长 200ms, CubicBezier 0.2/0/0/1)。
        assertTrue(
            "predictive commit 的 tween 必须 PredictiveDuration(200ms)+ CubicBezier(0.2, 0, 0, 1)(InstallerX Classic 物理对齐)",
            routesSrc.contains("PredictiveDuration: Int = 200") &&
                routesSrc.contains("CubicBezierEasing(0.2f, 0f, 0f, 1f)")
        )
        assertTrue(
            "predictivePop 必须引用顶层 predictiveCommitTween(禁止函数内另起 tween 参数)",
            Regex("""internal val sleepyPredictivePopTransform[\s\S]{0,1200}?predictiveCommitTween""").containsMatchIn(routesSrc)
        )
    }

    @Test
    fun popEnter_returns_None_so_target_page_stays_in_place() {
        // 官方推荐示例 popEnter = EnterTransition.None: 返回时目标页不重画,
        // 退出页独立缩放淡出。sleepy 之前 popEnter 是 slideIn+slideOut+1/6(返回的页面从左侧滑入),
        // 视觉上"两层都动", 与缩放叠加会出现用户吐槽的"卡很久"。
        val body = balancedBlock(routesSrc, "fun sleepyPopEnter")
        assertTrue(
            "popEnter 必须返回 EnterTransition.None(目标页原地不动画)",
            Regex("""fun\s+sleepyPopEnter\(\)[\s\S]{0,200}?EnterTransition\.None""").containsMatchIn(routesSrc)
        )
        // 旧 slide 痕迹不能再出现 (滑动位移在 popEnter 路径上)
        assertFalse(
            "popEnter 路径内不允许出现 slideInHorizontally (Material 方案已禁返回滑入)",
            body.contains("slideInHorizontally")
        )
    }

    @Test
    fun forward_enter_exit_use_mirrored_scale_92_with_220ms_tween() {
        // 推进 (forward): enter fadeIn + scaleIn(0.92, Center); exit fadeOut + scaleOut(0.92, Center);
        // tween(220, FastOutSlowInEasing) — 不对称 + spring 是 d7fc 那轮的二次翻车点。
        val enterBody = balancedBlock(routesSrc, "fun sleepyForwardEnter")
        val exitBody = balancedBlock(routesSrc, "fun sleepyForwardExit")
        assertTrue(
            "forwardEnter 必须用 fadeIn + scaleIn(initialScale = 0.92f, TransformOrigin.Center)",
            enterBody.contains("fadeIn(forwardFadeInTween)") &&
                enterBody.contains("scaleIn(") &&
                enterBody.contains("initialScale = 0.92f") &&
                enterBody.contains("TransformOrigin.Center")
        )
        assertTrue(
            "forwardExit 必须用 fadeOut + scaleOut(targetScale = 0.92f, TransformOrigin.Center)",
            exitBody.contains("fadeOut(forwardTween)") &&
                exitBody.contains("scaleOut(") &&
                exitBody.contains("targetScale = 0.92f") &&
                exitBody.contains("TransformOrigin.Center")
        )
        assertTrue(
            "enter/exit 必须引用顶层 forwardTween(其定义锁定 220ms + FastOutSlowInEasing)",
            enterBody.contains("forwardTween") && exitBody.contains("forwardTween") &&
                routesSrc.contains("durationMillis = ForwardDuration") &&
                routesSrc.contains("easing = FastOutSlowInEasing")
        )
        assertTrue(
            "fade 速度要锁: enter fadeIn 110ms, exit fadeOut 220ms (避免两块页面同进同出叠影)",
            enterBody.contains("forwardFadeInTween") &&
                exitBody.contains("forwardTween") &&
                routesSrc.contains("durationMillis = ForwardFadeIn") &&
                routesSrc.contains("durationMillis = ForwardDuration")
        )
    }

    @Test
    fun transition_constants_are_top_level_not_buried_in_function_body() {
        // 锁参数常量化 — 修改时长要改一处而非四处。常量是 internal const val(测试可见):
        listOf(
            "internal const val ForwardDuration",
            "internal const val ForwardFadeIn",
            "internal const val PredictiveDuration",
            "predictiveCommitTween",
        ).forEach { ident ->
            assertTrue(
                "SleepyMaterialTransition.kt 顶层必须暴露过渡参数 $ident(集中调参点, 否则动画散在 4 个函数里)",
                Regex("""$ident\b""").containsMatchIn(routesSrc)
            )
        }
    }

    @Test
    fun manifest_predictive_back_stays_on_when_popExit_is_scaleOut() {
        // 2026-09-20 用户真机 (Mate 30 EMUI) 翻车定稿: OEM 对 in-app BACK 也做
        // 系统级预测性返回预演(整个任务窗口矩形缩小) → 773ff116 关闭
        // enableOnBackInvokedCallback, BACK 走经典 KeyEvent 分发。
        // 本契约随决策反转: manifest 必须=false(防再次打开重蹈 OEM 缩放),
        // predictivePopTransform 保留 scaleOut 仅供 NavDisplay popTransitionSpec 使用。
        assertTrue(
            "OEM 真机翻车定稿: predictive-back 必须=false(矩形缩小+logo 残留, 见 773ff116)",
            Regex(
                """<application[\s\S]{0,500}?android:enableOnBackInvokedCallback\s*=\s*[\"']false[\"']"""
            ).containsMatchIn(manifestSrc)
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
        // 2026-09-20 决策再反转: NIA 模式下系统返回/最近任务的窗口快照仍会回看
        // windowBackground(纯色+logo), Mate 30 in-app 返回 = "矩形缩小+logo 残留"。
        // 773ff116 恢复 da3a6335 方案: 首帧后 OneShotPreDraw 把底衬降级为同色纯色。
        // 本契约锁定该 hack 必须存在(防误删), 且降级色 = splash_background(同色无感)。
        assertTrue(
            "MainActivity 必须保留 OneShotPreDraw 首帧后降级 windowBackground (OEM 快照露出 logo, 见 773ff116)",
            mainSrc.contains("OneShotPreDrawListener")
        )
        assertTrue(
            "降级底衬必须用 splash_background 纯色(与启动页同色, 用户无感)",
            mainSrc.contains("splash_background")
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
        // popToMain 才是唯一允许清栈回主屏的地方:任意栈→回 main。
        // 官方 nav3: 栈是 NavBackStack(MutableList),清栈 = 逐个 removeAt 直到 Main 是栈底之上的唯一残留。
        assertTrue(
            "popToMain 必须 while 循环 removeAt 清栈,且保留 SleepyRoute.Main",
            Regex(
                """fun\s+popToMain[\s\S]{0,400}?removeAt\(backStack\.lastIndex\)"""
            ).containsMatchIn(navigatorSrc) &&
                Regex("""fun\s+popToMain[\s\S]{0,400}?SleepyRoute\.Main""").containsMatchIn(navigatorSrc)
        )
    }

    @Test
    fun navigator_open_methods_only_use_routes_constants_no_string_literals() {
        // 14 个 openXxx 入口全部走 SleepyRoute typed 子类,不允许裸字符串。
        // 多数是表达式体 `fun openX() = push(SleepyRoute.X)`,少数是块体。
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
                "$fn 必须引用 SleepyRoute.X typed 子类(不许裸字符串路由名), 实际片段: ${window.take(120)}",
                Regex("""SleepyRoute\.\w+""").containsMatchIn(window)
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
        // 2026-09-20 决策反转(见 manifest_predictive_back_stays_on_when_popExit_is_scaleOut):
        // OEM 对 in-app BACK 做系统级预演 → 关闭 predictive-back, 走经典 KeyEvent。
        assertFalse(
            "AndroidManifest <application> 必须含 enableOnBackInvokedCallback=\"false\"(OEM 预演翻车定稿)",
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
        // 官方 nav3 版: SleepyRoute sealed 的每个子类都必须在 NavHost 有 entry<SleepyRoute.X> 注册。
        // 子类声明在 SleepyRoutes.kt(不是 transition 文件), 所以用 navHostSrc 同目录的独立读取。
        val routesFileSrc = File("src/main/java/com/lingion/sleepy/ui/nav/SleepyRoutes.kt").readText()
        val routeTypes = Regex("""@Serializable\s+(?:data\s+)?(?:object|class)\s+(\w+)""").findAll(routesFileSrc)
            .map { it.groupValues[1] }
            .filter { it != "NO_ID" }
            .toList()
        assertTrue("SleepyRoute 至少应有 14 个 typed 子类, 实际: $routeTypes", routeTypes.size >= 14)
        routeTypes.forEach { c ->
            assertTrue(
                "SleepyRoute.$c 必须有对应的 entry<SleepyRoute.$c> 注册",
                navHostSrc.contains("entry<SleepyRoute.$c>")
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
