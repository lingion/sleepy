package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Values transcribed from FLClash 0.9.1+2026101702, lib/ui/liquid_glass.dart:79–224. */
class LiquidGlassStyleReferenceTest {
    private val light = lightColorScheme()
    // Deliberately distinguish the highest surface from the ordinary card surface.
    private val dark = darkColorScheme(
        surfaceContainer = Color(0xFF112233),
        surfaceContainerHighest = Color(0xFF445566),
    )

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

    @Test fun menuAndAlertUseTheirOwnMaterialNotDockPill() {
        val menu = LiquidGlassStyle.menu(dark, true)
        assertEquals(dark.surfaceContainerHighest.copy(alpha = 0.63f), menu.tint)
        assertEquals(12.0, menu.blur, 0.0)
        assertEquals(1.8, menu.brightness, 0.0)
        assertEquals(-0.53, menu.contrast, 0.0)
        assertEquals(menu.copy(blur = 16.0), LiquidGlassStyle.alert(dark, true))
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
