package com.lingion.sleepy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 启动图标主题图层(monochrome)契约 —— PR #238。
 *
 * 纯 JVM 读源头文件断言(先例: [com.lingion.sleepy.widget.WidgetInfoXmlContractTest],
 * 仓库无 Robolectric,声明式 XML 读源等价于读打包产物)。
 *
 * 锁死三件事:
 * 1. 两个 adaptive-icon(ic_launcher / ic_launcher_round)都声明 monochrome 层 —— Android 13+
 *    主题图标与 Android 16 QPR2+ 自动重着色依赖该层,缺行即回退系统自动推导形状;
 * 2. monochrome vector 必须是单色纯不透明填充(#FFFFFF 全等) —— 启动器按主题色着色时读取的是
 *    形状 alpha,任何非纯白/带透明度/渐变都会污染着色(官方 adaptive icons 文档);
 * 3. 17 枚胶囊几何与品牌源 public/favicon.svg(iOS AppIcon 比例版)逐 rect 一致(容差 0.02)
 *    —— 防止后续有人"顺手美化"图标把波形改样。favicon 期望表与仓库外真源同步:
 *    sleepy-site 侧 favicon 若再变,本表必须同批改。
 *
 * 形状安全区:胶囊 bbox 55.4% × 59.7% ≤ 61.1%(66dp/108dp 单目标蒙版安全区),
 * 断言 3 的坐标表同时锁住这一比例(越界即形状被裁)。
 */
class LauncherIconMonochromeContractTest {

    private val mainRes: File by lazy {
        sequenceOf(File("app/src/main/res"), File("src/main/res")).first { it.isDirectory }
    }

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

    /** vector <path> 节点列表(android:pathData / android:fillColor 属性名带前缀,DOM 属性名保留原样)。 */
    private val monochromePaths: List<Element> by lazy {
        val vec = parse(File(mainRes, "drawable/ic_launcher_monochrome.xml"))
        assertEquals("monochrome vector 必须是 512 viewport(与 favicon 同坐标系)", "512",
            vec.getAttribute("android:viewportWidth"))
        assertEquals("512", vec.getAttribute("android:viewportHeight"))
        vec.getElementsByTagName("path").let { n -> (0 until n.length).map { n.item(it) as Element } }
    }

    @Test
    fun bothAdaptiveIconsDeclareMonochromeLayer() {
        for (name in listOf("ic_launcher", "ic_launcher_round")) {
            val root = parse(File(mainRes, "mipmap-anydpi-v26/$name.xml"))
            assertEquals("adaptive-icon", root.tagName)
            val layers = root.getElementsByTagName("monochrome")
            assertEquals("$name 必须恰有 1 个 monochrome 层", 1, layers.length)
            val drawable = (layers.item(0) as Element).getAttribute("android:drawable")
            assertEquals("$name 的 monochrome 必须指向主题图层",
                "@drawable/ic_launcher_monochrome", drawable)
            // background/foreground 不许被顺手删(PNG 前景在 2026-10-26 提交里丢过一次,锁回)
            assertEquals(1, root.getElementsByTagName("background").length)
            assertEquals(1, root.getElementsByTagName("foreground").length)
        }
    }

    @Test
    fun monochromeIsSingleColorOpaque() {
        val fills = monochromePaths.map { it.getAttribute("android:fillColor") }.distinct()
        assertEquals("单色契约:全部 path 共用一种填充", listOf("#FFFFFF"), fills)
        monochromePaths.forEach {
            val alpha = it.getAttribute("android:fillAlpha")
            assertTrue("禁 fillAlpha(主题着色读 alpha 形状)", alpha.isEmpty() || alpha == "1.0")
            assertTrue("禁 stroke 渐变类属性", it.getAttribute("android:strokeColor").isEmpty())
        }
    }

    /**
     * pathData 文法(由 favicon rect 逐胶囊转写生成):
     *   M(cx, top) h(w-2rx) a rx… v(h-2rx) a … z
     * 即 M 点 = 顶边左弧圆心,(cx-rx, top) 为 rect 左上角。
     */
    private val pathRectRegex = Regex(
        """^M([-\d.]+),([-\d.]+)h([-\d.]+)a([-\d.]+),([-\d.]+)[^v]*v([-\d.]+)""")

    private data class Capsule(val x: Double, val y: Double, val w: Double, val h: Double)

    private val actualCapsules: List<Capsule> by lazy {
        monochromePaths.map { p ->
            val m = pathRectRegex.find(p.getAttribute("android:pathData"))
                ?: error("pathData 不符胶囊文法: ${p.getAttribute("android:pathData").take(60)}")
            val (cx, top, halfDelta, rx, _, straightV) = m.destructured
            Capsule(
                x = cx.toDouble() - rx.toDouble(),
                y = top.toDouble(),
                w = kotlin.math.abs(halfDelta.toDouble()) + 2 * rx.toDouble(),
                h = kotlin.math.abs(straightV.toDouble()) + 2 * rx.toDouble(),
            )
        }
    }

    /** 期望表 = sleepy-site public/favicon.svg 的 17 枚 rect(x y w h,rx=13.23 或 12.49),逐字照抄。 */
    private val faviconCapsules = listOf(
        Capsule(114.18, 153.86, 26.45, 86.71),
        Capsule(114.18, 314.05, 26.45, 44.09),
        Capsule(156.80, 128.14, 26.45, 145.49),
        Capsule(156.80, 322.13, 26.45, 52.91),
        Capsule(200.16, 111.24, 25.72, 69.81),
        Capsule(200.16, 229.55, 25.72, 86.71),
        Capsule(200.16, 364.75, 25.72, 36.01),
        Capsule(242.77, 103.16, 26.45, 69.81),
        Capsule(242.77, 220.73, 26.45, 70.54),
        Capsule(242.77, 339.03, 26.45, 69.81),
        Capsule(286.13, 111.24, 25.72, 36.01),
        Capsule(286.13, 195.75, 25.72, 86.71),
        Capsule(286.13, 330.95, 25.72, 69.81),
        Capsule(328.75, 136.96, 25.72, 52.91),
        Capsule(372.10, 153.86, 25.72, 44.09),
        Capsule(372.10, 271.43, 25.72, 86.71),
        Capsule(328.75, 238.36, 25.72, 144.76),
    )

    @Test
    fun capsulesMatchFaviconGeometryAndSafeZone() {
        assertEquals("胶囊数必须 = favicon 17 枚", 17, actualCapsules.size)
        val d = 0.02 // vector 转写圆角取整容差
        faviconCapsules.forEachIndexed { i, exp ->
            val a = actualCapsules[i]
            assertEquals("胶囊$i x", exp.x, a.x, d)
            assertEquals("胶囊$i y", exp.y, a.y, d)
            assertEquals("胶囊$i w", exp.w, a.w, d)
            assertEquals("胶囊$i h", exp.h, a.h, d)
        }
        // 安全区:bbox 占比 ≤ 66dp/108dp(Google 单目标蒙版),超限即任何掩膜形状被裁
        val minX = actualCapsules.minOf { it.x }
        val maxX = actualCapsules.maxOf { it.x + it.w }
        val minY = actualCapsules.minOf { it.y }
        val maxY = actualCapsules.maxOf { it.y + it.h }
        val safe = 66.0 / 108.0
        assertTrue("宽占比 ${(maxX - minX) / 512} 超 66dp 安全区", (maxX - minX) / 512.0 <= safe)
        assertTrue("高占比 ${(maxY - minY) / 512} 超 66dp 安全区", (maxY - minY) / 512.0 <= safe)
        // 居中(允许 1% 偏移):形状歪了=转写错位
        assertEquals("水平居中", 256.0, (minX + maxX) / 2, 5.12)
        assertEquals("垂直居中", 256.0, (minY + maxY) / 2, 5.12)
    }

    @Test
    fun debugSourceSetSharesMainAdaptiveIcon() {
        // debug overlay 只有 PNG/background —— anydpi-v26 XML 单一真源在 main,
        // debug 包自动继承 monochrome;若 debug 侧新加同名 XML 会遮蔽本契约,必须在此失败提醒。
        val debugAdaptive = File("app/src/debug/res/mipmap-anydpi-v26")
        assertTrue("debug 源集禁复制 adaptive-icon XML(单源在 main)", !debugAdaptive.isDirectory)
    }
}
