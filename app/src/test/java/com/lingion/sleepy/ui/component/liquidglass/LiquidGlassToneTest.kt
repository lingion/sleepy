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

    // f1f39c1f (用户锁定的 FLClash 参考资产) 起, 暗色 regular 玻璃直接取
    // surfaceContainerHighest, 旧 darkGlassTint(+7) 模型废除。
    @Test
    fun darkRegularGlassUsesTheReferenceSurfaceTint() {
        val scheme = darkColorScheme(surfaceContainer = Color(0xFF202124))
        val style = LiquidGlassStyle.regular(scheme, dark = true)
        val highestTone = Hct.fromInt(scheme.surfaceContainerHighest.toArgb()).tone
        val tintTone = Hct.fromInt(style.tint.copy(alpha = 1f).toArgb()).tone
        assertEquals(highestTone, tintTone, 0.5)
        assertEquals(0.62f, style.tint.alpha, 0.005f)
    }
}
