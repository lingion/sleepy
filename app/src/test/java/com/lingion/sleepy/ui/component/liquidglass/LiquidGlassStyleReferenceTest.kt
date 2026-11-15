package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FLClash control defaults and the separately measured iOS presentation materials. */
class LiquidGlassStyleReferenceTest {
    private val light = lightColorScheme()
    // Deliberately distinguish the highest surface from the ordinary card surface.
    private val dark = darkColorScheme(
        surfaceContainer = Color(0xFF112233),
        surfaceContainerHighest = Color(0xFF445566),
    )

    @Test fun refractionLimitDefaultsToTheExistingHalfMinorCap() {
        assertEquals(1.0, LiquidGlassStyle(Color.Transparent).refractionLimit, 0.0)
    }

    @Test fun refractionLimitIsPreservedWhileTheStyleScales() {
        val style = LiquidGlassStyle(Color.White, refractionLimit = 2.0)
        listOf(-1.0, 0.0, 0.5, 1.0, 2.0).forEach { amount ->
            assertEquals(2.0, style.scale(amount).refractionLimit, 0.0)
        }
    }

    @Test fun refractionLimitInterpolatesBetweenStyles() {
        val a = LiquidGlassStyle(Color.White, refractionLimit = 1.0)
        val b = a.copy(refractionLimit = 2.0)
        assertEquals(1.0, LiquidGlassStyle.lerp(a, b, 0.0).refractionLimit, 0.0)
        assertEquals(1.25, LiquidGlassStyle.lerp(a, b, 0.25).refractionLimit, 0.0)
        assertEquals(1.5, LiquidGlassStyle.lerp(a, b, 0.5).refractionLimit, 0.0)
        assertEquals(2.0, LiquidGlassStyle.lerp(a, b, 1.0).refractionLimit, 0.0)
    }

    @Test fun refractionProfilePowerDefaultsToTheExistingCircularSection() {
        assertEquals(1.0, LiquidGlassStyle(Color.Transparent).refractionProfilePower, 0.0)
    }

    @Test fun refractionProfilePowerIsPreservedWhileTheStyleScales() {
        val style = LiquidGlassStyle(Color.White, refractionProfilePower = 2.0)
        listOf(-1.0, 0.0, 0.5, 1.0, 2.0).forEach { amount ->
            assertEquals(2.0, style.scale(amount).refractionProfilePower, 0.0)
        }
    }

    @Test fun refractionProfilePowerInterpolatesWithoutChangingDepthOrBevel() {
        val a = LiquidGlassStyle(Color.White, refractionLimit = 1.5, refractionProfilePower = .5)
        val b = a.copy(refractionProfilePower = 2.5)
        listOf(0.0 to .5, .25 to 1.0, .5 to 1.5, 1.0 to 2.5).forEach { (t, power) ->
            val actual = LiquidGlassStyle.lerp(a, b, t)
            assertEquals(power, actual.refractionProfilePower, 0.0)
            assertEquals(a.refraction, actual.refraction, 0.0)
            assertEquals(a.refractionLimit, actual.refractionLimit, 0.0)
            assertEquals(a.bevel, actual.bevel, 0.0)
        }
    }

    @Test fun everyFactoryRetainsTheDefaultProfileUntilItIsCalibrated() {
        listOf(false, true).forEach { isDark ->
            val colors = if (isDark) dark else light
            listOf(
                LiquidGlassStyle.regular(colors, isDark),
                LiquidGlassStyle.pill(colors, isDark),
                LiquidGlassStyle.menu(colors, isDark),
                LiquidGlassStyle.alert(colors, isDark),
                LiquidGlassStyle.sheet(isDark),
                LiquidGlassStyle.tinted(colors, Color.Red, isDark),
                LiquidGlassStyle.lens(colors, isDark),
                LiquidGlassStyle.controlLens(colors, isDark),
                LiquidGlassStyle.sliderLens(isDark),
                LiquidGlassStyle.rangeSliderLens(isDark),
                LiquidGlassStyle.lensContent(colors, isDark),
                LiquidGlassStyle.controlLensContent(colors, isDark),
            ).forEach { style ->
                assertEquals(1.0, style.refractionProfilePower, 0.0)
                assertEquals(1.0, LiquidGlassStyle.standard(style).refractionProfilePower, 0.0)
                assertEquals(0.0, style.dockBaseRimCubicShoulderBlend, 0.0)
                assertEquals(0.0, LiquidGlassStyle.standard(style).dockBaseRimCubicShoulderBlend, 0.0)
                assertEquals(null, style.dockToneResponse?.brightRim)
                assertEquals(null, style.dockToneResponse?.darkRim)
                assertEquals(null, style.dockToneResponse?.darkLowTone)
            }
            assertEquals(1.5, LiquidGlassStyle.pill(colors, isDark).refractionLimit, 0.0)
        }
    }

    @Test fun lateralRefractionIsLimitedToPanelsAndKeepsItsShapeWhileScaling() {
        assertEquals(0.0, LiquidGlassStyle(Color.Transparent).lateralRefractionGain, 0.0)
        listOf(false, true).forEach { isDark ->
            val colors = if (isDark) dark else light
            listOf(LiquidGlassStyle.alert(colors, isDark), LiquidGlassStyle.sheet(isDark)).forEach { panel ->
                assertEquals(.45, panel.lateralRefractionGain, 0.0)
                listOf(0.0, .5, 1.0).forEach { amount ->
                    assertEquals(.45, panel.scale(amount).lateralRefractionGain, 0.0)
                }
            }
            listOf(LiquidGlassStyle.pill(colors, isDark), LiquidGlassStyle.menu(colors, isDark),
                LiquidGlassStyle.tinted(colors, Color.Red, isDark), LiquidGlassStyle.lens(colors, isDark),
                LiquidGlassStyle.controlLens(colors, isDark)).forEach {
                assertEquals(0.0, it.lateralRefractionGain, 0.0)
            }
        }
        val a = LiquidGlassStyle(Color.White)
        val b = a.copy(lateralRefractionGain = .1)
        assertEquals(.025, LiquidGlassStyle.lerp(a, b, .25).lateralRefractionGain, 0.0)
    }

    @Test fun dockBaseShoulderKeepsItsProfileWhileTheRimStrengthScales() {
        val style = LiquidGlassStyle(Color.White, dockBaseRimCubicShoulderBlend = .75)
        listOf(-1.0, 0.0, .25, .5, 1.0, 2.0).forEach { amount ->
            val scaled = style.scale(amount)
            assertEquals(.75, scaled.dockBaseRimCubicShoulderBlend, 0.0)
            assertEquals(style.rim * amount.coerceIn(0.0, 1.0), scaled.rim, 0.0)
            assertEquals(style.rimWidth, scaled.rimWidth, 0.0)
            assertEquals(null, scaled.dockToneResponse)
        }
    }

    @Test fun dockBaseShoulderInterpolatesAndKeepsExistingStyleExtrapolation() {
        val legacy = LiquidGlassStyle(Color.White)
        val shoulder = legacy.copy(dockBaseRimCubicShoulderBlend = 1.0)
        assertEquals(0.0, legacy.dockBaseRimCubicShoulderBlend, 0.0)
        listOf(-1.0, 0.0, .001, .25, .5, .999, 1.0, 2.0).forEach { amount ->
            val forward = LiquidGlassStyle.lerp(legacy, shoulder, amount)
            val backward = LiquidGlassStyle.lerp(shoulder, legacy, amount)
            assertEquals(amount, forward.dockBaseRimCubicShoulderBlend, 0.0)
            assertEquals(1.0 - amount, backward.dockBaseRimCubicShoulderBlend, 0.0)
            listOf(forward, backward).forEach { actual ->
                assertEquals(legacy.rim, actual.rim, 0.0)
                assertEquals(legacy.rimWidth, actual.rimWidth, 0.0)
                assertEquals(legacy.rimTail, actual.rimTail, 0.0)
                assertEquals(null, actual.dockToneResponse)
            }
        }
    }

    @Test fun darkRimScalesAndInterpolatesWithoutChangingTheBrightRim() {
        val bright = DockBrightRimResponse(1.0, .25, -.5, .75)
        val rim = DockBrightRimResponse(1.0, -.5, .25, -.5, blend = .25,
            angularBlend = .25, normalCrossGain = -.5, cosineBlend = .25)
        val response = DockToneResponse(.25, 1.0, brightRim = bright)
        assertEquals(null, response.darkRim)
        val original = LiquidGlassStyle(Color.Transparent, dockToneResponse = response)
        val a = original.copy(dockToneResponse = response.copy(darkRim = rim))
        val other = DockBrightRimResponse(3.0, .5, .75, 1.5, blend = .75,
            angularBlend = .75, normalCrossGain = 1.5, cosineBlend = .75)
        val b = original.copy(dockToneResponse = response.copy(darkRim = other))
        val middle = LiquidGlassStyle.lerp(a, b, .5).dockToneResponse!!
        assertEquals(DockBrightRimResponse(2.0, 0.0, .5, .5, blend = .5,
            angularBlend = .5, normalCrossGain = .5, cosineBlend = .5), middle.darkRim)
        assertEquals(bright, middle.brightRim)
        listOf(-1.0, 0.0, .5, 1.0, 2.0).forEach { t ->
            assertEquals(rim.copy(blend = rim.blend * t.coerceIn(0.0, 1.0)), a.scale(t).dockToneResponse!!.darkRim)
        }
        listOf(0.0, .001, .5, .999, 1.0).forEach { t ->
            val forward = LiquidGlassStyle.lerp(original, a, t).dockToneResponse!!
            val backward = LiquidGlassStyle.lerp(a, original, t).dockToneResponse!!
            assertEquals(rim.blend * t, forward.darkRim?.blend ?: 0.0, 1e-12)
            assertEquals(rim.blend * (1.0 - t), backward.darkRim?.blend ?: 0.0, 1e-12)
            assertEquals(bright, forward.brightRim)
            assertEquals(bright, backward.brightRim)
        }
        assertEquals(null, LiquidGlassStyle.lerp(original, a, 0.0).dockToneResponse!!.darkRim)
        assertEquals(rim, LiquidGlassStyle.lerp(original, a, 1.0).dockToneResponse!!.darkRim)
    }

    @Test fun cubicShoulderIsOptInBoundedAndInterpolatesWithoutChangingWidthOrCosine() {
        val rim = DockBrightRimResponse(1.2, .1, -.1, .1, blend = .75, cosineBlend = .5)
        assertEquals(0.0, rim.cubicShoulderBlend, 0.0)
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -.001, 1.001)) {
            assertTrue(runCatching { rim.copy(cubicShoulderBlend = bad) }.isFailure)
        }
        val shoulder = rim.copy(cubicShoulderBlend = 1.0)
        val response = DockToneResponse(.5, 1.0, brightRim = rim, darkRim = rim)
        val original = LiquidGlassStyle(Color.Transparent, dockToneResponse = response)
        val candidate = original.copy(dockToneResponse = response.copy(brightRim = shoulder, darkRim = shoulder))
        val absent = original.copy(dockToneResponse = response.copy(brightRim = null, darkRim = null))
        for (t in listOf(0.0, .001, .25, .5, .999, 1.0)) {
            val mixed = LiquidGlassStyle.lerp(original, candidate, t).dockToneResponse!!
            for (actual in listOf(mixed.brightRim!!, mixed.darkRim!!)) {
                assertEquals(t, actual.cubicShoulderBlend, 1e-12)
                assertEquals(1.2, actual.widthDp, 0.0)
                assertEquals(rim.cosineBlend, actual.cosineBlend, 0.0)
            }
            val added = LiquidGlassStyle.lerp(absent, candidate, t).dockToneResponse!!
            val removed = LiquidGlassStyle.lerp(candidate, absent, t).dockToneResponse!!
            for (actual in listOfNotNull(added.brightRim, added.darkRim, removed.brightRim, removed.darkRim)) {
                assertEquals(1.0, actual.cubicShoulderBlend, 0.0)
            }
            assertEquals(shoulder.blend * t, added.brightRim?.blend ?: 0.0, 1e-12)
            assertEquals(shoulder.blend * t, added.darkRim?.blend ?: 0.0, 1e-12)
            assertEquals(shoulder.blend * (1.0 - t), removed.brightRim?.blend ?: 0.0, 1e-12)
            assertEquals(shoulder.blend * (1.0 - t), removed.darkRim?.blend ?: 0.0, 1e-12)
            val scaled = candidate.scale(t).dockToneResponse!!
            assertEquals(shoulder.copy(blend = shoulder.blend * t), scaled.brightRim)
            assertEquals(shoulder.copy(blend = shoulder.blend * t), scaled.darkRim)
        }
    }

    @Test fun darkLowToneScalesByBlendAndKeepsTheIdentityBodyAtZero() {
        val curve = DockDarkLowToneResponse(.015625, .5, DockToneThresholds(.25, .5), blend = .75)
        val response = DockToneResponse(.25, 1.5, darkLowTone = curve)
        val style = LiquidGlassStyle(Color.Transparent, dockToneResponse = response)
        listOf(-1.0, 0.0, .25, .5, 1.0, 2.0).forEach { amount ->
            val actual = style.scale(amount).dockToneResponse!!
            assertEquals(curve.copy(blend = curve.blend * amount.coerceIn(0.0, 1.0)), actual.darkLowTone)
        }
        val zero = style.scale(0.0).dockToneResponse!!
        assertEquals(0.0, zero.darkLowTone!!.blend, 0.0)
        assertEquals(0.0, zero.darkIntercept, 0.0)
        assertEquals(1.0, zero.darkGain, 0.0)
        assertEquals(1.0, zero.darkChromaGain, 0.0)
        assertEquals(0.0, zero.brightIntercept, 0.0)
        assertEquals(1.0, zero.brightGain, 0.0)
        assertEquals(1.0, zero.brightChromaGain, 0.0)
    }

    @Test fun headroomCompositionIsOptInBoundedAndPreservedWhileStrengthScales() {
        val rim = DockBrightRimResponse(1.2, .11130893001790144, .24841754457691995, 1.63092875493563)
        assertEquals(0.0, rim.headroomBlend, 0.0)
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -.001, 1.001)) {
            assertTrue(runCatching { rim.copy(headroomBlend = bad) }.isFailure)
        }
        val tone = DockToneResponse(.5, 1.0, brightRim = rim, darkRim = rim)
        val original = LiquidGlassStyle(Color.Transparent, dockToneResponse = tone)
        val candidate = original.copy(dockToneResponse = tone.copy(
            brightRim = rim.copy(headroomBlend = 1.0), darkRim = rim.copy(headroomBlend = 1.0)))
        for (amount in listOf(0.0, .25, .5, 1.0)) {
            val mixed = LiquidGlassStyle.lerp(original, candidate, amount).dockToneResponse!!
            assertEquals(rim.copy(headroomBlend = amount), mixed.brightRim)
            assertEquals(rim.copy(headroomBlend = amount), mixed.darkRim)
            val scaled = candidate.scale(amount).dockToneResponse!!
            assertEquals(rim.copy(blend = amount, headroomBlend = 1.0), scaled.brightRim)
            assertEquals(rim.copy(blend = amount, headroomBlend = 1.0), scaled.darkRim)
        }
    }

    @Test fun logitCompositionIsOptInBoundedAndKeepsItsModeWhileRimStrengthScales() {
        val rim = DockBrightRimResponse(1.2, .6425691964508747, .4944993509984069, 7.021637119286862,
            cosineBlend = 1.0)
        assertEquals(0.0, rim.logitBlend, 0.0)
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -.001, 1.001)) {
            assertTrue(runCatching { rim.copy(logitBlend = bad) }.isFailure)
        }
        assertTrue(runCatching { rim.copy(chromaGain = 8.001) }.isFailure)
        val tone = DockToneResponse(.5, 1.0, brightRim = rim, darkRim = rim)
        val additive = LiquidGlassStyle(Color.Transparent, dockToneResponse = tone)
        val logit = additive.copy(dockToneResponse = tone.copy(
            brightRim = rim.copy(logitBlend = 1.0), darkRim = rim.copy(logitBlend = 1.0)))
        for (t in listOf(0.0, .25, .5, 1.0)) {
            val mixed = LiquidGlassStyle.lerp(additive, logit, t).dockToneResponse!!
            assertEquals(rim.copy(logitBlend = t), mixed.brightRim)
            assertEquals(rim.copy(logitBlend = t), mixed.darkRim)
            val scaled = logit.scale(t).dockToneResponse!!
            assertEquals(rim.copy(blend = t, logitBlend = 1.0), scaled.brightRim)
            assertEquals(rim.copy(blend = t, logitBlend = 1.0), scaled.darkRim)
        }
    }

    @Test fun darkLowToneInterpolatesNullableEndpointsWithoutChangingRimsOrTransition() {
        val transition = DockToneThresholds(.25, .5)
        val curve = DockDarkLowToneResponse(.015625, .5, transition, blend = .25)
        val rim = DockBrightRimResponse(1.0, .25, -.5, .75)
        val response = DockToneResponse(.25, 1.0, brightRim = rim, darkRim = rim)
        assertEquals(null, response.darkLowTone)
        val original = LiquidGlassStyle(Color.Transparent, dockToneResponse = response)
        val a = original.copy(dockToneResponse = response.copy(darkLowTone = curve))
        val other = curve.copy(linearIntercept = .265625, linearGain = 1.5, blend = .75)
        val b = original.copy(dockToneResponse = response.copy(darkLowTone = other))
        assertEquals(DockDarkLowToneResponse(.140625, 1.0, transition, blend = .5),
            LiquidGlassStyle.lerp(a, b, .5).dockToneResponse!!.darkLowTone)
        for (t in listOf(0.0, .001, .25, .5, .999, 1.0)) {
            val forward = LiquidGlassStyle.lerp(original, a, t).dockToneResponse!!
            val backward = LiquidGlassStyle.lerp(a, original, t).dockToneResponse!!
            assertEquals(curve.blend * t, forward.darkLowTone?.blend ?: 0.0, 1e-12)
            assertEquals(curve.blend * (1.0 - t), backward.darkLowTone?.blend ?: 0.0, 1e-12)
            for (actual in listOf(forward, backward)) {
                assertEquals(rim, actual.brightRim)
                assertEquals(rim, actual.darkRim)
                assertEquals(response.darkIntercept, actual.darkIntercept, 0.0)
                assertEquals(response.darkGain, actual.darkGain, 0.0)
                assertEquals(response.darkChromaGain, actual.darkChromaGain, 0.0)
                actual.darkLowTone?.let { assertEquals(transition, it.transition) }
            }
        }
        assertEquals(null, LiquidGlassStyle.lerp(original, a, 0.0).dockToneResponse!!.darkLowTone)
        assertEquals(curve, LiquidGlassStyle.lerp(original, a, 1.0).dockToneResponse!!.darkLowTone)
        val incompatible = b.copy(dockToneResponse = b.dockToneResponse!!.copy(
            darkLowTone = other.copy(transition = DockToneThresholds(.125, .5))))
        assertTrue(runCatching { LiquidGlassStyle.lerp(a, incompatible, .5) }.isFailure)
    }

    @Test fun standardQualityPreservesAnExplicitProfilePower() {
        val style = LiquidGlassStyle(Color.White, haze = .5, hazeBlur = 12.0,
            dispersion = 3.0, refractionProfilePower = 2.0)
        assertEquals(2.0, LiquidGlassStyle.standard(style).refractionProfilePower, 0.0)
    }

    @Test fun onlyPillFactoriesRelaxThePositiveRefractionCap() {
        listOf(false, true).forEach { isDark ->
            val colors = if (isDark) dark else light
            assertEquals(1.5, LiquidGlassStyle.pill(colors, isDark).refractionLimit, 0.0)
            listOf(
                LiquidGlassStyle.regular(colors, isDark),
                LiquidGlassStyle.menu(colors, isDark),
                LiquidGlassStyle.alert(colors, isDark),
                LiquidGlassStyle.sheet(isDark),
                LiquidGlassStyle.tinted(colors, Color.Red, isDark),
                LiquidGlassStyle.lens(colors, isDark),
                LiquidGlassStyle.controlLens(colors, isDark),
                LiquidGlassStyle.sliderLens(isDark),
                LiquidGlassStyle.rangeSliderLens(isDark),
                LiquidGlassStyle.lensContent(colors, isDark),
                LiquidGlassStyle.controlLensContent(colors, isDark),
            ).forEach { assertEquals(1.0, it.refractionLimit, 0.0) }
        }
    }

    @Test fun regularUsesReferenceLightAndDarkSurfaces() {
        val l = LiquidGlassStyle.regular(light, false)
        val d = LiquidGlassStyle.regular(dark, true)
        assertEquals(Color(0x8AFDFDFD), l.tint)
        assertEquals(dark.surfaceContainerHighest.copy(alpha = 0.62f), d.tint)
        assertEquals(0.4, l.contrast, 0.0)
        assertEquals(-0.4, d.contrast, 0.0)
        assertEquals(2.08, l.saturation, 0.0)
        assertEquals(2.84, d.saturation, 0.0)
        listOf(l, d).forEach {
            assertEquals(0.28, it.rim, 0.0)
            assertEquals(1.2, it.rimWidth, 0.0)
            assertEquals(0.17, it.rimTail, 0.0)
            assertEquals(1.0, it.rimFocus, 0.0)
            assertEquals(0.38, it.rimAdded, 0.0)
            assertEquals(0.75, it.hairlineSideOrHairline, 0.0)
        }
    }

    @Test fun pillKeepsSeparateHazeSamplingAndHighestDarkTint() {
        val d = LiquidGlassStyle.pill(dark, true)
        assertEquals(dark.surfaceContainerHighest.copy(alpha = 0.48f), d.tint)
        listOf(d, LiquidGlassStyle.pill(light, false)).forEach {
            assertEquals(0.0, it.blur, 0.0)
            assertEquals(0.72, it.haze, 0.0)
            assertEquals(15.6, it.hazeBlur, 0.0)
        }
    }

    @Test fun neutralPresentationsDoNotBorrowTheThemeSurfaceOrSelectedColor() {
        listOf(false, true).forEach { isDark ->
            val colors = if (isDark) dark else light
            val changed = colors.copy(primary = Color.Red, surfaceBright = Color.Green,
                surfaceContainerHighest = Color.Blue)
            val menu = LiquidGlassStyle.menu(colors, isDark)
            val alert = LiquidGlassStyle.alert(colors, isDark)
            assertEquals(menu, LiquidGlassStyle.menu(changed, isDark))
            assertEquals(alert, LiquidGlassStyle.alert(changed, isDark))
            listOf(menu, alert).forEach {
                assertEquals(it.tint.red, it.tint.green, 0f)
                assertEquals(it.tint.green, it.tint.blue, 0f)
                assertEquals(0.0, it.contrast, 0.0)
            }
            assertTrue(alert.blur > menu.blur)
            assertTrue(alert.brightness < menu.brightness)
            assertNotEquals(menu.copy(blur = alert.blur), alert)
            assertEquals(Color.Red.copy(alpha = .82f),
                LiquidGlassStyle.tinted(changed, changed.primary, isDark).tint)
        }
    }

    @Test fun lensMatchesReferenceOpticsInBothAppearances() {
        listOf(false, true).forEach { isDark ->
            val s = LiquidGlassStyle.lens(if (isDark) dark else light, isDark)
            assertEquals(-4.3, s.refraction, 0.0)
            assertEquals(5.5, s.bevel, 0.0)
            assertEquals(1.0, s.saturation, 0.0)
            assertEquals(if (isDark) 1.33 else 1.0, s.brightness, 0.0)
            assertEquals(if (isDark) 0.3 else 0.6, s.rim, 0.0)
            assertEquals(1.1, s.rimWidth, 0.0)
            assertEquals(6.5, s.dispersion, 0.0)
            assertEquals(1.5, s.rimBlur, 0.0)
            assertEquals(0.42, s.hairline, 0.0)
        }
    }

    @Test fun contentLensPreservesReferenceDispersion() {
        listOf(false, true).forEach { isDark ->
            val s = LiquidGlassStyle.lensContent(if (isDark) dark else light, isDark)
            assertEquals(14.0, s.refraction, 0.0)
            assertEquals(11.2, s.bevel, 0.0)
            assertEquals(if (isDark) 1.33 else 1.0, s.brightness, 0.0)
            assertEquals(3.6, s.dispersion, 0.0)
            assertEquals(2.5, s.rimBlur, 0.0)
        }
    }
}
