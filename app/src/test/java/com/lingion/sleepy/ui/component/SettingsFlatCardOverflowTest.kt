package com.lingion.sleepy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.SleepyTypography
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w400dp-h800dp-mdpi")
class SettingsFlatCardOverflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fastDragKeepsTheRealSettingsCardsOverflowingLensUnclipped() {
        compose.setContent {
            MaterialTheme(typography = SleepyTypography) {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.fillMaxSize()) {
                        // A 320dp settings page has a 288dp card and 256dp track.
                        Column(Modifier.width(320.dp).padding(horizontal = 16.dp, vertical = 64.dp)) {
                            Box(Modifier.testTag("card")) {
                                var selected by remember { mutableIntStateOf(0) }
                                SettingsFlatCard(
                                    title = "时间对齐方式",
                                    options = listOf("短横线对齐", "时间整体居中"),
                                    selectedKey = selected,
                                    onSelect = { selected = it },
                                )
                            }
                        }
                    }
                }
            }
        }
        val track = compose.onNode(SemanticsMatcher("segmented group") {
            it.config.contains(SemanticsProperties.SelectableGroup)
        }, useUnmergedTree = true)
        val lens = compose.onNodeWithTag("segment-thumb", useUnmergedTree = true)
        val cardRight = compose.onNodeWithTag("card").getUnclippedBoundsInRoot().right.value
        val trackBounds = track.getUnclippedBoundsInRoot()
        assertEquals("fixture must use the real wrapped choices", 256f,
            (trackBounds.right - trackBounds.left).value, .5f)
        compose.mainClock.autoAdvance = false
        fun frame() {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        var overflowFrames = 0
        fun assertVisibleBounds() {
            val full = lens.getUnclippedBoundsInRoot()
            if (full.right.value > cardRight + 2f) {
                overflowFrames++
                val visible = lens.fetchSemanticsNode().boundsInRoot
                val expectedRight = with(compose.density) { full.right.toPx() }
                assertEquals("the lifted lens extends past the card, but its ancestor clips it: full=$full visible=$visible cardRight=$cardRight",
                    expectedRight, visible.right, with(compose.density) { .5.dp.toPx() })
            }
        }
        track.performTouchInput { down(Offset(width / 4f, centerY)) }
        try {
            repeat(32) { frame() }
            repeat(10) { index ->
                track.performTouchInput {
                    moveTo(Offset(width * (.25f + .5f * (index + 1) / 10f), centerY), 16)
                }
                frame()
                assertVisibleBounds()
            }
            repeat(18) { frame(); assertVisibleBounds() }
            assertTrue("the gesture must actually reproduce a lens extending beyond the 16dp card inset", overflowFrames > 0)
        } finally {
            track.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
        }
    }
}
