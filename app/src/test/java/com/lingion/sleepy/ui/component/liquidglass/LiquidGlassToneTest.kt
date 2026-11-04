package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import material.Hct
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassToneTest {
    @Test
    fun toneShiftRaisesLightnessByTheRequestedAmount() {
        val original = Color(0xFF336699)
        val shifted = original.shiftTone(7.0)
        val originalHct = Hct.fromInt(original.toArgb())
        val shiftedHct = Hct.fromInt(shifted.toArgb())
        assertEquals(originalHct.tone + 7.0, shiftedHct.tone, 0.5)
        assertEquals(originalHct.hue, shiftedHct.hue, 1.0)
    }

    @Test
    fun toneShiftClampsAtBlackAndWhite() {
        assertEquals(Color.Black, Color(0xFF336699).shiftTone(-200.0))
        assertEquals(Color.White, Color(0xFF336699).shiftTone(200.0))
    }

    @Test
    fun darkRegularGlassUsesTheShiftedSurfaceTint() {
        val scheme = darkColorScheme(surfaceContainer = Color(0xFF202124))
        val style = LiquidGlassStyle.regular(scheme, dark = true)
        val surfaceTone = Hct.fromInt(scheme.surfaceContainer.toArgb()).tone
        val tintTone = Hct.fromInt(style.tint.copy(alpha = 1f).toArgb()).tone
        assertEquals(surfaceTone + 7.0, tintTone, 0.5)
        assertEquals(0.62f, style.tint.alpha, 0.005f)
    }
}
