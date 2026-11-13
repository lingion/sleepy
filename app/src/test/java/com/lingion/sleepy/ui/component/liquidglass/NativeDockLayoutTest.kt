package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.PillNavItemSpec
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Normal text size; three-item standalone and four-item SwiftUI parent references. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w375dp-h667dp-mdpi")
class NativeDockLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeTabsFitInsideFixedCallerWidth() = verify(3, fill = false)
    @Test fun threeTabsFitInsideFillWidthCaller() = verify(3, fill = true)
    @Test fun fourTabsMatchSwiftUIParentAtFixedWidth() = verify(4, fill = false)
    @Test fun fourTabsMatchSwiftUIParentAtFillWidth() = verify(4, fill = true)

    private fun verify(count: Int, fill: Boolean) {
        val callerWidth = if (count == 3) 351.dp else 375.dp
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L },
                ) {
                    Box(Modifier.width(callerWidth)) {
                        LiquidGlassNavigationBar(
                            List(count) { PillNavItemSpec(Icons.Filled.Home, "Tab $it") },
                            0, {}, if (fill) Modifier.fillMaxWidth() else Modifier.width(callerWidth),
                        )
                    }
                }
            }
        }
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .fetchSemanticsNodes().sortedBy { it.boundsInRoot.center.x }
        assertEquals(count, tabs.size)
        val expectedPitch = if (count == 3) 86f else 78.9166667f
        tabs.zipWithNext().forEach { (a, b) ->
            assertEquals("actual tab centers retain native pitch", expectedPitch,
                b.boundsInRoot.center.x - a.boundsInRoot.center.x, .6f)
        }
        val carrier = compose.onNodeWithTag("dock-lens", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertEquals("visible platter width", if (count == 3) 274f else 333f,
            carrier.boundsInRoot.width, .6f)
        val lens = carrier.config[DockLensBoundsInRootPx]
        assertEquals("native lens width for the matching parent", if (count == 3) 94f else 88.25f,
            lens.width, .6f)
        assertEquals("natural fitting height preserves icon and label space", 54f, lens.height, .6f)
        if (count == 4) {
            assertEquals("SwiftUI reference first lens left", 25f, lens.left, .6f)
            assertEquals("SwiftUI reference first lens center", 69.125f, lens.center.x, .6f)
        }
    }
}
