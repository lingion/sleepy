package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.lingion.sleepy.ui.component.SegmentedSwitcher
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class GlassSelectionColorRegressionTest {
    @get:Rule val compose = createComposeRule()

    private val languages = listOf(
        "zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English",
        "ja" to "日本語", "es" to "Español",
    )

    @Test fun glassSelectionsRetainTheirMaterialThemeFill() {
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(secondaryContainer = Color.Magenta)) {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Column {
                            SegmentedSwitcher(
                                options = listOf(0 to "First", 1 to "Second"), selected = 0, onSelect = {},
                                modifier = Modifier.fillMaxWidth().testTag("segments"),
                            )
                            GlassFilterChip(
                                selected = true, onClick = {}, label = { Text(" ") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Magenta),
                                modifier = Modifier.testTag("chip"),
                            )
                        }
                    }
                }
            }
        }
        val segments = compose.onNodeWithTag("segments").captureToImage().asAndroidBitmap()
        saveFrame("glass-selection-segments.png", segments)
        assertEquals("selected segment keeps the same themed surface as Material", android.graphics.Color.MAGENTA,
            segments.getPixel(12, segments.height / 2))
        val chip = compose.onNodeWithTag("chip").captureToImage().asAndroidBitmap()
        saveFrame("glass-selection-chip.png", chip)
        assertEquals("selected chip keeps the supplied Material selection color", android.graphics.Color.MAGENTA,
            chip.getPixel(chip.width / 2, chip.height / 2))
    }

    @Test fun allFiveLanguagesFitTheGlassMenu() {
        showLanguages()
        compose.onNodeWithTag("general_language_card").performClick()
        languages.forEach { (code, _) -> compose.onNodeWithTag("general_language#$code").assertIsDisplayed() }
        val menu = compose.onNodeWithTag("general_language_menu").captureToImage().asAndroidBitmap()
        saveFrame("glass-five-language-menu.png", menu)
        assertEquals("menu uses the measured iOS 26.5 content width", 250, menu.width)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h220dp-mdpi")
    fun shortGlassMenuCanScrollToTheFifthLanguage() {
        showLanguages()
        compose.onNodeWithTag("general_language_card").performClick()
        compose.onNodeWithTag("general_language#es").performScrollTo().assertIsDisplayed()
        saveFrame("glass-language-short-window.png", compose.onNodeWithTag("general_language_menu").captureToImage().asAndroidBitmap())
    }

    @Test fun toggleThumbCrossfadesDuringBothRiseAndFall() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.fillMaxSize(), background = Color.Red) {
                        GlassSwitch(
                            checked = false, onCheckedChange = {}, modifier = Modifier.testTag("toggle"),
                            colors = SwitchDefaults.colors(
                                uncheckedTrackColor = Color.Red, checkedTrackColor = Color.Red,
                                uncheckedThumbColor = Color.White, checkedThumbColor = Color.White,
                            ),
                        )
                    }
                }
            }
        }
        val toggle = compose.onNodeWithTag("toggle")
        fun frame(name: String): android.graphics.Bitmap = toggle.captureToImage().asAndroidBitmap().also { saveFrame(name, it) }
        fun whiteness(bitmap: android.graphics.Bitmap): Int = android.graphics.Color.green(bitmap.getPixel(23, 24))
        val rest = frame("glass-toggle-0-rest.png")
        compose.mainClock.autoAdvance = false
        toggle.performTouchInput { down(Offset(centerX, centerY)) }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(64)
        val rising = frame("glass-toggle-1-rising.png")
        compose.mainClock.advanceTimeBy(800)
        val held = frame("glass-toggle-2-held.png")
        toggle.performTouchInput { cancel() }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(64)
        val lowering = frame("glass-toggle-3-lowering.png")
        compose.mainClock.advanceTimeBy(1_000)
        val settled = frame("glass-toggle-4-settled.png")
        compose.mainClock.autoAdvance = true
        assertEquals("resting thumb is opaque", 255, whiteness(rest))
        assertTrue("rise has a visible intermediate opacity", whiteness(rising) in 20..240)
        assertTrue("held lens reveals its red backdrop", whiteness(held) < whiteness(rising))
        assertTrue("fall progressively restores the solid thumb", whiteness(lowering) > whiteness(held) && whiteness(lowering) < 250)
        assertEquals("settled thumb restores the original opaque fill", whiteness(rest), whiteness(settled))
    }

    private fun showLanguages() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost { GlassLanguageMenu("Language", "en", languages, {}) }
                }
            }
        }
    }

    private fun saveFrame(name: String, bitmap: android.graphics.Bitmap) {
        val root = System.getProperty("sleepy.test.root")?.let(::File) ?: File("..").absoluteFile
        val directory = File(root, "app/build/reports/ui-regressions").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
