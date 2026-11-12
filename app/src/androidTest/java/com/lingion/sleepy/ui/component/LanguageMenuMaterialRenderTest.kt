package com.lingion.sleepy.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.liquidglass.GlassBackground
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.GlassLanguageMenu
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Text visibility alone cannot detect a menu whose optical backing clips after its first row. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LanguageMenuMaterialRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backingCoversEveryLanguageRowOnAContrastingBackground() {
        val background = Color(0xFF48245C)
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(surfaceBright = Color.White)) {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDarkTheme provides false) {
                    GlassHost(Modifier.fillMaxSize(), background = background) {
                        GlassBackground(0f, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(background)) }
                        Box(Modifier.offset(x = 20.dp, y = 100.dp).width(320.dp)) {
                            GlassLanguageMenu("Language", "en", listOf("zh-CN" to "简体中文", "zh-TW" to "繁體中文",
                                "en" to "English", "ja" to "日本語", "es" to "Español"), {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("general_language_card").performClick()
        compose.waitForIdle()
        val menu = compose.onNodeWithTag("general_language_menu").captureToImage().asAndroidBitmap()
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "glass-language-material")
        output.mkdirs()
        File(output, "all-five-language-material.png").outputStream().use { menu.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = (0..4).map { index ->
            val x = (menu.width * .88f).toInt()
            val y = (menu.height * (10f + 42f * (index + .5f)) / 230f).toInt().coerceIn(0, menu.height - 1)
            menu.getPixel(x, y)
        }
        val channels = colors.map { c -> listOf(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c)) }
        channels.forEachIndexed { index, rgb ->
            assertTrue("Language row $index must have a visible light material instead of the raw purple backing: $channels",
                rgb.min() > 120)
        }
        repeat(3) { channel ->
            assertTrue("Every row shares the same steady material: $channels",
                channels.maxOf { it[channel] } - channels.minOf { it[channel] } <= 8)
        }
    }
}
