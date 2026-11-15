package com.lingion.sleepy.ui.component

import com.lingion.sleepy.ui.component.liquidglass.LocalDockInputTimeNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SegmentedSwitcherBoundaryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun twoOptionsCanDragBackToTheFirst() {
        val selected = mutableStateOf(1)
        val changes = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        SegmentedSwitcher(
                            options = listOf(0 to "Grid", 1 to "List"), selected = selected.value,
                            onSelect = { changes += it; selected.value = it },
                            modifier = Modifier.width(240.dp).testTag("tabs"),
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("tabs").performTouchInput {
            swipe(Offset(width * .75f, centerY), Offset(width * .25f, centerY), 300)
        }
        compose.runOnIdle { assertEquals(listOf(0), changes); assertEquals(0, selected.value) }
    }

    @Test fun dragStartingOnAnUnselectedSegmentDoesNotSelect() {
        val changes = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        SegmentedSwitcher(
                            options = listOf(0 to "Grid", 1 to "Week", 2 to "Day"), selected = 0,
                            onSelect = { changes += it }, modifier = Modifier.width(300.dp).testTag("tabs"),
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("tabs").performTouchInput {
            swipe(Offset(width * .5f, centerY), Offset(width + 40f, centerY), 300)
        }
        compose.runOnIdle { assertEquals(emptyList<Int>(), changes) }
        compose.onNodeWithText("Week").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(1), changes) }
    }

    @Test fun optionsCanDisappearAndReturnWithoutStaleSelection() {
        val options = mutableStateOf(listOf(0 to "Grid", 1 to "List"))
        val changes = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                Box {
                    SegmentedSwitcher(
                        options = options.value, selected = 0, onSelect = { changes += it },
                        modifier = Modifier.width(240.dp).testTag("tabs"),
                    )
                }
            }
        }
        compose.runOnIdle { options.value = emptyList() }
        compose.onNodeWithTag("tabs").assertDoesNotExist()
        compose.runOnIdle { options.value = listOf(0 to "Grid", 1 to "List") }
        compose.onNodeWithText("List").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(1), changes) }
    }
}
