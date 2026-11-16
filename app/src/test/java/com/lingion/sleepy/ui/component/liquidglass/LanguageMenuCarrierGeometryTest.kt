package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageMenuCarrierGeometryTest {
    @Test fun settledHalfPixelMenuKeepsItsMaterialAlignedWithItsContent() {
        val anchor = Rect(70f, 536f, 1147f, 689f)
        val menu = Rect(236.5f, 536f, 980.5f, 1221f)
        val geometry = LanguageMenuMotion().apply { finish() }.geometry(anchor, menu)

        assertEquals(menu, geometry.content)
        assertEquals(menu, geometry.material)
        assertPlacedContours(geometry, Density(2.975f))
        assertEquals("retain the content's half-pixel placement", 236.5f, geometry.content.left, 0f)
    }

    @Test fun bothMorphContoursShareOneWorldOrigin() {
        val geometry = LanguageMenuGeometry(
            anchor = Rect(10.25f, 100.75f, 260.75f, 151.25f),
            material = Rect(80.75f, 60.25f, 190.25f, 200.75f),
            content = Rect(80.75f, 60.25f, 190.25f, 230.25f),
            anchorContentScale = .75f,
        )

        assertPlacedContours(geometry, Density(1f))
        assertEquals("the carrier encloses optical contours, not the separate content", 201f,
            geometry.carrier.bottom, 0f)
    }

    @Test fun offscreenNegativeEdgesDoNotTruncateTowardTheWrongOrigin() {
        val geometry = LanguageMenuGeometry(
            anchor = Rect(-20.75f, -10.25f, 30.25f, 40.75f),
            material = Rect(-5.25f, -30.75f, 70.75f, 10.25f),
            content = Rect(-5.25f, -30.75f, 70.75f, 90.25f),
            anchorContentScale = .5f,
        )

        assertPlacedContours(geometry, Density(2.75f))
    }

    @Test fun tinyContoursStillFitInsideTheAllocatedPixels() {
        val geometry = LanguageMenuGeometry(
            anchor = Rect(.25f, .25f, .5f, .5f),
            material = Rect(.5f, .5f, .75f, .75f),
            content = Rect(.5f, .5f, .75f, .75f),
            anchorContentScale = 1f,
        )

        assertPlacedContours(geometry, Density(4f))
        assertEquals(1f, geometry.carrier.width, 0f)
        assertEquals(1f, geometry.carrier.height, 0f)
    }

    @Test fun openingAndClosingKeepBothContoursInsideThePixelEnvelopeAtRealDensities() {
        listOf(1f, 2.75f, 2.975f, 4f).forEach { scale ->
            fun physical(rect: Rect) = Rect(rect.left * scale, rect.top * scale,
                rect.right * scale, rect.bottom * scale)
            val anchor = physical(Rect(20f, 180f, 382f, 231.25f))
            val menu = physical(Rect(76f, 180f, 326f, 410f))
            val motion = LanguageMenuMotion()
            repeat(2) { phase ->
                repeat(90) {
                    val geometry = motion.geometry(anchor, menu)
                    assertSmallestEnvelope(geometry)
                    assertAllocationContainsContours(geometry, Density(scale))
                    motion.tick(1.0 / 60.0)
                }
                if (phase == 0) motion.close()
            }
        }
    }

    private fun assertPlacedContours(geometry: LanguageMenuGeometry, density: Density) {
        assertSmallestEnvelope(geometry)
        val carrier = geometry.carrier
        // These are the actual integer placement and local contours consumed by LanguagePopup.
        val placement = IntOffset(carrier.left.toInt(), carrier.top.toInt())
        val worldOrigin = Offset(placement.x.toFloat(), placement.y.toFloat())
        listOf(geometry.anchor, geometry.material).forEach { world ->
            assertEquals("integer placement must not shift the floating-point contour",
                world, world.translate(-carrier.topLeft).translate(worldOrigin))
        }
        assertAllocationContainsContours(geometry, density)
    }

    private fun assertAllocationContainsContours(geometry: LanguageMenuGeometry, density: Density) {
        val carrier = geometry.carrier
        // requiredSize converts physical pixels through Dp before measuring the actual layer.
        val width = with(density) { carrier.width.toDp().roundToPx() }
        val height = with(density) { carrier.height.toDp().roundToPx() }
        assertTrue("a nonempty contour needs a nonempty layer", width > 0 && height > 0)
        listOf(geometry.anchor, geometry.material).forEach { world ->
            val local = world.translate(-carrier.topLeft)
            assertTrue("left/top cannot be clipped: $local in ${width}x$height",
                local.left >= 0f && local.top >= 0f)
            assertTrue("right/bottom cannot be clipped: $local in ${width}x$height",
                local.right <= width && local.bottom <= height)
        }
    }

    private fun assertSmallestEnvelope(geometry: LanguageMenuGeometry) {
        val carrier = geometry.carrier
        listOf(carrier.left, carrier.top, carrier.right, carrier.bottom).forEach { edge ->
            assertEquals("carrier edges must survive integer layout placement", edge.toInt().toFloat(), edge, 0f)
        }
        val gaps = listOf(
            minOf(geometry.anchor.left, geometry.material.left) - carrier.left,
            minOf(geometry.anchor.top, geometry.material.top) - carrier.top,
            carrier.right - maxOf(geometry.anchor.right, geometry.material.right),
            carrier.bottom - maxOf(geometry.anchor.bottom, geometry.material.bottom),
        )
        gaps.forEach { gap ->
            assertTrue("enclose both contours with less than one extra pixel on each edge: $gaps",
                gap >= 0f && gap < 1f)
        }
    }
}
