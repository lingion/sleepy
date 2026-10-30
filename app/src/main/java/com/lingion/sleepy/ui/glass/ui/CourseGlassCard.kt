package com.lingion.sleepy.ui.glass.ui

import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.LiquidCourseCardBlurMax
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.SimpleCourseCardBlurMax
import com.lingion.sleepy.ui.glass.performance.LocalGlassQuality
import com.xiaomanjun.sleepdownschedule.core.ui.text.LocalCourseTextBackground
import com.xiaomanjun.sleepdownschedule.courseCardBlurMaximum
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalPersonalizationPreview
import com.xiaomanjun.sleepdownschedule.core.wallpaper.hasAnyWallpaper
import com.xiaomanjun.sleepdownschedule.core.wallpaper.homeFlatBackgroundColor
import com.lingion.sleepy.ui.glass.CourseGlassOcclusionPhase
import com.lingion.sleepy.ui.glass.GlassBackdropDomain
import com.lingion.sleepy.ui.glass.GlassEffectFrame
import com.lingion.sleepy.ui.glass.GlassHighlightFrame
import com.lingion.sleepy.ui.glass.GlassHighlightStyle
import com.lingion.sleepy.ui.glass.GlassInnerShadowFrame
import com.lingion.sleepy.ui.glass.GlassMaterialRole
import com.lingion.sleepy.ui.glass.GlassMaterialSpec
import com.lingion.sleepy.ui.glass.LocalCourseGlassMaterialRevealProgress
import com.lingion.sleepy.ui.glass.LocalCourseGlassOcclusionPhase
import com.lingion.sleepy.ui.glass.decorationOnly
import com.lingion.sleepy.ui.glass.glassBackdropProducer
import com.lingion.sleepy.ui.glass.materialEffectsOnly
import com.lingion.sleepy.ui.glass.rememberGlassSurfaceDescriptor
import com.lingion.sleepy.ui.glass.sleepDownGlassSurface

internal const val FlatCourseCardMinimumAlpha = 0.35f
internal const val LiquidCourseCardSurfaceDimAlpha = 0.16f
// Compress bright wallpaper towards neutral grey without the soot of a black scrim.
internal val LiquidCourseCardSurfaceTone = Color(0xFF626A6D)

internal fun courseGlassTintAlpha(cardAlpha: Float, quality: Float, hasWallpaper: Boolean): Float {
    val maximum = if (hasWallpaper) 0.68f else 0.16f
    return (cardAlpha.coerceIn(0f, 1f) * maximum * quality)
        .coerceIn(0f, maximum)
}

internal fun courseSimpleBlurTintAlpha(cardAlpha: Float, quality: Float, hasWallpaper: Boolean): Float {
    if (hasWallpaper) {
        return (cardAlpha.coerceIn(0f, 1f) * 0.72f * quality)
            .coerceIn(0.28f, 0.78f)
    }
    return (cardAlpha.coerceIn(0f, 1f) * 0.18f * quality)
        .coerceIn(0f, 0.18f)
}

/**
 * 壁纸降亮度时课程卡片的跟随衰减系数。卡片也随壁纸变暗一点，但幅度小于壁纸；
 * 开启质感轮廓光时降得比关闭时更多（轮廓光更依赖亮度氛围）。
 */
internal fun courseCardBrightnessAttenuation(brightness: Float, outlineLightEnabled: Boolean): Float {
    val dim = (1f - brightness.coerceIn(0.35f, 1f)).coerceIn(0f, 0.65f)
    val strength = if (outlineLightEnabled) 0.55f else 0.18f
    return (1f - dim * strength).coerceIn(0.2f, 1f)
}

internal fun courseCardGlassEffectFrame(
    tokens: GlassMaterialSpec,
    liveBlur: Float,
    quality: Float,
    hasWallpaper: Boolean,
    refractionStrength: Float = 0.5f
): GlassEffectFrame {
    val refraction = refractionStrength.coerceIn(0f, 1f)
    // 以滑块三个点 0 / 默认 0.5 / 1 重新映射：乘子在 0.5~1.5 间线性变化，
    // 中点 f(0.5)=1.0 与原基准值一致，两端调节范围明显放大。
    val lensShapeFactor = 0.5f + refraction
    return GlassEffectFrame(
    blur = liveBlur.coerceIn(0f, LiquidCourseCardBlurMax).dp * quality,
    lensHeight = if (refraction <= 0f) {
        0.dp
    } else {
        tokens.lensHeight * quality * (if (hasWallpaper) 1f else 0.78f) * lensShapeFactor
    },
    lensAmount = if (refraction <= 0f) {
        0.dp
    } else {
        tokens.lensAmount * quality * (if (hasWallpaper) 1f else 1.35f) *
            lensShapeFactor
    },
    // Match Nexio's blur -> lens base; SleepDown tint, outline light and shadows follow it.
    useVibrancy = false,
    depthEffect = false,
    chromaticAberration = tokens.chromaticAberration,
    highlight = GlassHighlightFrame(
        style = GlassHighlightStyle.Default,
        alpha = if (hasWallpaper) {
            tokens.highlightAlpha
        } else {
            maxOf(tokens.highlightAlpha, 0.10f)
        }
    ),
    shadowAlpha = tokens.shadowAlpha,
    innerShadow = GlassInnerShadowFrame(
        radius = 5.dp,
        alpha = tokens.innerShadowAlpha
    )
    )
}

internal val MutedCourseLightColor = Color(0xFF989EA8)

@Composable
fun CourseGlassCard(
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    modifier: Modifier = Modifier,
    course: CourseEntity? = null,
    shape: Shape = RoundedRectangle(12.dp),
    blurOverride: Float? = null,
    renderSurface: Boolean = true,
    mountMaterial: Boolean = true,
    viewportMaterialVisible: Boolean = true,
    // Compatibility for the course editor's shell; timetable cards always sample at 1x.
    editorSampleScale: Float = 1f,
    expandedOutlineLight: Boolean = false,
    followTouch: Boolean = true,
    // Expanded forms can keep course material without the card's rim/caustic projection.
    nativeEdgeEffects: Boolean = true,
    followLightRadius: androidx.compose.ui.unit.Dp? = null,
    muted: Boolean = false,
    flatOpacityOverride: Float? = null,
    morphAllocation: com.lingion.sleepy.ui.glass.GlassMorphAllocation? = null,
    surfaceBackdrop: LayerBackdrop? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // A tint alone leaves glass highlights and the content bright. Dim the complete rendered
    // card, including native material, without an extra offscreen layer or changing its opacity.
    val statusModifier = modifier.then(if (muted) Modifier.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            drawOutline(outline, Color.Black.copy(alpha = 0.26f))
        }
    } else Modifier)
    val materialPolicy = com.lingion.sleepy.ui.glass.performance.effectiveAppMaterialPolicy()
    if (!materialPolicy.denseMaterials) {
        val preview = LocalPersonalizationPreview.current
        val base = if (muted) MutedCourseLightColor else courseCardBaseColor(config, course)
        val opacity = flatOpacityOverride?.coerceIn(0f, 1f) ?: materialPolicy.opacity(blurOverride ?: preview?.cardBlur ?: config.courseCardBlur,
            com.xiaomanjun.sleepdownschedule.model.courseCardBlurMaximum(config.courseCardGlassEnabled))
        val background = rememberCourseTextBackground(base, opacity, false, 0f, false, false,
            flatBackground = homeFlatBackgroundColor(appUsesDarkTheme(config)), ready = true, cardBounds = { null })
        Box(statusModifier.clip(shape).then(if (renderSurface) Modifier.background(base.copy(alpha = opacity)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
            CompositionLocalProvider(LocalCourseTextBackground provides background) { content() }
        }
        return
    }
    val occlusionPhase = LocalCourseGlassOcclusionPhase.current
    val materialRevealProgress = LocalCourseGlassMaterialRevealProgress.current
    val occlusionAllowsMaterial =
        mountMaterial && occlusionPhase.mountsMaterialNodes
    val drawMaterialNodes = occlusionAllowsMaterial && viewportMaterialVisible
    val mountMaterialNodes = occlusionAllowsMaterial ||
        com.lingion.sleepy.ui.glass.GlassMotionExperiments.retainMaterialNodes
    val materialCrossfadeActive =
        occlusionPhase == CourseGlassOcclusionPhase.PostCloseRestore ||
            occlusionPhase == CourseGlassOcclusionPhase.Revealing
    val previewState = LocalPersonalizationPreview.current
    val nativeMode = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardMode.current
    if (nativeMode?.enabled == true) {
        val base = if (muted) MutedCourseLightColor else courseCardBaseColor(config, course)
        val alpha = nativeMode.tintStrength
        val tintDraw: DrawScope.() -> Unit = remember(base, alpha, expandedOutlineLight) {
            var height = Float.NaN
            var brush: Brush? = null
            val draw: DrawScope.() -> Unit = {
                if (size.height != height) {
                    height = size.height
                    brush = bottomLitGlassTintBrush(base.copy(alpha = alpha), height,
                        expanded = expandedOutlineLight)
                }
                // Transfer course tint only. Native stroke and foreground light own the
                // material highlights; legacy shading would paint over and duplicate them.
                drawRect(requireNotNull(brush))
            }
            draw
        }
        val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val cardBounds = remember { {
            coordinates[0]?.takeIf { it.isAttached }?.let { position ->
                val origin = position.localToWindow(Offset.Zero)
                Rect(origin.x, origin.y, origin.x + position.size.width, origin.y + position.size.height)
            }
        } }
        val background = rememberCourseTextBackground(base, alpha, true, nativeMode.blurRadiusPx, true, expandedOutlineLight,
            flatBackground = homeFlatBackgroundColor(appUsesDarkTheme(config)).takeUnless { config.hasAnyWallpaper() },
            ready = true, cardBounds = cardBounds)
        val renderer = com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardRenderer.current
        if (renderer?.invoke(com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeCourseCardRequest(
                statusModifier.onGloballyPositioned { coordinates[0] = it }, shape, appUsesDarkTheme(config),
                renderSurface, drawMaterialNodes, onClick, tintDraw,
                followTouch = followTouch,
                edgeEffects = nativeEdgeEffects,
                followLightRadius = followLightRadius
            )) {
                CompositionLocalProvider(LocalCourseTextBackground provides background) { content() }
            } == true) return
    }
    // A native edition must not silently render the liquid recipe when a vendor surface
    // is unavailable. Keep the course tint and interaction in the existing flat material.
    if (com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardRenderer.current != null) {
        val base = if (muted) MutedCourseLightColor else courseCardBaseColor(config, course)
        val opacity = nativeMode?.tintStrength ?: 0.5f
        val background = rememberCourseTextBackground(base, opacity, false, 0f, false, false,
            flatBackground = homeFlatBackgroundColor(appUsesDarkTheme(config)), ready = true, cardBounds = { null })
        Box(statusModifier.clip(shape).then(if (renderSurface) Modifier.background(base.copy(alpha = opacity)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
            CompositionLocalProvider(LocalCourseTextBackground provides background) { content() }
        }
        return
    }
    val glassBackdrop = if (
        config.courseCardGlassEnabled &&
        config.hasAnyWallpaper() &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) backdrop else null
    val simpleBlurBackdrop = if (
        !config.courseCardGlassEnabled &&
        config.courseCardGaussianBlurEnabled &&
        config.hasAnyWallpaper() &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) backdrop else null
    val useGlass = glassBackdrop != null
    val quality = LocalGlassQuality.current
    val clickInteractionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val baseColor = if (muted) MutedCourseLightColor else courseCardBaseColor(config, course)
    val hasWallpaper = config.hasAnyWallpaper()
    val tokens = GlassTokens.courseCard(blurOverride ?: config.courseCardBlur)
    val lightGlass = glassUsesLightStyle(config)
    val liveLiquidBlur = blurOverride ?: previewState?.cardBlur ?: config.courseCardBlur
    val performanceMaterial = materialPolicy.level == com.lingion.sleepy.ui.glass.performance.AppMaterialLevel.PERFORMANCE
    val liveRefractionStrength = previewState?.cardRefractionStrength
        ?: config.courseCardRefractionStrength
    val outlineLightEnabled = muted || (config.courseCardGlassEnabled &&
        config.courseCardOutlineLightEnabled &&
        config.hasAnyWallpaper())
    val liquidEffectFrame = courseCardGlassEffectFrame(
        tokens = tokens,
        liveBlur = liveLiquidBlur,
        quality = quality,
        hasWallpaper = hasWallpaper,
        refractionStrength = if (performanceMaterial) 0f else liveRefractionStrength
    ).let { frame ->
        if (performanceMaterial) frame.copy(highlight = null, shadowAlpha = null, innerShadow = null) else frame
    }
    val liquidDescriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "CourseGlassCard",
        domain = GlassBackdropDomain.Content,
        materialRole = GlassMaterialRole.CourseCard,
        sceneKey = "course-card"
    )
    val simpleBlurValue = (blurOverride ?: previewState?.cardBlur ?: config.courseCardBlur)
        .coerceIn(0f, SimpleCourseCardBlurMax) * quality
    val textCardCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var textCardReady by remember { mutableStateOf(false) }
    val textCardBounds = remember {
        {
            textCardCoordinates[0]?.takeIf { it.isAttached }?.let { position ->
                val origin = position.localToWindow(Offset.Zero)
                // Keep the full card height when its top is scrolled outside the viewport.
                Rect(origin.x, origin.y, origin.x + position.size.width, origin.y + position.size.height)
            }
        }
    }
    val liveCardAlpha = (previewState?.cardAlpha ?: config.cardAlpha).coerceIn(0f, 1f)
    val textSurfaceAlpha = when {
        !hasWallpaper -> liveCardAlpha.coerceAtLeast(FlatCourseCardMinimumAlpha)
        useGlass -> courseGlassTintAlpha(if (outlineLightEnabled) 0.75f else liveCardAlpha, quality, hasWallpaper) *
            courseCardBrightnessAttenuation(config.wallpaperBrightness, outlineLightEnabled)
        simpleBlurBackdrop != null -> courseSimpleBlurTintAlpha(liveCardAlpha, quality, hasWallpaper)
        !config.courseCardGlassEnabled && !config.courseCardGaussianBlurEnabled ->
            liveCardAlpha.coerceAtLeast(if (hasWallpaper) 0.35f else 0.92f)
        else -> liveCardAlpha.coerceAtLeast(0.86f)
    }
    val textBackground = rememberCourseTextBackground(
        base = baseColor, tintAlpha = textSurfaceAlpha,
        blurred = useGlass || simpleBlurBackdrop != null,
        blurPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            (if (useGlass) liquidEffectFrame.blur ?: 0.dp else simpleBlurValue.dp).toPx()
        },
        outline = useGlass && outlineLightEnabled, expanded = expandedOutlineLight,
        flatBackground = if (!hasWallpaper) homeFlatBackgroundColor(appUsesDarkTheme(config)) else null,
        ready = textCardReady, cardBounds = textCardBounds,
        surfaceDimAlpha = if (useGlass) LiquidCourseCardSurfaceDimAlpha else 0f
    )
    val simpleMaterial = GlassMaterialSpec.simpleBlur(simpleBlurValue.dp)
    val simpleDescriptor = rememberGlassSurfaceDescriptor(
        debugLabel = "CourseSimpleBlurCard",
        domain = GlassBackdropDomain.Content,
        materialRole = GlassMaterialRole.SimpleBlur,
        sceneKey = "course-card-simple-blur"
    )
    val backdropViewport = com.lingion.sleepy.ui.glass.LocalCourseBackdropViewport.current
    val sharedWallpaper = com.lingion.sleepy.ui.glass.LocalSharedCourseBackdrop.current
    val requiredBlurPx = with(androidx.compose.ui.platform.LocalDensity.current) { (liquidEffectFrame.blur ?: 0.dp).toPx() }
    val sharedWallpaperCompatible = sharedWallpaper != null &&
        sharedWallpaper.source === glassBackdrop && sharedWallpaper.radiusPx == requiredBlurPx &&
        sharedWallpaper.vibrant == liquidEffectFrame.useVibrancy && morphAllocation == null
    val useSharedWallpaper = sharedWallpaperCompatible && sharedWallpaper.ready
    val sampledSource = if (useSharedWallpaper) sharedWallpaper else glassBackdrop
    // Course highlights use a fixed cached vector instead of the Kyant directional shader.
    val cardEffects = if (useSharedWallpaper) {
        liquidEffectFrame.materialEffectsOnly().copy(blur = null, useVibrancy = false)
    } else liquidEffectFrame.materialEffectsOnly()
    // Timetable shadows use Compose's cached drawing masks. The editor/morph compatibility
    // shell retains its existing allocation and decoration geometry.
    val drawCourseShadows = morphAllocation == null && surfaceBackdrop == null
    val decoratedFrame = if (drawCourseShadows) liquidEffectFrame.copy(shadowAlpha = null, innerShadow = null) else liquidEffectFrame
    val decorationEffectFrame = decoratedFrame.decorationOnly().copy(highlight = null)
    // At rest and during ordinary card motion, one draw node owns the sampled material,
    // tint and decorations. They then use the same measured size, outline and placement.
    // Keep the split path while the sampled material alone is crossfading or suspended.
    val unifiedLiquidSurface = useGlass && renderSurface && drawMaterialNodes &&
        !materialCrossfadeActive
    val unifiedLiquidEffectFrame = if (useSharedWallpaper) {
        decoratedFrame.copy(blur = null, useVibrancy = false, highlight = null)
    } else decoratedFrame.copy(highlight = null)
    val presetHighlight = Modifier.presetCourseCardHighlight(
        shape = shape,
        alpha = if (performanceMaterial) 0.20f else (liquidEffectFrame.highlight?.alpha ?: 0f) *
            if (outlineLightEnabled && lightGlass) 0.55f else 1f,
        enabled = { viewportMaterialVisible },
        bounds = { morphAllocation?.localBounds() },
        strokeWidth = if (performanceMaterial) 0.8.dp else 0.5.dp
    )
    val configuredAlpha = config.cardAlpha
    val configuredBrightness = config.wallpaperBrightness
    // Like Nexio's CourseCard, retain the actual surface callback, not only a wrapper.
    // Replacing a callback stored in snapshot state invalidates the sampled draw even if
    // its pixels are identical, which otherwise repeats work on unrelated recompositions.
    val liquidSurfaceDraw: DrawScope.() -> Unit = remember(previewState, configuredAlpha,
        configuredBrightness, outlineLightEnabled, quality, hasWallpaper, baseColor,
        expandedOutlineLight, lightGlass) {
        var cachedTint = Color.Unspecified
        var cachedHeight = Float.NaN
        var cachedBrush: Brush? = null
        val draw: DrawScope.() -> Unit = {
        val liveAlpha = previewState?.cardAlpha ?: configuredAlpha
        val brightnessAttenuation = courseCardBrightnessAttenuation(
            configuredBrightness,
            outlineLightEnabled
        )
        val tintStrength = if (outlineLightEnabled) 0.75f else liveAlpha
        val tintAlpha = courseGlassTintAlpha(tintStrength, quality, hasWallpaper) *
            brightnessAttenuation
        val tint = baseColor.copy(alpha = tintAlpha)
        if (outlineLightEnabled) {
            if (cachedTint != tint || cachedHeight != size.height) {
                cachedTint = tint
                cachedHeight = size.height
                cachedBrush = bottomLitGlassTintBrush(tint, size.height, expanded = expandedOutlineLight,
                    colorTransform = { courseGlassSurfaceColor(it, lightGlass) })
            }
            drawRect(requireNotNull(cachedBrush))
        } else {
            drawRect(courseGlassSurfaceColor(tint, lightGlass))
        }
        // Darken the sampled surface and its tint together, before foreground and rim.
        // This stays in the existing draw callback: no readback, filter or offscreen layer.
        drawRect(LiquidCourseCardSurfaceTone.copy(alpha = LiquidCourseCardSurfaceDimAlpha))
        }
        draw
    }
    val stableCardShape: () -> Shape = remember(shape) { { shape } }
    val stableMaterialEnabled: () -> Boolean = remember(drawMaterialNodes) { { drawMaterialNodes } }
    val stableMaterialBounds: () -> Rect? = remember(morphAllocation) { { morphAllocation?.localBounds() } }
    val materialAlphaModifier = if (materialCrossfadeActive) {
        Modifier.graphicsLayer {
            alpha = materialRevealProgress().coerceIn(0f, 1f)
        }
    } else {
        Modifier
    }
    val cardModifier = statusModifier.onGloballyPositioned {
        textCardCoordinates[0] = it
        textCardReady = true
    }
        .then(
            if (onClick == null) Modifier else Modifier
                .pointerInput(onClick) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        pressed = true
                        try {
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        } finally {
                            pressed = false
                        }
                    }
                }
                .clickable(
                    interactionSource = clickInteractionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
        )
    Box(modifier = cardModifier) {
        // Only sampled blur/lens is suspended. Tint and full-resolution decoration stay intact.
        val surfaceContent: @Composable BoxScope.() -> Unit = {
            if (renderSurface && useGlass && drawCourseShadows) {
                liquidEffectFrame.shadowAlpha?.let { shadowAlpha ->
                    Box(Modifier.matchParentSize().courseGlassOuterShadow(shape, shadowAlpha) { viewportMaterialVisible })
                }
            }
            val surfaceModifier = if (!renderSurface || (!mountMaterialNodes && (useGlass || simpleBlurBackdrop != null))) {
                Modifier
            } else if (useGlass) {
                Modifier
                    .matchParentSize()
                    .sleepDownGlassSurface(
                            backdrop = requireNotNull(sampledSource),
                            descriptor = liquidDescriptor,
                            material = tokens,
                            shape = stableCardShape,
                            effectFrame = if (unifiedLiquidSurface) unifiedLiquidEffectFrame else cardEffects,
                            effectInputKey = if (unifiedLiquidSurface) unifiedLiquidEffectFrame.materialEffectsOnly() else cardEffects,
                            backdropViewport = backdropViewport.takeIf { morphAllocation == null },
                            backdropSampleScale = if (surfaceBackdrop != null && morphAllocation == null) editorSampleScale else 1f,
                            cacheDecorations = morphAllocation == null,
                            placementLayer = morphAllocation != null,
                            renderEnabled = stableMaterialEnabled,
                            renderBounds = stableMaterialBounds,
                            allocationPaddingPx = morphAllocation?.paddingPx,
                            onDrawSurface = if (unifiedLiquidSurface) liquidSurfaceDraw else null
                    )
            } else if (simpleBlurBackdrop != null) {
                // Non-liquid mode still samples the content behind the course card, but
                // deliberately omits lens/refraction/vibrancy.  This is a cheap Gaussian
                // material rather than falling all the way back to an opaque rectangle.
                Modifier
                    .matchParentSize()
                    .sleepDownGlassSurface(
                        backdrop = simpleBlurBackdrop,
                        descriptor = simpleDescriptor,
                        material = simpleMaterial,
                        renderEnabled = stableMaterialEnabled,
                        placementLayer = false,
                        shape = stableCardShape,
                        effectFrame = GlassEffectFrame(blur = simpleBlurValue.dp)
                    )
            } else {
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .drawBehind {
                        val liveAlpha = previewState?.cardAlpha ?: config.cardAlpha
                        val alpha = liveAlpha.coerceIn(0f, 1f)
                        drawRect(
                            baseColor.copy(
                                alpha = if (!hasWallpaper) {
                                    alpha.coerceAtLeast(FlatCourseCardMinimumAlpha)
                                } else if (
                                    !config.courseCardGlassEnabled &&
                                    !config.courseCardGaussianBlurEnabled &&
                                    hasWallpaper
                                ) {
                                    // 纯纯色卡片：透明度拉满时也不能完全消失，保留可辨识底座
                                    alpha.coerceAtLeast(0.35f)
                                } else {
                                    alpha.coerceAtLeast(0.86f)
                                }
                            )
                        )
                    }
            }
            Box(
                (if (useGlass || simpleBlurBackdrop != null) materialAlphaModifier else Modifier)
                    .then(surfaceModifier)
                    .then(if (unifiedLiquidSurface) presetHighlight else Modifier)
            )
            if (useGlass && !unifiedLiquidSurface) {
                // Shared/downsampled consumers own only the expensive sampled backdrop. Keep tint,
                // highlight, outer shadow and inner shadow at full resolution and per-card geometry.
                Box(
                    Modifier
                        .matchParentSize()
                        .sleepDownGlassSurface(
                            backdrop = glassBackdrop,
                            descriptor = liquidDescriptor,
                            material = tokens,
                            shape = { shape },
                            effectFrame = decorationEffectFrame,
                            cacheDecorations = morphAllocation == null,
                            placementLayer = morphAllocation != null,
                            renderEnabled = { viewportMaterialVisible },
                            renderBounds = { morphAllocation?.localBounds() },
                            allocationPaddingPx = morphAllocation?.paddingPx,
                            sampleBackdrop = false,
                            effectsOverride = {},
                            onDrawSurface = liquidSurfaceDraw
                        )
                        .then(presetHighlight)
                )
            } else if (simpleBlurBackdrop != null) {
                Box(
                    Modifier.matchParentSize().sleepDownGlassSurface(
                        backdrop = simpleBlurBackdrop,
                        descriptor = simpleDescriptor,
                        material = simpleMaterial,
                        shape = { shape },
                        effectFrame = GlassEffectFrame(
                            blur = null,
                            highlight = GlassHighlightFrame(style = GlassHighlightStyle.Default, alpha = 0.10f),
                            shadowAlpha = 0.12f,
                            innerShadow = GlassInnerShadowFrame(radius = 3.dp, alpha = 0.08f)
                        ),
                        sampleBackdrop = false,
                        cacheDecorations = true,
                        placementLayer = false,
                        renderEnabled = { viewportMaterialVisible },
                        onDrawSurface = {
                            drawRect(baseColor.copy(alpha = courseSimpleBlurTintAlpha(
                                previewState?.cardAlpha ?: config.cardAlpha, quality, hasWallpaper
                            )))
                        }
                    )
                )
            }
            // Full-resolution additive inner light, independent of the sampled blur/lens. The base
            // tint fades toward the top while the brighter bottom light spreads with smooth falloff.
            if (renderSurface && useGlass && drawCourseShadows) {
                liquidEffectFrame.innerShadow?.let { shadow ->
                    Box(Modifier.matchParentSize().courseGlassInnerShadow(shape, shadow.radius, shadow.alpha) { viewportMaterialVisible })
                }
            }
            if (outlineLightEnabled && !performanceMaterial) {
                VerticalGlassAccentOverlay(
                    accentColor = baseColor,
                    shape = shape,
                    lightGlass = lightGlass,
                    intensity = (previewState?.cardAlpha ?: config.cardAlpha) *
                        courseCardBrightnessAttenuation(
                            config.wallpaperBrightness,
                            outlineLightEnabled = true
                        ),
                    expanded = expandedOutlineLight,
                    surroundingEdgeGlow = true,
                    morphAllocation = morphAllocation,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        if (surfaceBackdrop != null) {
            // Record the shell alone; the editor body and header are later siblings.
            Box(Modifier.matchParentSize().glassBackdropProducer(surfaceBackdrop), content = surfaceContent)
        } else {
            surfaceContent()
        }
        CompositionLocalProvider(LocalCourseTextBackground provides textBackground) { content() }
        if (pressed) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.13f))
            )
        }
    }
}
