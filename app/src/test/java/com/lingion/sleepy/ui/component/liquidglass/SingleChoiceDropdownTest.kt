package com.lingion.sleepy.ui.component.liquidglass

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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

/** Uses Material's expressive button, grouped menu and actual popup lifetime. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class SingleChoiceDropdownTest {
    @get:Rule val compose = createSoftwareWindowComposeRule()
    private val options = listOf("table:42" to "Morning", "none" to "None", "other" to "Other date")
    private val selected = mutableStateOf("table:42")
    private val enabled = mutableStateOf(true)
    private val choices = mutableStateOf(options)
    private val selections = mutableListOf<String>()
    private val dismissals = mutableListOf<String?>()

    private fun popups(): List<View> = WindowInspector.getGlobalWindowViews()
        .filter { it.javaClass.simpleName == "PopupLayout" }

    private fun show(style: UiStyle = UiStyle.Classic, label: String? = null, preserveOtherBinding: Boolean = false) {
        compose.setContent {
            MaterialExpressiveTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost {
                        Box(Modifier.fillMaxSize().padding(16.dp)) {
                            SingleChoiceDropdown(
                                title = "Timetable",
                                selectedId = selected.value,
                                options = choices.value,
                                onSelect = { id ->
                                    selections += id
                                    if (id != "other" || !preserveOtherBinding) selected.value = id
                                },
                                enabled = enabled.value,
                                selectedLabel = label,
                                tagPrefix = "choice",
                                onDismissed = { id ->
                                    assertTrue("Completion follows the actual Popup removal", popups().isEmpty())
                                    dismissals += id
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Test fun materialUsesAnExpressiveButtonAndOfficialGroupedSingleSelection() {
        show()
        compose.onNodeWithTag("choice_card").performClick()
        compose.onNodeWithTag("choice_menu").assertIsDisplayed()
        compose.onNodeWithTag("choice#table:42").assertIsSelected()
        compose.onNodeWithTag("choice#none").assertIsNotSelected()
        compose.runOnIdle { assertEquals(1, popups().size) }
    }

    @Test fun materialLongListMatchesItsAnchorAndScrollsToTheLastChoice() {
        choices.value = (1..40).map { "entry:$it" to "Timetable $it" }
        selected.value = "entry:1"
        show()
        val anchor = compose.onNodeWithTag("choice_card").fetchSemanticsNode().boundsInWindow
        compose.onNodeWithTag("choice_card").performClick()
        val menu = compose.onNodeWithTag("choice_menu").fetchSemanticsNode().boundsInWindow
        assertEquals("The official sizing helper matches the button width", anchor.width, menu.width, 1f)
        assertTrue("A long menu is constrained to the visible window", menu.height < 640f)
        compose.onNodeWithTag("choice#entry:40").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("entry:40"), selections)
            assertEquals(listOf("entry:40"), dismissals)
        }
    }

    @Test fun selectionCommitsOnceAndCompletionFollowsPopupDisposal() {
        show()
        compose.onNodeWithTag("choice_card").performClick()
        compose.onNodeWithTag("choice#none").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        val stale = compose.onNodeWithTag("choice#none").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        compose.onNodeWithTag("choice#none").performClick()
        compose.runOnIdle {
            stale()
            stale()
            assertEquals(listOf("none"), selections)
            assertTrue(dismissals.isEmpty())
            assertEquals(1, popups().size)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("choice#other").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("none"), selections)
            assertEquals(listOf("none"), dismissals)
        }
        compose.onNodeWithTag("choice_card").performClick()
        compose.onNodeWithTag("choice#none").assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf("none"), selections)
            assertEquals(listOf("none"), dismissals)
            assertEquals("A new popup can open after the previous session completes", 1, popups().size)
        }
        compose.onNodeWithTag("choice_menu").assertIsDisplayed()
        compose.onNodeWithTag("choice#table:42").performClick()
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("none", "table:42"), selections)
            assertEquals(listOf("none", "table:42"), dismissals)
        }
    }

    @Test fun outsideAndBackEachCompleteOnceWithoutSelection() {
        show()
        compose.onNodeWithTag("choice_card").performClick()
        compose.onNodeWithTag("choice_menu").assertIsDisplayed()
        compose.runOnIdle {
            val now = SystemClock.uptimeMillis()
            val outside = MotionEvent.obtain(now, now, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
            try { popups().single().dispatchTouchEvent(outside) } finally { outside.recycle() }
        }
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(selections.isEmpty())
            assertEquals(listOf<String?>(null), dismissals)
        }
        compose.onNodeWithTag("choice_card").performClick()
        compose.onNodeWithTag("choice_menu").assertIsDisplayed()
        compose.runOnIdle {
            popups().single().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            popups().single().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(selections.isEmpty())
            assertEquals(listOf<String?>(null, null), dismissals)
        }
    }

    @Test fun disabledAndEmptyEntriesNeverOpen() {
        enabled.value = false
        show()
        compose.onNodeWithTag("choice_card").assertIsNotEnabled().performTouchInput { down(center); up() }
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.runOnIdle { enabled.value = true; choices.value = emptyList() }
        compose.onNodeWithTag("choice_card").assertIsNotEnabled().performTouchInput { down(center); up() }
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(popups().isEmpty())
            assertTrue(selections.isEmpty())
            assertTrue(dismissals.isEmpty())
        }
    }

    @Test fun explicitLabelCanDescribeASelectionOutsideTheOptionList() {
        selected.value = "unlisted-date"
        show(label = "November 14")
        compose.onNodeWithText("November 14").assertIsDisplayed()
        compose.onNodeWithTag("choice_card").performClick()
        options.forEach { (id, _) -> compose.onNodeWithTag("choice#$id").assertIsNotSelected() }
    }

    @Test fun disablingAnOpenMenuCancelsAfterItsPopupLeaves() {
        show()
        compose.onNodeWithTag("choice_card").performClick()
        compose.onNodeWithTag("choice_menu").assertIsDisplayed()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(selections.isEmpty())
            assertEquals(listOf<String?>(null), dismissals)
        }
    }

    @Test fun liquidGlassDelegatesArbitraryOptionIdsToTheSharedMorph() {
        show(style = UiStyle.LiquidGlass)
        compose.onNodeWithTag("choice_card").performClick()
        compose.onNodeWithTag("choice#table:42").assertIsSelected()
        compose.onNodeWithTag("choice#none").performClick()
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("none"), selections)
            assertEquals(listOf("none"), dismissals)
        }
    }

    @Test fun liquidGlassExplicitLabelSurvivesClearAndOtherWithoutChangingItsBinding() {
        show(style = UiStyle.LiquidGlass, label = "Displayed mapping", preserveOtherBinding = true)
        compose.onNodeWithText("Displayed mapping").assertIsDisplayed()
        compose.onNodeWithTag("choice_card").performClick()
        compose.onNodeWithTag("choice#none").performClick()
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.onNodeWithText("Displayed mapping").assertIsDisplayed()
        compose.runOnIdle { assertEquals("none", selected.value) }

        compose.onNodeWithTag("choice_card").performClick()
        compose.onNodeWithTag("choice#other").performClick()
        compose.onNodeWithTag("choice_menu").assertDoesNotExist()
        compose.onNodeWithText("Displayed mapping").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("The secondary date action leaves the current binding intact", "none", selected.value)
            assertEquals(listOf("none", "other"), selections)
            assertEquals(listOf("none", "other"), dismissals)
        }
    }
}
