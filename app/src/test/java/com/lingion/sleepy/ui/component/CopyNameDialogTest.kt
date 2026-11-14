package com.lingion.sleepy.ui.component

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h640dp-mdpi")
class CopyNameDialogTest {
    @get:Rule val compose = createComposeRule()
    private val period = mutableStateOf(false)
    private val name = mutableStateOf("课程表2")
    private val nameTaken = mutableStateOf(false)
    private var confirmations = 0
    private var dismissals = 0

    private fun show(style: UiStyle, fontScale: Float = 1f) {
        // Dialog installs its own Android density. Set the actual configuration
        // so both the Activity and the separate dialog window see the same scale.
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost {
                        CopyNameDialog(
                            title = if (period.value) "复制作息表" else "创建副本",
                            name = name.value,
                            onNameChange = { name.value = it },
                            nameLabel = if (period.value) "作息表名称" else "课表名称",
                            nameTaken = nameTaken.value,
                            onConfirm = { confirmations++ },
                            onDismiss = { dismissals++ },
                            tagPrefix = "copy",
                        )
                    }
                }
            }
        }
    }

    @Test fun materialCopiesShareDefaultSizeSpacingAndButtons() = checkDefaultLayout(UiStyle.Classic)
    @Test fun glassCopiesShareDefaultSizeSpacingAndButtons() = checkDefaultLayout(UiStyle.LiquidGlass)

    private fun checkDefaultLayout(style: UiStyle) {
        show(style)
        val timetable = bounds("copy_dialog")
        val field = bounds("copy_field")
        val confirm = bounds("copy_confirm")
        val dismiss = bounds("copy_dismiss")
        assertEquals(20f, confirm.top - field.bottom, .1f)
        if (style == UiStyle.LiquidGlass) {
            assertEquals("footer owns the sole left inset", 16f, dismiss.left - timetable.left, .1f)
            assertEquals("footer owns the sole right inset", 16f, timetable.right - confirm.right, .1f)
            assertEquals(140f, confirm.width, .1f)
            assertEquals(confirm.width, dismiss.width, .1f)
            assertEquals(8f, confirm.left - dismiss.right, .1f)
            assertEquals(16f, timetable.bottom - confirm.bottom, .1f)
            assertEquals("the field keeps its separate body inset", 30f, field.left - timetable.left, .1f)
            assertEquals("action row remains centered on the pane", timetable.center.x,
                (dismiss.left + confirm.right) / 2f, .1f)
        }
        assertEquals(confirm.height, dismiss.height, .1f)
        assertTrue(confirm.height >= 48f)
        compose.runOnIdle { period.value = true }
        val periodTable = bounds("copy_dialog")
        assertEquals("default copies share their design system's width", timetable.width, periodTable.width, .1f)
        assertEquals("default copies share their design system's height", timetable.height, periodTable.height, .1f)
    }

    @Test fun blankAndDuplicateNamesCannotConfirmAndValidNamesKeepCallbacks() {
        name.value = ""
        show(UiStyle.LiquidGlass)
        compose.onNodeWithTag("copy_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("copy_field").performTextReplacement("New table")
        compose.onNodeWithTag("copy_confirm").assertIsEnabled()
        val normal = bounds("copy_dialog").height
        compose.runOnIdle { nameTaken.value = true }
        compose.onNodeWithTag("copy_confirm").assertIsNotEnabled()
        assertTrue("validation grows the form instead of clipping its message", bounds("copy_dialog").height > normal)
        compose.runOnIdle { nameTaken.value = false }
        compose.onNodeWithTag("copy_confirm").performClick()
        compose.onNodeWithTag("copy_dismiss").performClick()
        assertEquals(1, confirmations)
        assertEquals(1, dismissals)
    }

    @Test fun materialCopyButtonsGrowForLargeText() = checkLargeText(UiStyle.Classic)
    @Test fun glassCopyButtonsGrowForLargeText() = checkLargeText(UiStyle.LiquidGlass)

    private fun checkLargeText(style: UiStyle) {
        show(style, fontScale = 2f)
        compose.onNodeWithTag("copy_confirm").performScrollTo().assertIsDisplayed()
        listOf("确定" to "copy_confirm", "取消" to "copy_dismiss").forEach { (label, tag) ->
            val layouts = mutableListOf<TextLayoutResult>()
            val text = compose.onNodeWithText(label, useUnmergedTree = true)
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            assertTrue(layouts.isNotEmpty())
            val layout = layouts.single()
            assertEquals("the actual dialog label uses the requested font scale", 2f,
                layout.layoutInput.density.fontScale, 0f)
            // Compose 1.12.1 ParagraphLayoutCache.slowCreateTextLayoutResultOrNull
            // rebuilds the semantic MultiParagraph with prevConstraints.maxWidth,
            // while returning the cached, content-sized layoutSize. Normalize only
            // that Center offset back to the actual text box's drawing coordinates.
            val centerOffset = if (layout.layoutInput.style.textAlign == TextAlign.Center) {
                (layout.multiParagraph.width - layout.size.width) / 2f
            } else 0f
            val textBounds = text.fetchSemanticsNode().boundsInRoot
            assertFalse("$label has no vertical overflow", layout.didOverflowHeight)
            assertEquals("$label remains complete at large font scale", label.length,
                layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            repeat(layout.lineCount) { line ->
                assertFalse("$label must not be ellipsized", layout.isLineEllipsized(line))
                val left = layout.getLineLeft(line) - centerOffset
                val right = layout.getLineRight(line) - centerOffset
                assertTrue("$label line width fits the actual text box",
                    layout.getLineRight(line) - layout.getLineLeft(line) <= textBounds.width + .5f)
                assertTrue("$label fits the text box horizontally: line=$line left=$left right=$right size=${layout.size} semanticCenterOffset=$centerOffset",
                    left >= -.5f && right <= layout.size.width + .5f)
                assertTrue("$label fits the text box vertically",
                    layout.getLineTop(line) >= -.5f && layout.getLineBottom(line) <= layout.size.height + .5f)
            }
            val buttonBounds = bounds(tag)
            assertTrue("$label stays inside its button",
                textBounds.left >= buttonBounds.left && textBounds.right <= buttonBounds.right &&
                    textBounds.top >= buttonBounds.top && textBounds.bottom <= buttonBounds.bottom)
        }
        assertTrue("button height follows its scaled label and padding", bounds("copy_confirm").height > 48f)
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
