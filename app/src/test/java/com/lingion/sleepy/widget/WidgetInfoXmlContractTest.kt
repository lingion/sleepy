package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Widget 信息 XML / Manifest 声明契约 — issue #24 Feature 1(R1/R4/R5)。
 *
 * 纯 JVM 读源头文件断言(先例: [com.lingion.sleepy.ManifestExternalOpenFilterTest],
 * 仓库无 Robolectric, 声明式数据读源头文件等价于读打包产物)。
 *
 * 锁三层接线闭环:
 * 1. Manifest 里注册的每个 appwidget receiver 都带 meta-data 指向一个 *_widget_info.xml;
 * 2. 每个 info XML 都不声明 android:configure，添加到桌面时不弹白页;
 * 3. 每个 info XML 的 android:widgetFeatures 都含 reconfigurable，保留桌面长按编辑能力。
 */
class WidgetInfoXmlContractTest {

    private val resXmlDir: File by lazy {
        sequenceOf(
            File("app/src/main/res/xml"),
            File("src/main/res/xml")
        ).first { it.isDirectory }
    }

    private val manifest: Element by lazy {
        val f = sequenceOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        ).first { it.isFile }
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).documentElement
    }

    /** 解析 manifest 的 android:name(可能是 ".widget.X" 或 "com.lingion.sleepy.widget.X")为 FQCN。 */
    private fun toFqcn(androidName: String): String {
        val pkg = "com.lingion.sleepy"
        return if (androidName.startsWith(".")) "$pkg$androidName" else androidName
    }

    /** Manifest 声明的 (receiver 全限定类名 → widget info xml 资源键, 无 @xml/ 前缀也无 .xml 后缀) */
    private val manifestReceivers: Map<String, String> by lazy {
        val out = mutableMapOf<String, String>()
        val receivers = manifest.getElementsByTagName("receiver")
        for (i in 0 until receivers.length) {
            val receiver = receivers.item(i) as Element
            val nameAttr = receiver.getAttribute("android:name")
            if (nameAttr.isNullOrEmpty()) continue
            val metas = receiver.getElementsByTagName("meta-data")
            for (j in 0 until metas.length) {
                val meta = metas.item(j) as Element
                if (meta.getAttribute("android:name") != "android.appwidget.provider") continue
                val resource = meta.getAttribute("android:resource")
                val key = resource.removePrefix("@xml/")
                if (key.endsWith("_widget_info") || key.endsWith("_widget_info.xml")) {
                    out[toFqcn(nameAttr)] = key.removeSuffix(".xml")
                }
            }
        }
        out
    }

    /** info XML 资源键 (去 .xml 后缀) → 根元素(appwidget-provider) */
    private val infoXmls: Map<String, Element> by lazy {
        resXmlDir.listFiles { f -> f.name.endsWith("_widget_info.xml") }
            ?.associate { f -> f.name.removeSuffix(".xml") to
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).documentElement }
            ?: emptyMap()
    }

    /** 全部 receiver 短类名 ↔ ALL_WIDGET_VARIANTS 元数据一一对应(R1 列表的数据源) */
    @Test
    fun `manifest registers exactly the variants in ALL_WIDGET_VARIANTS`() {
        val expected = ALL_WIDGET_VARIANTS
            .map { it.receiverClass.name }
            .toSet()
        assertEquals(expected, manifestReceivers.keys)
    }

    @Test
    fun `every manifest widget info resource has a matching xml file`() {
        assertTrue("no widget receivers with meta-data found in manifest", manifestReceivers.isNotEmpty())
        manifestReceivers.values.forEach { key ->
            assertTrue("missing res/xml/$key.xml", key in infoXmls)
        }
    }

    /** Provider description 回归锁: 保持 v1.0.56 短 label (2026-10-03 拖放崩溃回退)。
     *  v1.0.57 曾换长 description, 与 keyguard 同批回退。 */
    @Test
    fun `every provider uses its short label as description resource`() {
        val expected = mapOf(
            "today_widget_info" to "widget_today_label",
            "today_small_widget_info" to "widget_today_small_label",
            "today_wide_widget_info" to "widget_today_wide_label",
            "twoday_widget_info" to "widget_twoday_label",
            "twoday_small_widget_info" to "widget_twoday_small_label",
            "twoday_wide_widget_info" to "widget_twoday_wide_label",
            "week_list_widget_info" to "widget_week_list_label",
            "week_list_small_widget_info" to "widget_week_list_small_label",
            "weeklist_wide_widget_info" to "widget_week_list_wide_label",
            "week_view_widget_info" to "widget_week_view_label",
            "week_view_small_widget_info" to "widget_week_view_small_label",
            "week_grid_widget_info" to "widget_week_grid_label",
            "week_grid_small_widget_info" to "widget_week_grid_small_label"
        )
        expected.forEach { (xmlName, stringName) ->
            val description = infoXmls.getValue(xmlName).getAttribute("android:description")
            assertEquals("@string/$stringName", description)
        }
    }

    /** Widget picker / OEM text must be localized with every shipped locale. */
    @Test
    fun `all shipped locales define every widget label and description string`() {
        val required = listOf(
            "widget_today_label",
            "widget_today_small_label",
            "widget_today_wide_label",
            "widget_twoday_label",
            "widget_twoday_small_label",
            "widget_twoday_wide_label",
            "widget_week_list_label",
            "widget_week_list_small_label",
            "widget_week_list_wide_label",
            "widget_week_view_label",
            "widget_week_view_small_label",
            "widget_week_grid_label",
            "widget_week_grid_small_label",
            "widget_today_description",
            "widget_today_small_description",
            "widget_today_wide_description",
            "widget_twoday_description",
            "widget_twoday_small_description",
            "widget_twoday_wide_description",
            "widget_week_list_description",
            "widget_week_list_small_description",
            "widget_week_list_wide_description",
            "widget_week_view_description",
            "widget_week_view_small_description",
            "widget_week_grid_description",
            "widget_week_grid_small_description"
        )
        val resRoot = resXmlDir.parentFile
            ?: error("res/xml must have a parent resource directory")
        val localeDirs = resRoot.listFiles { file ->
            file.isDirectory && (file.name == "values" || file.name.startsWith("values-"))
        } ?: emptyArray()
        localeDirs.forEach { dir ->
            val strings = File(dir, "strings.xml")
            if (!strings.isFile) return@forEach
            val source = strings.readText()
            required.forEach { key ->
                assertTrue("${dir.name}/strings.xml must define $key", Regex("name=\\\"$key\\\"").containsMatchIn(source))
            }
        }
    }

    /** vivo 互斥规则——`vivo_widget` 三件套声明把组件标成 OriginOS 原子组件，
     *  而原子组件是 vivo 平台审核制（上架组件平台 + 审核 7-10 工作日 + 发版二次
     *  审核）。Sleepy 从未申请审核（docs/widget-vendor-specs/vivo/atomic-widget-dev-notes.md:
     *  "Sleepy 走通道 1，不做通道 2 伪装"），未审核声明会把组件从智慧桌面标准
     *  AppWidget 池摘走 → 无法添加/编辑/删除（2026-10-26 vivo 群反馈，同 2026-09-29
     *  小米 miuiWidget 摘除案 #65 的镜像问题）。永久走标准 Android Widget 通路。 */
    @Test
    fun `no widget receiver declares unaudited vivo atomic component metadata`() {
        val vivoKeys = setOf("vivo_widget", "vivoWidgetVersion", "vivo.widget.description")
        val receivers = manifest.getElementsByTagName("receiver")
        for (i in 0 until receivers.length) {
            val receiver = receivers.item(i) as Element
            val providerMeta = run {
                val metas = receiver.getElementsByTagName("meta-data")
                var provider: Element? = null
                for (j in 0 until metas.length) {
                    val meta = metas.item(j) as Element
                    if (meta.getAttribute("android:name") == "android.appwidget.provider") {
                        provider = meta
                        break
                    }
                }
                provider
            } ?: continue
            val receiverName = receiver.getAttribute("android:name")
            val metas = receiver.getElementsByTagName("meta-data")
            for (j in 0 until metas.length) {
                val meta = metas.item(j) as Element
                assertFalse(
                    "$receiverName must NOT declare vivo atomic-component metadata (unaudited claim pulls the widget off OriginOS's standard AppWidget pool)",
                    meta.getAttribute("android:name") in vivoKeys
                )
            }
            assertTrue("$receiverName provider metadata must remain present", providerMeta.getAttribute("android:resource").isNotEmpty())
        }
    }

    /** 小米互斥规则——进入小米小部件中心的组件不会同时出现在安卓原生组件池里；
     *  未发布或审核中的组件也不会在线上小部件中心展示。Sleepy 不走小米开放平台
     *  审核, 一旦声明 miuiWidgetVersion 就会被从小米原生组件池摘除且小米中心
     *  也不收录 = 两头落空 (2026-09-29 用户拍板 A 案: 永久走标准 Android Widget 通路)。 */
    @Test
    fun `application does NOT declare unaudited miuiWidgetVersion`() {
        val application = manifest.getElementsByTagName("application").item(0) as Element
        val metas = application.getElementsByTagName("meta-data")
        for (i in 0 until metas.length) {
            val meta = metas.item(i) as Element
            assertFalse(
                "application must NOT declare miuiWidgetVersion (Xiaomi mutual exclusion rule; would pull the widget off Android's native pool without Xiaomi approval)",
                meta.getAttribute("android:name") == "miuiWidgetVersion"
            )
        }
    }

    /** 同互斥规则的回归锁——任何 widget receiver 都不得声明小米三件套
     *  (miuiWidget/miuiWidgetRefresh/miuiWidgetRefreshMinInterval) 也不得注册
     *  miui.appwidget.action.APPWIDGET_UPDATE。 */
    @Test
    fun `no widget receiver declares unaudited Xiaomi widget metadata or refresh action`() {
        val receiverNodes = (0 until manifest.getElementsByTagName("receiver").length)
            .map { manifest.getElementsByTagName("receiver").item(it) as Element }
        for (receiver in receiverNodes) {
            val fqcn = receiver.getAttribute("android:name")
            val metas = receiver.getElementsByTagName("meta-data")
            for (i in 0 until metas.length) {
                val name = (metas.item(i) as Element).getAttribute("android:name")
                assertFalse(
                    "$fqcn must NOT declare $name (Xiaomi mutual exclusion rule)",
                    name.startsWith("miui")
                )
            }
            val actions = receiver.getElementsByTagName("action")
            for (i in 0 until actions.length) {
                val actionName = (actions.item(i) as Element).getAttribute("android:name")
                assertFalse(
                    "$fqcn must NOT register $actionName (Xiaomi mutual exclusion rule)",
                    actionName.startsWith("miui.")
                )
            }
        }
    }

    /** 初始位图根节点回归锁: ImageView @+id/widget_bitmap (v1.0.56 形态)。
     *  历史: v1.0.57 f4a377cb 改用 FrameLayout(@android:id/background +
     *  splash_background) 试图兼容小米 AppVault, 后续 53e8ca1f 摘掉小米
     *  全部声明后该根节点已无存在理由; 2026-10-03 拖放崩溃排查顺手
     *  回到裸 ImageView, 减少 OEM 启动器对根布局的解释路径。 */
    @Test
    fun `bitmap widget container is a bare ImageView (v1 0 56 shape)`() {
        val file = sequenceOf(
            File("app/src/main/res/layout/widget_bitmap_container.xml"),
            File("src/main/res/layout/widget_bitmap_container.xml")
        ).first { it.isFile }
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        assertEquals("ImageView", root.tagName)
        assertEquals("@+id/widget_bitmap", root.getAttribute("android:id"))
        assertEquals("match_parent", root.getAttribute("android:layout_width"))
        assertEquals("match_parent", root.getAttribute("android:layout_height"))
    }

    /** previewLayout 回归锁 (v1.0.56 形态): 全部 13 info XML 必须保留 previewLayout。
     *  历史: v1.0.58 范围 PR#79 2554919f 摘除 previewLayout 后, Android 15+
     *  OEM 选取器(华为 EMUI/ColorOS 拖拽预览走 API35 setWidgetPreview 路径)
     *  拖拽预览变透明 + 放置崩 (用户 Mate30 实锤)。回退后 v1.0.56 previewLayout
     *  作为静态回退链必须重新存在; 锁 previewLayout 为必填属性。 */
    @Test
    fun `every info xml retains previewLayout as static fallback`() {
        infoXmls.forEach { (name, root) ->
            val preview = root.getAttribute("android:previewLayout")
            assertTrue(
                "$name must keep android:previewLayout (rollback guard for 2026-10-03 drag-to-place crash), got \"$preview\"",
                preview.startsWith("@layout/")
            )
        }
    }

    /**
     * 添加组件时不得弹出强制配置页(白屏闪烁)。
     * 所有现有变体首屏已自动绑定默认课表,无内容可让用户在首次添加时填写;
     * 长按 → 编辑入口仍走应用内 WidgetEditScreen。所以这里强制不允许
     * 任何 info XML 声明 `android:configure`。
     */
    @Test
    fun `no info xml declares android configure - add-to-home must be transparent`() {
        infoXmls.forEach { (name, root) ->
            assertEquals(
                "$name must NOT declare android:configure " +
                    "(it pops a white configure activity on add; the in-app " +
                    "WidgetEditScreen handles reconfigure instead)",
                "",
                root.getAttribute("android:configure")
            )
        }
    }

    /** R4: reconfigurable flag 是启动器显示长按"编辑"菜单的前提 */
    @Test
    fun `every info xml declares widgetFeatures reconfigurable`() {
        infoXmls.forEach { (name, root) ->
            val features = root.getAttribute("android:widgetFeatures")
            assertTrue(
                "$name must declare android:widgetFeatures containing reconfigurable, got \"$features\"",
                features.split('|', ',', ' ').contains("reconfigurable")
            )
        }
    }

    /**
     * 纯桌面类别回归锁: 全部变体只声明 home_screen (v1.0.56 形态)。
     * 历史: v1.0.57 曾扩 home_screen|keyguard (ce9f91e5), 部分厂商启动器
     * (ColorOS/EMUI) 在拖拽落位阶段对 keyguard 类别无法建立稳定占位 →
     * 拖拽无实体 + 手松开启动器崩溃 (2026-10-03 回退定法, 先例
     * fix/widget-launcher-placement 6fbd16da)。恢复 keyguard 前必须先真机
     * 拖放全链验证。
     */
    @Test
    fun `every info xml declares home_screen only - no keyguard`() {
        infoXmls.forEach { (name, root) ->
            val category = root.getAttribute("android:widgetCategory")
            val parts = category.split('|', ',', ' ').filter { it.isNotBlank() }
            assertEquals(
                "$name must declare widgetCategory exactly home_screen (keyguard breaks OEM drag-to-place; see 2026-10-03 rollback), got \"$category\"",
                listOf("home_screen"),
                parts
            )
        }
    }

    /** 全部 13 个 info XML 都在; 防止新变体漏建 xml */
    @Test
    fun `info xml count matches ALL_WIDGET_VARIANTS`() {
        assertEquals(ALL_WIDGET_VARIANTS.size, infoXmls.size)
    }

    // ---- 设计 §7/§13.3: 三档放置值精确 + minResize 全 40×40 + resizeMode ----

    /** receiver 短类名 → info XML 资源键 (manifest 单一事实来源) */
    private val keyByReceiver: Map<String, String> by lazy {
        manifestReceivers.mapKeys { it.key.substringAfterLast('.') }
    }

    private val tierByReceiver = mapOf(
        // S 档 110×110 targetCell 2×2
        "TodaySmallWidgetReceiver" to Triple("110dp", "110dp", "2x2"),
        "TwoDaySmallWidgetReceiver" to Triple("110dp", "110dp", "2x2"),
        "WeekListSmallWidgetReceiver" to Triple("110dp", "110dp", "2x2"),
        "WeekViewSmallWidgetReceiver" to Triple("110dp", "110dp", "2x2"),
        "WeekGridSmallWidgetProvider" to Triple("110dp", "110dp", "2x2"),
        // M 档 300×160 targetCell 4×2
        "TodayWideWidgetReceiver" to Triple("300dp", "160dp", "4x2"),
        "TwoDayWideWidgetReceiver" to Triple("300dp", "160dp", "4x2"),
        "WeekListWideWidgetReceiver" to Triple("300dp", "160dp", "4x2"),
        // L 档 300×250 targetCell 4×4
        "TodayWidgetReceiver" to Triple("300dp", "250dp", "4x4"),
        "TwoDayWidgetReceiver" to Triple("300dp", "250dp", "4x4"),
        "WeekListWidgetReceiver" to Triple("300dp", "250dp", "4x4"),
        "WeekViewWidgetReceiver" to Triple("300dp", "250dp", "4x4"),
        "WeekGridWidgetProvider" to Triple("300dp", "250dp", "4x4")
    )

    @Test
    fun `every receiver has a tier entry and vice versa`() {
        assertEquals(tierByReceiver.keys, keyByReceiver.keys)
    }

    @Test
    fun `placement sizes match the three tiers exactly`() {
        tierByReceiver.forEach { (receiver, tier) ->
            val (minW, minH, cell) = tier
            val (cellW, cellH) = cell.split('x')
            val root = infoXmls.getValue(keyByReceiver.getValue(receiver))
            assertEquals("$receiver minWidth", minW, root.getAttribute("android:minWidth"))
            assertEquals("$receiver minHeight", minH, root.getAttribute("android:minHeight"))
            assertEquals("$receiver targetCellWidth", cellW, root.getAttribute("android:targetCellWidth"))
            assertEquals("$receiver targetCellHeight", cellH, root.getAttribute("android:targetCellHeight"))
        }
    }

    @Test
    fun `all 13 variants unlock resize both ends with minResize 40x40`() {
        // 用户 2026-09-14 硬要求: 拖拽两端全开放 — minResize 统一 40×40, 不锁纵向
        infoXmls.forEach { (name, root) ->
            assertEquals("$name minResizeWidth", "40dp", root.getAttribute("android:minResizeWidth"))
            assertEquals("$name minResizeHeight", "40dp", root.getAttribute("android:minResizeHeight"))
            assertEquals("$name resizeMode", "horizontal|vertical", root.getAttribute("android:resizeMode"))
        }
    }

    @Test
    fun `M tier previews exist in both drawable dirs with wide aspect`() {
        // 评审 #20: 预览图 drawable 与 drawable-nodpi 两处都要有, M 档 2.7:1
        val base = sequenceOf(
            File("app/src/main/res"), File("src/main/res"), File("../app/src/main/res")
        ).first { File(it, "drawable").isDirectory }
        for (fam in listOf("today", "twoday", "weeklist")) {
            for (d in listOf("drawable", "drawable-nodpi")) {
                val f = File(base, "$d/widget_preview_${fam}_wide.png")
                assertTrue("missing ${f.path}", f.isFile && f.length() > 0)
            }
        }
    }
}
