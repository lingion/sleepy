package com.lingion.sleepy.ui.component.liquidglass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassInputBlurTest {
    @Test fun androidRadiusIsConvertedAfterDensityToActualTexturePixels() {
        for (density in listOf(.75f, 1f, 1.5f, 2f, 2.75f, 3f)) {
            for (radiusDp in listOf(1f, 6f, 8f, 12f, 16f)) {
                val radiusPx = radiusDp * density
                val sigmaPx = androidBlurRadiusToSigma(radiusPx)
                assertEquals(.57735f * radiusPx + .5f, sigmaPx, 0f)
                assertEquals(radiusPx, androidBlurSigmaToRadius(sigmaPx), .00001f)
            }
        }
        assertEquals(0f, androidBlurRadiusToSigma(0f), 0f)
        assertEquals(0f, androidBlurSigmaToRadius(0f), 0f)
    }

    @Test fun androidRadiusIsNotSilentlyTreatedAsSigma() {
        assertEquals(5.1188f, androidBlurRadiusToSigma(8f), .000001f)
        assertEquals(9.7376f, androidBlurRadiusToSigma(16f), .000001f)
        val sigma = androidBlurRadiusToSigma(16f)
        assertTrue(sigma < 16f)
    }

    @Test fun rawInputReceivesTheExplicitSamplingSigma() {
        val blur = liquidGlassInputBlur(sampledBlurPx = 0f, inputBlurPx = 8f)
        assertEquals(8f, blur.additionalBlurPx, 0f)
        assertEquals(8f, blur.heldBlurPx, 0f)
    }

    @Test fun preblurredInputReceivesOnlyTheMissingVariance() {
        val blur = liquidGlassInputBlur(sampledBlurPx = 4f, inputBlurPx = 8f)
        assertEquals(8f, blur.heldBlurPx, 0f)
        assertEquals(64f, 16f + blur.additionalBlurPx * blur.additionalBlurPx, .00001f)
    }

    @Test fun aSharperRequestCannotPretendToUndoExistingBlur() {
        for (target in listOf(0f, 8f, 16f)) {
            val blur = liquidGlassInputBlur(sampledBlurPx = 16f, inputBlurPx = target)
            assertEquals(0f, blur.additionalBlurPx, 0f)
            assertEquals(16f, blur.heldBlurPx, 0f)
        }
    }

    @Test fun noMissingBlurNeedsNeitherAnExtraPassNorExtraPadding() {
        for (sigma in listOf(0f, 6f, 8f, 16f)) {
            val blur = liquidGlassInputBlur(sigma, sigma)
            assertEquals(0f, blur.additionalBlurPx, 0f)
            assertEquals(0f, blur.capturePaddingPx(), 0f)
        }
    }

    @Test fun unrepresentableRemaindersKeepTheRealHeldSigma() {
        for (target in listOf(.1f, .25f, .5f)) {
            val blur = liquidGlassInputBlur(0f, target)
            assertEquals(0f, blur.additionalBlurPx, 0f)
            assertEquals(0f, blur.heldBlurPx, 0f)
        }
        val nearExisting = liquidGlassInputBlur(8f, 8.01f)
        assertEquals(0f, nearExisting.additionalBlurPx, 0f)
        assertEquals(8f, nearExisting.heldBlurPx, 0f)
    }

    @Test fun composedAndroidKernelsUseSigmaVarianceNotRadiusVariance() {
        val sampled = androidBlurRadiusToSigma(4f)
        val target = androidBlurRadiusToSigma(8f)
        val blur = liquidGlassInputBlur(sampled, target)
        val actualAdded = androidBlurRadiusToSigma(androidBlurSigmaToRadius(blur.additionalBlurPx))
        assertEquals(target * target, sampled * sampled + actualAdded * actualAdded, .00001f)
        assertEquals(target, blur.heldBlurPx, 0f)
    }

    @Test fun sourcePaddingCoversInputAndFollowingHazeKernels() {
        val blur = liquidGlassInputBlur(sampledBlurPx = 4f, inputBlurPx = 8f)
        val support = 3f * (blur.additionalBlurPx + 5f)
        val padding = blur.capturePaddingPx(followingBlurPx = 5f)
        assertTrue(padding >= support)
        assertTrue(padding < support + 1f)
        assertEquals(24f, liquidGlassInputBlur(0f, 8f).capturePaddingPx(), 0f)
    }

    @Test fun invalidSigmasAreRejectedInsteadOfEnteringTheGpuGraph() {
        for (invalid in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for ((sampled, target) in listOf(invalid to 8f, 0f to invalid)) {
                var rejected = false
                try { liquidGlassInputBlur(sampled, target) } catch (_: IllegalArgumentException) { rejected = true }
                assertTrue("sampled=$sampled target=$target", rejected)
            }
        }
    }

    @Test fun invalidConversionsAndTheUnrepresentableSigmaGapAreRejected() {
        for (invalid in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            var radiusRejected = false
            try { androidBlurRadiusToSigma(invalid) } catch (_: IllegalArgumentException) { radiusRejected = true }
            assertTrue("radius=$invalid", radiusRejected)
        }
        for (invalid in listOf(-1f, .1f, .25f, .5f, Float.NaN, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.MAX_VALUE)) {
            var sigmaRejected = false
            try { androidBlurSigmaToRadius(invalid) } catch (_: IllegalArgumentException) { sigmaRejected = true }
            assertTrue("sigma=$invalid", sigmaRejected)
        }
    }

    @Test fun finiteSigmasDoNotOverflowWhileSubtractingVariances() {
        val blur = liquidGlassInputBlur(Float.MAX_VALUE / 2f, Float.MAX_VALUE)
        assertTrue(blur.additionalBlurPx.isFinite())
        assertEquals(Float.MAX_VALUE, blur.heldBlurPx, 0f)
        var rejected = false
        try { blur.capturePaddingPx() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue("Unrepresentable source padding must be rejected", rejected)
    }
}
