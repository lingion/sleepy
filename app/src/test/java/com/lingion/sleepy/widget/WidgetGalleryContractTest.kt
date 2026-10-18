package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「添加小组件画廊」契约（docs/widget-add-gallery-design.md §4 不变量②③、§5）。
 *
 *  A. 画廊 = 注册表投影: 卡片数据恒等于 ALL_WIDGET_VARIANTS,数量零硬编码;
 *     新变体并入时画廊自动多一张,画廊代码零改动。
 *  B. 单一添加通路: 应用内 requestPinAppWidget 只允许出现在 WidgetPinRequest
 *     一处(adb 透明通路 PinWidgetActivity 除外,那是外部深链遗留);
 *     画廊页只准调 helper,禁止第二套实现。
 *  C. 管理页加号: 「添加或管理」标题栏 actions 位有 ＋ 按钮,路由到新画廊页。
 *
 * A 是真 JVM 运行时断言;B/C 与仓库其他契约测试同风格,源文件 token 扫描。
 */
class WidgetGalleryContractTest {

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    // ---- A. 画廊投影闸 ----

    @Test
    fun `gallery cards are a 1-to-1 projection of ALL_WIDGET_VARIANTS`() {
        val cards = WidgetGalleryData.buildCards()
        assertEquals(
            "画廊卡片数量必须恒等于注册表数量(当前 ${ALL_WIDGET_VARIANTS.size})",
            ALL_WIDGET_VARIANTS.size, cards.size
        )
        assertEquals(
            "卡片与注册表逐项同序同类",
            ALL_WIDGET_VARIANTS.map { it.receiverClass },
            cards.map { it.variant.receiverClass }
        )
        cards.forEach {
            assertTrue("每张卡必须有可解析名称资源", it.variant.displayNameRes != 0)
            assertTrue("每张卡必须有可解析预览图资源", it.variant.previewImageRes != 0)
        }
    }

    @Test
    fun `gallery screen renders the registry, never a hardcoded list`() {
        val src = findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetGalleryScreen.kt").readText()
        assertTrue("画廊必须来自 WidgetGalleryData.buildCards()", src.contains("WidgetGalleryData.buildCards()"))
        // 画廊数据源必须是注册表本身(或经 buildCards 派生),禁止逐 variant 手写枚举
        val data = findUpward("app/src/main/java/com/lingion/sleepy/widget/WidgetGalleryData.kt").readText()
        assertTrue("画廊数据必须投影 ALL_WIDGET_VARIANTS", data.contains("ALL_WIDGET_VARIANTS"))
    }

    // ---- B. 单一添加通路闸 ----

    @Test
    fun `requestPinAppWidget lives in exactly one UI-path helper`() {
        val mainJavaRoot = findUpward("app/src/main/java/com/lingion/sleepy/widget/PinWidgetActivity.kt")
            .let { it.parentFile.parentFile.parentFile.parentFile.parentFile } // → app/src/main/java
        val uiCallers = mainJavaRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // 注释行提及 API 名不算实现 — 只统计代码行的真实调用
            .filter { f ->
                f.readLines().any { line ->
                    !line.trimStart().startsWith("*") && !line.trimStart().startsWith("//") &&
                        line.contains("requestPinAppWidget")
                }
            }
            .map { it.name }
            .toSet()
        // helper = WidgetPinRequest.kt; PinWidgetActivity = adb/外部深链透明通路(遗留,非应用内 UI)
        assertEquals(
            "应用内添加通路必须唯一; 允许的文件集合 = {WidgetPinRequest.kt, PinWidgetActivity.kt}",
            setOf("WidgetPinRequest.kt", "PinWidgetActivity.kt"), uiCallers
        )
    }

    @Test
    fun `pin helper has supported precheck and failure result`() {
        val src = findUpward("app/src/main/java/com/lingion/sleepy/widget/WidgetPinRequest.kt").readText()
        assertTrue("helper 必须预检 isRequestPinAppWidgetSupported", src.contains("isRequestPinAppWidgetSupported"))
        assertTrue("helper 必须带 callback PendingIntent 闭环", src.contains("PendingIntent"))
        // 结果三态: 已发起 / 桌面不支持 / 发起失败 —— 后两态 UI 都要给手动添加指引
        listOf("NOT_SUPPORTED", "FAILED", "REQUESTED").forEach {
            assertTrue("PinResult 必须含 $it", src.contains(it))
        }
    }

    @Test
    fun `gallery screen pins via the single helper only`() {
        val src = findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetGalleryScreen.kt").readText()
        assertTrue("画廊必须走 WidgetPinRequest", src.contains("WidgetPinRequest.request"))
        assertFalse("画廊禁止直接 call requestPinAppWidget(第二套实现)", src.contains("requestPinAppWidget"))
    }

    // ---- C. 管理页加号 + 画廊路由 ----

    @Test
    fun `management screen has plus action routing to gallery`() {
        val src = findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetManagementScreen.kt").readText()
        assertTrue("管理页形参必须有 onOpenWidgetGallery", Regex("""onOpenWidgetGallery:\s*\(\)\s*->\s*Unit""").containsMatchIn(src))
        assertTrue("TopAppBar 必须有 actions 槽", src.contains("actions = {"))
        assertTrue("actions 必须是 Add 图标按钮", src.contains("Icons.Outlined.Add") && src.contains("onOpenWidgetGallery"))
    }

    @Test
    fun `gallery route wired through navigator and navhost`() {
        val routes = findUpward("app/src/main/java/com/lingion/sleepy/ui/nav/SleepyRoutes.kt").readText()
        val navigator = findUpward("app/src/main/java/com/lingion/sleepy/ui/nav/SleepyNavigator.kt").readText()
        val navHost = findUpward("app/src/main/java/com/lingion/sleepy/ui/nav/SleepyNavHost.kt").readText()
        assertTrue(routes.contains("data object WidgetGallery : SleepyRoute"))
        assertTrue(navigator.contains("fun openWidgetGallery() = push(SleepyRoute.WidgetGallery)"))
        assertTrue(navHost.contains("entry<SleepyRoute.WidgetGallery>"))
        assertTrue(navHost.contains("onOpenWidgetGallery = { navigator.openWidgetGallery() }"))
    }

    // ---- D. pin callback 闭环: 新实例即时可见(返回管理页时重扫) ----

    @Test
    fun `management screen reloads placed list on resume`() {
        val src = findUpward("app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetManagementScreen.kt").readText()
        assertTrue(
            "从画廊 pin 返回后列表必须反映新实例: 管理页需在 ON_RESUME 触发 vm.reload()",
            src.contains("LifecycleEventObserver") && src.contains("ON_RESUME") && src.contains("reload()")
        )
    }
}
