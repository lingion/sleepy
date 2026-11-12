package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
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

/** Same available width and normal text size as the real iOS 26.5 reference. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w375dp-h667dp-mdpi")
class NativeDockLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeTabsFitInsideFixedCallerWidth() = verify(3, fill = false)
    @Test fun threeTabsFitInsideFillWidthCaller() = verify(3, fill = true)
    @Test fun fourTabsOverlapWithoutShrinkingTheLensAtFixedWidth() = verify(4, fill = false)
    @Test fun fourTabsOverlapWithoutShrinkingTheLensAtFillWidth() = verify(4, fill = true)

    private fun verify(count: Int, fill: Boolean) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(351.dp)) {
                    LiquidGlassNavigationBar(
                        List(count) { PillNavItemSpec(Icons.Filled.Home, "Tab $it") },
                        0, {}, if (fill) Modifier.fillMaxWidth() else Modifier.width(351.dp),
                    )
                }
            }
        }
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .fetchSemanticsNodes().sortedBy { it.boundsInRoot.center.x }
        assertEquals(count, tabs.size)
        val expectedPitch = if (count == 3) 86f else 69f
        tabs.zipWithNext().forEach { (a, b) ->
            assertEquals("actual tab centers retain native pitch", expectedPitch,
                b.boundsInRoot.center.x - a.boundsInRoot.center.x, .6f)
        }
        val lens = compose.onNodeWithTag("dock-lens", useUnmergedTree = true)
            .fetchSemanticsNode().config[DockLensBoundsInRootPx]
        assertEquals("native lens width survives constrained destinations", 94f, lens.width, .6f)
        assertEquals("natural fitting height preserves icon and label space", 54f, lens.height, .6f)
    }
}
