package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cold-start insets may arrive after the first measured navigation frame. */
@OptIn(ExperimentalLayoutApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class GlassAttachedNavigationInsetsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lateNavigationInsetsGrowTheBarWithoutSqueezingItsItems() {
        verifyInsetChange(initialBottom = 0, nextBottom = 64)
    }

    @Test fun navigationModeChangeCanShrinkAnAlreadyMeasuredBar() {
        verifyInsetChange(initialBottom = 64, nextBottom = 24)
    }

    private fun verifyInsetChange(initialBottom: Int, nextBottom: Int) {
        val insets = MutableWindowInsets(WindowInsets(bottom = initialBottom))
        val selected = mutableIntStateOf(0)
        var measuredHeight = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(360.dp, 320.dp)) {
                    val backdrop = rememberCanvasBackdrop {}
                    GlassAttachedNavigationBar(
                        backdrop = backdrop,
                        modifier = Modifier.testTag("navigation"),
                        onHeightChanged = { measuredHeight = it },
                        windowInsets = insets,
                    ) {
                        listOf("Schedule", "Today", "Manage", "Mine").forEachIndexed { index, label ->
                            NavigationBarItem(
                                modifier = Modifier.testTag("tab-$index"),
                                selected = selected.intValue == index,
                                onClick = { selected.intValue = index },
                                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }
        val originalItemHeight = compose.onNodeWithTag("tab-0").fetchSemanticsNode().boundsInRoot.height
        val originalBarHeight = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { insets.insets = WindowInsets(bottom = nextBottom) }
        val changedBarHeight = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot.height
        assertEquals("Bar must follow the new navigation inset after its first measurement",
            originalBarHeight + nextBottom - initialBottom, changedBarHeight, .5f)
        assertEquals("Item content keeps its full height when the system inset changes",
            originalItemHeight, compose.onNodeWithTag("tab-0").fetchSemanticsNode().boundsInRoot.height, .5f)
        compose.runOnIdle { assertEquals(changedBarHeight.toInt(), measuredHeight) }
        compose.onNodeWithText("Mine").performClick()
        compose.onNodeWithTag("tab-3").assertIsSelected()
    }
}
