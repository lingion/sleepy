package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
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

    /** 全部 10 个 info XML 都在; 防止新变体漏建 xml */
    @Test
    fun `info xml count matches ALL_WIDGET_VARIANTS`() {
        assertEquals(ALL_WIDGET_VARIANTS.size, infoXmls.size)
    }
}
