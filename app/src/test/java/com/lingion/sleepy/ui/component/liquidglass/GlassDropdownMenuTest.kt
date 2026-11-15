package com.lingion.sleepy.ui.component.liquidglass

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose popup ownership; these assertions do not establish GPU or native timing parity. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class GlassDropdownMenuTest {
    @get:Rule val compose = createSoftwareWindowComposeRule()
    private val expanded = mutableStateOf(true)
    private var selections = 0
    private var dismissals = 0

    private fun showMenu() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(120.dp, 48.dp)) {
                                GlassDropdownMenu(expanded.value, {
                                    dismissals++
                                    expanded.value = false
                                }, Modifier.width(250.dp).testTag("menu")) {
                                    GlassDropdownMenuItem({ Text("Choice") }, {
                                        selections++
                                        expanded.value = false
                                    }, Modifier.testTag("choice"))
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("choice").assertIsDisplayed()
    }

    private fun popup(): View = WindowInspector.getGlobalWindowViews()
        .single { it.javaClass.simpleName == "PopupLayout" }

    @Test fun selectionRetainsOneWindowForExitAndPreventsStaleActions() {
        showMenu()
        compose.mainClock.autoAdvance = false
        val stale = compose.onNodeWithTag("choice").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.onNodeWithTag("choice").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("choice").assertIsNotEnabled()
        compose.runOnIdle {
            stale()
            assertEquals(1, selections)
            assertEquals(0, dismissals)
            assertEquals(1, WindowInspector.getGlobalWindowViews().count { it.javaClass.simpleName == "PopupLayout" })
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("menu").assertExists()
        compose.mainClock.advanceTimeBy(1_500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test fun reversingExitReusesTheLivePopupInsteadOfRemovingAndRecreatingItsWindow() {
        showMenu()
        var first: View? = null
        compose.runOnIdle { first = popup() }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { expanded.value = false }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { expanded.value = true }
        compose.mainClock.advanceTimeBy(1_500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("choice").assertIsDisplayed()
        compose.runOnIdle {
            assertSame(first, popup())
            assertEquals(0, selections)
            assertEquals(0, dismissals)
        }
    }

    @Test fun popupBackAndOutsideEventsReachTheOwnerWithoutSelecting() {
        showMenu()
        compose.runOnIdle {
            popup().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            popup().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(0, selections)
            expanded.value = true
        }
        compose.onNodeWithTag("choice").assertIsDisplayed()
        compose.runOnIdle {
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
            try { popup().dispatchTouchEvent(event) } finally { event.recycle() }
        }
        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, dismissals)
            assertEquals(0, selections)
            assertTrue(WindowInspector.getGlobalWindowViews().none { it.javaClass.simpleName == "PopupLayout" })
        }
    }
}
