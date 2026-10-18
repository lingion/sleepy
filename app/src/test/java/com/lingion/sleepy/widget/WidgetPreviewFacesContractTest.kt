package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 略缩图双显示位同源契约 — 启动器预览页(previewImage/previewLayout)与应用内
 * 添加画廊(WidgetVariantInfo.previewImageRes)必须显示同一张脸,且脸必须
 * 与真实 widget 的声明尺寸等比例(官方规范 discovery-promotion:
 * "Provide quality previews that are accurate to the widget size")。
 *
 * 锁四件事:
 * 1. 13 个 info XML 的 android:previewLayout 指向专属布局 @layout/widget_preview_<X>,
 *    该布局根为 ImageView 且 android:src == previewImage 同一张 drawable
 *    (修空白脸: previewLayout 优先级高于 previewImage,此前全 13 个指向无 src
 *    的空容器 → Android 12+ 选择器/拖拽预览显示空白);
 * 2. 每张 widget_preview_<X>.png 同时存在于 drawable/ 与 drawable-nodpi/ 且字节一致
 *    (两目录不一致 = 两显示位可能拿到不同的脸);
 * 3. PNG 像素宽高比 == info XML minWidth/minHeight 声明比例(±2%),
 *    且 == 专属预览布局的 contentDescription 所在 label 家族(经 X 名对齐);
 * 4. WidgetVariantInfo.kt 注册表里 previewImageRes 引用的 drawable 名与
 *    13 个 info XML 的 previewImage 名一一对应(画廊卡片 == 启动器缩略图)。
 *
 * 纯 JVM 读源头文件断言,同 [WidgetInfoXmlContractTest] 先例(仓库无 Robolectric)。
 */
class WidgetPreviewFacesContractTest {

    private val resDir: File by lazy {
        sequenceOf(
            File("app/src/main/res"),
            File("src/main/res"),
            File("../app/src/main/res")
        ).first { File(it, "xml").isDirectory }
    }

    private fun dpToNumber(dp: String): Int = dp.removeSuffix("dp").trim().toInt()

    /** (info XML 键, 根元素, previewImage 的 drawable 名 X) 三元组, 全 13 个。 */
    private data class InfoEntry(val xmlName: String, val root: Element, val drawableKey: String)

    private val entries: List<InfoEntry> by lazy {
        resDir.resolve("xml").listFiles { f -> f.name.endsWith("_widget_info.xml") }!!
            .map { f ->
                val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(f).documentElement
                val drawable = root.getAttribute("android:previewImage")
                assertTrue(
                    "${f.name} previewImage must be @drawable/widget_preview_<X>",
                    drawable.startsWith("@drawable/widget_preview_")
                )
                InfoEntry(
                    f.name.removeSuffix(".xml"),
                    root,
                    drawable.removePrefix("@drawable/widget_preview_")
                )
            }
            .sortedBy { it.xmlName }
    }

    @Test
    fun `there are exactly 13 widget info xmls`() {
        assertEquals(13, entries.size)
    }

    /** ① previewLayout 指向带静态 src 的专属预览布局, src 与 previewImage 同源。 */
    @Test
    fun `each info xml previewLayout points at its own static-faced preview layout`() {
        entries.forEach { e ->
            val layoutAttr = e.root.getAttribute("android:previewLayout")
            assertEquals(
                "${e.xmlName} previewLayout must be its dedicated layout",
                "@layout/widget_preview_${e.drawableKey}", layoutAttr
            )
            val layoutFile = resDir.resolve("layout/widget_preview_${e.drawableKey}.xml")
            assertTrue("missing ${layoutFile.path}", layoutFile.isFile)
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(layoutFile).documentElement
            assertEquals("ImageView", root.tagName)
            assertEquals(
                "${layoutFile.name} src must equal info xml previewImage",
                "@drawable/widget_preview_${e.drawableKey}",
                root.getAttribute("android:src")
            )
            assertEquals("match_parent", root.getAttribute("android:layout_width"))
            assertEquals("match_parent", root.getAttribute("android:layout_height"))
        }
    }

    /** initialLayout 必须仍是运行时空容器 — 预览布局不能混进运行时通路。 */
    @Test
    fun `initialLayout stays the runtime bitmap container`() {
        entries.forEach { e ->
            assertEquals(
                "${e.xmlName} initialLayout",
                "@layout/widget_bitmap_container",
                e.root.getAttribute("android:initialLayout")
            )
        }
    }

    /** ②+③ PNG 双目录同字节存在, 像素宽高比 == 声明 minWidth×minHeight(±2%),
     *  且出图密度 >= 3.5px/dp(用户验收「分辨率调高」定型, 防 D 回退悄悄变糊)。 */
    @Test
    fun `every preview png exists in both drawable dirs byte-identical matches declared aspect and min density`() {
        entries.forEach { e ->
            val mdpi = resDir.resolve("drawable/widget_preview_${e.drawableKey}.png")
            val nodpi = resDir.resolve("drawable-nodpi/widget_preview_${e.drawableKey}.png")
            assertTrue("missing ${mdpi.path}", mdpi.isFile && mdpi.length() > 0)
            assertTrue("missing ${nodpi.path}", nodpi.isFile && nodpi.length() > 0)
            assertTrue(
                "${e.drawableKey}: drawable/ 与 drawable-nodpi/ 字节必须一致(两显示位同一张脸)",
                mdpi.readBytes().contentEquals(nodpi.readBytes())
            )
            // PNG IHDR: 宽@16 高@20 (big-endian)
            val bytes = nodpi.readBytes()
            val w = ((bytes[16].toInt() and 0xFF shl 24) or (bytes[17].toInt() and 0xFF shl 16) or
                (bytes[18].toInt() and 0xFF shl 8) or (bytes[19].toInt() and 0xFF))
            val h = ((bytes[20].toInt() and 0xFF shl 24) or (bytes[21].toInt() and 0xFF shl 16) or
                (bytes[22].toInt() and 0xFF shl 8) or (bytes[23].toInt() and 0xFF))
            val declaredW = dpToNumber(e.root.getAttribute("android:minWidth"))
            val declaredH = dpToNumber(e.root.getAttribute("android:minHeight"))
            val declaredRatio = declaredW.toDouble() / declaredH
            val actualRatio = w.toDouble() / h
            val drift = kotlin.math.abs(actualRatio - declaredRatio) / declaredRatio
            assertTrue(
                "${e.drawableKey}: 像素比例 ${w}x$h=${"%.3f".format(actualRatio)} " +
                    "偏离声明 ${declaredW}x${declaredH}dp=${"%.3f".format(declaredRatio)} 超 2%",
                drift <= 0.02
            )
            // 分辨率下限: 出图密度 D = px/dp 必须 >= 3.5 (生成器 D=4.0, 留 rounding 裕量)。
            // 2026-10-18 用户验收「分辨率调高一点」定型; D 改回 <=3 即红。
            val density = w.toDouble() / declaredW
            assertTrue(
                "${e.drawableKey}: 出图密度 ${"%.2f".format(density)}px/dp < 3.5, 略缩图会糊",
                density >= 3.5
            )
        }
    }

    /** ④ 应用内画廊注册表引用的 drawable 名 == 13 个 info XML 的 previewImage 名。 */
    @Test
    fun `in-app gallery registry references exactly the launcher preview drawables`() {
        val registry = Regex("""R\.drawable\.widget_preview_(\w+)""")
            .findAll(registrySource()).map { it.groupValues[1] }.toSet()
        val xmlKeys = entries.map { it.drawableKey }.toSet()
        assertEquals("info XML previewImage 名必须 13 个互异", 13, xmlKeys.size)
        assertEquals(
            "画廊 previewImageRes 与启动器 previewImage 必须一一对应(同一组脸)",
            xmlKeys, registry
        )
    }

    /** ⑤ 注册表声明尺寸 == info XML 声明尺寸(经 Manifest receiver→meta-data 桥),
     *  且画廊卡片按各 variant 真实比例渲染 —— 禁止再硬编码统一 aspectRatio。 */
    @Test
    fun `registry declared sizes match info xml and gallery renders per-variant aspect`() {
        // receiver FQCN → info XML 键(与 WidgetInfoXmlContractTest 同逻辑)
        val manifestFile = sequenceOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        ).first { it.isFile }
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(manifestFile).documentElement
        val receivers = manifest.getElementsByTagName("receiver")
        val xmlByReceiver = mutableMapOf<String, String>()
        for (i in 0 until receivers.length) {
            val receiver = receivers.item(i) as Element
            val name = receiver.getAttribute("android:name")
            if (name.isEmpty()) continue
            val fqcn = if (name.startsWith(".")) "com.lingion.sleepy$name" else name
            val metas = receiver.getElementsByTagName("meta-data")
            for (j in 0 until metas.length) {
                val meta = metas.item(j) as Element
                if (meta.getAttribute("android:name") != "android.appwidget.provider") continue
                xmlByReceiver[fqcn] = meta.getAttribute("android:resource")
                    .removePrefix("@xml/").removeSuffix(".xml")
            }
        }
        val sizeByXml = entries.associate { e ->
            e.xmlName to Pair(
                dpToNumber(e.root.getAttribute("android:minWidth")),
                dpToNumber(e.root.getAttribute("android:minHeight"))
            )
        }
        for (v in ALL_WIDGET_VARIANTS) {
            val xmlKey = xmlByReceiver[v.receiverClass.name]
            val (w, h) = sizeByXml.getValue(xmlKey!!)
            assertEquals("${xmlKey} minWidth vs registry", w, v.widthDp)
            assertEquals("${xmlKey} minHeight vs registry", h, v.heightDp)
        }
        // 画廊源码: 卡片框比例必须来自 variant 声明尺寸, 禁统一硬编码
        val gallerySrc = sequenceOf(
            File("app/src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetGalleryScreen.kt"),
            File("src/main/java/com/lingion/sleepy/ui/screen/widget/WidgetGalleryScreen.kt")
        ).first { it.isFile }.readText()
        assertTrue(
            "画廊必须按 variant 声明比例渲染(硬编码 1.6f 会让 1.2/1.0 脸留白变形)",
            gallerySrc.contains("variant.widthDp") && gallerySrc.contains("variant.heightDp") &&
                !Regex("""aspectRatio\(\s*1\.6f""").containsMatchIn(gallerySrc)
        )
    }

    private fun registrySource(): String = sequenceOf(
        File("app/src/main/java/com/lingion/sleepy/widget/WidgetVariantInfo.kt"),
        File("src/main/java/com/lingion/sleepy/widget/WidgetVariantInfo.kt")
    ).first { it.isFile }.readText()
}
