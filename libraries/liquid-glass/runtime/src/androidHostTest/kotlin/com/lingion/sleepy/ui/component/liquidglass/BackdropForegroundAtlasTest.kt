package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropForegroundAtlasTest {
    @Test fun wideDockKeepsBothUnscaledSlotsAlongTheShortAxis() {
        val atlas = BackdropForegroundAtlas.create(Size(375f, 83f))
        assertEquals(IntSize(375, 83), atlas.contentSizePx)
        assertEquals(IntSize(375, 166), atlas.atlasSizePx)
        assertEquals(Rect(0f, 0f, 375f, 83f), atlas.sceneSlot)
        assertEquals(Rect(0f, 83f, 375f, 166f), atlas.maskSlot)
        assertEquals(atlas.sceneSlot.size, atlas.maskSlot.size)
        assertFalse(atlas.sceneSlot.overlaps(atlas.maskSlot))
    }

    @Test fun portraitAndFractionalExtentsKeepIntegerNonoverlappingSlots() {
        val atlas = BackdropForegroundAtlas.create(Size(45.25f, 101.5f))
        assertEquals(IntSize(46, 102), atlas.contentSizePx)
        assertEquals(IntSize(92, 102), atlas.atlasSizePx)
        assertEquals(Rect(0f, 0f, 46f, 102f), atlas.sceneSlot)
        assertEquals(Rect(46f, 0f, 92f, 102f), atlas.maskSlot)
        assertFalse(atlas.sceneSlot.overlaps(atlas.maskSlot))
        val glyphPoint = androidx.compose.ui.geometry.Offset(12.25f, 77.75f)
        val packed = glyphPoint + atlas.maskSlot.topLeft
        assertTrue(atlas.maskSlot.contains(packed))
        assertEquals(glyphPoint, packed - atlas.maskSlot.topLeft)
    }

    @Test fun explicitSampleRectMustFitTheOriginalContentNotTheAtlas() {
        val size = Size(375f, 83f)
        assertTrue(isBackdropForegroundSampleValid(Rect(0f, 10.5f, 375f, 72.5f), size))
        for (invalid in listOf(
            Rect(-1f, 0f, 20f, 20f), Rect(0f, -1f, 20f, 20f),
            Rect(0f, 0f, 376f, 83f), Rect(0f, 0f, 375f, 84f),
            Rect(5f, 0f, 5f, 20f), Rect(0f, 9f, 20f, 8f),
            Rect(Float.NaN, 0f, 20f, 20f), Rect(0f, 0f, Float.POSITIVE_INFINITY, 20f),
        )) assertFalse("$invalid", isBackdropForegroundSampleValid(invalid, size))
    }

    @Test fun invalidAndOverflowingAllocationsAreRejected() {
        for (size in listOf(Size.Zero, Size(-1f, 20f), Size(Float.NaN, 20f),
            Size(20f, Float.POSITIVE_INFINITY), Size(Int.MAX_VALUE.toFloat(), 20f))) {
            var rejected = false
            try { BackdropForegroundAtlas.create(size) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("$size", rejected)
        }
    }

    @Test fun responseRequiresAnExplicitOrderedFiniteRangeAndOpaqueFallback() {
        BackdropForegroundResponse(Color.White, Color.Black, .3f, .8f, Color.Black)
        for ((low, high, fallback) in listOf(
            Triple(.5f, .5f, Color.Black), Triple(.8f, .3f, Color.Black),
            Triple(-.1f, .8f, Color.Black), Triple(.3f, 1.1f, Color.Black),
            Triple(Float.NaN, .8f, Color.Black), Triple(.3f, Float.POSITIVE_INFINITY, Color.Black),
            Triple(.3f, .8f, Color.Transparent),
        )) {
            var rejected = false
            try {
                BackdropForegroundResponse(Color.White, Color.Black, low, high, fallback)
            } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("range $low..$high / $fallback", rejected)
        }
    }
}
