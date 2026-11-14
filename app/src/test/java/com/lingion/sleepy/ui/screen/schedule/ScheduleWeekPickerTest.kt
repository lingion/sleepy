package com.lingion.sleepy.ui.screen.schedule

import android.view.Gravity
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassHostState
import com.lingion.sleepy.ui.component.liquidglass.createSoftwareWindowComposeRule
import com.lingion.sleepy.ui.component.liquidglass.rememberGlassHostState
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w360dp-h640dp-mdpi")
class ScheduleWeekPickerTest {
    @get:Rule val compose = createSoftwareWindowComposeRule()
    private val backing = mutableStateOf(Color.Red)
    private lateinit var host: GlassHostState

    @Test fun materialPickerUsesCenteredWindowAndSelectsWeek() = checkPicker(UiStyle.Classic)
    @Test fun glassPickerUsesCenteredWindowAndSelectsWeek() = checkPicker(UiStyle.LiquidGlass)

    @Test fun materialPickerCentersCompleteAndPartialRows() = checkRowCentering(UiStyle.Classic)
    @Test fun glassPickerCentersCompleteAndPartialRows() = checkRowCentering(UiStyle.LiquidGlass)

    @Test fun glassPickerRespondsToTheSceneBehindItsActualDialogWindow() = checkBacking(UiStyle.LiquidGlass)
    @Test fun materialPickerRetainsItsOfficialOpaqueContainer() = checkBacking(UiStyle.Classic)

    private fun show(style: UiStyle, maxWeek: Int = 60, onSelect: (Int) -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    host = rememberGlassHostState()
                    GlassHost(Modifier.fillMaxSize(), state = host, background = backing.value) {
                        GlassBackground(depth = 0f, modifier = Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(backing.value))
                        }
                        ScheduleWeekPicker(2, maxWeek, {}, onSelect)
                    }
                }
            }
        }
    }

    private fun checkPicker(style: UiStyle) {
        var selected = 0
        show(style) { selected = it }
        compose.onNodeWithTag("schedule_week_dialog").assertIsDisplayed()
        compose.onNodeWithTag("schedule_week#2").assertIsSelected()
        compose.runOnIdle {
            val window = WindowInspector.getGlobalWindowViews()
                .first { it.javaClass.simpleName == "DecorView" &&
                    it.findViewById<android.view.View>(android.R.id.content)
                        ?.let { content -> content is android.view.ViewGroup &&
                            (0 until content.childCount).any { content.getChildAt(it).javaClass.simpleName == "DialogLayout" } } == true }
            val params = window.layoutParams as WindowManager.LayoutParams
            assertEquals(Gravity.CENTER, params.gravity and (Gravity.HORIZONTAL_GRAVITY_MASK or Gravity.VERTICAL_GRAVITY_MASK))
        }
        compose.onNodeWithTag("schedule_week#60").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(60, selected) }
    }

    private fun checkRowCentering(style: UiStyle) {
        // A prime count leaves a partial final row at either dialog's width.
        val maxWeek = 17
        show(style, maxWeek)
        val grid = compose.onNodeWithTag("schedule_week_grid").fetchSemanticsNode().boundsInRoot
        val rows = (1..maxWeek).map { week ->
            compose.onNodeWithTag("schedule_week#$week").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
        }.groupBy { it.top.roundToInt() }.toSortedMap().values.toList()
        val fullRowSize = rows.maxOf { it.size }
        assertTrue("the fixture must contain a complete multi-item row", fullRowSize > 1)
        assertTrue("the fixture must contain a partial final row", rows.last().size < fullRowSize)
        rows.dropLast(1).forEach { row ->
            assertEquals("all preceding rows must be complete", fullRowSize, row.size)
        }
        rows.forEachIndexed { index, row ->
            val leftInset = row.minOf { it.left } - grid.left
            val rightInset = grid.right - row.maxOf { it.right }
            assertTrue("row $index must fit inside the grid", leftInset >= 0f && rightInset >= 0f)
            assertEquals("row $index must have equal left and right space", leftInset, rightInset, 1f)
        }
    }

    private fun checkBacking(style: UiStyle) {
        show(style)
        fun sample(): Int {
            repeat(5) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            // Robolectric's default Compose idler skips window draws. The
            // software fixture must record the real parent scene before the
            // dialog can sample it; capturing only the dialog cannot do that.
            val scene = runBlocking { host.sceneLayer.toImageBitmap() }.asAndroidBitmap()
            assertEquals("the actual parent window has drawn the requested backing",
                backing.value.toArgb(), scene.getPixel(scene.width / 2, scene.height / 2))
            val image = compose.onNodeWithTag("schedule_week_dialog").captureToImage().asAndroidBitmap()
            // The center of the bottom inset is inside the pane and outside its controls.
            return image.getPixel(image.width / 2, image.height - 8)
        }
        val redScene = sample()
        compose.runOnIdle { backing.value = Color.Blue }
        val blueScene = sample()
        if (style == UiStyle.LiquidGlass) {
            val change = kotlin.math.abs(android.graphics.Color.red(redScene) - android.graphics.Color.red(blueScene)) +
                kotlin.math.abs(android.graphics.Color.blue(redScene) - android.graphics.Color.blue(blueScene))
            assertTrue("the glass pane must sample the changing parent scene: " +
                "red=${redScene.toUInt().toString(16)}, blue=${blueScene.toUInt().toString(16)}, change=$change", change > 32)
        } else {
            assertEquals("Material keeps its default filled surface", redScene, blueScene)
        }
    }
}
