package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabViewDockGeometryTest {
    @Test
    fun fourTabsMatchMeasuredSwiftUIParentAt375Points() {
        // iOS 26.5, swiftui-dock-parent-61: holder 375, platter x21/w333,
        // selected AX x25/w88.25. These are observations of this parent only.
        val layout = tabViewDockLayout(325f, 4)
        assertEquals(88.25f, layout.lens, .0001f)
        assertEquals(78.9166667f, layout.pitch, .0001f)
        val expectedLefts = listOf(25f, 103.9166667f, 182.8333333f, 261.75f)
        expectedLefts.forEachIndexed { index, expected ->
            assertEquals(expected, 21f + lensLeft(layout, index), .0001f)
        }
        assertEquals(69.125f, 21f + lensLeft(layout, 0) + layout.lens / 2f, .0001f)
    }

    @Test
    fun threeTabsRetainTheirNaturalIntrinsicExtent() {
        // The independent natural three-item reference remains 274/94/86.
        val layout = tabViewDockLayout(266f, 3)
        assertEquals(94f, layout.lens, .0001f)
        assertEquals(86f, layout.pitch, .0001f)
        assertEquals(4f, lensLeft(layout, 0), .0001f)
        assertEquals(270f, lensLeft(layout, 2) + layout.lens, .0001f)
    }

    @Test
    fun responsiveAndroidPolicyKeepsEvenCentersAndBothEdgeInsets() {
        // No native measurements are asserted for these other widths/counts.
        for (count in 1..5) {
            for (callerWidth in listOf(240f, 280f, 320f, 351f, 375f, 430f, 600f)) {
                val barWidth = minOf(callerWidth - 42f, 86f * count + 16f)
                val span = barWidth - 8f
                val layout = tabViewDockLayout(span, count)
                assertTrue("positive input pitch at $callerWidth/$count", layout.pitch > 0f)
                assertTrue("lens fits content at $callerWidth/$count", layout.lens in 0f..span)
                assertEquals("weighted row matches lens centers at $callerWidth/$count",
                    span - 2f * layout.inset, count * layout.pitch, .0001f)
                assertEquals("leading inset at $callerWidth/$count", 4f, lensLeft(layout, 0), .0001f)
                assertEquals("trailing inset at $callerWidth/$count", barWidth - 4f,
                    lensLeft(layout, count - 1) + layout.lens, .0001f)
                for (index in 0 until count - 1) {
                    assertEquals("equal centers at $callerWidth/$count", layout.pitch,
                        lensLeft(layout, index + 1) - lensLeft(layout, index), .0001f)
                }
            }
        }
    }

    @Test
    fun fourTabLensUsesAvailableSpaceWithoutBecomingAGlobalWidthChange() {
        val compact = tabViewDockLayout(301f, 4)
        val measured = tabViewDockLayout(325f, 4)
        val intrinsic = tabViewDockLayout(352f, 4)
        assertEquals(88.25f, compact.lens, .0001f)
        assertTrue(compact.pitch < measured.pitch)
        assertTrue(measured.pitch < intrinsic.pitch)
        assertEquals(94f, intrinsic.lens, .0001f)
        for (count in listOf(1, 2, 3, 5)) {
            assertEquals(FlClashDockPhysics.dockLayout(266f, count), tabViewDockLayout(266f, count))
        }
    }

    @Test
    fun rtlMirrorsTheSamePaintedLensCenters() {
        val layout = tabViewDockLayout(325f, 4)
        for (index in 0..3) {
            val ltrCenter = lensLeft(layout, index) + layout.lens / 2f
            val rtlCenter = lensLeft(layout, 3 - index) + layout.lens / 2f
            assertEquals(333f, ltrCenter + rtlCenter, .0001f)
        }
    }

    private fun lensLeft(layout: FlClashDockPhysics.DockLayout, index: Int): Float =
        4f + layout.inset + FlClashDockPhysics.lensRect(
            position = index.toFloat(), stretch = 0f, pitch = layout.pitch,
            width = layout.lens, height = 54f, lift = 0f,
        ).left
}
