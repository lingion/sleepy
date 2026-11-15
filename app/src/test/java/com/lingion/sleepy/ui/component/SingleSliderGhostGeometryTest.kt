package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import com.lingion.sleepy.ui.component.liquidglass.GlassSlider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Render Material sliders and their original-value markers, comparing pixel centers.
 * The 12px row crosses the 44dp Material handle above its 16dp rail at density 2.
 * Cases retain continuous/discrete values, endpoints and off-center values.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SingleSliderGhostGeometryTest {

    @get:Rule val compose = createComposeRule()

    private data class Case(
        val name: String,
        val value: Float,
        val start: Float,
        val end: Float,
        val steps: Int,
    )

    private fun caseOf(name: String) = cases.first { it.name == name }

    private val cases = listOf(
        Case("cont-lo", 4f, 4f, 20f, 0),
        Case("cont-mid", 12f, 4f, 20f, 0),
        Case("cont-hi", 20f, 4f, 20f, 0),
        Case("step-lo", -1f, -1f, 1f, 19),
        Case("step-q", -0.6f, -1f, 1f, 19),
        Case("step-mid", 0f, -1f, 1f, 19),
        Case("step-hi", 1f, -1f, 1f, 19),
        Case("s11-mid", 1.0f, 0.7f, 1.3f, 11),
    )

    /** 截行取证: ROW_BG=1 在 thumb 椭圆顶端 (y=24) 之上, 全行纯背景 — 拿它当底,
     *  ROW_Y 上与底不同的列即唯一墨迹 (thumb / 幽灵椭圆)。端点案例 thumb 贴列 0,
     *  禁直接拿 (0,ROW_Y) 当背景。 */
    private fun inkColumns(tag: String): List<Int> {
        compose.waitForIdle()
        val bmp = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val bg = mostCommonRowColor(bmp, ROW_BG)
        return (0 until bmp.width).filter { x -> bmp.getPixel(x, ROW_Y) != bg }
    }

    /** 参考行众数色 = 页面底色, 避免端点处的局部墨迹影响背景判定。 */
    private fun mostCommonRowColor(bmp: android.graphics.Bitmap, y: Int): Int {
        val counts = HashMap<Int, Int>()
        for (x in 0 until bmp.width) {
            val c = bmp.getPixel(x, y)
            counts[c] = (counts[c] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }!!.key
    }

    private fun centerOf(columns: List<Int>, what: String): Float {
        check(columns.isNotEmpty()) { "$what 未渲染出像素 (几何变了?)" }
        return (columns.first() + columns.last()) / 2f
    }

    private fun renderSlider(c: Case) {
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(modifier = Modifier.size(width = 360.dp, height = 48.dp).testTag("box-${c.name}")) {
                    GlassSlider(
                        value = c.value, steps = c.steps, valueRange = c.start..c.end,
                        onValueChange = {}, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    private fun renderGhost(c: Case) {
        val frac = (c.value - c.start) / (c.end - c.start)
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(modifier = Modifier.size(width = 360.dp, height = 48.dp).testTag("gbox-${c.name}")) {
                    SingleSliderGhostMark(baselineFrac = frac, steps = c.steps)
                }
            }
        }
    }

    private fun assertCoincide(c: Case) {
        renderGhost(c)
        val ghostCenter = centerOf(inkColumns("gbox-${c.name}"), "案例 ${c.name} 幽灵椭圆")
        val thumbCenter = checkNotNull(thumbCenters[c.name]) { "a 相未记录 ${c.name}" }
        val d = kotlin.math.abs(ghostCenter - thumbCenter)
        assertTrue(
            "案例 ${c.name}: 幽灵中心 $ghostCenter 与真 thumb 中心 $thumbCenter 偏离 ${d}px (上限 2px)",
            d <= 2f,
        )
    }

    @Test fun a0contLoThumb() { val c = caseOf("cont-lo"); renderSlider(c); thumbCenters[c.name] = centerOf(inkColumns("box-${c.name}"), "案例 ${c.name} thumb") }
    @Test fun b0contLoGhost() = assertCoincide(caseOf("cont-lo"))

    @Test fun a1contMidThumb() { val c = caseOf("cont-mid"); renderSlider(c); thumbCenters[c.name] = centerOf(inkColumns("box-${c.name}"), "案例 ${c.name} thumb") }
    @Test fun b1contMidGhost() = assertCoincide(caseOf("cont-mid"))

    @Test fun a2contHiThumb() { val c = caseOf("cont-hi"); renderSlider(c); thumbCenters[c.name] = centerOf(inkColumns("box-${c.name}"), "案例 ${c.name} thumb") }
    @Test fun b2contHiGhost() = assertCoincide(caseOf("cont-hi"))

    @Test fun a3stepLoThumb() { val c = caseOf("step-lo"); renderSlider(c); thumbCenters[c.name] = centerOf(inkColumns("box-${c.name}"), "案例 ${c.name} thumb") }
    @Test fun b3stepLoGhost() = assertCoincide(caseOf("step-lo"))

    @Test fun a4stepQThumb() { val c = caseOf("step-q"); renderSlider(c); thumbCenters[c.name] = centerOf(inkColumns("box-${c.name}"), "案例 ${c.name} thumb") }
    @Test fun b4stepQGhost() = assertCoincide(caseOf("step-q"))

    @Test fun a5stepMidThumb() { val c = caseOf("step-mid"); renderSlider(c); thumbCenters[c.name] = centerOf(inkColumns("box-${c.name}"), "案例 ${c.name} thumb") }
    @Test fun b5stepMidGhost() = assertCoincide(caseOf("step-mid"))

    @Test fun a6stepHiThumb() { val c = caseOf("step-hi"); renderSlider(c); thumbCenters[c.name] = centerOf(inkColumns("box-${c.name}"), "案例 ${c.name} thumb") }
    @Test fun b6stepHiGhost() = assertCoincide(caseOf("step-hi"))

    @Test fun a7s11MidThumb() { val c = caseOf("s11-mid"); renderSlider(c); thumbCenters[c.name] = centerOf(inkColumns("box-${c.name}"), "案例 ${c.name} thumb") }
    @Test fun b7s11MidGhost() = assertCoincide(caseOf("s11-mid"))

    companion object {
        /** Material handle top is 4px; the default rail starts at 32px. */
        private const val ROW_Y = 12
        /** 背景参考行 y: thumb 椭圆顶端之上, 全行无墨。 */
        private const val ROW_BG = 1
        val thumbCenters = mutableMapOf<String, Float>()
    }
}
