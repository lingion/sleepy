package com.lingion.sleepy.ui.glass.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.drawHighlight
import com.lingion.sleepy.ui.glass.glassBackdropProducer
import com.lingion.sleepy.ui.glass.GlassBackdropDomain
import com.lingion.sleepy.ui.glass.GlassEffectFrame
import com.lingion.sleepy.ui.glass.GlassMaterialRole
import com.lingion.sleepy.ui.glass.GlassMaterialSpec
import com.lingion.sleepy.ui.glass.rememberGlassSurfaceDescriptor
import com.lingion.sleepy.ui.glass.sleepDownGlassSurface

/** Full-resolution tint and rim, drawn after the sampled Gaussian material. */
internal fun Modifier.morphDialogTint(
    shape: Shape,
    surfaceColor: Color,
    bottomLitTint: Boolean = false
): Modifier = clip(shape).drawHighlight(shape = { shape }, highlight = { Highlight.Default.copy(alpha = 0.32f) }).drawWithCache {
    val tintBrush = if (bottomLitTint) {
        bottomLitGlassTintBrush(surfaceColor, size.height, expanded = true)
    } else null
    onDrawWithContent {
        if (tintBrush != null) drawRect(tintBrush) else drawRect(surfaceColor)
        drawContent()
    }
}

/** One half-resolution Gaussian pass; foreground, tint and rim retain their native resolution. */
@Composable
internal fun Modifier.morphDialogMaterial(
    backdrop: Backdrop?,
    shape: Shape,
    surfaceColor: Color,
    bottomLitTint: Boolean = false,
    blurRadius: Dp = 12.dp,
    domain: GlassBackdropDomain = GlassBackdropDomain.Content,
    renderEnabled: () -> Boolean = { true }
): Modifier {
    val sampled = if (backdrop != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
        com.lingion.sleepy.ui.glass.performance.AppMaterialPreferences.policy.denseMaterials) {
        val descriptor = rememberGlassSurfaceDescriptor("MorphDialogGaussian", domain, GlassMaterialRole.SimpleBlur)
        val material = remember(blurRadius) { GlassMaterialSpec.simpleBlur(blurRadius) }
        val effects = remember(blurRadius) { GlassEffectFrame(blur = blurRadius) }
        sleepDownGlassSurface(backdrop, descriptor, material, shape = { shape }, effectFrame = effects,
            backdropSampleScale = 0.5f, placementLayer = false, renderEnabled = renderEnabled)
    } else this
    return sampled.morphDialogTint(shape, surfaceColor, bottomLitTint)
}

/** The shell samples only its preceding scene; the exported producer never includes form content. */
@Composable
internal fun MorphDialogSurface(
    surfaceColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    surfaceBackdrop: LayerBackdrop? = null,
    bottomLitTint: Boolean = false,
    surfaceAlpha: () -> Float = { 1f },
    backdrop: Backdrop? = null,
    blurRadius: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier.clip(shape)) {
        CompositionLocalProvider(com.lingion.sleepy.ui.glass.LocalGlassCoordinatesFrozen provides { false }) {
            Box(Modifier.matchParentSize()
                .graphicsLayer { alpha = surfaceAlpha() }
                .then(if (surfaceBackdrop != null) Modifier.glassBackdropProducer(surfaceBackdrop) else Modifier)
                .morphDialogMaterial(backdrop, shape, surfaceColor, bottomLitTint, blurRadius,
                    renderEnabled = { surfaceAlpha() > 0.001f }))
        }
        content()
    }
}
