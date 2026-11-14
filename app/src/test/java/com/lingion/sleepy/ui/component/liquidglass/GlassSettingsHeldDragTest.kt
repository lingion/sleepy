package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.lingion.sleepy.ui.component.SettingsFlatCard
import com.lingion.sleepy.ui.component.SettingToggleRow
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.AppPrefs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h640dp-port-xhdpi")
class GlassSettingsHeldDragTest {
    @get:Rule val compose = createComposeRule()

    private fun drag(switch: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPrefs.setUiStyle(context, UiStyle.LiquidGlass)
        lateinit var list: LazyListState
        var checked by mutableStateOf(false)
        var selected by mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }) {
                SleepyThemeProvider(darkTheme = false) {
                    list = rememberLazyListState(initialFirstVisibleItemIndex = 4)
                    LazyColumn(Modifier.fillMaxSize().testTag("page"), state = list) {
                        items(5) { Spacer(Modifier.height(100.dp)) }
                        item {
                            Box(Modifier.padding(16.dp).testTag("card")) {
                                if (switch) SettingsFlatCard("Toggle") {
                                    SettingToggleRow("Toggle", checked, { checked = it }, dbg = "control")
                                } else SettingsFlatCard("Tabs", options = listOf("First", "Second"),
                                    selectedKey = selected, onSelect = { selected = it })
                            }
                        }
                        items(20) { Spacer(Modifier.height(100.dp)) }
                    }
                }
            }
        }
        val control = if (switch) compose.onNodeWithTag("control") else compose.onNode(
            SemanticsMatcher("segmented group") { it.config.contains(SemanticsProperties.SelectableGroup) }, useUnmergedTree = true)
        val origin = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        val bounds = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        control.performTouchInput { down(Offset(width * .25f, centerY)) }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(100)
        control.performTouchInput { moveBy(Offset(width * .55f, 0f), 32) }
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(100)
        repeat(240) { frame ->
            control.performTouchInput { moveBy(Offset(0f, 2f), 16) }
            compose.runOnIdle { }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals("page jumped at slow-move frame $frame", origin,
                    list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
                assertFalse(checked)
                assertEquals(0, selected)
            }
        }
        repeat(40) { interval ->
            compose.mainClock.advanceTimeBy(100)
            compose.runOnIdle {
                assertEquals("page jumped during hold interval $interval", origin,
                    list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
            }
        }
        // Also flush Android Handler deadlines, independent of the Compose animation clock.
        compose.runOnUiThread { ShadowLooper.idleMainLooper(4_000) }
        compose.runOnIdle {
            assertEquals(origin, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
        }
        assertEquals(bounds, compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot)
        control.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals(origin, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
            if (switch) assertTrue(checked) else assertEquals(1, selected)
        }
    }

    @Test fun settingsToggleHoldsPageDuringSlowVerticalDragAndPause() = drag(true)
    @Test fun settingsTabHoldsPageDuringSlowVerticalDragAndPause() = drag(false)
}
