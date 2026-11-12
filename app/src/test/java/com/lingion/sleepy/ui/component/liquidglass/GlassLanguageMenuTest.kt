package com.lingion.sleepy.ui.component.liquidglass

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inspector.WindowInspector
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class GlassLanguageMenuTest {
    @get:Rule val compose = createComposeRule()

    private val languages = listOf(
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "es" to "Español",
    )

    private fun assertLanguageOptionsDisplayed() {
        languages.forEach { (code, _) ->
            val option = compose.onNodeWithTag("general_language#$code").assertIsDisplayed()
            if (code == "en") option.assertIsSelected() else option.assertIsNotSelected()
        }
    }

    private fun popupView() = WindowInspector.getGlobalWindowViews()
        .first { it.javaClass.simpleName == "PopupLayout" }

    private fun menuScreenBounds(): Rect {
        val bounds = compose.onNodeWithTag("general_language_menu").fetchSemanticsNode().boundsInWindow
        var offset = Offset.Zero
        compose.runOnIdle {
            val screen = IntArray(2)
            val window = IntArray(2)
            popupView().getLocationOnScreen(screen)
            popupView().getLocationInWindow(window)
            offset = Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
        }
        return bounds.translate(offset)
    }

    private fun showMenu(
        style: UiStyle = UiStyle.LiquidGlass,
        direction: LayoutDirection = LayoutDirection.Ltr,
        options: List<Pair<String, String>> = languages,
        alignment: Alignment = Alignment.BottomCenter,
        onSelect: (String) -> Unit = {},
        onDismissed: (String?) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style, LocalLayoutDirection provides direction) {
                    GlassHost {
                        Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
                            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                GlassLanguageMenu("Language", "en", options, onSelect, onDismissed = onDismissed)
                                Text("Following setting", Modifier.testTag("following_setting"))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun narrowLargeFontKeepsLanguageTitleWhole() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f), LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Box(Modifier.width(248.dp)) { GlassLanguageMenu("语言 / Language", "zh-CN", languages, {}) }
                    }
                }
            }
        }
        val title = compose.onNodeWithText("语言 / Language", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("the English word must remain whole", layout.lineCount == 1 && layout.getLineEnd(0, true) == layout.layoutInput.text.length)
        val value = compose.onNodeWithTag("general_language_value", true).fetchSemanticsNode().boundsInRoot
        assertTrue("value uses its own row when the full labels cannot fit", value.top >= title.fetchSemanticsNode().boundsInRoot.bottom + 7.9f)
    }

    @Test fun wholeRowOpensAnIndependentMenuWithCurrentSelection() {
        showMenu(style = UiStyle.LiquidGlass)
        val before = compose.onNodeWithTag("following_setting").fetchSemanticsNode().boundsInWindow
        languages.forEach { (code, _) -> compose.onNodeWithTag("general_language#$code").assertDoesNotExist() }
        compose.onNodeWithTag("general_language_card").performClick()
        assertLanguageOptionsDisplayed()
        assertEquals(before, compose.onNodeWithTag("following_setting").fetchSemanticsNode().boundsInWindow)
    }

    @Test fun materialDesignLanguageCardExpandsInlineAndCollapsesWithoutPopup() {
        showMenu(style = UiStyle.Classic, alignment = Alignment.TopCenter)
        val before = compose.onNodeWithTag("following_setting").fetchSemanticsNode().boundsInWindow
        compose.runOnIdle {
            assertTrue(WindowInspector.getGlobalWindowViews().none { it.javaClass.simpleName == "PopupLayout" })
        }
        languages.forEach { (code, _) -> compose.onNodeWithTag("general_language#$code").assertDoesNotExist() }
        compose.onNodeWithTag("general_language_card").performClick()
        assertLanguageOptionsDisplayed()
        val expanded = compose.onNodeWithTag("following_setting").fetchSemanticsNode().boundsInWindow
        assertTrue("inline language options push the following setting down", expanded.top > before.top)
        compose.runOnIdle {
            assertTrue("Material Design language settings never create a popup window",
                WindowInspector.getGlobalWindowViews().none { it.javaClass.simpleName == "PopupLayout" })
        }
        compose.onNodeWithTag("general_language_card").performClick()
        languages.forEach { (code, _) -> compose.onNodeWithTag("general_language#$code").assertDoesNotExist() }
        assertEquals(before, compose.onNodeWithTag("following_setting").fetchSemanticsNode().boundsInWindow)
        compose.runOnIdle {
            assertTrue(WindowInspector.getGlobalWindowViews().none { it.javaClass.simpleName == "PopupLayout" })
        }
    }

    @Test fun selectionCommitsImmediatelyAndDismissalWaitsForExit() {
        val selected = mutableListOf<String>()
        val dismissed = mutableListOf<String?>()
        showMenu(onSelect = {
            assertTrue(
                "selection is accepted synchronously while the menu is present",
                WindowInspector.getGlobalWindowViews().any { view -> view.javaClass.simpleName == "PopupLayout" },
            )
            selected += it
        }, onDismissed = {
            assertTrue(
                "dismissal callback follows popup window removal",
                WindowInspector.getGlobalWindowViews().none { view -> view.javaClass.simpleName == "PopupLayout" },
            )
            dismissed += it
        })
        compose.onNodeWithTag("general_language_card").performClick()
        // 先让打开的重组/动画完成，再冻结退出动画；否则 Popup 还未加入语义树。
        compose.onNodeWithTag("general_language#ja").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        val staleClick = compose.onNodeWithTag("general_language#ja").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        compose.onNodeWithTag("general_language#ja").performClick()
        compose.runOnIdle {
            staleClick()
            staleClick()
            assertEquals("accepted selection is synchronous and emitted once", listOf("ja"), selected)
            assertTrue("dismissal must wait for exit", dismissed.isEmpty())
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("general_language#en").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle {
            assertEquals(listOf("ja"), selected)
            assertTrue("must not dismiss halfway through exit", dismissed.isEmpty())
        }
        compose.onNodeWithTag("general_language#en").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("general_language#en").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("ja"), selected)
            assertEquals(listOf("ja"), dismissed)
        }
        compose.onNodeWithTag("general_language_card").performClick()
        compose.onNodeWithTag("general_language#en").assertIsDisplayed()
    }

    @Test fun outsideTouchDismissesWithoutSelecting() {
        val selected = mutableListOf<String>()
        val dismissed = mutableListOf<String?>()
        showMenu(onSelect = { selected += it }, onDismissed = { dismissed += it })
        compose.onNodeWithTag("general_language_card").performClick()
        assertLanguageOptionsDisplayed()
        val menu = compose.onNodeWithTag("general_language_menu").fetchSemanticsNode().boundsInWindow
        compose.runOnIdle {
            val popup = popupView()
            val popupLocation = IntArray(2)
            popup.getLocationInWindow(popupLocation)
            val point = listOf(
                Offset(8f, popup.height / 2f), Offset(popup.width - 8f, popup.height / 2f),
                Offset(popup.width / 2f, 8f), Offset(popup.width / 2f, popup.height - 8f),
            ).first { !menu.contains(it + Offset(popupLocation[0].toFloat(), popupLocation[1].toFloat())) }
            val downTime = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
                val event = MotionEvent.obtain(downTime, downTime + index * 16L, action, point.x, point.y, 0)
                try { popup.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
        languages.forEach { (code, _) -> compose.onNodeWithTag("general_language#$code").assertDoesNotExist() }
        compose.runOnIdle {
            assertTrue(selected.isEmpty())
            assertEquals(listOf<String?>(null), dismissed)
        }
    }

    @Test fun actionOutsideStillDismissesWithoutSelecting() {
        val selected = mutableListOf<String>()
        val dismissed = mutableListOf<String?>()
        showMenu(onSelect = { selected += it }, onDismissed = { dismissed += it })
        compose.onNodeWithTag("general_language_card").performClick()
        assertLanguageOptionsDisplayed()
        compose.runOnIdle {
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
            try { popupView().dispatchTouchEvent(event) } finally { event.recycle() }
        }
        compose.onNodeWithTag("general_language_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(selected.isEmpty())
            assertEquals(listOf<String?>(null), dismissed)
        }
    }

    @Test fun backDismissesWithoutSelecting() {
        val selected = mutableListOf<String>()
        showMenu(onSelect = { selected += it })
        compose.onNodeWithTag("general_language_card").performClick()
        assertLanguageOptionsDisplayed()
        compose.runOnIdle {
            val popup = popupView()
            popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        languages.forEach { (code, _) -> compose.onNodeWithTag("general_language#$code").assertDoesNotExist() }
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
    }

    @Test fun menuCentersOnWholeCardAndClampsAboveBottomEdge() {
        showMenu()
        val anchor = compose.onNodeWithTag("general_language_card").fetchSemanticsNode().boundsInWindow
        compose.onNodeWithTag("general_language_card").performClick()
        val menu = menuScreenBounds()
        assertEquals(anchor.center.x, menu.center.x, 1f)
        assertEquals(250f, menu.width, 1f)
        assertTrue(menu.top >= 16f)
        assertEquals(640f - 16f, menu.bottom, 1f)
        assertTrue("the menu moves above a bottom anchor to remain on screen", menu.top < anchor.top)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w240dp-h320dp-mdpi")
    fun narrowRtlWindowClampsMenuAndLongListScrolls() {
        val options = languages + (1..20).map { "extra-$it" to "Language $it" }
        showMenu(direction = LayoutDirection.Rtl, options = options)
        compose.onNodeWithTag("general_language_card").performClick()
        val menu = menuScreenBounds()
        assertEquals(240f - 32f, menu.width, 1f)
        assertTrue(menu.left >= 16f && menu.right <= 240f - 16f)
        assertTrue(menu.top >= 16f && menu.bottom <= 320f - 16f)
        compose.onNodeWithTag("general_language#extra-20").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w240dp-h320dp-mdpi")
    fun heldSlidePastVisibleViewportCannotCommitAClippedRow() {
        val selected = mutableListOf<String>()
        val dismissed = mutableListOf<String?>()
        val options = languages + (1..20).map { "extra-$it" to "Language $it" }
        showMenu(options = options, alignment = Alignment.TopCenter,
            onSelect = { selected += it }, onDismissed = { dismissed += it })
        val anchor = compose.onNodeWithTag("general_language_card")
        val anchorOrigin = anchor.fetchSemanticsNode().boundsInWindow.topLeft
        anchor.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithTag("general_language_menu").assertIsDisplayed()
        val menu = menuScreenBounds()
        val clippedRow = unclippedPopupScreenBounds("general_language#extra-2")
        val outside = Offset(clippedRow.center.x, menu.bottom + 4f)
        assertTrue("the regression input is within a row's unclipped layout", clippedRow.contains(outside))
        assertTrue("the same point is beyond the actual visible viewport", !menu.contains(outside))
        assertTrue("the pointer remains on screen rather than relying on window cancellation", outside.y < 320f)
        val visible = unclippedPopupScreenBounds("general_language#en").center
        anchor.performTouchInput {
            moveTo(visible - anchorOrigin, 16)
            moveTo(outside - anchorOrigin, 16)
            up()
        }
        compose.onNodeWithTag("general_language_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue("a clipped row is never committed by the original held stream", selected.isEmpty())
            assertEquals("leaving the visible menu cancels the provisional selection", listOf<String?>(null), dismissed)
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w240dp-h320dp-mdpi")
    fun heldSlideStillCommitsAVisibleRowOnceInTheClippedMenu() {
        val selected = mutableListOf<String>()
        val dismissed = mutableListOf<String?>()
        val options = languages + (1..20).map { "extra-$it" to "Language $it" }
        showMenu(options = options, alignment = Alignment.TopCenter,
            onSelect = { selected += it }, onDismissed = { dismissed += it })
        val anchor = compose.onNodeWithTag("general_language_card")
        val anchorOrigin = anchor.fetchSemanticsNode().boundsInWindow.topLeft
        anchor.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val visible = unclippedPopupScreenBounds("general_language#ja").center
        assertTrue(menuScreenBounds().contains(visible))
        anchor.performTouchInput { moveTo(visible - anchorOrigin, 16); up() }
        compose.onNodeWithTag("general_language_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("ja"), selected)
            assertEquals(listOf("ja"), dismissed)
        }
    }

    private fun unclippedPopupScreenBounds(tag: String): Rect {
        val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val pixelBounds = with(compose.density) {
            Rect(bounds.left.toPx(), bounds.top.toPx(), bounds.right.toPx(), bounds.bottom.toPx())
        }
        var screenOrigin = Offset.Zero
        compose.runOnIdle {
            val origin = IntArray(2)
            popupView().getLocationOnScreen(origin)
            screenOrigin = Offset(origin[0].toFloat(), origin[1].toFloat())
        }
        return pixelBounds.translate(screenOrigin)
    }

    @Test fun rtlMenuUsesWholeCardCenterAndTopWhenSpacePermits() {
        showMenu(direction = LayoutDirection.Rtl, alignment = Alignment.TopCenter)
        val anchor = compose.onNodeWithTag("general_language_card").fetchSemanticsNode().boundsInWindow
        compose.onNodeWithTag("general_language_card").performClick()
        val menu = menuScreenBounds()
        assertEquals(anchor.center.x, menu.center.x, 1f)
        assertEquals(anchor.top, menu.top, 1f)
        assertEquals(250f, menu.width, 1f)
    }
}
