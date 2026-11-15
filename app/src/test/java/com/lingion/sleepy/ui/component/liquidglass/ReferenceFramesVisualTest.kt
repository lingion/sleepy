package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.component.liquidglass.GlassSwitch
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class ReferenceFramesVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun togglePressLensChangesPixelsAndRevealsTrackColor() {
        val checked = mutableStateOf(false)
        compose.setContent {
            androidx.compose.material3.MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.fillMaxSize(), background = Color(0xFFF0F0F2)) {
                        GlassSwitch(
                            checked = checked.value,
                            onCheckedChange = { checked.value = it },
                            modifier = Modifier.testTag("reference-toggle"),
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                uncheckedTrackColor = Color(0xFF35D653),
                                checkedTrackColor = Color(0xFF35D653),
                                uncheckedThumbColor = Color.White,
                                checkedThumbColor = Color.White,
                            ),
                        )
                    }
                }
            }
        }
        val control = compose.onNodeWithTag("reference-toggle")
        val rest = control.captureToImage().asAndroidBitmap()
        control.performTouchInput { down(Offset(centerX, centerY)) }
        compose.mainClock.advanceTimeBy(180)
        val pressed = control.captureToImage().asAndroidBitmap()
        assertTrue("press must visibly expand and refract the production toggle", changedPixels(rest, pressed) > 100)
        control.performTouchInput { up() }
    }

    @Test fun segmentedPressChangesRenderedPillAndDragMovesSelection() {
        val selected = mutableStateOf("For You")
        compose.setContent {
            androidx.compose.material3.MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.fillMaxSize(), background = Color(0xFFF0F0F2)) {
                        Box(Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF42DA5C)))
                        SegmentedSwitcher(
                            options = listOf("For You" to "For You", "Library" to "Library"),
                            selected = selected.value,
                            onSelect = { selected.value = it },
                            modifier = Modifier.testTag("reference-tab"),
                        )
                    }
                }
            }
        }
        val tab = compose.onNodeWithTag("reference-tab")
        val rest = tab.captureToImage().asAndroidBitmap()
        tab.performTouchInput { down(Offset(width * 0.25f, centerY)) }
        compose.mainClock.advanceTimeBy(180)
        val pressed = tab.captureToImage().asAndroidBitmap()
        assertTrue("press must visibly transform selected segment into glass", changedPixels(rest, pressed) > 50)
        tab.performTouchInput { moveTo(Offset(width * 0.8f, centerY), 200) }
        compose.mainClock.advanceTimeBy(200)
        val dragged = tab.captureToImage().asAndroidBitmap()
        assertTrue("drag must move the glass lens continuously with the finger", changedPixels(pressed, dragged) > 20)
        tab.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Library").assertExists()
        compose.runOnIdle { assertEquals("Library", selected.value) }
    }

    private fun changedPixels(before: android.graphics.Bitmap, after: android.graphics.Bitmap): Int {
        if (before.width != after.width || before.height != after.height) return Int.MAX_VALUE
        var changes = 0
        for (y in 0 until before.height step 2) {
            for (x in 0 until before.width step 2) {
                if (before.getPixel(x, y) != after.getPixel(x, y)) changes++
            }
        }
        return changes
    }
}
