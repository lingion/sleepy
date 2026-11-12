package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassHazeAtlasTest {
    @Test
    fun dockSlotsKeepFullResolutionAndFitTheAllocatedShortAxis() {
        val pane = Size(822f, 186f)
        val atlas = LiquidGlassShader.hazeAtlas(pane, Offset.Zero, 46.8f, 0f)
        assertEquals(pane, atlas.clearBoundsPx.size)
        assertEquals(pane, atlas.hazeBoundsPx.size)
        assertEquals(atlas.clearBoundsPx.bottom, atlas.hazeBoundsPx.top, 0f)
        assertTrue(atlas.hazeBoundsPx.right <= atlas.sizePx.width)
        assertTrue(atlas.hazeBoundsPx.bottom <= atlas.sizePx.height)
        assertTrue("Gaussian needs its surrounding source before packing", atlas.paddingPx >= 46.8f * 3f)
        assertTrue("short-axis packing must avoid a second Dock-width allocation", atlas.sizePx.width < pane.width * 1.5f)
    }

    @Test
    fun hazeAccountsForBlurAlreadyHeldByTheSourceWithoutBlurringClear() {
        val atlas = LiquidGlassShader.hazeAtlas(Size(64f, 160f), Offset(7f, 11f), 10f, 6f)
        assertEquals(8f, atlas.remainingBlurPx, 0f)
        assertEquals(Offset(7f + atlas.paddingPx, 11f + atlas.paddingPx), atlas.sourceOriginPx)
        assertEquals(atlas.clearBoundsPx.right, atlas.hazeBoundsPx.left, 0f)
        val clear = LiquidGlassShader.sampling(Offset.Zero, 2f, atlas.clearBoundsPx)
        val haze = LiquidGlassShader.sampling(atlas.hazeBoundsPx.topLeft, 2f, atlas.hazeBoundsPx)
        assertEquals(0f, clear[0], 0f)
        assertEquals(-atlas.hazeBoundsPx.left / 2f, haze[0], 0f)
        assertEquals(clear[2], haze[2], 0f)
    }

    @Test
    fun fractionalPanePixelsStillLeaveTwoDisjointCompleteSlots() {
        val atlas = LiquidGlassShader.hazeAtlas(Size(90.2f, 31.2f), Offset.Zero, 0f, 0f)
        assertEquals(32f, atlas.clearBoundsPx.height, 0f)
        assertEquals(32f, atlas.hazeBoundsPx.top, 0f)
        assertTrue(atlas.hazeBoundsPx.bottom <= atlas.sizePx.height)
        assertTrue(atlas.hazeBoundsPx.right <= atlas.sizePx.width)
    }
}
