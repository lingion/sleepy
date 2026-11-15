package com.lingion.sleepy.ui.component

import com.lingion.sleepy.ui.component.liquidglass.LocalDockInputTimeNanos
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
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
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
class SegmentedSwitcherAccessibilityLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun height(tag: String): Float = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

    private fun assertTextFits(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("the visible label must expose its text layout", layouts.isNotEmpty())
        layouts.forEach { layout ->
            // Hard line breaks can retain the paragraph's maximum constraint
            // width while Text wraps its box to the widest actual line. Check
            // visible characters instead of treating that empty width as ink.
            text.forEachIndexed { index, character ->
                if (!character.isWhitespace()) {
                    val glyph = layout.getBoundingBox(index)
                    assertTrue("$text character $index escapes ${layout.size}: $glyph",
                        glyph.left >= -.5f && glyph.right <= layout.size.width + .5f &&
                            glyph.top >= -.5f && glyph.bottom <= layout.size.height + .5f)
                }
            }
            assertTrue("$text needs its complete line height", layout.size.height + 1f >= layout.multiParagraph.height)
        }
    }

    @Test fun ordinaryFontsKeepTheNativeTrackAndRestingLensSizes() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(1f, 1f)) {
                    GlassHost {
                        SegmentedSwitcher(listOf(0 to "One", 1 to "Two"), 0, {},
                            Modifier.width(320.dp).testTag("tabs"))
                    }
                }
            }
        }
        assertEquals(48f, height("tabs"), .5f)
        assertEquals(32f, height("segment-track"), .5f)
        assertEquals(28f, height("segment-thumb"), .5f)
        assertTextFits("One")
    }

    @Test fun largeLabelsAndMultilineBadgesExpandTheTrackAndRestingLensTogether() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(1f, 2f)) {
                    GlassHost {
                        SegmentedSwitcher(
                            listOf(SegmentOption(0, "Time", "New\nPreview\nMode"), SegmentOption(1, "Week")),
                            0, {}, Modifier.width(360.dp).testTag("tabs"),
                        )
                    }
                }
            }
        }
        val track = height("segment-track")
        assertTrue("large text must expand beyond the native minimum", track > 48f)
        assertEquals("the interaction area must contain the full track", track, height("tabs"), .5f)
        assertEquals("the resting lens must follow the enlarged track", track - 4f, height("segment-thumb"), .5f)
        assertTextFits("Time")
        assertTextFits("Week")
        assertTextFits("New\nPreview\nMode")
    }

    @Test fun aTallerCallerKeepsItsInteractionAreaAndCentersTheMeasuredTrack() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides UiStyle.LiquidGlass, LocalDensity provides Density(1f, 2f)) {
                    GlassHost {
                        SegmentedSwitcher(listOf(0 to "Time", 1 to "Week"), 0, {},
                            Modifier.width(320.dp).height(80.dp).testTag("tabs"))
                    }
                }
            }
        }
        assertEquals(80f, height("tabs"), .5f)
        val tabs = compose.onNodeWithTag("tabs").getUnclippedBoundsInRoot()
        val track = compose.onNodeWithTag("segment-track", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the visible track must grow for large labels", (track.bottom - track.top).value > 32f)
        assertEquals((tabs.top.value + tabs.bottom.value) / 2f, (track.top.value + track.bottom.value) / 2f, .5f)
        assertTextFits("Time")
        assertTextFits("Week")
    }
}
