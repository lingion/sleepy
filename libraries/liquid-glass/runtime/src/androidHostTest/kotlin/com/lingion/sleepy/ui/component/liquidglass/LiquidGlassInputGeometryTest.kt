package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassInputGeometryTest {
    @Test fun positiveBoundsKeepTheirPositionWithinTheFullRecordedScene() {
        // The backend records a 200x120 outer node, not this 100x60 pane.
        // Its canvas translates by 15px before recording the whole scene.
        val geometry = liquidGlassInputGeometry(
            effectOriginPx = Offset(40f, 20f),
            captureSizePx = Size(200f, 120f),
            paddingPx = 15f,
        )
        assertEquals(Offset(55f, 35f), geometry.originPx)
        assertEquals(Size(230f, 150f), geometry.sizePx)
        assertEquals(Rect(0f, 0f, 230f, 150f), geometry.clampPx)
        val lastPanePixel = texturePoint(geometry, Offset(99f, 59f))
        assertEquals(Offset(154f, 94f), lastPanePixel)
        assertTrue(geometry.clampPx.contains(lastPanePixel))
        // A refracted point may read the scene beyond the pane's own boundary.
        assertTrue(geometry.clampPx.contains(texturePoint(geometry, Offset(110f, 70f))))
    }

    @Test fun negativeBoundsDoNotMoveOrInventCapturedPixels() {
        val geometry = liquidGlassInputGeometry(
            effectOriginPx = Offset(-20f, -8f),
            captureSizePx = Size(200f, 120f),
            paddingPx = 5f,
        )
        assertEquals(Offset(-15f, -3f), geometry.originPx)
        assertEquals(Size(210f, 130f), geometry.sizePx)
        assertEquals(Rect(0f, 0f, 210f, 130f), geometry.clampPx)
        assertEquals(Offset(-15f, -3f), texturePoint(geometry, Offset.Zero))
        assertEquals(Offset(.5f, .5f), texturePoint(geometry, Offset(15.5f, 3.5f)))
    }

    @Test fun existingPaddingSurvivesWhenNoAdditionalInputBlurIsNeeded() {
        val blur = liquidGlassInputBlur(sampledBlurPx = 8f, inputBlurPx = 8f)
        assertEquals(0f, blur.additionalBlurPx, 0f)
        val geometry = liquidGlassInputGeometry(
            effectOriginPx = Offset(7f, 9f),
            captureSizePx = Size(100f, 60f),
            paddingPx = maxOf(12f, blur.capturePaddingPx()),
        )
        assertEquals(Offset(19f, 21f), geometry.originPx)
        assertEquals(Size(124f, 84f), geometry.sizePx)
        assertEquals(Offset(19f, 21f), texturePoint(geometry, Offset.Zero))
    }

    @Test fun fixedAllocationChangesTheRasterExtentButNotTheSourceTranslation() {
        val geometry = liquidGlassInputGeometry(
            effectOriginPx = Offset(40f, 20f),
            captureSizePx = Size(200.25f, 120.75f),
            paddingPx = 5f,
            allocationPaddingPx = 20f,
        )
        assertEquals(Offset(45f, 25f), geometry.originPx)
        assertEquals(Size(241f, 161f), geometry.sizePx)
        assertEquals(Rect(0f, 0f, 241f, 161f), geometry.clampPx)
    }

    @Test fun fractionalCaptureUsesTheBackendsCeiledPhysicalRaster() {
        val geometry = liquidGlassInputGeometry(
            effectOriginPx = Offset.Zero,
            captureSizePx = Size(100.25f, 50.75f),
            paddingPx = 2.25f,
        )
        assertEquals(Offset(2.25f, 2.25f), geometry.originPx)
        assertEquals(Size(105f, 56f), geometry.sizePx)
        val sampling = LiquidGlassShader.sampling(geometry.originPx, 2f, geometry.clampPx)
        assertEquals(.5f, sampling[4], 0f)
        assertEquals(104.5f, sampling[6], 0f)
        assertEquals(55.5f, sampling[7], 0f)
    }

    @Test fun invalidOrImpossibleCaptureMetadataIsRejected() {
        val invalidCalls = listOf<() -> Unit>(
            { liquidGlassInputGeometry(Offset(Float.NaN, 0f), Size(100f, 60f), 0f) },
            { liquidGlassInputGeometry(Offset.Zero, Size(0f, 60f), 0f) },
            { liquidGlassInputGeometry(Offset.Zero, Size(100f, Float.POSITIVE_INFINITY), 0f) },
            { liquidGlassInputGeometry(Offset.Zero, Size(100f, 60f), -1f) },
            { liquidGlassInputGeometry(Offset.Zero, Size(100f, 60f), 5f, 4f) },
            { liquidGlassInputGeometry(Offset.Zero, Size(100f, 60f), 0f, Float.NaN) },
            { liquidGlassInputGeometry(Offset.Zero, Size(Float.MAX_VALUE, 60f), Float.MAX_VALUE) },
        )
        invalidCalls.forEach { call ->
            var rejected = false
            try { call() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Invalid raster geometry must not enter shader uniforms", rejected)
        }
    }

    private fun texturePoint(geometry: LiquidGlassInputGeometry, panePoint: Offset): Offset {
        val sampling = LiquidGlassShader.sampling(geometry.originPx, 1f, geometry.clampPx)
        return Offset((panePoint.x - sampling[0]) / sampling[2],
            (panePoint.y - sampling[1]) / sampling[3])
    }
}
