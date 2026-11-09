package com.lingion.sleepy.ui.screen.edit

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h800dp-port-xhdpi", application = SleepyApp::class)
class MeetingBlockHeaderLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun action(id: Int) = compose.onNodeWithContentDescription(context.getString(id))
    private fun render(width: Int = 360, font: Float = 1f, title: String = "时段 1", removable: Boolean = true, remove: () -> Unit = {}): MeetingBlockDraft {
        val block = MeetingBlockDraft(id = 1, days = mutableStateListOf(1), startNode = 1, step = 2, startTime = "08:00", endTime = "09:40")
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, font), LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Surface(Modifier.width(width.dp).testTag("meeting-block-card-root")) {
                            MeetingBlockEditor(
                                title = title, block = block, groupSourceColorHex = "#FF6750A4", canRemove = removable,
                                issues = emptyList(), fieldShape = SleepyTheme.shapes.medium, fieldColors = TextFieldDefaults.colors(),
                                maxStd = 12, maxWeek = 20, timeJson = "", candidates = emptyList(), onRemove = remove,
                                onPickEdge = {}, onEditSlot = { _, _, _ -> }, onDeselectEdge = {},
                            )
                        }
                    }
                }
            }
        }
        return block
    }
    private fun assertHeader(title: String) {
        val text = compose.onNodeWithText(title).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cross = action(R.string.delete_slot).assertIsDisplayed().fetchSemanticsNode()
        val arrow = action(R.string.expand).assertIsDisplayed().fetchSemanticsNode()
        assertTrue("title, delete, chevron order", text.right <= cross.boundsInRoot.left && cross.boundsInRoot.right <= arrow.boundsInRoot.left)
        val minimum = 48f * compose.density.density - 1f
        listOf(cross, arrow).forEach { assertTrue("48dp action target", it.touchBoundsInRoot.width >= minimum && it.touchBoundsInRoot.height >= minimum) }
        assertEquals(cross.boundsInRoot.width, arrow.boundsInRoot.width, .5f)
        assertEquals(cross.boundsInRoot.height, arrow.boundsInRoot.height, .5f)
    }
    private fun capture(name: String) {
        if (!System.getProperty("sleepy.test.settingsScreenshots", "false").toBoolean()) return
        val directory = File("build/reports/meeting-block-layout").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(compose.onNodeWithTag("meeting-block-card-root").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    @Test fun titleThenDeleteThenChevronHaveSeparateEqualTargets() {
        render(); assertHeader("时段 1"); capture("header_order_and_bounds_normal")
    }
    @Test fun singleBlockKeepsOnlyTheTrailingChevron() {
        render(removable = false)
        action(R.string.delete_slot).assertDoesNotExist()
        val arrow = action(R.string.expand).assertIsDisplayed().fetchSemanticsNode()
        assertTrue(compose.onNodeWithText("时段 1").fetchSemanticsNode().boundsInRoot.right <= arrow.boundsInRoot.left)
        assertTrue(arrow.touchBoundsInRoot.width >= 48f * compose.density.density - 1f)
        capture("header_single_block_no_delete")
    }
    @Test fun textAndChevronToggleWhileDeleteOnlyRemoves() {
        var removed = false
        val block = render(remove = { removed = true })
        assertFalse(block.expanded)
        compose.onNodeWithText("时段 1").performClick(); assertTrue(block.expanded)
        action(R.string.collapse).performClick(); assertFalse(block.expanded)
        action(R.string.delete_slot).performClick(); assertTrue(removed); assertFalse(block.expanded)
        capture("header_interactivity_verified")
    }
    @Test fun narrowLargeFontKeepsLongTitleClearOfBothActions() {
        val title = "长标题超长课程上课时段排版核验长文本换行不能挤压右侧按钮"
        render(width = 280, font = 1.5f, title = title); assertHeader(title)
        capture("header_narrow_large_font_anti_squeeze")
    }
}
