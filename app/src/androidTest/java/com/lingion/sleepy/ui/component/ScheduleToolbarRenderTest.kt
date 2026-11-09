package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.screen.schedule.TopBar
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual GPU frames for the two width decisions and their shared glass press. */
@RunWith(AndroidJUnit4::class)
class ScheduleToolbarRenderTest {
    @get:Rule val compose = createComposeRule()
    @After fun clearHistory() { UndoManager.clear() }
    @Test fun lightFullWeek() = capture(false, false, 360, 1f, "toolbar-full-light")
    @Test fun darkFullWeek() = capture(true, false, 360, 1f, "toolbar-full-dark")
    @Test fun lightGroupedWeek() = capture(false, true, 360, 1f, "toolbar-group-light")
    @Test fun darkGroupedWeek() = capture(true, true, 360, 1f, "toolbar-group-dark")
    @Test fun narrowLargeFontAlwaysStaysOnOneRow() = capture(false, true, 280, 1.5f, "toolbar-single-row-large")
    @Test fun classicLightKeepsFilledActions() = capture(false, false, 360, 1f, "toolbar-classic-light", UiStyle.Classic)
    @Test fun classicDarkKeepsFilledActions() = capture(true, false, 360, 1f, "toolbar-classic-dark", UiStyle.Classic)
    @Test fun classicLightBusyKeepsFilledActions() = capture(false, true, 360, 1f, "toolbar-classic-busy-light", UiStyle.Classic)
    @Test fun classicDarkBusyKeepsFilledActions() = capture(true, true, 360, 1f, "toolbar-classic-busy-dark", UiStyle.Classic)

    private fun capture(dark: Boolean, busy: Boolean, width: Int, font: Float, name: String, style: UiStyle = UiStyle.LiquidGlass) {
        UndoManager.clear()
        if (busy) UndoManager.capture(emptyList(), emptyList(), null)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(
                    LocalUiStyle provides style, LocalDarkTheme provides dark,
                    LocalDensity provides Density(LocalDensity.current.density, font),
                ) {
                    GlassHost {
                        Box(Modifier.width(width.dp).height(120.dp).background(MaterialTheme.colorScheme.surface)) {
                            TopBar(2, 20, "2099-09-01", null, {}, {}, {}, busy, {}, {}, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val previous = compose.onNodeWithTag("schedule_prev_week").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("schedule_next_week").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val toolbarWidth = with(compose.density) { width.dp.toPx() }
        org.junit.Assert.assertEquals("week group is centered on the toolbar", root.left + toolbarWidth / 2f, (previous.left + next.right) / 2f, 1f)
        val tags = listOf("schedule_switch_table", "schedule_prev_week", "schedule_week_label", "schedule_next_week", "schedule_add_course", "schedule_share") +
            if (busy) listOf("schedule_undo", "schedule_redo", "schedule_scale_keep") else emptyList()
        val boxes = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        for (a in boxes.indices) for (b in a + 1 until boxes.size) {
            org.junit.Assert.assertFalse("${tags[a]} overlaps ${tags[b]}", boxes[a].overlaps(boxes[b]))
        }
        boxes.forEach { org.junit.Assert.assertEquals("every control remains on the same row", previous.center.y, it.center.y, 1f) }
        if (busy) {
            compose.onNodeWithTag("schedule_week_group").assertIsDisplayed()
            compose.onNodeWithTag("schedule_week_label").assertTextEquals("2")
        } else {
            compose.onNodeWithTag("schedule_week_group").assertDoesNotExist()
            compose.onNodeWithTag("schedule_week_label").assertTextContains("2", substring = true)
            org.junit.Assert.assertTrue(compose.onNodeWithTag("schedule_week_label").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text != "2")
        }
        save(name)
        if (busy && width == 360 && style == UiStyle.LiquidGlass) {
            compose.mainClock.autoAdvance = false
            val undo = compose.onNodeWithTag("schedule_undo")
            undo.performTouchInput { down(center) }
            try {
                repeat(30) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
                save("$name-pressed")
                undo.performTouchInput { moveBy(Offset(-60f, 30f), 200) }
                repeat(30) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
                save("$name-dragged")
            } finally {
                undo.performTouchInput { cancel() }
                compose.mainClock.autoAdvance = true
            }
        }
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = InstrumentationRegistry.getArguments().getString("glassCaptureDirectory") ?: "glass-route-matrix"
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, output).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("GLASS_TOOLBAR_CAPTURE=$name")
    }
}
