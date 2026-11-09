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

/** Actual GPU frames for the three width decisions and their shared glass press. */
@RunWith(AndroidJUnit4::class)
class ScheduleToolbarRenderTest {
    @get:Rule val compose = createComposeRule()
    @After fun clearHistory() { UndoManager.clear() }
    @Test fun lightNumberOnly() = capture(false, false, 360, 1f, "toolbar-number-light")
    @Test fun darkNumberOnly() = capture(true, false, 360, 1f, "toolbar-number-dark")
    @Test fun lightGroupedWeek() = capture(false, true, 360, 1f, "toolbar-group-light")
    @Test fun darkGroupedWeek() = capture(true, true, 360, 1f, "toolbar-group-dark")
    @Test fun narrowLargeFontWrapsLast() = capture(false, true, 280, 1.5f, "toolbar-wrap-large")

    private fun capture(dark: Boolean, busy: Boolean, width: Int, font: Float, name: String) {
        UndoManager.clear()
        if (busy) UndoManager.capture(emptyList(), emptyList(), null)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(
                    LocalUiStyle provides UiStyle.LiquidGlass, LocalDarkTheme provides dark,
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
        if (width == 360) {
            compose.onNodeWithTag("schedule_week_label").assertTextEquals("2")
            if (busy) compose.onNodeWithTag("schedule_week_group").assertExists()
            else compose.onNodeWithTag("schedule_week_group").assertDoesNotExist()
        }
        save(name)
        if (busy && width == 360) {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("schedule_prev_week").performTouchInput { down(center) }
            try {
                compose.mainClock.advanceTimeBy(400)
                save("$name-pressed")
            } finally {
                compose.onNodeWithTag("schedule_prev_week").performTouchInput { up() }
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
