package com.kyant.backdrop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SampledEffectGeometryTest {
    @Test
    fun samplingScalesDpAndGeometryTogetherAndRestoresFullResolution() {
        val drawScope = object : DrawScope by CanvasDrawScope() {
            override val density = 3f
            override val size = Size(201f, 405f)
        }
        val effect = object : BackdropEffectScopeImpl() {
            override val shape = RectangleShape
        }
        assertTrue(effect.update(drawScope, drawScope.size * 0.5f, densityScale = 0.5f))
        assertEquals(Size(100.5f, 202.5f), effect.size)
        assertEquals(18f, with(effect) { 12.dp.toPx() })
        assertFalse(effect.update(drawScope, drawScope.size * 0.5f, densityScale = 0.5f))
        assertTrue(effect.update(drawScope))
        assertEquals(drawScope.size, effect.size)
        assertEquals(36f, with(effect) { 12.dp.toPx() })
    }

    @Test
    fun sharedCourseSamplingKeepsFractionalLensGeometryAtNexioResolution() {
        val drawScope = object : DrawScope by CanvasDrawScope() {
            override val density = 3f
            override val size = Size(201f, 405f)
        }
        val effect = object : BackdropEffectScopeImpl() { override val shape = RectangleShape }
        effect.update(drawScope)
        assertEquals(201f, effect.size.width, 0.001f)
        assertEquals(405f, effect.size.height, 0.001f)
        assertEquals(36f, with(effect) { 12.dp.toPx() }, 0.001f)
    }

    @Test
    fun boundedPaneCaptureTracksTheCompleteOuterNodeAndFixedAllocation() {
        var outerSize = Size(200f, 120f)
        val drawScope = object : DrawScope by CanvasDrawScope() {
            override val size get() = outerSize
        }
        val effect = object : BackdropEffectScopeImpl() { override val shape = RectangleShape }
        val paneSize = Size(100f, 60f)
        val paneOffset = Offset(40f, 20f)
        assertTrue(effect.update(drawScope, paneSize, paneOffset, allocationPadding = 20f))
        assertEquals(paneSize, effect.size)
        assertEquals(paneOffset, effect.effectOffset)
        assertEquals(outerSize, effect.captureSize)
        assertEquals(20f, effect.captureAllocationPadding)
        assertFalse(effect.update(drawScope, paneSize, paneOffset, allocationPadding = 20f))
        outerSize = Size(240f, 160f)
        assertTrue(effect.update(drawScope, paneSize, paneOffset, allocationPadding = 20f))
        assertEquals(paneSize, effect.size)
        assertEquals(outerSize, effect.captureSize)
        assertTrue(effect.update(drawScope, paneSize, paneOffset, allocationPadding = 24f))
        assertEquals(24f, effect.captureAllocationPadding)
    }

    @Test
    fun captureMetadataUsesSamplingPixelsAndResetsWithTheScope() {
        val drawScope = object : DrawScope by CanvasDrawScope() {
            override val density = 3f
            override val size = Size(201f, 405f)
        }
        val effect = object : BackdropEffectScopeImpl() { override val shape = RectangleShape }
        effect.update(drawScope, drawScope.size * .5f, densityScale = .5f)
        assertEquals(Size(100.5f, 202.5f), effect.captureSize)
        assertEquals(null, effect.captureAllocationPadding)
        effect.reset()
        assertEquals(Size.Unspecified, effect.captureSize)
        assertEquals(null, effect.captureAllocationPadding)
        effect.update(drawScope)
        assertEquals(drawScope.size, effect.captureSize)
    }
}
