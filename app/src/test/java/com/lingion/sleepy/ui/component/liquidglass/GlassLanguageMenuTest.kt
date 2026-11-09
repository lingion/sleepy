package com.lingion.sleepy.ui.component.liquidglass

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    private val languages = listOf("zh-CN" to "简体中文", "en" to "English", "ja" to "日本語")

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
        style: UiStyle = UiStyle.Classic,
        direction: LayoutDirection = LayoutDirection.Ltr,
        options: List<Pair<String, String>> = languages,
        onSelect: (String) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style, LocalLayoutDirection provides direction) {
                    GlassHost {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                GlassLanguageMenu("Language", "en", options, onSelect)
                                Text("Following setting", Modifier.testTag("following_setting"))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun wholeRowOpensAnIndependentMenuWithCurrentSelection() {
        showMenu(style = UiStyle.LiquidGlass)
        val before = compose.onNodeWithTag("following_setting").fetchSemanticsNode().boundsInWindow
        compose.onNodeWithTag("general_language#en").assertDoesNotExist()
        compose.onNodeWithTag("general_language_card").performClick()
        compose.onNodeWithTag("general_language#en").assertIsDisplayed().assertIsSelected()
        assertEquals(before, compose.onNodeWithTag("following_setting").fetchSemanticsNode().boundsInWindow)
    }

    @Test fun selectionWaitsForExitAndRejectsRepeatedActions() {
        val selected = mutableListOf<String>()
        showMenu(onSelect = {
            assertTrue(
                "popup window must be removed before saving or recreating",
                WindowInspector.getGlobalWindowViews().none { view -> view.javaClass.simpleName == "PopupLayout" },
            )
            selected += it
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
            assertTrue("must not save or recreate during exit", selected.isEmpty())
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("general_language#en").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertTrue("must not commit halfway through exit", selected.isEmpty()) }
        compose.onNodeWithTag("general_language#en").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("general_language#en").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("ja"), selected) }
        compose.onNodeWithTag("general_language_card").performClick()
        compose.onNodeWithTag("general_language#en").assertIsDisplayed()
    }

    @Test fun outsideTouchDismissesWithoutSelecting() {
        val selected = mutableListOf<String>()
        showMenu(onSelect = { selected += it })
        compose.onNodeWithTag("general_language_card").performClick()
        compose.runOnIdle {
            val popup = popupView()
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
            try { popup.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        compose.onNodeWithTag("general_language#en").assertDoesNotExist()
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
    }

    @Test fun backDismissesWithoutSelecting() {
        val selected = mutableListOf<String>()
        showMenu(onSelect = { selected += it })
        compose.onNodeWithTag("general_language_card").performClick()
        compose.runOnIdle {
            val popup = popupView()
            popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.onNodeWithTag("general_language#en").assertDoesNotExist()
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
    }

    @Test fun ltrMenuAlignsWithTrailingValueAndStaysAboveBottomEdge() {
        showMenu()
        compose.onNodeWithTag("general_language_card").performClick()
        val anchor = compose.onNodeWithTag("general_language_value", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val menu = menuScreenBounds()
        assertEquals(anchor.right, menu.right, 1f)
        assertTrue(menu.top >= 16f)
        assertTrue(menu.bottom <= 640f - 16f)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w240dp-h320dp-mdpi")
    fun narrowRtlWindowClampsMenuAndLongListScrolls() {
        val options = languages + (1..20).map { "extra-$it" to "Language $it" }
        showMenu(direction = LayoutDirection.Rtl, options = options)
        compose.onNodeWithTag("general_language_card").performClick()
        val menu = menuScreenBounds()
        assertTrue(menu.left >= 16f && menu.right <= 240f - 16f)
        assertTrue(menu.top >= 16f && menu.bottom <= 320f - 16f)
        compose.onNodeWithTag("general_language#extra-20").performScrollTo().assertIsDisplayed()
    }

    @Test fun rtlMenuAlignsItsLeftEdgeWithTheTrailingValue() {
        showMenu(direction = LayoutDirection.Rtl)
        compose.onNodeWithTag("general_language_card").performClick()
        val anchor = compose.onNodeWithTag("general_language_value", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val menu = menuScreenBounds()
        assertEquals(anchor.left, menu.left, 1f)
    }
}
