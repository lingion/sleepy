package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.PillNavItemSpec
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Production input, carrier transform, and semantics wiring; synthetic geometry contracts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w600dp-h800dp-mdpi")
class GlassDockParentTranslationContractTest {
    @get:Rule val compose = createComposeRule()
    private val receiptNanos = AtomicLong(0)
    private val selected = mutableIntStateOf(0)
    private val callbacks = mutableListOf<Int>()

    private fun show(count: Int = 4, rtl: Boolean = false, callerScale: Float = 1f) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDockInputTimeNanos provides receiptNanos::get,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.graphicsLayer { scaleX = callerScale }) {
                            LiquidGlassNavigationBar(
                                listOf(
                                    PillNavItemSpec(Icons.Filled.Home, "Home"),
                                    PillNavItemSpec(Icons.Filled.Search, "Explore"),
                                    PillNavItemSpec(Icons.Filled.Settings, "Settings"),
                                    PillNavItemSpec(Icons.Filled.Favorite, "Favorite"),
                                ).take(count),
                                selected.intValue,
                                { callbacks += it; selected.intValue = it },
                                Modifier.width(375.dp).testTag("dock"),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun carrier(): Rect = compose.onNodeWithTag("dock-parent").fetchSemanticsNode().boundsInRoot
    private fun dock(): Rect = compose.onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot
    private fun destination(label: String): Offset = compose.onAllNodesWithText(label, useUnmergedTree = true)
        .fetchSemanticsNodes().first().boundsInRoot.center - dock().topLeft

    private fun down(at: Offset) {
        receiptNanos.set((compose.mainClock.currentTime + 1) * 1_000_000L)
        compose.onNodeWithTag("dock").performTouchInput { down(at) }
    }

    private fun move(at: Offset) {
        receiptNanos.set((compose.mainClock.currentTime + 1) * 1_000_000L)
        compose.onNodeWithTag("dock").performTouchInput { moveTo(at, 16) }
    }

    private fun settleHeld() {
        // Drain the snapshotFlow wake before establishing the seven-spring
        // Press origin. A sole DOWN otherwise can first reach that origin at
        // the end of advanceTimeBy, leaving no following animation frame.
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
    }

    private fun cancel() {
        receiptNanos.set((compose.mainClock.currentTime + 1) * 1_000_000L)
        compose.onNodeWithTag("dock").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test fun productionFourTabInBoundsDragMovesWholeCarrierAndCancelReturnsIt() {
        show()
        val rest = carrier()
        assertEquals("whole carrier width", 333f, rest.width, .01f)
        assertEquals("carrier includes vertical shadow room", 86f, rest.height, .01f)
        val home = destination("Home")
        down(home)
        move(home + Offset(100f, 0f))

        settleHeld()

        assertEquals(1.028793253051172f, carrier().center.x - rest.center.x, .003f)
        compose.runOnIdle { assertTrue(callbacks.isEmpty()) }
        cancel()
        assertEquals(rest.center.x, carrier().center.x, .003f)
        compose.runOnIdle { assertTrue(callbacks.isEmpty()); assertEquals(0, selected.intValue) }
    }

    @Test fun productionFourTabSubthresholdMoveStillFeedsThePhysicalParent() {
        show()
        val rest = carrier()
        val home = destination("Home")
        down(home)
        move(home + Offset(3f, 0f))

        settleHeld()

        // A 3px move stays below the 4px lens-drag gate, but is actual finger input.
        assertEquals(.030863797591535f, carrier().center.x - rest.center.x, .002f)
        cancel()
    }

    @Test fun threeTabSubthresholdInputKeepsItsExistingParentTranslation() {
        show(count = 3)
        val rest = carrier()
        val home = destination("Home")
        down(home)
        move(home + Offset(3f, 0f))

        settleHeld()

        assertEquals(0f, carrier().center.x - rest.center.x, .002f)
        cancel()
    }

    @Test fun unselectedDirectPressCanMoveTheLensWithoutMovingTheParent() {
        show()
        val rest = carrier()
        val dockAtDown = dock()
        val exploreBounds = compose.onAllNodesWithText("Explore", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }
        val tap = destination("Explore")
        down(tap)

        settleHeld()

        assertEquals(0f, carrier().center.x - rest.center.x, .002f)
        val lens = compose.onNodeWithTag("dock-lens").fetchSemanticsNode().config[DockLensBoundsInRootPx]
        assertTrue("direct preview still moves the lens; Explore=$exploreBounds dock=$dockAtDown " +
            "rest=$rest tapLocal=$tap tapRoot=${tap + dockAtDown.topLeft} pressedLens=$lens " +
            "pressedCarrier=${carrier()}", lens.center.x > rest.left + rest.width / 3)
        receiptNanos.set((compose.mainClock.currentTime + 1) * 1_000_000L)
        compose.onNodeWithTag("dock").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1), callbacks) }
        assertEquals(rest.center.x, carrier().center.x, .002f)
    }

    @Test fun rtlParentKeepsPositivePhysicalScreenDirection() {
        show(rtl = true)
        val rest = carrier()
        val settings = destination("Settings")
        down(settings)
        move(settings + Offset(60f, 0f))

        settleHeld()

        assertEquals(.617275951830703f, carrier().center.x - rest.center.x, .003f)
        cancel()
    }

    @Test fun callerScalePointEightKeepsWindowInputAndWindowOutputUnits() {
        assertCallerScale(0.8f)
    }

    @Test fun callerScaleOnePointTwoKeepsWindowInputAndWindowOutputUnits() {
        assertCallerScale(1.2f)
    }

    private fun assertCallerScale(scale: Float) {
        show(callerScale = scale)
        val rest = carrier()
        val home = destination("Home")
        down(home)
        // Test touch positions are root pixels relative to node bounds, so this
        // remains a 100px physical move under either static caller transform.
        move(home + Offset(100f, 0f))

        settleHeld()

        assertEquals("same physical response under caller scale $scale",
            1.028793253051172f, carrier().center.x - rest.center.x, .003f)
        cancel()
    }
}
