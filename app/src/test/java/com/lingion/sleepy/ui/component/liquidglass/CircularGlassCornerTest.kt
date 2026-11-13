package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CircularGlassCornerTest {
    @Test fun actualCapsuleOutlineStaysCircularAtBothThumbSizesAndDuringDeformation() {
        val sizes = listOf(Size(37f, 24f), Size(27.75f, 18f), Size(58f, 38f + 1f / 3f),
            Size(94f, 54f), Size(107f, 28f), Size(131f, 44f), Size(36f, 36f))
        listOf(1f, 2.975f).forEach { scale ->
            sizes.forEach { size ->
                listOf(Size(1f, 1f), Size(1.15f, .8f), Size(.83f, 1.17f)).forEach { stretch ->
                    val pixels = Size(size.width * scale * stretch.width, size.height * scale * stretch.height)
                    val outline = RoundedCornerShape(50).createOutline(pixels, LayoutDirection.Ltr, Density(scale))
                    val expected = pixels.minDimension / (2f * scale)
                    assertEquals(LiquidGlassCornerRadii(expected, expected, expected, expected),
                        circularGlassCornerRadii(outline, scale))
                }
            }
        }
    }

    @Test fun callerUniformRoundRectKeepsItsOwnRadiusAndClampsOnlyOverlappingUniformArcs() {
        listOf(8f to 8f, 40f to 15f).forEach { (requested, expected) ->
            val outline = Outline.Rounded(RoundRect(0f, 0f, 120f, 60f,
                CornerRadius(requested * 2f, requested * 2f)))
            assertEquals(LiquidGlassCornerRadii(expected, expected, expected, expected),
                circularGlassCornerRadii(outline, 2f))
        }
    }

    @Test fun asymmetricAndEllipticalRoundRectsAreNotRelabelledAsUniformCircularCapsules() {
        val asymmetric = Outline.Rounded(RoundRect(0f, 0f, 100f, 60f,
            topLeftCornerRadius = CornerRadius(50f, 50f),
            topRightCornerRadius = CornerRadius(10f, 10f),
            bottomRightCornerRadius = CornerRadius(10f, 10f),
            bottomLeftCornerRadius = CornerRadius(10f, 10f)))
        val elliptical = Outline.Rounded(RoundRect(0f, 0f, 100f, 60f, CornerRadius(30f, 20f)))
        assertNull(circularGlassCornerRadii(asymmetric, 1f))
        assertNull(circularGlassCornerRadii(elliptical, 1f))
    }

    @Test fun actualPresentationSuperellipseRetainsItsSeparateContour() {
        val outline = FlClashGlassShape(34f).createOutline(Size(320f, 172f), LayoutDirection.Ltr, Density(1f))
        assertNull(circularGlassCornerRadii(outline, 1f))
    }
}
