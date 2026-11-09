package com.lingion.sleepy.ui.component.liquidglass

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.data.CustomTheme
import com.lingion.sleepy.ui.screen.mine.CustomThemeCard
import com.lingion.sleepy.ui.screen.mine.PresetThemeCard
import com.lingion.sleepy.ui.screen.mine.PendingPolicyBanner
import com.lingion.sleepy.ui.screen.schedule.SchedulePolicy
import com.lingion.sleepy.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h1600dp-mdpi")
class GlassDetailsLayoutGeometryTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun cards(width: Int, font: Float) {
        var edits = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(1f, font)) {
                    GlassHost {
                        Column(Modifier.width(width.dp)) {
                            Box(Modifier.testTag("preset")) { PresetThemeCard(ThemePresets.Default, false, {}) }
                            Box(Modifier.testTag("custom")) {
                                CustomThemeCard(CustomTheme("test", "长名称自定义主题", "#6750A4", "#625B71", "#7D5260", 265.0, 8.0, 0), false, {}, { edits++ })
                            }
                        }
                    }
                }
            }
        }
        val preset = compose.onNodeWithTag("preset").fetchSemanticsNode().boundsInRoot
        val custom = compose.onNodeWithTag("custom").fetchSemanticsNode().boundsInRoot
        val edit = compose.onNodeWithContentDescription(context.getString(R.string.theme_custom_edit)).assertIsDisplayed()
        val button = edit.fetchSemanticsNode().boundsInRoot
        assertEquals("real cards keep equal height", preset.height, custom.height, .2f)
        assertTrue("full edit target remains inside the card", button.width >= 47.9f && button.height >= 47.9f && button.right <= custom.right - 15.9f)
        val swatches = compose.onAllNodes(hasTestTag("theme-swatch") and hasAnyAncestor(hasTestTag("custom")), true).fetchSemanticsNodes()
        assertEquals(3, swatches.size)
        swatches.forEach { assertTrue("all three previews stay clear of edit", it.boundsInRoot.width > 0f && it.boundsInRoot.right <= button.left - 7.9f) }
        edit.performClick(); assertEquals(1, edits)
    }
    @Test fun realThemeCardsFit320DpPhoneColumnsAtLargeFont() { cards(138, 1.5f) }
    @Test fun realThemeCardsFit360DpPhoneColumns() { cards(158, 1f) }
    @Test fun realPolicyBannerKeepsItsTextClearOfTheModifyTarget() {
        var modified = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(1f, 1.5f)) {
                    GlassHost { Box(Modifier.width(280.dp)) { PendingPolicyBanner(SchedulePolicy.SYNC) { modified = true } } }
                }
            }
        }
        val button = compose.onNode(hasText("修改") and hasClickAction())
        button.assertHeightIsAtLeast(48.dp)
        val message = context.getString(R.string.schedule_conflict_banner_format, context.getString(R.string.schedule_conflict_option_sync_sub_only))
        val text = compose.onNodeWithText(message).fetchSemanticsNode().boundsInRoot
        assertTrue(button.fetchSemanticsNode().boundsInRoot.left - text.right >= 7.9f)
        button.performClick(); assertTrue(modified)
    }
}
