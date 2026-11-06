package com.kyant.backdrop

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
}
