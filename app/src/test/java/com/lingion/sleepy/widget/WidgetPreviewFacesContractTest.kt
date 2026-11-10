package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 验证启动器预览的资源引用、接收器数量与运行时容器。 */
class WidgetPreviewFacesContractTest {

    private val resDir: File by lazy {
        sequenceOf(
            File("app/src/main/res"),
            File("src/main/res"),
            File("../app/src/main/res")
        ).first { File(it, "xml").isDirectory }
    }

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
    fun `widget info xmls match the registered receiver count`() {
        assertEquals(ALL_WIDGET_VARIANTS.size, entries.size)
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

}
