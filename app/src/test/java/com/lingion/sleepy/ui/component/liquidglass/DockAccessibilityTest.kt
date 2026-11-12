package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.PillNavItemSpec
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class DockAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    private val selected = mutableIntStateOf(0)
    private val callbacks = mutableListOf<Int>()
    private val labels = listOf("Home", "Courses", "Explore", "Settings")
    private val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun show() {
        compose.setContent {
            MaterialTheme {
                LiquidGlassNavigationBar(
                    labels.map { PillNavItemSpec(Icons.Filled.Home, it) },
                    selected.intValue,
                    { callbacks += it; selected.intValue = it },
                    Modifier.width(316.dp).testTag("dock"),
                )
            }
        }
    }

    private fun tab(label: String) = compose.onNode(tabRole and hasText(label))

    @Test fun fourDestinationsExposeOneTabAndOneLabelEachAcrossOpticalLayers() {
        show()
        compose.onAllNodes(tabRole, useUnmergedTree = true).assertCountEquals(4)
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(4)
        labels.forEachIndexed { index, label ->
            // The raw unmerged tree intentionally retains optical painting
            // children; the accessible merged tree excludes the cleared copy.
            compose.onAllNodesWithText(label).assertCountEquals(1)
            tab(label).assertHasClickAction()
            if (index == 0) tab(label).assertIsSelected() else tab(label).assertIsNotSelected()
        }
        compose.runOnIdle { assertEquals(emptyList<Int>(), callbacks) }
    }

    @Test fun semanticActionsUseTheControlledSelectionAndCommitEachChangeOnce() {
        show()
        tab("Settings").performClick().assertIsSelected()
        tab("Home").assertIsNotSelected()
        tab("Settings").performClick()
        tab("Courses").performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(1, selected.intValue)
            assertEquals(listOf(3, 1), callbacks)
        }
    }

    @Test fun enterSpaceAndDpadCenterCommitOnlyOneCallbackPerDestination() {
        show()
        listOf("Settings" to Key.Enter, "Home" to Key.Spacebar, "Explore" to Key.DirectionCenter)
            .forEach { (label, key) ->
                val destination = tab(label)
                destination.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                destination.performKeyInput { pressKey(key) }.assertIsSelected()
            }
        tab("Explore").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle {
            assertEquals(2, selected.intValue)
            assertEquals(listOf(3, 0, 2), callbacks)
        }
    }

    @Test fun heldKeyboardActivationWaitsForReleaseAndFocusLossCancelsIt() {
        show()
        val courses = tab("Courses")
        courses.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        courses.performKeyInput { keyDown(Key.Enter); advanceEventTime(500) }
        compose.runOnIdle { assertEquals(emptyList<Int>(), callbacks) }
        tab("Settings").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        tab("Settings").performKeyInput { keyUp(Key.Enter) }
        compose.runOnIdle { assertEquals(emptyList<Int>(), callbacks) }
        tab("Settings").performKeyInput { pressKey(Key.Enter) }.assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(3), callbacks) }
    }

    @Test fun pointerTapAndSemanticActionShareSelectionWithoutDuplicateNavigation() {
        show()
        val dock = compose.onNodeWithTag("dock")
        val point = tab("Courses").fetchSemanticsNode().boundsInRoot.center -
            dock.fetchSemanticsNode().boundsInRoot.topLeft
        dock.performTouchInput { click(point) }
        tab("Courses").assertIsSelected().performClick()
        tab("Settings").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(1, 3), callbacks) }
    }
}
