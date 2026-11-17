package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.ui.component.PillNavItemSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs ART's missing-platform-class path on devices where RuntimeShader does not exist. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 32)
class DockLegacyPlatformDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restingPressedAndSelectedDockUseTheLegacyRenderer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNull("Old Android must avoid creating an API 33 optical shader", createDockLensShader(context))
        val selected = mutableIntStateOf(0)
        val callbacks = mutableListOf<Int>()
        val labels = listOf("Schedule", "Today", "Manage", "Mine")
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.setContent {
            MaterialTheme {
                // Compose advances its frame clock virtually during synchronization;
                // input timestamps must share that clock so a held spring can settle.
                CompositionLocalProvider(
                    LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L },
                ) {
                    LiquidGlassNavigationBar(
                        items = labels.map { PillNavItemSpec(Icons.Filled.Home, it) },
                        selectedIndex = selected.intValue,
                        onSelect = { callbacks += it; selected.intValue = it },
                        modifier = Modifier.width(316.dp).testTag("legacy-dock"),
                    )
                }
            }
        }
        compose.onAllNodes(tabRole).assertCountEquals(4)
        compose.onNode(tabRole and hasText("Schedule")).assertIsSelected()
        assertFiniteLens()

        val dock = compose.onNodeWithTag("legacy-dock")
        val mine = compose.onNode(tabRole and hasText("Mine"))
        val point = mine.fetchSemanticsNode().boundsInRoot.center - dock.fetchSemanticsNode().boundsInRoot.topLeft
        dock.performTouchInput { down(point) }
        compose.mainClock.advanceTimeBy(400)
        assertFiniteLens()
        dock.performTouchInput { up() }
        mine.assertIsSelected()
        assertFiniteLens()
        compose.runOnIdle { assertEquals(listOf(3), callbacks) }

        compose.onNode(tabRole and hasText("Today")).performClick().assertIsSelected()
        assertFiniteLens()
        compose.runOnIdle { assertEquals(listOf(3, 1), callbacks) }
    }

    private fun assertFiniteLens() {
        val bounds = compose.onNodeWithTag("dock-lens").fetchSemanticsNode().config[DockLensBoundsInRootPx]
        assertTrue("Legacy lens remains drawable", bounds.width.isFinite() && bounds.width > 0f &&
            bounds.height.isFinite() && bounds.height > 0f)
    }
}
