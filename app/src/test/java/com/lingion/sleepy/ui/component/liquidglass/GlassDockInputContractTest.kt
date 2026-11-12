package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.PillNavItemSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class GlassDockInputContractTest {
    @get:Rule val compose = createComposeRule()
    private val selected = mutableIntStateOf(0)
    private val callbacks = mutableListOf<Int>()

    private fun show(rtl: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    LiquidGlassNavigationBar(
                        listOf(
                            PillNavItemSpec(Icons.Filled.Home, "Home"),
                            PillNavItemSpec(Icons.Filled.Search, "Explore"),
                            PillNavItemSpec(Icons.Filled.Settings, "Settings"),
                        ),
                        selected.intValue,
                        { callbacks += it; selected.intValue = it },
                        Modifier.width(316.dp).testTag("dock"),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun paintedBounds(): Rect = compose.onNodeWithTag("dock-lens")
        .fetchSemanticsNode().config[DockLensBoundsInRootPx]

    private fun labelBounds(label: String): Rect = compose.onAllNodesWithText(label, useUnmergedTree = true)
        .fetchSemanticsNodes().first().boundsInRoot

    private fun localCenter(label: String): Offset {
        val dock = compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot
        return labelBounds(label).center - dock.topLeft
    }

    @Test fun rtlPaintedLensMatchesTheRowsForExternalAndTouchSelection() {
        show(rtl = true)
        assertTrue(labelBounds("Home").center.x > labelBounds("Settings").center.x)
        assertEquals(labelBounds("Home").center.x, paintedBounds().center.x, 1f)

        compose.runOnIdle { selected.intValue = 2 }
        compose.waitForIdle()
        assertEquals(labelBounds("Settings").center.x, paintedBounds().center.x, 1f)

        val home = localCenter("Home")
        compose.onNodeWithTag("dock").performTouchInput { click(home) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(0), callbacks); assertEquals(0, selected.intValue) }
        assertEquals(labelBounds("Home").center.x, paintedBounds().center.x, 1f)
    }

    @Test fun rtlDragMovesThePaintedLensToTheLogicalLastDestination() {
        show(rtl = true)
        val home = localCenter("Home")
        val settings = localCenter("Settings")
        compose.onNodeWithTag("dock").performTouchInput {
            down(home)
            moveTo(settings, 300)
            advanceEventTime(250)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(2), callbacks); assertEquals(2, selected.intValue) }
        assertEquals(labelBounds("Settings").center.x, paintedBounds().center.x, 1f)
    }

    @Test fun realPointerCancellationDoesNotNavigateAndTheNextTapWorks() {
        show()
        val initial = paintedBounds()
        val explore = localCenter("Explore")
        val dock = compose.onNodeWithTag("dock")
        dock.performTouchInput { down(explore); advanceEventTime(80); cancel() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(callbacks.isEmpty()); assertEquals(0, selected.intValue) }
        assertEquals(initial.center.x, paintedBounds().center.x, 1f)
        assertEquals(initial.width, paintedBounds().width, 1f)

        dock.performTouchInput { click(explore) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1), callbacks); assertEquals(1, selected.intValue) }
    }

    @Test fun realPointerCancellationDuringDragDoesNotCommitItsDestination() {
        show()
        val home = localCenter("Home")
        val settings = localCenter("Settings")
        compose.onNodeWithTag("dock").performTouchInput {
            down(home)
            moveTo(settings, 300)
            cancel()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(callbacks.isEmpty()); assertEquals(0, selected.intValue) }
        assertEquals(labelBounds("Home").center.x, paintedBounds().center.x, 1f)
    }

    @Test fun ordinaryVerticalExitAndUpStillCommitsThePressedDestination() {
        show()
        val explore = localCenter("Explore")
        compose.onNodeWithTag("dock").performTouchInput {
            down(explore)
            moveTo(explore + Offset(0f, -120f), 200)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1), callbacks); assertEquals(1, selected.intValue) }
    }
}
