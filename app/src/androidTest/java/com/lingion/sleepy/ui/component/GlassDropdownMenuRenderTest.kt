package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.GlassDropdownMenu
import com.lingion.sleepy.ui.component.liquidglass.GlassAlertDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassDatePickerDialog
import com.lingion.sleepy.ui.component.liquidglass.GlassLanguageMenu
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual popup-window pixels, including a foreground change after the enter transition. */
@RunWith(AndroidJUnit4::class)
class GlassDropdownMenuRenderTest {
    @get:Rule val compose = createComposeRule()
    private enum class Carrier { Dropdown, Alert, DatePicker, Language }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun popupForegroundRemainsVisibleAndUpdatesAfterEnterTransition() {
        val style = mutableStateOf(UiStyle.Classic)
        val carrier = mutableStateOf(Carrier.Dropdown)
        val foreground = mutableStateOf(Color.Red)
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(onSurface = foreground.value)) {
                CompositionLocalProvider(LocalUiStyle provides style.value) {
                    key(style.value, carrier.value) {
                        GlassHost {
                            Box(Modifier.size(240.dp)) {
                                when (carrier.value) {
                                    Carrier.Dropdown -> GlassDropdownMenu(true, {}) {
                                        Box(Modifier.size(80.dp).background(foreground.value))
                                    }
                                    Carrier.Alert -> GlassAlertDialog({}, confirmButton = {}, text = {
                                        Box(Modifier.size(80.dp).background(foreground.value))
                                    })
                                    Carrier.DatePicker -> GlassDatePickerDialog({}, confirmButton = {}) {
                                        Box(Modifier.size(80.dp).background(foreground.value))
                                    }
                                    Carrier.Language -> GlassLanguageMenu(
                                        title = "Language", selectedCode = "en",
                                        languages = listOf("en" to "English", "fr" to "French"), onSelect = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val counts = linkedMapOf<String, Int>()
        val failures = mutableListOf<String>()
        for (sample in Carrier.entries) {
            for (mode in listOf(UiStyle.Classic, UiStyle.LiquidGlass)) {
                compose.runOnIdle { style.value = mode; carrier.value = sample; foreground.value = Color.Red }
                if (sample == Carrier.Language) compose.onNodeWithTag("general_language_card").performClick()
                compose.mainClock.advanceTimeBy(600)
                compose.waitForIdle()
                val red = captureForeground("$sample-$mode-red", red = true)
                compose.runOnIdle { foreground.value = Color.Green }
                compose.waitForIdle()
                val green = captureForeground("$sample-$mode-green", red = false)
                counts["$sample-$mode-red"] = red
                counts["$sample-$mode-green"] = green
                val minimum = if (sample == Carrier.Language) 100 else 1000
                if (red <= minimum || green <= minimum) failures += "$sample-$mode: $red red, $green green"
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "Popup foreground pixels: $counts\n")
        })
        assertTrue("popup foreground must reach actual window pixels and stay live: $failures", failures.isEmpty())
    }

    private fun captureForeground(name: String, red: Boolean): Int {
        val image = compose.onAllNodes(isPopup() or isDialog()).onFirst().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "popup-$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        var count = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val pixel = image.getPixel(x, y)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            if (b < 50 && if (red) r > 220 && g < 50 else g > 220 && r < 50) count++
        }
        return count
    }
}
