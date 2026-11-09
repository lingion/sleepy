package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.*
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassFreeDragRenderTest {
    @get:Rule val compose = createComposeRule()
    private fun advance(ms: Int) {
        repeat(ms / 16) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
    }

    private fun verify(name: String, direction: Offset, long: Boolean = false) {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.fillMaxSize()) {
                        GlassBackground(0f, Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(Color(0xFFF3F7F8)).drawBehind {
                                val step = 8.dp.toPx()
                                for (i in 0..(size.width / step).toInt()) {
                                    drawRect(if (i % 2 == 0) Color(0xFF85BCDF) else Color(0xFFFFDBA8), Offset(i * step, 0f), Size(step, size.height))
                                }
                            })
                        }
                        Box(Modifier.padding(100.dp)) {
                            if (long) GlassButton({ clicks++ }, Modifier.testTag("target")) {
                                Text("Action", Modifier.testTag("pane"))
                            } else GlassIconButton({ clicks++ }, Modifier.testTag("target")) {
                                Box(Modifier.fillMaxSize().testTag("pane"), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("+") }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        fun save(phase: String) {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "glass-free-drag").apply { mkdirs() }
            File(directory, "$name-$phase.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val target = compose.onNodeWithTag("target")
        val pane = compose.onNodeWithTag("pane", true)
        val resting = pane.fetchSemanticsNode().boundsInRoot
        save("rest")
        compose.mainClock.autoAdvance = false
        target.performTouchInput { down(center) }
        advance(480)
        save("raised")
        target.performTouchInput { moveBy(direction, 200) }
        advance(480)
        val dragged = pane.fetchSemanticsNode().boundsInRoot
        if (direction.x != 0f) assertTrue((dragged.center.x - resting.center.x) * direction.x > 0f)
        if (direction.y != 0f) assertTrue((dragged.center.y - resting.center.y) * direction.y > 0f)
        assertTrue("held pane remains raised", dragged.width > resting.width)
        save("dragged")
        target.performTouchInput { cancel() }
        advance(1500)
        val released = pane.fetchSemanticsNode().boundsInRoot
        assertEquals(resting.center.x, released.center.x, 1f)
        assertEquals(resting.center.y, released.center.y, 1f)
        assertEquals(resting.width, released.width, 1f)
        assertEquals(0, clicks)
        save("released")
        compose.mainClock.autoAdvance = true
    }

    @Test fun right() = verify("right", Offset(180f, 0f))
    @Test fun left() = verify("left", Offset(-180f, 0f))
    @Test fun up() = verify("up", Offset(0f, -180f))
    @Test fun down() = verify("down", Offset(0f, 180f))
    @Test fun longActionDiagonal() = verify("long-diagonal", Offset(180f, 180f), long = true)
}
