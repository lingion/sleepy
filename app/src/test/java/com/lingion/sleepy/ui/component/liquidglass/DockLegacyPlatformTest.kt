package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.PillNavItemSpec
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** API 29 has no android.graphics.RuntimeShader; composing the fallback must not resolve it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w360dp-h640dp-mdpi")
class DockLegacyPlatformTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coldStartAndTabSelectionWorkWithoutRuntimeShader() {
        val selected = mutableIntStateOf(0)
        val callbacks = mutableListOf<Int>()
        val labels = listOf("Schedule", "Today", "Manage", "Mine")
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L },
                ) {
                    LiquidGlassNavigationBar(
                        items = labels.map { PillNavItemSpec(Icons.Filled.Home, it) },
                        selectedIndex = selected.intValue,
                        onSelect = { callbacks += it; selected.intValue = it },
                        modifier = Modifier.width(316.dp),
                    )
                }
            }
        }
        compose.onAllNodes(tabRole).assertCountEquals(4)
        compose.onNode(tabRole and hasText("Schedule")).assertIsSelected()
        compose.onNode(tabRole and hasText("Mine")).performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(3), callbacks) }
    }
}
