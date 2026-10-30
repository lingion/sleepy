// Based on Kyant0/AndroidLiquidGlass catalog components, Apache-2.0.
// Modified for SleepDown-Schedule.
package com.lingion.sleepy.ui.component.liquidglass

import com.lingion.sleepy.ui.glass.ui.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.lingion.sleepy.ui.glass.GlassBackdropDomain
import com.lingion.sleepy.ui.glass.GlassEffectFrame
import com.lingion.sleepy.ui.glass.GlassHighlightFrame
import com.lingion.sleepy.ui.glass.GlassHighlightStyle
import com.lingion.sleepy.ui.glass.GlassInnerShadowFrame
import com.lingion.sleepy.ui.glass.GlassMaterialRole
import com.lingion.sleepy.ui.glass.GlassMaterialSpec
import com.lingion.sleepy.ui.glass.rememberGlassSurfaceDescriptor
import com.lingion.sleepy.ui.glass.sleepDownGlassSurface

@Composable
fun LiquidPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedRectangle(28.dp),
    surfaceColor: Color = Color.White.copy(alpha = 0.20f),
    blurRadius: Dp = 10.dp,
    lensHeight: Dp = 24.dp,
    lensAmount: Dp = 34.dp,
    morphAllocation: com.lingion.sleepy.ui.glass.GlassMorphAllocation? = null,
    backdropSampleScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val material = remember(blurRadius, lensHeight, lensAmount, surfaceColor) {
        GlassMaterialSpec(
            role = GlassMaterialRole.Dialog,
            blur = blurRadius,
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            surfaceAlpha = surfaceColor.alpha,
            borderAlpha = 0f,
            highlightAlpha = 0.16f,
            shadowAlpha = 0.18f,
            innerShadowAlpha = 0.22f,
            chromaticAberration = false,
            depthEffect = false
        )
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "LiquidPanel",
        domain = GlassBackdropDomain.DialogBridge,
        materialRole = GlassMaterialRole.Dialog,
        sceneKey = "liquid-panel"
    )
    Box(
        modifier.sleepDownGlassSurface(
            backdrop = backdrop,
            descriptor = descriptor,
            material = material,
            shape = { shape },
            renderBounds = { morphAllocation?.localBounds() },
            allocationPaddingPx = morphAllocation?.paddingPx,
            backdropSampleScale = if (morphAllocation == null) backdropSampleScale else 1f,
            cacheDecorations = morphAllocation == null,
            effectFrame = GlassEffectFrame(
                blur = blurRadius,
                lensHeight = lensHeight,
                lensAmount = lensAmount,
                useVibrancy = true,
                chromaticAberration = false,
                highlight = GlassHighlightFrame(GlassHighlightStyle.Default, 0.16f),
                shadowAlpha = 0.18f,
                innerShadow = GlassInnerShadowFrame(radius = 10.dp, alpha = 0.22f)
            ),
            onDrawSurface = {
                drawRect(surfaceColor)
                drawRect(Color.White.copy(alpha = 0.035f), blendMode = BlendMode.Screen)
            }
        ),
        content = content
    )
}
