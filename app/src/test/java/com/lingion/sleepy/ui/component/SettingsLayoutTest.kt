package com.lingion.sleepy.ui.component

import com.lingion.sleepy.ui.component.liquidglass.LocalDockInputTimeNanos
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import java.io.File
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
@Config(sdk = [35], qualifiers = "zh-rCN-w400dp-h900dp-port-xhdpi")
class SettingsLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun page(width: Int = 360, scale: Float = 1f, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalDensity provides Density(LocalDensity.current.density, scale)) {
                SleepyThemeProvider(darkTheme = dark, themeKey = "default") {
                    Surface(Modifier.width(width.dp).testTag("settings-sample")) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun screenshot(name: String) {
        // Opt in with -PsettingsScreenshots=true; normal test runs only check layout and interaction.
        if (!System.getProperty("sleepy.test.settingsScreenshots", "false").toBoolean()) return
        val directory = File("build/reports/settings-layout").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(compose.onNodeWithTag("settings-sample").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun short_choices_stay_inline_and_selectable() {
        var selected = -1
        page {
            SettingsFlatCard("样式", options = listOf("贴底", "悬浮"), onSelect = { selected = it })
        }
        assertEquals(bounds("样式").center.y, bounds("贴底").center.y, 2f)
        assertTrue(bounds("样式").right < bounds("贴底").left)
        compose.onNodeWithText("悬浮").performClick()
        assertEquals(1, selected)
        screenshot("inline-light")
    }

    @Test fun long_title_moves_choices_below_without_compressing_title() {
        val title = "默认打开的课表显示方式"
        val description = "选择启动时默认展示的课表视图"
        page(width = 320) {
            SettingsFlatCard(title, subtitle = description, options = listOf("周视图", "网格"))
        }
        assertTrue(bounds(title).bottom <= bounds("周视图").top)
        val option = compose.onNodeWithText("周视图").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds(description).top - option.bottom >= with(compose.density) { 8.dp.toPx() })
        screenshot("wrapped-light")
    }

    @Test fun large_font_long_options_remain_readable_and_selectable() {
        var selected = -1
        val first = "按课程数量自动平衡"
        val second = "按每一天分别显示"
        page(width = 280, scale = 2f, dark = true) {
            SettingsFlatCard("两栏布局", options = listOf(first, second), onSelect = { selected = it })
        }
        assertTrue(bounds(first).bottom <= bounds(second).top)
        assertEquals(bounds("两栏布局").left, bounds(first).left, 1f)
        val container = compose.onNodeWithTag("settings-sample").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds(second).right <= container.right)
        compose.onNodeWithText(second).performClick()
        assertEquals(1, selected)
        screenshot("large-font-dark")
    }

    @Test fun expanded_rows_align_with_card_heading_and_empty_description_has_no_height() {
        page {
            Column(verticalArrangement = Arrangement.spacedBy(SettingsLayout.cardGap)) {
                SettingsCard("显示设置", expanded = true, onToggle = {}) {
                    SettingToggleRow("显示日期", true, {}, subtitle = "在表头显示具体日期")
                    DisplayModeOption("普通模式", "", true, {})
                }
                Column(Modifier.testTag("without-description").padding(16.dp)) {
                    SettingToggleRow("第一项", false, {})
                }
                Column(Modifier.testTag("blank-description").padding(16.dp)) {
                    SettingToggleRow("第二项", false, {}, subtitle = "  ")
                }
            }
        }
        assertEquals(bounds("显示设置").left, bounds("显示日期").left, 1f)
        assertEquals(bounds("显示设置").left, bounds("普通模式").left, 1f)
        assertEquals(bounds("显示日期").left, bounds("在表头显示具体日期").left, 1f)
        assertEquals(
            compose.onNodeWithTag("without-description").fetchSemanticsNode().boundsInRoot.height,
            compose.onNodeWithTag("blank-description").fetchSemanticsNode().boundsInRoot.height, 1f,
        )
        screenshot("aligned-rows-light")
    }
}
