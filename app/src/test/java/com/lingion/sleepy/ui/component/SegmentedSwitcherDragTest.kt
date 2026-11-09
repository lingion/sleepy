package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SegmentedSwitcherDragTest {
    @get:Rule val compose = createComposeRule()
    private val selected = mutableStateOf(0)
    private val changes = mutableListOf<Int>()

    private fun show(rtl: Boolean = false, accept: Boolean = true) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides UiStyle.LiquidGlass,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    GlassHost {
                        SegmentedSwitcher(
                            options = listOf(0 to "Grid", 1 to "Week", 2 to "Day"), selected = selected.value,
                            onSelect = { changes += it; if (accept) selected.value = it },
                            modifier = Modifier.width(300.dp).testTag("tabs"),
                        )
                    }
                }
            }
        }
    }

    @Test fun classicModeDoesNotTakeOverDraggingOrFloatTheThumb() {
        val classicSelected = mutableStateOf(0)
        val classicChanges = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUiStyle provides UiStyle.Classic,
                ) {
                    SegmentedSwitcher(
                        options = listOf(0 to "Grid", 1 to "Week", 2 to "Day"),
                        selected = classicSelected.value,
                        onSelect = { classicChanges += it; classicSelected.value = it },
                        modifier = Modifier.width(300.dp).testTag("classic-tabs"),
                    )
                }
            }
        }
        val tabs = compose.onNodeWithTag("classic-tabs")
        val before = tabs.fetchSemanticsNode().boundsInRoot
        tabs.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width * 5f / 6f, centerY), 250)
            up()
        }
        compose.runOnIdle {
            assertEquals("Classic must not install the Liquid Glass drag selection", emptyList<Int>(), classicChanges)
            assertEquals(0, classicSelected.value)
        }
        assertEquals("Classic pointer motion must not resize the switcher track", before, tabs.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun dragCommitsNearestSegmentOnlyOnRelease() {
        show()
        val tabs = compose.onNodeWithTag("tabs")
        tabs.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width * 5f / 6f, centerY), 250)
        }
        compose.runOnIdle { assertEquals(emptyList<Int>(), changes) }
        tabs.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(2), changes); assertEquals(2, selected.value) }
    }

    @Test fun cancelledDragDoesNotSelect() {
        show()
        compose.onNodeWithTag("tabs").performTouchInput {
            down(Offset(width / 6f, centerY)); moveTo(Offset(width * 5f / 6f, centerY), 250); cancel()
        }
        compose.runOnIdle { assertEquals(emptyList<Int>(), changes); assertEquals(0, selected.value) }
    }

    @Test fun rtlDragUsesLogicalOptionOrder() {
        show(rtl = true)
        compose.onNodeWithTag("tabs").performTouchInput {
            swipe(Offset(width * 5f / 6f, centerY), Offset(width / 6f, centerY), 300)
        }
        compose.runOnIdle { assertEquals(listOf(2), changes) }
    }

    @Test fun tapsStillSelectOnce() {
        show()
        compose.onNodeWithText("Week").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(1), changes) }
    }

    @Test fun rejectedDragSettlesBackAndCanBeDraggedAgain() {
        show(accept = false)
        val tabs = compose.onNodeWithTag("tabs")
        repeat(2) {
            tabs.performTouchInput { swipe(Offset(width / 6f, centerY), Offset(width / 2f, centerY), 300) }
            compose.waitForIdle()
        }
        compose.runOnIdle { assertEquals(listOf(1, 1), changes); assertEquals(0, selected.value) }
    }
}
