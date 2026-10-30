package com.lingion.sleepy.ui.glass.ui

import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.lingion.sleepy.ui.glass.performance.LocalGlassQuality
import com.xiaomanjun.sleepdownschedule.core.wallpaper.hasAnyWallpaper
import com.lingion.sleepy.ui.glass.GlassBackdropDomain
import com.lingion.sleepy.ui.glass.GlassEffectFrame
import com.lingion.sleepy.ui.glass.GlassHighlightFrame
import com.lingion.sleepy.ui.glass.GlassHighlightStyle
import com.lingion.sleepy.ui.glass.GlassInnerShadowFrame
import com.lingion.sleepy.ui.glass.GlassMaterialRole
import com.lingion.sleepy.ui.glass.GlassMaterialSpec
import com.lingion.sleepy.ui.glass.glassMorphContent
import com.lingion.sleepy.ui.glass.insetShapeFor
import com.lingion.sleepy.ui.glass.rememberGlassSurfaceDescriptor
import com.lingion.sleepy.ui.glass.sleepDownGlassSurface

typealias GlassTokens = GlassMaterialSpec

@Composable
fun glassUsesLightStyle(config: ScheduleConfigEntity): Boolean {
    if (config.wallpaperUri.isNullOrBlank()) return !appUsesDarkTheme(config)
    return when {
        config.wallpaperBrightness < 0.72f -> false
        config.homeTextLight -> false
        else -> true
    }
}

@Composable
fun GlassSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    shape: Shape = Capsule(),
    tokens: GlassTokens = GlassTokens.pill(),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    baseSurfaceColorOverride: Color? = null,
    domain: GlassBackdropDomain = GlassBackdropDomain.ChromeCombined,
    debugLabel: String = "GlassSurface",
    bottomLitTint: Boolean = false,
    bottomLitTintFloor: Float = 0.20f,
    shapeProvider: (() -> Shape)? = null,
    morphAllocation: com.lingion.sleepy.ui.glass.GlassMorphAllocation? = null,
    placementLayer: Boolean = true,
    surfaceColorOverride: Color? = null,
    restShadowAlpha: Float = 0f,
    nativeMaterial: Boolean = false,
    nativeDark: Boolean? = null,
    nativeBlurRadiusScale: Float = 1f,
    nativeTintAlphaScale: Float = 1f,
    nativeDockMaterial: Boolean = false,
    nativeFollowLightIntensityScale: Float = 1f,
    content: @Composable () -> Unit
) {
    if (nativeMaterial) {
        val renderer = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer.current
        val nativeDensity = LocalDensity.current
        if (renderer?.invoke(com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest(
            // A native RenderNode material occupies the View's bounds. An inset generic path
            // only clips its default capsule; it cannot relocate the stroke inside an envelope.
            modifier = if (morphAllocation != null) modifier.glassMorphContent(morphAllocation) else modifier,
            shape = shape, dark = nativeDark ?: !glassUsesLightStyle(config),
            blurRadiusScale = nativeBlurRadiusScale,
            tintAlphaScale = nativeTintAlphaScale,
            followLightIntensityScale = nativeFollowLightIntensityScale,
            role = if (nativeDockMaterial) com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRole.HomeDock
                else com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRole.Content,
            onClick = onClick,
            shapeProvider = if (morphAllocation != null) {
                { RoundedRectangle(with(nativeDensity) { morphAllocation.geometry().cornerRadiusPx.toDp() }) }
            } else shapeProvider,
            edgeEffects = tokens.highlightAlpha > 0f || tokens.borderAlpha > 0f
        ), content) == true) return
    }
    val glassBackdrop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop else null
    val useGlass = glassBackdrop != null
    val quality = LocalGlassQuality.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(if (pressed) 1f else 0f, label = "glass-press")
    val lightGlass = glassUsesLightStyle(config)
    val hasWallpaper = config.hasAnyWallpaper()
    val base = baseSurfaceColorOverride ?: if (lightGlass) Color.White else Color(0xFF050505)
    val selectedColor = if (useGlass) {
        if (lightGlass) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val clearAlpha = tokens.surfaceAlpha * quality
    val surfaceColor = surfaceColorOverride ?: if (selected) selectedColor else base.copy(alpha = clearAlpha)
    val restHighlightAlpha = if (hasWallpaper) 0f else tokens.highlightAlpha * 0.72f
    val highlightAlpha = if (selected) {
        tokens.highlightAlpha + 0.10f * pressProgress
    } else {
        restHighlightAlpha + tokens.highlightAlpha * 0.65f * pressProgress
    }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = debugLabel,
        domain = domain,
        materialRole = tokens.role
    )
    val effectFrame = GlassEffectFrame(
        blur = tokens.blur * quality,
        lensHeight = tokens.lensHeight * quality * (0.7f + 0.3f * pressProgress),
        lensAmount = tokens.lensAmount * quality * (0.85f + 0.35f * pressProgress),
        useVibrancy = tokens.useVibrancy,
        depthEffect = tokens.depthEffect,
        chromaticAberration = tokens.chromaticAberration,
        highlight = GlassHighlightFrame(
            style = if (highlightAlpha <= 0.001f) {
                GlassHighlightStyle.Plain
            } else {
                GlassHighlightStyle.Default
            },
            alpha = highlightAlpha
        ),
        shadowAlpha = if (selected) {
            tokens.shadowAlpha + 0.12f * pressProgress
        } else {
            maxOf(restShadowAlpha, tokens.shadowAlpha * pressProgress)
        },
        innerShadow = GlassInnerShadowFrame(
            radius = if (selected) 6.dp else 3.dp * pressProgress,
            alpha = if (selected) {
                tokens.innerShadowAlpha + 0.10f * pressProgress
            } else {
                tokens.innerShadowAlpha * pressProgress
            }
        ),
        // Passive glass never receives press events. An identity graphics layer here can
        // retain the previous pixels of a badge while its parent pager layer moves.
        layerScale = if (onClick == null) null else 1f + 0.055f * pressProgress
    )
    val contentModifier = if (useGlass) {
        modifier.sleepDownGlassSurface(
            backdrop = glassBackdrop,
            descriptor = descriptor,
            material = tokens,
            fallbackColor = com.lingion.sleepy.ui.glass.flatControlColor(!lightGlass),
            shape = shapeProvider ?: { shape },
            effectFrame = effectFrame,
            placementLayer = placementLayer,
            renderBounds = { morphAllocation?.localBounds() },
            allocationPaddingPx = morphAllocation?.paddingPx,
            onDrawSurface = {
                if (bottomLitTint) {
                    drawBottomLitGlassTint(surfaceColor, topTintFloor = bottomLitTintFloor)
                } else {
                    drawRect(surfaceColor)
                }
                // An explicit finished tint (such as the Dock material) already owns its color.
                if (surfaceColorOverride == null) {
                    if (lightGlass) {
                        drawRect(Color.White.copy(alpha = 0.014f + 0.018f * pressProgress), blendMode = BlendMode.Screen)
                    } else {
                        drawRect(Color.Black.copy(alpha = 0.014f + 0.018f * pressProgress))
                        drawRect(Color.White.copy(alpha = 0.006f + 0.010f * pressProgress), blendMode = BlendMode.Screen)
                    }
                }
            }
        )
    } else {
        modifier
            .then(if (morphAllocation == null) Modifier.clip(shape) else Modifier.graphicsLayer {
                this.shape = morphAllocation.envelope.insetShapeFor(morphAllocation.geometry())
                clip = true
            })
            .background(if (com.lingion.sleepy.ui.glass.performance.AppMaterialPreferences.isSuperPerformance)
                com.lingion.sleepy.ui.glass.flatControlColor(!lightGlass).copy(alpha =
                    com.lingion.sleepy.ui.glass.flatMaterialOpacity(tokens.role, tokens.blur.value))
                else surfaceColor.copy(alpha = surfaceColor.alpha.coerceAtLeast(0.86f)))
            .then(if (com.lingion.sleepy.ui.glass.performance.AppMaterialPreferences.isSuperPerformance)
                Modifier.border(1.dp, com.lingion.sleepy.ui.glass.flatControlBorder(!lightGlass), shape) else Modifier)
            .graphicsLayer {
                val scale = 1f + 0.04f * pressProgress
                scaleX = scale
                scaleY = scale
            }
    }
        .then(
            if (onClick == null) Modifier else Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
        )

    Box(modifier = contentModifier) {
        content()
    }
}

@Composable
fun GlassPill(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    GlassSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        shape = Capsule(),
        tokens = GlassTokens.pill(),
        selected = selected,
        onClick = onClick,
        debugLabel = "GlassPill",
        content = content
    )
}

@Composable
fun GlassLens(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    pressProgress: Float = 1f,
    content: @Composable () -> Unit = {}
) {
    val glassBackdrop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop else null
    val useGlass = glassBackdrop != null
    val quality = LocalGlassQuality.current
    val lightGlass = glassUsesLightStyle(config)
    val surfaceColor = if (lightGlass) Color.Black.copy(alpha = 0.07f * quality) else Color.White.copy(alpha = 0.08f * quality)
    val shape = Capsule()
    val material = remember { GlassMaterialSpec.lens() }
    val descriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "GlassLens",
        domain = GlassBackdropDomain.ChromeCombined,
        materialRole = GlassMaterialRole.Lens
    )
    val effectFrame = GlassEffectFrame(
        blur = 3.dp * quality,
        lensHeight = 8.dp * quality + 16.dp * quality * pressProgress,
        lensAmount = 14.dp * quality + 20.dp * quality * pressProgress,
        useVibrancy = true,
        chromaticAberration = false,
        highlight = GlassHighlightFrame(
            style = GlassHighlightStyle.Default,
            alpha = 0.08f + 0.10f * pressProgress
        ),
        shadowAlpha = 0.18f + 0.16f * pressProgress,
        innerShadow = GlassInnerShadowFrame(
            radius = 6.dp,
            alpha = 0.18f + 0.16f * pressProgress
        ),
        layerScale = 1f + 0.04f * pressProgress
    )
    val contentModifier = if (useGlass) {
        modifier.sleepDownGlassSurface(
            backdrop = glassBackdrop,
            descriptor = descriptor,
            material = material,
            shape = { shape },
            effectFrame = effectFrame,
            onDrawSurface = {
                drawRect(surfaceColor)
                if (lightGlass) {
                    drawRect(Color.White.copy(alpha = 0.014f), blendMode = BlendMode.Screen)
                } else {
                    drawRect(Color.Black.copy(alpha = 0.014f))
                    drawRect(Color.White.copy(alpha = 0.006f), blendMode = BlendMode.Screen)
                }
            }
        )
    } else {
        modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer)
    }
    Box(modifier = contentModifier) {
        content()
    }
}

@Composable
fun GlassDialogSurface(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedRectangle(26.dp),
    content: @Composable () -> Unit
) {
    if (!com.xiaomanjun.sleepdownschedule.core.ui.designsystem.usesNativeMaterialMotion()) {
        val base = if (glassUsesLightStyle(config)) Color.White else Color(0xFF050505)
        MorphDialogSurface(
            surfaceColor = base.copy(alpha = GlassTokens.dialog().surfaceAlpha * LocalGlassQuality.current),
            shape = shape, modifier = modifier
        ) { content() }
        return
    }
    GlassSurface(
        backdrop = backdrop,
        config = config,
        modifier = modifier,
        shape = shape,
        tokens = GlassTokens.dialog(),
        domain = GlassBackdropDomain.DialogBridge,
        debugLabel = "GlassDialogSurface",
        content = content
    )
}

private val StatusCapsuleBlue = Color(0xFF0A84FF)

/** Shared blue status material for the day and week headers. */
@Composable
fun BlueStatusGlassPill(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    accentColor: Color = StatusCapsuleBlue,
    content: @Composable () -> Unit
) {
    val shape = Capsule()
    val nativeTint: DrawScope.() -> Unit = remember(accentColor) {
        var height = Float.NaN
        var brush: Brush? = null
        val paint: DrawScope.() -> Unit = {
            if (height != size.height) {
                height = size.height
                // Keep the former 30% upper tint, then deepen it toward the lower rim.
                brush = bottomLitGlassTintBrush(accentColor.copy(alpha = 0.5f), height,
                    topTintFloor = 0.6f, expanded = true)
            }
            drawRect(requireNotNull(brush))
        }
        paint
    }
    val nativeSurface = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceRenderer.current
    if (nativeSurface?.invoke(com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest(
        modifier = modifier, shape = shape, dark = !glassUsesLightStyle(config),
        onDrawTint = nativeTint, blurRadiusScale = 0.6f
    ), content) == true) return
    Box(modifier = modifier) {
        GlassSurface(
            backdrop = backdrop,
            config = config,
            shape = shape,
            tokens = GlassTokens.pill().copy(
                blur = 4.dp,
                // Match the old upper-body tint; contour light below owns the lower edge.
                surfaceAlpha = 0.30f,
                highlightAlpha = 0.10f,
                innerShadowAlpha = 0.10f
            ),
            baseSurfaceColorOverride = accentColor,
            modifier = Modifier.matchParentSize()
        ) {}
        Box(
            Modifier.matchParentSize().statusPillContourLight(
                accentColor = accentColor,
                shape = shape,
                lightGlass = glassUsesLightStyle(config)
            )
        )
        content()
    }
}
