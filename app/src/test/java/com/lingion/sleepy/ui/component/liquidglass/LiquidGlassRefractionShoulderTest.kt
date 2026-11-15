package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The measured Slider transport must remain an explicit, independently fading material input. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiquidGlassRefractionShoulderTest {
    private val baseline = LiquidGlassStyle(Color.White, refraction = 7.8)
    private val shoulder = LiquidGlassRefractionShoulder(7.5)

    private fun build(
        style: LiquidGlassStyle = baseline,
        density: Float = 2.75f,
        stretch: Float = 1f,
    ) = LiquidGlassShader.build(
        size = Size(57.35f, 37.2f),
        corners = LiquidGlassCornerRadii(18.6f, 18.6f, 18.6f, 18.6f),
        style = style,
        originPx = Offset(30f, 400f),
        density = density,
        stretch = stretch,
        atlasSizePx = Size(320f, 160f),
        sampling = LiquidGlassShader.sampling(
            originPx = Offset(12f, 8f),
            texelsPerDp = 2f,
            clampPx = Rect(0f, 0f, 320f, 160f),
        ),
    )

    @Test
    fun onlySingleSliderFactoriesOptIntoTheMeasuredShoulder() {
        assertNull(baseline.refractionShoulder)
        listOf(false, true).forEach { dark ->
            val colors = if (dark) darkColorScheme() else lightColorScheme()
            val slider = LiquidGlassStyle.sliderLens(dark)
            assertEquals(LiquidGlassRefractionShoulder(7.5), slider.refractionShoulder)
            assertEquals(7.8, slider.refraction, 0.0)
            listOf(
                LiquidGlassStyle.regular(colors, dark),
                LiquidGlassStyle.pill(colors, dark),
                LiquidGlassStyle.menu(colors, dark),
                LiquidGlassStyle.alert(colors, dark),
                LiquidGlassStyle.sheet(dark),
                LiquidGlassStyle.tinted(colors, Color.Red, dark),
                LiquidGlassStyle.lens(colors, dark),
                LiquidGlassStyle.controlLens(colors, dark),
                LiquidGlassStyle.rangeSliderLens(dark),
                LiquidGlassStyle.lensContent(colors, dark),
                LiquidGlassStyle.controlLensContent(colors, dark),
            ).forEach { style ->
                assertNull(style.refractionShoulder)
                assertArrayEquals(floatArrayOf(0f, 0f), build(style).getValue("uRefractionShoulder"), 0f)
            }
        }
    }

    @Test
    fun enablingTheShoulderDoesNotRebindSamplingDispersionFrostOrLighting() {
        val original = build()
        val actual = build(baseline.copy(refractionShoulder = shoulder))
        assertArrayEquals(floatArrayOf(0f, 0f), original.getValue("uRefractionShoulder"), 0f)
        assertArrayEquals(floatArrayOf(7.5f, 1f), actual.getValue("uRefractionShoulder"), 0f)
        original.filterKeys { it != "uRefractionShoulder" }.forEach { (name, values) ->
            assertArrayEquals("$name must remain independent of common-channel transport", values, actual.getValue(name), 0f)
        }
    }

    @Test
    fun invalidSupportOrBlendDisablesBothUniformComponents() {
        listOf(
            shoulder.copy(widthDp = -1.0),
            shoulder.copy(widthDp = 0.0),
            shoulder.copy(widthDp = 1e-10),
            shoulder.copy(widthDp = Double.MIN_VALUE),
            shoulder.copy(widthDp = Double.NaN),
            shoulder.copy(widthDp = Double.POSITIVE_INFINITY),
            shoulder.copy(blend = Double.NaN),
            shoulder.copy(blend = Double.NEGATIVE_INFINITY),
        ).forEach { invalid ->
            val packed = build(baseline.copy(refractionShoulder = invalid)).getValue("uRefractionShoulder")
            assertArrayEquals("Invalid shoulder $invalid must not reach AGSL", floatArrayOf(0f, 0f), packed, 0f)
            assertTrue(packed.all { it.isFinite() })
        }
    }

    @Test
    fun finiteRequestsAreBoundedWithoutLosingTheSmallestValidSupport() {
        listOf(
            LiquidGlassRefractionShoulder(1e-3, .25) to floatArrayOf(.001f, .25f),
            LiquidGlassRefractionShoulder(Double.MAX_VALUE, 2.0) to floatArrayOf(32f, 1f),
            shoulder.copy(blend = -1.0) to floatArrayOf(7.5f, 0f),
        ).forEach { (requested, expected) ->
            val packed = build(baseline.copy(refractionShoulder = requested)).getValue("uRefractionShoulder")
            assertArrayEquals(expected, packed, 0f)
            assertTrue(packed.all { it.isFinite() })
        }
    }

    @Test
    fun supportStaysInDpWhileLiftChangesTransportAmplitudeAndReachesAFlatEndpoint() {
        val style = baseline.copy(refractionShoulder = shoulder)
        listOf(-1.0, 0.0, .25, .5, 1.0, 2.0).forEach { lift ->
            val scaled = style.scale(lift)
            assertEquals(shoulder, scaled.refractionShoulder)
            listOf(1f to 1f, 2.975f to 1.55f, 4f to .75f).forEach { (density, stretch) ->
                val packed = build(scaled, density, stretch)
                assertArrayEquals(floatArrayOf(7.5f, 1f), packed.getValue("uRefractionShoulder"), 0f)
                assertEquals((7.8 * lift.coerceIn(0.0, 1.0)).toFloat(), packed.getValue("uOptics")[0], 0f)
            }
        }
    }

    @Test
    fun nullableTransitionsFadeTheReplacementWithoutCollapsingItsSpatialSupport() {
        val explicit = baseline.copy(refractionShoulder = shoulder)
        listOf(0.0, 1e-6, .25, .5, .999999, 1.0).forEach { t ->
            val entering = LiquidGlassStyle.lerp(baseline, explicit, t)
            val leaving = LiquidGlassStyle.lerp(explicit, baseline, t)
            assertEquals(shoulder.copy(blend = t), entering.refractionShoulder)
            assertEquals(shoulder.copy(blend = 1.0 - t), leaving.refractionShoulder)
            assertArrayEquals(floatArrayOf(7.5f, t.toFloat()), build(entering).getValue("uRefractionShoulder"), 0f)
            assertArrayEquals(floatArrayOf(7.5f, (1.0 - t).toFloat()), build(leaving).getValue("uRefractionShoulder"), 0f)
        }
        assertNull(LiquidGlassStyle.lerp(baseline, baseline, .5).refractionShoulder)
    }

    @Test
    fun explicitProfilesInterpolateWidthAndBlendIndependentlyOfRefractionStrength() {
        val a = baseline.copy(refractionShoulder = LiquidGlassRefractionShoulder(6.0, .25))
        val b = baseline.copy(refractionShoulder = LiquidGlassRefractionShoulder(10.0, .75))
        val middle = LiquidGlassStyle.lerp(a, b, .5)
        assertEquals(LiquidGlassRefractionShoulder(8.0, .5), middle.refractionShoulder)
        assertEquals(baseline.refraction, middle.refraction, 0.0)
        assertArrayEquals(floatArrayOf(8f, .5f), build(middle).getValue("uRefractionShoulder"), 0f)
    }
}
